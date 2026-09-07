using System.Collections.ObjectModel;
using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Linq;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Windows.Media;
using OrangeGO.Windows.Localization;

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

    private bool _isFavorite;
    /// <summary>是否为收藏（白名单）设备（按设备 ID 判定，与 IP 无关）。</summary>
    public bool IsFavorite { get => _isFavorite; set { _isFavorite = value; OnChanged(); } }

    private bool _isLocalSend;
    /// <summary>是否为 LocalSend 设备（来自 iOS/安卓 LocalSend App）。仅兼容文件互传，文本/语音等专有功能不适配。</summary>
    public bool IsLocalSend { get => _isLocalSend; set { _isLocalSend = value; OnChanged(); } }

    /// <summary>LocalSend 设备的 TLS 证书 SHA-256 指纹（发送时钉扎校验，防局域网中间人）。</summary>
    public string LsFingerprint { get; set; } = "";
    /// <summary>LocalSend 设备的服务协议（https/http），由其组播通告或 register 请求获知。</summary>
    public string LsProtocol { get; set; } = "https";

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
    public string Summary => string.Format(LangManager.T("Incoming.Summary"), DeviceName, FileCount, FormatBytes(TotalBytes));
    public string Initial => string.IsNullOrEmpty(DeviceName) ? "?" : DeviceName[..1].ToUpperInvariant();

    /// <summary>待交还的接收确认；接受时 SetResult(保存目录)，拒绝时 SetResult(null)。</summary>
    public required TaskCompletionSource<string?> Completion { get; init; }

    public static string FormatBytes(long b) =>
        b >= 1L << 30 ? $"{b / (double)(1 << 30):0.##} GB"
        : b >= 1L << 20 ? $"{b / (double)(1 << 20):0.##} MB"
        : b >= 1L << 10 ? $"{b / (double)(1 << 10):0.##} KB"
        : $"{b} B";

    /// <summary>语言切换后刷新展示文本。</summary>
    public void RefreshText() => OnChanged(nameof(Summary));

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>传输进度项。</summary>
public sealed class TransferItem : INotifyPropertyChanged
{
    private string _name = "";
    public required string Name { get => _name; set { _name = value; OnChanged(nameof(Name)); } }
    /// <summary>EMA 平滑剩余时间（毫秒，-1 未初始化），消除瞬时估算抖动。</summary>
    public double RemainEma { get; set; } = -1;
    /// <summary>传输中气泡内进度文字（剩余时间 + 已接收/总大小），传输结束后清空。</summary>
    private string _progressText = "";
    public string ProgressText { get => _progressText; set { _progressText = value; OnChanged(nameof(ProgressText)); } }
    /// <summary>批次总字节数，供进度文字显示 已传输/总大小。</summary>
    public long TotalBytesForProgress { get; set; }
    public required string Direction { get; set; } // "发送"/"接收", 用于图标与配色
    public string Target { get; set; } = "";
    /// <summary>是否为文字消息气泡（而非文件传输）。</summary>
    public bool IsText { get; init; }
    /// <summary>文字消息内容（IsText=true 时有效）。</summary>
    public string Content { get; init; } = "";
    /// <summary>文件传输时对应的本地完整路径（用于传输记录里的图片/视频预览；单文件时设置）。</summary>
    public string MediaPath { get; init; } = "";
    /// <summary>多文件传输时的全部本地路径（按原文件顺序；空=未知/单文件）。</summary>
    public List<string> MediaPaths { get; init; } = new();
    /// <summary>是否包含多个文件（多文件记录用独立气泡/网格/文件列表展示）。</summary>
    public bool HasMultipleFiles => MediaPaths.Count > 1;
    /// <summary>多文件记录的子项（每个文件一个轻量子项，复用 TransferItem 的缩略图/图标/预览能力）；懒构建并缓存。</summary>
    private List<TransferItem>? _subItems;
    public List<TransferItem> SubItems => _subItems ??= BuildSubItems();
    /// <summary>扫描子项中的媒体（图片+视频，顺序与原始文件一致）。</summary>
    private List<TransferItem> AllMediaItems => SubItems.Where(s => s.IsImage || s.IsVideo).ToList();
    /// <summary>扫描子项中的普通文件（图标+文件名+大小的文件卡）。</summary>
    private List<TransferItem> AllFileItems => SubItems.Where(s => !s.IsImage && !s.IsVideo).ToList();
    /// <summary>子项中可预览的媒体（图片+视频），受单气泡体积上限 INTEGRATED_MEDIA_CAP 截断。</summary>
    public List<TransferItem> MediaGridItems => AllMediaItems.Take(INTEGRATED_MEDIA_CAP).ToList();
    /// <summary>子项中其余普通文件（显示图标+文件名+大小的文件卡），受单气泡体积上限 INTEGRATED_CARD_CAP 截断。</summary>
    public List<TransferItem> FileListItems => AllFileItems.Take(INTEGRATED_CARD_CAP).ToList();
    /// <summary>全部子项（独立气泡模式用）。独立气泡每个文件一个气泡，故不受单气泡体积上限限制。</summary>
    public List<TransferItem> SubBubbleItems => SubItems;
    /// <summary>因体积上限被截断而隐藏的文件数；大于 0 时底部显示「等 N 个文件」。</summary>
    public int MoreFilesCount =>
        Math.Max(0, AllMediaItems.Count - INTEGRATED_MEDIA_CAP)
        + Math.Max(0, AllFileItems.Count - INTEGRATED_CARD_CAP);
    /// <summary>集成模式是否因体积上限截断了部分文件（需显示底部汇总）。</summary>
    public bool ShowMoreFiles => MoreFilesCount > 0 && !SeparateBubbles;
    /// <summary>「等 N 个文件」底部汇总文案（体积上限截断时显示，与安卓端对齐）。</summary>
    public string MoreFilesText => MoreFilesCount > 0
        ? string.Format(LangManager.T("Transfer.MoreFiles"), MoreFilesCount)
        : "";
    /// <summary>气泡样式中，媒体网格与文件卡的体积上限（与安卓端对齐）。</summary>
    public const int INTEGRATED_MEDIA_CAP = 9;
    /// <summary>气泡样式中，普通文件卡的数量上限（与安卓端对齐）。</summary>
    public const int INTEGRATED_CARD_CAP = 6;
    /// <summary>是否以"文件集成显示"网格展示：设置开启 且 多文件 且 有图片/视频媒体。</summary>
    public bool IntegratedGrid => Core.AppSettings.Instance?.IntegrateImages == true && HasMultipleFiles && MediaGridItems.Count > 0;
    /// <summary>是否显示单张大图预览：集成网格未启用（含设置关闭或仅单图）时显示原有的单图完整预览。</summary>
    public bool ShowSingleImagePreview => IsImage && !IntegratedGrid;
    /// <summary>非集成模式且多文件：每个文件各自独立气泡（与安卓端对齐）。</summary>
    public bool SeparateBubbles => Core.AppSettings.Instance?.IntegrateImages != true && HasMultipleFiles;
    /// <summary>多文件且含普通文件（且非独立气泡模式）：显示文件卡列表。</summary>
    public bool ShowFileList => HasMultipleFiles && FileListItems.Count > 0 && !SeparateBubbles;
    /// <summary>文件大小文本（文件卡/独立气泡子项用）。</summary>
    public string SizeText => Size >= 0 ? IncomingItem.FormatBytes(Size) : "";
    /// <summary>纯媒体气泡（集成网格 / 单图预览 / 视频预览）：气泡内只有媒体，四边留白需统一，不用文字气泡的左右宽留白。</summary>
    public bool IsMediaBubble => IntegratedGrid || ShowSingleImagePreview || IsVideo;
    /// <summary>传输文件字节数（发送/接收均可用，用于显示大小/进度）。</summary>
    public long Size { get; set; }
    /// <summary>是否为图片文件（传输记录按它显示图片预览）。</summary>
    public bool IsImage => !string.IsNullOrEmpty(MediaPath) && TransferItem.IsImageExt(MediaPath);
    /// <summary>是否为音频文件（传输记录按它显示音频预览）。</summary>
    public bool IsAudio => !string.IsNullOrEmpty(MediaPath) && TransferItem.IsAudioExt(MediaPath);
    /// <summary>是否为视频文件（传输记录按它显示视频预览）。</summary>
    public bool IsVideo => !string.IsNullOrEmpty(MediaPath) && TransferItem.IsVideoExt(MediaPath);
    /// <summary>常见图片扩展名。</summary>
    private static readonly string[] _imgExts =
        [".png", ".jpg", ".jpeg", ".bmp", ".gif", ".ico", ".webp", ".heic", ".heif", ".dng"];
    /// <summary>常见音频扩展名。</summary>
    private static readonly string[] _audioExts =
        [".mp3", ".wav", ".ogg", ".aac", ".flac", ".m4a", ".wma", ".opus", ".amr"];
    /// <summary>常见视频扩展名。</summary>
    private static readonly string[] _videoExts = [".mp4", ".mov", ".mkv", ".avi", ".wmv", ".webm", ".flv", ".m4v"];
    /// <summary>Word 文档扩展名。</summary>
    private static readonly string[] _wordExts = [".doc", ".docx"];
    /// <summary>Excel 表格扩展名。</summary>
    private static readonly string[] _excelExts = [".xls", ".xlsx", ".xlsm", ".csv"];
    /// <summary>PowerPoint 幻灯片扩展名。</summary>
    private static readonly string[] _pptExts = [".ppt", ".pptx", ".pps", ".ppsx"];
    /// <summary>PDF 文档扩展名。</summary>
    private static readonly string[] _pdfExts = [".pdf"];
    /// <summary>是否为 Word 文档。</summary>
    public bool IsWord => _wordExts.Contains(SelfExt);
    /// <summary>是否为 Excel 表格。</summary>
    public bool IsExcel => _excelExts.Contains(SelfExt);
    /// <summary>是否为 PowerPoint 幻灯片。</summary>
    public bool IsPpt => _pptExts.Contains(SelfExt);
    /// <summary>是否为 PDF 文档。</summary>
    public bool IsPdf => _pdfExts.Contains(SelfExt);
    /// <summary>是否为 Office/PDF 文档（显示对应品牌图标）。</summary>
    public bool IsOffice => IsWord || IsExcel || IsPpt || IsPdf;
    /// <summary>媒体文件自身的扩展名（小写，含点）。</summary>
    private string SelfExt => string.IsNullOrEmpty(MediaPath)
        ? ""
        : System.IO.Path.GetExtension(MediaPath).ToLowerInvariant();
    /// <summary>Office/PDF 品牌图标资源（内嵌 PNG，pack URI），非 Office/PDF 为空。</summary>
    public string DocIcon => IsWord ? "pack://application:,,,/Assets/office/fabric_word.png"
        : IsExcel ? "pack://application:,,,/Assets/office/fabric_excel.png"
        : IsPpt ? "pack://application:,,,/Assets/office/fabric_powerpoint.png"
        : IsPdf ? "pack://application:,,,/Assets/office/fabric_pdf.png"
        : "";
    /// <summary>判断是否为图片扩展名。</summary>
    public static bool IsImageExt(string path) =>
        _imgExts.Contains(System.IO.Path.GetExtension(path).ToLowerInvariant());
    /// <summary>判断是否为音频扩展名。</summary>
    public static bool IsAudioExt(string path) =>
        _audioExts.Contains(System.IO.Path.GetExtension(path).ToLowerInvariant());
    /// <summary>判断是否为视频扩展名。</summary>
    public static bool IsVideoExt(string path) =>
        _videoExts.Contains(System.IO.Path.GetExtension(path).ToLowerInvariant());

