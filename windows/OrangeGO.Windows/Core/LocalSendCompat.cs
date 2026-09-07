using System.IO;
using System.Net;
using System.Net.Http;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace OrangeGO.Windows.Core;

/// <summary>
/// LocalSend 互操作：接送 iOS/安卓的 LocalSend 报文（v2 明文实现）。
/// 与 OrangeGO 自研协议共用 53317 端口与「prepare-upload → upload」会话模型，
/// 只因 LocalSend 的报文/字段命名不同而需做一次「翻译」。
/// 见 PROTOCOL 兼容说明。
/// </summary>
public static class LsCompat
{
    public const string PathInfoV1 = "/api/localsend/v1/info";
    public const string PathInfoV2 = "/api/localsend/v2/info";
    public const string PathRegister = "/api/localsend/v2/register";
    public const string PathPrepareUpload = "/api/localsend/v2/prepare-upload";
    public const string PathUpload = "/api/localsend/v2/upload";   // 同 OrangeGO 现即 PathUpload
    public const string PathCancel = "/api/localsend/v2/cancel";

    /// <summary>
    /// LocalSend /info 响应（v2 DTO 为 camelCase：alias/version/deviceModel/deviceType/fingerprint/download）。
    /// 字段名必须与官方 dto_v2.rs 的 serde rename_all=camelCase 完全一致，否则对端反序列化失败。
    /// </summary>
    public static string BuildInfoJson(string alias, string fingerprint, string deviceModel)
    {
        var obj = new
        {
            alias,
            version = "2.0",
            deviceModel,
            deviceType = "desktop",
            fingerprint,
            download = false
        };
        return JsonSerializer.Serialize(obj);
    }

    /// <summary>
    /// LocalSend register 响应（RegisterResponseDtoV2，camelCase）。
    /// 手机收到我们的组播通告后会 POST /register；只有本响应可被正确反序列化，手机才会把我们加入设备列表。
    /// </summary>
    public static string BuildRegisterResponseJson(string alias, string fingerprint, string deviceModel)
    {
        var obj = new
        {
            alias,
            version = "2.0",
            deviceModel,
            deviceType = "desktop",
            fingerprint,
            download = false
        };
        return JsonSerializer.Serialize(obj);
    }

    // ============ TLS 自签证书：LocalSend 要求 HTTPS（TLS），对齐其安全模型 ============
    // 证书持久化到 %AppData%\OrangeGO\*.pfx，跨重启指纹稳定（TOFU 指纹钉扎才不会误判 MITM）。

    private static X509Certificate2? _serverCert;
    private static string? _serverFingerprint;
    private static string CertDir => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO");

