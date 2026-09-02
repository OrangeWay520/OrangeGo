using System.ComponentModel;
using System.IO;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>应用持久化设置（收件目录/设备别名/外观模式/开机自启），存于 %AppData%\OrangeGO\settings.json。</summary>
public sealed class AppSettings : INotifyPropertyChanged
{
    private string _saveDir = "";
    private string _deviceName = "";
    private string _themeMode = "follow"; // follow|light|dark
    private bool _autoStart;

    public string SaveDir { get => _saveDir; set { _saveDir = value; OnChanged(); } }
    public string DeviceName { get => _deviceName; set { _deviceName = value; OnChanged(); } }
    public string ThemeMode { get => _themeMode; set { _themeMode = value; OnChanged(); } }
    public bool AutoStart { get => _autoStart; set { _autoStart = value; OnChanged(); } }

    private static string FilePath => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO", "settings.json");

    /// <summary>构造默认设置（基于当前环境）。</summary>
    public static AppSettings Defaults() => new()
    {
        SaveDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "OrangeGO收件"),
        DeviceName = Environment.MachineName,
        ThemeMode = "follow",
        AutoStart = false
    };

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
                    s.SaveDir = string.IsNullOrWhiteSpace(fromDisk.SaveDir) ? s.SaveDir : fromDisk.SaveDir;
                    s.DeviceName = string.IsNullOrWhiteSpace(fromDisk.DeviceName) ? s.DeviceName : fromDisk.DeviceName;
                    if (fromDisk.ThemeMode is "follow" or "light" or "dark") s.ThemeMode = fromDisk.ThemeMode;
                    s.AutoStart = fromDisk.AutoStart;
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

    private static readonly JsonSerializerOptions JsonOpts = new() { WriteIndented = true };

    public event PropertyChangedEventHandler? PropertyChanged;
    private void OnChanged([System.Runtime.CompilerServices.CallerMemberName] string? p = null)
        => PropertyChanged?.Invoke(this, new(p));
}