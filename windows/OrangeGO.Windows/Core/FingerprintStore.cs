using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>
/// 持久化指纹信任库（TOFU：首次连接信任并存储，后续连接校验一致性，防局域网 MITM）。
/// 按 IP 存储 TLS 证书 SHA-256 指纹；与 SSH known_hosts 同模型。
/// </summary>
public sealed class FingerprintStore
{
    private readonly string _path;
    private readonly object _lock = new();
    private Dictionary<string, string> _map;

    public FingerprintStore()
    {
        _path = Path.Combine(AppContext.BaseDirectory, "fingerprints.json");
        _map = Load();
    }

    public string Get(string ip)
    {
        lock (_lock) { return _map.TryGetValue(ip, out var fp) ? fp : ""; }
    }

    public void Put(string ip, string fingerprint)
    {
        lock (_lock)
        {
            _map[ip] = fingerprint;
            Save();
        }
    }

    private Dictionary<string, string> Load()
    {
        try
        {
            if (File.Exists(_path))
            {
                var json = File.ReadAllText(_path, Encoding.UTF8);
                return JsonSerializer.Deserialize<Dictionary<string, string>>(json) ?? new();
            }
        }
        catch { }
        return new();
    }

    private void Save()
    {
        try { File.WriteAllText(_path, JsonSerializer.Serialize(_map), Encoding.UTF8); }
        catch { /* 持久化失败不影响运行（下次 TOFU 重写） */ }
    }
}