    /// <summary>
    /// 自签 TLS 服务器证书（用于 53317 上为 LocalSend/OrangeGO 提供 HTTPS）。
    /// 持久化到 %AppData%\OrangeGO\server.pfx，跨重启指纹稳定。
    /// </summary>
    public static X509Certificate2 ServerCertificate
    {
        get
        {
            if (_serverCert is not null) return _serverCert;
            var pfxPath = Path.Combine(CertDir, "server.pfx");
            try
            {
                if (File.Exists(pfxPath))
                {
                    _serverCert = new X509Certificate2(pfxPath, "og", X509KeyStorageFlags.Exportable);
                    _serverFingerprint = Convert.ToHexString(SHA256.HashData(_serverCert.RawData));
                    return _serverCert;
                }
            }
            catch { /* 损坏则重新生成 */ }
            using var ecdsa = ECDsa.Create(ECCurve.NamedCurves.nistP256);
            var req = new CertificateRequest("CN=OrangeGO", ecdsa, HashAlgorithmName.SHA256);
            req.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
            req.CertificateExtensions.Add(new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment, false));
            req.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
                new OidCollection { new Oid("1.3.6.1.5.5.7.3.1") }, false)); // serverAuth
            var cert = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddDays(3650));
            var pfx = cert.Export(X509ContentType.Pfx, "og");
            try { Directory.CreateDirectory(CertDir); File.WriteAllBytes(pfxPath, pfx); } catch { }
            _serverCert = new X509Certificate2(pfx, "og", X509KeyStorageFlags.Exportable);
            _serverFingerprint = Convert.ToHexString(SHA256.HashData(_serverCert.RawData));
            return _serverCert;
        }
    }

    /// <summary>进程内稳定指纹（与 ServerCertificate 同源，避免每次新生成）。</summary>
    public static string ServerFingerprint
    {
        get { _ = ServerCertificate; return _serverFingerprint!; }
    }

    private static X509Certificate2? _clientCert;

    /// <summary>
    /// 向 LocalSend/OrangeGO HTTPS 目标出示的客户端身份证书。
    /// 镜像官方设备身份：RSA-2048 自签、CN=LocalSend User + clientAuth EKU。
    /// 持久化到 %AppData%\OrangeGO\client.pfx，跨重启稳定。
    /// 必须经 PFX 往返导入使私钥真正附着：Windows SChannel 无法把「瞬时 CNG 密钥」的证书用作 mTLS 客户端凭证，
    /// 否则握手报 SEC_E_UNKNOWN_CREDENTIALS（实测根因）。
    /// </summary>
    public static X509Certificate2 ClientCertificate
    {
        get
        {
            if (_clientCert is not null) return _clientCert;
            var pfxPath = Path.Combine(CertDir, "client.pfx");
            try
            {
                if (File.Exists(pfxPath))
                {
                    _clientCert = new X509Certificate2(pfxPath, "og", X509KeyStorageFlags.Exportable);
                    return _clientCert;
                }
            }
            catch { /* 损坏则重新生成 */ }
            using var rsa = RSA.Create(2048);
            var req = new CertificateRequest("CN=LocalSend User", rsa, HashAlgorithmName.SHA256, RSASignaturePadding.Pkcs1);
            req.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
                new OidCollection { new Oid("1.3.6.1.5.5.7.3.2") }, false)); // clientAuth
            using var raw = req.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(2000));
            var pfx = raw.Export(X509ContentType.Pfx, "og");
            try { Directory.CreateDirectory(CertDir); File.WriteAllBytes(pfxPath, pfx); } catch { }
            _clientCert = new X509Certificate2(pfx, "og", X509KeyStorageFlags.Exportable);
            return _clientCert;
        }
    }

    /// <summary>LocalSend prepare-upload 请求体（{info, files:{fileId→FileDto}}）。files 是 Map 而非数组。</summary>
    public sealed class PrepareUpload
    {
        [JsonPropertyName("info")] public RegisterDto? Info { get; set; }
        [JsonPropertyName("files")] public Dictionary<string, LocalSendFile>? Files { get; set; }
    }

    /// <summary>LocalSend register/prepare-upload 请求中的设备身份（RegisterDtoV2，camelCase）。</summary>
    public sealed class RegisterDto
    {
        [JsonPropertyName("alias")] public string Alias { get; set; } = "";
        [JsonPropertyName("version")] public string Version { get; set; } = "";
        [JsonPropertyName("deviceModel")] public string? DeviceModel { get; set; }
        [JsonPropertyName("deviceType")] public string? DeviceType { get; set; }
        [JsonPropertyName("fingerprint")] public string Fingerprint { get; set; } = "";
        [JsonPropertyName("port")] public int Port { get; set; } = global::OrangeGO.Windows.Core.Protocol.Port;
        [JsonPropertyName("protocol")] public string Protocol { get; set; } = "https";
        [JsonPropertyName("download")] public bool Download { get; set; }
    }

    public sealed class LocalSendFile
    {
        [JsonPropertyName("id")] public required string Id { get; set; }
        [JsonPropertyName("fileName")] public string FileName { get; set; } = "";
        [JsonPropertyName("size")] public long Size { get; set; }
        [JsonPropertyName("fileType")] public string? FileType { get; set; }
    }

    /// <summary>在 53317 明文端口上探测一个 IP 是否为 LocalSend 设备（能回 /api/localsend/v1/info 视为 LocalSend 目标）。</summary>
    public static async Task<LocalSendDevice?> ProbeAsync(IPAddress ip, int port, int timeoutMs = 400)
    {
        try
        {
            using var client = new HttpClient(new HttpClientHandler
            {
                AutomaticDecompression = System.Net.DecompressionMethods.None,
                AllowAutoRedirect = false
            });
            client.Timeout = TimeSpan.FromMilliseconds(timeoutMs);
            client.DefaultRequestHeaders.UserAgent.ParseAdd("OrangeGO/2.0");
            var resp = await client.GetAsync($"http://{ip}:{port}{PathInfoV1}");
            if (!resp.IsSuccessStatusCode) return null;
            var text = await resp.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(text);
            var root = doc.RootElement;
            if (root.TryGetProperty("fingerprint", out var fp) && fp.ValueKind == JsonValueKind.String)
            {
                return new LocalSendDevice
                {
                    Fingerprint = fp.GetString() ?? "",
                    Alias = root.TryGetProperty("alias", out var a) ? a.GetString() ?? "" : "",
                    DeviceModel = ReadCamelOrSnake(root, "deviceModel", "device_model"),
                    DeviceType = ReadCamelOrSnake(root, "deviceType", "device_type") ?? "desktop",
                    Ip = ip,
                    Port = port
                };
            }
            return null;
        }
        catch { return null; }
    }

    private static string? ReadCamelOrSnake(JsonElement root, string camel, string snake)
        => root.TryGetProperty(camel, out var c) && c.ValueKind == JsonValueKind.String ? c.GetString()
         : root.TryGetProperty(snake, out var s) && s.ValueKind == JsonValueKind.String ? s.GetString()
         : null;
}

