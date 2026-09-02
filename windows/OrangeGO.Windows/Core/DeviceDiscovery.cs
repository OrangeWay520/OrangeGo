using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Text.Json;

namespace OrangeGO.Windows.Core;

/// <summary>
/// UDP 设备发现：周期性广播本机设备信息、监听同网段通告、维护在线设备表。
/// 协议见 OrangeGO/PROTOCOL.md「一、设备发现」。
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
    private readonly TimeSpan _offlineTimeout = TimeSpan.FromSeconds(15);

    public event Action<IReadOnlyCollection<PeerInfo>>? PeersChanged;

    public DiscoReader(Func<Protocol.DeviceInfo> identity)
    {
        _identity = identity;
        _listen.EnableBroadcast = true;
        _ = Task.Run(ListenLoopAsync);
        _ = Task.Run(BroadcastLoopAsync);
        _ = Task.Run(PruneLoopAsync);
    }

    /// <summary>立即广播一次（用于 UI 主动刷新）。</summary>
    public void BroadcastNow()
    {
        try { Broadcast(_identity(), broadcast: true); } catch { /* 忽略单次失败 */ }
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
        }
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
        Protocol.DeviceInfo? info;
        try
        {
            info = JsonSerializer.Deserialize<Protocol.DeviceInfo>(Encoding.UTF8.GetString(buffer));
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

    private async Task PruneLoopAsync()
    {
        using var timer = new PeriodicTimer(TimeSpan.FromSeconds(_offlineTimeout.TotalSeconds / 3));
        while (await timer.WaitForNextTickAsync(_cts.Token))
        {
            bool changed = false;
            lock (_lock)
            {
                var stale = _peers.Where(p => DateTime.UtcNow - p.Value.LastSeen > _offlineTimeout).Select(p => p.Key).ToList();
                foreach (var key in stale) { _peers.Remove(key); changed = true; }
                if (changed) PeersChanged?.Invoke(_peers.Values.ToList());
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        _listen.Dispose();
    }
}