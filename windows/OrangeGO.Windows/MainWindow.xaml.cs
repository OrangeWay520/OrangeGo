using System.IO;
using System.Net;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using Microsoft.Win32;
using OrangeGO.Windows.Core;

namespace OrangeGO.Windows;

public partial class MainWindow : Window
{
    private readonly MainViewModel _vm = new();
    private readonly DiscoReader _discovery;
    private MdnsDiscovery _mdns;
    private readonly ReceiveServer _server;
    private readonly SenderClient _sender;
    private readonly System.Windows.Threading.DispatcherTimer _staleTimer;
    private readonly System.Windows.Forms.NotifyIcon _tray;

    private readonly string _deviceId;
    private string _deviceName;
    private string _saveDir;
    private readonly AppSettings _settings;
    // 进行中的发送任务 → 取消令牌，供 UI 发起取消
    private readonly Dictionary<TransferItem, CancellationTokenSource> _activeCts = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _vm;

        // 窗口四角圆角（DWM 系统级圆角，避免 AllowsTransparency 性能损耗）
        SourceInitialized += (_, _) =>
            ApplyCornerPreference(new System.Windows.Interop.WindowInteropHelper(this).Handle);

        _settings = AppSettings.Load();
        _deviceId = "og_" + Guid.NewGuid().ToString("N")[..8];
        _deviceName = _settings.DeviceName;
        _saveDir = _settings.SaveDir;
        Directory.CreateDirectory(_saveDir);

        // 应用保存的外观模式；后续可在设置里手动切换
        Theme.ThemeManager.SetMode(Application.Current, _settings.ThemeMode);

        // 身份回调（三端一致的 DeviceInfo）
        Func<Protocol.DeviceInfo> identity = () => new Protocol.DeviceInfo
        {
            MessageType = Protocol.DiscoveryRequest,
            DeviceId = _deviceId,
            Name = _deviceName
        };

        _sender = new SenderClient(identity);
        _server = new ReceiveServer { OnIncoming = OnIncomingAsync, OnMessage = OnTextMessageAsync };

        // UDP 广播 + mDNS 两类发现结果合并进同一设备表；离线统一超时判定
        _discovery = new DiscoReader(identity);
        _discovery.PeersChanged += peers => Dispatcher.Invoke(() =>
        {
            foreach (var p in peers) UpsertPeer(p.DeviceId, p.Name, p.Ip, p.Port);
        });

        _mdns = new MdnsDiscovery(_deviceName, _deviceId);
        _mdns.PeersChanged += OnMdnsChanged;
        _mdns.PeerLost += OnMdnsLost;