/// <summary>LocalSend 设备快照（由局域网探测得到）。</summary>
public sealed class LocalSendDevice
{
    public string Fingerprint { get; set; } = "";
    public string Alias { get; set; } = "";
    public string DeviceModel { get; set; } = "";
    public string DeviceType { get; set; } = "desktop";
    public IPAddress Ip { get; set; } = IPAddress.None;
    public int Port { get; set; } = global::OrangeGO.Windows.Core.Protocol.Port;
    /// <summary>对端声明的服务协议（"https"=TLS 自签证书 / "http"=明文），决定发送端用哪种客户端。</summary>
    public string Protocol { get; set; } = "https";
    public DateTime LastSeen { get; set; } = DateTime.UtcNow;
}

/// <summary>
/// LocalSend 官方组播发现（224.0.0.167:53317）的静态辅助。
/// 关键约束：发现监听必须复用 OrangeGO DiscoReader 已绑定 53317 的 UDP socket（见 DeviceDiscovery），
/// 绝不能再开一个 socket 绑定同一端口，否则启动即抛 SocketException(10048) 导致窗口无法打开。
/// 发送用独立临时 socket（无需绑定 53317），逐网卡强制指定出口。
/// </summary>
public static class LsDiscovery
{
    public const string MulticastIp = "224.0.0.167";
    public const int MulticastPort = 53317;

    /// <summary>构建 LocalSend 组播存在报文（camelCase，与官方 DiscoveryClient 对齐）。</summary>
    public static string BuildPresenceJson(string alias, string fingerprint, string deviceModel, int port)
    {
        var obj = new
        {
            alias,
            version = "2.0",
            deviceModel,
            deviceType = "desktop",
            fingerprint,
            port,
            protocol = "https",
            download = false
        };
        return JsonSerializer.Serialize(obj);
    }

