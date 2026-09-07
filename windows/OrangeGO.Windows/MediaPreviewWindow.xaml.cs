using System;
using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Media.Imaging;
using System.Windows.Navigation;
using System.Windows.Shell;
using OrangeGO.Windows.Core;
using OrangeGO.Windows.Localization;
using LibVLCSharp.Shared;
using LibVLCSharp.WPF;
using MediaPlayer = LibVLCSharp.Shared.MediaPlayer;
// 项目启用了 WinForms 全局 using，以下类型需固定为 WPF 语义
using Point = System.Windows.Point;
using MouseEventArgs = System.Windows.Input.MouseEventArgs;
using MouseButtonEventArgs = System.Windows.Input.MouseButtonEventArgs;
using KeyEventArgs = System.Windows.Input.KeyEventArgs;
using HorizontalAlignment = System.Windows.HorizontalAlignment;
using VerticalAlignment = System.Windows.VerticalAlignment;
using Stretch = System.Windows.Media.Stretch;

namespace OrangeGO.Windows;

/// <summary>媒体预览窗口：图片（滚轮缩放+拖拽平移+双击复位）、视频/音频（播放/暂停/进度条/音量）。样式与应用主题统一。</summary>
public sealed partial class MediaPreviewWindow : Window
{
    private readonly string _path;
    private LibVLC? _libVLC;
    private MediaPlayer? _mp;
    private VideoView? _videoView;
    private bool _isVideo, _isAudio, _isImage;
    private bool _playing;             // 当前是否在播放
    private double _videoW, _videoH;   // 视频原始尺寸（Playing 事件后取）

    private readonly System.Windows.Threading.DispatcherTimer _ticker;

    public MediaPreviewWindow(string path)
    {
        _path = path;
        InitializeComponent();
        TitleText.Text = Path.GetFileName(path);
        AudioName.Text = Path.GetFileName(path);

        _ticker = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(250) };
        _ticker.Tick += (_, _) => RefreshUi();

        // 独立预览窗口：显示在任务栏（与主窗口并存），沿用应用图标；调用方以 Show() 非模态打开
        ShowInTaskbar = true;
        try { Icon = new BitmapImage(new Uri("pack://application:,,,/Assets/logo.png")); } catch { /* 图标缺失忽略 */ }

        // 与主窗口/设置窗口一致：系统级圆角 + 阴影（DWM）
        SourceInitialized += (_, _) =>
            ApplyCornerPreference(new System.Windows.Interop.WindowInteropHelper(this).Handle);

        StateChanged += (_, _) => UpdateMaxIcon();

        // 音量胶囊：鼠标移入音量区（图标+胶囊，透明背景统一命中区）淡入，移出淡出
        VolumeZone.MouseEnter += (_, _) => ShowVol(true);
        VolumeZone.MouseLeave += (_, _) => ShowVol(false);

