using System.IO;
using System.Windows;
using System.Windows.Threading;

namespace OrangeGO.Windows;

/// <summary>Interaction logic for App.xaml</summary>
public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);
        // 全局崩溃捕获：任何未处理异常都写入 %AppData%\OrangeGO\crash.log 供排查（含闪退）
        DispatcherUnhandledException += (_, args) =>
        {
            LogCrash(args.Exception);
            args.Handled = true;
        };
        AppDomain.CurrentDomain.UnhandledException += (_, args) =>
            LogCrash(args.ExceptionObject as Exception);
        Theme.ThemeManager.Start(this);
        // 启动即按上次语言设置应用界面语言（follow → 跟随系统）
        Localization.LangManager.Apply(this, Core.AppSettings.Load().Language);
    }

    internal static void LogCrash(Exception? ex)
    {
        try
        {
            var dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "OrangeGO");
            Directory.CreateDirectory(dir);
            var line = $"[{DateTime.Now:yyyy-MM-dd HH:mm:ss}]{Environment.NewLine}{ex}{Environment.NewLine}{new string('-', 60)}";
            File.AppendAllText(Path.Combine(dir, "crash.log"), line);
        }
        catch { /* 日志失败不阻塞 */ }
    }
}