using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;
using System.Web;

namespace OrangeGO.Windows.Core;

/// <summary>
/// HTTP 传输服务器（接收端）。监听 53317，处理 init(prepare) / file(upload) / cancel / message / recall。
/// 协议与 Android/Web 端对齐，见 OrangeGO/PROTOCOL.md「二、传输」。
///
/// 为什么用 TcpListener 而不是 HttpListener：
///   HttpListener 绑定任意前缀（含 localhost / 具体 IP）都需要 Windows URL ACL 预留或管理员权限，
///   非管理员下 Start() 会抛「拒绝访问」→ 表现为"监听 53317 端口，接收不可用"。
///   LocalSend 用 TcpListener 直接监听普通端口，无需任何 ACL/管理员权限即可接收。
///   这里改为同款策略：自解析 HTTP/1.1 报文（含大文件 body 流式落盘）。
/// </summary>
public sealed class ReceiveServer : IDisposable
{
    /// <summary>一个待处理/进行中的接收会话。</summary>
    public sealed class ReceiveSession
    {
        public required string SendId { get; init; }
        public required string Token { get; init; }
        public required Protocol.SendInitPayload Payload { get; init; }
        public string SaveDir { get; set; } = "";
        public bool Approved { get; set; }
        public bool Rejected { get; set; }
        /// <summary>v2：fileId → 文件专属 token。</summary>
        public Dictionary<string, string> FileTokens { get; set; } = new();
        /// <summary>已据 init 登记的预期文件数/字节。</summary>
        public int FileCount { get; init; }
        public long TotalBytes { get; init; }
        /// <summary>会话创建时刻，用于超时回收，避免字典无限增长。</summary>
        public DateTime CreatedAt { get; init; } = DateTime.UtcNow;
        /// <summary>P2: fileId → 断点续传半文件路径。</summary>
        public Dictionary<string, string> PartialFiles { get; } = new();
    }

    private readonly TcpListener _listener;
    private readonly CancellationTokenSource _cts = new();
    private readonly Dictionary<string, ReceiveSession> _sessions = new(); // sendId → session
    private readonly object _lock = new();

    // 会话最长存活时间：超时未完成即回收，避免字典无限增长
    private static readonly TimeSpan SessionTtl = TimeSpan.FromMinutes(15);

    /// <summary>收到新发送请求，等待 UI 决定。返回保存目录；返回 null/空 = 拒绝。</summary>
    public Func<ReceiveSession, Task<string?>>? OnIncoming { get; set; }

    /// <summary>收到文本消息（聊天气泡）。不落盘、不建会话，直接回 ok。</summary>
    public Func<Protocol.TextMessage, Task>? OnMessage { get; set; }

    /// <summary>收到撤回命令（携带被撤回的 sendId）。</summary>
    public Func<string, Task>? OnRecall { get; set; }

    /// <summary>某个文件成功落盘后回调：真正把接收结果推给 UI（会话、落盘路径、字节数）。</summary>
    public Func<ReceiveSession, string, long, Task>? OnFileReceived { get; set; }

    /// <summary>某文件开始落盘回调（dest、预期总字节）。用于在传输记录里展现实时进度。</summary>
    public Func<ReceiveSession, string, long, string?, Task>? OnFileStarted { get; set; }

    /// <summary>某文件落盘进度回调（dest、已写、总字节）。</summary>
    public Func<ReceiveSession, string, long, long, Task>? OnFileProgress { get; set; }

    /// <summary>对方取消发送（sendId）：UI 标"已取消" + 清理半文件。</summary>
    public Func<ReceiveSession, Task>? OnCanceled { get; set; }

    /// <summary>传输失败（会话、文件路径）：UI 标"失败" + 删半文件。</summary>
    public Func<ReceiveSession, string, Task>? OnFileFailed { get; set; }

    /// <summary>LocalSend 设备 POST /register 注册自己时回调（线程：socket 线程，UI 需自行调度）。</summary>
    public Action<LocalSendDevice>? OnLocalSendRegister { get; set; }

    /// <summary>本机展示名（LocalSend 设备表/Info 用）。</summary>
    public string LocalAlias { get; set; } = "OrangeGO";
    /// <summary>本机 TLS 证书 SHA-256 指纹（LocalSend info 曝光；明文阶段用稳定占位）。</summary>
    public string LocalFingerprint { get; set; } = "";
    /// <summary>本机设备型号描述。</summary>
    public string LocalDeviceModel { get; set; } = "橙子台式机 / OrangeGO Desktop";

    public ReceiveServer()
    {
        // 绑定所有接口（0.0.0.0）；TcpListener 绑定普通端口无需管理员/URLACL。
        _listener = new TcpListener(IPAddress.Any, Protocol.Port);
    }

    public void Start()
    {
        _listener.Start();
        _ = Task.Run(AcceptLoopAsync);
        _ = Task.Run(ReapLoopAsync);
    }

