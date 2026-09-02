using System.Collections.ObjectModel;
using System.ComponentModel;
using System.Runtime.CompilerServices;

namespace OrangeGO.Windows.Core;

/// <summary>设备磁贴项。</summary>
public sealed class PeerItem : INotifyPropertyChanged
{
    private string _status = "在线";
    public required string DeviceId { get; set; }
    public required string Name { get; set; }
    public string Ip { get; set; } = "";
    public int Port { get; set; } = Protocol.Port;
    public DateTime LastSeen { get; set; }

    /// <summary>磁贴头像上的首字母。</summary>
    public string Initial => string.IsNullOrEmpty(Name) ? "?" : Name[..1].ToUpperInvariant();

    public string Status { get => _status; set { _status = value; OnChanged(); } }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>应用内接收卡片。用户点接受/拒绝时通过 Completion 交还 HTTP init 请求。</summary>
public sealed class IncomingItem : INotifyPropertyChanged
{
    public required string SendId { get; set; }
    public required string DeviceName { get; set; }
    public int FileCount { get; set; }
    public long TotalBytes { get; set; }
    public string Summary => $"「{DeviceName}」要发送 {FileCount} 个文件 · {FormatBytes(TotalBytes)}";
    public string Initial => string.IsNullOrEmpty(DeviceName) ? "?" : DeviceName[..1].ToUpperInvariant();

    /// <summary>待交还的接收确认；接受时 SetResult(保存目录)，拒绝时 SetResult(null)。</summary>
    public required TaskCompletionSource<string?> Completion { get; init; }

    public static string FormatBytes(long b) =>
        b >= 1L << 30 ? $"{b / (double)(1 << 30):0.##} GB"
        : b >= 1L << 20 ? $"{b / (double)(1 << 20):0.##} MB"
        : b >= 1L << 10 ? $"{b / (double)(1 << 10):0.##} KB"
        : $"{b} B";

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>传输进度项。</summary>
public sealed class TransferItem : INotifyPropertyChanged
{
    public required string Name { get; set; }
    public required string Direction { get; set; } // "发送"/"接收", 用于图标与配色
    public string Target { get; set; } = "";
    /// <summary>是否为文字消息气泡（而非文件传输）。</summary>
    public bool IsText { get; init; }
    /// <summary>文字消息内容（IsText=true 时有效）。</summary>
    public string Content { get; init; } = "";
    private double _progress;
    private string _state = "进行中";
    public double Progress { get => _progress; set { _progress = value; OnChanged(); OnChanged(nameof(Percent)); } }
    public string Percent => $"{_progress * 100:0}%";
    public string State { get => _state; set { _state = value; OnChanged(); } }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>主窗口视图模型。</summary>
public sealed class MainViewModel : INotifyPropertyChanged
{
    public ObservableCollection<PeerItem> Peers { get; } = new();
    public ObservableCollection<IncomingItem> Incoming { get; } = new();
    public ObservableCollection<TransferItem> Transfers { get; } = new();

    private string _status = "未连接";
    public string Status { get => _status; set { _status = value; OnChanged(); } }

    private string _receiveDirHint = "";
    public string ReceiveDirHint { get => _receiveDirHint; set { _receiveDirHint = value; OnChanged(); } }

    private PeerItem? _selected;
    public PeerItem? SelectedPeer { get => _selected; set { _selected = value; OnChanged(); OnChanged(nameof(HasSelection)); OnChanged(nameof(SelectedLabel)); } }
    public bool HasSelection => _selected != null;
    public string SelectedLabel => _selected is null ? "请选择一台设备" : $"已选择「{_selected.Name}」";

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}