using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Windows.Media.Imaging;

namespace OrangeGO.Windows.Core;

/// <summary>缩略图磁盘持久缓存。key = 内容指纹（文件名|大小|修改时间 的 SHA-256），与源文件路径解耦，
/// 源文件被删除/移动后缩略图仍可命中，直到传输记录被删除/撤回/清空时精确回收。</summary>
public static class ThumbCache
{
    private static string Dir => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "OrangeGO", "thumbnails");

    /// <summary>由源文件元数据计算缓存 key（文件名、字节数、UTC 修改时间）。</summary>
    public static string KeyOf(string name, long size, long mtimeUtcTicks)
    {
        var raw = System.Text.Encoding.UTF8.GetBytes($"{name}|{size}|{mtimeUtcTicks}");
        var hash = SHA256.HashData(raw);
        return Convert.ToHexString(hash).ToLowerInvariant()[..32];
    }

    /// <summary>由本地路径直接计算 key（统计文件大小与修改时间）；文件不存在或不可读时返回 null。</summary>
    public static string? KeyOfPath(string path)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(path) || !File.Exists(path)) return null;
            var fi = new FileInfo(path);
            return KeyOf(fi.Name, fi.Length, fi.LastWriteTimeUtc.Ticks);
        }
        catch { return null; }
    }

    private static string ResolvePath(string key) => Path.Combine(Dir, key + ".png");

    /// <summary>读取已缓存缩略图；未命中或解码失败返回 null。</summary>
    public static BitmapSource? Load(string key)
    {
        try
        {
            var p = ResolvePath(key);
            if (!File.Exists(p)) return null;
            var src = new BitmapImage();
            src.BeginInit();
            src.CacheOption = BitmapCacheOption.OnLoad;
            src.CreateOptions = BitmapCreateOptions.IgnoreImageCache;
            src.UriSource = new Uri(p, UriKind.Absolute);
            src.EndInit();
            src.Freeze();
            return src;
        }
        catch { return null; }
    }

    /// <summary>把缩略图编码为 PNG 写入磁盘缓存；失败静默忽略。</summary>
    public static void Save(string key, BitmapSource bitmap)
    {
        try
        {
            if (string.IsNullOrWhiteSpace(key) || bitmap is null) return;
            Directory.CreateDirectory(Dir);
            var encoder = new PngBitmapEncoder();
            encoder.Frames.Add(BitmapFrame.Create(bitmap));
            using var fs = new FileStream(ResolvePath(key), FileMode.Create, FileAccess.Write);
            encoder.Save(fs);
        }
        catch { /* 缓存写失败不阻塞传输记录 */ }
    }

    /// <summary>删除单个/多个缓存条目。</summary>
    public static void Delete(string key)
    {
        if (string.IsNullOrWhiteSpace(key)) return;
        var p = ResolvePath(key);
        try { if (File.Exists(p)) File.Delete(p); } catch { }
    }

    public static void Delete(IEnumerable<string> keys)
    {
        if (keys is null) return;
        foreach (var k in keys) Delete(k);
    }

    /// <summary>清空整个缩略图缓存目录（逐个删文件，单个占用不阻塞其余）。</summary>
    public static void ClearAll()
    {
        try
        {
            if (!Directory.Exists(Dir)) return;
            foreach (var f in Directory.EnumerateFiles(Dir))
            {
                try { File.Delete(f); } catch { }
            }
        }
        catch { }
    }

    /// <summary>当前缓存总字节数（供设置界面显示）。</summary>
    public static long TotalSizeBytes()
    {
        try
        {
            if (!Directory.Exists(Dir)) return 0;
            return Directory.EnumerateFiles(Dir, "*.png", SearchOption.TopDirectoryOnly)
                .Sum(f => { try { return new FileInfo(f).Length; } catch { return 0L; } });
        }
        catch { return 0; }
    }
}