    /// <summary>解析 LocalSend 组播存在报文；非映射格式返回 null。</summary>
    public static LocalSendDevice? ParsePresence(string json)
    {
        try
        {
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            if (root.ValueKind != JsonValueKind.Object) return null;
            if (!root.TryGetProperty("fingerprint", out var fp) || fp.ValueKind != JsonValueKind.String) return null;
            var port = MulticastPort;
            if (root.TryGetProperty("port", out var portEl) && portEl.TryGetInt32(out var pv)) port = pv;
            return new LocalSendDevice
            {
                Fingerprint = fp.GetString() ?? "",
                Alias = root.TryGetProperty("alias", out var a) ? a.GetString() ?? "" : "",
                DeviceModel = root.TryGetProperty("deviceModel", out var dm) ? dm.GetString() ?? "" : "",
                DeviceType = root.TryGetProperty("deviceType", out var dt) ? dt.GetString() ?? "desktop" : "desktop",
                Protocol = root.TryGetProperty("protocol", out var pr) && pr.GetString() == "http" ? "http" : "https",
                Port = port
            };
        }
        catch { return null; }
    }

    /// <summary>本机非回环 IPv4 地址列表（字符串形式）。</summary>
    public static IReadOnlyList<string> GetLocalIpv4()
    {
        var list = new List<string>();
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
            foreach (var ip in ni.GetIPProperties().UnicastAddresses)
            {
                var a = ip.Address;
                if (a.AddressFamily != AddressFamily.InterNetwork) continue;
                var s = a.ToString();
                if (s.StartsWith("169.254.", StringComparison.OrdinalIgnoreCase)) continue;
                if (!s.StartsWith("127.")) list.Add(s);
            }
        }
        return list;
    }

    /// <summary>LocalSend 兼容层诊断日志：追加到 %APPDATA%\OrangeGO\localsend.log（仅供排查发现/传输问题）。</summary>
    public static void LogDiag(string message)
    {
        try
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO");
            Directory.CreateDirectory(dir);
            File.AppendAllText(Path.Combine(dir, "localsend.log"),
                $"{DateTime.Now:yyyy-MM-dd HH:mm:ss} {message}{Environment.NewLine}");
        }
        catch { }
    }
}

/// <summary>
/// LocalSend 发送客户端：向 iOS/安卓的 LocalSend App 推文件。
/// 安全模型与 LocalSend 官方对齐：
///   - HTTPS 目标：TLS 握手时出示本机自签客户端证书（LocalSend 服务端仅要求「证书有效」，不校验签发链），
///     并按对端通告的 SHA-256 指纹钉扎校验服务端证书，防局域网中间人；
///   - PIN：对端开启 PIN 时 prepare-upload 回 401，携带 ?pin= 重试；
///   - 会话：prepare-upload(200) → 逐文件 upload?sessionId&fileId&token → 200；对端忙回 409、拒绝回 403。
/// </summary>
public sealed class LsSender : IDisposable
{
    /// <summary>携带 HTTP 状态码的发送失败（UI 据此区分 PIN/拒绝/忙等场景）。</summary>
    public sealed class LsSendException : Exception
    {
        public int StatusCode { get; }
        public LsSendException(int status, string message) : base(message) { StatusCode = status; }
    }

    public sealed class PrepareResult
    {
        public string SessionId { get; set; } = "";
        public Dictionary<string, string> Tokens { get; set; } = new();
    }

    private readonly string _alias;
    private readonly string _deviceModel;
    private readonly int _port;
    /// <summary>按 目标ip:port:指纹 缓存的 HttpClient（指纹校验闭包按目标固定，连接可复用）。</summary>
    private readonly Dictionary<string, HttpClient> _clients = new();

    public LsSender(string alias, string deviceModel, int port)
    {
        _alias = alias;
        _deviceModel = deviceModel;
        _port = port;
    }

