using System.Windows;

namespace OrangeGO.Windows;

/// <summary>Interaction logic for App.xaml</summary>
public partial class App : System.Windows.Application
{
    protected override void OnStartup(System.Windows.StartupEventArgs e)
    {
        base.OnStartup(e);
        Theme.ThemeManager.Start(this);
    }
}