        Loaded += (_, _) => Setup();
        Closed += (_, _) => { _ticker.Stop(); _mp?.Stop(); _mp?.Dispose(); _videoView?.Dispose(); };
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
            const int DWMWA_TRANSITIONS_FORCEDISABLED = 3;
            var disabled = 0;
            _ = DwmSetWindowAttribute(hwnd, DWMWA_TRANSITIONS_FORCEDISABLED, ref disabled, sizeof(int));
        }
        catch { /* 老系统不支持则保持直角 */ }
    }

    [System.Runtime.InteropServices.DllImport("dwmapi.dll")]
    private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int attrValue, int attrSize);

    private void Setup()
    {
        _isVideo = TransferItem.IsVideoExt(_path);
        _isAudio = TransferItem.IsAudioExt(_path);
        _isImage = TransferItem.IsImageExt(_path);

        ImageHost.Visibility = _isImage ? Visibility.Visible : Visibility.Collapsed;
        MediaBar.Visibility = (_isVideo || _isAudio) ? Visibility.Visible : Visibility.Collapsed;

        if (_isImage) { LoadImage(); return; }

        // LibVLC 播放器：支持 MOV 等 WPF MediaElement 无法 seek 的容器
        // native libs 由 VideoLAN.LibVLC.Windows 包部署到 libvlc/win-x64/，LibVLCSharp 自动发现
        _libVLC = new LibVLC();
        _mp = new MediaPlayer(_libVLC);
        _mp.LengthChanged += (_, e) => Dispatcher.Invoke(() =>
        {
            var totalSec = e.Length / 1000.0;
            if (totalSec > 0) SeekBar.Maximum = totalSec;

        });
        _mp.Playing += (_, _) => Dispatcher.Invoke(() =>
        {
            if (_isVideo && _videoW == 0)
            {
                uint w = 0, h = 0;
                _mp!.Size(0, ref w, ref h);
                _videoW = w; _videoH = h;
                if (w > 0 && h > 0) AdjustWindowSize(w, h); // LibVLC 自动应用 rotation
            }
        });
        _mp.EndReached += (_, _) => Dispatcher.Invoke(() => { _playing = false; ShowPaused(); });

        if (_isVideo)
        {
            VideoHost.Visibility = Visibility.Visible;
            _videoView = new VideoView { MediaPlayer = _mp };
            VideoHost.Children.Insert(0, _videoView);
            // 预设窗口尺寸：用 Shell 缩略图方向提前设竖版窗口，避免先横版闪现
            var thumb = ShellThumbnail.GetThumbnail(_path, 256);
            if (thumb != null && thumb.PixelWidth > 0 && thumb.PixelHeight > 0)
                AdjustWindowSize(thumb.PixelWidth, thumb.PixelHeight);
        }
        else
        {
            AudioHost.Visibility = Visibility.Visible;
            AudioBigPlay.Visibility = Visibility.Collapsed;
        }
        // 沿用上次音量（0-100，首次 100）
        _mp.Volume = (int)Math.Clamp(_rememberedVolume * 100, 0, 100);
        VolBar.Value = _rememberedVolume * 100;
        UpdateVolIcon(_rememberedVolume);
        // 打开即自动播放
        using var media = new Media(_libVLC, _path);
        _mp.Play(media);
        _playing = true;
        ShowPlaying();
        _ticker.Start();
    }

    /// <summary>按视频显示宽高比调整窗口尺寸并重新居中（WindowStartupLocation 只首次生效，尺寸变化后需手动居中）。</summary>
    private void AdjustWindowSize(double dispW, double dispH)
    {
        const double ctrlH = 96;   // 底部控制栏 + 边距
        const double maxW = 1000, maxH = 780;
        double ratio = dispW / dispH;
        double w, h;
        if (ratio >= 1)
        {
            w = Math.Min(maxW, Math.Max(MinWidth, dispW));
            h = w / ratio + ctrlH;
            if (h > maxH) { h = maxH; w = (h - ctrlH) * ratio; }
        }
        else
        {
            h = Math.Min(maxH, Math.Max(MinHeight, dispH + ctrlH));
            w = (h - ctrlH) * ratio;
            if (w > maxW) { w = maxW; h = w / ratio + ctrlH; }
        }
        Width = Math.Clamp(w, MinWidth, maxW);
        Height = Math.Clamp(h, MinHeight, maxH);
        // 尺寸变化后重新居中到工作区中央
        var work = SystemParameters.WorkArea;
        Left = work.Left + (work.Width - Width) / 2;
        Top = work.Top + (work.Height - Height) / 2;
    }

    // ---------- 图片加载 ----------
    private void LoadImage()
    {
        try
        {
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;
            bmp.UriSource = new Uri(_path);
            bmp.EndInit();
            bmp.Freeze();
            MediaImage.Source = bmp;
        }
        catch
        {
            // DNG/RAW 等 WIC 不支持的格式：退化提取内嵌 JPEG 预览
            var jpeg = ShellThumbnail.ExtractEmbeddedJpeg(_path);
            if (jpeg is not null)
            {
                var src = ShellThumbnail.JpegToBitmapSource(jpeg);
                if (src is not null) { MediaImage.Source = src; return; }
            }
            ShowImageHint();
        }
    }

    /// <summary>解码失败：HEIC（缺 HEIF 扩展）给出安装提示，其余给出通用解码失败提示。</summary>
    private void ShowImageHint()
    {
        MediaImage.Source = null;
        var heic = IsHeicFile(_path);
        ImageHintText.Text = heic
            ? LangManager.T("Msg.HeifNoPreview")
            : LangManager.T("Msg.ImageDecodeFailed");
        InstallHintLink.Visibility = heic ? Visibility.Visible : Visibility.Collapsed;
        if (heic)
        {
            InstallLink.Inlines.Clear();
            InstallLink.Inlines.Add(new Run(LangManager.T("Msg.HeifGoInstall")));
        }
        ImageHint.Visibility = Visibility.Visible;
    }

    private static bool IsHeicFile(string path)
    {
        var ext = Path.GetExtension(path).ToLowerInvariant();
        return ext == ".heic" || ext == ".heif";
    }

    /// <summary>点击「去安装」：打开微软商店 HEIF 图像扩展页面。</summary>
    private void InstallHeifLink_Click(object sender, RequestNavigateEventArgs e)
    {
        e.Handled = true;
        try
        {
            Process.Start(new ProcessStartInfo("ms-windows-store://pdp/?ProductId=9N4WGH0Z6VHQ")
            { UseShellExecute = true });
        }
        catch { /* 商店不可用则忽略 */ }
    }

    // ---------- 图片：缩放（以鼠标为中心）+ 平移（窗口坐标，防丢图）+ 双击复位 ----------
    private double _zoom = 1;
    private Point _lastScreen;
    private bool _dragging;

    private void ImgMouseWheel(object sender, MouseWheelEventArgs e)
    {
        var pos = e.GetPosition(ImageHost);          // 容器坐标（不受图片变换影响）
        var factor = e.Delta > 0 ? 1.2 : 1 / 1.2;
        var newZoom = Math.Clamp(_zoom * factor, 0.5, 8);
        // 以鼠标位置为中心缩放（RenderTransformOrigin=0.5,0.5）：缩放前后保持光标下的图片点不动
        if (ImageHost.ActualWidth > 0 && ImageHost.ActualHeight > 0)
        {
            var cx = ImageHost.ActualWidth / 2;
            var cy = ImageHost.ActualHeight / 2;
            var lx = cx + (pos.X - ImgPan.X - cx) / _zoom;
            var ly = cy + (pos.Y - ImgPan.Y - cy) / _zoom;
            ImgPan.X = pos.X - cx - (lx - cx) * newZoom;
            ImgPan.Y = pos.Y - cy - (ly - cy) * newZoom;
        }
        _zoom = newZoom;
        ImgScale.ScaleX = ImgScale.ScaleY = _zoom;
        ClampPan();
        e.Handled = true;
    }

    private void ImgMouseDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2) { ResetView(); return; }
        _lastScreen = e.GetPosition(this);           // 用窗口坐标，避免变换干扰
        _dragging = true;
        ImageRoot.CaptureMouse();
    }

    private void ImgMouseMove(object sender, MouseEventArgs e)
    {
        if (!_dragging || !ImageRoot.IsMouseCaptured) return;
        var p = e.GetPosition(this);
        ImgPan.X += p.X - _lastScreen.X;
        ImgPan.Y += p.Y - _lastScreen.Y;
        _lastScreen = p;
        ClampPan();
    }

    private void ImgMouseUp(object sender, MouseButtonEventArgs e)
    {
        _dragging = false;
        ImageRoot.ReleaseMouseCapture();
    }

    /// <summary>限制平移范围：任意缩放级别均可拖动，但图片不能完全离开可视区；默认居中（pan=0）。</summary>
    private void ClampPan()
    {
        var viewW = ImageHost.ActualWidth;
        var viewH = ImageHost.ActualHeight;
        if (viewW <= 0 || viewH <= 0) return;
        var dispW = viewW * _zoom;   // 近似显示尺寸
        var dispH = viewH * _zoom;
        // 图片比视口大：范围 (disp-view)/2；图片比视口小：范围 (view-disp)/2（可游走但不越界）
        var maxX = Math.Abs(dispW - viewW) / 2;
        var maxY = Math.Abs(dispH - viewH) / 2;
        ImgPan.X = Math.Clamp(ImgPan.X, -maxX, maxX);
        ImgPan.Y = Math.Clamp(ImgPan.Y, -maxY, maxY);
    }

    private void ResetView()
    {
        _zoom = 1;
        ImgScale.ScaleX = ImgScale.ScaleY = 1;
        ImgPan.X = ImgPan.Y = 0;
    }

    // ---------- 播放控制 ----------
    private void BigPlay_Click(object sender, RoutedEventArgs e)
    {
        if (_mp is null) return;
        _mp.Time = 0;
        _mp.Play();
        _playing = true;
        ShowPlaying();
        _ticker.Start();
    }

    private void PlayToggle_Click(object sender, RoutedEventArgs e)
    {
        if (_mp is null) return;
        if (_playing) { _mp.SetPause(true); _playing = false; ShowPaused(); }
        else { if (_mp.Length > 0 && _mp.Time >= _mp.Length) _mp.Time = 0; _mp.Play(); _playing = true; ShowPlaying(); }
        _ticker.Start();
    }

    private void ShowPlaying()
    {
        PlayIcon.Data = PauseGeometry;
        VideoBigPlay.Visibility = Visibility.Collapsed;
        AudioBigPlay.Visibility = Visibility.Collapsed;
    }

    private void ShowPaused()
    {
        PlayIcon.Data = PlayGeometry;
        if (_isVideo) VideoBigPlay.Visibility = Visibility.Visible;
        if (_isAudio) AudioBigPlay.Visibility = Visibility.Visible;
    }

    private static readonly Geometry PlayGeometry = Geometry.Parse("M10 6 L24 16 L10 26 Z");
    private static readonly Geometry PauseGeometry = Geometry.Parse("M8 6 L14 6 L14 26 L8 26 Z M18 6 L24 6 L24 26 L18 26 Z");

    private void RefreshUi()
    {
        if (_mp is null || _seekScrub) return;
        var lenMs = _mp.Length;
        if (lenMs > 0)
        {
            SeekBar.Value = Math.Min(_mp.Time / 1000.0, SeekBar.Maximum);
            TimeText.Text = $"{Fmt(TimeSpan.FromMilliseconds(_mp.Time))} / {Fmt(TimeSpan.FromMilliseconds(lenMs))}";
        }
        else
        {
            TimeText.Text = $"{Fmt(TimeSpan.FromMilliseconds(_mp.Time))} / --:--";
        }
    }

    private static string Fmt(TimeSpan t) => $"{(int)t.TotalMinutes:00}:{t.Seconds:00}";

    private void Seek_Changed(object s, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_mp is null || _mp.Length <= 0) return;
        TimeText.Text = $"{Fmt(TimeSpan.FromSeconds(e.NewValue))} / {Fmt(TimeSpan.FromMilliseconds(_mp.Length))}";
    }

    // 进度条：点击轨道任意位置即跳转，按住不放可继续拖动（scrubbing），松手应用
    private bool _seekScrub;

    private void Seek_PreviewDown(object s, MouseButtonEventArgs e)
    {
        _seekScrub = true;
        SeekScrub(s, e);
        SeekBar.CaptureMouse();
        e.Handled = true;
    }
    private void Seek_PreviewMove(object s, MouseEventArgs e)
    {
        if (_seekScrub) SeekScrub(s, e);
    }
    private void Seek_PreviewUp(object s, MouseButtonEventArgs e)
    {
        if (!_seekScrub) return;
        SeekBar.ReleaseMouseCapture();
        if (_mp is not null && _mp.Length > 0)
        {
            _mp.Time = (long)(SeekBar.Value * 1000);
            _playing = true;
            ShowPlaying();
        }
        var t = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(500) };
        t.Tick += (_, _) => { t.Stop(); _seekScrub = false; };
        t.Start();
    }
    private void SeekScrub(object s, MouseEventArgs e)
    {
        var w = SeekBar.ActualWidth;
        if (w <= 0) return;
        var ratio = Math.Clamp(e.GetPosition(SeekBar).X / w, 0, 1);
        SeekBar.Value = SeekBar.Minimum + ratio * (SeekBar.Maximum - SeekBar.Minimum);
    }

    // ---------- 音量控制 ----------
    private static double _rememberedVolume = 1;   // 跨预览窗口记住音量，首次默认 100%

    private void VolBar_Changed(object s, RoutedPropertyChangedEventArgs<double> e)
    {
        if (_mp is null) return;
        var v = Math.Clamp(e.NewValue / 100.0, 0, 1);
        _mp.Volume = (int)(v * 100);
        _rememberedVolume = v;
        UpdateVolIcon(v);
    }

    // 音量条：点击轨道任意位置即调节，按住不放可继续拖动
    private bool _volScrub;
    private void VolBar_PreviewDown(object s, MouseButtonEventArgs e)
    {
        _volScrub = true;
        VolScrub(s, e);
        VolBar.CaptureMouse();
        e.Handled = true;
    }
    private void VolBar_PreviewMove(object s, MouseEventArgs e)
    {
        if (_volScrub) VolScrub(s, e);
    }
    private void VolBar_PreviewUp(object s, MouseButtonEventArgs e)
    {
        _volScrub = false;
        VolBar.ReleaseMouseCapture();
    }
    private void VolScrub(object s, MouseEventArgs e)
    {
        var w = VolBar.ActualWidth;
        if (w <= 0) return;
        var ratio = Math.Clamp(e.GetPosition(VolBar).X / w, 0, 1);
        VolBar.Value = VolBar.Minimum + ratio * (VolBar.Maximum - VolBar.Minimum);
    }

    private void VolBtn_Click(object s, RoutedEventArgs e)
    {
        if (_mp is null) return;
        var curVol = _mp.Volume / 100.0;
        if (curVol > 0.001)
        {
            _rememberedVolume = curVol;
            _mp.Volume = 0;
            VolBar.Value = 0;
        }
        else
        {
            var v = _rememberedVolume > 0.001 ? _rememberedVolume : 1;
            _mp.Volume = (int)(v * 100);
            VolBar.Value = v * 100;
        }
        UpdateVolIcon(_mp.Volume / 100.0);
    }

    private void UpdateVolIcon(double vol)
    {
        VolIcon.Text = vol <= 0.001 ? "\uE74F" : "\uE767";
    }

    private void ShowVol(bool show)
    {
        if (show)
        {
            VolCapsule.Visibility = Visibility.Visible;
            VolCapsule.BeginAnimation(OpacityProperty, null);
            VolCapsule.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(150)));
        }
        else if (!VolCapsule.IsMouseOver && !VolBtn.IsMouseOver)
        {
            VolCapsule.BeginAnimation(OpacityProperty, new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(120)));
            var t = new System.Windows.Threading.DispatcherTimer { Interval = TimeSpan.FromMilliseconds(130) };
            t.Tick += (_, _) => { t.Stop(); if (!VolCapsule.IsMouseOver && !VolBtn.IsMouseOver) VolCapsule.Visibility = Visibility.Collapsed; };
            t.Start();
        }
    }

    // ---------- 标题栏：拖动 / 窗口按钮 ----------
    private void TitleBar_MouseLeftDown(object s, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            MaxBtn_Click(s, e);
            return;
        }
        if (e.ButtonState == MouseButtonState.Pressed) DragMove();
    }

    private void MinBtn_Click(object s, RoutedEventArgs e) => SystemCommands.MinimizeWindow(this);

    private void MaxBtn_Click(object s, RoutedEventArgs e)
    {
        if (WindowState == WindowState.Maximized) SystemCommands.RestoreWindow(this);
        else SystemCommands.MaximizeWindow(this);
    }

    private void UpdateMaxIcon()
    {
        MaxIcon.Text = WindowState == WindowState.Maximized ? "\uE923" : "\uE922";
    }

    private void CloseBtn_Click(object s, RoutedEventArgs e) => Close();

    protected override void OnKeyDown(KeyEventArgs e)
    {
        if (e.Key == Key.Escape) { Close(); return; }
        base.OnKeyDown(e);
    }
}
