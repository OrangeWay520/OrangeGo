using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>
/// HTTP 传输客户端（发送端）。按 PROTOCOL.md「二、传输」先 init 授权再逐文件上传。
/// </summary>
public sealed class SenderClient : IDisposable
{
    private readonly HttpClient _http;
    private readonly Func<Protocol.DeviceInfo> _identity;

    public SenderClient(Func<Protocol.DeviceInfo> identity, long timeoutMs = 30_000)
    {
        _identity = identity;
        _http = new HttpClient { Timeout = TimeSpan.FromMilliseconds(timeoutMs) };
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

        using var resp = await _http.PostAsync($"http://{ip}:{port}/api/v1/send/init",
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
        var req = new HttpRequestMessage(HttpMethod.Post, $"http://{ip}:{port}/api/v1/send/file")
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
    public async Task<Protocol.PrepareResult?> PrepareAsync(IPAddress ip, int port, string sendId, IReadOnlyList<string> filePaths,
        List<string>? fileIds = null)
    {
        long total = 0;
        var files = new Dictionary<string, Protocol.FileMeta>();
        foreach (var path in filePaths)
        {
            var len = new FileInfo(path).Length;
            total += len;
            var id = Guid.NewGuid().ToString("N");
            fileIds?.Add(id); // 与 filePaths 同序记录 id
            files[id] = new Protocol.FileMeta { Id = id, FileName = Path.GetFileName(path), Size = len };
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

        using var resp = await _http.PostAsync($"http://{ip}:{port}{Protocol.PathPrepare}",
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
        using var stream = File.OpenRead(filePath);
        var content = new StreamContent(stream) { Headers = { ContentLength = len } };
        content.Headers.ContentType = new MediaTypeHeaderValue("application/octet-stream");
        var wrapped = new ProgressContent(content, len, onProgress, ct);
        var req = new HttpRequestMessage(HttpMethod.Post,
            $"http://{ip}:{port}{Protocol.PathUpload}?sessionId={sessionId}&fileId={fileId}&token={token}")
        { Content = wrapped };
        req.Headers.TryAddWithoutValidation("X-FileName", name);
        req.Headers.TryAddWithoutValidation("X-FileSize", len.ToString());

        using var resp = await _http.SendAsync(req, ct);
        if (!resp.IsSuccessStatusCode)
        {
            var b = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
            throw new Exception(b?.Info ?? $"上传失败 ({resp.StatusCode})");
        }
    }

    /// <summary>发送完成或失败时通知对端取消/结束会话。</summary>
    public async Task CancelAsync(IPAddress ip, int port, string sendId)
    {
        try
        {
            var payload = new Protocol.CancelPayload { SendId = sendId };
            await _http.PostAsync($"http://{ip}:{port}{Protocol.PathCancel}",
                new StringContent(JsonSerializer.Serialize(payload), Encoding.UTF8, "application/json"));
        }
        catch { /* 忽略清理失败 */ }
    }

    /// <summary>发送文本消息（聊天气泡通道，不落盘）。发到 PathMessage，失败抛异常。</summary>
    public async Task SendMessageAsync(IPAddress ip, int port, Protocol.TextMessage msg)
    {
        using var resp = await _http.PostAsync($"http://{ip}:{port}{Protocol.PathMessage}",
            new StringContent(JsonSerializer.Serialize(msg), Encoding.UTF8, "application/json"));
        if (!resp.IsSuccessStatusCode)
        {
            var b = JsonSerializer.Deserialize<Protocol.Response>(await resp.Content.ReadAsStringAsync());
            throw new Exception(b?.Info ?? $"消息发送失败 ({resp.StatusCode})");
        }
    }

    public void Dispose() => _http.Dispose();

    /// <summary>包装 Content 以实时上报上传字节进度（可响应取消令牌）。</summary>
    private sealed class ProgressContent : HttpContent
    {
        private readonly HttpContent _inner;
        private readonly long _total;
        private readonly Action<double>? _onProgress;
        private readonly CancellationToken? _ct;
        public ProgressContent(HttpContent inner, long total, Action<double>? onProgress, CancellationToken? ct = null)
        { _inner = inner; _total = Math.Max(1, total); _onProgress = onProgress; _ct = ct; }
        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            var buffer = new byte[81920];
            using var inner = await _inner.ReadAsStreamAsync();
            long read = 0; int n;
            while (!_ct.HasValue || !_ct.Value.IsCancellationRequested)
            {
                n = await inner.ReadAsync(buffer);
                if (n <= 0) break;
                await stream.WriteAsync(buffer.AsMemory(0, n));
                read += n;
                _onProgress?.Invoke((double)read / _total);
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