using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Point = System.Windows.Point;
using MouseEventArgs = System.Windows.Input.MouseEventArgs;
using KeyEventArgs = System.Windows.Input.KeyEventArgs;

namespace OrangeGO.Windows;

/// <summary>
/// 微信式区域截图窗口：捕获主屏 → 拖拽选择区域 → 简单标注（矩形/椭圆/箭头/画笔/文字、换色、撤销）→ 保存 PNG。
/// </summary>
public partial class ScreenshotWindow : Window
{
    private readonly string _saveDir;

    private BitmapSource? _source;
    private System.Drawing.Bitmap? _fullBmp; // 全屏原始位图，用于 GDI+ 离屏合成导出
    private double _pixelW, _pixelH;      // 截图像素尺寸
    private double _scaleX = 1, _scaleY = 1; // 像素/窗口DIP 比例

    private bool _selecting;
    private bool _editing;
    private Point _start;
    private Rect _selRect;

    // 标注状态
    private EditTool _tool = EditTool.Rect;
    private System.Windows.Media.Brush _color = System.Windows.Media.Brushes.Red;
    private UIElement? _cur;
    private List<Point>? _penPts;
    private System.Windows.Shapes.Line? _arrowLine;
    private System.Windows.Shapes.Polygon? _arrowHead;
    private readonly Stack<List<UIElement>> _strokes = new();

    /// <summary>截图保存的完整路径；取消则为 null。</summary>
    public string? SavedPath { get; private set; }

    private enum EditTool { None, Rect, Oval, Arrow, Pen, Text }

    public ScreenshotWindow(string saveDir)
    {
        InitializeComponent();
        _saveDir = saveDir;
        Loaded += OnLoaded;
        TextEditor.KeyDown += TextEditor_KeyDown;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        CaptureScreen();
        Root.MouseLeftButtonDown += OnMouseDown;
        Root.MouseMove += OnMouseMove;
        Root.MouseLeftButtonUp += OnMouseUp;
    }

    private void CaptureScreen()
    {
        try
        {
            var bounds = System.Windows.Forms.Screen.PrimaryScreen.Bounds;
            using var bmp = new System.Drawing.Bitmap(bounds.Width, bounds.Height);
            using (var g = System.Drawing.Graphics.FromImage(bmp))
                g.CopyFromScreen(bounds.X, bounds.Y, 0, 0, bmp.Size);
            _fullBmp = (System.Drawing.Bitmap)bmp.Clone();

            _pixelW = bmp.Width;
            _pixelH = bmp.Height;
            var hbmp = bmp.GetHbitmap();
            try
            {
                _source = System.Windows.Interop.Imaging.CreateBitmapSourceFromHBitmap(
                    hbmp, System.IntPtr.Zero, System.Windows.Int32Rect.Empty,
                    BitmapSizeOptions.FromEmptyOptions());
                _source.Freeze();
            }
            finally { DeleteObject(hbmp); }
        }
        catch { _source = null; }

        Left = 0;
        Top = 0;
        Width = SystemParameters.PrimaryScreenWidth;
        Height = SystemParameters.PrimaryScreenHeight;
        if (_source is null)
        {
            DialogResult = false;
            return;
        }
        Shot.Source = _source;
        Shot.Width = Width;
        Shot.Height = Height;
        DrawCanvas.Width = Width;
        DrawCanvas.Height = Height;
        Dim.Width = Width;
        Dim.Height = Height;
        _scaleX = _pixelW / Width;
        _scaleY = _pixelH / Height;
        Focus();
    }

    // ---------- 选区阶段 ----------
    private void OnMouseDown(object sender, MouseButtonEventArgs e)
    {
        var p = e.GetPosition(Root);

        // 编辑模式：文字工具点击则放置输入框
        if (_editing)
        {
            if (_tool == EditTool.Text)
            {
                PlaceTextEditor(p);
                return;
            }
            BeginDraw(e.GetPosition(DrawCanvas));
            return;
        }

        _start = p;
        _selecting = true;
        Root.CaptureMouse();
        SelBorder.Visibility = Visibility.Visible;
    }

    private void OnMouseMove(object sender, MouseEventArgs e)
    {
        if (_selecting)
        {
            UpdateSelection(_start, e.GetPosition(Root));
            return;
        }
        if (_editing && _cur != null)
        {
            UpdateDraw(e.GetPosition(DrawCanvas));
        }
    }

