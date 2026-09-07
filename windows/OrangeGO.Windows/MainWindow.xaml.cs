using System.Net;
using System.Net.Http;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Interop;
using System.Text;
using System.Windows.Documents;
using System.Diagnostics;
using System.IO;
using System.Linq;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using SharpVectors.Converters;
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
    private readonly LsSender _lsSender;
    private readonly FingerprintStore _fpStore = new();
    private readonly System.Windows.Threading.DispatcherTimer _staleTimer;
    private readonly System.Windows.Forms.NotifyIcon _tray;
    private HwndSource? _hwndSource;   // 保持窗口句柄消息钩子（WM_GETMINMAXINFO）存活

    private readonly string _deviceId;
    private string _deviceName;
    private string _saveDir;
    private readonly AppSettings _settings;
    // 设置页加载/恢复期间为 true：抑制主题单选、语言下拉等 Checked/SelectionChanged 触发实时持久化
    private bool _suppressSettings;
    // 进入设置前的导航主页（"Transfer"/"Devices"）：再按设置键关闭设置页时回到该主页，避免留下空白
    private string _navBeforeSettings = "Transfer";
    // 进行中的发送任务 → 取消令牌，供 UI 发起取消
    private readonly Dictionary<TransferItem, CancellationTokenSource> _activeCts = new();
    // 接收中的文件：落盘路径(dest) → 传输条目，用于实时进度更新与完成去重。
    private readonly Dictionary<string, TransferItem> _recvItems = new();
    // 拖放看门狗：事件死区（窗口缩放边框等非客户区、无元素空白处）不派发拖放事件，
    // 提示会在那里残留；改为轮询鼠标按键+光标位置兜底清理。
    private System.Windows.Threading.DispatcherTimer? _dragWatch;

    // 接收中的多文件批次（sendId → 已累积该批次的介质小节）：
    // 同一 sendId 的多个文件共用一个 TransferItem，在「文件集成显示」开启时合并成一个气泡展示。
    private readonly Dictionary<string, ReceiveBatch> _recvBatches = new();

    public MainWindow()
    {
        InitializeComponent();
        DataContext = _vm;

        // 收藏星标：ToggleButton 的 Click 已被其自身标记处理，需以 handledEventsToo=true
        // 的类处理器捕获。仅对携带 PeerItem 数据上下文（即设备磁贴星标）的点击真正处理。
        EventManager.RegisterClassHandler(typeof(System.Windows.Controls.Primitives.ToggleButton),
            System.Windows.Controls.Primitives.ToggleButton.ClickEvent,
            new RoutedEventHandler(ToggleFavorite_Click), true);

        // 统一拖放：类处理器同时覆盖隧道(Preview)与冒泡两个阶段。
        // 关键根因：TextBox 内置拖放(TextBoxBase.OnDragOver)是类处理器、必定执行，
        // 且发生在冒泡阶段，会把文件拖放的光标效果覆盖成 None(禁止)。
        // 因此必须在冒泡阶段也注册一次（位于其后执行），最终写回 Copy。
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.PreviewDragOverEvent,
            new System.Windows.DragEventHandler(GlobalDragOver), true);
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.DragOverEvent,
            new System.Windows.DragEventHandler(GlobalDragOver), true);
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.PreviewDragEnterEvent,
            new System.Windows.DragEventHandler(GlobalDragOver), true);
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.DragEnterEvent,
            new System.Windows.DragEventHandler(GlobalDragOver), true);
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.DropEvent,
            new System.Windows.DragEventHandler(GlobalDrop), true);
        EventManager.RegisterClassHandler(typeof(UIElement), UIElement.DragLeaveEvent,
            new System.Windows.DragEventHandler(GlobalDragLeave), true);
        // 文本框实例级兜底：实例处理器在其内置类处理器之后执行，最终改回 Copy
        ChatInput.AddHandler(UIElement.DragOverEvent,
            new System.Windows.DragEventHandler(ChatInput_DragFinalize), true);
        ChatInput.AddHandler(UIElement.DropEvent,
            new System.Windows.DragEventHandler(ChatInput_DropFinalize), true);
        // 拖放被 Esc 取消等边缘情况：任意点击清除残留提示与遮罩
        PreviewMouseLeftButtonDown += (_, _) => { HideDragHint(); DropOverlay.Visibility = Visibility.Collapsed; };

        // 窗口四角圆角（DWM 系统级圆角，避免 AllowsTransparency 性能损耗）
        SourceInitialized += (_, _) =>
        {
            var hwnd = new System.Windows.Interop.WindowInteropHelper(this).Handle;
            ApplyCornerPreference(hwnd);
            // WindowStyle=None + WindowChrome 时，系统按“整个屏幕”最大化会盖住任务栏；
            // 用 WM_GETMINMAXINFO 把最大化区域限制到当前显示器工作区
            _hwndSource = System.Windows.Interop.HwndSource.FromHwnd(hwnd);
            _hwndSource?.AddHook(WindowProc);
        };

        // 系统最大化/还原（如贴边、快捷键）同步图标
        _settings = AppSettings.Load();
        Core.AppSettings.Instance = _settings;
        _deviceId = _settings.DeviceId;
        if (string.IsNullOrWhiteSpace(_deviceId))
        {
            // 首启：生成本机稳定身份并持久化，之后逐次复用（收藏夹/白名单端到端稳定，不受 IP 变动影响）
            _deviceId = "og_" + Guid.NewGuid().ToString("N")[..8];
            _settings.DeviceId = _deviceId;
            _settings.Save();
        }
        _deviceName = _settings.DeviceName;
        _saveDir = _settings.SaveDir;
        Directory.CreateDirectory(_saveDir);

        // 加载窗口内设置页控件值
        LoadSettingsUi();

        // 初始化收藏夹（白名单）：面板 + 已在列表中的磁贴星标
        RefreshFavoritesUi();

        // 恢复上次会话的传输记录（安卓端同款"保存历史"逻辑），并接入实时持久化
        RestoreTransferHistory();
        SubscribeTransferPersistence();
        // 自动清除：启动即按设置的档位清理一次历史
        if (_settings.AutoCleanupDays > 0) ApplyCleanup(_settings.AutoCleanupDays);

        // 应用保存的外观模式；后续可在设置里手动切换
        Theme.ThemeManager.SetMode(Application.Current, _settings.ThemeMode);

        // 身份回调（三端一致的 DeviceInfo）
        Func<Protocol.DeviceInfo> identity = () => new Protocol.DeviceInfo
        {
            MessageType = Protocol.DiscoveryRequest,
            DeviceId = _deviceId,
            Name = _deviceName
        };

        _sender = new SenderClient(identity, fpStore: _fpStore);
        // LocalSend 发送客户端：出示自签客户端证书 + 指纹钉扎，见 LsSender 安全说明
        _lsSender = new LsSender(_deviceName, "OrangeGO Desktop / Windows", Protocol.Port);
        _server = new ReceiveServer { OnIncoming = OnIncomingAsync, OnMessage = OnTextMessageAsync, OnRecall = OnRecallAsync, OnFileReceived = OnFileReceivedAsync, OnFileStarted = OnFileStartedAsync, OnFileProgress = OnFileProgressAsync, OnCanceled = OnCanceledAsync, OnFileFailed = OnFileFailedAsync };

        // LocalSend 兼容：发现由 DiscoReader 承载（复用 53317 socket），指纹统一用证书指纹。

        // info 暴露 TLS 证书指纹，供 LocalSend 客户端校验/识别（对齐其安全模型）
        _server.LocalAlias = _deviceName;
        _server.LocalFingerprint = LsCompat.ServerFingerprint;
        _server.LocalDeviceModel = "OrangeGO Desktop / Windows";
        // 手机 LocalSend 收到我们的组播通告后会 POST /register：既确认它在线，也让我们出现在它的设备列表
        _server.OnLocalSendRegister = d => Dispatcher.Invoke(() => UpsertLocalSend(d));

        // UDP 广播 + mDNS 两类发现结果合并进同一设备表；离线统一超时判定
        _discovery = new DiscoReader(identity);
        _discovery.LocalSendAlias = _deviceName;
        // 指纹必须与 /info 及 TLS 证书一致，否则 iOS LocalSend 判定不可信而不显示本机
        _discovery.LocalSendFingerprint = LsCompat.ServerFingerprint;
        _discovery.LocalSendDeviceModel = "OrangeGO Desktop / Windows";
        _discovery.LocalSendPeersChanged += devices => Dispatcher.Invoke(() =>
        {
            foreach (var d in devices) UpsertLocalSend(d);
        });
        _discovery.PeersChanged += peers => Dispatcher.Invoke(() =>
        {
            foreach (var p in peers) UpsertPeer(p.DeviceId, p.Name, p.Ip, p.Port);
        });

        _mdns = new MdnsDiscovery(_deviceName, _deviceId);
        _mdns.PeersChanged += OnMdnsChanged;
        // 不订阅 mDNS 的即时“下线”事件：ServiceLost 会对仍在线（仅某次通告未刷到）的设备误触发，
        // 造成卡片闪跳；统一交给上面 _staleTimer 的较长离线阈值清除。

        _staleTimer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromSeconds(5) };
        _staleTimer.Tick += (_, _) =>
        {
            // 离线阈值与发现广播周期保持足够余量：过紧（如 15s）会在个别广播丢包/间隔稍长时
            // 反复“移除→重新发现”，表现为设备卡隔十几秒消失再出现。LocalSend 由 DiscoReader 另行清除。
            var cutoff = DateTime.UtcNow - TimeSpan.FromSeconds(45);
            foreach (var gone in _vm.Peers.Where(x => !x.IsLocalSend && x.LastSeen < cutoff).ToList())
                _vm.Peers.Remove(gone);
        };
        _staleTimer.Start();

        _vm.Incoming.CollectionChanged += (_, _) => UpdateIncomingBadge();

        BuildEmojiPanel(); // 初始化表情面板的分类与页面

        _tray = BuildTrayIcon();

        _discovery.BroadcastNow(); // 立即同时广播 OrangeGO 通告 + LocalSend 存在，供 iOS 尽快发现

        // 启动本地接收服务（P2P 接收端）。
        try { _server.Start(); }
        catch (Exception ex)
        {
            // 监听失败：仅记录于调试输出。
            System.Diagnostics.Debug.WriteLine("[OrangeGO] listener start failed: " + ex.Message);
        }

        // 入站防火墙放行（best-effort）：缺失时自动注册一条 53317 端口放行规则，详见 FirewallHelper。
        Core.FirewallHelper.EnsureInboundAllowed(Protocol.Port);
    }

    // ---------- 窗口控制 ----------
    /// <summary>标题栏左键：拖拽窗口；单击不处理，双击最大化/还原。</summary>
    private void TitleBar_MouseLeftDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2) { ToggleMaximized(); return; }
        if (e.LeftButton == MouseButtonState.Pressed) DragMove();
    }

    private void MinButton_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void MaxButton_Click(object sender, RoutedEventArgs e) => ToggleMaximized();

    /// <summary>最大化/还原：交给系统原生切换（与微信一致，由系统提供窗口过渡动画）。
    /// 无边框窗口的最大化尺寸被 WM_GETMINMAXINFO 限制在工作区内，不会盖住任务栏。</summary>
    private void ToggleMaximized()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
        UpdateMaxGlyph();
    }

    private void UpdateMaxGlyph()
        => MaxGlyph.Text = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";

    private void CloseButton_Click(object sender, RoutedEventArgs e)
    {
        // 开启"关闭时最小化到系统托盘"则隐藏到托盘，否则退出
        if (_settings.MinimizeToTray) HideToTray();
        else Close();
    }

    private void HideToTray()
    {
        Hide();
        ShowInTaskbar = false;
    }
    /// <summary>导航切换时的页面淡入动画（由"动画效果"开关控制）。</summary>
    private void Nav_Checked(object sender, RoutedEventArgs e)
    {
        // 切到传输/设备时，关闭设置齿轮的选中态，让设置页隐藏。
        // 注意：XAML 构造阶段 NavTransfer 默认 IsChecked=True 会先触发本回调，
        // 此时 GearButton 尚未创建，必须判空，否则启动即抛 NullReferenceException。
        if (GearButton is not null) GearButton.IsChecked = false;
        if (_settings is null || !_settings.Animations) return;
        var target = ReferenceEquals(sender, NavTransfer) ? TransferPage : DevicePage;
        FadeInPage(target);
    }

    /// <summary>齿轮打开设置页：记住来自哪个主页，取消导航高亮，淡入设置页。</summary>
    private void Gear_Checked(object sender, RoutedEventArgs e)
    {
        if (NavTransfer.IsChecked == true) _navBeforeSettings = "Transfer";
        else if (NavDevices.IsChecked == true) _navBeforeSettings = "Devices";
        NavTransfer.IsChecked = false;
        NavDevices.IsChecked = false;
        if (_settings is not null && _settings.Animations)
            FadeInPage(SettingsPage);
    }

    private void FadeInPage(System.Windows.UIElement target)
    {
        target.BeginAnimation(UIElement.OpacityProperty, null);
        target.Opacity = 0;
        target.BeginAnimation(UIElement.OpacityProperty,
            new System.Windows.Media.Animation.DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(160)));
    }

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
            Text = Localization.LangManager.T("Tray.Tooltip"),
            Visible = true
        };
        tray.DoubleClick += (_, _) => Dispatcher.Invoke(ShowFromTray);

        var menu = new System.Windows.Forms.ContextMenuStrip();
        menu.Items.Add(Localization.LangManager.T("Tray.Show"), null, (_, _) => Dispatcher.Invoke(ShowFromTray));
        menu.Items.Add(Localization.LangManager.T("Tray.Exit"), null, (_, _) => ExitApplication());
        tray.ContextMenuStrip = menu;

        // 窗口关闭时清理托盘；图标句柄在托盘销毁时一并释放
        Closed += (_, _) =>
        {
            try { _tray.Visible = false; _tray.Dispose(); }
            catch { }
        };
        return tray;
    }

    /// <summary>托盘菜单"退出"：先让托盘不可见，再立即关窗退出。
    /// 网络资源在进程退出时统一回收，避免在 UI 线程上做耗时 Dispose 造成"先消失、过一会儿才关窗"的延时。</summary>
    private void ExitApplication()
    {
        try { _tray.Visible = false; _tray.ContextMenuStrip = null; }
        catch { }
        Close();
    }

    private static System.Drawing.Icon? CreateTrayIcon()
    {
        try
        {
            // 从应用内嵌资源取品牌 logo 生成托盘图标（替代原来手绘的"O"字母）
            var streamInfo = System.Windows.Application.GetResourceStream(
                new Uri("pack://application:,,,/Assets/logo.png", UriKind.Absolute));
            if (streamInfo is null) return null;
            using var src = streamInfo.Stream;
            using var img = System.Drawing.Image.FromStream(src);
            using var bmp = new System.Drawing.Bitmap(img, new System.Drawing.Size(32, 32));
            return System.Drawing.Icon.FromHandle(bmp.GetHicon());
        }
        catch { return null; }
    }

    // ---------- 设备表同步 ----------
    private void OnMdnsChanged(IReadOnlyCollection<DiscoReader.PeerInfo> peers)
        => Dispatcher.Invoke(() => { foreach (var p in peers) UpsertPeer(p.DeviceId, p.Name, p.Ip, p.Port); });

    private PeerItem UpsertPeer(string deviceId, string name, IPAddress ip, int port, bool isLocalSend = false)
    {
        var ipStr = ip.ToString();
        // 同一台机器可能同时发 OrangeGo 通告和 LocalSend 组播 presence（deviceId 不同但 IP 相同）。
        // 以 IP 去重，OrangeGo 身份优先（功能更全：支持文字/撤回），避免同一台 OrangeGo 设备显示成两条。
        var byIp = _vm.Peers.FirstOrDefault(x => x.Ip == ipStr && x.DeviceId != deviceId);
        if (byIp != null && byIp.IsLocalSend && !isLocalSend)
        {
            // 新通告是 OrangeGo，旧的是 LocalSend → 移除旧 LocalSend，新建 OrangeGo 覆盖
            _vm.Peers.Remove(byIp);
        }
        else if (byIp != null && !byIp.IsLocalSend && isLocalSend)
        {
            // 新通告是 LocalSend，旧的是 OrangeGo → 跳过（OrangeGo 优先），返回旧 OrangeGo 条目
            return byIp;
        }

        var item = _vm.Peers.FirstOrDefault(x => x.DeviceId == deviceId);
        if (item is null)
        {
            item = new PeerItem { DeviceId = deviceId, Name = name, Ip = ipStr, Port = port };
            _vm.Peers.Add(item);
        }
        else { item.Name = name; item.Ip = ipStr; item.Port = port; item.Status = "在线"; }
        item.IsLocalSend = isLocalSend;
        item.LastSeen = DateTime.UtcNow;
        // 同步收藏星标：按设备 ID（而非 IP）判定
        item.IsFavorite = _vm.IsFavorite(deviceId);
        return item;
    }

    /// <summary>把探测到的 LocalSend 设备并入设备表（标记 LocalSend 烧标，文件互传可用，文本/语音不适配）。</summary>
    private void UpsertLocalSend(LocalSendDevice d)
    {
        var id = string.IsNullOrEmpty(d.Fingerprint) ? d.Ip.ToString() : d.Fingerprint;
        var peer = UpsertPeer("ogLS_" + id, string.IsNullOrEmpty(d.Alias) ? "LocalSend 设备" : d.Alias, d.Ip, d.Port, isLocalSend: true);
        // 仅当 peer 确为 LocalSend 条目时才设 LS 字段（UpsertPeer 可能因同 IP 已有 OrangeGo 而返回旧 OrangeGo 条目）
        if (peer.IsLocalSend)
        {
            peer.LsFingerprint = d.Fingerprint;
            peer.LsProtocol = string.IsNullOrEmpty(d.Protocol) ? "https" : d.Protocol;
        }
    }

    // ---------- 设备收藏夹（白名单） ----------
    /// <summary>磁贴星标点击：加入/取消收藏，按设备 ID 记（不依赖 IP）。
    /// 通过 ListBoxItem 上的 ToggleButton.Click EventSetter 捕获，原始源携带该磁贴的 PeerItem 数据上下文。</summary>
    private void ToggleFavorite_Click(object sender, RoutedEventArgs e)
    {
        var peer = (e.OriginalSource as FrameworkElement)?.DataContext as PeerItem
                   ?? (sender as FrameworkElement)?.DataContext as PeerItem;
        if (peer is null) return;
        if (_vm.IsFavorite(peer.DeviceId))
            _settings.Favorites.RemoveAll(f => f.Id == peer.DeviceId);
        else
            _settings.Favorites.Add(new Core.FavDevice { Id = peer.DeviceId, Name = peer.Name, Ip = peer.Ip });
        _settings.Save();
        RefreshFavoritesUi();
    }

    /// <summary>收藏面板中点击移除：按设备 ID 移除。</summary>
    private void RemoveFavorite_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is not Core.FavDevice f) return;
        _settings.Favorites.RemoveAll(x => x.Id == f.Id);
        _settings.Save();
        RefreshFavoritesUi();
    }

    /// <summary>把持久化收藏同步到视图模型（刷新面板 + 所有磁贴星标）。</summary>
    private void RefreshFavoritesUi()
        => _vm.RefreshFavorites(_settings.Favorites);

    /// <summary>计算输入字符串的 SHA-256 十六进制小写（用于生成 LocalSend 稳定指纹）。</summary>
    private static string Sha256Hex(string input)
    {
        var bytes = System.Security.Cryptography.SHA256.HashData(System.Text.Encoding.UTF8.GetBytes(input));
        return Convert.ToHexString(bytes).ToLowerInvariant();
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

    /// <summary>接收批次：同一 sendId 的全部文件聚合成一条消息气泡（「文件集成显示」开启时合并展示）。</summary>
    private sealed class ReceiveBatch
    {
        public required string Target { get; init; }
        public required string SendId { get; init; }
        /// <summary>该批次已开始落盘的全部文件路径（按发起顺序）。</summary>
        public List<string> Paths { get; } = new();
        public long Timestamp { get; init; } = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        /// <summary>批次首文件对应的气泡（最终合并时替换为集成气泡）。</summary>
        public TransferItem? FinalItem { get; set; }
        /// <summary>首文件之外每个文件的独立占位气泡（合并时移除）。</summary>
        public Dictionary<string, TransferItem> Slots { get; } = new();
        /// <summary>已落盘完成的文件数。</summary>
        public int Done { get; set; }
        /// <summary>集成模式：接收过程中即显示一个进度气泡（多文件 + 开启文件集成显示时启用）。</summary>
        public bool IsIntegrated { get; set; }
        /// <summary>每个文件的已接收字节数（集成模式下计算整体进度用）。</summary>
        public Dictionary<string, long> ReceivedBytes { get; } = new();
    }

    /// <summary>多文件批次全部完成后：移除各占位气泡，替换为一条集成气泡（文件卡/媒体网格由 MediaPaths 驱动）。</summary>
    private void CommitReceiveBatch(ReceiveBatch b)
    {
        if (b.FinalItem is { } f) _vm.Transfers.Remove(f);
        foreach (var s in b.Slots.Values) _vm.Transfers.Remove(s);
        var merged = new TransferItem
        {
            Name = $"{b.Paths.Count} 个文件",
            Direction = "接收",
            Target = b.Target,
            MediaPath = "",
            MediaPaths = b.Paths.ToList(), // 集成气泡：全部路径→文件卡/媒体网格
            Size = 0,
            SendId = b.SendId,
            Timestamp = b.Timestamp
        };
        merged.Progress = 1.0;
        _vm.Transfers.Add(merged);
        merged.State = "已完成"; // 先入列再置终态，确保持久化包含本条
        foreach (var sub in merged.SubItems)
        {
            if (sub.IsImage || sub.IsVideo)
                LoadThumbAsync(sub, sub.MediaPath);
        }

    }

    private Task OnFileStartedAsync(ReceiveServer.ReceiveSession session, string filePath, long total, string? thumb = null)
    {
        Dispatcher.BeginInvoke(() =>
        {
            var name = System.IO.Path.GetFileName(filePath);
            if (!_recvBatches.TryGetValue(session.SendId, out var batch))
            {
                batch = new ReceiveBatch { Target = session.Payload.Name, SendId = session.SendId };
                _recvBatches[session.SendId] = batch;
            }
            batch.Paths.Add(filePath);
            var isFirst = batch.Paths.Count == 1;
            // 首文件时判定是否走集成模式（多文件 + 开启文件集成显示）
            if (isFirst)
                batch.IsIntegrated = session.FileCount > 1 && Core.AppSettings.Instance?.IntegrateImages == true;
            LsDiscovery.LogDiag($"OnFileStarted paths={batch.Paths.Count} isFirst={isFirst} FileCount={session.FileCount} IntegrateImages={Core.AppSettings.Instance?.IntegrateImages} FilesCount={session.Payload.Files?.Count} IsIntegrated={batch.IsIntegrated} thumbNull={string.IsNullOrEmpty(thumb)} filePath={filePath}");
            if (batch.IsIntegrated)
            {
                // 集成模式：首文件创建进度气泡（含全部缩略图网格），后续文件不创建新气泡
                if (isFirst)
                {
                    var saveDir = System.IO.Path.GetDirectoryName(filePath) ?? "";
                    var allPaths = new List<string>();
                    var thumbMap = new Dictionary<string, string?>();
                    if (session.Payload.Files is { } fmap && fmap.Count > 0)
                    {
                        foreach (var kv in fmap)
                        {
                            var p = System.IO.Path.Combine(saveDir, kv.Value.FileName);
                            allPaths.Add(p);
                            thumbMap[p] = kv.Value.Thumb;
                        }
                    }
                    else
                    {
                        allPaths.Add(filePath);
                        thumbMap[filePath] = thumb;
                    }
                    var trans = new TransferItem
                    {
                        Name = $"{session.FileCount} 个文件",
                        Direction = "接收",
                        Target = session.Payload.Name,
                        MediaPaths = allPaths,
                        Size = session.TotalBytes,
                        TotalBytesForProgress = session.TotalBytes,
                        SendId = session.SendId,
                        Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                    };
                    trans.Progress = 0;
                    trans.State = "进行中";
                    trans.ProgressText = "等待接收...";
                    _vm.Transfers.Add(trans);
                    batch.FinalItem = trans;
                    foreach (var sub in trans.SubItems)
                    {
                        if (thumbMap.TryGetValue(sub.MediaPath, out var t) && !string.IsNullOrEmpty(t))
                        {
                            try
                            {
                                var bytes = Convert.FromBase64String(t);
                                var bmp = new BitmapImage();
                                bmp.BeginInit();
                                bmp.StreamSource = new MemoryStream(bytes);
                                bmp.CacheOption = BitmapCacheOption.OnLoad;
                                bmp.EndInit();
                                bmp.Freeze();
                                sub.Thumb = bmp;
                            }
                            catch { }
                        }
                    }
                }
                batch.ReceivedBytes[filePath] = 0;
            }
            else
            {
                // 非集成模式：每个文件一个独立气泡
                var trans = new TransferItem
                {
                    Name = name,
                    Direction = "接收",
                    Target = session.Payload.Name,
                    MediaPath = filePath,
                    Size = total,
                    TotalBytesForProgress = session.TotalBytes,
                    SendId = session.SendId,
                    Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                };
                trans.Progress = 0;
                trans.State = "进行中";
                trans.ProgressText = "等待接收...";
                if (!string.IsNullOrEmpty(thumb))
                {
                    try
                    {
                        var bytes = Convert.FromBase64String(thumb);
                        var bmp = new BitmapImage();
                        bmp.BeginInit();
                        bmp.StreamSource = new MemoryStream(bytes);
                        bmp.CacheOption = BitmapCacheOption.OnLoad;
                        bmp.EndInit();
                        bmp.Freeze();
                        trans.Thumb = bmp;
                    }
                    catch { }
                }
                _recvItems[filePath] = trans;
                _vm.Transfers.Add(trans);
                if (isFirst) batch.FinalItem = trans;
                else batch.Slots[filePath] = trans;
                if (TransferItem.IsImageExt(name) || TransferItem.IsVideoExt(name))
                    LoadThumbAsync(trans, filePath);
            }
            if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;
        });
        return Task.CompletedTask;
    }

    private Task OnFileProgressAsync(ReceiveServer.ReceiveSession session, string filePath, long written, long total)
    {
        Dispatcher.BeginInvoke(() =>
        {
            if (_recvBatches.TryGetValue(session.SendId, out var batch) && batch.IsIntegrated && batch.FinalItem is { } f)
            {
                // 集成模式：更新进度气泡的整体进度
                batch.ReceivedBytes[filePath] = written;
                var totalReceived = batch.ReceivedBytes.Values.Sum();
                var prog = f.TotalBytesForProgress > 0 ? (double)totalReceived / f.TotalBytesForProgress : 0;
                f.Progress = prog;
                if (prog > 0.02 && prog < 1 && f.TotalBytesForProgress > 0)
                {
                    var elapsed = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - f.Timestamp;
                    if (elapsed > 0)
                    {
                        var remainInstant = elapsed * (1 - prog) / prog;
                        f.RemainEma = f.RemainEma < 0 ? remainInstant : 0.3 * remainInstant + 0.7 * f.RemainEma;
                        f.ProgressText = $"剩余 {FormatDuration((long)f.RemainEma)} · {IncomingItem.FormatBytes(totalReceived)}/{IncomingItem.FormatBytes(f.TotalBytesForProgress)}";
                    }
                }
            }
            else if (_recvItems.TryGetValue(filePath, out var t))
            {
                var prog = total > 0 ? (double)written / total : 1.0;
                t.Progress = prog;
                // 进度文字：剩余时间(EMA 平滑) + 已传输/总大小
                if (prog > 0.02 && prog < 1 && t.TotalBytesForProgress > 0)
                {
                    var elapsed = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - t.Timestamp;
                    if (elapsed > 0)
                    {
                        var remainInstant = elapsed * (1 - prog) / prog;
                        t.RemainEma = t.RemainEma < 0 ? remainInstant : 0.3 * remainInstant + 0.7 * t.RemainEma;
                        var transferred = (long)(t.TotalBytesForProgress * prog);
                        t.ProgressText = $"剩余 {FormatDuration((long)t.RemainEma)} · {IncomingItem.FormatBytes(transferred)}/{IncomingItem.FormatBytes(t.TotalBytesForProgress)}";
                    }
                }
            }
        });
        return Task.CompletedTask;
    }

    private static string FormatDuration(long ms)
    {
        if (ms < 0) return "--";
        var s = ms / 1000;
        return s < 60 ? $"{s}秒" : s < 3600 ? $"{s / 60}分{s % 60}秒" : $"{s / 3600}时{(s % 3600) / 60}分";
    }

    private Task OnFileReceivedAsync(ReceiveServer.ReceiveSession session, string filePath, long size)
    {
        Dispatcher.BeginInvoke(() =>
        {
            var batch = _recvBatches.GetValueOrDefault(session.SendId);
            // 集成模式：更新进度气泡，不查找独立气泡
            if (batch is { IsIntegrated: true } && batch.FinalItem is { } f)
            {
                batch.ReceivedBytes[filePath] = size;
                batch.Done++;
                var totalReceived = batch.ReceivedBytes.Values.Sum();
                f.Progress = f.TotalBytesForProgress > 0 ? (double)totalReceived / f.TotalBytesForProgress : 1.0;
                var integratedTotal = session.FileCount > 0 ? session.FileCount : batch.Paths.Count;
                LsDiscovery.LogDiag($"OnFileReceived integrated Done={batch.Done} integratedTotal={integratedTotal} PathsCount={batch.Paths.Count} MediaPaths={batch.FinalItem?.MediaPaths.Count} IntegratedGrid={batch.FinalItem?.IntegratedGrid} MediaGridItems={batch.FinalItem?.MediaGridItems.Count}");
                if (batch.Done < integratedTotal) return;
                // 全部完成：直接更新进度气泡为已完成（已含缩略图网格，无需移除重建）
                f.Progress = 1.0;
                f.ProgressText = "";
                f.State = "已完成";
                _recvBatches.Remove(session.SendId);
                if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;
                return;
            }
            // 非集成模式：更新该文件独立气泡为完成
            var slot = _recvItems.GetValueOrDefault(filePath);
            if (slot is not null)
            {
                slot.Name = System.IO.Path.GetFileName(filePath);
                slot.Size = size;
                slot.Progress = 1.0;
                slot.ProgressText = "";
                slot.State = "已完成";
                _recvItems.Remove(filePath);
                if (TransferItem.IsImageExt(slot.Name) || TransferItem.IsVideoExt(slot.Name))
                    LoadThumbAsync(slot, filePath);
            }
            if (batch is null)
            {
                // 无批次（不经过 OnFileStarted 的单文件历史兼容路径）：直接补一条完成记录
                if (slot is null)
                {
                    var name = System.IO.Path.GetFileName(filePath);
                    var trans = new TransferItem
                    {
                        Name = name,
                        Direction = "接收",
                        Target = session.Payload.Name,
                        MediaPath = filePath,
                        Size = size,
                        SendId = session.SendId,
                        Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
                    };
                    trans.Progress = 1.0;
                    trans.State = "已完成";
                    _vm.Transfers.Add(trans);
                    if (TransferItem.IsImageExt(name) || TransferItem.IsVideoExt(name))
                        LoadThumbAsync(trans, filePath);
                }
                return;
            }

            batch.Done++;
            // 先尝试用 init 里的总文件数判定是否还有在途文件；仅当文件数未知时退化为按已登记路径数判断
            var totalInBatch = session.FileCount > 0 ? session.FileCount : batch.Paths.Count;
            var pendingInBatch = batch.Done < totalInBatch;
            if (pendingInBatch) return;

            // 批次已全部完成
            if (batch.Paths.Count == 1)
            {
                // 单文件：保留已完成的首文件气泡，直接收尾
                _recvBatches.Remove(session.SendId);
                if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;
                return;
            }
            // 多文件：移除各占位气泡，替换为一条集成气泡
            CommitReceiveBatch(batch);
            _recvBatches.Remove(session.SendId);
            if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;
        });
        return Task.CompletedTask;
    }

    /// <summary>对方取消发送：标"已取消" + 删除已落盘半文件 + 清理批次。</summary>
    private Task OnCanceledAsync(ReceiveServer.ReceiveSession session)
    {
        Dispatcher.BeginInvoke(() =>
        {
            var batch = _recvBatches.GetValueOrDefault(session.SendId);
            if (batch is not null)
            {
                foreach (var p in batch.Paths)
                {
                    try { if (System.IO.File.Exists(p)) System.IO.File.Delete(p); } catch { }
                }
                if (batch.FinalItem is { } f) { f.State = "已取消"; f.ProgressText = ""; }
                foreach (var s in batch.Slots.Values) { s.State = "已取消"; s.ProgressText = ""; }
                _recvBatches.Remove(session.SendId);
            }
            foreach (var kv in _recvItems.ToList())
            {
                if (kv.Value.SendId == session.SendId)
                {
                    kv.Value.State = "已取消";
                    kv.Value.ProgressText = "";
                    _recvItems.Remove(kv.Key);
                }
            }
        });
        return Task.CompletedTask;
    }

    /// <summary>传输失败：标"失败" + 删半文件。</summary>
    private Task OnFileFailedAsync(ReceiveServer.ReceiveSession session, string filePath)
    {
        Dispatcher.BeginInvoke(() =>
        {
            try { if (System.IO.File.Exists(filePath)) System.IO.File.Delete(filePath); } catch { }
            if (_recvItems.TryGetValue(filePath, out var t))
            {
                t.State = "失败";
                t.ProgressText = "";
                _recvItems.Remove(filePath);
            }
            // 集成模式：标进度气泡为"失败"
            if (_recvBatches.TryGetValue(session.SendId, out var batch) && batch.IsIntegrated && batch.FinalItem is { } f)
            {
                f.State = "失败";
                f.ProgressText = "";
            }
        });
        return Task.CompletedTask;
    }

    // ---------- 传输记录历史（持久化，重启不丢） ----------
    /// <summary>恢复上次会话的传输记录（与安卓端"保存历史"逻辑对齐）。</summary>
    private void RestoreTransferHistory()
    {
        foreach (var t in Core.TransferHistory.Load())
        {
            _vm.Transfers.Add(t);
            LoadRecordThumbs(t);
        }
    }

    /// <summary>为一条传输记录的子项（及单文件记录的自身）补载缩略图（重启恢复历史后调用）。
    /// 恢复时源文件可能已被删除/移动，无法按路径反算指纹，故用持久化的 路径→key 精确映射取回各自的缩略图。</summary>
    private void LoadRecordThumbs(TransferItem t)
    {
        foreach (var sub in t.SubItems)
        {
            if (sub.IsImage || sub.IsVideo)
                LoadThumbAsync(sub, sub.MediaPath, t.ThumbKeyMap.GetValueOrDefault(sub.MediaPath));
        }
        if (t.IsImage || t.IsVideo)
            LoadThumbAsync(t, t.MediaPath, t.ThumbKeyMap.GetValueOrDefault(t.MediaPath));
    }

    /// <summary>为一条传输记录加载缩略图：优先按精确 key（文件存在则算内容指纹，否则用恢复时的映射 key）
    /// 读磁盘缓存，未命中才用 Shell 生成，生成后写回缓存并记录 key，源文件删/移后仍可命中。</summary>
    private void LoadThumbAsync(TransferItem target, string path, string? knownKey = null)
    {
        var key = Core.ThumbCache.KeyOfPath(path) ?? knownKey;
        if (!string.IsNullOrWhiteSpace(key))
        {
            var cached = Core.ThumbCache.Load(key);
            if (cached != null)
            {
                target.Thumb = cached;
                target.AddThumbKey(key);
                return;
            }
        }
        _ = ShellThumbnail.LoadThumbnailAsync(path, 256, src =>
        {
            if (src == null) return;
            target.Thumb = src;
            if (key != null)
            {
                Core.ThumbCache.Save(key, src);
                target.AddThumbKey(key);
            }
        });
    }

    /// <summary>订阅传输记录的增删与终态变化，实时落盘。</summary>
    private void SubscribeTransferPersistence()
    {
        // 新接收请求到达：用户停留在底部（最新处）时自动滚到底，让请求卡直接可见可处理
        _vm.Incoming.CollectionChanged += (_, e) =>
        {
            if (e.Action == System.Collections.Specialized.NotifyCollectionChangedAction.Add)
                Dispatcher.BeginInvoke(ScrollToLatestIfAtBottom);
        };
        _vm.Transfers.CollectionChanged += (_, e) =>
        {
            foreach (var o in e.OldItems?.OfType<TransferItem>() ?? Enumerable.Empty<TransferItem>())
            {
                o.PropertyChanged -= OnTransferPropChanged;
                foreach (var s in o.SubItems) s.PropertyChanged -= OnTransferPropChanged;
            }
            foreach (var n in e.NewItems?.OfType<TransferItem>() ?? Enumerable.Empty<TransferItem>())
            {
                n.PropertyChanged += OnTransferPropChanged;
                // 多文件集成气泡的子项不在 Transfers 集合里，但其 ThumbKeys 变化（缩略图异步生成完
                // AddThumbKey 时）同样必须触发落盘——否则接收记录的内容指纹 key 永远写不进 transfers.json，
                // 重启后源文件一旦被删，缩略图就再也找不回来
                foreach (var s in n.SubItems) s.PropertyChanged += OnTransferPropChanged;
            }
            // 新增记录：若用户停留在底部则自动滚到最新；上滑浏览历史时不打扰
            if (e.Action == System.Collections.Specialized.NotifyCollectionChangedAction.Add)
                Dispatcher.BeginInvoke(ScrollToLatestIfAtBottom);
            PersistTransfers();
        };
    }

    // ---------- 聊天记录自动滚底（与安卓端 reverseLayout 行为对齐：默认停在最新，上滑不打扰） ----------
    private bool _initialSnapped;   // 是否已完成首次滚底
    private bool _userScrolledUp;   // 用户是否已上滑浏览历史（离开底部）

    /// <summary>首次加载后自动滚到最新消息（最新在底部）。在绘制前完成，用户看不到跳变。</summary>
    private void ChatScroller_ScrollChanged(object sender, ScrollChangedEventArgs e)
    {
        HookScrollThumbDragOnce();

        // 程序自动滚底等也会触发这里；仅在无可滚内容时直接隐藏，避免照亮滑块
        if (ChatScroller.ScrollableHeight <= 0) SetThumbOpacity(0);

        if (!_initialSnapped)
        {
            if (ChatScroller.ScrollableHeight > 0)
            {
                ChatScroller.ScrollToEnd();
                _initialSnapped = true;
            }
            return; // 尚未有可滚动内容（如列表为空/所在页未显示），等下次变化
        }
        // 用户滚动：离开底部 → 标记上滑；滚回底部 → 恢复跟随
        _userScrolledUp = ChatScroller.VerticalOffset < ChatScroller.ScrollableHeight - 24;
    }

    /// <summary>仅用户主动滚动（滚轮/拖滑块）时点亮滑块并延时淡出，程序自动滚底不闪。</summary>
    private void ChatScroller_PreviewMouseWheel(object sender, MouseWheelEventArgs e)
    {
        if (ChatScroller.ScrollableHeight > 0)
        {
            ShowScrollThumbOnScroll();
        }
    }

    // ---------- 微信式滑块自动显示/淡出 ----------
    private System.Windows.Threading.DispatcherTimer? _thumbFadeTimer;
    private bool _thumbDragHooked;

    // ---------- 通用现代滚动条自动显示/淡出（设置页等非聊天区 ScrollViewer） ----------
    private static readonly System.Collections.Generic.Dictionary<ScrollViewer, System.Windows.Threading.DispatcherTimer> _scrollFadeTimers = new();
    private static readonly System.Collections.Generic.Dictionary<ScrollViewer, bool> _scrollDragHooked = new();

    /// <summary>给用 ModernScrollerStyle 的 ScrollViewer 挂上滚动时显示/淡出行为（微信式）。</summary>
    private void ModernScroll_Loaded(object sender, System.Windows.RoutedEventArgs e)
    {
        if (sender is not ScrollViewer sv) return;
        if (_scrollFadeTimers.ContainsKey(sv)) return; // 已挂

        var timer = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(800) };
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            FadeScrollThumb(sv, 0);
        };
        _scrollFadeTimers[sv] = timer;
        _scrollDragHooked[sv] = false;

        sv.ScrollChanged += (_, _) =>
        {
            if (sv.ScrollableHeight > 0)
            {
                HookScrollDragOnce(sv);
                ShowScrollThumb(sv, timer);
            }
        };
        sv.PreviewMouseWheel += (_, _) =>
        {
            if (sv.ScrollableHeight > 0) ShowScrollThumb(sv, timer);
        };
    }

    private static void HookScrollDragOnce(ScrollViewer sv)
    {
        if (_scrollDragHooked.GetValueOrDefault(sv)) return;
        var sb = FindVisualChild<System.Windows.Controls.Primitives.ScrollBar>(sv, "PART_VerticalScrollBar");
        if (sb == null) return;
        var timer = _scrollFadeTimers.GetValueOrDefault(sv);
        sb.Scroll += (_, _) =>
        {
            if (sv.ScrollableHeight > 0 && timer != null) ShowScrollThumb(sv, timer);
        };
        _scrollDragHooked[sv] = true;
    }

    private static void ShowScrollThumb(ScrollViewer sv, System.Windows.Threading.DispatcherTimer timer)
    {
        if (sv.ScrollableHeight <= 0) { SetScrollThumbOpacity(sv, 0); return; }
        timer.Stop();
        SetScrollThumbOpacity(sv, 0.32);
        timer.Start();
    }

    private static void SetScrollThumbOpacity(ScrollViewer sv, double value)
    {
        var tb = FindVisualChild<System.Windows.FrameworkElement>(sv, "Tb");
        if (tb == null) return;
        tb.BeginAnimation(System.Windows.UIElement.OpacityProperty, null);
        tb.Opacity = value;
    }

    private static void FadeScrollThumb(ScrollViewer sv, double target)
    {
        var tb = FindVisualChild<System.Windows.FrameworkElement>(sv, "Tb");
        if (tb == null) return;
        var anim = new System.Windows.Media.Animation.DoubleAnimation(
            target, TimeSpan.FromMilliseconds(target == 0 ? 900 : 60));
        anim.EasingFunction = new System.Windows.Media.Animation.QuadraticEase();
        tb.BeginAnimation(System.Windows.UIElement.OpacityProperty, anim);
    }

    /// <summary>模板就绪后给纵向滚动条挂一次拖动事件，拖动/点击轨道时也点亮滑块。</summary>
    private void HookScrollThumbDragOnce()
    {
        if (_thumbDragHooked) return;
        var sb = FindVisualChild<System.Windows.Controls.Primitives.ScrollBar>(ChatScroller, "PART_VerticalScrollBar");
        if (sb == null) return;
        sb.Scroll += (_, _) =>
        {
            if (ChatScroller.ScrollableHeight > 0) ShowScrollThumbOnScroll();
        };
        _thumbDragHooked = true;
    }

    /// <summary>滚动发生时立即点亮滑块；短暂停顿后缓缓淡出（微信式）。无可滚内容时直接隐藏。</summary>
    private void ShowScrollThumbOnScroll()
    {
        if (ChatScroller.ScrollableHeight <= 0) { SetThumbOpacity(0); return; }
        if (_thumbFadeTimer == null)
        {
            _thumbFadeTimer = new System.Windows.Threading.DispatcherTimer
            { Interval = TimeSpan.FromMilliseconds(700) };
            _thumbFadeTimer.Tick += OnThumbFadeTick;
        }
        _thumbFadeTimer.Stop();
        SetThumbOpacity(0.32);
        _thumbFadeTimer.Start();
    }

    private void OnThumbFadeTick(object? s, EventArgs e)
    {
        if (_thumbFadeTimer != null) _thumbFadeTimer.Stop();
        FadeThumbOpacity(0);
    }

    private void SetThumbOpacity(double value)
    {
        var tb = FindVisualChild<System.Windows.FrameworkElement>(ChatScroller, "Tb");
        if (tb == null) return;
        tb.BeginAnimation(System.Windows.UIElement.OpacityProperty, null);
        tb.Opacity = value;
    }

    private void FadeThumbOpacity(double target)
    {
        var tb = FindVisualChild<System.Windows.FrameworkElement>(ChatScroller, "Tb");
        if (tb == null) return;
        var anim = new System.Windows.Media.Animation.DoubleAnimation(
            target, TimeSpan.FromMilliseconds(target == 0 ? 900 : 60));
        anim.EasingFunction = new System.Windows.Media.Animation.QuadraticEase();
        tb.BeginAnimation(System.Windows.UIElement.OpacityProperty, anim);
    }

    private static T? FindVisualChild<T>(System.Windows.DependencyObject parent, string name)
        where T : System.Windows.DependencyObject
    {
        if (parent is T t && t is System.Windows.FrameworkElement fe && fe.Name == name) return t;
        int n = System.Windows.Media.VisualTreeHelper.GetChildrenCount(parent);
        for (int i = 0; i < n; i++)
        {
            var r = FindVisualChild<T>(System.Windows.Media.VisualTreeHelper.GetChild(parent, i), name);
            if (r != null) return r;
        }
        return null;
    }

    /// <summary>用户停在底部时新增消息自动滚到最新；上滑浏览历史则保持不动。</summary>
    private void ScrollToLatestIfAtBottom()
    {
        if (_userScrolledUp) return;
        if (ChatScroller.ScrollableHeight <= 0) { _initialSnapped = false; return; }
        ChatScroller.ScrollToEnd();
    }

    /// <summary>传输条目的属性变化：仅状态/撤回这种"终态"变化落盘，跳过 Progress/Percent 的高频更新。</summary>
    private void OnTransferPropChanged(object? s, System.ComponentModel.PropertyChangedEventArgs e)
    {
        if (e.PropertyName is "State" or "Recalled" or "ThumbKeys")
            PersistTransfers();
    }

    /// <summary>把当前传输记录写入磁盘历史。</summary>
    private void PersistTransfers()
        => Core.TransferHistory.Save(_vm.Transfers.ToList());

    /// <summary>清除全部传输记录（设置「清除传输记录」）：取消进行中的任务并删除磁盘历史。</summary>
    private void ClearTransferHistory()
    {
        foreach (var kv in _activeCts.ToList())
        {
            try { kv.Value.Cancel(); } catch { }
        }
        _activeCts.Clear();
        _recvItems.Clear();
        _recvBatches.Clear();   // 接收批次随记录一并清空，避免残留
        _vm.Transfers.Clear();          // 触发 CollectionChanged → 落盘空列表
        Core.TransferHistory.Clear();   // 清空磁盘历史文件
        Core.ThumbCache.ClearAll();     // 传输记录清空同步清空缩略图缓存
        RefreshThumbCacheInfo();        // 刷新设置页缓存大小显示
    }

    /// <summary>设置「清除传输记录」点击：弹自定义确认浮层，确认后清空传输记录与磁盘历史。</summary>
    private void ClearHistory_Click(object sender, RoutedEventArgs e)
    {
        ShowDialog(
            Localization.LangManager.T("Settings.ClearHistory"),
            Localization.LangManager.T("Settings.ClearHistoryConfirm"),
            DialogKind.Danger,
            onOk: ClearTransferHistory);
    }

    // ---------- 自定义确认浮层（替代系统 MessageBox，样式与主界面统一） ----------
    private System.Action? _confirmAction;
    /// <summary>PIN 输入回调：确定携带所输 PIN、取消/关闭携带 null（null 表示用户放弃输入）。</summary>
    private System.Action<string?>? _pinCallback;

    private void HideConfirm()
    {
        _confirmAction = null;
        _pinCallback = null;
        PinPanel.Visibility = Visibility.Collapsed;
        ConfirmPinBox.Clear();
        ConfirmOverlay.Visibility = Visibility.Collapsed;
    }

    /// <summary>显示 PIN 输入浮层（向开启 PIN 的 LocalSend 设备发送文件时使用）。</summary>
    private void ShowPinPrompt(string title, string message)
    {
        ConfirmTitle.Text = title;
        ConfirmMessage.Text = message;
        SetDialogIcon(DialogKind.Info);
        PinPanel.Visibility = Visibility.Visible;
        ConfirmCancelBtn.Visibility = Visibility.Visible;
        ConfirmOkBtn.Content = Localization.LangManager.T("Common.OK");
        ConfirmOkBtn.Style = (Style)FindResource("PrimaryButtonStyle");
        ShowOverlay(ConfirmOverlay, ConfirmScale);
        ConfirmPinBox.Focus();
        PinBox.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Background,
            new System.Action(() => ConfirmPinBox.Focus()));
    }

    /// <summary>PIN 输入框回车确认 / Esc 取消。</summary>
    private void ConfirmPinBox_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        if (e.Key == System.Windows.Input.Key.Enter) { e.Handled = true; Confirm_Ok_Click(sender, e); }
        else if (e.Key == System.Windows.Input.Key.Escape) { e.Handled = true; Confirm_Cancel_Click(sender, e); }
    }

    /// <summary>统一浮层对话框类型（决定图标与按钮形态）。</summary>
    private enum DialogKind
    {
        Info,      // 信息：蓝 info 图标，单「确定」
        Warning,   // 警告：橙 warning 图标，单「确定」
        Error,     // 错误：红 error 图标，单「确定」
        Success,   // 成功：绿 check 图标，单「确定」
        Danger     // 危险确认：红 trash 图标，双按钮「取消/确定」
    }

    /// <summary>显示统一信息/确认浮层（替代系统 MessageBox，样式与主界面一致）。
    /// Danger 为危险确认（取消+确定），其余为单「确定」信息提示。okText 缺省用「确定」。</summary>
    private void ShowDialog(string title, string message, DialogKind kind, System.Action? onOk = null, string? okText = null)
    {
        ConfirmTitle.Text = title;
        ConfirmMessage.Text = message;
        _confirmAction = onOk;
        PinPanel.Visibility = Visibility.Collapsed;

        var isDanger = kind == DialogKind.Danger;
        SetDialogIcon(kind);
        ConfirmCancelBtn.Visibility = isDanger ? Visibility.Visible : Visibility.Collapsed;
        ConfirmOkBtn.Content = okText ?? Localization.LangManager.T("Common.OK");
        ConfirmOkBtn.Style = (Style)FindResource(isDanger ? "DangerButtonStyle" : "PrimaryButtonStyle");

        ShowOverlay(ConfirmOverlay, ConfirmScale);
    }

    /// <summary>按类型设置浮层图标（开源 Font Awesome 6 矢量）与配色。</summary>
    private void SetDialogIcon(DialogKind kind)
    {
        var fill = kind switch
        {
            DialogKind.Info => FindResource("AccentBrush"),
            DialogKind.Warning => FindResource("WarningBrush"),
            DialogKind.Error => FindResource("DangerBrush"),
            DialogKind.Success => FindResource("SuccessBrush"),
            _ => FindResource("DangerBrush")
        } as System.Windows.Media.Brush;
        var badge = kind switch
        {
            DialogKind.Info => FindResource("AccentAltBrush"),
            DialogKind.Warning => FindResource("WarningAltBrush"),
            DialogKind.Error => FindResource("DangerAltBrush"),
            DialogKind.Success => FindResource("SuccessAltBrush"),
            _ => FindResource("AccentAltBrush")
        } as System.Windows.Media.Brush;
        ConfirmIconBadge.Background = badge ?? System.Windows.Media.Brushes.Transparent;
        ConfirmIconPath.Fill = fill ?? System.Windows.Media.Brushes.Transparent;

        const string info = "M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM216 336h24V272H216c-13.3 0-24-10.7-24-24s10.7-24 24-24h48c13.3 0 24 10.7 24 24v88h8c13.3 0 24 10.7 24 24s-10.7 24-24 24H216c-13.3 0-24-10.7-24-24s10.7-24 24-24zm40-208a32 32 0 1 1 0 64 32 32 0 1 1 0-64z";
        const string warning = "M256 32c14.2 0 27.3 7.5 34.5 19.8l216.4 373.6c6.7 11.6 6.9 25.9.3 37.7-6.5 11.8-18.9 18.9-32.1 18.9H37.3c-13.1 0-25.5-7.1-32-18.9-6.7-11.7-6.5-26.1.3-37.7L221.6 51.8C228.7 39.5 241.8 32 256 32zm-24 136v144c0 13.3 10.7 24 24 24s24-10.7 24-24V168c0-13.3-10.7-24-24-24s-24 10.7-24 24zm24 288a24 24 0 1 0 0-48 24 24 0 1 0 0 48z";
        const string error = "M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zm97.9-321.1a28 28 0 0 1 0 39.6L293 257.0l60.9 60.9a28 28 0 1 1-39.6 39.6L253.4 296.6 192.5 357.5a28 28 0 1 1-39.6-39.6L213.8 257.0l-60.9-60.9a28 28 0 0 1 39.6-39.6l60.9 60.9 60.9-60.9a28 28 0 0 1 39.6 0z";
        const string check = "M256 512A256 256 0 1 0 256 0a256 256 0 1 0 0 512zM369 209L241 337c-9.4 9.4-24.6 9.4-33.9 0L161 291c-9.4-9.4-9.4-24.6 0-33.9s24.6-9.4 33.9 0l22.1 22.1L335.1 175c9.4-9.4 24.6-9.4 33.9 0s9.4 24.6 0 33.9z";
        const string trash = "M135.2 17.7L128 32H32C14.3 32 0 46.3 0 64s14.3 32 32 32h384c17.7 0 32-14.3 32-32s-14.3-32-32-32h-96l-7.2-14.3C307.4 6.8 296.3 0 284.2 0H163.8c-12.1 0-23.2 6.8-28.6 17.7zM416 128H32L53.2 467c1.6 25.3 22.6 45 47.9 45h245.8c25.3 0 46.3-19.7 47.9-45L416 128z";

        ConfirmIconPath.Data = Geometry.Parse(kind switch
        {
            DialogKind.Info => info,
            DialogKind.Warning => warning,
            DialogKind.Error => error,
            DialogKind.Success => check,
            _ => trash
        });
    }

    /// <summary>显示浮层并播放弹性出现动画（统一入口）。
    /// 直接对命名 ScaleTransform 动画：旧实现把 ScaleTransform 当作 Storyboard 目标却用
    /// (UIElement.RenderTransform).(ScaleTransform.ScaleX) 复杂路径，运行时必抛
    /// "冻结属性值无法自动克隆" InvalidOperationException，导致弹层动画每次出现都被中断。</summary>
    private static void ShowOverlay(System.Windows.FrameworkElement overlay, System.Windows.Media.Animation.Animatable transform)
    {
        overlay.Visibility = Visibility.Visible;
        if (transform is not System.Windows.Media.ScaleTransform scale) return;
        var ease = new System.Windows.Media.Animation.CubicEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseOut };
        var duration = TimeSpan.FromMilliseconds(180);
        scale.BeginAnimation(System.Windows.Media.ScaleTransform.ScaleXProperty,
            new System.Windows.Media.Animation.DoubleAnimation(0.94, 1.0, duration) { EasingFunction = ease });
        scale.BeginAnimation(System.Windows.Media.ScaleTransform.ScaleYProperty,
            new System.Windows.Media.Animation.DoubleAnimation(0.94, 1.0, duration) { EasingFunction = ease });
    }

    /// <summary>确认按钮：PIN 输入态取输入值，普通确认执行回调，随后关闭。</summary>
    private void Confirm_Ok_Click(object sender, RoutedEventArgs e)
    {
        if (PinPanel.Visibility == Visibility.Visible)
        {
            var pinCb = _pinCallback;
            var pin = ConfirmPinBox.Password;
            HideConfirm();
            pinCb?.Invoke(string.IsNullOrWhiteSpace(pin) ? null : pin);
            return;
        }
        var act = _confirmAction;
        HideConfirm();
        act?.Invoke();
    }

    /// <summary>取消按钮 / 右上角关闭：PIN 输入态回调 null，普通确认仅关闭。</summary>
    private void Confirm_Cancel_Click(object sender, RoutedEventArgs e)
    {
        e.Handled = true;
        if (PinPanel.Visibility == Visibility.Visible)
        {
            var pinCb = _pinCallback;
            HideConfirm();
            pinCb?.Invoke(null);
            return;
        }
        HideConfirm();
    }

    /// <summary>点击浮层阴影（卡片外部）视为取消；点卡片内不触发。</summary>
    private void ConfirmOverlay_PreviewMouseLeftButtonDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        var src = e.OriginalSource as DependencyObject;
        while (src is not null && src != ConfirmCard)
            src = System.Windows.Media.VisualTreeHelper.GetParent(src);
        if (src is null)
        {
            if (PinPanel.Visibility == Visibility.Visible)
            {
                var pinCb = _pinCallback;
                HideConfirm();
                pinCb?.Invoke(null);
            }
            else HideConfirm();
            e.Handled = true;
        }
    }

    /// <summary>删除单条传输记录（右键气泡菜单「删除」）。</summary>
    private void DeleteTransferItem(TransferItem item)
    {
        if (_activeCts.TryGetValue(item, out var cts))
        {
            try { cts.Cancel(); } catch { }
            _activeCts.Remove(item);
        }
        Core.ThumbCache.Delete(item.AllThumbKeys()); // 精确回收该条记录（含子项）已生成的缩略图缓存
        _vm.Transfers.Remove(item); // 触发落盘
    }

    /// <summary>传输记录右键「删除」。</summary>
    private void DeleteTransfer_Click(object sender, RoutedEventArgs e)
    {
        BubbleMenuPopup.IsOpen = false; // 删除后立即关闭右键菜单
        if ((sender as FrameworkElement)?.DataContext is TransferItem item)
            DeleteTransferItem(item);
    }

    /// <summary>气泡右键：用自绘透明 Popup（BubbleMenuPopup）打开菜单，替代原生 ContextMenu。
    /// 原生 ContextMenu 弹窗是半透明窗口，DWM 会额外画一层无视圆角的系统矩形阴影；改用
    /// AllowsTransparency=True 的合成窗口后只保留跟随圆角的 DropShadowEffect。菜单项的显隐由
    /// Popup 的 DataContext（即所右键的气泡 TransferItem）驱动。</summary>
    private void BubbleContext_MouseRightButtonUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not TransferItem item) return;
        BubbleMenuPopup.DataContext = item;
        BubbleMenuPopup.Placement = System.Windows.Controls.Primitives.PlacementMode.MousePoint;
        BubbleMenuPopup.IsOpen = true;
        e.Handled = true;
    }

    /// <summary>右键「多选」：进入多选模式（气泡可点选，底部显示操作栏）。</summary>
    private void MultiSelect_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsMultiSelect = true;
        BubbleMenuPopup.IsOpen = false; // 点击「多选」后立即关闭右键菜单，避免遮挡下方的选择区域
    }

    /// <summary>多选模式下点击仍切换选中状态；非多选模式不拦截，交给原有点击逻辑。
    /// 从命中元素沿可视树向上查找绑定到 TransferItem 的容器，保证点击气泡、时间戳右侧空白、
    /// 撤回提示等消息行任意位置都能选中整行（微信式）。</summary>
    private void Bubble_MouseUp(object sender, MouseButtonEventArgs e)
    {
        if (!_vm.IsMultiSelect) return;
        System.Windows.DependencyObject? node = sender as System.Windows.DependencyObject;
        while (node != null)
        {
            if (node is FrameworkElement fe && fe.DataContext is TransferItem item)
            {
                _vm.ToggleSelection(item);
                e.Handled = true; // 拦截预览/播放等原有点击行为
                return;
            }
            node = System.Windows.Media.VisualTreeHelper.GetParent(node);
        }
    }

    /// <summary>多选操作栏「删除所选」：删除当前所有选中的传输记录。</summary>
    private void DeleteSelected_Click(object sender, RoutedEventArgs e)
    {
        var selected = _vm.SelectedTransfers();
        if (selected.Count == 0) return;
        var count = selected.Count;
        ShowDialog(
            Localization.LangManager.T("Transfer.DeleteSelected"),
            string.Format(Localization.LangManager.T("Transfer.DeleteSelectedConfirm"), count),
            DialogKind.Danger,
            onOk: () =>
            {
                foreach (var item in selected) DeleteTransferItem(item);
                _vm.IsMultiSelect = false;
                ShowDialog(Localization.LangManager.T("Common.Success"),
                    string.Format(Localization.LangManager.T("Transfer.DeleteSelectedDone"), count), DialogKind.Success);
            });
    }

    /// <summary>多选操作栏「退出」：退出多选模式并清空选中。</summary>
    private void ExitMultiSelect_Click(object sender, RoutedEventArgs e)
    {
        _vm.IsMultiSelect = false;
    }

    /// <summary>右键「另存为」：把该条记录的文件（含集成子项）复制到用户自选位置。</summary>
    private void SaveAs_Click(object sender, RoutedEventArgs e)
    {
        BubbleMenuPopup.IsOpen = false; // 另存为后立即关闭右键菜单
        if ((sender as FrameworkElement)?.DataContext is not TransferItem item) return;

        var paths = new List<string>();
        if (!string.IsNullOrEmpty(item.MediaPath) && File.Exists(item.MediaPath)) paths.Add(item.MediaPath);
        foreach (var p in item.MediaPaths)
            if (File.Exists(p)) paths.Add(p);
        // 去重（MediaPath 可能与 MediaPaths 首项相同）
        SavePathsTo(paths.Distinct().ToList());
    }

    /// <summary>多选操作栏「另存为」：把所有选中记录的文件一次性复制到用户自选位置。</summary>
    private void SaveAsSelected_Click(object sender, RoutedEventArgs e)
    {
        var selected = _vm.SelectedTransfers();
        if (selected.Count == 0) return;
        var paths = new List<string>();
        foreach (var item in selected)
        {
            if (!string.IsNullOrEmpty(item.MediaPath) && File.Exists(item.MediaPath)) paths.Add(item.MediaPath);
            foreach (var p in item.MediaPaths)
                if (File.Exists(p)) paths.Add(p);
        }
        SavePathsTo(paths.Distinct().ToList());
    }

    /// <summary>把一组文件复制到用户自选位置：单文件弹保存对话框，多文件选目录批量复制。</summary>
    private void SavePathsTo(List<string> paths)
    {
        if (paths.Count == 0)
        {
            ShowDialog(Localization.LangManager.T("Common.Error"), Localization.LangManager.T("Msg.FileMissing"), DialogKind.Error);
            return;
        }

        if (paths.Count == 1)
        {
            var dlg = new Microsoft.Win32.SaveFileDialog
            {
                FileName = Path.GetFileName(paths[0]),
                Filter = "所有文件 (*.*)|*.*"
            };
            if (dlg.ShowDialog() != true) return;
            try
            {
                File.Copy(paths[0], dlg.FileName, true);
                ShowDialog(Localization.LangManager.T("Common.Success"),
                    string.Format(Localization.LangManager.T("Msg.SavedTo"), dlg.FileName), DialogKind.Success);
            }
            catch (Exception ex)
            {
                ShowDialog(Localization.LangManager.T("Common.Error"),
                    string.Format(Localization.LangManager.T("Msg.SaveFailed"), ex.Message), DialogKind.Error);
            }
            return;
        }

        using var fb = new System.Windows.Forms.FolderBrowserDialog
        {
            Description = Localization.LangManager.T("Transfer.SaveAsFolder"),
            ShowNewFolderButton = true
        };
        if (fb.ShowDialog() != System.Windows.Forms.DialogResult.OK) return;
        try
        {
            foreach (var p in paths)
                File.Copy(p, Path.Combine(fb.SelectedPath, Path.GetFileName(p)), true);
            ShowDialog(Localization.LangManager.T("Common.Success"),
                string.Format(Localization.LangManager.T("Msg.SavedTo"), fb.SelectedPath), DialogKind.Success);
        }
        catch (Exception ex)
        {
            ShowDialog(Localization.LangManager.T("Common.Error"),
                string.Format(Localization.LangManager.T("Msg.SaveFailed"), ex.Message), DialogKind.Error);
        }
    }

    /// <summary>统一拖放判定：文件拖入传输页时铺开整页遮罩（单一上传区域），
    /// 由遮罩接管后续所有判定；离开传输页收起遮罩并禁止；设备页保持自身拖放逻辑。</summary>
    private void GlobalDragOver(object sender, System.Windows.DragEventArgs e)
    {
        if (!e.Data.GetDataPresent(DataFormats.FileDrop)) return; // 非文件拖放（如框内文字拖选）不干预
        if (e.OriginalSource is not DependencyObject o) return;
        if (IsInsideVisual(o, TransferPage))
        {
            e.Effects = System.Windows.DragDropEffects.Copy;
            ShowDragHint();
            // 置顶遮罩接管整页：后续命中不再落入文本框内部（绕开其内置拖放逻辑）
            DropOverlay.Visibility = Visibility.Visible;
            e.Handled = true;
        }
        else
        {
            DropOverlay.Visibility = Visibility.Collapsed;
            HideDragHint();
            if (!IsInsideVisual(o, DevicePage))
            {
                e.Effects = System.Windows.DragDropEffects.None;
                e.Handled = true;
            }
        }
    }

    /// <summary>统一放置：传输页内的文件拖放进入待发送预览；其余区域不干预。</summary>
    private void GlobalDrop(object sender, System.Windows.DragEventArgs e)
    {
        if (!e.Data.GetDataPresent(DataFormats.FileDrop)) return;
        DropOverlay.Visibility = Visibility.Collapsed;
        if (e.OriginalSource is DependencyObject o && IsInsideVisual(o, TransferPage))
        {
            HideDragHint();
            if (e.Data.GetData(DataFormats.FileDrop) is string[] files)
                AddDroppedFiles(files);
            e.Handled = true;
        }
    }

    /// <summary>拖放离开：仅当光标真正移出传输页范围才收起遮罩与提示；
    /// 子元素间移动引发的 DragLeave 一律忽略，杜绝闪烁。</summary>
    private void GlobalDragLeave(object sender, System.Windows.DragEventArgs e)
    {
        HideOverlayIfOutsidePage();
    }

    // ---------- 拖放遮罩（整页单一上传区域） ----------
    private void DropOverlay_DragOver(object sender, System.Windows.DragEventArgs e)
    {
        if (e.Data.GetDataPresent(DataFormats.FileDrop))
        {
            e.Effects = System.Windows.DragDropEffects.Copy;
            ShowDragHint();
        }
        else
        {
            e.Effects = System.Windows.DragDropEffects.None;
        }
        e.Handled = true;
    }

    private void DropOverlay_DragLeave(object sender, System.Windows.DragEventArgs e)
    {
        HideOverlayIfOutsidePage();
        e.Handled = true;
    }

    private void DropOverlay_Drop(object sender, System.Windows.DragEventArgs e)
    {
        DropOverlay.Visibility = Visibility.Collapsed;
        HideDragHint();
        if (e.Data.GetData(DataFormats.FileDrop) is string[] files)
            AddDroppedFiles(files);
        e.Handled = true;
    }

    /// <summary>光标真正移出传输页范围时，收起拖放遮罩与提示。</summary>
    private void HideOverlayIfOutsidePage()
    {
        try
        {
            var topLeft = TransferPage.PointToScreen(new System.Windows.Point(0, 0));
            var bottomRight = TransferPage.PointToScreen(
                new System.Windows.Point(TransferPage.ActualWidth, TransferPage.ActualHeight));
            var cursor = System.Windows.Forms.Cursor.Position;
            var inside = cursor.X >= topLeft.X && cursor.X <= bottomRight.X
                      && cursor.Y >= topLeft.Y && cursor.Y <= bottomRight.Y;
            if (!inside)
            {
                DropOverlay.Visibility = Visibility.Collapsed;
                HideDragHint();
            }
        }
        catch
        {
            DropOverlay.Visibility = Visibility.Collapsed;
            HideDragHint();
        }
    }

    /// <summary>文本框拖放兜底：其实例处理器在 TextBoxBase 内置类处理器之后执行，
    /// 把被内置逻辑改掉的"禁止"光标最终写回 Copy。</summary>
    private void ChatInput_DragFinalize(object sender, System.Windows.DragEventArgs e)
    {
        if (!e.Data.GetDataPresent(DataFormats.FileDrop)) return;
        e.Effects = System.Windows.DragDropEffects.Copy;
        e.Handled = true;
        ShowDragHint();
    }

    /// <summary>文本框放置兜底：文件一律进入待发送预览（与传输页其余区域行为一致）。</summary>
    private void ChatInput_DropFinalize(object sender, System.Windows.DragEventArgs e)
    {
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] files) return;
        e.Handled = true;
        HideDragHint();
        AddDroppedFiles(files);
    }

    /// <summary>判断 d 是否为 root 的后代（含自身），兼容可视树与逻辑树。</summary>
    private static bool IsInsideVisual(DependencyObject? d, DependencyObject root)
    {
        while (d != null)
        {
            if (ReferenceEquals(d, root)) return true;
            d = d is Visual v ? VisualTreeHelper.GetParent(v) : LogicalTreeHelper.GetParent(d);
        }
        return false;
    }

    /// <summary>顶部显示"松开鼠标，上传文件"反馈；已显示则不重复。同时启动看门狗轮询。</summary>
    private void ShowDragHint()
    {
        DragHintText.Text = "松开鼠标，上传文件";
        DragHint.Visibility = Visibility.Visible;
        EnsureDragWatch();
    }

    /// <summary>启动拖放看门狗：轮询左键与光标位置，覆盖事件死区（非客户区/空白像素）。</summary>
    private void EnsureDragWatch()
    {
        if (_dragWatch is null)
        {
            _dragWatch = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(60) };
            _dragWatch.Tick += (_, _) =>
            {
                // 左键已松开：拖放结束（含在死区松手、按 Esc 取消），立即清理停表
                if ((GetAsyncKeyState(0x01) & 0x8000) == 0)
                {
                    _dragWatch.Stop();
                    DropOverlay.Visibility = Visibility.Collapsed;
                    HideDragHint();
                    return;
                }
                // 光标仍在拖放中但已移出传输页范围：清理（事件死区兜底）
                HideOverlayIfOutsidePage();
            };
        }
        _dragWatch.Start();
    }

    private void HideDragHint()
    {
        if (DragHint.Visibility != Visibility.Collapsed) DragHint.Visibility = Visibility.Collapsed;
    }

    /// <summary>把拖入的文件/文件夹去重后加进待发送预览，并切到传输页。</summary>
    private void AddDroppedFiles(string[] dropped)
    {
        var before = _pending.Count;
        foreach (var p in dropped)
        {
            if (File.Exists(p) && !_pending.Any(u => u.File == p))
                _pending.Add(new PendingUnit { File = p });
            else if (Directory.Exists(p) && !_pending.Any(u => u.Folder == p))
                _pending.Add(new PendingUnit { Folder = p });
        }
        if (_pending.Count != before)
        {
            UpdatePendingPreview();
            NavTransfer.IsChecked = true;
        }
    }

    /// <summary>把拖入的文件加进待发送预览（微信式：先进输入框，点发送再发出）。</summary>
    // 统一拖放放置由 GlobalDrop 处理（见上方）。

    // ---------- 发送 ----------
    /// <summary>设备页「开始传输」：选好设备后直接进入传输页（不再弹文件选择窗）。</summary>
    private void StartTransfer_Click(object sender, RoutedEventArgs e)
        => NavTransfer.IsChecked = true;

    private void SendButton_Click(object sender, RoutedEventArgs e)
    {
        // 点「选择文件」直接打开文件选择器，不要求先选设备（微信式：文件先进输入框预览）；
        // 没有选中设备时，点【发送】键才会弹出设备选择浮层（ChatSend_Click 中处理）。
        ContinuePickFilesFlow();
    }

    /// <summary>把设备加入当前选中态（多选列表）。</summary>
    private void SelectPeerInList(PeerItem picked)
    {
        var peerToSelect = _vm.Peers.FirstOrDefault(x => x.DeviceId == picked.DeviceId) ?? picked;
        DeviceList.SelectedItems.Clear();
        DeviceList.SelectedItems.Add(peerToSelect);
        _vm.UpdateSelection(DeviceList.SelectedItems.Count);
    }

    /// <summary>选好设备后的文件选择与预览流程（微信式：文件先进输入框预览，点发送才真正发出）。</summary>
    private void ContinuePickFilesFlow()
    {
        var dlg = new OpenFileDialog { Multiselect = true, Title = "选择要发送的文件" };
        if (dlg.ShowDialog() != true || dlg.FileNames.Length == 0) return;
        foreach (var f in dlg.FileNames)
            if (File.Exists(f) && !_pending.Any(u => u.File == f))
                _pending.Add(new PendingUnit { File = f });
        UpdatePendingPreview();
        // 跳转到传输页，供用户在输入框里确认待发文件并点发送
        NavTransfer.IsChecked = true;
    }

    /// <summary>文件夹选择：选整个文件夹，点发送时递归展开为带相对路径的文件列表发送（接收端重建目录结构）。</summary>
    private void FolderPick_Click(object sender, RoutedEventArgs e)
    {
        var dlg = new Microsoft.Win32.OpenFolderDialog { Title = "选择要发送的文件夹" };
        if (dlg.ShowDialog() != true || string.IsNullOrEmpty(dlg.FolderName)) return;
        var folder = dlg.FolderName;
        if (!Directory.Exists(folder)) return;
        if (!_pending.Any(u => u.Folder == folder))
            _pending.Add(new PendingUnit { Folder = folder });
        UpdatePendingPreview();
        NavTransfer.IsChecked = true;
    }

    // ---------- 设备选择浮层（替代旧的系统风格独立弹窗，与整体 UI 统一） ----------
    private System.Action<PeerItem>? _pickerCallback;
    private Border? _pickerSelectedRow;
    private PeerItem? _pickerSelectedPeer;
    private readonly List<Border> _pickerRows = new();

    /// <summary>弹出设备选择浮层：列出最近在线设备，点击选中后回调所选设备（取消则无回调）。</summary>
    private void PromptPickPeer(System.Action<PeerItem> onPicked)
    {
        var peers = _vm.Peers.Where(x => x.LastSeen >= DateTime.UtcNow.AddMinutes(-5)).ToList();
        if (peers.Count == 0)
        {
            ShowDialog(Localization.LangManager.T("Send.NoDeviceTitle"), Localization.LangManager.T("Send.NoDevice"), DialogKind.Info);
            return;
        }
        _pickerCallback = onPicked;
        _pickerSelectedRow = null;
        _pickerSelectedPeer = null;
        _pickerRows.Clear();
        PickerTitle.Text = Localization.LangManager.T("Send.PickDevice");
        PickerOkBtn.Content = Localization.LangManager.T("Send.UseDevice");
        PickerOkBtn.IsEnabled = false;
        PickerItems.Children.Clear();

        foreach (var p in peers) PickerItems.Children.Add(BuildPickerRow(p));

        PickerOverlay.Visibility = Visibility.Visible;
        ShowOverlay(PickerOverlay, PickerScale); // 弹性出现（与确认浮层同一套安全实现）
    }

    private Border BuildPickerRow(PeerItem p)
    {
        var accent = FindResource("AccentBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent;
        var accentAlt = FindResource("AccentAltBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent;
        var surface = FindResource("SurfaceAltBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent;
        var textSec = FindResource("TextSecondaryBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Gray;

        var row = new Border
        {
            Tag = p,
            CornerRadius = new System.Windows.CornerRadius(12),
            Margin = new System.Windows.Thickness(0, 0, 0, 8),
            Padding = new System.Windows.Thickness(12, 10, 12, 10),
            Background = surface,
            Cursor = System.Windows.Input.Cursors.Hand
        };
        var grid = new System.Windows.Controls.Grid();
        grid.ColumnDefinitions.Add(new System.Windows.Controls.ColumnDefinition { Width = GridLength.Auto });
        grid.ColumnDefinitions.Add(new System.Windows.Controls.ColumnDefinition());
        var avatar = new Border { Width = 36, Height = 36, CornerRadius = new System.Windows.CornerRadius(11), Background = accentAlt, VerticalAlignment = System.Windows.VerticalAlignment.Center };
        var initial = new TextBlock
        {
            Text = string.IsNullOrEmpty(p.Name) ? "?" : p.Name[..1].ToUpperInvariant(),
            FontSize = 15, FontWeight = FontWeights.SemiBold,
            Foreground = accent, HorizontalAlignment = System.Windows.HorizontalAlignment.Center, VerticalAlignment = System.Windows.VerticalAlignment.Center
        };
        avatar.Child = initial;
        grid.Children.Add(avatar);
        var texts = new System.Windows.Controls.StackPanel { Margin = new System.Windows.Thickness(12, 0, 0, 0), VerticalAlignment = System.Windows.VerticalAlignment.Center };
        texts.Children.Add(new TextBlock { Text = p.Name, FontSize = 14, FontWeight = FontWeights.SemiBold, TextTrimming = TextTrimming.CharacterEllipsis });
        texts.Children.Add(new TextBlock { Text = p.Ip, FontSize = 11, Foreground = textSec, Margin = new System.Windows.Thickness(0, 2, 0, 0) });
        Grid.SetColumn(texts, 1);
        grid.Children.Add(texts);
        row.Child = grid;

        row.MouseLeftButtonUp += (_, _) => SelectPickerRow(row);
        row.MouseEnter += (_, _) => { if (!ReferenceEquals(row, _pickerSelectedRow)) row.Background = FindResource("SurfaceBrush") as System.Windows.Media.Brush; };
        row.MouseLeave += (_, _) =>
        {
            if (!ReferenceEquals(row, _pickerSelectedRow))
                row.Background = FindResource("SurfaceAltBrush") as System.Windows.Media.Brush;
        };
        _pickerRows.Add(row);
        return row;
    }

    private void SelectPickerRow(Border row)
    {
        var accent = FindResource("AccentBrush") as System.Windows.Media.Brush;
        var selBg = FindResource("SelectionBrush") as System.Windows.Media.Brush ?? FindResource("AccentAltBrush") as System.Windows.Media.Brush;
        foreach (var r in _pickerRows)
        {
            r.Background = FindResource("SurfaceAltBrush") as System.Windows.Media.Brush;
            r.BorderBrush = null;
            r.BorderThickness = new System.Windows.Thickness(0);
        }
        row.Background = selBg;
        row.BorderBrush = accent;
        row.BorderThickness = new System.Windows.Thickness(1.5);
        _pickerSelectedRow = row;
        _pickerSelectedPeer = row.Tag as PeerItem;
        PickerOkBtn.IsEnabled = _pickerSelectedPeer is not null;
    }

    private void Picker_Ok_Click(object sender, RoutedEventArgs e)
    {
        var cb = _pickerCallback;
        var peer = _pickerSelectedPeer;
        PickerOverlay.Visibility = Visibility.Collapsed;
        _pickerCallback = null;
        _pickerRows.Clear();
        PickerItems.Children.Clear();
        if (peer is not null) cb?.Invoke(peer);
    }

    private void Picker_Cancel_Click(object sender, RoutedEventArgs e)
    {
        e.Handled = true;
        PickerOverlay.Visibility = Visibility.Collapsed;
        _pickerCallback = null;
        _pickerRows.Clear();
        PickerItems.Children.Clear();
    }

    /// <summary>点击浮层阴影（卡片外部）视为取消。</summary>
    private void PickerOverlay_PreviewMouseLeftButtonDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        var src = e.OriginalSource as DependencyObject;
        while (src is not null && src != PickerCard)
            src = System.Windows.Media.VisualTreeHelper.GetParent(src);
        if (src is null)
        {
            PickerOverlay.Visibility = Visibility.Collapsed;
            _pickerCallback = null;
            _pickerRows.Clear();
            PickerItems.Children.Clear();
            e.Handled = true;
        }
    }

    private async void SendFiles(PeerItem peer, IReadOnlyList<string> files, IReadOnlyList<string?>? relPaths = null)
    {
        var sendId = Guid.NewGuid().ToString("N");
        var trans = new TransferItem
        {
            Name = files.Count == 1 ? Path.GetFileName(files[0]) : $"{files.Count} 个文件",
            Direction = "发送",
            Target = peer.Name,
            MediaPath = files.Count == 1 ? files[0] : "",
            MediaPaths = files.ToList(), // 多文件集成显示用（收集全部路径，图片/视频/普通文件分类展示）
            // 发送记录必须带时间戳，否则气泡信息行的时间为空（与接收/文本记录一致）
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        _vm.Transfers.Add(trans);
        // 为单文件图片/视频生成 Shell 缩略图，供传输记录预览（避免视频 MediaElement 黑屏）
        if (files.Count == 1 && (TransferItem.IsImageExt(files[0]) || TransferItem.IsVideoExt(files[0])))
        {
            LoadThumbAsync(trans, files[0]);
        }
        // 多文件记录：为每个图片/视频子项异步加载 Shell 缩略图
        foreach (var sub in trans.SubItems)
        {
            if (sub.IsImage || sub.IsVideo)
                LoadThumbAsync(sub, sub.MediaPath);
        }
        if (NavDevices.IsChecked == true) NavTransfer.IsChecked = true;

        var ip = IPAddress.Parse(peer.Ip);
        // 取消令牌：由 CancelSend 触发
        var cts = new CancellationTokenSource();
        _activeCts[trans] = cts;
        var ct = cts.Token;
        LsDiscovery.LogDiag($"SendFiles peer={peer.Name} ip={peer.Ip} IsLocalSend={peer.IsLocalSend} LsProtocol={peer.LsProtocol} files={files.Count} sendId={sendId}");
        try
        {
            if (peer.IsLocalSend)
            {
                // LocalSend 目标：走 LocalSend v2 协议（prepare-upload → upload，TLS 指纹钉扎）
                await SendLocalSendAsync(peer, files, trans, ct);
                return;
            }
            // fileIds 与 files 同序，用于把接收方返回的 fileId→token 反向映射到原路径
            var fileIds = new List<string>();
            var prepared = await _sender.PrepareAsync(ip, peer.Port, sendId, files, fileIds, relPaths);
            if (prepared is null || prepared.Tokens.Count == 0)
            {
                trans.State = "被拒";
                return;
            }
            // fid → 原路径
            var idToPath = new Dictionary<string, string>();
            for (int i = 0; i < files.Count; i++) idToPath[fileIds[i]] = files[i];

            // P1: 并行多文件上传（HttpClient 连接池自然限流），聚合进度
            double total = 0; foreach (var f in files) total += new FileInfo(f).Length;
            var tokenList = prepared.Tokens.ToList();
            var perFileBytes = new long[tokenList.Count];
            var startTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            double remainEma = -1;
            var tasks = tokenList.Select((kv, i) =>
            {
                var f = idToPath[kv.Key];
                var len = new FileInfo(f).Length;
                return _sender.SendFileV2Async(ip, peer.Port, prepared.SessionId, kv.Key, kv.Value, f,
                    p =>
                    {
                        perFileBytes[i] = (long)(len * p);
                        var prog = perFileBytes.Sum() / total;
                        trans.Progress = prog;
                        if (prog > 0.02 && prog < 1)
                        {
                            var elapsed = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - startTime;
                            if (elapsed > 0)
                            {
                                var remainInstant = elapsed * (1 - prog) / prog;
                                remainEma = remainEma < 0 ? remainInstant : 0.3 * remainInstant + 0.7 * remainEma;
                                var sent = (long)(total * prog);
                                trans.ProgressText = $"剩余 {FormatDuration((long)remainEma)} · {IncomingItem.FormatBytes(sent)}/{IncomingItem.FormatBytes((long)total)}";
                            }
                        }
                    }, ct);
            }).ToArray();
            await Task.WhenAll(tasks);
            trans.Progress = 1;
            trans.ProgressText = "";
            trans.State = "完成";
        }
        catch (OperationCanceledException)
        {
            trans.ProgressText = "";
            trans.State = "已取消";
        }
        catch (Exception ex)
        {
            trans.ProgressText = "";
            trans.State = ct.IsCancellationRequested ? "已取消" : "失败";
            if (!ct.IsCancellationRequested)
                ShowDialog(Localization.LangManager.T("Common.Error"), string.Format(Localization.LangManager.T("Msg.SendFailed"), ex.Message), DialogKind.Error);
        }
        finally
        {
            // 取消时通知 OrangeGo 接收方（LocalSend 设备靠连接断开自动超时清理）
            LsDiscovery.LogDiag($"SendFiles finally cancelled={ct.IsCancellationRequested} IsLocalSend={peer.IsLocalSend}");
            if (ct.IsCancellationRequested && !peer.IsLocalSend)
            {
                _ = Task.Run(async () =>
                {
                    try { await _sender.CancelAsync(IPAddress.Parse(peer.Ip), peer.Port, sendId); LsDiscovery.LogDiag($"CancelAsync(orange) OK sendId={sendId}"); }
                    catch (Exception ex) { LsDiscovery.LogDiag($"CancelAsync(orange) FAILED: {ex.Message}"); }
                });
            }
            _activeCts.Remove(trans);
        }
    }

    /// <summary>取消某个进行中的发送任务。</summary>
    private void CancelSend(TransferItem trans)
    {
        if (_activeCts.TryGetValue(trans, out var cts)) cts.Cancel();
    }

    /// <summary>
    /// 向 LocalSend 设备发送文件（v2 协议）。
    /// 流程：prepare-upload 请求授权（对端 PIN 开启时回 401 → 弹 PIN 输入重试，最多 3 次）
    /// → 逐文件 upload?sessionId&amp;fileId&amp;token → 全部 200 完成。
    /// </summary>
    private async Task SendLocalSendAsync(PeerItem peer, IReadOnlyList<string> files,
        TransferItem trans, CancellationToken ct)
    {
        var target = new LocalSendDevice
        {
            Ip = IPAddress.Parse(peer.Ip),
            Port = peer.Port,
            Alias = peer.Name,
            Fingerprint = peer.LsFingerprint,
            Protocol = string.IsNullOrEmpty(peer.LsProtocol) ? "https" : peer.LsProtocol
        };

        var fileIds = new List<string>();
        string? pin = null;
        LsSender.PrepareResult? prepared = null;
        Exception? lastError = null;
        for (int attempt = 0; attempt < 3 && prepared is null; attempt++)
        {
            ct.ThrowIfCancellationRequested();
            try
            {
                prepared = await _lsSender.PrepareUploadAsync(target, files, fileIds, pin);
            }
            catch (LsSender.LsSendException ex)
            {
                lastError = ex;
                if (ex.StatusCode == 401)
                {
                    // 对端要求 PIN：弹窗让用户输入后携带重试
                    var input = await PromptPinAsync(peer.Name);
                    if (input is null) { trans.State = "已取消"; return; }
                    pin = input;
                }
                else break; // 403/409/429/其他：直接失败，交给下方统一处理
            }
        }

        if (prepared is null)
        {
            if (lastError is LsSender.LsSendException lex)
            {
                if (lex.StatusCode == 403) { trans.State = "被拒"; return; }
                if (lex.StatusCode is 409 or 429)
                {
                    trans.State = "失败";
                    if (!ct.IsCancellationRequested)
                        ShowDialog(Localization.LangManager.T("Common.Info"),
                            Localization.LangManager.T("Msg.LsPeerBusy"), DialogKind.Info);
                    return;
                }
            }
            trans.State = ct.IsCancellationRequested ? "已取消" : "失败";
            if (!ct.IsCancellationRequested && lastError is not null)
                ShowDialog(Localization.LangManager.T("Common.Error"),
                    string.Format(Localization.LangManager.T("Msg.SendFailed"), lastError.Message), DialogKind.Error);
            return;
        }

        if (prepared.Tokens.Count == 0)
        {
            // 对端无需传输（文件被判定为已存在等）：视为正常完成
            trans.State = "完成";
            return;
        }

        var idToPath = new Dictionary<string, string>();
        for (int i = 0; i < files.Count; i++) idToPath[fileIds[i]] = files[i];

        double total = 0; foreach (var f in files) total += new FileInfo(f).Length;
        long done = 0;
        var startTime = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        double remainEma = -1;
        try
        {
            foreach (var (fid, ftoken) in prepared.Tokens)
            {
                ct.ThrowIfCancellationRequested();
                var f = idToPath[fid];
                var len = new FileInfo(f).Length;
                await _lsSender.UploadFileAsync(target, prepared.SessionId, fid, ftoken, f,
                    p =>
                    {
                        var prog = (done + len * p) / total;
                        trans.Progress = prog;
                        if (prog > 0.02 && prog < 1)
                        {
                            var elapsed = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - startTime;
                            if (elapsed > 0)
                            {
                                var remainInstant = elapsed * (1 - prog) / prog;
                                remainEma = remainEma < 0 ? remainInstant : 0.3 * remainInstant + 0.7 * remainEma;
                                var sent = (long)(total * prog);
                                trans.ProgressText = $"剩余 {FormatDuration((long)remainEma)} · {IncomingItem.FormatBytes(sent)}/{IncomingItem.FormatBytes((long)total)}";
                            }
                        }
                    }, ct);
                done += len;
            }
            trans.Progress = 1;
            trans.ProgressText = "";
            trans.State = "完成";
        }
        catch (Exception ex)
        {
            // 上传中止/失败：通知对端结束会话，其余状态交给外层统一 catch 设置
            LsDiscovery.LogDiag($"SendLocalSendAsync catch: {ex.GetType().Name} cancelled={ct.IsCancellationRequested} sessionId={prepared.SessionId}");
            try { await _lsSender.CancelAsync(target, prepared.SessionId); LsDiscovery.LogDiag($"CancelAsync(ls) OK sessionId={prepared.SessionId}"); }
            catch (Exception cex) { LsDiscovery.LogDiag($"CancelAsync(ls) FAILED: {cex.Message}"); }
            throw;
        }
    }

    /// <summary>弹 PIN 输入浮层；确定返回所输 PIN，取消/关闭返回 null。</summary>
    private Task<string?> PromptPinAsync(string deviceName)
    {
        var tcs = new TaskCompletionSource<string?>();
        _pinCallback = v => tcs.TrySetResult(v);
        ShowPinPrompt(Localization.LangManager.T("Msg.LsPinTitle"),
            string.Format(Localization.LangManager.T("Msg.LsPinPrompt"), deviceName));
        return tcs.Task;
    }

    /// <summary>传输记录行的"取消"按钮点击。</summary>
    private void CancelTransfer_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        if (((System.Windows.FrameworkElement)sender).DataContext is TransferItem item && item.Direction == "发送")
            CancelSend(item);
    }

    // ---------- 文字消息 ----------
    /// <summary>传输页底部聊天输入栏"发送"按钮：把输入文字作为即时消息发到当前选中的设备。
    /// 未选中设备时也可点击：弹出设备选择窗，选中后自动进入选中态（内存，本次运行有效）。</summary>
    private async void ChatSend_Click(object sender, RoutedEventArgs e)
    {
        // 未选中设备时弹层让用户选一次（选完加入选中态，本次运行有效）
        var peers = ResolveSendPeers();
        if (peers.Count == 0)
        {
            PromptPickPeer(p =>
            {
                SelectPeerInList(p); // 加入选中态（内存），本次运行有效
                _ = SendChatPayloadAsync(SelectedPeers());
            });
            return;
        }
        await SendChatPayloadAsync(peers);
    }

    /// <summary>把输入框内容（待发附件 + 文字）发给指定设备。</summary>
    private async Task SendChatPayloadAsync(IReadOnlyList<PeerItem> peers)
    {
        if (peers.Count == 0) return;
        var text = GetTextFromInput().Trim();

        // 有待发送附件（截图/文件/文件夹）：先统一发出，再发文字（文字可同时携带）。
        // 文件夹在发送前递归展开为「文件 + 相对路径」，接收端据此重建目录结构。
        if (_pending.Count > 0)
        {
            var files = new List<string>();
            var relPaths = new List<string?>();
            foreach (var unit in _pending)
            {
                if (unit.IsFolder && unit.Folder is { } fd && Directory.Exists(fd))
                {
                    var root = Path.GetFullPath(fd);
                    foreach (var f in Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories))
                    {
                        files.Add(f);
                        relPaths.Add(Path.GetRelativePath(root, f).Replace('\\', '/'));
                    }
                }
                else if (unit.File is { } fp && File.Exists(fp))
                {
                    files.Add(fp);
                    relPaths.Add(null);
                }
            }
            _pending.Clear();
            PendingWrap.Children.Clear();
            PendingPanel.Visibility = Visibility.Collapsed;
            if (files.Count > 0)
                foreach (var peer in peers) SendFiles(peer, files, relPaths);
        }

        if (text.Length == 0) return;
        ClearInputBox();
        foreach (var peer in peers) await SendTextAsync(peer, text);
    }

    /// <summary>聊天输入栏回车发送（Shift+回车换行）。
    /// 用 PreviewKeyDown（隧道）而非 KeyDown：WPF RichTextBox 在 KeyDown 冒泡阶段拦截不了
    /// Enter 的"另起一段"默认处理，必须在最早隧道阶段把 Enter 标记 Handled 才能阻止换行。
    /// 按键逻辑：Enter=发送，Shift+Enter=手动换行。</summary>
    private void ChatInput_KeyDown(object sender, System.Windows.Input.KeyEventArgs e)
    {
        if (e.Key != Key.Enter) return;
        bool shift = (Keyboard.Modifiers & ModifierKeys.Shift) != 0;
        if (!shift)
        {
            e.Handled = true; // 拦截 Enter，避免 RichTextBox 插入新段落（换行）
            ChatSend_Click(sender, e);
            return;
        }
        // Shift+Enter：手动换行（在光标处插入硬换行，行距与自动换行一致）。
        e.Handled = true;
        InsertLineBreakAtCaret();
    }

    /// <summary>在光标处插入一个硬换行（LineBreak）。
    /// 打破「新建段落」的做法：段落之间自带段间距，即使行高调成紧凑也仍会隔出空行，反复修不好。
    /// 改为在同一个段落内插入 LineBreak，其行距与自动绕行完全一致，从根上消除断行处的多余间隔。</summary>
    private void InsertLineBreakAtCaret()
    {
        // 空段落直接换到新段落（当前段无任何内容，无需拆行）
        if (ChatInput.CaretPosition.Paragraph is { Inlines.Count: 0 } emptyPara)
        {
            var p = NewCompactParagraph();
            ChatInput.Document.Blocks.InsertAfter(emptyPara, p);
            ChatInput.CaretPosition = p.ContentStart;
            ChatInput.Focus();
            return;
        }

        var caret = ChatInput.CaretPosition;
        // 定位到可插入位置
        var pos = caret.GetInsertionPosition(LogicalDirection.Forward) ?? caret;
        // 确保所在段落行距紧凑，统一行高
        if (pos.Paragraph is { } para) ApplyCompactLineHeight(para);
        // 在当前段落内插入硬换行（行距与自动换行一致，不再产生段间空白）
        pos.InsertLineBreak();
        // 光标移到新行起始
        ChatInput.CaretPosition = pos.GetNextInsertionPosition(LogicalDirection.Forward) ?? pos.Paragraph?.ContentEnd ?? pos;
        ChatInput.Focus();
    }

    /// <summary>表情功能键：切换弹出表情面板。</summary>
    private void EmojiButton_Click(object sender, RoutedEventArgs e) => EmojiPopup.IsOpen = !EmojiPopup.IsOpen;

    /// <summary>输入框内容变动时统一所有段落为紧凑行距并去掉段落边距。
    /// Root级修复：无论 Enter / Shift+Enter / 粘贴 / 撤销产生的任何新段落，行距都与自动换行一致，
    /// 从根本上杜绝"手动换行后隔空一整行"（此前仅手动覆盖个别拆段路径，覆盖不全）。</summary>
    private void ChatInput_TextChanged(object sender, TextChangedEventArgs e)
    {
        foreach (Block block in ChatInput.Document.Blocks)
        {
            if (block is Paragraph p) ApplyCompactLineHeight(p);
        }
        RefreshSendEnabled();
    }

    /// <summary>发送按钮状态：有输入文字或有待发送文件时点亮，否则置灰。</summary>
    private void RefreshSendEnabled() => ChatSendBtn.IsEnabled = _pending.Count > 0 || HasInputText();

    /// <summary>输入框是否有内容（文字/emoji 图片/行Break）；空则发送按钮置灰。</summary>
    private bool HasInputText()
    {
        var text = new System.Windows.Documents.TextRange(
            ChatInput.Document.ContentStart, ChatInput.Document.ContentEnd).Text;
        if (!string.IsNullOrWhiteSpace(text)) return true;
        // 无文字时检查是否内嵌了 emoji 图片（InlineUIContainer）
        foreach (Block block in ChatInput.Document.Blocks)
        {
            if (block is not Paragraph p) continue;
            foreach (var inline in p.Inlines)
                if (inline is System.Windows.Documents.InlineUIContainer) return true;
        }
        return false;
    }

    // ---------- 表情面板（iOS 苹果输入法风格：多分类多页，标准彩色 Unicode emoji） ----------
    // 每个分类：分类名 / 类别图标(用代表字符) / emoji 列表（均为标准 Unicode，Segoe UI Emoji 彩色渲染）
    private static readonly (string Name, string Icon, string[] Emojis)[] EmojiCategories =
    {
        ("表情", "😀", new[]{ "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😙","😚","😋","😛","😝","😜","🤪","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😪","😴","😷","🤒","🤕","🤢","🤮","🥵","🥶","🥴","😵","🤯","🤠","🥺","😢","😭","😤","😠","😡","🤬","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","😳","🥱","😍","🤤","😪" }),
        ("手势", "👍", new[]{ "👍","👎","👌","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","👇","☝️","✋","🤚","🖐️","🖖","👋","🤝","💪","🦾","✍️","🙏","👏","👐","🤲","🤏","👊","✊","🤛","🤜","👶","🧒","👦","👧","🧑","👨","👩","🧓","👴","👵" }),
        ("动物", "🐶", new[]{ "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊","🐔","🐧","🐦","🐤","🐣","🦆","🦅","🦉","🦇","🐺","🐗","🐴","🦄","🐝","🪲","🐛","🦋","🐌","🐞","🐜","🦂","🐢","🐍","🦎","🦖","🦕","🐙","🦑","🦀","🐳","🐬","🐋","🦈","🐊","🐅","🦓","🦍","🐘","🦛","🐪","🐫","🦒","🦘","🦬","🐃","🐂","🐄","🐎" }),
        ("食物", "🍎", new[]{ "🍏","🍎","🍐","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥","🥝","🍅","🍆","🥑","🥦","🥬","🥒","🌽","🥕","🧄","🧅","🥔","🍠","🥐","🥯","🍞","🥖","🥨","🧀","🥚","🍳","🧈","🥞","🧇","🥓","🥩","🍗","🍖","🌭","🍔","🍟","🍕","🥪","🥙","🧆","🌮","🌯","🥗","🥘","🍝","🍜","🍲","🍛","🍣","🍱","🥟","🍤","🍙","🍚","🍘","🍥","🥠","🍢","🍡" }),
        ("活动", "⚽", new[]{ "⚽","🏀","🏈","⚾","🎾","🏐","🏉","🥏","🎱","🏓","🏸","🏒","🏑","🥍","🏏","🪃","🥅","⛳","🪁","🏹","🎣","🤿","🥊","🥋","🎽","🛹","🛼","🛷","⛸️","🥌","🎿","⛷️","🏂","🪂","🏋️","🤼","🤸","⛹️","🤺","🤾","🏌️","🏇","🧘","🏄","🏊","🤽","🚣","🧗","🚵","🚴","🏆","🥇","🥈","🥉","🏅","🎖️","🏵️","🎗️","🎫","🎟️","🎪","🤹","🎭","🎨","🎬","🎤","🎧","🎼","🎹","🥁","🎷","🎺","🎸","🪕","🎻","🎲","♟️","🎯","🎳","🎮","🎰","🧩" }),
        ("旅行", "🌍", new[]{ "🚗","🚕","🚙","🚌","🚎","🏎️","🚓","🚑","🚒","🚐","🛻","🚚","🚛","🚜","🛵","🏍️","🛺","🚲","🛴","🛹","🚏","🛣️","🛤️","✈️","🛫","🛬","🛩️","💺","🚁","🚟","🚠","🚡","🚢","🛳️","⛴️","🛥️","🚤","⛵","🛶","🚀","🛸","🗼","🗽","🗿","🗺️","🧭","🏔️","⛰️","🌋","🗻","🏕️","🏖️","🏜️","🏝️","🏞️","🏟️","🏛️","🏗️","🧱","🏘️","🏚️","🏠","🏡","🏢","🕍","⛩️","🕋","⛲","🎡","🎢","🎠" }),
        ("物品", "💡", new[]{ "👓","🕶️","🥽","🥼","🦺","👔","👕","👖","🧣","🧤","🧥","🧦","👗","👘","🥻","🩱","🩲","🩳","👙","👚","👛","👜","👝","🎒","🧳","💼","👞","👟","🥾","🥿","👠","👡","🩰","👢","👑","👒","🎩","🎓","🧢","🪖","⛑️","📿","💄","💍","💎","🔦","🕯️","💡","🔋","🔌","💻","🖥️","🖨️","⌨️","🖱️","🖲️","💽","💾","💿","📀","📷","📸","📹","🎥","📽️","🎞️","📞","☎️","📟","📠","📺","📻","🎙️","📍","📌","📎","🖇️","📐","📏","🧮","📚","📖","🔑","🗝️","🔨","🪓","⛏️","⚒️","🛠️","🗡️","⚔️","🛡️","🔫","🪃","🏹","🪚","🔧","🔩","⚙️","🧰","🧲" }),
        ("符号", "❤️", new[]{ "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❤️‍🔥","❤️‍🩹","❣️","💕","💞","💓","💗","💖","💘","💝","💟","☮️","✝️","☪️","🕉️","☸️","✡️","🔯","🕎","🔱","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","⚛️","✴️","✅","❌","❗","❓","💯","💢","💥","✨","⭐","🌟","🔥","⚡","☀️","🌤️","⛅","🌧️","🌨️","🌩️","🌪️","🌈","☂️","❄️","☃️","⛄","💫","💨","💧","💦","🫧","🌊","🌸","🌺","🌻","🌹","🥀","🌷","🌼","🌍","🌏","🌕","🌙" }),
        ("旗帜", "🚩", new[]{ "🏁","🚩","🎌","🏴","🏳️","🏳️‍🌈","🏴‍☠️","🇨🇳","🇭🇰","🇲🇴","🇹🇼","🇺🇸","🇬🇧","🇯🇵","🇰🇷","🇫🇷","🇩🇪","🇮🇹","🇪🇸","🇷🇺","🇧🇷","🇮🇳","🇦🇺","🇨🇦","🇸🇬","🇲🇾","🇮🇩","🇻🇳","🇹🇭","🇵🇭","🇺🇳","🇪🇺","🇩🇰","🇸🇪","🇳🇴","🇫🇮","🇳🇱","🇧🇪","🇨🇭","🇦🇹","🇵🇱","🇺🇦","🇬🇷","🇹🇷","🇸🇦","🇦🇪","🇪🇬","🇳🇿" }),
    };
    private readonly List<Button> _emojiCatButtons = new();
    // 预渲染 emoji PNG 缓存：避免每次解码，面板与输入框共用，显著降低 UI 卡顿
    private static readonly Dictionary<string, BitmapImage> _emojiBmpCache = new();

    /// <summary>构建表情面板：生成分类按钮与各分类页面的 emoji 网格。</summary>
    private void BuildEmojiPanel()
    {
        EmojiCatBar.Children.Clear();
        EmojiPages.Children.Clear();
        _emojiCatButtons.Clear();

        for (int c = 0; c < EmojiCategories.Length; c++)
        {
            var cat = EmojiCategories[c];

            // 分类按钮（顶部图标 + 选中态高亮）。图标用彩色 SVG（与网格一致），不再用纯字体渲染
            var catGrid = new Grid();
            var btn = new Button
            {
                Content = catGrid,
                ToolTip = cat.Name,
                Tag = c,
                Width = 40, Height = 36, Margin = new Thickness(2, 0, 2, 0),
                Background = System.Windows.Media.Brushes.Transparent,
                BorderThickness = new Thickness(0),
                Cursor = System.Windows.Input.Cursors.Hand
            };
            AttachEmojiSvg(catGrid, cat.Icon); // 分类图标加载彩色 SVG（无则保持空，选中态仍高亮）
            var border = new Border { CornerRadius = new CornerRadius(9), Child = btn };
            border.Background = System.Windows.Media.Brushes.Transparent;
            btn.Template = BuildTransparentRoundTemplate();
            btn.Click += EmojiCat_Click;
            EmojiCatBar.Children.Add(border);
            _emojiCatButtons.Add(btn);

            // 该分类的页面（emoji 网格）：加宽至 386，一行可容纳 8 列，顶部分类栏完整显示 9 个分类
            var page = new WrapPanel { Width = 386 };
            foreach (var emoji in cat.Emojis)
            {
                // 彩色 emoji：SvgViewbox 渲染；无对应 SVG（缺失）时留空，不显示黑白字体兜底
                var grid = new Grid();
                var eb = new Button
                {
                    Tag = emoji,
                    Width = 42, Height = 40, Margin = new Thickness(2),
                    Background = System.Windows.Media.Brushes.Transparent,
                    BorderThickness = new Thickness(0),
                    Cursor = System.Windows.Input.Cursors.Hand,
                    Content = grid
                };
                eb.Template = BuildTransparentRoundTemplate();
                eb.Click += EmojiPick_Click;
                page.Children.Add(eb);
                AttachEmojiSvg(grid, emoji); // 离线加载彩色 SVG，成功后覆盖字体
            }
            page.Visibility = c == 0 ? Visibility.Visible : Visibility.Collapsed;
            EmojiPages.Children.Add(page);
        }
    }

    /// <summary>透明圆角按钮模板：内容居中，悬停淡色反馈，点击略微变暗。</summary>
    private static ControlTemplate BuildTransparentRoundTemplate()
    {
        var tpl = new ControlTemplate(typeof(Button));
        var bg = new FrameworkElementFactory(typeof(Border), "Bg");
        bg.SetValue(Border.CornerRadiusProperty, new CornerRadius(9));
        bg.SetValue(Border.BackgroundProperty, System.Windows.Media.Brushes.Transparent);
        var cp = new FrameworkElementFactory(typeof(ContentPresenter));
        cp.SetValue(ContentPresenter.HorizontalAlignmentProperty, System.Windows.HorizontalAlignment.Center);
        cp.SetValue(ContentPresenter.VerticalAlignmentProperty, System.Windows.VerticalAlignment.Center);
        bg.AppendChild(cp);
        tpl.VisualTree = bg;
        var hover = new Trigger { Property = Button.IsMouseOverProperty, Value = true };
        hover.Setters.Add(new Setter(System.Windows.Controls.Control.BackgroundProperty, FindBrush("SurfaceAltBrush"), "Bg"));
        tpl.Triggers.Add(hover);
        var press = new Trigger { Property = Button.IsPressedProperty, Value = true };
        press.Setters.Add(new Setter(System.Windows.Controls.Control.BackgroundProperty, FindBrush("AccentAltBrush"), "Bg"));
        tpl.Triggers.Add(press);
        return tpl;
    }

    private static System.Windows.Media.Brush FindBrush(string key)
    {
        var app = System.Windows.Application.Current;
        return app.TryFindResource(key) as System.Windows.Media.Brush ?? new SolidColorBrush(System.Windows.Media.Color.FromArgb(40, 128, 128, 128));
    }

    /// <summary>切换表情分类页面。</summary>
    private void EmojiCat_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button b || b.Tag is not int idx) return;
        for (int i = 0; i < EmojiPages.Children.Count; i++)
            EmojiPages.Children[i].Visibility = i == idx ? Visibility.Visible : Visibility.Collapsed;
        // 更新分类按钮选中态
        for (int i = 0; i < _emojiCatButtons.Count; i++)
        {
            var host = EmojiCatBar.Children[i] as Border;
            if (host != null) host.Background = i == idx ? FindBrush("AccentAltBrush") : System.Windows.Media.Brushes.Transparent;
        }
    }

    /// <summary>在表情面板中选中一个表情：以彩色图片形式插入输入框并关闭面板（苹果式，点一次选一个）。</summary>
    private void EmojiPick_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b && b.Tag is string emoji)
        {
            var para = ChatInput.Document.Blocks.LastBlock as Paragraph
                       ?? AppendEmptyParagraph();
            ApplyCompactLineHeight(para);
            para.Inlines.Add(BuildEmojiInline(emoji));
            ChatInput.Focus();
            ChatInput.CaretPosition = para.ContentEnd;
            EmojiPopup.IsOpen = false; // 选中即关闭，返回输入状态
        }
    }

    /// <summary>生成承载彩色 emoji 的内联元素：优先 Fluent SVG / 国旗 SVG，其次 Noto PNG；都缺失则退回 Unicode 文本。</summary>
    private Inline BuildEmojiInline(string emoji)
    {
        const string FallbackFamily = "Segoe UI Emoji";
        try
        {
            var hex = EmojiToHexName(emoji);
            if (hex.Length > 0)
            {
                var emojiDir = Path.Combine(AppContext.BaseDirectory, "Assets", "emoji");
                // 1) 预渲染 PNG（快、稳定、必定彩色）
                var pngPath = Path.Combine(emojiDir, hex + ".png");
                if (File.Exists(pngPath))
                {
                    return new InlineUIContainer(
                        new System.Windows.Controls.Image
                        {
                            Source = LoadEmojiBitmapCached(pngPath), Width = 14, Height = 14,
                            Stretch = System.Windows.Media.Stretch.Uniform,
                            VerticalAlignment = System.Windows.VerticalAlignment.Center
                        })
                    { Tag = emoji, BaselineAlignment = System.Windows.BaselineAlignment.Center };
                }
                // 2) Fluent 彩色 SVG
                var svg = Path.Combine(emojiDir, hex + "_color.svg");
                if (File.Exists(svg))
                    return MakeEmojiContainer(MakeEmojiSvg(svg), emoji);
                // 3) 国旗 SVG
                var flag = Path.Combine(emojiDir, hex + "_flag.svg");
                if (File.Exists(flag))
                    return MakeEmojiContainer(MakeEmojiSvg(flag), emoji);
                // 4) Noto PNG
                var png = Path.Combine(emojiDir, hex + "_noto.png");
                if (File.Exists(png))
                {
                    var bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.UriSource = new Uri(png, UriKind.Absolute);
                    bmp.EndInit();
                    bmp.Freeze();
                    return new InlineUIContainer(
                        new System.Windows.Controls.Image
                        {
                            Source = bmp, Width = 14, Height = 14,
                            Stretch = System.Windows.Media.Stretch.Uniform,
                            VerticalAlignment = System.Windows.VerticalAlignment.Center
                        })
                    { Tag = emoji, BaselineAlignment = System.Windows.BaselineAlignment.Center };
                }
            }
        }
        catch { /* 缺失/解析失败 → 退回文本 */ }
        return new Run(emoji) { FontFamily = new System.Windows.Media.FontFamily(FallbackFamily) };
    }

    /// <summary>把 emoji SVG 渲染成固定尺寸、竖直居中的大小用于内联展示。</summary>
    private static SvgViewbox MakeEmojiSvg(string path) => new()
    {
        Source = new Uri(path, UriKind.Absolute),
        Width = 14, Height = 14,
        Stretch = System.Windows.Media.Stretch.Uniform,
        VerticalAlignment = System.Windows.VerticalAlignment.Center
    };

    /// <summary>内联区容器统一竖直居中，使 emoji 与文字行基线对齐。</summary>
    private static InlineUIContainer MakeEmojiContainer(UIElement el, string emoji) => new(el)
    {
        Tag = emoji,
        BaselineAlignment = System.Windows.BaselineAlignment.Center
    };

    /// <summary>输入框末尾追加一个空段落并返回，确保始终有可写入的段落。</summary>
    private Paragraph AppendEmptyParagraph()
    {
        var p = NewCompactParagraph();
        ChatInput.Document.Blocks.Add(p);
        return p;
    }

    /// <summary>创建承载紧凑行距的段落，避免 RichTextBox 默认行高过大导致换行后两行间隔异常大。</summary>
    private static Paragraph NewCompactParagraph()
    {
        var p = new Paragraph();
        ApplyCompactLineHeight(p);
        return p;
    }

    /// <summary>把段落行距固化为紧凑值（对齐 TextBox 的观感）；LineHeight 取输入字号 13 的约 1.4 倍，14px emoji 不越界。</summary>
    private static void ApplyCompactLineHeight(Paragraph p)
    {
        p.LineHeight = 18;
        p.LineStackingStrategy = System.Windows.LineStackingStrategy.BlockLineHeight;
        p.Margin = new Thickness(0); // 去掉段落边距，避免段落之间再叠加垂直间距导致"空一整行"
    }

    /// <summary>把输入框内容还原为纯文本：内联 emoji 图片还原为其对应的 Unicode 字符，段落用换行分隔。</summary>
    private string GetTextFromInput()
    {
        var sb = new StringBuilder();
        var first = true;
        foreach (Block block in ChatInput.Document.Blocks)
        {
            if (block is not Paragraph para) continue;
            if (!first) sb.Append('\n');
            first = false;
            foreach (Inline inline in para.Inlines)
            {
                switch (inline)
                {
                    case Run run:
                        sb.Append(run.Text);
                        break;
                    case LineBreak:
                        sb.Append('\n'); // 手动换行的硬换行 → 换行符，保证发送/展示时换行保留
                        break;
                    case InlineUIContainer ic when ic.Tag is string em:
                        sb.Append(em);
                        break;
                }
            }
        }
        return sb.ToString();
    }

    /// <summary>清空输入框内容，并重建一个空段落以便继续输入。</summary>
    private void ClearInputBox()
    {
        ChatInput.Document.Blocks.Clear();
        ChatInput.Document.Blocks.Add(NewCompactParagraph());
    }

    /// <summary>离线加载 emoji 彩色图标（随应用部署于输出目录）：
