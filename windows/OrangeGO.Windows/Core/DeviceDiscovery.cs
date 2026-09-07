using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>
/// UDP 设备发现：周期性广播本机设备信息、监听同网段通告、维护在线设备表。
/// 协议见 OrangeGO/PROTOCOL.md「一、设备发现」。
/// 同时承载 LocalSend 官方组播发现：本类唯一持有 53317 UDP socket（复用端口，避免 10048），
/// 加入 224.0.0.167 组播组收 LocalSend 存在报文、向该组广播本机存在。
/// </summary>
public sealed class DiscoReader : IDisposable
{
    public sealed class PeerInfo
    {
        public required string DeviceId { get; set; }
        public required string Name { get; set; }
        public required IPAddress Ip { get; set; }
        public int Port { get; set; } = Protocol.Port;
        public DateTime LastSeen { get; set; }
        public override string ToString() => $"{Name} ({Ip}:{Port})";
    }

    private readonly UdpClient _listen = new(new IPEndPoint(IPAddress.Any, Protocol.Port));
    private readonly CancellationTokenSource _cts = new();
    private readonly Dictionary<string, PeerInfo> _peers = new();
    private readonly object _lock = new();
    private readonly Func<Protocol.DeviceInfo> _identity;
    private readonly TimeSpan _broadcastInterval = TimeSpan.FromSeconds(5);
    /// <summary>离线判定阈值：需超过数个广播周期仍未收到才移除，避免单包丢失导致设备卡闪跳。
    /// 旧值 15s 与部分设备（尤其安卓）的广播间隔相近，会出现“消失→重现”循环。</summary>
    private readonly TimeSpan _offlineTimeout = TimeSpan.FromSeconds(45);
    /// <summary>LocalSend 设备广播较稀疏，离线容忍放大，避免在 OrangeGo 端闪跳。</summary>
    private readonly TimeSpan _lsOfflineTimeout = TimeSpan.FromSeconds(60);

    // ===== LocalSend 发现 =====
    private readonly IPAddress _lsGroup = IPAddress.Parse(LsDiscovery.MulticastIp);
    private readonly Dictionary<string, LocalSendDevice> _lsPeers = new();
    /// <summary>LocalSend 存在广播所需的本机信息（由 MainWindow 构造时注入；空则不广播）。</summary>
    public string LocalSendAlias { get; set; } = "";
    public string LocalSendFingerprint { get; set; } = "";
    public string LocalSendDeviceModel { get; set; } = "";
    /// <summary>新确认/更新的 LocalSend 在线设备快照。现场序调用，注意用 Dispatcher 回 UI 线程。</summary>
    public event Action<IReadOnlyCollection<LocalSendDevice>>? LocalSendPeersChanged;

    public event Action<IReadOnlyCollection<PeerInfo>>? PeersChanged;

    public DiscoReader(Func<Protocol.DeviceInfo> identity)
    {
        _identity = identity;
        _listen.EnableBroadcast = true;
        // 加入 LocalSend 组播组（复用本 socket，不能再新开 53317）
        foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
        {
            if (ni.OperationalStatus != OperationalStatus.Up) continue;
            if (ni.NetworkInterfaceType is NetworkInterfaceType.Loopback or NetworkInterfaceType.Tunnel) continue;
            try { _listen.JoinMulticastGroup(_lsGroup, ni.GetIPProperties().GetIPv4Properties().Index); } catch { }
        }
        _ = Task.Run(ListenLoopAsync);
        _ = Task.Run(BroadcastLoopAsync);
        _ = Task.Run(PruneLoopAsync);
    }

    /// <summary>立即广播一次（OrangeGO 通告 + LocalSend 存在），用于 UI 主动刷新。</summary>
    public void BroadcastNow()
    {
        try { Broadcast(_identity(), broadcast: true); } catch { /* 忽略单次失败 */ }
        BroadcastLocalSend();
    }