    private HttpClient ClientFor(LocalSendDevice target)
    {
        var key = $"{target.Ip}:{target.Port}:{target.Fingerprint}";
        if (_clients.TryGetValue(key, out var cached)) return cached;
        var handler = new SocketsHttpHandler
        {
            ConnectTimeout = TimeSpan.FromSeconds(8),
            SslOptions = new System.Net.Security.SslClientAuthenticationOptions
            {
                // 出示 PFX 往返导入的自签客户端证书（LocalSend HTTPS 服务端强制 mTLS）。
                // 必须用 ClientCertificate（密钥附着/可导出），瞬时 CNG 密钥证书在 Windows SChannel 上报
                // SEC_E_UNKNOWN_CREDENTIALS，握手失败。
                ClientCertificates = new X509CertificateCollection { LsCompat.ClientCertificate },
                // 指纹钉扎：与对端通告指纹不符则拒绝（对齐 LocalSend 的 PinnedServerCertVerifier）
                RemoteCertificateValidationCallback = (_, cert, _, _) =>
                {
                    if (string.IsNullOrEmpty(target.Fingerprint) || cert is not X509Certificate2 c2) return true;
                    var sha = Convert.ToHexString(c2.GetCertHash(HashAlgorithmName.SHA256)).ToLowerInvariant();
                    var expect = target.Fingerprint.Replace(":", "").Replace(" ", "").ToLowerInvariant();
                    return sha == expect;
                }
            }
        };
        var client = new HttpClient(handler) { Timeout = Timeout.InfiniteTimeSpan };
        _clients[key] = client;
        return client;
    }

