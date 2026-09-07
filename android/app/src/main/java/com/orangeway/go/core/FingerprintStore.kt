package com.orangeway.go.core

import android.content.Context

/**
 * 持久化指纹信任库（TOFU：首次连接信任并存储，后续连接校验一致性，防局域网 MITM）。
 * 按 ip 存储 TLS 证书 SHA-256 指纹；与 SSH known_hosts 同模型。
 */
class FingerprintStore(context: Context) {
    private val prefs = context.getSharedPreferences("ogo_fingerprints", Context.MODE_PRIVATE)

    init {
        // v2：电脑端证书已持久化，旧指纹会因不匹配导致 TOFU 误判，一次性清空
        if (prefs.getString("_version", "") != "v2") {
            prefs.edit().clear().putString("_version", "v2").apply()
        }
    }

    fun get(ip: String): String = prefs.getString(ip, "") ?: ""

    fun put(ip: String, fingerprint: String) {
        prefs.edit().putString(ip, fingerprint).apply()
    }
}