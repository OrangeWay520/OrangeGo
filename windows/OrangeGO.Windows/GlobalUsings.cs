// 因启用 UseWindowsForms，System.Windows.Forms 被隐式全局引入导致类型冲突。
// 这里用全局别名统一固定到 WPF/对应命名空间版本。
global using Application = System.Windows.Application;
global using Button = System.Windows.Controls.Button;
global using MessageBox = System.Windows.MessageBox;
global using DragEventArgs = System.Windows.DragEventArgs;
global using DataFormats = System.Windows.DataFormats;
global using DragDropEffects = System.Windows.DragDropEffects;
global using OpenFileDialog = Microsoft.Win32.OpenFileDialog;