    private async Task AcceptLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            TcpClient client;
            try { client = await _listener.AcceptTcpClientAsync(_cts.Token); }
            catch { break; }
            _ = Task.Run(() => ProcessConnectionAsync(client));
        }
    }

    /// <summary>处理单个 TCP 连接：识别 TLS（LocalSend 客户端）或明文（OrangeGO 客户端），按需 SPI 解密后逐字节解析请求。</summary>
    private async Task ProcessConnectionAsync(TcpClient client)
    {
        try
        {
            using (client)
            using (var raw = client.GetStream())
            {
                // 对端卡住/中止时，底层读会在约 60s 后抛 IOException，避免接收循环永久挂起。
                client.ReceiveTimeout = 60_000;

                // P0-3: 全量 TLS + mTLS（出示服务端证书 + 要求客户端证书；信任所有自签客户端证书，
                // 加密+双向认证已防窃听/篡改，fingerprint pinning 由 P0-4 叠加防 MITM）。
                var ssl = new SslStream(raw, false, (_, _, _, _) => true);
                await ssl.AuthenticateAsServerAsync(LsCompat.ServerCertificate, true, System.Security.Authentication.SslProtocols.None, false);
                Stream app = ssl;

                var head = await ReadRequestHeadAsync(app);
                if (head is null) return; // 连接被对端关闭

                var remoteIp = (client.Client.RemoteEndPoint as IPEndPoint)?.Address;

                if (head.Method.Equals("GET", StringComparison.OrdinalIgnoreCase))
                {
                    // LocalSend 兼容：局域网探测 /info（明文或 TLS），用于被 iPhone 发现并推送文件
                    if (head.Path is LsCompat.PathInfoV1 or LsCompat.PathInfoV2)
                        await WriteRawAsync(app, 200, LsCompat.BuildInfoJson(LocalAlias, LocalFingerprint, LocalDeviceModel), "application/json; charset=utf-8");
                    else
                        await WriteJsonAsync(app, 404, new Protocol.Response { MessageType = Protocol.Error, Info = "not found" });
                    return;
                }

                // P2: HEAD on upload → 返回已接收字节数（供发送端断点续传）
                if (head.Method.Equals("HEAD", StringComparison.OrdinalIgnoreCase) &&
                    (head.Path == Protocol.PathUpload || head.Path == Protocol.PathFile))
                {
                    await HandleUploadHeadAsync(app, head.Query);
                    return;
                }

                if (!head.Method.Equals("POST", StringComparison.OrdinalIgnoreCase))
                {
                    await WriteJsonAsync(app, 405, new Protocol.Response { MessageType = Protocol.Error, Info = "method not allowed" });
                    return;
                }

                await RouteHttpRequestAsync(app, head.Path, head.Query, head.Headers, head.ContentLength, remoteIp);
            }
        }
        catch { /* 吞掉客户端断开等良性错误 */ }
    }

    private async Task RouteHttpRequestAsync(Stream stream, string path, string query,
        Dictionary<string, string> headers, long contentLength, IPAddress? remoteIp)
    {
        try
        {
            if (path == Protocol.PathPrepare || path == Protocol.PathInit)
                await HandleInitAsync(stream, contentLength);
            else if (path == Protocol.PathUpload || path == Protocol.PathFile)
                await HandleFileAsync(stream, path, query, headers, contentLength);
            else if (path == Protocol.PathCancel || path == LsCompat.PathCancel)
                await HandleCancelAsync(stream, contentLength, query);
            else if (path == Protocol.PathMessage)
                await HandleMessageAsync(stream, contentLength);
            else if (path == Protocol.PathRecall)
                await HandleRecallAsync(stream, contentLength);
            else if (path == LsCompat.PathRegister)
                await HandleLocalSendRegisterAsync(stream, contentLength, remoteIp);
            else
                await WriteJsonAsync(stream, 404, new Protocol.Response { MessageType = Protocol.Error, Info = "not found" });
        }
        catch (Exception ex)
        {
            try { await WriteJsonAsync(stream, 500, new Protocol.Response { MessageType = Protocol.Error, Info = ex.Message }); }
            catch { }
        }
    }

    /// <summary>
    /// LocalSend 发现协议核心：手机收到我们的组播通告后 POST /register 注册自己。
    /// 我们必须回 RegisterResponseDtoV2（camelCase，200），手机才会把 OrangeGO 加入它的设备列表。
    /// 同时借 register 得知对端设备（比等组播通告更及时），回调 UI 更新设备表。
    /// </summary>
    private async Task HandleLocalSendRegisterAsync(Stream stream, long contentLength, IPAddress? remoteIp)
    {
        var raw = await ReadBodyAsync(stream, contentLength);
        LsCompat.RegisterDto? dto = null;
        try { dto = JsonSerializer.Deserialize<LsCompat.RegisterDto>(raw); } catch { }
        LsDiscovery.LogDiag($"收到 register：ip={remoteIp} alias={dto?.Alias ?? "?"} fp={(dto?.Fingerprint ?? "?")[..Math.Min(8, (dto?.Fingerprint ?? "?").Length)]}");
        if (dto is not null && remoteIp is not null && OnLocalSendRegister is not null)
        {
            try
            {
                OnLocalSendRegister(new LocalSendDevice
                {
                    Fingerprint = dto.Fingerprint,
                    Alias = string.IsNullOrEmpty(dto.Alias) ? "LocalSend 设备" : dto.Alias,
                    DeviceModel = dto.DeviceModel ?? "",
                    DeviceType = dto.DeviceType ?? "mobile",
                    Ip = remoteIp,
                    Port = dto.Port > 0 ? dto.Port : Protocol.Port,
                    Protocol = string.IsNullOrEmpty(dto.Protocol) ? "https" : dto.Protocol,
                    LastSeen = DateTime.UtcNow
                });
            }
            catch { /* 回调失败不影响协议响应 */ }
        }
        await WriteRawAsync(stream, 200,
            LsCompat.BuildRegisterResponseJson(LocalAlias, LocalFingerprint, LocalDeviceModel),
            "application/json; charset=utf-8");
    }

    private async Task HandleInitAsync(Stream stream, long contentLength)
    {
        var raw = await ReadBodyAsync(stream, contentLength);
        if (string.IsNullOrWhiteSpace(raw))
        {
            await WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }
        // 兼容 LocalSend：prepare-upload 请求体根含 "info" 且 "files" 为数组
        if (IsLocalSendPrepare(raw))
            await HandleLocalSendPrepareAsync(stream, raw);
        else
            await HandleOrangeGOInitAsync(stream, raw);
    }

    private static bool IsLocalSendPrepare(string raw)
    {
        try
        {
            using var doc = JsonDocument.Parse(raw);
            var r = doc.RootElement;
            // LocalSend 的 files 是 Map（fileId→FileDto）；兼容老的数组形态
            return r.ValueKind == JsonValueKind.Object
                && r.TryGetProperty("info", out _)
                && r.TryGetProperty("files", out var f)
                && f.ValueKind is JsonValueKind.Object or JsonValueKind.Array;
        }
        catch { return false; }
    }

    /// <summary>LocalSend 设备（iPhone/安卓的 LocalSend App）的 prepare-upload：翻译成 OrangeGO 会话并回 LocalSend 格式。</summary>
    private async Task HandleLocalSendPrepareAsync(Stream stream, string raw)
    {
        LsCompat.PrepareUpload? req = null;
        try { req = JsonSerializer.Deserialize<LsCompat.PrepareUpload>(raw); }
        catch { }
        if (req is null)
        {
            await WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }
        var files = req.Files ?? new Dictionary<string, LsCompat.LocalSendFile>();
        if (files.Count == 0)
        {
            await WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }
        var alias = string.IsNullOrEmpty(req.Info?.Alias) ? "LocalSend 设备" : req.Info!.Alias;
        var sendId = Guid.NewGuid().ToString("N");
        var payload = new Protocol.SendInitPayload
        {
            MessageType = Protocol.SendInit,
            SendId = sendId,
            DeviceId = req.Info?.Fingerprint ?? "",
            Name = alias,
            TotalFiles = files.Count,
            TotalSize = files.Values.Sum(f => f.Size),
            Files = files.ToDictionary(
                kv => kv.Key,
                kv => new Protocol.FileMeta
                {
                    Id = kv.Value.Id,
                    FileName = kv.Value.FileName,
                    Size = kv.Value.Size,
                    FileType = string.IsNullOrEmpty(kv.Value.FileType) ? "application/octet-stream" : kv.Value.FileType!
                })
        };

        var session = CreateSession(payload);
        var saveDir = OnIncoming is null ? null : await OnIncoming(session);
        if (string.IsNullOrEmpty(saveDir))
        {
            lock (_lock) { session.Rejected = true; _sessions.Remove(sendId); }
            await WriteJsonAsync(stream, 403, new Protocol.Response { MessageType = Protocol.Reject, SendId = sendId, Info = "rejected by user" });
            return;
        }
        session.SaveDir = saveDir;
        session.Approved = true;
        // LocalSend 期望响应（PrepareUploadResponseDtoV2，camelCase）：{"sessionId": ..., "files": {"<fileId>": "<token>"}}
        await WriteRawAsync(stream, 200, JsonSerializer.Serialize(new { sessionId = sendId, files = session.FileTokens }), "application/json; charset=utf-8");
    }

    private async Task HandleOrangeGOInitAsync(Stream stream, string raw)
    {
        var payload = JsonSerializer.Deserialize<Protocol.SendInitPayload>(raw);
        if (payload is null || payload.DeviceId is null || payload.SendId is null)
        {
            await WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }

        var session = CreateSession(payload);
        if (session is null)
        {
            await WriteJsonAsync(stream, 500, new Protocol.Response { MessageType = Protocol.Error });
            return;
        }

        // 向 UI 请求审批，决定保存目录（应用内接收卡片排队确认）
        var saveDir = OnIncoming is null ? null : await OnIncoming(session);
        if (string.IsNullOrEmpty(saveDir))
        {
            lock (_lock) { session.Rejected = true; _sessions.Remove(payload.SendId); }
            await WriteJsonAsync(stream, 403, new Protocol.Response { MessageType = Protocol.Reject, SendId = payload.SendId, Info = "rejected by user" });
            return;
        }

        session.SaveDir = saveDir;
        session.Approved = true;

        // v2：返回 sessionId + files(fileId→token)；v1 退化为单 token
        if (session.FileTokens.Count > 0)
        {
            await WriteJsonAsync(stream, 200, new Protocol.Response
            {
                MessageType = Protocol.Ok,
                SendId = session.SendId,
                SessionId = session.SendId,
                Files = session.FileTokens
            });
        }
        else
        {
            await WriteJsonAsync(stream, 200, new Protocol.Response { MessageType = Protocol.Ok, SendId = session.SendId, Token = session.Token });
        }
    }

    /// <summary>创建或复用会话；为每个文件预生成 token。自动入表。</summary>
    private ReceiveSession CreateSession(Protocol.SendInitPayload payload)
    {
        var token = Guid.NewGuid().ToString("N");
        lock (_lock)
        {
            if (_sessions.TryGetValue(payload.SendId, out var existing)) return existing;
            var session = new ReceiveSession
            {
                SendId = payload.SendId,
                Token = token,
                Payload = payload,
                FileCount = payload.TotalFiles,
                TotalBytes = payload.TotalSize
            };
            if (payload.Files is not null)
            {
                foreach (var id in payload.Files.Keys)
                    session.FileTokens[id] = Guid.NewGuid().ToString("N");
            }
            _sessions[payload.SendId] = session;
            return session;
        }
    }

    /// <summary>P2: HEAD on upload → 返回已接收字节数（供发送端断点续传查询半文件偏移）。</summary>
    private async Task HandleUploadHeadAsync(Stream stream, string query)
    {
        var q = HttpUtility.ParseQueryString(query);
        var sid = q["sessionId"];
        var fid = q["fileId"];
        long received = 0;
        if (!string.IsNullOrEmpty(sid) && !string.IsNullOrEmpty(fid))
        {
            lock (_lock)
            {
                if (_sessions.TryGetValue(sid!, out var s) && s.PartialFiles.TryGetValue(fid!, out var partialPath))
                {
                    try { received = new FileInfo(partialPath).Length; } catch { }
                }
            }
        }
        var head = $"HTTP/1.1 200 OK\r\nX-Received-Bytes: {received}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
        var headBytes = Encoding.ASCII.GetBytes(head);
        await stream.WriteAsync(headBytes.AsMemory());
        await stream.FlushAsync();
    }

    private async Task HandleFileAsync(Stream stream, string path, string query,
        Dictionary<string, string> headers, long contentLength)
    {
        var fileName = Uri.UnescapeDataString(headers.TryGetValue("X-FileName", out var fn) ? fn : "");
        if (string.IsNullOrEmpty(fileName))
        {
            // LocalSend 客户端不带 X-FileName：按 query.fileId 从 prepare-upload 清单解析文件名
            var q = HttpUtility.ParseQueryString(query);
            var fid = q["fileId"];
            var sid = q["sessionId"];
            ReceiveSession? byMeta = null;
            lock (_lock)
            {
                if (!string.IsNullOrEmpty(sid) && _sessions.TryGetValue(sid, out var s0)) byMeta = s0;
                else if (!string.IsNullOrEmpty(fid)) byMeta = _sessions.Values.FirstOrDefault(x => x.FileTokens.ContainsKey(fid!));
            }
            if (byMeta?.Payload.Files is { } mf && !string.IsNullOrEmpty(fid) && mf.TryGetValue(fid, out var meta))
                fileName = meta.FileName;
        }
        if (string.IsNullOrEmpty(fileName))
        {
            await WriteJsonAsync(stream, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "no file name" });
            return;
        }

        ReceiveSession sess;
        string safeName;
        // 文件夹发送：filename 之外的相对子目录（可为空），以及由 fileId 解析出的最终文件名
        string? relDir = null;
        string finalName = "";
        string? relFid = null;
        lock (_lock)
        {
            if (path == Protocol.PathUpload)
            {
                // v2：query 带 sessionId/fileId/token，逐文件 token 校验
                var q = HttpUtility.ParseQueryString(query);
                var sid = q["sessionId"];
                var fid = q["fileId"];
                relFid = fid; // 供相对路径解析（仅 v2）
                var ftok = q["token"];
                if (string.IsNullOrEmpty(sid) || string.IsNullOrEmpty(fid) || string.IsNullOrEmpty(ftok))
                {
                    _ = WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "missing query params" });
                    return;
                }
                if (!_sessions.TryGetValue(sid, out var s) || s is null || s.Rejected || !s.Approved)
                {
                    _ = WriteJsonAsync(stream, 401, new Protocol.Response { MessageType = Protocol.Expired, Info = "invalid token" });
                    return;
                }
                if (!s.FileTokens.TryGetValue(fid, out var fileToken) || fileToken != ftok)
                {
                    _ = WriteJsonAsync(stream, 403, new Protocol.Response { MessageType = Protocol.Error, Info = "token mismatch" });
                    return;
                }
                sess = s;
            }
            else
            {
                // v1：Bearer 单 token 匹配任意批准会话
                var auth = headers.TryGetValue("Authorization", out var a) ? a : "";
                var bearer = auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? auth["Bearer ".Length..] : "";
                if (string.IsNullOrEmpty(bearer))
                {
                    _ = WriteJsonAsync(stream, 401, new Protocol.Response { MessageType = Protocol.Expired });
                    return;
                }
                sess = _sessions.Values.FirstOrDefault(x => x.Token == bearer && x.Approved && !x.Rejected) ?? null!;
                if (sess is null)
                {
                    _ = WriteJsonAsync(stream, 401, new Protocol.Response { MessageType = Protocol.Expired });
                    return;
                }
            }
            safeName = SafeFileName(fileName);
            if (string.IsNullOrEmpty(safeName))
            {
                _ = WriteJsonAsync(stream, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "bad file name" });
                return;
            }
            // 文件夹发送：若 init 清单中该 fileId 带 relativePath，则安全重建子目录结构（防路径穿越）
            if (relFid is not null && sess.Payload.Files is { } fmap && fmap.TryGetValue(relFid, out var meta)
                && !string.IsNullOrEmpty(meta.RelativePath))
            {
                var rel = ResolveSafeRelative(meta.RelativePath);
                if (rel is null)
                {
                    _ = WriteJsonAsync(stream, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "bad relative path" });
                    return;
                }
                relDir = Path.GetDirectoryName(rel);
                var leaf = Path.GetFileName(rel);
                if (!string.IsNullOrEmpty(leaf)) finalName = leaf;
            }
            else
            {
                finalName = safeName;
            }
        }

        var targetDir = sess.SaveDir;
        if (!string.IsNullOrEmpty(relDir)) targetDir = Path.Combine(sess.SaveDir, relDir);
        Directory.CreateDirectory(targetDir);
        // P2: 断点续传 — 解析 Content-Range 获取起始偏移，复用半文件追加写入
        long resumeOffset = 0;
        if (headers.TryGetValue("Content-Range", out var cr))
        {
            var m = System.Text.RegularExpressions.Regex.Match(cr, @"bytes (\d+)-");
            if (m.Success && long.TryParse(m.Groups[1].Value, out var off)) resumeOffset = off;
        }
        var dest = (resumeOffset > 0 && relFid is not null && sess.PartialFiles.TryGetValue(relFid, out var pp) && File.Exists(pp))
            ? pp : UniquePath(targetDir, finalName);

        long size;
        // LocalSend 客户端用流式上传（Transfer-Encoding: chunked，无 Content-Length）：
        // 期望大小取 prepare-upload 清单中该 fileId 的 size，供进度与批次气泡使用
        bool chunked = headers.TryGetValue("Transfer-Encoding", out var te) && te.Contains("chunked", StringComparison.OrdinalIgnoreCase);
        bool gzip = headers.TryGetValue("Content-Encoding", out var ce) && ce.Contains("gzip", StringComparison.OrdinalIgnoreCase); // P3: 提前检测供 expectedSize 用
        long expectedSize = (chunked || gzip) // P3: gzip 时 expectedSize 从元数据取原始大小（contentLength 是压缩大小）
            ? (sess.Payload.Files is { } m0 && relFid is not null && m0.TryGetValue(relFid, out var meta0) ? meta0.Size : 0)
            : contentLength;
        // 通知 UI 该文件开始接收（chunked 与定长两条路径都必须发，否则 LocalSend 多文件批次无法合并成集成气泡）
        if (OnFileStarted is not null)
        {
            string? thumb = (sess.Payload.Files is { } mf && relFid is not null && mf.TryGetValue(relFid, out var meta))
                ? meta.Thumb : null;
            try { await OnFileStarted(sess, dest, expectedSize, thumb); } catch { }
        }
        if (chunked)
        {
            var ok = await WriteChunkedToFileAsync(stream, dest, async written =>
            {
                if (OnFileProgress is not null)
                {
                    try { await OnFileProgress(sess, dest, written, expectedSize); } catch { }
                }
            }, () => sess.Rejected);
            if (!ok)
            {
                try { File.Delete(dest); } catch { }
                if (sess.Rejected && OnCanceled is not null) try { await OnCanceled(sess); } catch { }
                else if (OnFileFailed is not null) try { await OnFileFailed(sess, dest); } catch { }
                await WriteJsonAsync(stream, 500, new Protocol.Response { MessageType = Protocol.Error, Info = sess.Rejected ? "canceled" : "bad chunked body" });
                return;
            }
            size = new FileInfo(dest).Length;
        }
        else
        {

            long written = gzip ? 0 : resumeOffset; // P3: gzip 从 0 开始（不支持续传）
            var buf = new byte[64 * 1024];
            long remaining = contentLength;
            try
            {
                if (gzip)
                {
                    // P3: gzip 解压路径 — 从 GZipStream 读到 EOF，校验解压后大小
                    using var gzIn = new GZipStream(stream, CompressionMode.Decompress);
                    using var fsGz = new FileStream(dest, FileMode.Create, FileAccess.Write);
                    while (true)
                    {
                        if (sess.Rejected) { try { File.Delete(dest); } catch { } if (OnCanceled is not null) try { await OnCanceled(sess); } catch { } return; }
                        int n = await gzIn.ReadAsync(buf.AsMemory());
                        if (n <= 0) break;
                        fsGz.Write(buf, 0, n);
                        written += n;
                        if (OnFileProgress is not null) try { await OnFileProgress(sess, dest, written, expectedSize); } catch { }
                    }
                    if (expectedSize > 0 && written != expectedSize)
                    {
                        try { File.Delete(dest); } catch { }
                        await WriteJsonAsync(stream, 500, new Protocol.Response { MessageType = Protocol.Error, Info = $"size mismatch expected={expectedSize} actual={written}" });
                        return;
                    }
                }
                else
                {
                    using (var fs = new FileStream(dest, resumeOffset > 0 ? FileMode.Append : FileMode.Create, FileAccess.Write)) // P2: 续传时追加写入
                    {
                        while (remaining > 0)
                        {
                            if (sess.Rejected) { try { File.Delete(dest); } catch { } if (OnCanceled is not null) try { await OnCanceled(sess); } catch { } return; }
                            int n = await stream.ReadAsync(buf.AsMemory(0, (int)Math.Min(buf.Length, remaining)));
                            if (n <= 0) break;
                            fs.Write(buf, 0, n);
                            written += n;
                            remaining -= n;
                            if (OnFileProgress is not null)
                            {
                                try { await OnFileProgress(sess, dest, written, expectedSize); } catch { }
                            }
                        }
                    }
                    if (written - resumeOffset != contentLength)
                    {
                        if (written > resumeOffset && relFid is not null) sess.PartialFiles[relFid] = dest; // P2: 保留半文件
                        else try { File.Delete(dest); } catch { }
                        await WriteJsonAsync(stream, 500, new Protocol.Response { MessageType = Protocol.Error, Info = $"size mismatch expected={contentLength} actual={written - resumeOffset}" });
                        return;
                    }
                }
            }
            catch
            {
                // P2: 连接异常时保留半文件供续传
                if (written > resumeOffset && relFid is not null) sess.PartialFiles[relFid] = dest;
                else try { File.Delete(dest); } catch { }
                if (OnFileFailed is not null) try { await OnFileFailed(sess, dest); } catch { }
                throw;
            }
            size = written;
        }

        // 落盘成功后推给 UI 展示接收结果（否则文件写了却看不到）
        if (relFid is not null) sess.PartialFiles.Remove(relFid); // P2: 完成后清理半文件记录
        if (OnFileReceived is not null)
        {
            try { await OnFileReceived(sess, dest, size); }
            catch { /* 回调失败不影响协议响应 */ }
        }

        await WriteJsonAsync(stream, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    /// <summary>解析并写入 RFC-7230 chunked 编码的请求体到目标文件；progress 回调携带累计已写字节。</summary>
    private static async Task<bool> WriteChunkedToFileAsync(Stream stream, string dest, Func<long, Task>? progress = null, Func<bool>? isCanceled = null)
    {
        try
        {
            byte[] buf = new byte[64 * 1024];
            using var fs = new FileStream(dest, FileMode.Create, FileAccess.Write);
            var scratch = new byte[1];
            long total = 0;
            while (true)
            {
                if (isCanceled is not null && isCanceled()) return false;
                // 读 chunk 大小行（十六进制，直到 \n）
                var sb = new StringBuilder();
                while (true)
                {
                    int n = await stream.ReadAsync(scratch.AsMemory(0, 1));
                    if (n <= 0) return false;
                    char c = (char)scratch[0];
                    if (c == '\n') break;
                    if (c != '\r' && c != ' ') sb.Append(c);
                }
                var sizeStr = sb.ToString().Split(';', 2)[0].Trim();
                if (!long.TryParse(sizeStr, System.Globalization.NumberStyles.HexNumber, null, out long size))
                    return false;
                if (size == 0)
                {
                    // 末尾可能有 trailer，读到空行结束
                    while (true)
                    {
                        int n = await stream.ReadAsync(scratch.AsMemory(0, 1));
                        if (n <= 0) break;
                        if (scratch[0] == (byte)'\n')
                        {
                            // 上个字节若是 \n 前的 \r 会再读一行；简化：多读一行后结束
                            break;
                        }
                    }
                    await fs.FlushAsync();
                    return true;
                }

                long remaining = size;
                while (remaining > 0)
                {
                    if (isCanceled is not null && isCanceled()) return false;
                    int want = (int)Math.Min(buf.Length, remaining);
                    int n = await stream.ReadAsync(buf.AsMemory(0, want));
                    if (n <= 0) return false;
                    fs.Write(buf, 0, n);
                    remaining -= n;
                    total += n;
                    if (progress is not null) await progress(total);
                }

                // 每个 chunk 后跟 CRLF
                int c1 = await stream.ReadAsync(scratch.AsMemory(0, 1));
                if (c1 <= 0) return false;
                int c2 = await stream.ReadAsync(scratch.AsMemory(0, 1));
                if (c2 <= 0) return false;
            }
        }
        catch { return false; }
    }

    private async Task HandleCancelAsync(Stream stream, long contentLength, string? query)
    {
        // LocalSend 用 query 传 sessionId；OrangeGO 自家用 body.sendId
        var sid = string.IsNullOrEmpty(query) ? "" : HttpUtility.ParseQueryString(query)["sessionId"];
        if (string.IsNullOrEmpty(sid))
        {
            var payload = await ReadBodyJsonAsync<Protocol.CancelPayload>(stream, contentLength);
            sid = payload?.SendId ?? "";
        }
        ReceiveSession? canceled = null;
        lock (_lock)
        {
            if (!string.IsNullOrEmpty(sid) && _sessions.TryGetValue(sid, out var s))
            {
                s.Rejected = true;
                _sessions.Remove(sid);
                canceled = s;
            }
        }
        if (canceled is not null && OnCanceled is not null)
        {
            try { await OnCanceled(canceled); } catch { }
        }
        await WriteJsonAsync(stream, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    private async Task HandleMessageAsync(Stream stream, long contentLength)
    {
        var payload = await ReadBodyJsonAsync<Protocol.TextMessage>(stream, contentLength);
        if (payload is null || string.IsNullOrEmpty(payload.Content))
        {
            await WriteJsonAsync(stream, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }
        if (OnMessage is not null)
        {
            try { await OnMessage(payload); }
            catch { /* 回调失败不影响协议响应 */ }
        }
        await WriteJsonAsync(stream, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    private async Task HandleRecallAsync(Stream stream, long contentLength)
    {
        var body = await ReadBodyJsonAsync<Protocol.RecallPayload>(stream, contentLength);
        var sendId = body?.SendId ?? "";
        if (sendId.Length > 0 && OnRecall is not null)
        {
            try { await OnRecall(sendId); }
            catch { /* 回调失败不影响协议响应 */ }
        }
        await WriteJsonAsync(stream, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    private async Task ReapLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try { await Task.Delay(TimeSpan.FromMinutes(1), _cts.Token); }
            catch { break; }
            var cutoff = DateTime.UtcNow - SessionTtl;
            lock (_lock)
            {
                var stale = _sessions.Where(kv => kv.Value.CreatedAt < cutoff).Select(kv => kv.Key).ToList();
                foreach (var k in stale) _sessions.Remove(k);
            }
        }
    }

    // ============ HTTP 报文解析与响应 ============

    /// <summary>逐字节读取请求行 + 请求头，直到空行；请求体留在流中等 handler 读取。</summary>
    private static async Task<RequestHead?> ReadRequestHeadAsync(Stream stream)
    {
        var firstLine = await ReadHeadLineAsync(stream);
        if (string.IsNullOrEmpty(firstLine)) return null;
        var parts = firstLine.Split(' ');
        if (parts.Length < 2) return null;

        var pathQuery = parts[1];
        var qIdx = pathQuery.IndexOf('?');
        var path = qIdx >= 0 ? pathQuery[..qIdx] : pathQuery;
        var query = qIdx >= 0 ? pathQuery[(qIdx + 1)..] : "";

        var headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        long contentLength = 0;
        string? line;
        while (!string.IsNullOrEmpty(line = await ReadHeadLineAsync(stream)))
        {
            var colonIdx = line.IndexOf(':');
            if (colonIdx <= 0) continue;
            var key = line[..colonIdx].Trim();
            var val = line[(colonIdx + 1)..].Trim();
            headers[key] = val;
            if (key.Equals("Content-Length", StringComparison.OrdinalIgnoreCase)
                && long.TryParse(val, out var cl))
                contentLength = cl;
        }

        return new RequestHead(parts[0], path, query, headers, contentLength);
    }

    /// <summary>读取一行（到 \n 为止，去掉末尾 \r）。逐字节读以保证请求体不被吞噬。</summary>
    private static async Task<string?> ReadHeadLineAsync(Stream stream)
    {
        var sb = new StringBuilder();
        var one = new byte[1];
        while (true)
        {
            int n = await stream.ReadAsync(one.AsMemory(0, 1));
            if (n == 0) return sb.Length == 0 ? null : sb.ToString();
            if (one[0] == (byte)'\n')
            {
                if (sb.Length > 0 && sb[^1] == '\r') sb.Length--;
                return sb.ToString();
            }
            sb.Append((char)one[0]);
        }
    }

    /// <summary>读取 Content-Length 字节作为 JSON 反序列化。</summary>
    private static async Task<T?> ReadBodyJsonAsync<T>(Stream stream, long contentLength)
    {
        var text = await ReadBodyAsync(stream, contentLength);
        if (string.IsNullOrWhiteSpace(text)) return default;
        try { return JsonSerializer.Deserialize<T>(text!); }
        catch { return default; }
    }

    /// <summary>读取 Content-Length 字节并返回 UTF-8 文本。</summary>
    private static async Task<string?> ReadBodyAsync(Stream stream, long contentLength)
    {
        var ms = new MemoryStream();
        var buf = new byte[81920];
        long remaining = contentLength;
        while (remaining > 0)
        {
            int n = await stream.ReadAsync(buf.AsMemory(0, (int)Math.Min(buf.Length, remaining)));
            if (n <= 0) break;
            ms.Write(buf, 0, n);
            remaining -= n;
        }
        return Encoding.UTF8.GetString(ms.ToArray());
    }

    /// <summary>写入原始 JSON 字符串响应（LocalSend 场景需要自定义 body 而非强类型 Response）。</summary>
    private static async Task WriteRawAsync(Stream stream, int status, string body, string contentType)
    {
        var bodyBytes = Encoding.UTF8.GetBytes(body);
        var head = $"HTTP/1.1 {status} {ReasonPhrase(status)}\r\n" +
                   $"Content-Type: {contentType}\r\n" +
                   $"Content-Length: {bodyBytes.Length}\r\n" +
                   "Connection: close\r\n\r\n";
        var headBytes = Encoding.ASCII.GetBytes(head);
        await stream.WriteAsync(headBytes.AsMemory());
        await stream.WriteAsync(bodyBytes.AsMemory());
        await stream.FlushAsync();
    }

    private static async Task WriteJsonAsync(Stream stream, int status, object body)
    {
        var json = JsonSerializer.Serialize(body);
        var bodyBytes = Encoding.UTF8.GetBytes(json);
        var head = $"HTTP/1.1 {status} {ReasonPhrase(status)}\r\n" +
                   "Content-Type: application/json; charset=utf-8\r\n" +
                   $"Content-Length: {bodyBytes.Length}\r\n" +
                   "Connection: close\r\n\r\n";
        var headBytes = Encoding.ASCII.GetBytes(head);
        await stream.WriteAsync(headBytes.AsMemory());       // MemoryStream内的 byte[] 使用 AsMemory
        await stream.WriteAsync(bodyBytes.AsMemory());
        await stream.FlushAsync();
    }

    private static string ReasonPhrase(int status) => status switch
    {
        200 => "OK",
        204 => "No Content",
        400 => "Bad Request",
        401 => "Unauthorized",
        403 => "Forbidden",
        404 => "Not Found",
        405 => "Method Not Allowed",
        413 => "Payload Too Large",
        500 => "Internal Server Error",
        _ => "Unknown"
    };

    public static string SafeFileName(string name)
    {
        // 只取末尾路径段，剥离目录前缀，防路径穿越
        var last = (name ?? "").Replace('\\', '/');
        last = last[(last.LastIndexOf('/') + 1)..];
        foreach (var c in Path.GetInvalidFileNameChars()) last = last.Replace(c, '_');
        var cleaned = last.Trim().TrimEnd('.', ' ');
        if (string.IsNullOrWhiteSpace(cleaned) || cleaned is "." or "..") return "unnamed";
        return cleaned.Length > 240 ? cleaned[..240] : cleaned;
    }

    /// <summary>
    /// 把发送端传来的相对路径（`/` 分隔，含最终文件名）安全规范化为可安全拼入 SaveDir 的相对路径。
    /// 拒绝空段、`.`、`..`（防路径穿越）；每段逐非法字符清理。无法安全解析时返回 null。
    /// </summary>
    private static string? ResolveSafeRelative(string relPath)
    {
        var norm = (relPath ?? "").Replace('\\', '/').Trim('/');
        if (string.IsNullOrEmpty(norm)) return null;
        var segments = norm.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segments.Length == 0) return null;
        var cleaned = new List<string>(segments.Length);
        foreach (var seg in segments)
        {
            if (seg is "." or "..") return null; // 防穿越
            var c = seg;
            foreach (var ch in Path.GetInvalidFileNameChars()) c = c.Replace(ch, '_');
            c = c.Trim().TrimEnd('.', ' ');
            if (string.IsNullOrEmpty(c)) return null;
            cleaned.Add(c.Length > 200 ? c[..200] : c);
        }
        // 确保末段不是目录结尾（必须有文件名），拼接相对路径
        var dir = string.Join(Path.DirectorySeparatorChar, cleaned);
        if (cleaned[^1].EndsWith(".")) return null;
        return dir;
    }

    /// <summary>目标已存在时追加 (1)/(2)… 序号，避免覆盖已有文件。</summary>
    private static string UniquePath(string dir, string name)
    {
        var candidate = Path.Combine(dir, name);
        if (!File.Exists(candidate)) return candidate;
        var i = 1;
        while (true)
        {
            var stem = Path.GetFileNameWithoutExtension(name);
            var ext = Path.GetExtension(name);
            var next = Path.Combine(dir, $"{stem} ({i}){ext}");
            if (!File.Exists(next)) return next;
            i++;
        }
    }

    private sealed class RequestHead
    {
        public string Method;
        public string Path;
        public string Query;
        public Dictionary<string, string> Headers;
        public long ContentLength;
        public RequestHead(string method, string path, string query, Dictionary<string, string> headers, long contentLength)
        {
            Method = method; Path = path; Query = query; Headers = headers; ContentLength = contentLength;
        }
    }

    /// <summary>
    /// 把从底层流预读的首字节回填的最小推送流。用于 TCP 先读一个字节判断 TLS/明文，
    /// 再交给 SslStream 或 HTTP 解析器继续读——保证首字节不被吞掉。
    /// </summary>
    private sealed class PushbackStream : Stream
    {
        private readonly NetworkStream _inner;
        private byte? _pushed;

        public PushbackStream(NetworkStream inner, byte first) { _inner = inner; _pushed = first; }

        public override bool CanRead => true;
        public override bool CanSeek => false;
        public override bool CanWrite => true;
        public override long Length => throw new NotSupportedException();
        public override long Position { get => throw new NotSupportedException(); set => throw new NotSupportedException(); }

        public override int Read(byte[] buffer, int offset, int count)
        {
            if (_pushed is byte b)
            {
                _pushed = null;
                buffer[offset] = b;
                return 1;
            }
            return _inner.Read(buffer, offset, count);
        }

        public override int Read(Span<byte> buffer)
        {
            if (_pushed is byte b)
            {
                _pushed = null;
                buffer[0] = b;
                return 1;
            }
            return _inner.Read(buffer);
        }

        public override async ValueTask<int> ReadAsync(Memory<byte> buffer, CancellationToken ct = default)
        {
            if (_pushed is byte b)
            {
                _pushed = null;
                if (buffer.Length > 0) buffer.Span[0] = b;
                return 1;
            }
            return await _inner.ReadAsync(buffer, ct);
        }

        public override void Write(byte[] buffer, int offset, int count) => _inner.Write(buffer, offset, count);
        public override void Write(ReadOnlySpan<byte> buffer) => _inner.Write(buffer);
        public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken ct = default) => _inner.WriteAsync(buffer, ct);
        public override void Flush() => _inner.Flush();
        public override Task FlushAsync(CancellationToken ct) => _inner.FlushAsync(ct);
        public override long Seek(long offset, SeekOrigin origin) => throw new NotSupportedException();
        public override void SetLength(long value) => throw new NotSupportedException();
    }

    public void Dispose()
    {
        _cts.Cancel();
        try { _listener.Stop(); } catch { }
    }
}