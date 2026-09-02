using System.Text.Json.Serialization;

namespace OrangeGO.Windows.Core;

/// <summary>协议常量与数据模型（与 OrangeGO/PROTOCOL.md 对齐）。</summary>
public static class Protocol
{
    public const int Port = 53317;
    public const string ServiceType = "_orangego._tcp";
    public const string DiscoveryRequest = "discoveryRequest";
    public const string DiscoveryResponse = "discoveryResponse";
    public const string SendInit = "sendInit";
    public const string Ok = "ok";
    public const string Reject = "reject";
    public const string Error = "error";
    public const string Expired = "expired";
    public const string SendCancel = "sendCancel";
    public const string Message = "message"; // 文本消息命令
    public const string MessageType = "messageType";

    // 端点：v2 prepare/upload 为主，v1 init/file 保留兼容
    public const string PathPrepare = "/api/localsend/v2/prepare-upload";
    public const string PathUpload = "/api/localsend/v2/upload";
    public const string PathInit = "/api/v1/send/init";
    public const string PathFile = "/api/v1/send/file";
    public const string PathCancel = "/api/v1/send/cancel";
    public const string PathMessage = "/api/v1/send/message";

    /// <summary>发现/信息报文公共字段。</summary>
    public sealed class DeviceInfo
    {
        [JsonPropertyName("messageType")] public string MessageType { get; set; } = "";
        [JsonPropertyName("deviceId")] public string DeviceId { get; set; } = "";
        [JsonPropertyName("name")] public string Name { get; set; } = "";
        [JsonPropertyName("port")] public int Port { get; set; } = 53317;
        [JsonPropertyName("version")] public string Version { get; set; } = "1.0";
    }

    /// <summary>单个待传输文件元数据（v2）。</summary>
    public sealed class FileMeta
    {
        [JsonPropertyName("id")] public string Id { get; set; } = "";
        [JsonPropertyName("fileName")] public string FileName { get; set; } = "";
        [JsonPropertyName("size")] public long Size { get; set; }
        [JsonPropertyName("fileType")] public string FileType { get; set; } = "application/octet-stream";
        [JsonPropertyName("sha256")] public string? Sha256 { get; set; }
    }

    /// <summary>发送初始化请求（v2 携带文件清单）。</summary>
    public sealed class SendInitPayload
    {
        [JsonPropertyName("messageType")] public string MessageType { get; set; } = SendInit;
        [JsonPropertyName("sendId")] public string SendId { get; set; } = "";
        [JsonPropertyName("deviceId")] public string DeviceId { get; set; } = "";
        [JsonPropertyName("name")] public string Name { get; set; } = "";
        [JsonPropertyName("totalFiles")] public int TotalFiles { get; set; }
        [JsonPropertyName("totalSize")] public long TotalSize { get; set; }
        [JsonPropertyName("files")] public Dictionary<string, FileMeta>? Files { get; set; }
    }

    /// <summary>标准响应。</summary>
    public sealed class Response
    {
        [JsonPropertyName("messageType")] public string MessageType { get; set; } = "";
        [JsonPropertyName("sendId")] public string? SendId { get; set; }
        [JsonPropertyName("sessionId")] public string? SessionId { get; set; }
        [JsonPropertyName("token")] public string? Token { get; set; }
        [JsonPropertyName("files")] public Dictionary<string, string>? Files { get; set; }
        [JsonPropertyName("info")] public string? Info { get; set; }
    }

    /// <summary>v2 prepare-upload 结果：会话 id + 每文件 token。</summary>
    public sealed class PrepareResult
    {
        public string SessionId { get; set; } = "";
        public Dictionary<string, string> Tokens { get; set; } = new();
    }

    /// <summary>取消请求。</summary>
    public sealed class CancelPayload
    {
        [JsonPropertyName("messageType")] public string MessageType { get; set; } = SendCancel;
        [JsonPropertyName("sendId")] public string SendId { get; set; } = "";
    }

    /// <summary>文本消息负载（聊天气泡通道：不落盘、不是文件，无需 init/prepare/授权）。</summary>
    public sealed class TextMessage
    {
        [JsonPropertyName("messageType")] public string MessageType { get; set; } = Message;
        [JsonPropertyName("sendId")] public string SendId { get; set; } = "";
        [JsonPropertyName("deviceId")] public string DeviceId { get; set; } = "";
        [JsonPropertyName("name")] public string Name { get; set; } = "";
        [JsonPropertyName("content")] public string Content { get; set; } = "";
        [JsonPropertyName("timestamp")] public long Timestamp { get; set; }
    }
}