    private void OnMouseUp(object sender, MouseButtonEventArgs e)
    {
        if (_selecting)
        {
            _selecting = false;
            Root.ReleaseMouseCapture();
            var cur = e.GetPosition(Root);
            UpdateSelection(_start, cur);
            var r = NormalRect(_start, cur);
            if (_source is null || r.Width < 4 || r.Height < 4)
            {
                // 太小或未拖动：退出（等效取消）
                if (Toolbar.Visibility == Visibility.Collapsed) Cancel();
                _selecting = false;
                return;
            }
            EnterEdit(r);
            return;
        }
        if (_editing && _cur != null)
        {
            UpdateDraw(e.GetPosition(DrawCanvas));
            FinishDraw();
        }
    }

    /// <summary>选区完成，进入标注阶段。</summary>
    private void EnterEdit(Rect r)
    {
        _editing = true;
        _selRect = r;
        DrawCanvas.Clip = new RectangleGeometry(r);

        // 工具栏出现在选区正下方
        Toolbar.Visibility = Visibility.Visible;
        Toolbar.UpdateLayout();
        Canvas.SetLeft(Toolbar, (Width - Toolbar.ActualWidth) / 2);
        Canvas.SetTop(Toolbar, Height - Toolbar.ActualHeight - 16);

        // 尺寸标签贴在选区上方
        SizeLabel.Text = $"{(int)(r.Width * _scaleX + 0.5)} × {(int)(r.Height * _scaleY + 0.5)}";
        Canvas.SetLeft(SizeTag, r.X);
        Canvas.SetTop(SizeTag, r.Y - 28);
        SizeTag.Visibility = Visibility.Visible;
        Hint.Visibility = Visibility.Collapsed;
    }

    // ---------- 标注绘制 ----------
    private void BeginDraw(Point p)
    {
        switch (_tool)
        {
            case EditTool.Rect:
                _cur = new System.Windows.Shapes.Rectangle
                {
                    Stroke = _color, StrokeThickness = 2.6,
                    StrokeLineJoin = PenLineJoin.Round, Fill = System.Windows.Media.Brushes.Transparent
                };
                DrawCanvas.Children.Add(_cur);
                break;
            case EditTool.Oval:
                _cur = new System.Windows.Shapes.Ellipse
                {
                    Stroke = _color, StrokeThickness = 2.6,
                    Fill = System.Windows.Media.Brushes.Transparent
                };
                DrawCanvas.Children.Add(_cur);
                break;
            case EditTool.Arrow:
                _start = p;
                _arrowLine = new System.Windows.Shapes.Line
                {
                    Stroke = _color, StrokeThickness = 2.6,
                    StrokeStartLineCap = PenLineCap.Round, StrokeEndLineCap = PenLineCap.Round
                };
                _arrowHead = new System.Windows.Shapes.Polygon { Fill = _color };
                DrawCanvas.Children.Add(_arrowLine);
                DrawCanvas.Children.Add(_arrowHead);
                _cur = _arrowLine;
                break;
            case EditTool.Pen:
                _start = p;
                _penPts = new List<Point> { p };
                var path = new System.Windows.Shapes.Path
                {
                    Stroke = _color, StrokeThickness = 2.6, StrokeLineJoin = PenLineJoin.Round,
                    StrokeEndLineCap = PenLineCap.Round, Fill = null
                };
                path.Data = BuildPenGeometry(_penPts);
                path.Tag = _penPts; // 供 GDI+ 导出按原轨迹绘制
                DrawCanvas.Children.Add(path);
                _cur = path;
                break;
        }
    }

    private void UpdateDraw(Point p)
    {
        var r = NormalRect(_start, p);
        switch (_tool)
        {
            case EditTool.Rect:
            case EditTool.Oval:
                if (_cur is System.Windows.Shapes.Shape s)
                {
                    Canvas.SetLeft(s, r.X);
                    Canvas.SetTop(s, r.Y);
                    s.Width = r.Width;
                    s.Height = r.Height;
                }
                break;
            case EditTool.Arrow:
                if (_arrowLine != null && _arrowHead != null)
                {
                    _arrowLine.X1 = _start.X; _arrowLine.Y1 = _start.Y;
                    _arrowLine.X2 = p.X; _arrowLine.Y2 = p.Y;
                    _arrowHead.Points = ArrowHead(_start, p, 12);
                }
                break;
            case EditTool.Pen:
                if (_penPts != null && _cur is System.Windows.Shapes.Path pp)
                {
                    _penPts.Add(p);
                    pp.Data = BuildPenGeometry(_penPts);
                }
                break;
        }
    }