    // ---------- HEIC/HEIF：需要系统「HEIF 图像扩展」编解码器才能解码预览 ----------
    private static readonly string[] _heicExts = [".heic", ".heif"];
    private static bool _heifProbed;
    private static bool _heifOk;

    /// <summary>是否为 HEIC/HEIF 图片。</summary>
    public bool IsHeic => _heicExts.Contains(SelfExt);

    /// <summary>
    /// HEIC 且本机未安装 HEIF 解码器 → 无法解码，缩略图/预览处应提示用户安装。
    /// 首次遇到任意 HEIC 文件时探测一次并缓存结果（系统解码器是否安装是机器级、基本不变）。
    /// </summary>
    public bool HeicUnavailable
    {
        get
        {
            if (!IsHeic) return false;
            EnsureHeifStatus(MediaPath);
            return !_heifOk;
        }
    }

    private static void EnsureHeifStatus(string samplePath)
    {
        if (_heifProbed) return;
        lock (typeof(TransferItem))
        {
            if (_heifProbed) return;
            // 文件不存在时不缓存结果，等下次有文件时再探测（避免传输中误判为未安装）
            if (string.IsNullOrEmpty(samplePath) || !File.Exists(samplePath)) return;
            _heifOk = TryDecodeHeic(samplePath);
            _heifProbed = true;
        }
    }

