using System.Windows;
using System.Windows.Threading;

namespace OrangeGO.Windows.Theme;

/// <summary>
/// 主题管理：默认跟随 Windows 深浅色；也支持手动锁定为 浅色/深色。
/// 切换时交换 merged dictionary；UI 控件全部用 DynamicResource 即时跟随。
/// </summary>
public static class ThemeManager
{
    private static readonly ResourceDictionary _light = new() { Source = new Uri("/Theme/Light.xaml", UriKind.Relative) };
    private static readonly ResourceDictionary _dark = new() { Source = new Uri("/Theme/Dark.xaml", UriKind.Relative) };
    private static readonly DispatcherTimer _timer = new() { Interval = TimeSpan.FromSeconds(3) };
    private static bool _installed;
    private static bool _applied;
    private static string _mode = "follow"; // follow | light | dark

    public static bool IsDark { get; private set; }
    public static string Mode => _mode;

    /// <summary>启动轮询；mode 可后续通过 SetMode 变更。</summary>
    public static void Start(System.Windows.Application app)
    {
        if (_installed) return;
        _installed = true;
        ApplyCurrent(app);
        _timer.Tick += (_, _) => ApplyCurrent(app);
        _timer.Start();
    }

    /// <summary>设置外观模式并立即生效。</summary>
    public static void SetMode(System.Windows.Application app, string mode)
    {
        if (mode is not ("follow" or "light" or "dark")) return;
        _mode = mode;
        // 强制重算一次（即使当前判定相同也刷新，保证从 follow 切走/切回都正确）
        _applied = false;
        ApplyCurrent(app);
        // 停掉跟随轮询时避免闪烁：非 follow 下每次计时也会走同分支，无副作用，保留即可
    }

    private static void ApplyCurrent(System.Windows.Application app)
    {
        var dark = _mode switch
        {
            "light" => false,
            "dark" => true,
            _ => IsSystemDark()
        };
        if (_applied && dark == IsDark) return; // 未变化

        // 先清掉两套，再并入正确的一套；DynamicResource 自动刷新所有引用
        app.Resources.MergedDictionaries.Remove(_light);
        app.Resources.MergedDictionaries.Remove(_dark);
        app.Resources.MergedDictionaries.Add(dark ? _dark : _light);

        IsDark = dark;
        _applied = true;
    }

    /// <summary>读 Windows「应用使用浅色模式」注册表项。默认深色。</summary>
    private static bool IsSystemDark()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(
                @"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return (key?.GetValue("AppsUseLightTheme") is int v) && v == 0;
        }
        catch { return true; }
    }
}