    private void Broadcast(Protocol.DeviceInfo info, bool broadcast)
    {
        var json = JsonSerializer.Serialize(info);
        var bytes = Encoding.UTF8.GetBytes(json);
        if (broadcast)
            _listen.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Broadcast, Protocol.Port));
        else
            _listen.Send(bytes, bytes.Length, new IPEndPoint(IPAddress.Parse("239.255.255.250"), Protocol.Port));
    }

    private async Task BroadcastLoopAsync()
    {
        using var timer = new PeriodicTimer(_broadcastInterval);
        while (await timer.WaitForNextTickAsync(_cts.Token))
        {
            Broadcast(_identity(), broadcast: true);
            Broadcast(_identity(), broadcast: false);
            BroadcastLocalSend();
        }
    }

    /// <summary>
    /// 向 LocalSend 组播组广播本机存在。按官方实现（core/multicast/socket.rs）：
    /// 每块网卡一个独立发送 socket，并用 IP_MULTICAST_IF 把出口钉死在该网卡——
    /// 否则由路由表决定出口，虚拟网卡（Wi-Fi Direct 等）会吞掉组播包。
    /// 发送 socket 用临时端口即可：对端只认报文里的 port 字段，不关心 UDP 源端口。
    /// </summary>
    private void BroadcastLocalSend()
    {
        if (string.IsNullOrEmpty(LocalSendFingerprint) || string.IsNullOrEmpty(LocalSendAlias)) return;
        var json = LsDiscovery.BuildPresenceJson(LocalSendAlias, LocalSendFingerprint, LocalSendDeviceModel, Protocol.Port);
        var data = Encoding.UTF8.GetBytes(json);
        var group = new IPEndPoint(_lsGroup, LsDiscovery.MulticastPort);
        var fail = new StringBuilder();
        foreach (var addrStr in LsDiscovery.GetLocalIpv4())
        {
            if (!IPAddress.TryParse(addrStr, out var addr)) continue;
            try
            {
                using var s = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);
                s.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastTimeToLive, 1);
                // 官方 socket2 同款：按网卡 IPv4 地址（网络字节序）指定组播出口
                s.SetSocketOption(SocketOptionLevel.IP, SocketOptionName.MulticastInterface, addr.GetAddressBytes());
                s.SendTo(data, group);
            }
            catch (Exception ex)
            {
                fail.Append(addr).Append(':').Append(ex.Message).Append(' ');
            }
        }
        if (fail.Length > 0) LsDiscovery.LogDiag("组播发送失败 " + fail);
    }

    private async Task ListenLoopAsync()
    {
        while (!_cts.IsCancellationRequested)
        {
            try
            {
                var result = await _listen.ReceiveAsync(_cts.Token);
                HandlePacket(result.RemoteEndPoint, result.Buffer);
            }
            catch (OperationCanceledException) { break; }
            catch { /* 忽略单包异常 */ }
        }
    }

    private void HandlePacket(IPEndPoint remote, byte[] buffer)
    {
        var text = Encoding.UTF8.GetString(buffer);

        // LocalSend 组播存在报文：无 OrangeGO deviceId，但含 fingerprint+alias → 单独处理
        if (!text.Contains("\"deviceId\"", StringComparison.Ordinal))
        {
            var ls = LsDiscovery.ParsePresence(text);
            if (ls is not null && !string.IsNullOrEmpty(ls.Fingerprint) && ls.Fingerprint != LocalSendFingerprint)
            {
                ls.Ip = IPAddress.IsLoopback(remote.Address) ? IPAddress.Loopback : remote.Address;
                ls.LastSeen = DateTime.UtcNow;
                HandleLocalSend(ls);
                return;
            }
        }

        Protocol.DeviceInfo? info;
        try
        {
            info = JsonSerializer.Deserialize<Protocol.DeviceInfo>(text);
        }
        catch { return; }

        if (info is null || string.IsNullOrEmpty(info.DeviceId)) return;

        // 自己
        if (info.DeviceId == _identity().DeviceId) return;

        // 收到广播请求 → 单播响应（端口可达性确认）
        if (info.MessageType == Protocol.DiscoveryRequest)
        {
            var resp = JsonSerializer.Serialize(_identity());
            var bytes = Encoding.UTF8.GetBytes(resp);
            try { _listen.Send(bytes, bytes.Length, remote); } catch { }
        }

        lock (_lock)
        {
            _peers[info.DeviceId] = new PeerInfo
            {
                DeviceId = info.DeviceId,
                Name = info.Name,
                Ip = IPAddress.IsLoopback(remote.Address) ? IPAddress.Loopback : remote.Address,
                Port = info.Port,
                LastSeen = DateTime.UtcNow
            };
            var snapshot = _peers.Values.ToList();
            PeersChanged?.Invoke(snapshot);
        }
    }

    /// <summary>记录/更新一个本地检测到的 LocalSend 设备并通知 UI。</summary>
    private void HandleLocalSend(LocalSendDevice d)
    {
        lock (_lock)
        {
            _lsPeers[d.Fingerprint] = d;
            LocalSendPeersChanged?.Invoke(_lsPeers.Values.ToList());
        }
    }

    private async Task PruneLoopAsync()
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(_offlineTimeout.TotalSeconds / 3));
        while (await timer.WaitForNextTickAsync(_cts.Token))
        {
            lock (_lock)
            {
                var stale = _peers.Where(p => DateTime.UtcNow - p.Value.LastSeen > _offlineTimeout).Select(p => p.Key).ToList();
                foreach (var key in stale) { _peers.Remove(key); }
                if (stale.Count > 0) PeersChanged?.Invoke(_peers.Values.ToList());

                var staleLs = _lsPeers.Where(p => DateTime.UtcNow - p.Value.LastSeen > _lsOfflineTimeout).Select(p => p.Key).ToList();
                foreach (var key in staleLs) { _lsPeers.Remove(key); }
                if (staleLs.Count > 0) LocalSendPeersChanged?.Invoke(_lsPeers.Values.ToList());
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _listen.Dispose();
    }
}