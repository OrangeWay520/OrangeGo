using System.Collections.Generic;
using System.ComponentModel;
using System.IO;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>收藏设备（白名单）条目：按设备 ID（而非 IP）收藏；Name/Ip 为加入时的快照，用于收藏夹列表区分同名/同型号设备。</summary>
public sealed class FavDevice
{
    public string Id { get; set; } = "";
    public string Name { get; set; } = "";
    public string Ip { get; set; } = "";

    /// <summary>收藏夹列表区分条目的短标识：设备 ID（唯一且稳定，不随 IP 变化）。</summary>
    public string ShortId => "ID " + (Id.StartsWith("og_", System.StringComparison.Ordinal) ? Id[3..] : Id);
}

/// <summary>应用持久化设置（收件目录/设备别名/外观模式/开机自启/托盘/接收行为），存于 %AppData%\OrangeGO\settings.json。</summary>
public sealed class AppSettings : INotifyPropertyChanged
{
    private string _saveDir = "";
    private string _deviceName = "";
    private string _deviceId = "";           // 本机稳定身份标识（首启生成后持久化；收藏夹/白名单按它跨重启匹配）
    private string _themeMode = "follow";      // follow|light|dark
    private string _language = "follow";       // follow|en|zh|... 界面语言；follow 按系统解析，未命中回退英文
    private bool _autoStart;
    private bool _minimizeToTray;          // 关闭时最小化到系统托盘
    private bool _animations = true;       // 界面动画效果
    private bool _autoSave;                // 接收文件自动保存（无需逐条确认）
    private bool _autoSaveWhitelist;       // 仅自动保存来自"收藏夹(白名单)"设备
    private bool _requirePin;              // 接收前要求 PIN（生效前提：PinCode 非空，逻辑同安卓端）
    private string _pinCode = "";          // 接收 PIN 密码
    private readonly List<FavDevice> _favorites = new();  // 收藏设备（白名单），按设备 ID 记
    private bool _autoComplete;            // 传输完成后自动完成（预留）
    private bool _saveHistory = true;      // 保存到历史记录
    private bool _integrateImages;         // 图片集成显示：多张图片合并到一个消息气泡（固定小缩略图，默认关闭）
    private int _autoCleanupDays;          // 传输记录自动清除：删除 N 天前的记录（0=不自动清理）
    private bool _keepWindowDuringScreenshot; // 截图时保留本地窗口（否则隐藏主窗口再截）
    private string _lastSendPeerIds = "";     // 上次发送选中的设备 ID（逗号分隔），下次发送未选设备时自动沿用

    /// <summary>当前应用的设置单例（绑定气泡内的计算属性需实时读取）。</summary>
    public static AppSettings? Instance;

    public string SaveDir { get => _saveDir; set { _saveDir = value; OnChanged(); } }
    public string DeviceName { get => _deviceName; set { _deviceName = value; OnChanged(); } }
    public string DeviceId { get => _deviceId; set { _deviceId = value; OnChanged(); } }
    public string ThemeMode { get => _themeMode; set { _themeMode = value; OnChanged(); } }
    public string Language { get => _language; set { _language = value; OnChanged(); } }
    public bool AutoStart { get => _autoStart; set { _autoStart = value; OnChanged(); } }
    public bool MinimizeToTray { get => _minimizeToTray; set { _minimizeToTray = value; OnChanged(); } }
    public bool Animations { get => _animations; set { _animations = value; OnChanged(); } }
    public bool AutoSave { get => _autoSave; set { _autoSave = value; OnChanged(); } }
    public bool AutoSaveWhitelist { get => _autoSaveWhitelist; set { _autoSaveWhitelist = value; OnChanged(); } }
    public bool RequirePin { get => _requirePin; set { _requirePin = value; OnChanged(); } }
    public string PinCode { get => _pinCode; set { _pinCode = value; OnChanged(); } }
    public List<FavDevice> Favorites { get => _favorites; set { _favorites.Clear(); if (value is not null) _favorites.AddRange(value); OnChanged(); } }
    public bool AutoComplete { get => _autoComplete; set { _autoComplete = value; OnChanged(); } }
    public bool SaveHistory { get => _saveHistory; set { _saveHistory = value; OnChanged(); } }
    public bool IntegrateImages { get => _integrateImages; set { _integrateImages = value; OnChanged(); } }
    public int AutoCleanupDays { get => _autoCleanupDays; set { _autoCleanupDays = value; OnChanged(); } }
    public bool KeepWindowDuringScreenshot { get => _keepWindowDuringScreenshot; set { _keepWindowDuringScreenshot = value; OnChanged(); } }
    public string LastSendPeerIds { get => _lastSendPeerIds; set { _lastSendPeerIds = value; OnChanged(); } }

    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO", "settings.json");