/// 首选预渲染 PNG（快、稳、无 SVG 解析卡顿），其次 Fluent color SVG / 国旗 SVG / Noto PNG；缺失则留空。</summary>
    private void AttachEmojiSvg(Grid grid, string emoji)
    {
        try
        {
            var hex = EmojiToHexName(emoji);
            if (hex.Length == 0) return;
            var emojiDir = Path.Combine(AppContext.BaseDirectory, "Assets", "emoji");

            // 1) 预渲染 PNG（离线转好的位图，读取快、必定能画出来）
            var pngPath = Path.Combine(emojiDir, hex + ".png");
            if (File.Exists(pngPath))
            {
                grid.Children.Add(new System.Windows.Controls.Image
                {
                    Source = LoadEmojiBitmapCached(pngPath),
                    Width = 30, Height = 30,
                    Stretch = System.Windows.Media.Stretch.Uniform,
                    HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                    VerticalAlignment = System.Windows.VerticalAlignment.Center
                });
                return;
            }

            // 2) Fluent color SVG
            var svgPath = Path.Combine(emojiDir, hex + "_color.svg");
            if (File.Exists(svgPath))
            {
                grid.Children.Add(new SharpVectors.Converters.SvgViewbox
                {
                    Source = new Uri(svgPath, UriKind.Absolute),
                    Width = 30, Height = 30,
                    HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                    VerticalAlignment = System.Windows.VerticalAlignment.Center
                });
                return;
            }

            // 3) 国家旗帜 SVG
            var flagPath = Path.Combine(emojiDir, hex + "_flag.svg");
            if (File.Exists(flagPath))
            {
                grid.Children.Add(new SharpVectors.Converters.SvgViewbox
                {
                    Source = new Uri(flagPath, UriKind.Absolute),
                    Width = 30, Height = 30,
                    HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                    VerticalAlignment = System.Windows.VerticalAlignment.Center
                });
                return;
            }

            // 4) Noto PNG（非国旗的零散缺项）
            var notoPath = Path.Combine(emojiDir, hex + "_noto.png");
            if (File.Exists(notoPath))
            {
                var bmp = new BitmapImage();
                bmp.BeginInit();
                bmp.CacheOption = BitmapCacheOption.OnLoad;
                bmp.UriSource = new Uri(notoPath, UriKind.Absolute);
                bmp.EndInit();
                bmp.Freeze();
                grid.Children.Add(new System.Windows.Controls.Image
                {
                    Source = bmp,
                    Width = 30, Height = 30,
                    Stretch = System.Windows.Media.Stretch.Uniform,
                    HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                    VerticalAlignment = System.Windows.VerticalAlignment.Center
                });
            }
        }
        catch { /* 缺失/解析失败：留空，不显示黑白兜底 */ }
    }

    /// <summary>按文件路径加载并缓存 emoji 位图（冻结后复用，避免重复解码）。</summary>
    private static ImageSource LoadEmojiBitmapCached(string path)
    {
        if (_emojiBmpCache.TryGetValue(path, out var cached)) return cached;
        var bmp = new BitmapImage();
        bmp.BeginInit();
        bmp.CacheOption = BitmapCacheOption.OnLoad;
        bmp.UriSource = new Uri(path, UriKind.Absolute);
        bmp.EndInit();
        bmp.Freeze();
        _emojiBmpCache[path] = bmp;
        return bmp;
    }

    /// <summary>将 emoji 字符串转为以 '-' 连接的小写十六进制码点序列，去掉变体选择符(U+FE0F)。</summary>
    private static string EmojiToHexName(string emoji)
    {
        var sb = new StringBuilder();
        for (int i = 0; i < emoji.Length;)
        {
            int cp = char.ConvertToUtf32(emoji, i);
            i += char.IsSurrogatePair(emoji, i) ? 2 : 1;
            if (cp == 0xFE0F) continue; // 变体选择符不计入文件名
            if (sb.Length > 0) sb.Append('-');
            sb.Append(cp.ToString("x"));
        }
        return sb.ToString();
    }

    /// <summary>待发送的一个单元：单个文件，或一个文件夹（发送时递归展开为带相对路径的文件列表，接收端据此重建目录结构）。</summary>
    private sealed class PendingUnit
    {
        public string? File { get; init; }    // 普通文件/截图的绝对路径
        public string? Folder { get; init; }  // 文件夹的绝对路径
        public bool IsFolder => !string.IsNullOrEmpty(Folder);
    }

    /// <summary>待发送的附件单元集合（截图 + 文件 + 文件夹），点发送后统一发出。</summary>
    private readonly List<PendingUnit> _pending = new();

    /// <summary>截图下拉托盘开合：IsChecked 已双向绑定 Popup.IsOpen，这里只负责显式同步。</summary>
    private void ScreenshotOpt_Click(object sender, RoutedEventArgs e)
        => ScreenshotOptPopup.IsOpen = ScreenshotOptBtn.IsChecked == true;

    /// <summary>截图功能键：弹出全屏区域截图窗口，选定区域后可简单编辑，确认后放入输入区等待发送。
    /// 按「截图时保留本地窗口」设置决定是否隐藏主窗口再截（默认隐藏，保证截图干净）。</summary>
    private void Screenshot_Click(object sender, RoutedEventArgs e)
    {
        EmojiPopup.IsOpen = false;
        var keepWindow = _settings.KeepWindowDuringScreenshot;
        var wasVisible = Visibility;
        if (!keepWindow) Visibility = Visibility.Hidden;
        try
        {
            var shot = new ScreenshotWindow(_saveDir);
            if (shot.ShowDialog() == true && shot.SavedPath is string path && File.Exists(path))
            {
                // 微信式：截图进入输入区缩略图，点发送才发出，可移除
                _pending.Add(new PendingUnit { File = path });
                UpdatePendingPreview();
            }
        }
        finally
        {
            if (!keepWindow && wasVisible == Visibility.Visible)
                Visibility = Visibility.Visible;
        }
    }

    /// <summary>刷新待发送附件缩略图区（靠左放大，微信式，右上角移除叉）。</summary>
    private void UpdatePendingPreview()
    {
        PendingWrap.Children.Clear();
        foreach (var (unit, index) in _pending.Select((u, i) => (u, i)))
        {
            // 文件夹单元：显示文件夹瓦片（点发送时再递归展开为文件）
            if (unit.IsFolder)
            {
                if (!Directory.Exists(unit.Folder)) { _pending.Remove(unit); continue; }
                PendingWrap.Children.Add(BuildPendingFolderEntry(unit.Folder!, index));
                continue;
            }
            var path = unit.File!;
            if (!File.Exists(path)) continue;
            var ext = Path.GetExtension(path).ToLowerInvariant();
            var isImg = TransferItem.IsImageExt(path);
            var isVideo = TransferItem.IsVideoExt(path);
            var isAudio = TransferItem.IsAudioExt(path);

            var thumb = new Border
            {
                Width = 108, Height = 108, Margin = new Thickness(0),
                CornerRadius = new CornerRadius(8),
                Background = new SolidColorBrush(System.Windows.Media.Color.FromArgb(255, 0, 0, 0)),
                ClipToBounds = true,
                BorderBrush = TryFindResource("BorderBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent,
                BorderThickness = new Thickness(1)
            };

            if (isVideo)
            {
                thumb.Child = BuildVideoTile(path);
            }
            else if (isImg)
            {
                try
                {
                    var bmp = new BitmapImage();
                    bmp.BeginInit();
                    bmp.CacheOption = BitmapCacheOption.OnLoad;
                    bmp.UriSource = new Uri(path);
                    bmp.EndInit();
                    // 居中裁切：WPF Image+UniformToFill 锚定左上（实验验证），必须用 ImageBrush 显式居中
                    thumb.Child = new Border
                    {
                        Background = new ImageBrush(bmp)
                        {
                            Stretch = Stretch.UniformToFill,
                            AlignmentX = AlignmentX.Center,
                            AlignmentY = AlignmentY.Center
                        }
                    };
                }
                catch
                {
                    thumb.Child = BuildFileNameLabel(path);
                }
            }
            else if (isAudio)
            {
                thumb.Child = BuildAudioTile(path);
            }
            else
            {
                // 普通文件用浅底而非黑底，与浅色主题一致
                thumb.Background = TryFindResource("SurfaceBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent;
                thumb.Child = BuildPendingFileTile(path); // 普通文件：专属图标(Office/PDF)或通用文件图标 + 底部文件名
            }

            // 右上角移除叉（微信式：深灰圆形按钮）
            var remove = new Button
            {
                Width = 22, Height = 22, Tag = index,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Right,
                VerticalAlignment = System.Windows.VerticalAlignment.Top,
                Margin = new Thickness(0, 2, 2, 0),
                Background = System.Windows.Media.Brushes.Transparent, BorderThickness = new Thickness(0),
                ToolTip = "移除"
            };
            remove.Click += PendRemove_Click;
            remove.Style = (Style)FindResource("PendingRemoveTemplate");

            // 图片/视频/音频缩略图可点击 → 打开预览窗口
            if (isImg || isVideo || isAudio)
            {
                thumb.Tag = path;
                thumb.Cursor = System.Windows.Input.Cursors.Hand;
                thumb.ToolTip = "点击预览";
                thumb.MouseLeftButtonUp += PendingThumb_MouseUp;
            }

            var grid = new Grid { Margin = new Thickness(0, 0, 8, 8) };
            grid.Children.Add(thumb);
            grid.Children.Add(remove);
            PendingWrap.Children.Add(grid);
        }
        PendingPanel.Visibility = _pending.Count > 0 ? Visibility.Visible : Visibility.Collapsed;
        RefreshSendEnabled();
        ChatInput.Focus();
    }

    private static TextBlock BuildFileNameLabel(string path)
    {
        var name = Path.GetFileName(path);
        var label = new TextBlock
        {
            Text = name, FontSize = 12, TextWrapping = TextWrapping.Wrap,
            Foreground = System.Windows.Media.Brushes.White,
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
            VerticalAlignment = System.Windows.VerticalAlignment.Center,
            TextAlignment = System.Windows.TextAlignment.Center,
            MaxWidth = 100, Margin = new Thickness(6)
        };
        return label;
    }

    /// <summary>按扩展名返回 Office / PDF 专属品牌图标（嵌入资源），无专属图标返回 null。</summary>
    private static BitmapImage? MapOfficeIcon(string path)
    {
        var ext = Path.GetExtension(path).ToLowerInvariant();
        string? name = ext switch
        {
            ".pdf" => "fabric_pdf.png",
            ".doc" or ".docx" or ".dot" or ".dotx" or ".rtf" or ".wps" or ".odt" or ".odm" or ".ott" => "fabric_word.png",
            ".xls" or ".xlsx" or ".xlsm" or ".xlsb" or ".xltx" or ".ods" or ".csv" => "fabric_excel.png",
            ".ppt" or ".pptx" or ".pps" or ".ppsx" or ".pot" or ".odp" or ".key" => "fabric_powerpoint.png",
            // 压缩包 / 归档文件
            ".zip" or ".zipx" or ".7z" or ".rar" or ".tar" or ".gz" or ".tgz" or ".bz2" or ".tbz2" or ".xz" or ".zst" or ".z" or ".lz" or ".arj" or ".iso" => "fabric_zip.png",
            _ => null
        };
        if (name is null) return null;
        try
        {
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.UriSource = new Uri("pack://application:,,,/Assets/office/" + name, UriKind.Absolute);
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.EndInit();
            return bmp;
        }
        catch { return null; }
    }

    /// <summary>通用文件图标（纸张 + 折角 + 文档线），颜色取自适应前景色，浅深主题均清晰。</summary>
    private static DrawingImage BuildGenericDocIcon(System.Windows.Media.Color fg)
    {
        var g = new System.Windows.Media.DrawingGroup();
        var paper = new SolidColorBrush(System.Windows.Media.Color.FromArgb(0x26, fg.R, fg.G, fg.B)); // 纸张底色(前景淡透明)
        var linePen = new System.Windows.Media.Pen(new SolidColorBrush(System.Windows.Media.Color.FromArgb(0x66, fg.R, fg.G, fg.B)), 1.6);
        var fold = new SolidColorBrush(System.Windows.Media.Color.FromArgb(0x3D, fg.R, fg.G, fg.B));
        var page = new System.Windows.Media.GeometryDrawing(paper, linePen, new RectangleGeometry(new Rect(10, 8, 28, 30), 2, 2));
        var fold2 = new System.Windows.Media.GeometryDrawing(fold, null, Geometry.Parse("M40 8 L27 8 L40 21 Z"));
        var lines = new System.Windows.Media.GeometryDrawing(null, linePen, Geometry.Parse("M16 16 H32 M16 21 H28 M16 26 H31"));
        g.Children.Add(fold2);
        g.Children.Add(page);
        g.Children.Add(lines);
        return new DrawingImage(g);
    }

    /// <summary>普通文件待发缩略图：中央专属/通用文件图标 + 底部文件名（替代此前仅黑底文字的无图标样式）。
    /// 文件名与通用图标颜色取自适应前景色，浅色/深色主题均清晰可辨。</summary>
    private StackPanel BuildPendingFileTile(string path)
    {
        var fgBrush = TryFindResource("TextPrimaryBrush") as SolidColorBrush;
        var fg = (fgBrush?.Color ?? System.Windows.Media.Colors.Gray);
        var stack = new StackPanel
        {
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
            VerticalAlignment = System.Windows.VerticalAlignment.Center
        };
        var icon = MapOfficeIcon(path);
        stack.Children.Add(new System.Windows.Controls.Image
        {
            Source = (ImageSource?)icon ?? BuildGenericDocIcon(fg),
            Width = 40, Height = 40, Stretch = Stretch.Uniform,
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center
        });
        var name = Path.GetFileName(path);
        if (!string.IsNullOrEmpty(name))
            stack.Children.Add(new TextBlock
            {
                Text = name, FontSize = 11, TextWrapping = TextWrapping.Wrap,
                Foreground = new SolidColorBrush(fg),
                TextAlignment = System.Windows.TextAlignment.Center,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                MaxWidth = 96, Margin = new Thickness(6, 6, 6, 6)
            });
        return stack;
    }

    /// <summary>文件夹待发瓦片：浅底 + 中央黄色文件夹图标(flat-color-icons--folder) + 底部文件夹名。
    /// 自带右上角移除叉（文件夹分支在 UpdatePendingPreview 中 continue，未走通用移除逻辑）。</summary>
    private FrameworkElement BuildPendingFolderEntry(string folder, int index)
    {
        var fgBrush = TryFindResource("TextPrimaryBrush") as SolidColorBrush;
        var fg = fgBrush?.Color ?? System.Windows.Media.Colors.Gray;
        var name = Path.GetFileName(Path.TrimEndingDirectorySeparator(folder));
        if (string.IsNullOrEmpty(name)) name = folder;

        var folderIcon = new BitmapImage();
        try
        {
            folderIcon.BeginInit();
            folderIcon.UriSource = new Uri("pack://application:,,,/Assets/office/fabric_folder.png", UriKind.Absolute);
            folderIcon.CacheOption = BitmapCacheOption.OnLoad;
            folderIcon.EndInit();
        }
        catch { /* 图标缺失时留空 */ }

        var thumb = new Border
        {
            Width = 108, Height = 108, CornerRadius = new CornerRadius(8),
            Background = TryFindResource("SurfaceBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent,
            ClipToBounds = true,
            BorderBrush = TryFindResource("BorderBrush") as System.Windows.Media.Brush ?? System.Windows.Media.Brushes.Transparent,
            BorderThickness = new Thickness(1),
            Child = new StackPanel
            {
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = System.Windows.VerticalAlignment.Center,
                Children =
                {
                    new System.Windows.Controls.Image
                    {
                        Source = folderIcon, Width = 46, Height = 46, Stretch = Stretch.Uniform,
                        HorizontalAlignment = System.Windows.HorizontalAlignment.Center
                    },
                    new TextBlock
                    {
                        Text = name, FontSize = 11, TextWrapping = TextWrapping.Wrap,
                        Foreground = new SolidColorBrush(fg),
                        TextAlignment = System.Windows.TextAlignment.Center,
                        HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                        MaxWidth = 96, Margin = new Thickness(6, 6, 6, 6)
                    }
                }
            }
        };

        var remove = new Button
        {
            Width = 22, Height = 22, Tag = index,
            HorizontalAlignment = System.Windows.HorizontalAlignment.Right,
            VerticalAlignment = System.Windows.VerticalAlignment.Top,
            Margin = new Thickness(0, 2, 2, 0),
            Background = System.Windows.Media.Brushes.Transparent, BorderThickness = new Thickness(0),
            ToolTip = "移除"
        };
        remove.Click += PendRemove_Click;
        remove.Style = (Style)FindResource("PendingRemoveTemplate");

        var grid = new Grid { Margin = new Thickness(0, 0, 8, 8) };
        grid.Children.Add(thumb);
        grid.Children.Add(remove);
        return grid;
    }
    private static FrameworkElement BuildVideoTile(string path)
    {
        var g = new Grid { Background = System.Windows.Media.Brushes.Black };
        // 与图片瓦片同格式：居中裁切填满方格（ImageBrush 显式居中，WPF Image+UniformToFill 会锚定左上）
        var tile = new Border();
        g.Children.Add(tile);
        _ = ShellThumbnail.LoadThumbnailAsync(path, 256, src =>
        {
            if (src != null) tile.Background = new ImageBrush(src)
            {
                Stretch = Stretch.UniformToFill,
                AlignmentX = AlignmentX.Center,
                AlignmentY = AlignmentY.Center
            };
        });

        var play = new Border
        {
            Background = new SolidColorBrush(System.Windows.Media.Color.FromArgb(170, 0, 0, 0)),
            Width = 38, Height = 38, CornerRadius = new CornerRadius(19),
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
            VerticalAlignment = System.Windows.VerticalAlignment.Center,
            Child = new System.Windows.Shapes.Path
            {
                Data = (Geometry)Geometry.Parse("M7 4 L20 12 L7 20 Z"),
                Fill = System.Windows.Media.Brushes.White,
                Width = 14, Height = 15, Stretch = Stretch.Uniform,
                HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
                VerticalAlignment = System.Windows.VerticalAlignment.Center
            }
        };
        g.Children.Add(play);
        return g;
    }

    /// <summary>音频预览卡：黑底 + 中央播放按钮（MediaElement 播放该音频）+ 底部文件名。</summary>
    private static FrameworkElement BuildAudioTile(string path)
    {
        var g = new Grid { Background = System.Windows.Media.Brushes.Black };

        // 居中圆形播放按钮，点击切换播放/暂停
        MediaElement? me = null;
        var play = new System.Windows.Shapes.Path
        {
            Data = (Geometry)Geometry.Parse("M8 5 L19 13 L8 21 Z"),
            Fill = System.Windows.Media.Brushes.White,
            Width = 16, Height = 18, Stretch = Stretch.Uniform,
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
            VerticalAlignment = System.Windows.VerticalAlignment.Center
        };
        var btn = new Button
        {
            Background = new SolidColorBrush(System.Windows.Media.Color.FromArgb(170, 0, 0, 0)),
            BorderThickness = new Thickness(0),
            Width = 40, Height = 40,
            HorizontalAlignment = System.Windows.HorizontalAlignment.Center,
            VerticalAlignment = System.Windows.VerticalAlignment.Center,
            Cursor = System.Windows.Input.Cursors.Hand,
            Content = play
        };
        btn.Click += (_, _) =>
        {
            if (me is null) return;
            if (me.HasAudio && _playingAudio.Contains(me)) { me.Pause(); _playingAudio.Remove(me); }
            else { me.Position = TimeSpan.Zero; me.Play(); _playingAudio.Add(me); }
        };

        me = new MediaElement
        {
            Source = new Uri(path),
            LoadedBehavior = System.Windows.Controls.MediaState.Manual,
            IsHitTestVisible = false
        };

        var cap = new Border
        {
            VerticalAlignment = System.Windows.VerticalAlignment.Bottom,
            Background = new SolidColorBrush(System.Windows.Media.Color.FromArgb(140, 0, 0, 0)),
            Padding = new Thickness(4, 1, 4, 1),
            Child = new TextBlock
            {
                Text = Path.GetFileName(path),
                FontSize = 10, Foreground = System.Windows.Media.Brushes.White,
                TextTrimming = TextTrimming.CharacterEllipsis, MaxWidth = 100
            }
        };
        g.Children.Add(me);
        g.Children.Add(btn);
        g.Children.Add(cap);
        return g;
    }

    /// <summary>当前正在播放的音频 MediaElement（用于播放/暂停状态跟踪）。</summary>
    private static readonly System.Collections.Generic.HashSet<MediaElement> _playingAudio = new();

    /// <summary>传输记录气泡里的音频播放按钮：切换同气泡内 MediaElement 的播放/暂停。</summary>
    private void AudioPlay_Click(object sender, RoutedEventArgs e)
    {
        if (sender is not Button btn) return;
        if (btn.DataContext is not TransferItem ti || string.IsNullOrEmpty(ti.MediaPath)) return;
        // 在按钮的整棵可视树祖先里找同气泡的 MediaElement
        var me = FindVisualChild<MediaElement>(btn) ?? FindMediaInAncestors(btn);
        if (me is null || me.Source is null) return;
        if (_playingAudio.Contains(me)) { me.Pause(); _playingAudio.Remove(me); }
        else { me.Position = TimeSpan.Zero; me.Play(); _playingAudio.Add(me); }
    }

    /// <summary>沿可视祖先向上查找某子树内的第一个 MediaElement。</summary>
    private static MediaElement? FindMediaInAncestors(DependencyObject start)
    {
        var node = start;
        while (node != null)
        {
            var found = FindVisualChild<MediaElement>(node);
            if (found != null && found.Source != null) return found;
            node = System.Windows.Media.VisualTreeHelper.GetParent(node);
        }
        return null;
    }

    /// <summary>深度优先查找指定类型的第一个可视子元素。</summary>
    private static T? FindVisualChild<T>(DependencyObject parent) where T : DependencyObject
    {
        var count = System.Windows.Media.VisualTreeHelper.GetChildrenCount(parent);
        for (int i = 0; i < count; i++)
        {
            var child = System.Windows.Media.VisualTreeHelper.GetChild(parent, i);
            if (child is T hit) return hit;
            if (child is DependencyObject d)
            {
                var deep = FindVisualChild<T>(d);
                if (deep != null) return deep;
            }
        }
        return null;
    }

    /// <summary>点击传输记录里的图片/视频缩略图：打开应用内媒体预览窗口（图片可缩放、视频/音频可播放）。</summary>
    private void PreviewMedia_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (IsHeicHintHit(e.OriginalSource)) return; // 点击 HEIC 缺解码器提示不触发预览
        if (sender is Border b && b.DataContext is TransferItem ti && !string.IsNullOrEmpty(ti.MediaPath))
        {
            e.Handled = true;
            // 独立顶级窗口（不设 Owner）：任务栏两个按钮各自显示各自内容，像微信图片查看那样
            OpenPreview(ti.MediaPath);
        }
    }

    /// <summary>集成网格内小缩略图点击：打开对应图片/视频的大图预览（支持旧版 string 路径与新版 TransferItem 子项）。</summary>
    private void IntegratedThumb_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (IsHeicHintHit(e.OriginalSource)) return; // 点击 HEIC 缺解码器提示不触发预览
        if (sender is not Border b) return;
        var path = b.DataContext switch
        {
            string s => s,
            TransferItem ti => ti.MediaPath,
            _ => ""
        };
        if (!string.IsNullOrEmpty(path))
        {
            e.Handled = true;
            OpenPreview(path);
        }
    }

    /// <summary>气泡/网格里的「去安装」链接：打开微软商店 HEIF 图像扩展页面。</summary>
    private void InstallHeifLink_Click(object sender, System.Windows.Navigation.RequestNavigateEventArgs e)
    {
        e.Handled = true;
        try
        {
            System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(
                "ms-windows-store://pdp/?ProductId=9N4WGH0Z6VHQ")
            { UseShellExecute = true });
        }
        catch { /* 商店不可用则忽略 */ }
    }

    /// <summary>命中判断：鼠标点击是否落在 Tag="HeicHint" 的提示浮层内。</summary>
    private static bool IsHeicHintHit(object? original)
    {
        if (original is not DependencyObject d) return false;
        for (var o = d; o is not null; o = System.Windows.Media.VisualTreeHelper.GetParent(o))
            if (o is FrameworkElement fe && fe.Tag is string s && s == "HeicHint") return true;
        return false;
    }

    /// <summary>圆角裁剪：在容器 Border 尺寸真实确定（SizeChanged）后按实际宽高生成圆角矩形裁剪，
    /// 一次性裁掉其全部子内容（含图片），四个角都圆。圆角半径取自 Border.Tag。
    /// 取代"绑定 ActualWidth 的动态几何/只裁图不裁容器"的旧方案，彻底避免半圆角/空白。</summary>
    private void RoundedClipBorder_SizeChanged(object sender, SizeChangedEventArgs e)
    {
        if (sender is not Border b) return;
        double w = b.ActualWidth, h = b.ActualHeight;
        if (w <= 0 || h <= 0) return;
        double r = b.Tag is string tag && double.TryParse(tag, out var v) && v > 0 ? v : 12;
        var geo = new System.Windows.Media.RectangleGeometry(new Rect(0, 0, w, h), r, r);
        geo.Freeze();
        b.Clip = geo;
    }

    /// <summary>在主体窗口附近打开独立预览窗口（不设 Owner，任务栏两窗口各显各的内容）。</summary>
    private void OpenPreview(string path)
    {
        if (!File.Exists(path))
        {
            ShowDialog(Localization.LangManager.T("Common.Error"), Localization.LangManager.T("Msg.FileMissing"), DialogKind.Warning);
            return;
        }
        var win = new MediaPreviewWindow(path) { WindowStartupLocation = WindowStartupLocation.Manual };
        win.Left = Left + (ActualWidth - win.Width) / 2;
        win.Top = Top + (ActualHeight - win.Height) / 2;
        win.Show();
    }

    /// <summary>普通文件（PDF/Office/压缩包等）点击：用系统默认应用快速打开该文件。
    /// 图片/视频走 PreviewMedia_MouseUp 预览窗口，此处仅处理非媒体文件，文件已被删除/移动则提示。</summary>
    private void OpenFile_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (sender is FrameworkElement fe && fe.DataContext is TransferItem ti && !string.IsNullOrEmpty(ti.MediaPath))
        {
            e.Handled = true;
            if (File.Exists(ti.MediaPath))
            {
                try { System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo(ti.MediaPath) { UseShellExecute = true }); }
                catch (Exception ex) { ShowDialog(Localization.LangManager.T("Common.Error"), ex.Message, DialogKind.Error); }
            }
            else
            {
                ShowDialog(Localization.LangManager.T("Common.Error"), Localization.LangManager.T("Msg.FileMissing"), DialogKind.Warning);
            }
        }
    }

    /// <summary>移除指定待发送附件。</summary>
    private void PendRemove_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button b && b.Tag is int index && index >= 0 && index < _pending.Count)
            _pending.RemoveAt(index);
        UpdatePendingPreview();
    }

    /// <summary>点击待发缩略图 → 打开图片/视频/音频预览窗口。</summary>
    private void PendingThumb_MouseUp(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (sender is Border b && b.Tag is string path && File.Exists(path))
        {
            e.Handled = true;
            // 独立顶级窗口（不设 Owner）：任务栏两个按钮各自显示各自内容，像微信图片查看那样
            OpenPreview(path);
        }
    }

    private async Task SendTextAsync(PeerItem peer, string text)
    {
        // LocalSend 设备仅兼容文件互传，不支持文字消息（未实现阶段二发送；先明确提示，避免静默失败）
        if (peer.IsLocalSend)
        {
            ShowDialog(Localization.LangManager.T("Common.Info"), string.Format(Localization.LangManager.T("Msg.LocalSendNoText"), peer.Name), DialogKind.Info);
            return;
        }

        var msg = new Protocol.TextMessage
        {
            SendId = Guid.NewGuid().ToString("N"),
            DeviceId = _deviceId,
            Name = _deviceName,
            Content = text,
            Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()
        };
        var trans = new TransferItem { Name = "文字", Direction = "发送", Target = peer.Name, IsText = true, Content = text, SendId = msg.SendId, Timestamp = msg.Timestamp };
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
            ShowDialog(Localization.LangManager.T("Common.Error"), string.Format(Localization.LangManager.T("Msg.SendFailed"), ex.Message), DialogKind.Error);
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
                State = "完成",
                SendId = msg.SendId,
                Timestamp = msg.Timestamp
            });
        });
        return Task.CompletedTask;
    }

    /// <summary>收到对端撤回命令：把本机对应"接收"记录标记为已撤回（按 sendId 匹配）。</summary>
    private Task OnRecallAsync(string sendId)
    {
        Dispatcher.BeginInvoke(() =>
        {
            foreach (var t in _vm.Transfers.Where(t => t.Direction == "接收" && t.SendId == sendId && !t.Recalled))
            {
                // 撤回即删除已落盘的接收文件（单文件 MediaPath / 多文件 MediaPaths）
                var paths = new List<string>();
                if (!string.IsNullOrEmpty(t.MediaPath)) paths.Add(t.MediaPath);
                paths.AddRange(t.MediaPaths);
                foreach (var p in paths)
                {
                    if (string.IsNullOrEmpty(p)) continue;
                    // [诊断] 记录待删路径，用于排查"撤回后文件残留"
                    var recallLog = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "ogo_recall.log");
                    try { System.IO.File.AppendAllText(recallLog, $"[{DateTime.Now:HH:mm:ss}] DELETE=[{p}] exists={System.IO.File.Exists(p)}\n"); } catch { }
                    try
                    {
                        if (System.IO.File.Exists(p))
                        {
                            var attrs = System.IO.File.GetAttributes(p);
                            if ((attrs & FileAttributes.ReadOnly) != 0)
                                System.IO.File.SetAttributes(p, attrs & ~FileAttributes.ReadOnly);
                            System.IO.File.Delete(p);
                        }
                    }
                    catch { /* 占用/只读等忽略，仅标记撤回 */ }
                }
                // 更新记录关联路径，避免界面继续指向已删除的文件
                Core.ThumbCache.Delete(t.AllThumbKeys()); // 撤回同时精确回收该条（含子项）缩略图缓存
                t.ThumbKeys.Clear();
                foreach (var s in t.SubItems) s.ThumbKeys.Clear();
                t.Recalled = true;
            }
        });
        return Task.CompletedTask;
    }

    /// <summary>撤回某条已发送记录：本地标撤回，并向所有在线对端广播撤回命令（接收端按 sendId 匹配）。</summary>
    private async void RecallTransfer_Click(object sender, RoutedEventArgs e)
    {
        if ((sender as FrameworkElement)?.DataContext is not TransferItem item || item.Direction != "发送") return;
        item.Recalled = true;
        item.State = "已撤回";
        Core.ThumbCache.Delete(item.AllThumbKeys()); // 撤回发送记录（含子项）同步回收其缩略图缓存
        item.ThumbKeys.Clear();
        foreach (var s in item.SubItems) s.ThumbKeys.Clear();
        var sendId = item.SendId;
        if (string.IsNullOrEmpty(sendId)) return;
        foreach (var peer in _vm.Peers.ToList())
        {
            try
            {
                await _sender.SendRecallAsync(IPAddress.Parse(peer.Ip), peer.Port, sendId);
            }
            catch { /* 忽略送达失败 */ }
        }
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
            var path = Path.Combine(downloads, string.Format(Localization.LangManager.T("File.ChatSaveName"), stamp));
            File.WriteAllText(path, item.Content ?? "");
            ShowDialog(Localization.LangManager.T("Common.Success"), string.Format(Localization.LangManager.T("Msg.SavedTo"), path), DialogKind.Success);
        }
        catch (Exception ex)
        {
            ShowDialog(Localization.LangManager.T("Common.Error"), string.Format(Localization.LangManager.T("Msg.SaveFailed"), ex.Message), DialogKind.Error);
        }
    }

    // ---------- 拖拽发送 ----------
    private void DeviceArea_DragOver(object sender, System.Windows.DragEventArgs e)
    {
        e.Effects = e.Data.GetDataPresent(DataFormats.FileDrop) && _vm.HasSelection
            ? DragDropEffects.Copy : DragDropEffects.None;
        e.Handled = true;
    }
    private void DeviceArea_Drop(object sender, System.Windows.DragEventArgs e)
    {
        e.Handled = true;
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] files || files.Length == 0) return;
        // 与发送按钮一致：未选中设备时先沿用上次发送目标，仍无记录才提示用户选择
        var peers = ResolveSendPeers();
        if (peers.Count == 0)
        {
            ShowDialog(Localization.LangManager.T("Send.NoDeviceTitle"), Localization.LangManager.T("Msg.PickDeviceFirst"), DialogKind.Info);
            return;
        }
        foreach (var peer in peers) SendFiles(peer, files);
    }
    private void DeviceTile_Drop(object sender, System.Windows.DragEventArgs e)
    {
        e.Handled = true;
        if (e.Data.GetData(DataFormats.FileDrop) is not string[] files || files.Length == 0) return;
        if ((sender as FrameworkElement)?.DataContext is PeerItem peer)
        {
            // 拖到具体设备磁贴是明确的目标选择（内存选中，本次运行有效）
            SelectPeerInList(peer);
            SendFiles(peer, files);
        }
    }

    /// <summary>返回当前多选中的设备。</summary>
    private List<PeerItem> SelectedPeers()
    {
        var list = new List<PeerItem>();
        foreach (var s in DeviceList.SelectedItems)
            if (s is PeerItem p) list.Add(p);
        return list;
    }

    /// <summary>设备列表多选变化：更新底部栏“已选 N 台”计数与发送可用状态（选中态仅内存，本次运行有效）。</summary>
    private void DeviceList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _vm.UpdateSelection(DeviceList.SelectedItems.Count);
    }

    /// <summary>发送目标统一入口：仅使用设备界面当前选中的设备。选中态由列表内存维护（本次运行有效，重启后需重新选择）。</summary>
    private List<PeerItem> ResolveSendPeers()
    {
        return SelectedPeers();
    }

    /// <summary>点一下选中、再点一下取消选中（切换）。点击已选中卡片时，等 ListBox 本轮选中完成后反选。</summary>
    private void DeviceList_PreviewMouseLeftButtonDown(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if (e.ChangedButton != System.Windows.Input.MouseButton.Left) return;
        // 点收藏星标 / Shift、Ctrl 多选时不做反选切换
        if (FindVisualParent<System.Windows.Controls.Primitives.ToggleButton>(e.OriginalSource as DependencyObject) is not null) return;
        if ((Keyboard.Modifiers & (ModifierKeys.Control | ModifierKeys.Shift)) != 0) return;
        var item = ItemsControl.ContainerFromElement(DeviceList, e.OriginalSource as DependencyObject) as System.Windows.Controls.ListBoxItem;
        if (item is null || !item.IsSelected) return;
        item.Dispatcher.BeginInvoke(System.Windows.Threading.DispatcherPriority.Input, new Action(() => item.IsSelected = false));
    }

    /// <summary>从可视树向上找指定类型的父级（用于判定点击是否落在收藏星标上）。</summary>
    private static T? FindVisualParent<T>(DependencyObject? d) where T : DependencyObject
    {
        while (d is not null && d is not T) d = System.Windows.Media.VisualTreeHelper.GetParent(d);
        return d as T;
    }

    // ---------- 设置页（窗口内切换） ----------
    /// <summary>当前应用版本号（用于“关于 / 检查更新”）。</summary>
    private readonly string _appVersion = AppCurrentVersion();
    private static string AppCurrentVersion()
    {
        var v = System.Reflection.Assembly.GetExecutingAssembly().GetName().Version ?? new Version(1, 0, 0);
        return $"{v.Major}.{v.Minor}.{v.Build}";
    }

    // ===== 设置二级页（检查更新 / 关于 / 问题反馈）=====

    /// <summary>当前设置子页：主列表 / 检查更新 / 关于 / 反馈。</summary>
    private enum SubPage { Main, Update, About, Feedback }
    private SubPage _subPage = SubPage.Main;

    /// <summary>切到指定二级页；Main 即回到设置主列表。</summary>
    private void ShowSubPage(SubPage page)
    {
        _subPage = page;
        SettingsBackBtn.Visibility = page == SubPage.Main ? Visibility.Collapsed : Visibility.Visible;
        ResetDefaultBtn.Visibility = page == SubPage.Main ? Visibility.Visible : Visibility.Collapsed;
        SettingsMainScroll.Visibility = page == SubPage.Main ? Visibility.Visible : Visibility.Collapsed;
        UpdatePage.Visibility = page == SubPage.Update ? Visibility.Visible : Visibility.Collapsed;
        AboutPage.Visibility = page == SubPage.About ? Visibility.Visible : Visibility.Collapsed;
        FeedbackPage.Visibility = page == SubPage.Feedback ? Visibility.Visible : Visibility.Collapsed;
        // 离开反馈页时清空草稿，下次打开是全新界面
        if (page != SubPage.Feedback)
        {
            FbTitleBox.Text = "";
            FbDescBox.Text = "";
            FbStatusText.Text = "";
            FbCaptchaBorder.Visibility = Visibility.Collapsed;
            FbCaptchaHint.Visibility = Visibility.Collapsed;
        }

        SettingsSubtitleText.Text = page switch
        {
            SubPage.Update => Localization.LangManager.T("Settings.UpdateSource"),
            SubPage.About => Localization.LangManager.T("Settings.AboutAppSub"),
            SubPage.Feedback => Localization.LangManager.T("Settings.Feedback"),
            _ => Localization.LangManager.T("Settings.Subtitle")
        };
        if (page == SubPage.Update) StartUpdateCheck();
    }

    private void SettingsBack_Click(object sender, System.Windows.RoutedEventArgs e) => ShowSubPage(SubPage.Main);

    /// <summary>“检查更新”行：进入更新二级页。</summary>
    private void CheckUpdate_Click(object sender, System.Windows.Input.MouseButtonEventArgs e) => ShowSubPage(SubPage.Update);

    /// <summary>“关于应用”行：进入关于二级页。</summary>
    private void AboutRow_Click(object sender, System.Windows.Input.MouseButtonEventArgs e) => ShowSubPage(SubPage.About);

    /// <summary>“问题反馈”行：进入反馈二级页。</summary>
    private void FeedbackRow_Click(object sender, System.Windows.Input.MouseButtonEventArgs e) => ShowSubPage(SubPage.Feedback);

    private static void OpenUrl(string url)
        => Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });

    private void OpenRepo_Click(object sender, System.Windows.RoutedEventArgs e)
        => OpenUrl("https://github.com/orange-way/OrangeGO");

    // ---- 检查更新 ----
    /// <summary>三个下载源（对齐安卓端 GitCode / Gitee / GitHub）。</summary>
    private static readonly Dictionary<string, string> UpdateSources = new()
    {
        ["gitcode"] = "https://api.gitcode.com/api/v5/repos/OrangeWay/OrangeGO/releases/latest",
        ["gitee"]   = "https://gitee.com/api/v5/repos/orange-way/OrangeGO/releases/latest",
        ["github"]  = "https://api.github.com/repos/orange-way/OrangeGO/releases/latest"
    };

    private void UpdateSrc_Changed(object sender, System.Windows.RoutedEventArgs e)
    {
        if (_settings is not null && _subPage == SubPage.Update) StartUpdateCheck();
    }

    private void UpdateRecheck_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        // Hero 圆形按钮：已发现更新时是可点击的下载键（下载 APK），否则重新检查更新
        HeroSpinOnce();
        if (!string.IsNullOrEmpty(_updateApkUrl)) OpenUrl(_updateApkUrl);
        else StartUpdateCheck();
    }

    /// <summary>Hero 圆形图标点击反馈：丝滑旋转一整圈后回到原位（围绕自身中心，先快后缓）。</summary>
    private void HeroSpinOnce()
    {
        UpdateHeroSpin.Angle = 0;
        var spin = new System.Windows.Media.Animation.DoubleAnimation(0, 360, TimeSpan.FromMilliseconds(800))
        {
            EasingFunction = new System.Windows.Media.Animation.CubicEase
            {
                EasingMode = System.Windows.Media.Animation.EasingMode.EaseInOut
            }
        };
        UpdateHeroSpin.BeginAnimation(System.Windows.Media.RotateTransform.AngleProperty, spin);
    }

    /// <summary>已发现的更新 APK 直连下载地址；为空表示无更新（Hero 按钮为「检查更新」态）。</summary>
    private string _updateApkUrl = "";

    /// <summary>切换 Hero 圆形按钮：download=true → 下载图标+下载提示；false → 刷新图标+检查更新提示。</summary>
    private void SetHeroButton(bool download)
    {
        var tint = new System.Windows.Media.SolidColorBrush(System.Windows.Media.Color.FromRgb(0xFF, 0xFF, 0xFF));
        if (download)
        {
            // Material download 图标（箭头入盘）
            UpdateHeroIcon.Data = System.Windows.Media.Geometry.Parse("M19,9h-4V3H9v6H5l7,7L19,9zM5,18v2h14v-2H5z");
            UpdateHeroBtn.ToolTip = Localization.LangManager.T("Settings.DownloadLatest");
        }
        else
        {
            // Material refresh 图标
            UpdateHeroIcon.Data = System.Windows.Media.Geometry.Parse("M17.65,6.35C16.2,4.9 14.21,4 12,4c-4.42,0 -7.99,3.58 -7.99,8s3.57,8 7.99,8c3.73,0 6.84,-2.55 7.73,-6h-2.08c-0.82,2.33 -3.04,4 -5.65,4 -3.31,0 -6,-2.69 -6,-6s2.69,-6 6,-6c1.66,0 3.14,0.69 4.22,1.78L13,11h7V4l-2.35,2.35z");
            UpdateHeroBtn.ToolTip = Localization.LangManager.T("Settings.CheckUpdate");
        }
        UpdateHeroIcon.Fill = tint;
    }

    private async void StartUpdateCheck()
    {
        UpdateResultPanel.Visibility = Visibility.Collapsed;
        UpdateStatusText.Text = Localization.LangManager.T("Settings.Checking");
        UpdateOpenReleaseBtn.Tag = null;
        // 重置 Hero 圆形按钮为「检查更新」态
        _updateApkUrl = "";
        SetHeroButton(download: false);
        try
        {
            var src = (SrcGitCode.IsChecked == true ? "gitcode" : SrcGitee.IsChecked == true ? "gitee" : "github");
            var url = UpdateSources[src];
            using var http = new HttpClient();
            http.DefaultRequestHeaders.UserAgent.ParseAdd("OrangeGO-Windows");
            http.Timeout = TimeSpan.FromSeconds(12);
            var json = await http.GetStringAsync(url);
            using var doc = System.Text.Json.JsonDocument.Parse(json);
            var root = doc.RootElement;
            var tag = root.TryGetProperty("tag_name", out var t) ? t.GetString() ?? "" : "";
            var body = root.TryGetProperty("body", out var b) ? b.GetString() ?? "" : "";
            var releaseUrl = root.TryGetProperty("html_url", out var h) ? h.GetString() : "";
            var tagUrl = string.IsNullOrEmpty(tag) ? "" : $"https://github.com/orange-way/OrangeGO/releases/tag/{Uri.EscapeDataString(tag)}";

            var assets = new List<UpdateAsset>();
            if (root.TryGetProperty("assets", out var arr))
                foreach (var a in arr.EnumerateArray())
                {
                    var nm = a.TryGetProperty("name", out var n) ? n.GetString() ?? "" : "";
                    var size = a.TryGetProperty("size", out var s) ? s.GetInt64() : 0L;
                    var au = a.TryGetProperty("browser_download_url", out var u) ? u.GetString() : "";
                    if (nm.Length == 0) continue;
                    assets.Add(new UpdateAsset(nm, FormatSize(size), au));
                }

            var remote = tag.TrimStart('v');
            var newer = Version.TryParse(remote, out var rv) && Version.TryParse(_appVersion, out var cv) && rv > cv;
            if (!newer)
            {
                UpdateStatusText.Text = string.Format(Localization.LangManager.T("Settings.UpdateLatest") + "  v{0}", tag);
                return;
            }
            UpdateStatusText.Visibility = Visibility.Collapsed;
            UpdateNewVerText.Text = string.Format(Localization.LangManager.T("Settings.UpdateAvailableHead"), tag, _appVersion);
            UpdateChangelog.Text = string.IsNullOrWhiteSpace(body) ? "-" : body;
            UpdateAssetsList.ItemsSource = assets;
            UpdateResultPanel.Visibility = Visibility.Visible;
            var apk = assets.FirstOrDefault(a => a.Name.EndsWith(".apk", StringComparison.OrdinalIgnoreCase));
            if (apk is not null && !string.IsNullOrEmpty(apk.Url))
            {
                _updateApkUrl = apk.Url;              // 已发现更新 → 圆形按钮变下载键
                SetHeroButton(download: true);
            }
            UpdateOpenReleaseBtn.Tag = !string.IsNullOrEmpty(releaseUrl) ? releaseUrl : (tagUrl.Length > 0 ? tagUrl : null);
        }
        catch
        {
            UpdateResultPanel.Visibility = Visibility.Collapsed;
            UpdateStatusText.Visibility = Visibility.Visible;
            UpdateStatusText.Text = Localization.LangManager.T("Settings.UpdateError");
        }
    }

    private sealed class UpdateAsset
    {
        public UpdateAsset(string name, string size, string url) { Name = name; Size = size; Url = url; }
        public string Name { get; }
        public string Size { get; }
        public string Url { get; }
    }

    private static string FormatSize(long bytes)
    {
        if (bytes <= 0) return "";
        string[] unit = { "B", "KB", "MB", "GB" };
        double v = bytes; int i = 0;
        while (v >= 1024 && i < unit.Length - 1) { v /= 1024; i++; }
        return $"{v:0.#} {unit[i]}";
    }

    private void AssetRow_Click(object sender, System.Windows.Input.MouseButtonEventArgs e)
    {
        if ((sender as FrameworkElement)?.Tag is UpdateAsset a && !string.IsNullOrEmpty(a.Url)) OpenUrl(a.Url);
    }

    private void UpdateOpenRelease_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        if (UpdateOpenReleaseBtn.Tag is string url && url.Length > 0) OpenUrl(url);
    }

    // ---- 问题反馈（内嵌 hCaptcha，不弹窗） ----
    private const string FbSiteKey = "b20c5714-7cda-4964-9aa0-b26ce4b21a82";
    private const string FbProxyUrl = "https://orangego.orangeway.workers.dev/feedback";
    private bool _fbInited;
    private bool _fbSubmitting;
    private string _fbDesc;

    /// <summary>“提交反馈”：校验标题后，在页内显示 hCaptcha 验证，验证通过即自动上传。</summary>
    private void FbSubmit_Click(object sender, System.Windows.RoutedEventArgs e)
    {
        if (_fbSubmitting) return;
        var title = FbTitleBox.Text.Trim();
        if (title.Length == 0) { FbStatusText.Text = Localization.LangManager.T("Feedback.AskTitle"); return; }
        _fbDesc = FbDescBox.Text.Trim();
        FbCaptchaHint.Visibility = Visibility.Visible;
        FbCaptchaBorder.Visibility = Visibility.Visible;

        _ = InitFeedbackCaptchaAsync();
    }

    private async Task InitFeedbackCaptchaAsync()
    {
        if (_fbInited)
        {
            try { FbCaptchaWv.Reload(); } catch { /* 无需处理 */ }
            return;
        }
        _fbInited = true;
        try
        {
            await FbCaptchaWv.EnsureCoreWebView2Async();
            FbCaptchaWv.CoreWebView2.Settings.AreDefaultContextMenusEnabled = false;
            FbCaptchaWv.CoreWebView2.Settings.IsStatusBarEnabled = false;
            FbCaptchaWv.CoreWebView2.Settings.AreDevToolsEnabled = false;
            FbCaptchaWv.CoreWebView2.WebMessageReceived += FbCaptcha_WebMessageReceived;
            var dark = (_settings?.ThemeMode switch { "dark" => true, "light" => false, _ => IsSystemDark() });
            // HTML 从本地加载（不访问 workers.dev），但 origin 设为 Worker 域名（hCaptcha 后台已配置该 hostname）。
            // 这样 WebView2 不需要访问 workers.dev 来获取 HTML，只有 hCaptcha JS SDK 从 hcaptcha.com 加载。
            var tempDir = System.IO.Path.Combine(System.IO.Path.GetTempPath(), "OrangeGO_captcha");
            System.IO.Directory.CreateDirectory(tempDir);
            System.IO.File.WriteAllText(System.IO.Path.Combine(tempDir, "captcha.html"), BuildCaptchaHtml(dark));
            FbCaptchaWv.CoreWebView2.SetVirtualHostNameToFolderMapping(
                "orangego.orangeway.workers.dev", tempDir, Microsoft.Web.WebView2.Core.CoreWebView2HostResourceAccessKind.Allow);
            FbCaptchaWv.CoreWebView2.Navigate("https://orangego.orangeway.workers.dev/captcha.html");
        }
        catch (Exception ex)
        {
            FbStatusText.Text = Localization.LangManager.T("Feedback.CaptchaFail") + ": " + ex.Message;
        }
    }

    private string BuildCaptchaHtml(bool dark) =>
        @"<!DOCTYPE html><html><head><meta charset='utf-8'>
