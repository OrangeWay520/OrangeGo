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

/** 设备发现：mDNS(NsdManager) 广告+浏览 + UDP 广播 + LocalSend 224.0.0.167 组播，三通道结果合并进同一设备表。 */
class DiscoveryManager(
    private val context: Context,
    private val deviceId: String,
    private val deviceName: String,
    // LocalSend 组播通告所需的本机身份（延迟求值，fingerprint 在 LsCert.init 后才有）
    private val localFingerprint: () -> String = { "" },
    private val localDeviceModel: () -> String = { "Android" }
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
            val ms = java.net.MulticastSocket(Protocol.Port).also { it.reuseAddress = true; it.broadcast = true }
            socket = ms
            // 指定出口接口（多网卡时确保从 selfIp 对应网卡收发）
            selfIp?.let { runCatching { ms.networkInterface = java.net.NetworkInterface.getByInetAddress(it) } }
            // 加入 LocalSend 组播组 224.0.0.167（与官方 multicast 对齐）
            runCatching {
                val group = InetAddress.getByName(Protocol.LS_MULTICAST_IP)
                ms.joinGroup(java.net.InetSocketAddress(group, Protocol.LS_MULTICAST_PORT), ms.networkInterface)
            }
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
                val ip = p.address
                if (ip !is java.net.Inet4Address) continue
                val ipStr = ip.hostAddress ?: continue
                if (ipStr == selfIp?.hostAddress) continue
                // 判别报文类型：LocalSend presence（有 fingerprint）vs OrangeGo 发现报文（有 messageType）
                val lsDev = Protocol.parseLsDevice(raw)
                if (lsDev != null && lsDev.fingerprint.isNotEmpty()) {
                    Log.i(TAG, "LS presence 收包 from $ipStr alias=${lsDev.alias} fp=${lsDev.fingerprint.take(8)} port=${lsDev.port} proto=${lsDev.protocol}")
                    upsert(Peer(
                        deviceId = "ogLS_" + lsDev.fingerprint,
                        name = lsDev.alias.ifEmpty { "LocalSend 设备" },
                        ip = ipStr,
                        port = if (lsDev.port > 0) lsDev.port else Protocol.Port,
                        isLocalSend = true,
                        fingerprint = lsDev.fingerprint,
                        protocol = lsDev.protocol.ifEmpty { "https" },
                        deviceType = lsDev.deviceType ?: "mobile",
                        deviceModel = lsDev.deviceModel ?: ""
                    ))
                    continue
                }
                val info = Protocol.deviceFromRaw(raw) ?: continue
                if (info.deviceId == deviceId) continue
                if (info.messageType == Protocol.DiscoveryRequest) {
                    // 收到广播请求 → 单播回应自己
                    val resp = Protocol.deviceJson(
                        Protocol.DeviceInfo(messageType = Protocol.DiscoveryResponse, deviceId = deviceId, name = deviceName)
                    ).toByteArray(Charsets.UTF_8)
                    runCatching { s.send(DatagramPacket(resp, resp.size, p.address, p.port)) }
                }
                Log.i(TAG, "UDP 收包 from $ipStr/${info.port} mtype=${info.messageType}")
                upsert(Peer(info.deviceId, info.name, ipStr, info.port))
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
        val s = socket ?: return
        // OrangeGo 自研：255.255.255.255 有限广播
        runCatching {
            val payload = Protocol.deviceJson(
                Protocol.DeviceInfo(deviceId = deviceId, name = deviceName)
            ).toByteArray(Charsets.UTF_8)
            s.send(DatagramPacket(payload, payload.size, InetAddress.getByName("255.255.255.255"), Protocol.Port))
        }
        // LocalSend：224.0.0.167 组播 presence（有 fingerprint 才发，避免启动早期 LsCert 未初始化）
        val fp = localFingerprint()
        if (fp.isNotEmpty()) {
            runCatching {
                val presence = Protocol.buildLsPresenceJson(deviceName, fp, localDeviceModel(), Protocol.Port)
                    .toByteArray(Charsets.UTF_8)
                val group = InetAddress.getByName(Protocol.LS_MULTICAST_IP)
                s.send(DatagramPacket(presence, presence.size, group, Protocol.LS_MULTICAST_PORT))
            }
        }
    }

    private fun upsert(peer: Peer) {
        synchronized(peers) {
            // 同一台机器可能同时发 OrangeGo 通告（mDNS/广播）和 LocalSend 组播 presence，
            // 两者 deviceId 不同（og_xxx vs ogLS_fp）但 IP 相同。以 IP 为同机判定，
            // OrangeGo 身份优先（功能更全：支持文字/撤回），避免同一台 OrangeGo 设备显示成两条。
            val byIp = peers.values.firstOrNull { it.ip == peer.ip }
            if (byIp != null && byIp.deviceId != peer.deviceId) {
                // 同 IP 已有不同 deviceId 条目（双协议通告）
                if (!peer.isLocalSend) {
                    // 新通告是 OrangeGo → 覆盖旧条目（无论旧的 OrangeGo 还是 LocalSend）
                    peers.remove(byIp.deviceId)
                    peers[peer.deviceId] = peer
                } else if (byIp.isLocalSend) {
                    // 新旧都是 LocalSend → 刷新
                    peers[byIp.deviceId] = peer.copy(lastSeen = System.currentTimeMillis())
                }
                // else: 新通告是 LocalSend，旧的是 OrangeGo → 跳过（OrangeGo 优先）
            } else if (byIp != null) {
                // 同 IP 同 deviceId（多通道）→ 刷新
                peers[peer.deviceId] = peer.copy(lastSeen = System.currentTimeMillis())
            } else {
                peers[peer.deviceId] = peer
            }
            // 清理过期条目（TTL 15 秒，与 ViewModel.pruneLoop 对齐）。
            // 否则 emitSnapshot 会把已离线设备反复推给 ViewModel.onChanged（合并语义），
            // 与 pruneLoop 形成"加-删"循环造成 UI 闪烁（由其他设备的周期广播触发 emitSnapshot 放大）。
            val cutoff = System.currentTimeMillis() - 15_000
            peers.entries.removeIf { it.value.lastSeen < cutoff }
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