    /// <summary>构造默认设置（基于当前环境）。</summary>
    public static AppSettings Defaults() => new()
    {
        SaveDir = DefaultSaveDir(),
        DeviceName = Environment.MachineName,
        DeviceId = NewDeviceId(),
        ThemeMode = "follow",
        Language = "follow",
        AutoStart = false,
        MinimizeToTray = false,
        Animations = true,
        AutoSave = false,
        AutoSaveWhitelist = true,
        RequirePin = false,
        AutoComplete = false,
        SaveHistory = true
    };

    /// <summary>默认收件目录：下载\OrangeGo。</summary>
    private static string DefaultSaveDir() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads", "OrangeGo");

    /// <summary>生成新的本机身份标识（首启时用，之后逐次加载复用）。</summary>
    private static string NewDeviceId() => "og_" + System.Guid.NewGuid().ToString("N")[..8];

    /// <summary>旧版本把默认收件目录放到了桌面（OrangeGO收件），检测到未改动时迁移到新默认。</summary>
    private static string OldDesktopDefault() =>
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "OrangeGO收件");

    public static AppSettings Load()
    {
        var s = Defaults();
        try
        {
            if (File.Exists(FilePath))
            {
                var fromDisk = JsonSerializer.Deserialize<AppSettings>(File.ReadAllText(FilePath), JsonOpts);
                if (fromDisk is not null)
                {
                    s.SaveDir = string.IsNullOrWhiteSpace(fromDisk.SaveDir) || PathEquals(fromDisk.SaveDir, OldDesktopDefault())
                        ? s.SaveDir
                        : fromDisk.SaveDir;
                    s.DeviceName = string.IsNullOrWhiteSpace(fromDisk.DeviceName) ? s.DeviceName : fromDisk.DeviceName;
                    s.DeviceId = string.IsNullOrWhiteSpace(fromDisk.DeviceId) ? s.DeviceId : fromDisk.DeviceId;
                    if (fromDisk.ThemeMode is "follow" or "light" or "dark") s.ThemeMode = fromDisk.ThemeMode;
                    if (fromDisk.Language is not null) s.Language = fromDisk.Language;
                    s.AutoStart = fromDisk.AutoStart;
                    s.MinimizeToTray = fromDisk.MinimizeToTray;
                    s.Animations = fromDisk.Animations;
                    s.AutoSave = fromDisk.AutoSave;
                    s.AutoSaveWhitelist = fromDisk.AutoSaveWhitelist;
                    s.RequirePin = fromDisk.RequirePin;
                    s.PinCode = fromDisk.PinCode ?? "";
                    if (fromDisk.Favorites is not null) s.Favorites = fromDisk.Favorites;
                    s.AutoComplete = fromDisk.AutoComplete;
                    s.SaveHistory = fromDisk.SaveHistory;
                    s.IntegrateImages = fromDisk.IntegrateImages;
                    s.AutoCleanupDays = fromDisk.AutoCleanupDays;
                    s.KeepWindowDuringScreenshot = fromDisk.KeepWindowDuringScreenshot;
                    s.LastSendPeerIds = fromDisk.LastSendPeerIds ?? "";
                }
            }
        }
        catch { /* 配置损坏则退回默认 */ }
        return s;
    }

    public void Save()
    {
        try
        {
            Directory.CreateDirectory(Path.GetDirectoryName(FilePath)!);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, JsonOpts));
        }
        catch { /* 保存失败不阻塞使用 */ }
    }

    private static bool PathEquals(string a, string b)
        => string.Equals(a?.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
                         b?.TrimEnd(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar),
                         StringComparison.OrdinalIgnoreCase);

    private static readonly JsonSerializerOptions JsonOpts = new() { WriteIndented = true };

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([System.Runtime.CompilerServices.CallerMemberName] string? p = null)
        => PropertyChanged?.Invoke(this, new(p));
}