    /// <summary>尝试用系统 WIC 解码一份 HEIC：能解出帧说明已安装 HEIF 图像扩展。</summary>
    private static bool TryDecodeHeic(string path)
    {
        try
        {
            if (string.IsNullOrEmpty(path) || !File.Exists(path)) return false;
            using var fs = new FileStream(path, FileMode.Open, FileAccess.Read, FileShare.ReadWrite | FileShare.Delete);
            var dec = System.Windows.Media.Imaging.BitmapDecoder.Create(
                fs,
                System.Windows.Media.Imaging.BitmapCreateOptions.PreservePixelFormat,
                System.Windows.Media.Imaging.BitmapCacheOption.OnLoad);
            return dec.Frames.Count > 0;
        }
        catch { return false; }
    }
    /// <summary>懒构建多文件记录的子项（每文件一个 TransferItem，方向/目标/发送方继承，各自带真实大小）。</summary>
    private List<TransferItem> BuildSubItems()
    {
        var list = new List<TransferItem>(MediaPaths.Count);
        foreach (var p in MediaPaths)
        {
            if (string.IsNullOrWhiteSpace(p)) continue;
            var name = System.IO.Path.GetFileName(p);
            long size = -1;
            try { if (File.Exists(p)) size = new FileInfo(p).Length; } catch { }
            list.Add(new TransferItem
            {
                Name = string.IsNullOrEmpty(name) ? p : name,
                Direction = Direction,
                Target = Target,
                MediaPath = p,
                MediaPaths = new List<string>(),
                Size = size,
                SendId = SendId,
                Timestamp = Timestamp
            });
        }
        return list;
    }
    /// <summary>Shell 生成的首帧/文件缩略图（单文件或视频传输时设置，供传输记录预览，替代易黑屏的 MediaElement）。</summary>
    private ImageSource? _thumb;
    public ImageSource? Thumb { get => _thumb; set { _thumb = value; OnChanged(); } }
    /// <summary>跨端消息唯一标识（发送方生成，撤回/取消按它匹配）。</summary>
    public string SendId { get; set; } = "";
    /// <summary>消息发送时刻（Unix 毫秒），用于判断 2 分钟内能否撤回。</summary>
    public long Timestamp { get; set; }
    /// <summary>已生成并落盘到 ThumbCache 的缩略图内容指纹 key（与 MediaPaths/MediaPath 对应），删除/撤回/清空记录时据此精确回收缓存。</summary>
    public List<string> ThumbKeys { get; set; } = new();
    /// <summary>恢复历史时的 路径→key 精确映射（来自持久化的 ThumbKeyMap），源文件被删/移后各子项据此取回各自的缩略图。</summary>
    public Dictionary<string, string> ThumbKeyMap { get; set; } = new();
    /// <summary>气泡外元信息：发送→「发给 X」，接收→「来自 X」。</summary>
    public string MetaLabel => Direction == "发送"
        ? (string.IsNullOrEmpty(Target) ? LangManager.T("Transfer.Sent") : string.Format(LangManager.T("Transfer.SentTo"), Target))
        : (string.IsNullOrEmpty(Target) ? LangManager.T("Transfer.Received") : string.Format(LangManager.T("Transfer.ReceivedFrom"), Target));
    /// <summary>气泡外时间（Unix 毫秒 → 本地 MM-dd HH:mm）。</summary>
    public string TimeText => Timestamp > 0
        ? DateTimeOffset.FromUnixTimeMilliseconds(Timestamp).ToLocalTime().ToString("MM-dd HH:mm")
        : "";
    private bool _recalled;
    /// <summary>是否已被撤回。</summary>
    public bool Recalled { get => _recalled; set { _recalled = value; OnChanged(); OnChanged(nameof(RecalledText)); OnChanged(nameof(CanRecall)); } }
    /// <summary>撤回后的展示文案：发送方显示"你撤回了一条消息"，接收方显示"对方撤回了一条消息"。</summary>
    public string RecalledText => Recalled ? (Direction == "发送" ? LangManager.T("Transfer.YouRecalled") : LangManager.T("Transfer.PeerRecalled")) : "";
    /// <summary>发送方在 2 分钟内、尚未撤回时可提供"撤回"。</summary>
    public bool CanRecall => !_recalled && Direction == "发送" && !string.IsNullOrEmpty(SendId) &&
        Timestamp > 0 && (DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - Timestamp) <= 120_000;
    private double _progress;
    private string _stateKey = "Progress";
    public double Progress
    {
        get => _progress;
        set
        {
            var becameDone = _progress < 1.0 && value >= 1.0;
            _progress = value;
            OnChanged();
            OnChanged(nameof(Percent));
            // 传输完成时让媒体预览从"半成品"重新加载完整文件，否则接收端图片/视频会始终停在空帧
            if (becameDone) OnChanged(nameof(MediaPreview));
        }
    }
    public string Percent => $"{_progress * 100:0}%";
    /// <summary>媒体预览源（与 MediaPath 同值）；完成进度时重发通知以强制重载完整文件。</summary>
    public string MediaPreview => MediaPath;