    private void FinishDraw()
    {
        if (_cur != null)
        {
            var group = new List<UIElement> { _cur };
            if (_arrowLine != null && _arrowLine != _cur) group.Add(_arrowLine);
            if (_arrowHead != null) group.Add(_arrowHead);
            _strokes.Push(group);
            _cur = null;
            _arrowLine = null;
            _arrowHead = null;
            _penPts = null;
        }
    }

    private static StreamGeometry BuildPenGeometry(List<Point> pts)
    {
        var geo = new StreamGeometry();
        using (var ctx = geo.Open())
        {
            ctx.BeginFigure(pts[0], isFilled: false, isClosed: false);
            for (int i = 1; i < pts.Count; i++) ctx.LineTo(pts[i], true, false);
        }
        geo.Freeze();
        return geo;
    }

    private static PointCollection ArrowHead(Point a, Point b, double sz)
    {
        var dx = b.X - a.X;
        var dy = b.Y - a.Y;
        var len = Math.Sqrt(dx * dx + dy * dy);
        if (len < 1) len = 1;
        var ux = dx / len;
        var uy = dy / len;
        var basex = b.X - ux * sz;
        var basey = b.Y - uy * sz;
        // 两翼端点
        var px = -uy * sz * 0.5;
        var py = ux * sz * 0.5;
        var c = new PointCollection
        {
            b,
            new Point(basex + px, basey + py),
            new Point(basex - px, basey - py)
        };
        return c;
    }

    // ---------- 文字标注 ----------
    private void PlaceTextEditor(Point p)
    {
        Canvas.SetLeft(TextEditor, p.X);
        Canvas.SetTop(TextEditor, p.Y);
        TextEditor.Visibility = Visibility.Visible;
        TextEditor.Text = "";
        TextEditor.Focus();
    }

