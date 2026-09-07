using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Windows.Media.Imaging;

namespace OrangeGO.Windows.Core;

/// <summary>
/// HTTP 传输客户端（发送端）。按 PROTOCOL.md「二、传输」先 init 授权再逐文件上传。
/// </summary>
public sealed class SenderClient : IDisposable
{
    private readonly HttpClient _http;
    private readonly Func<Protocol.DeviceInfo> _identity;
    private readonly FingerprintStore? _fpStore;

    public SenderClient(Func<Protocol.DeviceInfo> identity, long timeoutMs = 30_000, FingerprintStore? fpStore = null)
    {
        _identity = identity;
        _fpStore = fpStore;
        // P0-3+P0-4: HTTPS + mTLS（出示客户端证书 + TOFU 指纹钉扎防 MITM）
        var handler = new HttpClientHandler
        {
            ServerCertificateCustomValidationCallback = (msg, cert, _, _) => VerifyTofu(msg, cert)
        };
        handler.ClientCertificates.Add(LsCompat.ClientCertificate);
        _http = new HttpClient(handler) { Timeout = TimeSpan.FromMilliseconds(timeoutMs) };
    }

    /// <summary>TOFU 指纹校验：首次连接信任并存储，后续校验一致性（防 MITM）。无 store 时放行。</summary>
    private bool VerifyTofu(HttpRequestMessage msg, X509Certificate2? cert)
    {
        if (_fpStore is null || cert is null) return true;
        var sha = Convert.ToHexString(SHA256.HashData(cert.RawData));
        var ip = msg.RequestUri?.Host ?? "";
        var stored = _fpStore.Get(ip);
        if (string.IsNullOrEmpty(stored)) _fpStore.Put(ip, sha); // TOFU：首次信任并存储
        else if (sha != stored) return false; // 指纹不匹配 → 拒绝（潜在 MITM）
        return true;
    }

    /// <summary>先发送 init，接收方授权后返回授权 token；被拒绝则抛异常。</summary>
    public async Task<string> InitAsync(IPAddress ip, int port, string sendId, IReadOnlyList<string> filePaths)
    {
        long total = 0;
        foreach (var f in filePaths) total += new FileInfo(f).Length;

        var payload = new Protocol.SendInitPayload
        {
            SendId = sendId,
            DeviceId = _identity().DeviceId,
            Name = _identity().Name,
            TotalFiles = filePaths.Count,
            TotalSize = total
        };

        using var resp = await _http.PostAsync($"https://{ip}:{port}/api/v1/send/init",
            new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"));
        var body = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());

        if (!resp.IsSuccessStatusCode || body is null || body.MessageType != Protocol.Ok)
            throw new Exception(body?.Info ?? $"init 失败 ({resp.StatusCode})");

        return body.Token!;
    }

    /// <summary>上传单个文件，回调进度（0..1）。</summary>
    public async Task SendFileAsync(IPAddress ip, int port, string token, string filePath, Action<double>? onProgress = null)
    {
        var fileInfo = new FileInfo(filePath);
        var name = Uri.EscapeDataString(Path.GetFileName(filePath));

        using var stream = File.OpenRead(filePath);
        var content = new StreamContent(stream) { Headers = { ContentLength = fileInfo.Length } };
        content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");

        // 用 ProgressContent 包装以感知上传进度
        var wrapped = new ProgressContent(content, fileInfo.Length, onProgress);
        var req = new HttpRequestMessage(HttpMethod.Post, $"https://{ip}:{port}/api/v1/send/file")
        {
            Content = wrapped
        };
        req.Headers.TryAddWithoutValidation("Authorization", $"Bearer {token}");
        req.Headers.TryAddWithoutValidation("X-FileName", name);
        req.Headers.TryAddWithoutValidation("X-FileSize", fileInfo.Length.ToString());

        using var resp = await _http.SendAsync(req);
        if (!resp.IsSuccessStatusCode)
        {
            var b = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
            throw new Exception(b?.Info ?? $"发送失败 ({resp.StatusCode})");
        }
    }

    /// <summary>v2 prepare-upload：携带文件清单，接收方授权后返回 sessionId + 每文件 token。</summary>
    private static readonly string[] _imgExts = [".png", ".jpg", ".jpeg", ".bmp", ".gif", ".ico", ".webp", ".heic", ".heif", ".dng"];
    private static readonly string[] _vidExts = [".mp4", ".mov", ".mkv", ".avi", ".wmv", ".webm", ".flv", ".m4v"];

    private static string? GenerateThumbBase64(string path)
    {
        var src = ShellThumbnail.GetThumbnail(path, 256);
        if (src is null)
        {
            // Shell 无法生成缩略图（如 DNG 无 RAW 编解码器）：退化提取内嵌 JPEG 预览
            var jpeg = ShellThumbnail.ExtractEmbeddedJpeg(path);
            return jpeg is null ? null : Convert.ToBase64String(jpeg);
        }
        var encoder = new JpegBitmapEncoder { QualityLevel = 60 };
        encoder.Frames.Add(BitmapFrame.Create(src));
        using var ms = new MemoryStream();
        encoder.Save(ms);
        return Convert.ToBase64String(ms.ToArray());
    }