    /// <summary>记录一个已落盘到 ThumbCache 的缩略图 key（去重）；key 空白时忽略。</summary>
    public void AddThumbKey(string? key)
    {
        if (string.IsNullOrWhiteSpace(key)) return;
        if (!ThumbKeys.Contains(key))
        {
            ThumbKeys.Add(key);
            OnChanged(nameof(ThumbKeys)); // 通知持久化，保证内容指纹 key 在源文件被删/移前已写回磁盘
        }
    }

    /// <summary>聚合本记录及其全部子项（多文件集成气泡）已生成的缩略图缓存 key，供删除/撤回/自动清理时精确回收。</summary>
    public IEnumerable<string> AllThumbKeys()
    {
        foreach (var k in ThumbKeys) yield return k;
        foreach (var s in SubItems)
            foreach (var k in s.ThumbKeys) yield return k;
    }

    /// <summary>传输状态展示文案：内部存 token（State.Pending/...），按当前语言取词。</summary>
    public string State { get => LangManager.T("State." + _stateKey); set { _stateKey = ToStateKey(value); OnChanged(nameof(State)); OnChanged(nameof(StateKey)); OnChanged(nameof(ShowCancel)); } }
    /// <summary>状态内部 token（用于持久化，如 "Progress"/"Completed"）。</summary>
    public string StateKey { get => _stateKey; }

