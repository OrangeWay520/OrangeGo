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
import java.util.concurrent.TimeUnit

/** 待发送文件：名称、大小、以及按需打开的输入流（用于 SAF Uri）。 */
data class OutgoingFile(
    val id: String,
    val name: String,
    val size: Long,
    val open: () -> InputStream
)

/**
 * HTTP 传输客户端（发送端）。按 PROTOCOL.md 先 prepare 授权（v2，每文件独立 token）再逐个上传。
 */
class SenderClient(private val deviceId: String, private val deviceName: String) {

    private val json = "application/json; charset=utf-8".toMediaType()
    private val octet = "application/octet-stream".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    /** v2 prepare-upload：携带全部文件清单，接收方授权后返回 sessionId + 每文件 token；拒绝抛异常。 */
    suspend fun prepare(peerIp: String, peerPort: Int, sendId: String, files: List<OutgoingFile>): Protocol.PrepareResult =
        withContext(Dispatchers.IO) {
            val metas = files.associate { it.id to Protocol.FileMeta(id = it.id, fileName = it.name, size = it.size) }
            val payload = Protocol.SendInitPayload(
                sendId = sendId, deviceId = deviceId, name = deviceName,
                totalFiles = files.size, totalSize = files.sumOf { it.size }, files = metas
            )
            val req = Request.Builder()
                .url("http://$peerIp:$peerPort${Protocol.PATH_PREPARE}")
                .post(Protocol.initJson(payload).toRequestBody(json))
                .build()
            val resp = client.newCall(req).execute()
            try {
                val body = resp.body?.string().orEmpty()
                val parsed = Protocol.responseFromRaw(body)
                if (resp.code == 204 || resp.code in 200..299 && parsed?.messageType == Protocol.Ok) {
                    // 全部被拒绝时 204，files 为空；否则取 sessionId + files token 表
                    Protocol.PrepareResult(sessionId = parsed?.sessionId ?: sendId, tokens = parsed?.files ?: emptyMap())
                } else throw IOException(parsed?.info ?: "prepare 失败 (${resp.code})")
            } finally {
                resp.close()
            }
        }

    /** 上传单个文件（v2 走 query 传 sessionId/fileId/token）。 */
    suspend fun uploadFile(
        peerIp: String, peerPort: Int,
        sessionId: String, fileId: String, token: String,
        file: OutgoingFile,
        onProgress: (Double) -> Unit
    ) = withContext(Dispatchers.IO) {
        val body = ProgressBody(total = file.size, opener = { file.open() }, onFraction = onProgress)
        val url = "http://$peerIp:$peerPort${Protocol.PATH_UPLOAD}?sessionId=$sessionId&fileId=$fileId&token=$token"
        val req = Request.Builder()
            .url(url)
            .addHeader("X-FileName", java.net.URLEncoder.encode(file.name, Charsets.UTF_8.name()))
            .addHeader("X-FileSize", file.size.toString())
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        try {
            if (resp.code !in 200..299) {
                val parsed = Protocol.responseFromRaw(resp.body?.string().orEmpty())
                throw IOException(parsed?.info ?: "上传失败 (${resp.code})")
            }
        } finally {
            resp.close()
        }
    }

    /** cancel 与旧版单文件上传保留（v1 兼容/清理用）。 */
    suspend fun cancel(ip: String, port: Int, sendId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url("http://$ip:$port${Protocol.PATH_CANCEL}")
                    .post(Protocol.cancelJson(Protocol.CancelPayload(sendId = sendId)).toRequestBody(json))
                    .build()
                client.newCall(req).execute().close()
            }
        }
    }

    /** 发送文本消息（聊天气泡通道）。发到 PATH_MESSAGE，解析响应；失败抛异常。 */
    suspend fun sendMessage(ip: String, port: Int, msg: Protocol.TextMessage) {
        withContext(Dispatchers.IO) {
            val body = Protocol.textJson(msg).toRequestBody(json)
            val req = Request.Builder().url("http://$ip:$port${Protocol.PATH_MESSAGE}").post(body).build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    val parsed = Protocol.responseFromRaw(resp.body?.string().orEmpty())
                    throw IOException(parsed?.info ?: "消息发送失败 (${resp.code})")
                }
            }
        }
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
            // 空文件（或长度为 0）直接上报完整进度，避免除零；有内容则按 64KB 块写入
            if (total <= 0L) {
                opener().close()
                onFraction(1.0)
                return
            }
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
}