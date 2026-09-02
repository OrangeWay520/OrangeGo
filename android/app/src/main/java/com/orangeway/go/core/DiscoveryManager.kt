package com.orangeway.go.core

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/** 设备发现：mDNS(NsdManager) 广告+浏览 + UDP 广播，双通道结果合并进同一设备表。 */
class DiscoveryManager(
    private val context: Context,
    private val deviceId: String,
    private val deviceName: String
) {
    private val peers = LinkedHashMap<String, Peer>()
    private val main = Handler(Looper.getMainLooper())
    private var nsdManager: NsdManager? = null
    private var registered = false
    private var discovering = false
    private var multicastLock: WifiManager.MulticastLock? = null
    private val pool = Executors.newSingleThreadExecutor()
    // UDP 周期广播用独立线程：公共 pool 被 udpLoop 永久阻塞，无法承载周期广播
    private val announcer = Executors.newSingleThreadExecutor()
    private val selfIp = localIp()

    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var running = false

    var onChanged: ((List<Peer>) -> Unit)? = null
    var onLost: ((String) -> Unit)? = null

    fun start() {
        if (running) return
        running = true
        Log.i(TAG, "Discovery start: deviceId=$deviceId deviceName=$deviceName selfIp=${selfIp?.hostAddress}")
        try {
            multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("orangego-mdns").apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {}
        nsdManager = context.getSystemService(NsdManager::class.java)
        registerService()
        startDiscovery()
        pool.execute { udpLoop() }
        // 周期广播走独立线程，避免被 udpLoop 阻塞；启动时先立即广播一次
        announcer.submit { announceLoop() }
    }

    fun dispose() {
        running = false
        try { socket?.close() } catch (_: Throwable) {}
        pool.shutdown()
        announcer.shutdownNow()
        nsdManager?.let { nsd ->
            if (registered) runCatching { nsd.unregisterService(registerListener) }
            if (discovering) runCatching { nsd.stopServiceDiscovery(discoverListener) }
        }
        multicastLock?.let { runCatching { it.release() } }
    }

    fun broadcastNow() { pool.execute { broadcast() } }

    // ---------- mDNS ----------
    private fun registerService() {
        val nsd = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = "${Protocol.ServiceType}."
            port = Protocol.Port
            // TXT 记录携带 id/name/v，供对端跨通道以 deviceId 去重
            setAttribute("id", deviceId)
            setAttribute("name", deviceName)
            setAttribute("v", "1")
        }
        runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registerListener)
        }
    }

    private val registerListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(info: NsdServiceInfo) { registered = true }
        override fun onServiceUnregistered(info: NsdServiceInfo) { registered = false }
        override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) { Log.w(TAG, "registration failed $error") }
        override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
    }

    private fun startDiscovery() {
        val nsd = nsdManager ?: return
        runCatching {
            nsd.discoverServices("${Protocol.ServiceType}.", NsdManager.PROTOCOL_DNS_SD, discoverListener)
        }.onSuccess { discovering = true }
    }

    private val discoverListener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(info: NsdServiceInfo) {
            if (info.serviceName == deviceName) return // 自己
            resolveService(info)
        }
        override fun onServiceLost(info: NsdServiceInfo) { /* 由超时统一清理 */ }
        override fun onDiscoveryStarted(t: String) {}
        override fun onDiscoveryStopped(t: String) { discovering = false }
        override fun onStartDiscoveryFailed(t: String, e: Int) {}
        override fun onStopDiscoveryFailed(t: String, e: Int) {}
    }

    private fun resolveService(info: NsdServiceInfo) {
        val nsd = nsdManager ?: return
        runCatching {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(si: NsdServiceInfo) {
                    val host = pickRoutableV4(si.host) ?: return // 取不到可路由 IPv4 则跳过，交给 UDP 通道兜底
                    if (host.hostAddress == selfIp?.hostAddress) return // 自己
                    val id = deviceIdFromTxt(si) ?: si.serviceName // 优先 TXT 里的 id，跨通道去重用
                    upsert(Peer(id, si.serviceName, host.hostAddress ?: "", si.port))
                }
                override fun onResolveFailed(si: NsdServiceInfo, e: Int) {}
            })
        }
    }

    // ---------- UDP 广播 ----------
    private val buffer = ByteArray(4096)

    private fun udpLoop() {
        try {
            socket = DatagramSocket(Protocol.Port).also { it.reuseAddress = true; it.broadcast = true }
            Log.i(TAG, "UDP 53317 bind success, local=${socket?.localSocketAddress}")
        } catch (e: Throwable) {
            Log.w(TAG, "udp bind fail", e)
            return
        }
        val s = socket ?: return
        while (running) {
            try {
                val p = DatagramPacket(buffer, buffer.size)
                s.receive(p)
                val raw = String(p.data, p.offset, p.length, Charsets.UTF_8)
                val info = Protocol.deviceFromRaw(raw) ?: continue
                if (info.deviceId == deviceId) continue
                if (info.messageType == Protocol.DiscoveryRequest) {
                    // 收到广播请求 → 单播回应自己
                    val resp = Protocol.deviceJson(
                        Protocol.DeviceInfo(messageType = Protocol.DiscoveryResponse, deviceId = deviceId, name = deviceName)
                    ).toByteArray(Charsets.UTF_8)
                    runCatching { s.send(DatagramPacket(resp, resp.size, p.address, p.port)) }
                }
                val ip = p.address
                if (ip is java.net.Inet4Address) {
                    val ipStr = ip.hostAddress
                    if (ipStr != null && ipStr != selfIp?.hostAddress) {
                        Log.i(TAG, "UDP 收包 from $ipStr/${info.port} mtype=${info.messageType}")
                        upsert(Peer(info.deviceId, info.name, ipStr, info.port))
                    }
                }
            } catch (e: Throwable) {
                if (!running) break
            }
        }
    }

    // 周期广播：每 4 秒发一次广播通告，持续保活让对方即使掉 mDNS 也能保持在线
    private fun announceLoop() {
        try {
            while (running) {
                broadcast()
                Thread.sleep(4000)
            }
        } catch (_: Throwable) {}
    }

    private fun broadcast() {
        val payload = Protocol.deviceJson(
            Protocol.DeviceInfo(deviceId = deviceId, name = deviceName)
        ).toByteArray(Charsets.UTF_8)
        val s = socket ?: return
        runCatching {
            s.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), Protocol.Port))
        }
    }

    private fun upsert(peer: Peer) {
        synchronized(peers) {
            // 以 deviceId 为唯一键去重合并：同一设备的多个地址/双通道统一成一条，避免出现同名重复设备
            val exist = peers[peer.deviceId]
            if (exist == null || !exist.key.equals(peer.key, ignoreCase = true)) {
                peers[peer.deviceId] = peer
            } else {
                peers[peer.deviceId] = peer.copy(lastSeen = System.currentTimeMillis())
            }
        }
        emitSnapshot()
    }

    private fun emitSnapshot() {
        val snapshot = synchronized(peers) { peers.values.toList() }
        main.post { onChanged?.invoke(snapshot) }
    }

    private fun localIp(): InetAddress? = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { it is InetAddress && it.address.size == 4 }
    }.getOrNull()

    /** 只取可路由的 IPv4 地址，排除 IPv6(链路本地/回环)与虚拟网卡地址，保证后续 HTTP 传输可达。 */
    private fun pickRoutableV4(addr: InetAddress?): InetAddress? {
        if (addr is java.net.Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) return addr
        return null
    }

    /** 从 mDNS TXT 记录里读取 id（我们协议携带 id=xxx），供跨通道去重用；读不到返回 null。 */
    private fun deviceIdFromTxt(si: NsdServiceInfo): String? = runCatching {
        si.attributes?.get("id")?.let { arr ->
            val bytes = ByteArray(arr.size) { arr[it] }
            String(bytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private companion object {
        const val TAG = "OrangeGO.Discovery"
    }
}