    /// <summary>是否显示"取消"按钮：仅发送中的发送记录显示；已完成/失败/撤回等终态一律隐藏。
    /// 历史恢复的记录终态为 Completed，因此不会再看到残留的取消按钮。</summary>
    public bool ShowCancel =>
        Direction == "发送" && !IsText && _stateKey is "Pending" or "Progress";

    /// <summary>转为磁盘可序列化快照。</summary>
    public TransferRecordDto ToDto() => new()
    {
        Name = Name, Direction = Direction, Target = Target,
        IsText = IsText, Content = Content, SendId = SendId,
        Timestamp = Timestamp, Size = Size, MediaPath = MediaPath,
        MediaPaths = MediaPaths,
        StateKey = StateKey, Recalled = Recalled,
        // 持久化时聚合本记录及全部子项的内容指纹 key，源文件被删除/移动后重启仍可通过统一 key 命中缩略图缓存
        ThumbKeys = AllThumbKeys().Where(k => !string.IsNullOrWhiteSpace(k)).Distinct().ToList(),
        // 精确到"每个文件各自对应哪个 key"的映射：恢复时即使源文件已删除，各子项也能取回自己的缩略图
        ThumbKeyMap = BuildThumbKeyMap()
    };

    /// <summary>构建 路径→key 映射（本记录自身 + 全部子项，各取其第一个有效 key）。</summary>
    private Dictionary<string, string> BuildThumbKeyMap()
    {
        var map = new Dictionary<string, string>();
        void Add(string? path, List<string> keys)
        {
            if (string.IsNullOrEmpty(path)) return;
            var k = keys.FirstOrDefault(x => !string.IsNullOrWhiteSpace(x));
            if (k is not null && !map.ContainsKey(path)) map[path] = k;
        }
        Add(MediaPath, ThumbKeys);
        foreach (var s in SubItems) Add(s.MediaPath, s.ThumbKeys);
        return map;
    }

