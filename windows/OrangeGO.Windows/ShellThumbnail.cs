using System;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Interop;
using System.Windows.Media.Imaging;

namespace OrangeGO.Windows;

/// <summary>通过 Windows Shell API 获取文件缩略图（视频会返回其首帧画面，与资源管理器一致）。</summary>
public static class ShellThumbnail
{
    [ComImport]
    [Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IShellItemImageFactory
    {
        [PreserveSig]
        int GetImage([In] ref NativeSize size, [In] int flags, [Out] out IntPtr phbm);
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct NativeSize
    {
        public int cx, cy;
        public NativeSize(int w, int h) { cx = w; cy = h; }
    }

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHCreateItemFromParsingName(
        [MarshalAs(UnmanagedType.LPWStr)] string pszPath,
        IntPtr pbc,
        ref Guid riid,
        [MarshalAs(UnmanagedType.Interface)] out IShellItemImageFactory ppv);

    [DllImport("gdi32.dll")]
    private static extern bool DeleteObject(IntPtr hObject);

    /// <summary>同步获取指定文件/视频的缩略图；失败或无效时返回 null。</summary>
    public static BitmapSource? GetThumbnail(string path, int size = 256)
    {
        try
        {
            var iid = new Guid("bcc18b79-ba16-442f-80c4-8a59c30c463b");
            if (SHCreateItemFromParsingName(path, IntPtr.Zero, ref iid, out var factory) != 0)
                return null;
            var sz = new NativeSize(size, size);
            if (factory.GetImage(ref sz, 0, out var hbm) != 0 || hbm == IntPtr.Zero)
                return null;
            try
            {
                var src = Imaging.CreateBitmapSourceFromHBitmap(
                    hbm, IntPtr.Zero, Int32Rect.Empty, BitmapSizeOptions.FromEmptyOptions());
                src.Freeze();
                return src;
            }
            finally
            {
                DeleteObject(hbm);
            }
        }
        catch { return null; }
    }

    /// <summary>后台线程获取缩略图并回调到 UI 线程；成功时以 <paramref name="onGot"/> 交付。</summary>
    public static async Task LoadThumbnailAsync(string path, int size, Action<BitmapSource?> onGot)
    {
        var src = await Task.Run(() => GetThumbnail(path, size));
        onGot(src);
    }

    /// <summary>从 RAW/DNG 文件提取内嵌 JPEG 预览（扫描最大 JPEG 段 FF D8…FF D9）；无嵌入 JPEG 或文件过大返回 null。</summary>
    public static byte[]? ExtractEmbeddedJpeg(string path)
    {
        try
        {
            if (!File.Exists(path)) return null;
            var fi = new FileInfo(path);
            if (fi.Length > 100L * 1024 * 1024) return null;
            var bytes = File.ReadAllBytes(path);
            int bestStart = -1, bestLen = 0;
            int i = 0;
            while (i < bytes.Length - 1)
            {
                if (bytes[i] == 0xFF && bytes[i + 1] == 0xD8)
                {
                    int j = i + 2;
                    while (j < bytes.Length - 1)
                    {
                        if (bytes[j] == 0xFF && bytes[j + 1] == 0xD9) { j += 2; break; }
                        j++;
                    }
                    if (j > bytes.Length) j = bytes.Length;
                    int len = j - i;
                    if (len > bestLen) { bestStart = i; bestLen = len; }
                    i = j;
                }
                else i++;
            }
            if (bestStart < 0 || bestLen < 2048) return null;
            var result = new byte[bestLen];
            Array.Copy(bytes, bestStart, result, 0, bestLen);
            return result;
        }
        catch { return null; }
    }

    /// <summary>由内嵌 JPEG 字节生成 BitmapSource；失败返回 null。</summary>
    public static BitmapSource? JpegToBitmapSource(byte[] jpeg)
    {
        try
        {
            if (jpeg is null || jpeg.Length < 4) return null;
            var src = new BitmapImage();
            src.BeginInit();
            src.CacheOption = BitmapCacheOption.OnLoad;
            src.StreamSource = new MemoryStream(jpeg);
            src.EndInit();
            src.Freeze();
            return src;
        }
        catch { return null; }
    }
}
