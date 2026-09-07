package com.orangeway.go.core

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/** 待发送文件：名称、大小、以及按需打开的输入流（用于 SAF Uri）。 */
data class OutgoingFile(
    val id: String,
    val name: String,
    val size: Long,
    val open: () -> InputStream,
    val relativePath: String? = null,
    val thumb: String? = null
)

/**
 * HTTP 传输客户端（发送端）。按 PROTOCOL.md 先 prepare 授权（v2，每文件独立 token）再逐个上传。
 */
class SenderClient(
    private val deviceId: String,
    private val deviceName: String,
    private val fingerprintStore: FingerprintStore? = null
) {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val octet = "application/octet-stream".toMediaType()
    /** 按 ip:port 缓存的 OkHttpClient（TOFU TrustManager 按目标 IP 固定，连接可复用）。 */
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    /** 按 ip:port 获取/缓存 OkHttpClient（mTLS 客户端证书 + TOFU 指纹钉扎）。 */
    private fun clientFor(ip: String, port: Int): OkHttpClient {
        val key = "$ip:$port"
        clients[key]?.let { return it }
        val tm = TofuTrustManager(ip, fingerprintStore)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers(), arrayOf(tm), SecureRandom())
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(300, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, tm)
            .hostnameVerifier { _, _ -> true } // 自签证书无 SAN，按 mTLS + TOFU 指纹钉扎而非主机名
            .build()
        clients[key] = client
        return client
    }

    /** 可取消的 HTTP 执行：协程取消时调 call.cancel() 真正中断连接，而非依赖 Thread.interrupt()。 */
    private suspend fun executeCancellable(call: okhttp3.Call): okhttp3.Response =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : okhttp3.Callback {
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    cont.resume(response) { runCatching { response.close() } }
                }
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    cont.resumeWith(kotlin.Result.failure(e))
                }
            })
        }

    /** v2 prepare-upload：携带全部文件清单，接收方授权后返回 sessionId + 每文件 token；拒绝抛异常。 */
    suspend fun prepare(peerIp: String, peerPort: Int, sendId: String, files: List<OutgoingFile>): Protocol.PrepareResult =
        withContext(Dispatchers.IO) {
            val metas = files.associate { it.id to Protocol.FileMeta(id = it.id, fileName = it.name, size = it.size, relativePath = it.relativePath, thumb = it.thumb) }
            val payload = Protocol.SendInitPayload(
                sendId = sendId, deviceId = deviceId, name = deviceName,
                totalFiles = files.size, totalSize = files.sumOf { it.size }, files = metas
            )
            val req = Request.Builder()
                .url("https://$peerIp:$peerPort${Protocol.PATH_PREPARE}")
                .post(Protocol.initJson(payload).toRequestBody(json))
                .build()
            val resp = executeCancellable(clientFor(peerIp, peerPort).newCall(req))
            try {
                val body = resp.body?.string().orEmpty()
                val parsed = Protocol.responseFromRaw(body)
                if (resp.code == 204 || resp.code in 200..299 && parsed?.messageType == Protocol.Ok) {
                    Protocol.PrepareResult(sessionId = parsed?.sessionId ?: sendId, tokens = parsed?.files ?: emptyMap())
                } else throw IOException(parsed?.info ?: "prepare 失败 (${resp.code})")
            } finally {
                resp.close()
            }
        }

    /** 上传单个文件（v2 走 query 传 sessionId/fileId/token）。P2: 断点续传。P3: gzip 压缩。 */
    suspend fun uploadFile(
        peerIp: String, peerPort: Int,
        sessionId: String, fileId: String, token: String,
        file: OutgoingFile,
        onProgress: (Double) -> Unit
    ) = withContext(Dispatchers.IO) {
        // P2: HEAD 查已接收字节数（不能吞 CancellationException，否则取消信号丢失）
        val resumeOffset = try {
            val headReq = Request.Builder()
                .url("https://$peerIp:$peerPort${Protocol.PATH_UPLOAD}?sessionId=$sessionId&fileId=$fileId&token=$token")
                .head().build()
            executeCancellable(clientFor(peerIp, peerPort).newCall(headReq)).use { resp ->
                resp.header("X-Received-Bytes")?.toLongOrNull() ?: 0L
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            0L
        }
        // P3: 可压缩类型且非续传时 gzip 压缩上传
        val useGzip = resumeOffset == 0L && isCompressible(file.name) && file.size > 0
        val url = "https://$peerIp:$peerPort${Protocol.PATH_UPLOAD}?sessionId=$sessionId&fileId=$fileId&token=$token"
        val reqBuilder = Request.Builder().url(url)
            .addHeader("X-FileName", java.net.URLEncoder.encode(file.name, Charsets.UTF_8.name()))
            .addHeader("X-FileSize", file.size.toString())
        if (resumeOffset > 0) reqBuilder.addHeader("Content-Range", "bytes $resumeOffset-${file.size - 1}/${file.size}")
        if (useGzip) reqBuilder.addHeader("Content-Encoding", "gzip")
        if (useGzip) {
            val tmp = java.io.File.createTempFile("ogo_gz_", ".gz")
            try {
                java.util.zip.GZIPOutputStream(java.io.FileOutputStream(tmp)).use { gzOut ->
                    file.open().use { it.copyTo(gzOut) }
                }
                val compressedLen = tmp.length()
                val body = ProgressBody(total = compressedLen, opener = { tmp.inputStream() }, onFraction = onProgress)
                val req = reqBuilder.post(body).build()
                val resp = executeCancellable(clientFor(peerIp, peerPort).newCall(req))
                try {
                    if (resp.code !in 200..299) {
                        val parsed = Protocol.responseFromRaw(resp.body?.string().orEmpty())
                        throw IOException(parsed?.info ?: "上传失败 (${resp.code})")
                    }
                    onProgress(1.0)
                } finally {
                    resp.close()
                }
            } finally {
                tmp.delete()
            }
        } else {
            val body = ProgressBody(total = file.size, opener = { file.open() }, onFraction = onProgress, skipBytes = resumeOffset)
            val req = reqBuilder.post(body).build()
            val resp = executeCancellable(clientFor(peerIp, peerPort).newCall(req))
            try {
                if (resp.code !in 200..299) {
                    val parsed = Protocol.responseFromRaw(resp.body?.string().orEmpty())
                    throw IOException(parsed?.info ?: "上传失败 (${resp.code})")
                }
                onProgress(1.0)
            } finally {
                resp.close()
            }
        }
    }

    /** cancel 与旧版单文件上传保留（v1 兼容/清理用）。 */
    suspend fun cancel(ip: String, port: Int, sendId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://$ip:$port${Protocol.PATH_CANCEL}")
                    .post(Protocol.cancelJson(Protocol.CancelPayload(sendId = sendId)).toRequestBody(json))
                    .build()
                clientFor(ip, port).newCall(req).execute().close()
            }
        }
    }

    /** 发送撤回命令到对端（携带被撤回的 sendId）。 */
    suspend fun sendRecall(ip: String, port: Int, sendId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("https://$ip:$port${Protocol.PATH_RECALL}")
                    .post(Protocol.recallJson(Protocol.RecallPayload(sendId = sendId)).toRequestBody(json))
                    .build()
                clientFor(ip, port).newCall(req).execute().close()
            }
        }
    }

    /** 发送文本消息（聊天气泡通道）。发到 PATH_MESSAGE，解析响应；失败抛异常。 */
    suspend fun sendMessage(ip: String, port: Int, msg: Protocol.TextMessage) {
        withContext(Dispatchers.IO) {
            val body = Protocol.textJson(msg).toRequestBody(json)
            val req = Request.Builder().url("https://$ip:$port${Protocol.PATH_MESSAGE}").post(body).build()
            clientFor(ip, port).newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    val parsed = Protocol.responseFromRaw(resp.body?.string().orEmpty())
                    throw IOException(parsed?.info ?: "消息发送失败 (${resp.code})")
                }
            }
        }
    }

    /** P3: 判断文件类型是否值得 gzip 压缩（文本/代码/文档类；已压缩的图片/视频/归档不压缩）。 */
    private fun isCompressible(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in setOf(
            "txt", "json", "xml", "csv", "log", "md", "js", "ts", "java", "kt", "py", "go", "cs", "c", "cpp", "h", "rs",
            "html", "css", "svg", "rtf", "doc", "xls", "ppt", "yaml", "yml", "ini", "cfg", "conf", "sh", "bat", "ps1"
        )
    }

    fun dispose() {
        clients.values.forEach { runCatching { it.dispatcher.executorService.shutdown() } }
        clients.clear()
    }

    /** 构建本机客户端证书的 KeyManager（从 LsCert 取私钥+证书装入内存 KeyStore，供 mTLS 握手出示）。 */
    private fun keyManagers(): Array<javax.net.ssl.KeyManager> {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("ogo", LsCert.privateKey(), CharArray(0), arrayOf(LsCert.certificate()))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))
        return kmf.keyManagers
    }

    /**
     * TOFU 指纹钉扎 TrustManager：
     * 首次连接 → 信任并持久化服务端证书 SHA-256；后续连接 → 校验一致性，不匹配则抛异常（防 MITM）。
     * 无 FingerprintStore 时退化为信任所有（仅靠 mTLS 加密+双向认证）。
     */
    private class TofuTrustManager(private val ip: String, private val store: FingerprintStore?) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (store == null) return // 无持久化库则放行（mTLS 已防窃听/篡改）
            val sha = LsCert.sha256HexUpper(chain[0].encoded)
            val stored = store.get(ip)
            if (stored.isEmpty()) store.put(ip, sha) // TOFU：首次信任并存储
            else if (sha != stored) throw CertificateException("fingerprint mismatch for $ip: $sha != $stored")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** 上报字节进度的 RequestBody。P2: skipBytes 跳过已传部分（断点续传）。 */
    private class ProgressBody(
        private val total: Long,
        private val opener: () -> InputStream,
        private val onFraction: (Double) -> Unit,
        private val skipBytes: Long = 0L
    ) : RequestBody() {
        override fun contentType() = null
        override fun contentLength(): Long = (total - skipBytes).coerceAtLeast(0)
        override fun writeTo(sink: BufferedSink) {
            if (total <= 0L) {
                opener().close()
                return
            }
            var written = skipBytes // P2: 从偏移量起算进度
            opener().use { input ->
                if (skipBytes > 0) input.skip(skipBytes) // P2: 跳过已传部分
                val source = input.source()
                val buf = Buffer()
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException("上传已取消")
                    val read = source.read(buf, 256 * 1024)
                    if (read == -1L) break
                    written += read
                    sink.write(buf, read)
                    if (written < total) onFraction(written.toDouble() / total)
                }
            }
        }
    }
}