    /// <summary>从磁盘快照还原传输条目。</summary>
    public static TransferItem FromDto(TransferRecordDto d)
    {
        var t = new TransferItem
        {
            Name = d.Name, Direction = d.Direction, Target = d.Target,
            IsText = d.IsText, Content = d.Content, SendId = d.SendId,
            Timestamp = d.Timestamp, Size = d.Size, MediaPath = d.MediaPath,
            MediaPaths = d.MediaPaths ?? new(),
            ThumbKeys = d.ThumbKeys ?? new(),
            ThumbKeyMap = d.ThumbKeyMap ?? new()
        };
        t.Recalled = d.Recalled;
        // 先按状态 token 恢复显示文案，再以撤回态覆盖（撤回优先）
        if (!string.IsNullOrEmpty(d.StateKey)) t.State = "State." + d.StateKey;
        if (d.Recalled) t._stateKey = "Recalled";
        return t;
    }
    private static string ToStateKey(string v) => v switch
    {
        "等待中" => "Pending",
        "进行中" => "Progress",
        "完成" or "已完成" => "Completed",
        "失败" => "Failed",
        "已取消" => "Cancelled",
        "已撤回" => "Recalled",
        "被拒" => "Declined",
        _ when v.StartsWith("State.", StringComparison.Ordinal) => v[6..],
        _ => v,
    };

    /// <summary>语言切换后刷新所有展示文本。</summary>
    public void RefreshText()
    {
        OnChanged(nameof(MetaLabel));
        OnChanged(nameof(State));
        OnChanged(nameof(RecalledText));
    }

    /// <summary>"文件集成显示"设置变化后刷新所有派生展示状态。</summary>
    public void RefreshIntegrateImages()
    {
        OnChanged(nameof(IntegratedGrid));
        OnChanged(nameof(ShowSingleImagePreview));
        OnChanged(nameof(IsMediaBubble));
        OnChanged(nameof(SeparateBubbles));
        OnChanged(nameof(ShowFileList));
        OnChanged(nameof(MediaGridItems));
        OnChanged(nameof(FileListItems));
        OnChanged(nameof(SubBubbleItems));
    }

