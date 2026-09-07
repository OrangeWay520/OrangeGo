package com.orangeway.go.core

import java.net.InetAddress

/**
 * 一台已发现的设备。以 ip:port 作为去重键（mDNS 与 UDP 双通道可能同时看到同一设备）。
 * LocalSend 设备额外携带 fingerprint/protocol/deviceType/deviceModel，isLocalSend=true。
 */
data class Peer(
    val deviceId: String,   // UDP 通告的 deviceId；mDNS 通道则用服务实例名兜底；LocalSend 设备用 ogLS_ 前缀 + fingerprint
    val name: String,
    val ip: String,
    val port: Int = Protocol.Port,
    var lastSeen: Long = System.currentTimeMillis(),
    val isLocalSend: Boolean = false,
    val fingerprint: String = "",
    val protocol: String = "https",   // LocalSend 设备声明的服务协议（http/https），决定发送端用明文还是 mTLS
    val deviceType: String = "desktop",
    val deviceModel: String = ""
) {
    val key: String get() = "$ip:$port"
    val initial: String get() = name.trim().takeIf { it.isNotEmpty() }?.first()?.uppercase() ?: "?"
}

fun Peer.looksLike(address: InetAddress, port: Int) =
    this.port == port && this.ip == address.hostAddress
