using System.Net;
using System.Net.Sockets;
using Haukcode.Mdns;

namespace OrangeGO.Windows.Core;

/// <summary>
/// 真正的 mDNS / DNS-SD 发现（RFC 6762 / 6763）。
/// 广告 _orangego._tcp 服务（TXT 携带 deviceId、name、version），同时浏览同型服务。
/// 与 UDP 广播 DiscoReader 并存互补，均把结果喂给统一设备表。
/// </summary>
public sealed class MdnsDiscovery : IDisposable
{
    private readonly MdnsAdvertiser _advertiser;
    private readonly MdnsBrowser _browser;
    private readonly string _deviceId;
    private readonly Dictionary<string, DiscoReader.PeerInfo> _peers = new(); // deviceId → peer
    private readonly object _lock = new();

    /// <summary>设备表有 tervers 变化（新增/刷新）。</summary>
    public event Action<IReadOnlyCollection<DiscoReader.PeerInfo>>? PeersChanged;
    /// <summary>某个 deviceId 的设备被 mDNS 判定下线。</summary>
    public event Action<string>? PeerLost;

    public MdnsDiscovery(string deviceName, string deviceId, int port = Protocol.Port)
    {
        _deviceId = deviceId;

        var props = new Dictionary<string, string>
        {
            ["id"] = deviceId,
            ["name"] = deviceName,
            ["v"] = "1"
        };
        var profile = new ServiceProfile(deviceName, Protocol.ServiceType, (ushort)port, props);

        _advertiser = new MdnsAdvertiser(profile);
        _advertiser.Start();

        _browser = new MdnsBrowser(Protocol.ServiceType);
        _browser.ServiceFound += OnServiceFound;
        _browser.ServiceLost += OnServiceLost;
        _browser.Start();
    }

    private void OnServiceFound(ServiceProfile svc)
    {
        var id = svc.Properties.TryGetValue("id", out var txtId) ? txtId : svc.InstanceName;
        if (string.IsNullOrEmpty(id) || id == _deviceId) return; // 忽略自身

        var ip = PickIp(svc) ?? IPAddress.Loopback;
        lock (_lock)
        {
            _peers[id] = new DiscoReader.PeerInfo
            {
                DeviceId = id,
                Name = svc.InstanceName,
                Ip = ip,
                Port = svc.Port,
                LastSeen = DateTime.UtcNow
            };
            PeersChanged?.Invoke(_peers.Values.ToList());
        }
    }

    private void OnServiceLost(ServiceProfile svc)
    {
        var id = svc.Properties.TryGetValue("id", out var txtId) ? txtId : svc.InstanceName;
        if (string.IsNullOrEmpty(id)) return;
        lock (_lock)
        {
            if (_peers.Remove(id))
                PeerLost?.Invoke(id);
        }
    }

    /// <summary>优先取 IPv4 地址（传输走本机网卡，避免选到虚拟/多播地址）。</summary>
    private static IPAddress? PickIp(ServiceProfile svc)
    {
        foreach (var a in svc.Addresses ?? Enumerable.Empty<IPAddress>())
            if (a.AddressFamily == AddressFamily.InterNetwork) return a;
        return svc.Address;
    }

    public void Dispose()
    {
        _browser.ServiceFound -= OnServiceFound;
        _browser.ServiceLost -= OnServiceLost;
        _browser.Dispose();
        _advertiser.Dispose();
    }
}