    /// <summary>多选模式下是否被选中（右键「多选」进入选择模式后点亮该气泡）。</summary>
    private bool _isSelected;
    public bool IsSelected
    {
        get => _isSelected;
        set { if (_isSelected == value) return; _isSelected = value; OnChanged(); }
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>主窗口视图模型。</summary>
public sealed class MainViewModel : INotifyPropertyChanged
{
    public ObservableCollection<PeerItem> Peers { get; } = new();
    public ObservableCollection<IncomingItem> Incoming { get; } = new();
    public ObservableCollection<TransferItem> Transfers { get; } = new();

    private readonly System.Collections.Generic.HashSet<TransferItem> _selected = new();
    private bool _isMultiSelect;
    /// <summary>是否处于气泡多选模式（右键「多选」进入）。</summary>
    public bool IsMultiSelect
    {
        get => _isMultiSelect;
        set
        {
            if (_isMultiSelect == value) return;
            _isMultiSelect = value;
            if (!value) ClearSelection();
            OnChanged();
            OnChanged(nameof(ShowMultiBar));
            OnChanged(nameof(MultiSelectedCount));
            OnChanged(nameof(SelectionBarText));
        }
    }
    /// <summary>多选操作栏是否显示（进入多选模式即显示）。</summary>
    public bool ShowMultiBar => _isMultiSelect;
    /// <summary>已选中（多选）的传输记录条数。</summary>
    public int MultiSelectedCount => _selected.Count;
    /// <summary>多选栏左侧提示文案：已选 N 条。</summary>
    public string SelectionBarText => string.Format(OrangeGO.Windows.Localization.LangManager.T("Transfer.SelectedCount"), MultiSelectedCount);

    /// <summary>切换某条记录的选择状态；当前非多选模式时忽略。</summary>
    public void ToggleSelection(TransferItem item)
    {
        if (!_isMultiSelect || item is null) return;
        if (!_selected.Add(item)) _selected.Remove(item);
        item.IsSelected = _selected.Contains(item);
        OnChanged(nameof(MultiSelectedCount));
        OnChanged(nameof(SelectionBarText));
    }

    /// <summary>清空所有选中状态（退出多选时调用）。</summary>
    public void ClearSelection()
    {
        foreach (var i in _selected) i.IsSelected = false;
        _selected.Clear();
        OnChanged(nameof(MultiSelectedCount));
        OnChanged(nameof(SelectionBarText));
    }

    /// <summary>当前选中的记录列表。</summary>
    public System.Collections.Generic.List<TransferItem> SelectedTransfers()
        => _selected.ToList();
    /// <summary>收藏设备（白名单）列表，按设备 ID 记录。</summary>
    public ObservableCollection<Core.FavDevice> Favorites { get; } = new();

    /// <summary>同步收藏列表并刷新所有设备的收藏星标。</summary>
    public void RefreshFavorites(System.Collections.Generic.IEnumerable<Core.FavDevice> list)
    {
        Favorites.Clear();
        foreach (var f in list) Favorites.Add(f);
        OnChanged(nameof(Favorites.Count));
        foreach (var p in Peers)
            p.IsFavorite = IsFavorite(p.DeviceId);
    }

    /// <summary>判断某设备 ID 是否已被收藏。</summary>
    public bool IsFavorite(string id)
    {
        foreach (var f in Favorites)
            if (f.Id == id) return true;
        return false;
    }

    public MainViewModel()
    {
        // 语言切换时刷新所有已生成文本
        Localization.LangManager.LanguageChanged += RefreshAllTexts;
    }

    private void RefreshAllTexts()
    {
        foreach (var t in Transfers) t.RefreshText();
        foreach (var i in Incoming) i.RefreshText();
        OnChanged(nameof(SelectedLabel));
    }

    private string _status = "未连接";
    public string Status { get => _status; set { _status = value; OnChanged(); } }

    private string _receiveDirHint = "";
    public string ReceiveDirHint { get => _receiveDirHint; set { _receiveDirHint = value; OnChanged(); } }

    /// <summary>当前选中（多选）的设备数量。</summary>
    public int SelectedCount { get; private set; }
    public bool HasSelection => SelectedCount > 0;
    public string SelectedLabel => SelectedCount > 0
        ? string.Format(LangManager.T("Devices.SelectedCount"), SelectedCount)
        : LangManager.T("Devices.SelectPrompt");

    /// <summary>由设备列表的多选变化调起：更新底部栏计数与状态。</summary>
    public void UpdateSelection(int count)
    {
        SelectedCount = count;
        OnChanged(nameof(SelectedCount));
        OnChanged(nameof(HasSelection));
        OnChanged(nameof(SelectedLabel));
    }

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([CallerMemberName] string? p = null) => PropertyChanged?.Invoke(this, new(p));
}

/// <summary>传输记录的磁盘快照模型（供重启后恢复历史记录）。</summary>
public sealed class TransferRecordDto
{
    public string Name { get; set; } = "";
    public string Direction { get; set; } = "";
    public string Target { get; set; } = "";
    public bool IsText { get; set; }
    public string Content { get; set; } = "";
    public string SendId { get; set; } = "";
    public long Timestamp { get; set; }
    public long Size { get; set; }
    public string MediaPath { get; set; } = "";
    public List<string> MediaPaths { get; set; } = new();
    public string StateKey { get; set; } = "Completed";
    public bool Recalled { get; set; }
    public List<string> ThumbKeys { get; set; } = new();
    /// <summary>路径 → 缩略图缓存 key 的精确映射（源文件被删/移后无法按路径反算指纹，靠它恢复每个文件各自的缩略图）。</summary>
    public Dictionary<string, string>? ThumbKeyMap { get; set; }
}

/// <summary>传输记录历史存取（%AppData%\OrangeGO\transfers.json），与安卓端"保存历史"对齐。</summary>
public static class TransferHistory
{
    private static readonly JsonSerializerOptions JsonOpts = new() { WriteIndented = true };
    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO", "transfers.json");

    /// <summary>把当前传输记录序列化到磁盘。</summary>
    public static void Save(IEnumerable<TransferItem> items)
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            var list = items.Select(t => t.ToDto()).ToList();
            File.WriteAllText(FilePath, JsonSerializer.Serialize(list, JsonOpts));
        }
        catch { /* 保存失败不阻塞使用 */ }
    }

    /// <summary>从磁盘加载历史传输记录；损坏则返回空。</summary>
    public static List<TransferItem> Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return new List<TransferItem>();
            var dtos = JsonSerializer.Deserialize<List<TransferRecordDto>>(File.ReadAllText(FilePath), JsonOpts);
            return dtos is null ? new List<TransferItem>()
                : dtos.Select(TransferItem.FromDto).ToList();
        }
        catch { return new List<TransferItem>(); }
    }

    /// <summary>清除磁盘上的历史传输记录。</summary>
    public static void Clear()
    {
        try { if (File.Exists(FilePath)) File.Delete(FilePath); } catch { }
    }
}