<script src='https://hcaptcha.com/1/api.js?onload=onCb&render=explicit' async defer></script>
</head><body style='margin:0;padding:16px;display:flex;justify-content:center;align-items:flex-end;background:{BG};overflow:hidden;height:100vh;box-sizing:border-box;'>
<div id='cap'></div>
<script>
function onCb(){hcaptcha.render('cap',{sitekey:'{SITEKEY}',theme:'{THEME}',
callback:function(t){window.chrome.webview.postMessage('challenge-close');window.chrome.webview.postMessage(t);},
'open-callback':function(){window.chrome.webview.postMessage('challenge-open');},
'close-callback':function(){window.chrome.webview.postMessage('challenge-close');}});}
function getNewToken(){try{hcaptcha.execute('cap',{async:true}).then(function(t){window.chrome.webview.postMessage(t);});}catch(e){window.chrome.webview.postMessage('captcha-error');}}
</script></body></html>"
            .Replace("{SITEKEY}", FbSiteKey)
            .Replace("{THEME}", dark ? "dark" : "light")
            .Replace("{BG}", dark ? "#1f1f1f" : "#ffffff");

    private void FbCaptcha_WebMessageReceived(object? sender, Microsoft.Web.WebView2.Core.CoreWebView2WebMessageReceivedEventArgs e)
    {
        var msg = e.TryGetWebMessageAsString();
        if (string.IsNullOrEmpty(msg)) return;
        // hCaptcha 图片挑战展开/关闭：checkbox 在页面底部，挑战向上展开；WebView2 向上扩展（负 top margin），不推动布局，ZIndex 浮最上层
        if (msg == "challenge-open")
        {
            FbCaptchaWv.Height = 420;
            FbCaptchaBorder.Margin = new System.Windows.Thickness(0, -310, 0, 0);
            System.Windows.Controls.Panel.SetZIndex(FbCaptchaBorder, 100);
            FbCaptchaBorder.Effect = new System.Windows.Media.Effects.DropShadowEffect
            { BlurRadius = 16, ShadowDepth = 4, Opacity = 0.35, Color = System.Windows.Media.Colors.Black };
            return;
        }
        if (msg == "challenge-close")
        {
            FbCaptchaWv.Height = 110;
            FbCaptchaBorder.Margin = new System.Windows.Thickness(0);
            System.Windows.Controls.Panel.SetZIndex(FbCaptchaBorder, 0);
            FbCaptchaBorder.Effect = null;
            return;
        }
        // 其余消息视为验证 token
        if (_fbSubmitting) return;
        _ = SubmitFeedbackAsync(msg);
    }

    /// <summary>与安卓端一致：POST 到自建 Worker（GitHub Issues + 微信推送）。</summary>
    private async Task SubmitFeedbackAsync(string token)
    {
        if (_fbSubmitting) return;
        _fbSubmitting = true;
        FbSubmitBtn.IsEnabled = false;
        FbStatusText.Text = Localization.LangManager.T("Feedback.Sending");
        try
        {
            var title = FbTitleBox.Text.Trim();
            var auto = $"----\n{Localization.LangManager.T("Feedback.Group")}: {System.Environment.GetEnvironmentVariable("COMPUTERNAME")}\n" +
                       $"OS: {GetOsVersion()}\n" +
                       $"CPU: {GetCpuName()}\n" +
                       $"RAM: {GetTotalRam()}\n" +
                       $"GPU: {GetGpuInfo()}\n" +
                       $"Screen: {GetScreenResolution()}\n" +
                       $"App: {_appVersion}\n" +
                       $"Runtime: .NET {System.Environment.Version}\n" +
                       $"Theme: {(_settings?.ThemeMode switch { "dark" => "深色", "light" => "浅色", _ => $"跟随系统（当前：{(IsSystemDark() ? "深色" : "浅色")}）" })}";
            var body = (_fbDesc.Length > 0 ? _fbDesc + "\n\n" : "") + auto;
            var payload = new { title, body, hcaptcha_token = token };
            var json = System.Text.Json.JsonSerializer.Serialize(payload);
            using var http = new HttpClient();
            http.DefaultRequestHeaders.UserAgent.ParseAdd("OrangeGO-Windows");
            http.Timeout = TimeSpan.FromSeconds(15);
            using var content = new StringContent(json, System.Text.Encoding.UTF8, "application/json");
            using var resp = await http.PostAsync(FbProxyUrl, content);
            if ((int)resp.StatusCode is not (>= 200 and < 300))
            {
                var errBody = await resp.Content.ReadAsStringAsync();
                string detail = "";
                try { using var ed = System.Text.Json.JsonDocument.Parse(errBody); detail = ed.RootElement.TryGetProperty("detail", out var d) ? d.GetString() ?? "" : ""; } catch { /* 非 JSON */ }
                FbStatusText.Text = string.Format(Localization.LangManager.T("Feedback.Fail"), $"HTTP {(int)resp.StatusCode}" + (string.IsNullOrEmpty(detail) ? "" : $"：{detail}"));
                FbSubmitBtn.IsEnabled = true; // 失败可重试
                _ = RetryCaptchaTokenAsync(); // 无感获取新 token，用户不需再次人机验证
                return;
            }
            var text = await resp.Content.ReadAsStringAsync();
            using var doc = System.Text.Json.JsonDocument.Parse(text);
            var root = doc.RootElement;
            // 显示各通道详细状态（WxPusher 失败时也能看到原因）
            var ghOk = root.TryGetProperty("github", out var gh) && gh.TryGetProperty("ok", out var ghk) && ghk.GetBoolean();
            var wpOk = root.TryGetProperty("wxpusher", out var wp) && wp.TryGetProperty("ok", out var wpk) && wpk.GetBoolean();
            var wpDetail = wp.TryGetProperty("detail", out var wpd) ? wpd.GetString() : "";
            FbStatusText.Text = Localization.LangManager.T("Feedback.Success") +
                (wpOk ? "" : $"（WxPusher: {wpDetail}）");
            FbCaptchaBorder.Visibility = Visibility.Collapsed;
            FbCaptchaHint.Visibility = Visibility.Collapsed;
            FbSubmitBtn.IsEnabled = false; // 成功后不可再次提交
            // 倒计时自动返回首页
            _ = FeedbackCountdownAsync();
        }
        catch (Exception ex)
        {
            FbStatusText.Text = string.Format(Localization.LangManager.T("Feedback.Fail"), ex.Message);
            FbSubmitBtn.IsEnabled = true; // 失败可重试
            _ = RetryCaptchaTokenAsync();
        }
        finally
        {
            _fbSubmitting = false;
        }
    }

    /// <summary>提交失败后无感获取新 hCaptcha token（不需用户再次点击 checkbox），新 token 通过 postMessage 回传自动重试提交。</summary>
    private async Task RetryCaptchaTokenAsync()
    {
        try
        {
            await Task.Delay(500); // 等错误提示显示
            if (FbCaptchaWv.CoreWebView2 != null)
                await FbCaptchaWv.CoreWebView2.ExecuteScriptAsync("getNewToken()");
        }
        catch { /* WebView 未就绪，用户可手动重新提交 */ }
    }

    private async Task FeedbackCountdownAsync()
    {
        for (int i = 3; i > 0; i--)
        {
            FbStatusText.Text = $"{Localization.LangManager.T("Feedback.Success")} {i}s";
            await Task.Delay(1000);
        }
        ShowSubPage(SubPage.Main);
    }

    // ---------- 系统信息采集（注册表 + P/Invoke，无需额外 NuGet） ----------

    private static string GetCpuName()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"HARDWARE\DESCRIPTION\System\CentralProcessor\0");
            return key?.GetValue("ProcessorNameString")?.ToString()?.Trim() ?? "Unknown";
        }
        catch { return "Unknown"; }
    }

    private static string GetTotalRam()
    {
        try
        {
            var ms = new MEMORYSTATUSEX { dwLength = (uint)System.Runtime.InteropServices.Marshal.SizeOf<MEMORYSTATUSEX>() };
            if (GlobalMemoryStatusEx(ref ms))
            {
                var gb = ms.ullTotalPhys / (1024.0 * 1024 * 1024);
                return $"{gb:F1} GB";
            }
        }
        catch { }
        return "Unknown";
    }

    private static string GetOsVersion()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows NT\CurrentVersion");
            var display = key?.GetValue("DisplayVersion")?.ToString();
            var buildStr = key?.GetValue("CurrentBuild")?.ToString();
            var arch = System.Runtime.InteropServices.RuntimeInformation.OSArchitecture;
            int.TryParse(buildStr, out int build);
            var major = build >= 22000 ? 11 : 10;
            return $"Windows {major} {display} (Build {buildStr}, {arch})";
        }
        catch { return $"Windows {System.Environment.OSVersion.Version}"; }
    }

    private static string GetGpuInfo()
    {
        try
        {
            using var cls = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SYSTEM\CurrentControlSet\Control\Class\{4d36e968-e325-11ce-bfc1-08002be10318}");
            if (cls == null) return "Unknown";
            var gpus = new System.Collections.Generic.List<string>();
            foreach (var sub in cls.GetSubKeyNames())
            {
                using var key = cls.OpenSubKey(sub);
                if (key == null) continue;
                var name = key.GetValue("DriverDesc")?.ToString()?.Trim();
                if (string.IsNullOrEmpty(name)) continue;
                if (name.Contains("Basic Render") || name.Contains("Microsoft Display")) continue;
                var memObj = key.GetValue("HardwareInformation.qwMemorySize");
                string vram = "";
                if (memObj is long memLong && memLong > 0)
                    vram = $" ({memLong / (1024.0 * 1024 * 1024):F1} GB)";
                else if (memObj is int memInt && memInt > 0)
                    vram = $" ({memInt / (1024.0 * 1024 * 1024):F1} GB)";
                gpus.Add(name + vram);
            }
            return gpus.Count > 0 ? string.Join(", ", gpus) : "Unknown";
        }
        catch { return "Unknown"; }
    }

    private static string GetScreenResolution()
    {
        try
        {
            var w = (int)SystemParameters.PrimaryScreenWidth;
            var h = (int)SystemParameters.PrimaryScreenHeight;
            var scale = PresentationSource.FromVisual(Application.Current.MainWindow)?.CompositionTarget?.TransformFromDevice.M11 ?? 1.0;
            var physW = (int)(w / scale);
            var physH = (int)(h / scale);
            return $"{w}x{h}" + (Math.Abs(scale - 1.0) > 0.01 ? $" (物理 {physW}x{physH}, 缩放 {Math.Round(1/scale*100)}%)" : "");
        }
        catch { return "Unknown"; }
    }

    [System.Runtime.InteropServices.DllImport("kernel32.dll")]
    [return: System.Runtime.InteropServices.MarshalAs(System.Runtime.InteropServices.UnmanagedType.Bool)]
    private static extern bool GlobalMemoryStatusEx(ref MEMORYSTATUSEX lpBuffer);

    [System.Runtime.InteropServices.StructLayout(System.Runtime.InteropServices.LayoutKind.Sequential)]
    private struct MEMORYSTATUSEX
    {
        public uint dwLength;
        public uint dwMemoryLoad;
        public ulong ullTotalPhys;
        public ulong ullAvailPhys;
        public ulong ullTotalPageFile;
        public ulong ullAvailPageFile;
        public ulong ullTotalVirtual;
        public ulong ullAvailVirtual;
        public ulong ullAvailExtendedVirtual;
    }

    private static bool IsSystemDark()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.CurrentUser.OpenSubKey(@"Software\Microsoft\Windows\CurrentVersion\Themes\Personalize");
            return key?.GetValue("AppsUseLightTheme") is int v && v == 0;
        }
        catch { return false; }
    }

    private void OpenPrivacy_Click(object sender, System.Windows.RoutedEventArgs e)
        => OpenUrl("https://github.com/orange-way/OrangeGO");

    /// <summary>把当前持久化设置写入设置页控件（含首次构造）。</summary>
    private void LoadSettingsUi()
    {
        _suppressSettings = true;
        NameBox.Text = _settings.DeviceName;
        DirBox.Text = _settings.SaveDir;
        UpdateVerText.Text = "v" + _appVersion;
        UpdateCurVerText.Text = "v" + _appVersion;
        AboutVersionText.Text = "v" + _appVersion;
        AboutSystemText.Text = $"Windows {System.Environment.OSVersion.Version} ({System.Runtime.InteropServices.RuntimeInformation.OSArchitecture})";
        AboutRuntimeText.Text = $".NET {System.Environment.Version}";
        FeedbackTipText.Text = Localization.LangManager.T("Feedback.Tip");
        (_settings.ThemeMode switch
        {
            "light" => ThemeLight,
            "dark" => ThemeDark,
            _ => ThemeFollow
        }).IsChecked = true;
        MinimizeTrayBox.IsChecked = _settings.MinimizeToTray;
        AutoStartBox.IsChecked = _settings.AutoStart;
        AutoSaveBox.IsChecked = _settings.AutoSave;
        AutoSaveWhitelistBox.IsChecked = _settings.AutoSaveWhitelist;
        RequirePinBox.IsChecked = _settings.RequirePin;
        PinBox.Text = _settings.PinCode;
        AutoCompleteBox.IsChecked = _settings.AutoComplete;
        SaveHistoryBox.IsChecked = _settings.SaveHistory;
        IntegrateImagesBox.IsChecked = _settings.IntegrateImages;
        KeepWindowBox.IsChecked = _settings.KeepWindowDuringScreenshot;

        // 语言下拉：跟随系统 + 15 种常用语言（母语自名，按英文名国际标准排序）
        RebuildLanguageList();
        // 传输记录自动清除下拉：不清理 / 清除 N 天/月/年前
        RebuildAutoCleanupList();
        RefreshThumbCacheInfo();
        _suppressSettings = false;
    }

    /// <summary>刷新设置页「缩略图缓存」大小显示（MB）。</summary>
    private void RefreshThumbCacheInfo()
        => ThumbCacheSizeText.Text = $"{Core.ThumbCache.TotalSizeBytes() / (1024.0 * 1024.0):0.0} MB";

    /// <summary>设置「清除缩略图缓存」点击：弹自定义确认浮层，确认后清空缩略图缓存并刷新大小。</summary>
    private void ClearThumbCache_Click(object sender, RoutedEventArgs e)
    {
        ShowDialog(
            Localization.LangManager.T("Settings.ClearThumbCache"),
            Localization.LangManager.T("Settings.ClearThumbCacheConfirm"),
            DialogKind.Danger,
            onOk: () =>
            {
                Core.ThumbCache.ClearAll();
                RefreshThumbCacheInfo();
            });
    }

    /// <summary>重建「自动清除传输记录」下拉，并选中当前保存的天数档位。</summary>
    private void RebuildAutoCleanupList()
    {
        var cur = _settings.AutoCleanupDays;
        AutoCleanupBox.Items.Clear();
        void Add(int days, string label) =>
            AutoCleanupBox.Items.Add(new System.Windows.Controls.ComboBoxItem { Tag = days, Content = label });
        Add(0, Localization.LangManager.T("AutoCleanup.Never"));
        Add(1, string.Format(Localization.LangManager.T("AutoCleanup.Day"), 1));
        Add(7, string.Format(Localization.LangManager.T("AutoCleanup.Day"), 7));
        Add(30, string.Format(Localization.LangManager.T("AutoCleanup.Day"), 30));
        Add(90, string.Format(Localization.LangManager.T("AutoCleanup.Month"), 3));
        Add(180, string.Format(Localization.LangManager.T("AutoCleanup.Month"), 6));
        Add(365, string.Format(Localization.LangManager.T("AutoCleanup.Year"), 1));
        for (var i = 0; i < AutoCleanupBox.Items.Count; i++)
        {
            if (AutoCleanupBox.Items[i] is ComboBoxItem { Tag: int d } && d == cur) { AutoCleanupBox.SelectedIndex = i; return; }
        }
        AutoCleanupBox.SelectedIndex = 0;
    }

    /// <summary>读取自动清除下拉的档位（天）。</summary>
    private int SelectedCleanupDays() =>
        (AutoCleanupBox.SelectedItem as ComboBoxItem)?.Tag is int d ? d : 0;

    /// <summary>下拉切换：改动即保存并立即执行一次清理。</summary>
    private void AutoCleanup_Changed(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        if (_suppressSettings || _settings is null) return;
        PersistSettings();
    }

    /// <summary>删除超过 N 天的历史记录（进行中的收发跳过）；days<=0 不动作。</summary>
    private void ApplyCleanup(int days)
    {
        if (days <= 0) return;
        var cut = DateTimeOffset.UtcNow.AddDays(-days).ToUnixTimeMilliseconds();
        var toRemove = _vm.Transfers
            .Where(t => t.Timestamp > 0 && t.Timestamp < cut && !_activeCts.ContainsKey(t) && !_recvItems.ContainsValue(t))
            .ToList();
        foreach (var t in toRemove)
        {
            Core.ThumbCache.Delete(t.AllThumbKeys()); // 自动清理删记录同步回收其（含子项）缩略图缓存
            _vm.Transfers.Remove(t);
        }
    }

    /// <summary>重建语言下拉（跟随系统 + 15 种语言）。"跟随系统"文案取自当前生前景的语言，随界面语言即时切换。</summary>
    private void RebuildLanguageList()
    {
        var current = SelectedComboTag(LangBox, _settings.Language);
        LangBox.Items.Clear();
        LangBox.Items.Add(new System.Windows.Controls.ComboBoxItem { Tag = "follow", Content = Localization.LangManager.T("Lang.Follow") });
        foreach (var (code, native) in Localization.LangManager.Menu)
            LangBox.Items.Add(new System.Windows.Controls.ComboBoxItem { Content = native, Tag = code });
        SelectComboByTag(LangBox, current);
    }

    /// <summary>按 ComboBoxItem.Tag 选中下拉项；找不到则保持默认第一项。</summary>
    private static void SelectComboByTag(System.Windows.Controls.ComboBox box, string tag)
    {
        for (var i = 0; i < box.Items.Count; i++)
        {
            if (box.Items[i] is ComboBoxItem { Tag: string t } && t == tag)
            {
                box.SelectedIndex = i;
                return;
            }
        }
    }

    /// <summary>读取当前选中下拉项的 Tag，无选中项时回退默认值。</summary>
    private static string SelectedComboTag(System.Windows.Controls.ComboBox box, string fallback)
        => (box.SelectedItem as ComboBoxItem)?.Tag as string ?? fallback;

    private void BrowseDir_Click(object sender, RoutedEventArgs e)
    {
        using var dlg = new System.Windows.Forms.FolderBrowserDialog
        {
            Description = Localization.LangManager.T("Settings.DirPickTitle"),
            SelectedPath = Directory.Exists(DirBox.Text) ? DirBox.Text : ""
        };
        if (dlg.ShowDialog() == System.Windows.Forms.DialogResult.OK)
            DirBox.Text = dlg.SelectedPath;
    }

    /// <summary>开关类（CheckBox 点击）：所有设置均为实时生效。</summary>
    private void SettingsToggle_Click(object sender, RoutedEventArgs e)
        => PersistSettings();

    /// <summary>主题单选切换：实时生效。</summary>
    private void Theme_Changed(object sender, RoutedEventArgs e)
    {
        // XAML 构造阶段 ThemeFollow 的 IsChecked=True 也会触发本回调，此时 _settings 尚未就绪，须跳过
        if (_settings is null || _suppressSettings) return;
        PersistSettings();
    }

    /// <summary>语言下拉选择：实时生效（DynamicResource 立即跟随）；随后重建菜单使"跟随系统"文案切换为新语言。</summary>
    private void LangBox_SelectionChanged(object sender, RoutedEventArgs e)
    {
        if (_settings is null || _suppressSettings) return;
        PersistSettings();          // 内部会 LangManager.Apply 应用新语言
        _suppressSettings = true;   // 重建/改选过程不再触发本级回调
        try { RebuildLanguageList(); }
        finally { _suppressSettings = false; }
    }

    /// <summary>文本输入框（设备别名/收件目录/PIN）失焦时提交：回车或点别处即生效。</summary>
    private void SettingsTextBox_LostFocus(object sender, RoutedEventArgs e)
        => PersistSettings();

    /// <summary>把当前设置页的 UI 值即时写入并保存、应用（无“保存”按钮，改动即生效）。</summary>
    private void PersistSettings()
    {
        _settings.DeviceName = string.IsNullOrWhiteSpace(NameBox.Text) ? _settings.DeviceName : NameBox.Text.Trim();
        _settings.SaveDir = string.IsNullOrWhiteSpace(DirBox.Text) ? _settings.SaveDir : DirBox.Text.Trim();
        _settings.ThemeMode = ThemeLight.IsChecked == true ? "light"
            : ThemeDark.IsChecked == true ? "dark" : "follow";
        _settings.Language = SelectedComboTag(LangBox, _settings.Language);
        _settings.MinimizeToTray = MinimizeTrayBox.IsChecked == true;
        _settings.AutoStart = AutoStartBox.IsChecked == true;
        _settings.AutoSave = AutoSaveBox.IsChecked == true;
        _settings.AutoSaveWhitelist = AutoSaveWhitelistBox.IsChecked == true;
        _settings.RequirePin = RequirePinBox.IsChecked == true;
        _settings.PinCode = PinBox.Text.Trim();
        _settings.AutoComplete = AutoCompleteBox.IsChecked == true;
        _settings.SaveHistory = SaveHistoryBox.IsChecked == true;
        _settings.IntegrateImages = IntegrateImagesBox.IsChecked == true;
        var prevCleanup = _settings.AutoCleanupDays;
        _settings.AutoCleanupDays = SelectedCleanupDays();
        _settings.KeepWindowDuringScreenshot = KeepWindowBox.IsChecked == true;

        _settings.Save();
        ApplySettings();
        Theme.ThemeManager.SetMode(Application.Current, _settings.ThemeMode);
        SetAutoStart(_settings.AutoStart);
        // 语言选择立即生效（follow → 跟随系统；未命中回退英文）
        Localization.LangManager.Apply(Application.Current, _settings.Language);
        // 图片集成显示变化 → 刷新传输记录里集成网格的可见性（无需重启）
        foreach (var t in _vm.Transfers) t.RefreshIntegrateImages();
        // 自动清除档位有变化 → 立即清理一次
        if (_settings.AutoCleanupDays != prevCleanup) ApplyCleanup(_settings.AutoCleanupDays);
    }

    /// <summary>离开设置页时：恢复进入设置前的主页导航（仅当用户没有通过点导航直接退出时）；
    /// 启用 PIN 但未设置 → 自动关闭，逻辑同安卓端（空 PIN 视为未启用）。</summary>
    private void Gear_Unchecked(object sender, RoutedEventArgs e)
    {
        // 设置键二段式：位于任一二级界面（检查更新/关于/问题反馈）时，点击齿轮=返回设置主界面，不退出设置
        if (_subPage != SubPage.Main)
        {
            ShowSubPage(SubPage.Main);
            GearButton.IsChecked = true; // 保持设置打开高亮，仅回到主设置列表
            return;
        }
        // 若此时两个主页都未选中（即用户只是再点了一次设置键退出）→ 恢复进入设置前的主页，避免空白
        if (NavTransfer.IsChecked == false && NavDevices.IsChecked == false)
        {
            if (_navBeforeSettings == "Devices") NavDevices.IsChecked = true;
            else NavTransfer.IsChecked = true;
        }
        if (_settings is null) return;
        if (_settings.RequirePin && string.IsNullOrEmpty(_settings.PinCode))
        {
            _settings.RequirePin = false;
            _settings.Save();
            RequirePinBox.IsChecked = false;
        }
    }

    /// <summary>恢复默认：按默认值填充界面并即时保存、应用（实时生效，不退出设置页）。</summary>
    private void ResetDefault_Click(object sender, RoutedEventArgs e)
    {
        _suppressSettings = true;   // 填充过程不触发主题/语言的实时持久化
        var def = Core.AppSettings.Defaults();
        NameBox.Text = def.DeviceName;
        DirBox.Text = def.SaveDir;
        (def.ThemeMode switch { "light" => ThemeLight, "dark" => ThemeDark, _ => ThemeFollow }).IsChecked = true;
        MinimizeTrayBox.IsChecked = def.MinimizeToTray;
        AutoStartBox.IsChecked = def.AutoStart;
        AutoSaveBox.IsChecked = def.AutoSave;
        AutoSaveWhitelistBox.IsChecked = def.AutoSaveWhitelist;
        RequirePinBox.IsChecked = def.RequirePin;
        PinBox.Text = def.PinCode;
        AutoCompleteBox.IsChecked = def.AutoComplete;
        SaveHistoryBox.IsChecked = def.SaveHistory;
        IntegrateImagesBox.IsChecked = def.IntegrateImages;
        KeepWindowBox.IsChecked = def.KeepWindowDuringScreenshot;
        SelectComboByTag(LangBox, def.Language);
        for (var i = 0; i < AutoCleanupBox.Items.Count; i++)
        {
            if (AutoCleanupBox.Items[i] is ComboBoxItem { Tag: int d } && d == def.AutoCleanupDays) { AutoCleanupBox.SelectedIndex = i; break; }
        }
        _suppressSettings = false;
        PersistSettings();
    }

    /// <summary>写入/删除当前用户的「开机自启动」注册表项。</summary>
    private static void SetAutoStart(bool enable)
    {
        const string valueName = "OrangeGO";
        try
        {
            using var key = Microsoft.Win32.Registry.CurrentUser.CreateSubKey(@"Software\Microsoft\Windows\CurrentVersion\Run");
            if (enable)
            {
                var exe = Environment.ProcessPath ?? Process.GetCurrentProcess().MainModule?.FileName;
                if (!string.IsNullOrEmpty(exe)) key.SetValue(valueName, $"\"{exe}\"");
            }
            else key.DeleteValue(valueName, false);
        }
        catch { /* 自启设置失败不阻塞 */ }
    }

    /// <summary>设置保存后主窗口应用新值。</summary>
    private void ApplySettings()
    {
        if (_saveDir != _settings.SaveDir)
        {
            _saveDir = _settings.SaveDir;
            Directory.CreateDirectory(_saveDir);
            _vm.ReceiveDirHint = string.Format(Localization.LangManager.T("Dir.ReceiveHint"), _saveDir);
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
        // 同启动时策略：不订阅 mDNS 即时下线（见 OnMdnsChanged 上方注释），避免误移除造成闪跳
        old.Dispose();
    }

    /// <summary>让系统给窗口四角绘制圆角（DWMWCP_ROUND），并确保 DWM 过渡动画启用。</summary>
    private static void ApplyCornerPreference(IntPtr hwnd)
    {
        try
        {
            const int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
            const int DWMWCP_ROUND = 2;
            var pref = DWMWCP_ROUND;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, ref pref, sizeof(int));
            // 强制启用 DWM 过渡动画（最小化/最大化/还原），WindowStyle=None 下 DWM 可能默认禁用
            const int DWMWA_TRANSITIONS_FORCEDISABLED = 3;
            var disabled = 0;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_TRANSITIONS_FORCEDISABLED, ref disabled, sizeof(int));
        }
        catch { /* 老系统不支持则保持直角 */ }
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern short GetAsyncKeyState(int vKey);

    // ================= 最大化限制到工作区（WindowStyle=None 时防止盖住任务栏） =================

    private const int WM_GETMINMAXINFO = 0x0024;
    private const uint MONITOR_DEFAULTTONEAREST = 2;

    /// <summary>最大化时把窗口上/左边界与尺寸限制在当前显示器工作区，避免底部被任务栏遮住。</summary>
    private static IntPtr WindowProc(IntPtr hwnd, int msg, IntPtr wParam, IntPtr lParam, ref bool handled)
    {
        if (msg == WM_GETMINMAXINFO)
        {
            var mmi = Marshal.PtrToStructure<MINMAXINFO>(lParam);
            var monitor = MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST);
            if (monitor != IntPtr.Zero)
            {
                var mi = new MONITORINFO { cbSize = Marshal.SizeOf<MONITORINFO>() };
                if (GetMonitorInfo(monitor, ref mi))
                {
                    var wa = mi.rcWork;
                    int w = wa.Right - wa.Left;
                    int h = wa.Bottom - wa.Top;
                    mmi.ptMaxPosition.X = wa.Left;
                    mmi.ptMaxPosition.Y = wa.Top;
                    mmi.ptMaxSize.X = w;
                    mmi.ptMaxSize.Y = h;
                    mmi.ptMaxTrackSize.X = w;
                    mmi.ptMaxTrackSize.Y = h;
                    Marshal.StructureToPtr(mmi, lParam, false);
                }
            }
            handled = true;
        }
        return IntPtr.Zero;
    }

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern IntPtr MonitorFromWindow(IntPtr hwnd, uint dwFlags);

    [System.Runtime.InteropServices.DllImport("user32.dll")]
    private static extern bool GetMonitorInfo(IntPtr hMonitor, ref MONITORINFO lpmi);

    [StructLayout(LayoutKind.Sequential)]
    private struct MINMAXINFO
    {
        public POINT ptReserved;
        public POINT ptMaxSize;
        public POINT ptMaxPosition;
        public POINT ptMinTrackSize;
        public POINT ptMaxTrackSize;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct POINT
    {
        public int X;
        public int Y;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct RECT
    {
        public int Left;
        public int Top;
        public int Right;
        public int Bottom;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MONITORINFO
    {
        public int cbSize;
        public RECT rcMonitor;
        public RECT rcWork;
        public uint dwFlags;
    }
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
