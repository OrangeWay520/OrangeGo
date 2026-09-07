using System;
using System.Collections.Concurrent;
using System.Globalization;
using System.IO;
using System.Windows;
using System.Windows.Data;
using System.Windows.Media;
using System.Windows.Media.Imaging;

namespace OrangeGO.Windows;

/// <summary>把 0~1 的传输进度转换为顺时针圆弧（圆形进度环）：0 为空、1 为整圆。
/// 圆弧基于 11x11 视图框、圆心 (5.5,5.5) 半径 4.7，与完成态 Ellipse 11x11 StrokeThickness=1.6 一一对应。</summary>
public sealed class ProgressRingConverter : IValueConverter
{
    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        double p = value is double d ? d : 0d;
        const double cx = 5.5, cy = 5.5, r = 4.7;
        if (p >= 1)
        {
            var full = new EllipseGeometry { Center = new System.Windows.Point(cx, cy), RadiusX = r, RadiusY = r };
            full.Freeze();
            return full;
        }
        if (p <= 0) return Geometry.Empty;

        double sweep = p * 360.0;
        System.Windows.Point A(double deg) => new System.Windows.Point(cx + r * Math.Cos(Math.PI * (deg - 90) / 180.0),
                                         cy + r * Math.Sin(Math.PI * (deg - 90) / 180.0));
        var fig = new PathFigure { StartPoint = A(0), IsClosed = false };
        fig.Segments.Add(new ArcSegment(A(sweep), new System.Windows.Size(r, r), 0, sweep > 180,
                                        SweepDirection.Clockwise, true));
        var geo = new PathGeometry();
        geo.Figures.Add(fig);
        geo.Freeze();
        return geo;
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// <summary>以元素的实际宽/高（MultiBinding 传入）生成 Rect，供 RectangleGeometry.Rect 使用，
/// 让图片缩略图按实际尺寸获得与气泡一致的圆角裁剪（注意：目标属性类型是 Rect 结构，不是 Geometry）。</summary>
public sealed class RoundedRectConverter : IMultiValueConverter
{
    public object Convert(object[] values, Type targetType, object parameter, CultureInfo culture)
    {
        if (values is { Length: >= 2 } && values[0] is double w && values[1] is double h
            && !double.IsNaN(w) && !double.IsNaN(h) && w > 0 && h > 0)
        {
            return new System.Windows.Rect(0, 0, w, h);
        }
        return System.Windows.Rect.Empty;
    }

    public object[] ConvertBack(object value, Type[] targetTypes, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}

/// <summary>把本地图片完整路径转换为"解码尺寸受限"的 BitmapImage（480px，防大图全尺寸解码卡顿/占内存）。
/// 只在文件确实完整可解码时才缓存；解码失败不缓存（避免文件尚未就绪/下载中时把"空"永久缓存导致永不显示）。</summary>
public sealed class PathThumbConverter : IValueConverter
{
    private sealed record Entry(long Length, long WriteTicks, BitmapImage Image);
    private static readonly ConcurrentDictionary<string, Entry> Cache = new(StringComparer.OrdinalIgnoreCase);

    public object Convert(object value, Type targetType, object parameter, CultureInfo culture)
    {
        if (value is not string path || string.IsNullOrWhiteSpace(path))
            return null!;
        try
        {
            var fi = new FileInfo(path);
            if (!fi.Exists || fi.Length == 0) return null!;
            // 文件长度/修改时间变化（如下载中）→ 视为过期，重新解码
            if (Cache.TryGetValue(path, out var e) && e.Length == fi.Length && e.WriteTicks == fi.LastWriteTimeUtc.Ticks)
                return e.Image;
            var bmp = Load(path);
            if (bmp is null) return null!; // 解码失败不缓存，待文件就绪后重试
            Cache[path] = new Entry(fi.Length, fi.LastWriteTimeUtc.Ticks, bmp);
            return bmp;
        }
        catch { return null!; }
    }

    private static BitmapImage? Load(string path)
    {
        try
        {
            var bmp = new BitmapImage();
            bmp.BeginInit();
            bmp.CacheOption = BitmapCacheOption.OnLoad;  // 立即解码释放文件句柄
            bmp.DecodePixelWidth = 480;                   // 仅解码到缩略图分辨率，防内存爆涨
            bmp.UriSource = new Uri(path);
            bmp.EndInit();
            bmp.Freeze();
            return bmp;
        }
        catch { return null; }
    }

    public object ConvertBack(object value, Type targetType, object parameter, CultureInfo culture)
        => throw new NotSupportedException();
}