    /// <summary>prepare-upload：携带文件清单请求对端授权；返回 sessionId + fileId→token。</summary>
    public async Task<PrepareResult> PrepareUploadAsync(LocalSendDevice target, IReadOnlyList<string> filePaths,
        List<string>? fileIds = null, string? pin = null)
    {
        fileIds ??= new List<string>();
        var files = new Dictionary<string, object>();
        for (int i = 0; i < filePaths.Count; i++)
        {
            var id = Guid.NewGuid().ToString("N");
            fileIds.Add(id); // 与 filePaths 同序
            var p = filePaths[i];
            files[id] = new
            {
                id,
                fileName = Path.GetFileName(p),
                size = new FileInfo(p).Length,
                fileType = MimeOf(p)
            };
        }
        var body = JsonSerializer.Serialize(new
        {
            info = new
            {
                alias = _alias,
                version = "2.2",
                deviceModel = _deviceModel,
                deviceType = "desktop",
                fingerprint = LsCompat.ServerFingerprint,
                port = _port,
                protocol = "https",
                download = false
            },
            files
        });
        var url = $"{target.Protocol}://{target.Ip}:{target.Port}{LsCompat.PathPrepareUpload}";
        if (!string.IsNullOrEmpty(pin)) url += $"?pin={Uri.EscapeDataString(pin)}";

        using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(120));
        using var resp = await ClientFor(target).PostAsync(url,
            new StringContent(body, Encoding.UTF8, "application/json"), cts.Token);
        return (int)resp.StatusCode switch
        {
            204 => new PrepareResult(), // 对端判定无需传输（如文件已存在被全跳过）
            401 => throw new LsSendException(401, "对方要求 PIN"),
            403 => throw new LsSendException(403, "对方拒绝了本次发送"),
            409 => throw new LsSendException(409, "对方忙（已有进行中的接收会话）"),
            429 => throw new LsSendException(429, "PIN 尝试次数过多，请稍后再试"),
            _ when !resp.IsSuccessStatusCode => throw new LsSendException((int)resp.StatusCode,
                $"prepare 失败 ({(int)resp.StatusCode})"),
            _ => ParsePrepareResponse(await resp.Content.ReadAsStringAsync(cts.Token))
        };
    }

    private static PrepareResult ParsePrepareResponse(string json)
    {
        var result = new PrepareResult();
        using var doc = JsonDocument.Parse(json);
        var root = doc.RootElement;
        if (root.TryGetProperty("sessionId", out var sid)) result.SessionId = sid.GetString() ?? "";
        if (root.TryGetProperty("files", out var f) && f.ValueKind == JsonValueKind.Object)
            foreach (var kv in f.EnumerateObject())
                if (kv.Value.ValueKind == JsonValueKind.String && kv.Value.GetString() is { } t)
                    result.Tokens[kv.Name] = t;
        return result;
    }

    /// <summary>上传单文件到 LocalSend 会话（查询参数携带 sessionId/fileId/token），回调进度 0..1。</summary>
    public async Task UploadFileAsync(LocalSendDevice target, string sessionId, string fileId, string token,
        string filePath, Action<double>? onProgress = null, CancellationToken ct = default)
    {
        var len = new FileInfo(filePath).Length;
        await using var stream = File.OpenRead(filePath);
        var content = new ProgressContent(stream, len, onProgress, ct);
        var url = $"{target.Protocol}://{target.Ip}:{target.Port}{LsCompat.PathUpload}" +
                  $"?sessionId={Uri.EscapeDataString(sessionId)}&fileId={Uri.EscapeDataString(fileId)}&token={Uri.EscapeDataString(token)}";
        using var req = new HttpRequestMessage(HttpMethod.Post, url) { Content = content };
        using var resp = await ClientFor(target).SendAsync(req, HttpCompletionOption.ResponseHeadersRead, ct);
        if (!resp.IsSuccessStatusCode)
            throw new LsSendException((int)resp.StatusCode, $"上传失败 ({(int)resp.StatusCode})");
    }

    /// <summary>取消进行中的 LocalSend 会话（发送端放弃时通知对端）。</summary>
    public async Task CancelAsync(LocalSendDevice target, string sessionId)
    {
        try
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(8));
            var url = $"{target.Protocol}://{target.Ip}:{target.Port}{LsCompat.PathCancel}?sessionId={Uri.EscapeDataString(sessionId)}";
            await ClientFor(target).PostAsync(url, new StringContent("", Encoding.UTF8, "application/json"), cts.Token);
        }
        catch { /* 取消通知尽力而为 */ }
    }

    /// <summary>常见扩展名 → MIME（LocalSend 用 fileType 展示预览类型；未知类型给 octet-stream）。</summary>
    private static string MimeOf(string path)
    {
        var ext = Path.GetExtension(path).ToLowerInvariant();
        return ext switch
        {
            ".jpg" or ".jpeg" => "image/jpeg",
            ".png" => "image/png",
            ".gif" => "image/gif",
            ".webp" => "image/webp",
            ".bmp" => "image/bmp",
            ".heic" or ".heif" => "image/heic",
            ".svg" => "image/svg+xml",
            ".mp4" or ".m4v" => "video/mp4",
            ".mov" => "video/quicktime",
            ".mkv" => "video/x-matroska",
            ".webm" => "video/webm",
            ".avi" => "video/x-msvideo",
            ".flv" => "video/x-flv",
            ".wmv" => "video/x-ms-wmv",
            ".mp3" => "audio/mpeg",
            ".wav" => "audio/wav",
            ".ogg" or ".opus" => "audio/ogg",
            ".aac" => "audio/aac",
            ".flac" => "audio/flac",
            ".m4a" => "audio/mp4",
            ".pdf" => "application/pdf",
            ".txt" => "text/plain",
            ".html" or ".htm" => "text/html",
            ".zip" => "application/zip",
            ".apk" => "application/vnd.android.package-archive",
            _ => "application/octet-stream"
        };
    }

    public void Dispose()
    {
        foreach (var c in _clients.Values) c.Dispose();
        _clients.Clear();
    }

    /// <summary>包装文件流以实时上报上传进度（可响应取消令牌）。</summary>
    private sealed class ProgressContent : HttpContent
    {
        private readonly Stream _stream;
        private readonly long _total;
        private readonly Action<double>? _onProgress;
        private readonly CancellationToken _ct;
        public ProgressContent(Stream stream, long total, Action<double>? onProgress, CancellationToken ct)
        { _stream = stream; _total = Math.Max(1, total); _onProgress = onProgress; _ct = ct; }
        protected override async Task SerializeToStreamAsync(Stream stream, TransportContext? context)
        {
            var buffer = new byte[81920];
            long read = 0;
            while (!_ct.IsCancellationRequested)
            {
                int n = await _stream.ReadAsync(buffer, _ct);
                if (n <= 0) break;
                await stream.WriteAsync(buffer.AsMemory(0, n), _ct);
                read += n;
                _onProgress?.Invoke((double)read / _total);
            }
            if (_ct.IsCancellationRequested) throw new OperationCanceledException(_ct);
        }
        protected override bool TryComputeLength(out long length)
        { length = _total; return true; }
    }
}