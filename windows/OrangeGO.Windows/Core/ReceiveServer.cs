using System.IO;
using System.Net;
using System.Text;
using System.Text.Json;
using System.Web;

namespace OrangeGO.Windows.Core;

/// <summary>
/// HTTP 传输服务器（接收端）。监听 53317，处理 init / file / cancel。
/// 协议见 OrangeGO/PROTOCOL.md「二、传输」。
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
        /// <summary>会话创建时刻，用于超时回收。</summary>
        public DateTime CreatedAt { get; init; } = DateTime.UtcNow;
    }

    private readonly HttpListener _listener = new();
    private readonly CancellationTokenSource _cts = new();
    private readonly Dictionary<string, ReceiveSession> _sessions = new(); // sendId → session
    private readonly object _lock = new();

    // 会话最长存活时间：超时未完成即回收，避免字典无限增长
    private static readonly TimeSpan SessionTtl = TimeSpan.FromMinutes(15);

    /// <summary>收到新发送请求，等待 UI 决定。返回保存目录；返回 null/空 = 拒绝。</summary>
    public Func<ReceiveSession, Task<string?>>? OnIncoming { get; set; }

    /// <summary>收到文本消息（聊天气泡）。不落盘、不建会话，直接回 ok。</summary>
    public Func<Protocol.TextMessage, Task>? OnMessage { get; set; }

    public void Start(int port = Protocol.Port, string host = "")
    {
        _listener.Prefixes.Clear();
        if (string.IsNullOrWhiteSpace(host))
        {
            // 枚举本机非回环 IPv4，以"具体 IP 前缀"逐个绑定。
            // HttpListener 绑定通配前缀 http://*:port/ 需要管理员权限（URL 保留/ACL）；
            // 绑定具体本机 IP 则免管理员（LocalSend 用 TcpListener 监听普通端口，同样免权限）。
            var ips = EnumerateLocalIPv4().Where(i => i != "127.0.0.1" && i != "0.0.0.0").ToList();
            foreach (var ip in ips) _listener.Prefixes.Add($"http://{ip}:{port}/");
            // 同时允许本机回环访问
            _listener.Prefixes.Add($"http://localhost:{port}/");
            if (_listener.Prefixes.Count == 0) _listener.Prefixes.Add($"http://*:{port}/");
        }
        else
        {
            _listener.Prefixes.Add($"http://{host}:{port}/");
        }
        _listener.Start();
        _ = Task.Run(ServeLoopAsync);
        _ = Task.Run(ReapLoopAsync);
    }

    /// <summary>枚举本机所有非回环 IPv4 地址（供 HttpListener 具体 IP 绑定，规避通配符需管理员）。</summary>
    private static IEnumerable<string> EnumerateLocalIPv4()
    {
        try
        {
            return System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces()
                .Where(n => n.OperationalStatus == System.Net.NetworkInformation.OperationalStatus.Up)
                .SelectMany(n => n.GetIPProperties().UnicastAddresses)
                .Select(a => a.Address)
                .Where(a => a.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork
                            && !System.Net.IPAddress.IsLoopback(a))
                .Select(a => a.ToString())
                .Distinct();
        }
        catch { return Enumerable.Empty<string>(); }
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

    private async Task ServeLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            HttpListenerContext ctx;
            try { ctx = await _listener.GetContextAsync(); }
            catch { break; }
            _ = Task.Run(() => RouteAsync(ctx));
        }
    }

    private async Task RouteAsync(HttpListenerContext ctx)
    {
        try
        {
            var path = ctx.Request.Url?.AbsolutePath ?? "/";
            var method = ctx.Request.HttpMethod;

            if (method == "POST" && (path == Protocol.PathPrepare || path == Protocol.PathInit)) await HandleInitAsync(ctx);
            else if (method == "POST" && (path == Protocol.PathUpload || path == Protocol.PathFile)) await HandleFileAsync(ctx);
            else if (method == "POST" && path == Protocol.PathCancel) await HandleCancelAsync(ctx);
            else if (method == "POST" && path == Protocol.PathMessage) await HandleMessageAsync(ctx);
            else await WriteJsonAsync(ctx, 404, new Protocol.Response { MessageType = Protocol.Error, Info = "not found" });
        }
        catch (Exception ex)
        {
            try { await WriteJsonAsync(ctx, 500, new Protocol.Response { MessageType = Protocol.Error, Info = ex.Message }); }
            catch { }
        }
    }

    private async Task HandleInitAsync(HttpListenerContext ctx)
    {
        var payload = await ReadBodyAsync<Protocol.SendInitPayload>(ctx);
        if (payload is null || payload.DeviceId is null || payload.SendId is null)
        {
            await WriteJsonAsync(ctx, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }

        var token = Guid.NewGuid().ToString("N");
        ReceiveSession? session = null;

        lock (_lock)
        {
            // 同 sendId 重复 init → 复用
            if (_sessions.TryGetValue(payload.SendId, out var existing) && existing is not null)
            {
                session = existing;
            }
            else
            {
                session = new ReceiveSession
                {
                    SendId = payload.SendId,
                    Token = token,
                    Payload = payload,
                    FileCount = payload.TotalFiles,
                    TotalBytes = payload.TotalSize
                };
                // v2：为每个被接受文件生成独立 token；v1 无文件清单则空表（走单 Token 兼容）
                if (payload.Files is not null)
                {
                    foreach (var id in payload.Files.Keys)
                        session.FileTokens[id] = Guid.NewGuid().ToString("N");
                }
                _sessions[payload.SendId] = session;
            }
        }

        if (session is null) { await WriteJsonAsync(ctx, 500, new Protocol.Response { MessageType = Protocol.Error }); return; }

        // 向 UI 请求审批，决定保存目录（应用内接收卡片排队确认）
        var saveDir = OnIncoming is null ? null : await OnIncoming(session);
        if (string.IsNullOrEmpty(saveDir))
        {
            lock (_lock) { session.Rejected = true; _sessions.Remove(payload.SendId); }
            await WriteJsonAsync(ctx, 403, new Protocol.Response { MessageType = Protocol.Reject, SendId = payload.SendId, Info = "rejected by user" });
            return;
        }

        session.SaveDir = saveDir;
        session.Approved = true;

        // v2：返回 sessionId + files(fileId→token)；v1 退化为单 token
        if (session.FileTokens.Count > 0)
        {
            await WriteJsonAsync(ctx, 200, new Protocol.Response
            {
                MessageType = Protocol.Ok,
                SendId = session.SendId,
                SessionId = session.SendId,
                Files = session.FileTokens
            });
        }
        else
        {
            await WriteJsonAsync(ctx, 200, new Protocol.Response { MessageType = Protocol.Ok, SendId = session.SendId, Token = session.Token });
        }
    }

    private async Task HandleFileAsync(HttpListenerContext ctx)
    {
        var fileName = Uri.UnescapeDataString(ctx.Request.Headers["X-FileName"] ?? "");
        if (string.IsNullOrEmpty(fileName))
        {
            await WriteJsonAsync(ctx, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "no file name" });
            return;
        }

        var path = ctx.Request.Url?.AbsolutePath ?? "";
        ReceiveSession sess;
        string safeName;
        lock (_lock)
        {
            if (path == Protocol.PathUpload)
            {
                // v2：query 带 sessionId/fileId/token，逐文件 token 校验
                var q = System.Web.HttpUtility.ParseQueryString(ctx.Request.Url!.Query);
                var sid = q["sessionId"];
                var fid = q["fileId"];
                var ftok = q["token"];
                if (string.IsNullOrEmpty(sid) || string.IsNullOrEmpty(fid) || string.IsNullOrEmpty(ftok))
                {
                    _ = WriteJsonAsync(ctx, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "missing query params" });
                    return;
                }
                if (!_sessions.TryGetValue(sid, out var s) || s is null || s.Rejected || !s.Approved)
                {
                    _ = WriteJsonAsync(ctx, 401, new Protocol.Response { MessageType = Protocol.Expired, Info = "invalid token" });
                    return;
                }
                if (!s.FileTokens.TryGetValue(fid, out var fileToken) || fileToken != ftok)
                {
                    _ = WriteJsonAsync(ctx, 403, new Protocol.Response { MessageType = Protocol.Error, Info = "token mismatch ip" });
                    return;
                }
                sess = s;
            }
            else
            {
                // v1：Bearer 单 token 匹配任意批准会话
                var auth = ctx.Request.Headers["Authorization"] ?? "";
                var bearer = auth.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase) ? auth["Bearer ".Length..] : "";
                if (string.IsNullOrEmpty(bearer))
                {
                    _ = WriteJsonAsync(ctx, 401, new Protocol.Response { MessageType = Protocol.Expired });
                    return;
                }
                sess = _sessions.Values.FirstOrDefault(x => x.Token == bearer && x.Approved && !x.Rejected) ?? null!;
                if (sess is null)
                {
                    _ = WriteJsonAsync(ctx, 401, new Protocol.Response { MessageType = Protocol.Expired });
                    return;
                }
            }
            safeName = SafeFileName(fileName);
            if (string.IsNullOrEmpty(safeName))
            {
                _ = WriteJsonAsync(ctx, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "bad file name" });
                return;
            }
        }

        var dest = UniquePath(sess.SaveDir, safeName);
        Directory.CreateDirectory(sess.SaveDir);

        // 字节数校验：HttpListener 已解析 Content-Length；不足/超出则删半文件并报错
        var expected = ctx.Request.ContentLength64;
        long written = 0;
        using (var inStream = ctx.Request.InputStream)
        using (var outStream = File.Create(dest))
        {
            var buf = new byte[64 * 1024];
            int n;
            while ((n = inStream.Read(buf, 0, buf.Length)) > 0)
            {
                written += n;
                if (expected > 0 && written > expected)
                {
                    outStream.Dispose();
                    File.Delete(dest);
                    await WriteJsonAsync(ctx, 413, new Protocol.Response { MessageType = Protocol.Error, Info = "size too large" });
                    return;
                }
                outStream.Write(buf, 0, n);
            }
        }

        if (expected > 0 && written != expected)
        {
            try { File.Delete(dest); } catch { }
            await WriteJsonAsync(ctx, 500, new Protocol.Response { MessageType = Protocol.Error, Info = $"size mismatch expected={expected} actual={written}" });
            return;
        }

        await WriteJsonAsync(ctx, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    private async Task HandleCancelAsync(HttpListenerContext ctx)
    {
        var payload = await ReadBodyAsync<Protocol.CancelPayload>(ctx);
        lock (_lock)
        {
            if (payload is { SendId: not null } p && _sessions.TryGetValue(p.SendId, out var s))
            {
                s.Rejected = true;
                _sessions.Remove(p.SendId);
            }
        }
        await WriteJsonAsync(ctx, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

    /// <summary>处理文本消息命令：解析 JSON body → 触发回调 → 回 ok。不写盘、不建会话。</summary>
    private async Task HandleMessageAsync(HttpListenerContext ctx)
    {
        var payload = await ReadBodyAsync<Protocol.TextMessage>(ctx);
        if (payload is null)
        {
            await WriteJsonAsync(ctx, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "bad body" });
            return;
        }
        if (string.IsNullOrEmpty(payload.Content))
        {
            await WriteJsonAsync(ctx, 400, new Protocol.Response { MessageType = Protocol.Error, Info = "empty content" });
            return;
        }
        if (OnMessage is not null)
        {
            try { await OnMessage(payload); }
            catch { /* 回调失败不影响协议响应 */ }
        }
        await WriteJsonAsync(ctx, 200, new Protocol.Response { MessageType = Protocol.Ok });
    }

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

    private static async Task<T?> ReadBodyAsync<T>(HttpListenerContext ctx)
    {
        using var reader = new StreamReader(ctx.Request.InputStream, Encoding.UTF8);
        var text = await reader.ReadToEndAsync();
        try { return JsonSerializer.Deserialize<T>(text); }
        catch { return default; }
    }

    private static Task WriteJsonAsync(HttpListenerContext ctx, int status, object body)
    {
        ctx.Response.StatusCode = status;
        ctx.Response.ContentType = "application/json; charset=utf-8";
        var bytes = Encoding.UTF8.GetBytes(JsonSerializer.Serialize(body));
        ctx.Response.ContentLength64 = bytes.Length;
        ctx.Response.OutputStream.Write(bytes, 0, bytes.Length);
        ctx.Response.Close();
        return Task.CompletedTask;
    }

    public void Dispose()
    {
        _cts.Cancel();
        _listener.Stop();
        _listener.Close();
    }
}