        _staleTimer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(5) };
        _staleTimer.Tick += (_, _) =>
        {
            var cutoff = DateTime.UtcNow - TimeSpan.FromSeconds(15);
            foreach (var gone in _vm.Peers.Where(x => x.LastSeen < cutoff).ToList())
                _vm.Peers.Remove(gone);
        };
        _staleTimer.Start();

        _vm.Incoming.CollectionChanged += (_, _) => UpdateIncomingBadge();

        _tray = BuildTrayIcon();

        string recv = "可接收文件";
        var listening = true;
        try { _server.Start(); }
        catch (Exception ex)
        {
            // 非管理员绑定 *:port 可能抛 AccessDenied，优雅降级：仅保留发现
            listening = false;
            recv = ex.Message;
        }
        _discovery.BroadcastNow();
        _vm.Status = listening
            ? $"监听 {Protocol.Port} 端口，可以接收文件"
            : $"监听 {Protocol.Port} 端口，接收不可用（{recv}）";
        _vm.ReceiveDirHint = $"收件：{_saveDir}";

        Closed += (_, _) => _tray.Dispose();
    }

    // ---------- 窗口控制 ----------
    /// <summary>标题栏左键：拖拽窗口；单击不处理，双击最大化。</summary>
    private void TitleBar_MouseLeftDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleMaximized();
            return;
        }
        if (e.LeftButton == MouseButtonState.Pressed)
            DragMove();
    }

    private void MinButton_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void MaxButton_Click(object sender, RoutedEventArgs e) => ToggleMaximized();

    private void ToggleMaximized()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        // 切换最大化/还原图标
        MaxGlyph.Text = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";
    }

    private void CloseButton_Click(object sender, RoutedEventArgs e) => Close();

    // ---------- 托盘 ----------
    private void ShowFromTray()
    {
        Show();
        ShowInTaskbar = true;
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();
    }

    private System.Windows.Forms.NotifyIcon BuildTrayIcon()
    {
        var icon = CreateTrayIcon();
        var tray = new System.Windows.Forms.NotifyIcon
        {
            Icon = icon,
            Text = "OrangeGO 局域网互传",
            Visible = true
        };
        tray.DoubleClick += (_, _) => Dispatcher.Invoke(ShowFromTray);

        var menu = new System.Windows.Forms.ContextMenuStrip();
        menu.Items.Add("显示主界面", null, (_, _) => Dispatcher.Invoke(ShowFromTray));
        menu.Items.Add("退出", null, (_, _) =>
        {
            Dispatcher.Invoke(() => { _discovery.Dispose(); _mdns.Dispose(); _server.Dispose(); _sender.Dispose(); Close(); });
        });
        tray.ContextMenuStrip = menu;

        // 清理例程图标句柄
        Unloaded += (_, _) => icon?.Dispose();
        return tray;
    }

    private static System.Drawing.Icon? CreateTrayIcon()
    {
        using var bmp = new System.Drawing.Bitmap(32, 32);
        using (var g = System.Drawing.Graphics.FromImage(bmp))
        {
            g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
            var brush = new System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(47, 111, 237));
            var white = new System.Drawing.SolidBrush(System.Drawing.Color.White);
            g.FillRoundedRectangle(brush, new System.Drawing.Rectangle(0, 0, 32, 32), 8);
            using var font = new System.Drawing.Font("Segoe UI", 16, System.Drawing.FontStyle.Bold, System.Drawing.GraphicsUnit.Pixel);
            g.DrawString("O", font, white, new System.Drawing.PointF(6, 3));
        }
        return System.Drawing.Icon.FromHandle(bmp.GetHicon());
    }

    // ---------- 设备表同步 ----------
    private void OnMdnsChanged(IReadOnlyCollection<DiscoReader.PeerInfo> peers)
        => Dispatcher.Invoke(() => { foreach (var p in peers) UpsertPeer(p.DeviceId, p.Name, p.Ip, p.Port); });
    private void OnMdnsLost(string id) => Dispatcher.Invoke(() => RemovePeer(id));

    private void UpsertPeer(string deviceId, string name, IPAddress ip, int port)
    {
        var item = _vm.Peers.FirstOrDefault(x => x.DeviceId == deviceId);
        if (item is null)
        {
            item = new PeerItem { DeviceId = deviceId, Name = name, Ip = ip.ToString(), Port = port };
            _vm.Peers.Add(item);
        }
        else { item.Name = name; item.Ip = ip.ToString(); item.Port = port; item.Status = "在线"; }
        item.LastSeen = DateTime.UtcNow;
    }

    private void RemovePeer(string deviceId)
    {
        var item = _vm.Peers.FirstOrDefault(x => x.DeviceId == deviceId);
        if (item is not null) _vm.Peers.Remove(item);
    }

    // ---------- 应用内接收卡片 ----------
    /// <summary>在后台线程被调用：入队卡片并挂起，等用户点接受/拒绝后返回保存目录。</summary>
    private Task<string?> OnIncomingAsync(ReceiveServer.ReceiveSession session)
    {
        var tcs = new TaskCompletionSource<string?>();
        Dispatcher.BeginInvoke(() =>
        {
            var card = new IncomingItem
            {
                SendId = session.SendId,
                DeviceName = session.Payload.Name,
                FileCount = session.FileCount,
                TotalBytes = session.TotalBytes,
                Completion = tcs
            };
            _vm.Incoming.Add(card);
            UpdateIncomingBadge();
        });
        return tcs.Task;
    }

    private void AcceptClick(object sender, RoutedEventArgs e)
        => ResolveIncoming((sender as Button)?.Tag as IncomingItem, _saveDir);
    private void RejectClick(object sender, RoutedEventArgs e)
        => ResolveIncoming((sender as Button)?.Tag as IncomingItem, null);

    private void ResolveIncoming(IncomingItem? card, string? saveDir)
    {
        if (card is null) return;
        _vm.Incoming.Remove(card);
        card.Completion.TrySetResult(saveDir);
        UpdateIncomingBadge();
    }

    private void UpdateIncomingBadge()
    {
        var n = _vm.Incoming.Count;
        IncomingBadgeText.Text = n.ToString();
        IncomingBadge.Visibility = n > 0 ? Visibility.Visible : Visibility.Collapsed;
    }

    // ---------- 发送 ----------
    private void SendButton_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.SelectedPeer is not PeerItem peer) return;
        var dlg = new OpenFileDialog { Multiselect = true, Title = "选择要发送的文件" };
        if (dlg.ShowDialog() == true && dlg.FileNames.Length > 0)
            SendFiles(peer, dlg.FileNames);
    }

    private async void SendFiles(PeerItem peer, IReadOnlyList<string> files)
    {
        var sendId = Guid.NewGuid().ToString("N");
        var trans = new TransferItem
        {
            Name = files.Count == 1 ? Path.GetFileName(files[0]) : $"{files.Count} 个文件",
            Direction = "发送",
            Target = peer.Name
        };
        _vm.Transfers.Add(trans);
        if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;

        var ip = IPAddress.Parse(peer.Ip);
        // 取消令牌：由 CancelSend 触发
        var cts = new CancellationTokenSource();
        _activeCts[trans] = cts;
        var ct = cts.Token;
        try
        {
            // fileIds 与 files 同序，用于把接收方返回的 fileId→token 反向映射到原路径
            var fileIds = new List<string>();
            var prepared = await _sender.PrepareAsync(ip, peer.Port, sendId, files, fileIds);
            if (prepared is null || prepared.Tokens.Count == 0)
            {
                trans.State = "被拒";
                return;
            }
            // fid → 原路径
            var idToPath = new Dictionary<string, string>();
            for (int i = 0; i < files.Count; i++) idToPath[fileIds[i]] = files[i];

            double total = 0; foreach (var f in files) total += new FileInfo(f).Length;
            long done = 0;
            foreach (var (fid, ftoken) in prepared.Tokens)
            {
                var f = idToPath[fid];
                var len = new FileInfo(f).Length;
                await _sender.SendFileV2Async(ip, peer.Port, prepared.SessionId, fid, ftoken, f,
                    p => trans.Progress = (done + len * p) / total, ct);
                done += len;
            }
            trans.Progress = 1;
            trans.State = "完成";
        }
        catch (OperationCanceledException)
        {
            trans.State = "已取消";
        }
        catch (Exception ex)
        {
            trans.State = ct.IsCancellationRequested ? "已取消" : "失败";
            if (!ct.IsCancellationRequested)
                MessageBox.Show($"发送失败：{ex.Message}", "OrangeGO", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
        finally
        {
            _activeCts.Remove(trans);
        }
    }

    /// <summary>取消某个进行中的发送任务。</summary>
    private void CancelSend(TransferItem trans)
    {
        if (_activeCts.TryGetValue(trans, out var cts)) cts.Cancel();
    }

    /// <summary>传输记录行的"取消"按钮点击。</summary>
    private void CancelTransfer_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        if (((System.Windows.FrameworkElement)sender).DataContext is TransferItem item && item.Direction == "发送")
            CancelSend(item);
    }

    // ---------- 文字消息 ----------
    /// <summary>设备页"发送文字"按钮：读取输入框文字，直发文本消息命令（不打包成 txt 文件）。</summary>
    private async void SendText_Click(object sender, RoutedEventArgs e)
    {
        if (_vm.SelectedPeer is not PeerItem peer) return;
        var text = MsgInput.Text?.Trim() ?? "";
        if (text.Length == 0) return;
        MsgInput.Clear();
        await SendTextAsync(peer, text);
    }

    private async Task SendTextAsync(PeerItem peer, string text)
    {
        var msg = new Protocol.TextMessage
        {
            SendId = Guid.NewGuid().ToString("N"),
            DeviceId = _deviceId,
            Name = _deviceName,
            Content = text,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        var trans = new TransferItem { Name = "文字", Direction = "发送", Target = peer.Name, IsText = true, Content = text };
        _vm.Transfers.Add(trans);
        if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;

        var ip = IPAddress.Parse(peer.Ip);
        try
        {
            await _sender.SendMessageAsync(ip, peer.Port, msg);
            trans.State = "完成";
        }
        catch (Exception ex)
        {
            trans.State = "失败";
            MessageBox.Show($"发送失败：{ex.Message}", "OrangeGO", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    /// <summary>收到文本消息：生成接收文字气泡记录（不落盘、不建会话）。在服务器线程被调用。</summary>
    private Task OnTextMessageAsync(Protocol.TextMessage msg)
    {
        Dispatcher.BeginInvoke(() =>
        {
            _vm.Transfers.Add(new TransferItem
            {
                Name = "文字",
                Direction = "接收",
                Target = string.IsNullOrEmpty(msg.Name) ? msg.DeviceId : msg.Name,
                IsText = true,
                Content = msg.Content,
                State = "完成"
            });
        });
        return Task.CompletedTask;
    }

    /// <summary>文字气泡"保存为 .txt"菜单项：把消息内容保存到用户下载目录。</summary>
    private void SaveText_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as System.Windows.FrameworkElement)?.DataContext is TransferItem item && item.IsText)
            SaveTextToFile(item);
    }

    private void SaveTextToFile(TransferItem item)
    {
        try
        {
            var downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
            Directory.CreateDirectory(downloads);
            var stamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
            var path = Path.Combine(downloads, $"OrangeGO_消息_{stamp}.txt");
            File.WriteAllText(path, item.Content ?? "");
            MessageBox.Show($"已保存到：{path}", "OrangeGO", MessageBoxButton.OK, MessageBoxImage.Information);
        }
        catch (Exception ex)
        {
            MessageBox.Show($"保存失败：{ex.Message}", "OrangeGO", MessageBoxButton.OK, MessageBoxImage.Warning);
        }
    }

    // ---------- 拖拽发送 ----------
    private void DeviceArea_DragOver(object sender, System.Windows.DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop) && _vm.SelectedPeer != null
            ? DragDropEffects.Copy : DragDropEffects.None;
        e.Handled = true;
    }
    private void DeviceArea_Drop(object sender, System.Windows.DragEventArgs e)
    {
        e.Handled = true;
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] files || files.Length == 0) return;
        if (_vm.SelectedPeer is PeerItem peer) SendFiles(peer, files);
        else MessageBox.Show("请先点击选中一台目标设备，再拖入文件。", "OrangeGO", MessageBoxButton.OK, MessageBoxImage.Information);
    }
    private void DeviceTile_Drop(object sender, System.Windows.DragEventArgs e)
    {
        e.Handled = true;
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] files || files.Length == 0) return;
        if ((sender as FrameworkElement)?.DataContext is PeerItem peer) SendFiles(peer, files);
    }

    // ---------- 其它 ----------
    private void IncomingCard_Enter(object sender, RoutedEventArgs e) { /* 预留卡片强调动效 */ }
    private void RefreshClick(object sender, RoutedEventArgs e) => _discovery.BroadcastNow();

    // ---------- 设置 ----------
    private void OpenSettings_Click(object sender, RoutedEventArgs e)
    {
        var w = new SettingsWindow(_settings, ApplySettings) { Owner = this };
        w.ShowDialog();
    }

    /// <summary>设置保存后主窗口应用新值。</summary>
    private void ApplySettings()
    {
        if (_saveDir != _settings.SaveDir)
        {
            _saveDir = _settings.SaveDir;
            Directory.CreateDirectory(_saveDir);
            _vm.ReceiveDirHint = $"收件：{_saveDir}";
        }
        if (_deviceName != _settings.DeviceName)
        {
            _deviceName = _settings.DeviceName;
            RecreateMdns();
        }
    }

    /// <summary>设备别名变化后，重建 mDNS 通告（非受影响端无需重建）。</summary>
    private void RecreateMdns()
    {
        var old = _mdns;
        _mdns = new MdnsDiscovery(_deviceName, _deviceId);
        _mdns.PeersChanged += OnMdnsChanged;
        _mdns.PeerLost += OnMdnsLost;
        old.Dispose();
    }

    /// <summary>让系统给窗口四角绘制圆角（DWMWCP_ROUND），配合系统原生标题栏更美观。</summary>
    private static void ApplyCornerPreference(IntPtr hwnd)
    {
        try
        {
            const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
            const int DWMWCP_ROUND = 2;
            var pref = DWMWCP_ROUND;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref pref, sizeof(int));
        }
        catch { /* 老系统不支持则保持直角 */ }
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);
}

// 扩展：给 Graphics 增加圆角矩形填充，便于托盘图标绘制
internal static class GdiExtensions
{
    public static void FillRoundedRectangle(this System.Drawing.Graphics g, System.Drawing.Brush brush,
        System.Drawing.Rectangle rect, int radius)
    {
        using var path = new System.Drawing.Drawing2D.GraphicsPath();
        path.AddRoundedRect(rect, radius);
        g.FillPath(brush, path);
    }
    private static void AddRoundedRect(this System.Drawing.Drawing2D.GraphicsPath gp, System.Drawing.Rectangle r, int radius)
    {
        int d = radius * 2;
        gp.AddArc(r.X, r.Y, d, d, 180, 90);
        gp.AddArc(r.Right - d, r.Y, d, d, 270, 90);
        gp.AddArc(r.Right - d, r.Bottom - d, d, d, 0, 90);
        gp.AddArc(r.X, r.Bottom - d, d, d, 90, 90);
        gp.CloseFigure();
    }
}