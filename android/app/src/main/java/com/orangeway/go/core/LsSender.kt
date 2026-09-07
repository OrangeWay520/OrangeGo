package com.orangeway.go.core

import kotlinx.coroutines.Dispatchers
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
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * LocalSend 发送客户端：向 iOS/安卓 LocalSend App 推文件。
 * 安全模型与 LocalSend 官方对齐：
 *   - HTTPS 目标：TLS 握手时出示本机自签客户端证书（LocalSend 服务端仅要求「证书有效」，不校验签发链），
 *     并按对端通告的 SHA-256 指纹钉扎校验服务端证书，防局域网中间人；
 *   - PIN：对端开启 PIN 时 prepare-upload 回 401，携带 ?pin= 重试；
 *   - 会话：prepare-upload(200) → 逐文件 upload?sessionId&fileId&token → 200；对端忙回 409、拒绝回 403。
 *
 * 对齐 Windows 端 `Core/LocalSendCompat.cs::LsSender`。
 */
class LsSender(
    private val alias: String,
    private val deviceModel: String,
    private val port: Int = Protocol.Port
) {
    /** 携带 HTTP 状态码的发送失败（UI 据此区分 PIN/拒绝/忙等场景）。 */
    class LsSendException(val statusCode: Int, message: String) : IOException(message)

    class PrepareResult(val sessionId: String, val tokens: Map<String, String>)

    private val json = "application/json; charset=utf-8".toMediaType()
    /** 按 目标ip:port:指纹 缓存的 OkHttpClient（指纹校验闭包按目标固定，连接可复用）。 */
    private val clients = ConcurrentHashMap<String, OkHttpClient>()

    /** prepare-upload：携带文件清单请求对端授权；返回 sessionId + fileId→token。 */
    suspend fun prepareUpload(
        ip: String, peerPort: Int, fingerprint: String, protocol: String,
        files: List<OutgoingFile>, pin: String? = null
    ): PrepareResult = withContext(Dispatchers.IO) {
        val fileIds = files.map { it.id }
        val filesObj = org.json.JSONObject()
        files.forEach { f ->
            filesObj.put(f.id, org.json.JSONObject().apply {
                put("id", f.id)
                put("fileName", f.name)
                put("size", f.size)
                put("fileType", mimeOf(f.name))
            })
        }
        val body = org.json.JSONObject().apply {
            put("info", org.json.JSONObject().apply {
                put("alias", alias)
                put("version", Protocol.LS_PROTOCOL_VERSION)
                put("deviceModel", deviceModel)
                put("deviceType", "mobile")
                put("fingerprint", LsCert.fingerprint())
                put("port", port)
                put("protocol", "https")
                put("download", false)
            })
            put("files", filesObj)
        }.toString()
        val url = "$protocol://$ip:$peerPort${Protocol.PATH_PREPARE}" +
            (if (!pin.isNullOrEmpty()) "?pin=${java.net.URLEncoder.encode(pin, "UTF-8")}" else "")
        val req = Request.Builder().url(url)
            .post(body.toByteArray(Charsets.UTF_8).toRequestBody(json)).build()
        val client = clientFor(ip, peerPort, fingerprint)
        client.newCall(req).execute().use { resp ->
            when (resp.code) {
                204 -> PrepareResult("", emptyMap()) // 对端判定无需传输
                401 -> throw LsSendException(401, "对方要求 PIN")
                403 -> throw LsSendException(403, "对方拒绝了本次发送")
                409 -> throw LsSendException(409, "对方忙（已有进行中的接收会话）")
                429 -> throw LsSendException(429, "PIN 试错过多，请稍后再试")
                else -> if (resp.code !in 200..299) throw LsSendException(resp.code, "prepare 失败 (${resp.code})")
            }
            parsePrepareResponse(resp.body?.string().orEmpty())
        }
    }

    private fun parsePrepareResponse(json: String): PrepareResult {
        val j = org.json.JSONObject(json)
        val sid = j.optString("sessionId")
        val tokens = LinkedHashMap<String, String>()
        val filesObj = j.optJSONObject("files")
        filesObj?.keys()?.forEach { id ->
            val t = filesObj.optString(id)
            if (t.isNotEmpty()) tokens[id] = t
        }
        return PrepareResult(sid, tokens)
    }

    /** 上传单文件到 LocalSend 会话（查询参数携带 sessionId/fileId/token），回调进度 0..1。 */
    suspend fun uploadFile(
        ip: String, peerPort: Int, fingerprint: String, protocol: String,
        sessionId: String, fileId: String, token: String,
        file: OutgoingFile, onProgress: (Double) -> Unit
    ) = withContext(Dispatchers.IO) {
        val body = ProgressBody(file.size, { file.open() }, onProgress)
        val url = "$protocol://$ip:$peerPort${Protocol.PATH_UPLOAD}?sessionId=$sessionId&fileId=$fileId&token=$token"
        val req = Request.Builder().url(url).post(body).build()
        val client = clientFor(ip, peerPort, fingerprint)
        client.newCall(req).execute().use { resp ->
            if (resp.code !in 200..299) throw LsSendException(resp.code, "上传失败 (${resp.code})")
        }
    }

    /** 取消进行中的 LocalSend 会话（发送端放弃时通知对端）。 */
    suspend fun cancel(ip: String, peerPort: Int, fingerprint: String, protocol: String, sessionId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$protocol://$ip:$peerPort${Protocol.PATH_LS_CANCEL}?sessionId=$sessionId"
                val req = Request.Builder().url(url)
                    .post(ByteArray(0).toRequestBody(json)).build()
                clientFor(ip, peerPort, fingerprint).newCall(req).execute().close()
            }
        }
    }

    /** 按 目标ip:port:指纹 获取/缓存 OkHttpClient（mTLS 客户端证书 + 指纹钉扎 TrustManager）。 */
    private fun clientFor(ip: String, peerPort: Int, fingerprint: String): OkHttpClient {
        val key = "$ip:$peerPort:$fingerprint"
        clients[key]?.let { return it }
        val pinnedTm = PinnedTrustManager(fingerprint)
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(keyManagers(), arrayOf(pinnedTm), SecureRandom())
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, pinnedTm)
            .hostnameVerifier { _, _ -> true } // 自签证书无 SAN，按指纹钉扎而非主机名
            .build()
        clients[key] = client
        return client
    }

    /** 构建本机客户端证书的 KeyManager（从 LsCert 取私钥+证书装入内存 KeyStore）。 */
    private fun keyManagers(): Array<javax.net.ssl.KeyManager> {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("ls", LsCert.privateKey(), CharArray(0), arrayOf(LsCert.certificate()))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))
        return kmf.keyManagers
    }

    /** 指纹钉扎 TrustManager：校验服务端证书 DER SHA-256 与对端通告 fingerprint 一致。 */
    private class PinnedTrustManager(private val expected: String) : X509TrustManager {
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            if (expected.isEmpty()) return // 无指纹则放行（降级）
            val cert = chain[0]
            val sha = LsCert.sha256HexUpper(cert.encoded)
            val want = expected.replace(":", "").replace(" ", "").uppercase()
            if (sha != want) throw CertificateException("fingerprint mismatch: $sha != $want")
        }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    /** 上报字节进度的 RequestBody。 */
    private class ProgressBody(
        private val total: Long,
        private val opener: () -> InputStream,
        private val onFraction: (Double) -> Unit
    ) : RequestBody() {
        override fun contentType() = null
        override fun contentLength(): Long = total
        override fun writeTo(sink: BufferedSink) {
            if (total <= 0L) { opener().close(); onFraction(1.0); return }
            var written = 0L
            opener().use { input ->
                val source = input.source()
                val buf = Buffer()
                while (true) {
                    val read = source.read(buf, 64 * 1024)
                    if (read == -1L) break
                    written += read
                    sink.write(buf, read)
                    onFraction(written.toDouble() / total)
                }
            }
        }
    }

    /** 常见扩展名 → MIME（对齐 Windows LsSender.MimeOf）。 */
    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "dng" -> "image/x-adobe-dng"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    fun dispose() {
        clients.values.forEach { runCatching { it.dispatcher.executorService.shutdown() } }
        clients.clear()
    }
}