    public async Task<Protocol.PrepareResult?> PrepareAsync(IPAddress ip, int port, string sendId, IReadOnlyList<string> filePaths,
        List<string>? fileIds = null, IReadOnlyList<string?>? relPaths = null)
    {
        long total = 0;
        var files = new Dictionary<string, Protocol.FileMeta>();
        for (int i = 0; i < filePaths.Count; i++)
        {
            var path = filePaths[i];
            var len = new FileInfo(path).Length;
            total += len;
            var id = Guid.NewGuid().ToString("N");
            fileIds?.Add(id); // 与 filePaths 同序记录 id
            var ext = Path.GetExtension(path).ToLowerInvariant();
            var thumb = (Array.IndexOf(_imgExts, ext) >= 0 || Array.IndexOf(_vidExts, ext) >= 0)
                ? GenerateThumbBase64(path) : null;
            files[id] = new Protocol.FileMeta
            {
                Id = id,
                FileName = Path.GetFileName(path),
                Size = len,
                RelativePath = relPaths is not null && i < relPaths.Count ? relPaths[i] : null,
                Thumb = thumb
            };
        }

        var payload = new Protocol.SendInitPayload
        {
            SendId = sendId,
            DeviceId = _identity().DeviceId,
            Name = _identity().Name,
            TotalFiles = filePaths.Count,
            TotalSize = total,
            Files = files
        };

        using var resp = await _http.PostAsync($"https://{ip}:{port}{Protocol.PathPrepare}",
            new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"));
        if ((int)resp.StatusCode == 204) // 全部被拒绝/无接受
            return new Protocol.PrepareResult { SessionId = sendId, Tokens = new Dictionary<string, string>() };
        var body = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
        if (!resp.IsSuccessStatusCode || body is null || body.MessageType != Protocol.Ok)
            throw new Exception(body?.Info ?? $"prepare 失败 ({resp.StatusCode})");
        return new Protocol.PrepareResult { SessionId = body.SessionId ?? sendId, Tokens = body.Files ?? new() };
    }

    /// <summary>v2 上传单文件：按会话+文件+专属 token 上传。</summary>
    public Task SendFileV2Async(IPAddress ip, int port, string sessionId, string fileId, string token,
        string filePath, Action<double>? onProgress = null, CancellationToken ct = default)
    {
        var len = new FileInfo(filePath).Length;
        return UploadV2CoreAsync(ip, port, sessionId, fileId, token, filePath, len, onProgress, ct);
    }

    private async Task UploadV2CoreAsync(IPAddress ip, int port, string sessionId, string fileId, string token,
        string filePath, long len, Action<double>? onProgress, CancellationToken ct)
    {
        var name = Uri.EscapeDataString(Path.GetFileName(filePath));
        // P2: HEAD 查已接收字节数，支持断点续传
        long resumeOffset = 0;
        try
        {
            using var headResp = await _http.SendAsync(new HttpRequestMessage(HttpMethod.Head,
                $"https://{ip}:{port}{Protocol.PathUpload}?sessionId={sessionId}&fileId={fileId}&token={token}"), ct);
            if (headResp.Headers.TryGetValues("X-Received-Bytes", out var vals))
                long.TryParse(vals.FirstOrDefault(), out resumeOffset);
        }
        catch { /* HEAD 失败则从头上传 */ }
        // P3: 可压缩类型且非续传时 gzip 压缩上传
        bool useGzip = resumeOffset == 0 && IsCompressible(filePath) && len > 0;
        string? tmpGz = null;
        Stream uploadStream;
        long sendLen;
        if (useGzip)
        {
            tmpGz = Path.GetTempFileName();
            await using (var gzOut = new GZipStream(File.Create(tmpGz), CompressionLevel.Optimal))
                await using (var src = File.OpenRead(filePath))
                    await src.CopyToAsync(gzOut, ct);
            uploadStream = File.OpenRead(tmpGz);
            sendLen = new FileInfo(tmpGz).Length;
        }
        else
        {
            uploadStream = File.OpenRead(filePath);
            if (resumeOffset > 0) uploadStream.Seek(resumeOffset, SeekOrigin.Begin); // P2: 跳过已传部分
            sendLen = len - resumeOffset;
        }
        try
        {
            var content = new StreamContent(uploadStream) { Headers = { ContentLength = sendLen } };
            content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
            var wrapped = new ProgressContent(content, sendLen, onProgress, ct, resumeOffset, useGzip ? sendLen : len); // P3: gzip 时 fullTotal=压缩大小
            var req = new HttpRequestMessage(HttpMethod.Post,
                $"https://{ip}:{port}{Protocol.PathUpload}?sessionId={sessionId}&fileId={fileId}&token={token}")
            { Content = wrapped };
            req.Headers.TryAddWithoutValidation("X-FileName", name);
            req.Headers.TryAddWithoutValidation("X-FileSize", len.ToString());
            if (resumeOffset > 0) req.Headers.TryAddWithoutValidation("Content-Range", $"bytes {resumeOffset}-{len - 1}/{len}");
            if (useGzip) req.Headers.TryAddWithoutValidation("Content-Encoding", "gzip"); // P3

            using var resp = await _http.SendAsync(req, ct);
            if (!resp.IsSuccessStatusCode)
            {
                var b = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
                throw new Exception(b?.Info ?? $"上传失败 ({resp.StatusCode})");
            }
        }
        finally
        {
            uploadStream.Dispose();
            if (tmpGz is not null) try { File.Delete(tmpGz); } catch { }
        }
    }

    /// <summary>P3: 判断文件类型是否值得 gzip 压缩。</summary>
    private static bool IsCompressible(string path)
    {
        var ext = Path.GetExtension(path).TrimStart('.').ToLowerInvariant();
        return ext is "txt" or "json" or "xml" or "csv" or "log" or "md" or "js" or "ts" or "java" or "kt"
            or "py" or "go" or "cs" or "c" or "cpp" or "h" or "rs" or "html" or "css" or "svg" or "rtf"
            or "doc" or "xls" or "ppt" or "yaml" or "yml" or "ini" or "cfg" or "conf" or "sh" or "bat" or "ps1";
    }

    /// <summary>发送完成或失败时通知对端取消/结束会话。</summary>
    public async Task CancelAsync(IPAddress ip, int port, string sendId)
    {
        try
        {
            var payload = new Protocol.CancelPayload { SendId = sendId };
            await _http.PostAsync($"https://{ip}:{port}{Protocol.PathCancel}",
                new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"));
        }
        catch { /* 忽略清理失败 */ }
    }

    /// <summary>发送文本消息（聊天气泡通道，不落盘）。发到 PathMessage，失败抛异常。</summary>
    public async Task SendMessageAsync(IPAddress ip, int port, Protocol.TextMessage msg)
    {
        using var resp = await _http.PostAsync($"https://{ip}:{port}{Protocol.PathMessage}",
            new StringContent(JsonSerializer.Serialize(msg), Encoding.UTF8, "application/json"));
        if (!resp.IsSuccessStatusCode)
        {
            var b = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
            throw new Exception(b?.Info ?? $"消息发送失败 ({resp.StatusCode})");
        }
    }

    /// <summary>发送撤回命令到对端（携带被撤回的 sendId）。</summary>
    public async Task SendRecallAsync(IPAddress ip, int port, string sendId)
    {
        try
        {
            var payload = new Protocol.RecallPayload { SendId = sendId };
            await _http.PostAsync($"https://{ip}:{port}{Protocol.PathRecall}",
                new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"));
        }
        catch { /* 忽略撤回送达失败 */ }
    }

    public void Dispose() => _http.Dispose();

    /// <summary>包装 Content 以实时上报上传字节进度（可响应取消令牌）。P2: 支持断点续传偏移量。</summary>
    private sealed class ProgressContent : HttpContent
    {
        private readonly HttpContent _inner;
        private readonly long _total;
        private readonly Action<double>? _onProgress;
        private readonly CancellationToken? _ct;
        private readonly long _baseOffset; // P2: 续传偏移量
        private readonly long _fullTotal;  // P2: 完整文件大小
        public ProgressContent(HttpContent inner, long total, Action<double>? onProgress, CancellationToken? ct = null, long baseOffset = 0, long fullTotal = 0)
        { _inner = inner; _total = Math.Max(1, total); _onProgress = onProgress; _ct = ct; _baseOffset = baseOffset; _fullTotal = Math.Max(1, fullTotal > 0 ? fullTotal : total); }
        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            var buffer = new byte[262144]; // 256KB
            using var inner = await _inner.ReadAsStreamAsync();
            long read = 0; int n;
            while (!_ct.HasValue || !_ct.Value.IsCancellationRequested)
            {
                n = await inner.ReadAsync(buffer);
                if (n <= 0) break;
                await stream.WriteAsync(buffer.AsMemory(0, n));
                read += n;
                _onProgress?.Invoke((double)(_baseOffset + read) / _fullTotal); // P2: 进度含偏移量
            }
            if (_ct.HasValue && _ct.Value.IsCancellationRequested)
                throw new OperationCanceledException(_ct.Value);
        }
        protected override bool TryComputeLength(out long length)
        { length = _total; return true; }
        protected override void Dispose(bool disposing)
        { if (disposing) _inner.Dispose(); base.Dispose(disposing); }
    }
}