    private void TextEditor_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Enter)
        {
            e.Handled = true;
            CommitText();
        }
        else if (e.Key == Key.Escape)
        {
            e.Handled = true;
            TextEditor.Visibility = Visibility.Collapsed;
            Focus();
        }
    }

    private void CommitText()
    {
        var t = TextEditor.Text.Trim();
        if (t.Length > 0)
        {
            var tb = new TextBlock
            {
                Text = t, Foreground = _color, FontSize = 15,
                FontWeight = FontWeights.SemiBold
            };
            Canvas.SetLeft(tb, Canvas.GetLeft(TextEditor));
            Canvas.SetTop(tb, Canvas.GetTop(TextEditor));
            DrawCanvas.Children.Add(tb);
            _strokes.Push(new List<UIElement> { tb });
        }
        TextEditor.Visibility = Visibility.Collapsed;
        Root.ReleaseMouseCapture();
        Focus();
    }

    // ---------- 工具栏 ----------
    private void Tool_Click(object sender, RoutedEventArgs e)
    {
        if (e.OriginalSource is not Button b || b.Tag is not string tag) return;
        CommitOrCancelText();
        _tool = tag switch
        {
            "rect" => EditTool.Rect,
            "oval" => EditTool.Oval,
            "arrow" => EditTool.Arrow,
            "pen" => EditTool.Pen,
            "text" => EditTool.Text,
            _ => _tool
        };
        Cursor = System.Windows.Input.Cursors.Arrow;
    }

    private void Swatch_Click(object sender, RoutedEventArgs e)
    {
        if (e.OriginalSource is not Button b || b.Tag is not string tag) return;
        _color = tag switch
        {
            "red" => System.Windows.Media.Brushes.Red,
            "orange" => System.Windows.Media.Brushes.Orange,
            "yellow" => System.Windows.Media.Brushes.Gold,
            "green" => System.Windows.Media.Brushes.Green,
            "blue" => System.Windows.Media.Brushes.DodgerBlue,
            "white" => System.Windows.Media.Brushes.White,
            _ => _color
        };
    }

    private void Undo_Click(object sender, RoutedEventArgs e)
    {
        CommitOrCancelText();
        if (_strokes.Count > 0)
        {
            foreach (var el in _strokes.Pop())
                DrawCanvas.Children.Remove(el);
            Focus();
        }
    }

    private void CancelTool_Click(object sender, RoutedEventArgs e) => Cancel();

    private void Done_Click(object sender, RoutedEventArgs e)
    {
        CommitOrCancelText();
        SaveSelection();
    }

    private void CommitOrCancelText()
    {
        if (TextEditor.Visibility != Visibility.Visible) return;
        TextEditor.Visibility = Visibility.Collapsed;
        Root.ReleaseMouseCapture();
        Focus();
    }

    // ---------- 选区亮色渲染 ----------
    private void UpdateSelection(Point a, Point b)
    {
        var r = NormalRect(a, b);
        Canvas.SetLeft(SelBorder, r.X);
        Canvas.SetTop(SelBorder, r.Y);
        SelBorder.Width = r.Width;
        SelBorder.Height = r.Height;

        Canvas.SetLeft(SelFill, r.X);
        Canvas.SetTop(SelFill, r.Y);
        SelFill.Width = r.Width;
        SelFill.Height = r.Height;
        if (_source is not null && r.Width > 0 && r.Height > 0)
        {
            var px = new System.Windows.Int32Rect(
                (int)(r.X * _scaleX), (int)(r.Y * _scaleY),
                (int)(r.Width * _scaleX), (int)(r.Height * _scaleY));
            try
            {
                var bright = new ImageBrush(new CroppedBitmap(_source, px)) { Stretch = Stretch.Fill };
                SelFill.Fill = bright;
            }
            catch { }
        }
    }

    private static Rect NormalRect(Point a, Point b)
    {
        var x = Math.Min(a.X, b.X);
        var y = Math.Min(a.Y, b.Y);
        return new Rect(x, y, Math.Abs(a.X - b.X), Math.Abs(a.Y - b.Y));
    }

    // ---------- 保存 / 取消 ----------
    private void SaveSelection()
    {
        if (_fullBmp is null || _selRect.Width < 1 || _selRect.Height < 1)
        {
            Cancel();
            return;
        }
        try
        {
            // 选中区域像素尺寸
            int selW = (int)Math.Round(_selRect.Width * _scaleX);
            int selH = (int)Math.Round(_selRect.Height * _scaleY);
            selW = Math.Max(1, selW);
            selH = Math.Max(1, selH);
            float sx = (float)_scaleX, sy = (float)_scaleY;
            float ox = (float)(_selRect.X * _scaleX);
            float oy = (float)(_selRect.Y * _scaleY);

            // 纯 GDI+ 离屏合成：不触发任何 WPF 渲染管线，彻底避免卡死
            var outBmp = new System.Drawing.Bitmap(selW, selH, System.Drawing.Imaging.PixelFormat.Format32bppArgb);
            using (var g = System.Drawing.Graphics.FromImage(outBmp))
            {
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                g.PixelOffsetMode = System.Drawing.Drawing2D.PixelOffsetMode.HighQuality;

                var src = new System.Drawing.Rectangle((int)Math.Round(_selRect.X * _scaleX),
                    (int)Math.Round(_selRect.Y * _scaleY), selW, selH);
                g.DrawImage(_fullBmp, new System.Drawing.Rectangle(0, 0, selW, selH), src, System.Drawing.GraphicsUnit.Pixel);

                // 叠加标注
                foreach (var el in DrawCanvas.Children.OfType<System.Windows.UIElement>())
                    DrawAsGdi(g, el, sx, sy, ox, oy);
            }

            Directory.CreateDirectory(_saveDir);
            var name = $"screenshot_{DateTime.Now:yyyyMMdd_HHmmss}.png";
            SavedPath = System.IO.Path.Combine(_saveDir, name);
            outBmp.Save(SavedPath, System.Drawing.Imaging.ImageFormat.Png);
            outBmp.Dispose();
        }
        catch (Exception saveEx)
        {
            App.LogCrash(new Exception("[SaveSelection]", saveEx));
            SavedPath = null;
        }
        DialogResult = true;
    }

    /// <summary>把 DrawCanvas 里的标注对象按屏幕像素映射，用 GDI+ 绘制到导出图。</summary>
    private void DrawAsGdi(System.Drawing.Graphics g, System.Windows.UIElement el,
        float sx, float sy, float ox, float oy)
    {
        var css = el as System.Windows.Shapes.Shape;
        var stroke = css?.Stroke as SolidColorBrush;
        var strokeColor = stroke?.Color ?? System.Windows.Media.Colors.Red;
        var penCol = System.Drawing.Color.FromArgb(strokeColor.A, strokeColor.R, strokeColor.G, strokeColor.B);

        // 画笔（钢笔轨迹）
        if (el is System.Windows.Shapes.Path pen && pen.Tag is List<Point> penPts && penPts.Count >= 2)
        {
            var th = (float)(pen.StrokeThickness * ((sx + sy) / 2));
            using var p = new System.Drawing.Pen(penCol, Math.Max(1f, th))
            { LineJoin = System.Drawing.Drawing2D.LineJoin.Round, StartCap = System.Drawing.Drawing2D.LineCap.Round, EndCap = System.Drawing.Drawing2D.LineCap.Round };
            float[] xs = new float[penPts.Count];
            float[] ys = new float[penPts.Count];
            for (int i = 0; i < penPts.Count; i++)
            {
                xs[i] = (float)penPts[i].X * sx - ox;
                ys[i] = (float)penPts[i].Y * sy - oy;
            }
            g.DrawLines(p, ToPoints(xs, ys));
            return;
        }

        switch (el)
        {
            case System.Windows.Shapes.Line line:
            {
                using var p = new System.Drawing.Pen(penCol, Math.Max(1f, (float)line.StrokeThickness * Math.Max(sx, sy)));
                g.DrawLine(p, (float)line.X1 * sx - ox, (float)line.Y1 * sy - oy,
                    (float)line.X2 * sx - ox, (float)line.Y2 * sy - oy);
                break;
            }
            case System.Windows.Shapes.Polygon pg:
            {
                var fc = (pg.Fill as SolidColorBrush)?.Color ?? strokeColor;
                using var b = new System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(fc.A, fc.R, fc.G, fc.B));
                var pts = new System.Drawing.PointF[pg.Points.Count];
                for (int i = 0; i < pg.Points.Count; i++)
                {
                    pts[i] = new System.Drawing.PointF((float)pg.Points[i].X * sx - ox,
                        (float)pg.Points[i].Y * sy - oy);
                }
                if (pts.Length >= 3) g.FillPolygon(b, pts);
                break;
            }
            case System.Windows.Shapes.Rectangle rect:
            {
                var r = GdiRect(Canvas.GetLeft(rect), Canvas.GetTop(rect), rect.Width, rect.Height, sx, sy, ox, oy);
                using var p = new System.Drawing.Pen(penCol, Math.Max(1f, (float)rect.StrokeThickness * Math.Max(sx, sy)));
                g.DrawRectangle(p, r.X, r.Y, r.Width, r.Height);
                break;
            }
            case System.Windows.Shapes.Ellipse oval:
            {
                var r = GdiRect(Canvas.GetLeft(oval), Canvas.GetTop(oval), oval.Width, oval.Height, sx, sy, ox, oy);
                using var p = new System.Drawing.Pen(penCol, Math.Max(1f, (float)oval.StrokeThickness * Math.Max(sx, sy)));
                g.DrawEllipse(p, r.X, r.Y, r.Width, r.Height);
                break;
            }
            case TextBlock tb:
            {
                var tc = (tb.Foreground as SolidColorBrush)?.Color ?? strokeColor;
                using var b = new System.Drawing.SolidBrush(System.Drawing.Color.FromArgb(tc.A, tc.R, tc.G, tc.B));
                var fsize = Math.Max(8f, (float)tb.FontSize * sy);
                using var f = new System.Drawing.Font("Microsoft YaHei UI", fsize, System.Drawing.GraphicsUnit.Pixel);
                g.DrawString(tb.Text, f, b,
                    (float)Canvas.GetLeft(tb) * sx - ox, (float)Canvas.GetTop(tb) * sy - oy);
                break;
            }
        }
    }

    private static System.Drawing.RectangleF GdiRect(double x, double y, double w, double h,
        float sx, float sy, float ox, float oy)
        => new System.Drawing.RectangleF((float)x * sx - ox, (float)y * sy - oy,
            (float)w * sx, (float)h * sy);

    private static System.Drawing.PointF[] ToPoints(float[] xs, float[] ys)
    {
        var pts = new System.Drawing.PointF[xs.Length];
        for (int i = 0; i < xs.Length; i++) pts[i] = new System.Drawing.PointF(xs[i], ys[i]);
        return pts;
    }

    private void Window_KeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape) Cancel();
    }

    private void Cancel()
    {
        SavedPath = null;
        DialogResult = false;
    }

    [System.Runtime.InteropServices.DllImport("gdi32.dll")]
    private static extern bool DeleteObject(IntPtr hObject);
}