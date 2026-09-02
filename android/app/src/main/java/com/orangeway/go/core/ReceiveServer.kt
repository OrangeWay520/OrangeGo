package com.orangeway.go.core

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.concurrent.Executors

/**
 * HTTP 接收服务器（ServerSocket 手写 HTTP）。监听 53317，处理 init / file / cancel。
 * 协议见 OrangeGO/PROTOCOL.md「二、传输」。
 */
class ReceiveServer(
    private val saveDirProvider: () -> String?,
    private val onIncoming: (Protocol.SendInitPayload) -> String?,
    private val onSaved: ((String) -> Unit)? = null, // 单文件落盘成功，回传实际保存路径
    private val onMessage: ((Protocol.TextMessage) -> Unit)? = null // 收到文本消息（聊天气泡）
) {
    // 会话：sendId → 会话。v2 下每个文件有独立 token（fileId→token），v1 仅兼容单 token。
    private data class Session(
        val sendId: String,
        val token: String,
        val saveDir: String,
        val fileTokens: Map<String, String> = emptyMap(), // fileId → token (v2)
        val createdAt: Long = System.currentTimeMillis()
    )

    private val sessions = HashMap<String, Session>() // sendId → session
    private val acceptor = Executors.newSingleThreadExecutor()
    private val workers = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        acceptor.execute {
            try {
                val s = ServerSocket(Protocol.Port).also { running = true; server = it }
                Log.i(TAG, "TCP 53317 监听成功 (${s.localSocketAddress})")
                while (running) {
                    val client = try { s.accept() } catch (_: Exception) { break }
                    workers.execute { handle(client) }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "server stop", e)
                running = false
            }
        }
        // 周期清理长期未完成的会话，防止 sessions 永久增长（内存泄漏）
        workers.execute {
            while (running) {
                try {
                    Thread.sleep(60_000)
                    val cutoff = System.currentTimeMillis() - SESSION_TTL_MS
                    synchronized(sessions) {
                        val it = sessions.entries.iterator()
                        while (it.hasNext()) {
                            if (it.next().value.createdAt < cutoff) it.remove()
                        }
                    }
                } catch (_: Throwable) { if (!running) break }
            }
        }
    }

    fun stop() {
        running = false
        try { server?.close() } catch (_: Throwable) {}
        acceptor.shutdown()
        workers.shutdown()
    }

    // ---------- 连接处理 ----------
    private fun handle(socket: Socket) {
        Log.d(TAG, "收到连接 from ${socket.inetAddress?.hostAddress}")
        try {
            socket.soTimeout = 60_000
            val input = socket.inputStream
            val header = readHeader(input) ?: return
            val reqLine = header.first
            val headers = header.second
            val parts = reqLine.split(' ')
            val rawPath = if (parts.size >= 2) parts[1] else "/"
            // 分离路径与查询串（v2 upload 把 sessionId/fileId/token 放 URL 查询里）
            val qIdx = rawPath.indexOf('?')
            val path = if (qIdx >= 0) rawPath.substring(0, qIdx) else rawPath
            val query = if (qIdx >= 0) rawPath.substring(qIdx + 1) else ""
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

            // v2 upload 或 v1 file 都走原始流（不预读 body）
            if (parts.firstOrNull() == "POST" && (path == Protocol.PATH_UPLOAD || path == Protocol.PATH_FILE)) {
                serveUpload(path, query, socket, headers, input, contentLength)
                return
            }
            val body = if (contentLength > 0) readFully(input, contentLength) else null

            when {
                parts.firstOrNull() == "POST" && (path == Protocol.PATH_PREPARE || path == Protocol.PATH_INIT) ->
                    serveInit(socket, body)
                parts.firstOrNull() == "POST" && path == Protocol.PATH_CANCEL ->
                    serveCancel(socket, body)
                parts.firstOrNull() == "POST" && path == Protocol.PATH_MESSAGE ->
                    serveMessage(socket, body)
                else -> writeJson(socket, 404, Protocol.Response(Protocol.Error, info = "not found"))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "handle fail", e)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun serveInit(socket: Socket, body: ByteArray?) {
        Log.d(TAG, "serveInit 收到初始化请求")
        val payload = runCatching {
            val j = org.json.JSONObject(String(body ?: return writeJson(socket, 400,
                Protocol.Response(Protocol.Error, info = "no body"))))
            // 解析文件清单（v2 可选；v1 无 files）
            val files = LinkedHashMap<String, Protocol.FileMeta>()
            j.optJSONObject("files")?.keys()?.forEach { id ->
                val f = j.optJSONObject("files").getJSONObject(id)
                files[id] = Protocol.FileMeta(
                    id = id,
                    fileName = f.optString("fileName"),
                    size = f.optLong("size"),
                    fileType = f.optString("fileType", "application/octet-stream"),
                    sha256 = f.optString("sha256").ifEmpty { null }
                )
            }
            Protocol.SendInitPayload(
                sendId = j.optString("sendId"),
                deviceId = j.optString("deviceId"),
                name = j.optString("name"),
                totalFiles = j.optInt("totalFiles"),
                totalSize = j.optLong("totalSize"),
                files = files
            )
        }.getOrNull() ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "bad body"))

        if (payload.sendId.isEmpty()) return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "bad sendId"))

        // 复用已存在会话
        synchronized(sessions) { sessions[payload.sendId] }?.let { existing ->
            writeJson(socket, 200, makeInitResponse(payload.sendId, existing))
            return
        }

        val saveDir = onIncoming(payload) // 同步等待 UI 决定
        if (saveDir == null) {
            writeJson(socket, 403, Protocol.Response(Protocol.Reject, sendId = payload.sendId, info = "rejected by user"))
            return
        }
        synchronized(sessions) {
            sessions[payload.sendId]?.let { return@synchronized } // 其它线程可能已建
            val token = randomToken()
            // v2：为每个文件生成独立 token；v1：空表，仅保留单 token 兼容
            val fileTokens = if (payload.files.isEmpty()) emptyMap()
            else payload.files.keys.associateWith { randomToken() }
            sessions[payload.sendId] = Session(payload.sendId, token, saveDir, fileTokens)
        }
        val session = synchronized(sessions) { sessions[payload.sendId] }
        writeJson(socket, 200, session?.let { makeInitResponse(payload.sendId, it) }
            ?: Protocol.Response(Protocol.Error, info = "session not created"))
    }

    /** 构造初始化响应：v2 带 sessionId + files(fileId→token)；v1 仅带单 token。 */
    private fun makeInitResponse(sendId: String, s: Session): Protocol.Response =
        if (s.fileTokens.isEmpty()) {
            Protocol.Response(Protocol.Ok, sendId = sendId, token = s.token)
        } else {
            Protocol.Response(Protocol.Ok, sendId = sendId, sessionId = sendId, files = s.fileTokens)
        }

    private fun serveUpload(path: String, query: String, socket: Socket, headers: Map<String, String>, input: InputStream, contentLength: Int) {
        val rawName = headers["x-filename"] ?: return writeJson(socket, 413, Protocol.Response(Protocol.Error, info = "no file name"))
        val safeName = sanitizeFileName(rawName)
        if (safeName.isEmpty()) return writeJson(socket, 413, Protocol.Response(Protocol.Error, info = "bad file name"))

        val saveDir: String
        if (path == Protocol.PATH_UPLOAD) {
            // v2：校验 sessionId + fileId + 单文件 token
            val q = parseQuery(query)
            val sessionId = q["sessionId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no sessionId"))
            val fileId = q["fileId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no fileId"))
            val token = q["token"] ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired, info = "invalid token"))

            val s = synchronized(sessions) { sessions[sessionId] }
                ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired, info = "invalid token"))
            val fileToken = s.fileTokens[fileId]
            if (fileToken != token) return writeJson(socket, 403, Protocol.Response(Protocol.Error, info = "token mismatch ip"))
            saveDir = s.saveDir
        } else {
            // v1：单 token 匹配任意会话
            val auth = headers["authorization"] ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired))
            val bearer = auth.removePrefix("Bearer ").trim()
            val s = synchronized(sessions) { sessions.values.firstOrNull { it.token == bearer } }
                ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired))
            saveDir = s.saveDir
        }

        val dest = uniquePath(File(saveDir), safeName)
        var written = 0L
        try {
            dest.parentFile?.mkdirs()
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                var remaining = contentLength
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining.toLong()).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                    remaining -= n
                }
            }
            // 字节数校验：写入长度必须等于声明的长度，否则丢弃半文件并报错
            if (contentLength <= 0 || written != contentLength.toLong()) {
                runCatching { dest.delete() }
                return writeJson(socket, 500, Protocol.Response(Protocol.Error,
                    info = "size mismatch expected=$contentLength actual=$written"))
            }
            writeJson(socket, 200, Protocol.Response(Protocol.Ok))
            runCatching { onSaved?.invoke(dest.absolutePath) }
        } catch (e: Throwable) {
            runCatching { dest.delete() }
            writeJson(socket, 500, Protocol.Response(Protocol.Error, info = e.message))
        }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = HashMap<String, String>()
        query.split('&').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) map[kv.substring(0, i)] = kv.substring(i + 1)
        }
        return map
    }

    private fun serveCancel(socket: Socket, body: ByteArray?) {
        val sendId = runCatching {
            org.json.JSONObject(String(body ?: ByteArray(0))).optString("sendId")
        }.getOrDefault("")
        if (sendId.isNotEmpty()) synchronized(sessions) { sessions.remove(sendId) }
        writeJson(socket, 200, Protocol.Response(Protocol.Ok))
    }

    /** 处理文本消息：解析 body 后回调，不落盘、不建会话，直接回 ok。 */
    private fun serveMessage(socket: Socket, body: ByteArray?) {
        val m = Protocol.textFromRaw(String(body ?: ByteArray(0)))
            ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "bad body"))
        if (m.content.isEmpty()) return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "empty content"))
        Log.d(TAG, "收到文本消息 from=${m.deviceId} len=${m.content.length}")
        runCatching { onMessage?.invoke(m) }
        writeJson(socket, 200, Protocol.Response(Protocol.Ok))
    }

    // ---------- HTTP 低级工具 ----------
    private fun readHeader(input: InputStream): Pair<String, Map<String, String>>? {
        val cap = 32 * 1024
        val header = ByteArrayOutputStream()
        val terminator = byteArrayOf(0x0D, 0x0A, 0x0D, 0x0A)
        var matched = 0
        while (header.size() < cap) {
            val b = input.read()
            if (b == -1) return null
            header.write(b)
            matched = if (b == terminator[matched].toInt()) matched + 1
            else if (b == 0x0D) 1 else 0
            if (matched == 4) break
        }
        val text = String(header.toByteArray(), Charsets.ISO_8859_1)
        val lines = text.split("\r\n")
        val reqLine = lines.firstOrNull() ?: return null
        val headers = HashMap<String, String>()
        for (line in lines.drop(1)) {
            val idx = line.indexOf(':')
            if (idx > 0) headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
        }
        return reqLine to headers
    }

    private fun readFully(input: InputStream, len: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var remaining = len
        while (remaining > 0) {
            val n = input.read(buf, 0, minOf(buf.size, remaining))
            if (n <= 0) break
            out.write(buf, 0, n)
            remaining -= n
        }
        return out.toByteArray()
    }

    private fun writeJson(socket: Socket, status: Int, response: Protocol.Response) {
        val js = org.json.JSONObject().apply {
            put(Protocol.MSG_TYPE, response.messageType)
            response.sendId?.let { put("sendId", it) }
            response.sessionId?.let { put("sessionId", it) }
            response.token?.let { put("token", it) }
            if (response.files.isNotEmpty()) {
                val f = org.json.JSONObject()
                response.files.forEach { (id, tok) -> f.put(id, tok) }
                put("files", f)
            }
            response.info?.let { put("info", it) }
        }.toString()
        val body = js.toByteArray(Charsets.UTF_8)
        val reason = if (status == 200) "OK" else if (status == 401) "Unauthorized" else if (status == 403) "Forbidden" else "Error"
        val head = "HTTP/1.1 $status $reason\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n\r\n"
        socket.getOutputStream().let { out ->
            out.write(head.toByteArray(Charsets.ISO_8859_1)); out.write(body); out.flush()
        }
    }

    /** 净化接收到的文件名：仅保留末尾文件名段，剔除路径与非法字符，防路径穿越。 */
    private fun sanitizeFileName(raw: String): String {
        val decoded = runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
        // 只取最后一个路径段，剥离任何目录前缀（防 ../、反斜杠等穿越）
        val last = decoded.replace('\\', '/').substringAfterLast('/')
        // 替换 Windows/各端非法字符，去掉首尾空白与点
        val cleaned = last.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            .trimEnd('.', ' ').replace("\u0000", "")
        // 空名视作非法
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") return ""
        // 长度上限（ext4/vfat 常见 255 字节），过长则截断
        return if (cleaned.length > 240) cleaned.take(240) else cleaned
    }

    /** 目标已存在时追加 (1)/(2)… 序号，避免覆盖已有文件。 */
    private fun uniquePath(dir: File, name: String): File {
        val candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val dot = name.lastIndexOf('.')
        val (stem, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
        var i = 1
        while (true) {
            val next = File(dir, "$stem ($i)$ext")
            if (!next.exists()) return next
            i++
        }
    }

    private fun randomToken() = java.util.UUID.randomUUID().toString().replace("-", "")

    private companion object {
        const val TAG = "OrangeGO.Receive"
        /** 会话最长存活时间：超时未完成即回收，避免 sessions 无限增长。 */
        const val SESSION_TTL_MS = 15 * 60 * 1000L
    }
}