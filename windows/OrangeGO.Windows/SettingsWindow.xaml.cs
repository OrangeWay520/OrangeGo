using System.Diagnostics;
using System.IO;
using System.Windows;
using Microsoft.Win32;
using OrangeGO.Windows.Core;

namespace OrangeGO.Windows;

/// <summary>设置对话框：编辑并持久化 设备别名/收件目录/外观模式/开机自启。</summary>
public partial class SettingsWindow : Window
{
    private readonly AppSettings _settings;
    private readonly Action _onSaved;

    public SettingsWindow(AppSettings settings, Action onSaved)
    {
        InitializeComponent();
        _settings = settings;
        _onSaved = onSaved;

        // 与主窗口一致：系统级圆角
        SourceInitialized += (_, _) =>
            ApplyCornerPreference(new System.Windows.Interop.WindowInteropHelper(this).Handle);

        // 载入当前值
        NameBox.Text = settings.DeviceName;
        DirBox.Text = settings.SaveDir;
        (settings.ThemeMode switch
        {
            "light" => ThemeLight,
            "dark" => ThemeDark,
            _ => ThemeFollow
        }).IsChecked = true;
        AutoStartBox.IsChecked = settings.AutoStart;
    }

    private void BrowseDir_Click(object sender, RoutedEventArgs e)
    {
        using var dlg = new System.Windows.Forms.FolderBrowserDialog
        {
            Description = "选择接收文件的保存目录",
            SelectedPath = Directory.Exists(DirBox.Text) ? DirBox.Text : ""
        };
        if (dlg.ShowDialog() == System.Windows.Forms.DialogResult.OK)
            DirBox.Text = dlg.SelectedPath;
    }

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        var name = NameBox.Text.Trim();
        var dir = DirBox.Text.Trim();
        if (string.IsNullOrEmpty(name) || string.IsNullOrEmpty(dir))
        {
            MessageBox.Show("设备别名与收件目录不能为空。", "OrangeGO 设置",
                MessageBoxButton.OK, MessageBoxImage.Warning);
            return;
        }

        _settings.DeviceName = name;
        _settings.SaveDir = dir;
        _settings.ThemeMode = ThemeLight.IsChecked == true ? "light"
            : ThemeDark.IsChecked == true ? "dark" : "follow";
        _settings.AutoStart = AutoStartBox.IsChecked == true;

        _settings.Save();
        Theme.ThemeManager.SetMode(Application.Current, _settings.ThemeMode);
        SetAutoStart(_settings.AutoStart);

        _onSaved();
        Close();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Close();

    /// <summary>自绘标题栏拖动窗口。</summary>
    private void TitleBar_MouseLeftDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ButtonState == System.Windows.Input.MouseButtonState.Pressed) DragMove();
    }

    /// <summary>让系统给窗口四角绘制圆角（与主窗口一致）。</summary>
    private static void ApplyCornerPreference(IntPtr hwnd)
    {
        try
        {
            const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
            const int DWMWCP_ROUND = 2;
            var pref = DWMWCP_ROUND;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref pref, sizeof(int));
            const int DWMWA_TRANSITIONS_FORCEDISABLED = 3;
            var disabled = 0;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_TRANSITIONS_FORCEDISABLED, ref disabled, sizeof(int));
        }
        catch { /* 老系统不支持则保持直角 */ }
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    /// <summary>写入/删除当前用户的「开机自启动」注册表项。</summary>
    private static void SetAutoStart(bool enable)
    {
        const string valueName = "OrangeGO";
        try
        {
            using var key = Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run");
            if (enable)
            {
                var exe = Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule?.FileName;
                if (!string.IsNullOrEmpty(exe)) key.SetValue(valueName, $"\"{exe}\"");
            }
            else key.DeleteValue(valueName, false);
        }
        catch { /* 自启设置失败不阻塞 */ }
    }
}