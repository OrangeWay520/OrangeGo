package com.orangeway.go.core

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.X509TrustManager

/**
 * HTTP 接收服务器（ServerSocket 手写 HTTP）。监听 53317，处理 init / file / cancel。
 * 协议见 OrangeGO/PROTOCOL.md「二、传输」。
 * 同时兼容 LocalSend v2：/info、/register、prepare-upload（翻译）、upload（chunked）、cancel。
 */
class ReceiveServer(
    private val saveDirProvider: () -> String?,
    private val onIncoming: (Protocol.SendInitPayload) -> String?,
    private val onSaved: ((String) -> Unit)? = null, // 单文件落盘成功，回传实际保存路径（兼容旧路径）
    // 接收进度链（对齐电脑端 OnFileStarted/OnFileProgress/OnFileReceived）
    private val onFileStarted: ((sendId: String, filePath: String, total: Long, thumb: String?) -> Unit)? = null,
    private val onFileProgress: ((sendId: String, filePath: String, written: Long, total: Long) -> Unit)? = null,
    private val onFileReceived: ((sendId: String, filePath: String, size: Long) -> Unit)? = null,
    private val onCanceled: ((sendId: String) -> Unit)? = null, // 对方取消发送：UI 标"已取消" + 清理半文件
    private val onFileFailed: ((sendId: String, filePath: String) -> Unit)? = null, // 传输失败（断开等）
    private val onMessage: ((Protocol.TextMessage) -> Unit)? = null, // 收到文本消息（聊天气泡）
    private val onRecall: ((String) -> Unit)? = null, // 收到撤回命令，带被撤回的 sendId
    // LocalSend 兼容：收到对端 POST /register 时回调，把对端并入设备表
    private val onLsRegister: ((Peer) -> Unit)? = null,
    // 本机身份（延迟求值，fingerprint 在 LsCert.init 后才有）
    private val localAlias: () -> String = { "OrangeGO" },
    private val localFingerprint: () -> String = { "" },
    private val localDeviceModel: () -> String = { "Android" }
) {
    // 会话：sendId → 会话。v2 下每个文件有独立 token（fileId→token），v1 仅兼容单 token。
    private data class Session(
        val sendId: String,
        val token: String,
        val saveDir: String,
        val fileTokens: Map<String, String> = emptyMap(), // fileId → token (v2)
        val files: Map<String, Protocol.FileMeta> = emptyMap(), // fileId → meta (v2，供相对路径重建)
        val createdAt: Long = System.currentTimeMillis(),
        val partialFiles: MutableMap<String, String> = mutableMapOf() // P2: fileId → 断点续传半文件路径
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
                val sslCtx = SSLContext.getInstance("TLS")
                sslCtx.init(serverKeyManagers(), arrayOf(TrustAllManager), SecureRandom())
                val s = (sslCtx.serverSocketFactory.createServerSocket(Protocol.Port) as SSLServerSocket).also {
                    it.needClientAuth = true
                    running = true; server = it
                }
                Log.i(TAG, "TLS 53317 监听成功 (mTLS, ${s.localSocketAddress})")
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
            // P2: HEAD on upload → 返回已接收字节数（供发送端断点续传）
            if (parts.firstOrNull() == "HEAD" && (path == Protocol.PATH_UPLOAD || path == Protocol.PATH_FILE)) {
                serveUploadHead(socket, query)
                return
            }
            val body = if (contentLength > 0) readFully(input, contentLength) else null

            val method = parts.firstOrNull()
            when {
                method == "GET" && (path == Protocol.PATH_INFO_V1 || path == Protocol.PATH_INFO_V2) ->
                    serveInfo(socket)
                method == "POST" && (path == Protocol.PATH_PREPARE || path == Protocol.PATH_INIT) ->
                    serveInit(socket, body)
                method == "POST" && path == Protocol.PATH_REGISTER ->
                    serveLsRegister(socket, body, socket.inetAddress?.hostAddress)
                method == "POST" && (path == Protocol.PATH_CANCEL || path == Protocol.PATH_LS_CANCEL) ->
                    serveCancel(socket, body, query)
                method == "POST" && path == Protocol.PATH_MESSAGE ->
                    serveMessage(socket, body)
                method == "POST" && path == Protocol.PATH_RECALL ->
                    serveRecall(socket, body)
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
        // LocalSend prepare-upload：根含 info + files(Map) → 翻译成 OrangeGo 会话，回 LocalSend 格式
        val raw = body?.let { String(it, Charsets.UTF_8) } ?: ""
        if (raw.isNotEmpty() && Protocol.isLocalSendPrepare(raw)) { serveLsPrepare(socket, raw); return }
        val payload = runCatching {
            val j = org.json.JSONObject(String(body ?: return writeJson(socket, 400,
                Protocol.Response(Protocol.Error, info = "no body"))))
            // 解析文件清单（v2 可选；v1 无 files）
            val files = LinkedHashMap<String, Protocol.FileMeta>()
            j.optJSONObject("files")?.keys()?.forEach { id ->
                val f = j.optJSONObject("files").getJSONObject(id)
                files[id] = Protocol.FileMeta(
                    id = id,
                    fileName = if (f.isNull("fileName")) "" else f.optString("fileName"),
                    size = f.optLong("size"),
                    fileType = f.optString("fileType", "application/octet-stream"),
                    sha256 = if (f.isNull("sha256")) null else f.optString("sha256").ifEmpty { null },
                    relativePath = if (f.isNull("relativePath")) null else f.optString("relativePath").ifEmpty { null },
                    thumb = if (f.isNull("thumb")) null else f.optString("thumb").ifEmpty { null }
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
            sessions[payload.sendId] = Session(payload.sendId, token, saveDir, fileTokens, payload.files)
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

    // ---------- LocalSend v2 兼容端点 ----------

    /** LocalSend GET /info：回本机设备身份（RegisterDtoV2，camelCase）。 */
    private fun serveInfo(socket: Socket) {
        writeRawJson(socket, 200, Protocol.buildLsInfoJson(localAlias(), localFingerprint(), localDeviceModel(), "mobile"))
    }

    /** LocalSend POST /register：解析对端身份并入设备表，回本机 info（让对端把我们加入其列表）。 */
    private fun serveLsRegister(socket: Socket, body: ByteArray?, remoteIp: String?) {
        val raw = body?.let { String(it, Charsets.UTF_8) } ?: ""
        val dto = if (raw.isNotEmpty()) Protocol.parseLsDevice(raw) else null
        Log.d(TAG, "收到 LS register: ip=$remoteIp alias=${dto?.alias} fp=${dto?.fingerprint?.take(8)}")
        if (dto != null && remoteIp != null) {
            val peer = Peer(
                deviceId = "ogLS_" + dto.fingerprint,
                name = dto.alias.ifEmpty { "LocalSend 设备" },
                ip = remoteIp,
                port = if (dto.port > 0) dto.port else Protocol.Port,
                isLocalSend = true,
                fingerprint = dto.fingerprint,
                protocol = dto.protocol.ifEmpty { "https" },
                deviceType = dto.deviceType ?: "mobile",
                deviceModel = dto.deviceModel ?: ""
            )
            runCatching { onLsRegister?.invoke(peer) }
        }
        writeRawJson(socket, 200, Protocol.buildLsInfoJson(localAlias(), localFingerprint(), localDeviceModel(), "mobile"))
    }

    /** LocalSend prepare-upload：翻译成 OrangeGo 会话模型，回 {sessionId, files:{fileId→token}}（camelCase）。 */
    private fun serveLsPrepare(socket: Socket, raw: String) {
        val parsed = Protocol.parseLsPrepare(raw)
        if (parsed == null || parsed.second.isEmpty()) {
            writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "bad body"))
            return
        }
        val (info, lsFiles) = parsed
        val alias = info.alias.ifEmpty { "LocalSend 设备" }
        val sendId = randomToken()
        val metas = lsFiles.mapValues { (id, f) ->
            Protocol.FileMeta(id = id, fileName = f.fileName, size = f.size,
                fileType = f.fileType ?: "application/octet-stream")
        }
        val payload = Protocol.SendInitPayload(
            sendId = sendId, deviceId = info.fingerprint, name = alias,
            totalFiles = lsFiles.size, totalSize = lsFiles.values.sumOf { it.size }, files = metas
        )
        val saveDir = onIncoming(payload)
        if (saveDir == null) {
            writeJson(socket, 403, Protocol.Response(Protocol.Reject, sendId = sendId, info = "rejected by user"))
            return
        }
        val fileTokens = synchronized(sessions) {
            sessions[sendId]?.let { return@synchronized it.fileTokens }
            val ft = metas.keys.associateWith { randomToken() }
            sessions[sendId] = Session(sendId, randomToken(), saveDir, ft, metas)
            ft
        }
        writeRawJson(socket, 200, Protocol.buildLsPrepareResponseJson(sendId, fileTokens))
    }

    /** P2: HEAD on upload → 返回已接收字节数（供发送端断点续传查询半文件偏移）。 */
    private fun serveUploadHead(socket: Socket, query: String) {
        val q = parseQuery(query)
        val sessionId = q["sessionId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no sessionId"))
        val fileId = q["fileId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no fileId"))
        val received = synchronized(sessions) {
            sessions[sessionId]?.partialFiles?.get(fileId)?.let { java.io.File(it).length() } ?: 0L
        }
        val head = "HTTP/1.1 200 OK\r\n" +
            "X-Received-Bytes: $received\r\n" +
            "Content-Length: 0\r\n" +
            "Connection: close\r\n\r\n"
        runCatching { socket.getOutputStream().write(head.toByteArray(Charsets.ISO_8859_1)); socket.getOutputStream().flush() }
    }

    private fun serveUpload(path: String, query: String, socket: Socket, headers: Map<String, String>, input: InputStream, contentLength: Int) {
        // 先解析会话与 fileId（v2 从 query，v1 从 Bearer），再确定文件名：
        // LocalSend 客户端不带 X-FileName，需用 fileId 从 prepare 清单查文件名
        val saveDir: String
        var relativePath: String? = null
        var nameFromMeta: String? = null
        var sid = "" // 会话 sendId，供进度回调关联 UI 记录
        var expectedTotal = 0L // 期望文件大小，供进度计算（chunked 时从 prepare 清单取，无 Content-Length）
        var resumeFileId = "" // P2: v2 路径下的 fileId，供断点续传记录半文件
        var thumb: String? = null
        if (path == Protocol.PATH_UPLOAD) {
            // v2：校验 sessionId + fileId + 单文件 token
            val q = parseQuery(query)
            val sessionId = q["sessionId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no sessionId"))
            val fileId = q["fileId"] ?: return writeJson(socket, 400, Protocol.Response(Protocol.Error, info = "no fileId"))
            resumeFileId = fileId
            val token = q["token"] ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired, info = "invalid token"))

            val s = synchronized(sessions) { sessions[sessionId] }
                ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired, info = "invalid token"))
            val fileToken = s.fileTokens[fileId]
            if (fileToken != token) return writeJson(socket, 403, Protocol.Response(Protocol.Error, info = "token mismatch ip"))
            saveDir = s.saveDir
            sid = s.sendId
            expectedTotal = s.files[fileId]?.size ?: 0L
            relativePath = s.files[fileId]?.relativePath
            nameFromMeta = s.files[fileId]?.fileName
            thumb = s.files[fileId]?.thumb
        } else {
            // v1：单 token 匹配任意会话
            val auth = headers["authorization"] ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired))
            val bearer = auth.removePrefix("Bearer ").trim()
            val s = synchronized(sessions) { sessions.values.firstOrNull { it.token == bearer } }
                ?: return writeJson(socket, 401, Protocol.Response(Protocol.Expired))
            saveDir = s.saveDir
            sid = s.sendId
            expectedTotal = contentLength.toLong()
        }
        // 文件名：X-FileName 优先；缺失则用 prepare 清单中的 fileName（LocalSend chunked 上传不带文件名头）
        val rawName = headers["x-filename"]
        val safeName = if (!rawName.isNullOrEmpty()) sanitizeFileName(rawName)
            else (nameFromMeta?.let { sanitizeFileName(it) } ?: "")
        if (safeName.isEmpty()) return writeJson(socket, 413, Protocol.Response(Protocol.Error, info = "no file name"))

        // 文件夹发送：按安全净化后的相对路径重建子目录结构（防路径穿越），落盘到子目录
        var targetDir = File(saveDir)
        var finalName = safeName
        val relDir = safeRelativeDir(relativePath)
        if (relDir != null) {
            targetDir = File(targetDir, relDir)
            val leaf = relativePath!!.replace('\\', '/').trim('/').substringAfterLast('/')
            if (leaf.isNotEmpty()) finalName = safeNameForLeaf(leaf)
        }
        targetDir.mkdirs()

        // P2: 断点续传 — 解析 Content-Range 获取起始偏移，复用半文件追加写入
        val contentRange = headers["content-range"]
        val resumeOffset = if (contentRange != null) {
            val m = Regex("bytes (\\d+)-").find(contentRange)
            m?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } else 0L
        val dest = if (resumeOffset > 0 && resumeFileId.isNotEmpty()) {
            synchronized(sessions) { sessions[sid]?.partialFiles?.get(resumeFileId) }?.let { File(it) }
                ?.takeIf { it.exists() } ?: uniquePath(targetDir, finalName)
        } else {
            uniquePath(targetDir, finalName)
        }
        // LocalSend 客户端用流式上传（Transfer-Encoding: chunked，无 Content-Length）：
        // 期望大小取 prepare 清单中该 fileId 的 size，供进度与批次气泡使用
        val chunked = headers["transfer-encoding"]?.contains("chunked", ignoreCase = true) == true
        var written = resumeOffset // P2: 续传时从偏移量起算
        runCatching { onFileStarted?.invoke(sid, dest.absolutePath, expectedTotal, thumb) }
        try {
            dest.parentFile?.mkdirs()
            if (chunked) {
                val w = writeChunkedToFile(input, dest, expectedTotal, { wr, tot ->
                    runCatching { onFileProgress?.invoke(sid, dest.absolutePath, wr, tot) }
                }) { synchronized(sessions) { sessions[sid] == null } }
                if (w < 0) {
                    runCatching { dest.delete() }
                    if (w == -2L) runCatching { onCanceled?.invoke(sid) }
                    return writeJson(socket, 500, Protocol.Response(Protocol.Error, info = if (w == -2L) "canceled" else "bad chunked body"))
                }
                written = w
            } else {
                val gzip = headers["content-encoding"]?.contains("gzip", ignoreCase = true) == true
                if (gzip) {
                    // P3: gzip 解压路径 — 从 GZIPInputStream 读到 EOF，校验解压后大小
                    java.util.zip.GZIPInputStream(input).use { gzIn ->
                        FileOutputStream(dest).use { out ->
                            val buf = ByteArray(256 * 1024)
                            while (true) {
                                if (synchronized(sessions) { sessions[sid] } == null) { runCatching { dest.delete() }; runCatching { onCanceled?.invoke(sid) }; return }
                                val n = gzIn.read(buf)
                                if (n <= 0) break
                                out.write(buf, 0, n)
                                written += n
                                runCatching { onFileProgress?.invoke(sid, dest.absolutePath, written, expectedTotal) }
                            }
                        }
                    }
                    if (expectedTotal > 0 && written != expectedTotal) {
                        runCatching { dest.delete() }
                        return writeJson(socket, 500, Protocol.Response(Protocol.Error, info = "size mismatch expected=$expectedTotal actual=$written"))
                    }
                } else {
                    FileOutputStream(dest, resumeOffset > 0).use { out -> // P2: 续传时追加写入
                        val buf = ByteArray(64 * 1024)
                        var remaining = contentLength.toLong()
                        while (remaining > 0) {
                            if (synchronized(sessions) { sessions[sid] } == null) { runCatching { dest.delete() }; runCatching { onCanceled?.invoke(sid) }; return
                            }
                            val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            written += n
                            remaining -= n
                            runCatching { onFileProgress?.invoke(sid, dest.absolutePath, written, expectedTotal) }
                        }
                    }
                    // 字节数校验：本请求写入长度必须等于声明的 Content-Length（续传时 written 含偏移量）
                    if (contentLength <= 0 || (written - resumeOffset) != contentLength.toLong()) {
                        if (written > resumeOffset && resumeFileId.isNotEmpty()) // P2: 保留半文件供续传
                            synchronized(sessions) { sessions[sid]?.partialFiles?.put(resumeFileId, dest.absolutePath) }
                        else runCatching { dest.delete() }
                        return writeJson(socket, 500, Protocol.Response(Protocol.Error,
                            info = "size mismatch expected=$contentLength actual=${written - resumeOffset}"))
                    }
                }
            }
            writeJson(socket, 200, Protocol.Response(Protocol.Ok))
            if (resumeFileId.isNotEmpty()) synchronized(sessions) { sessions[sid]?.partialFiles?.remove(resumeFileId) } // P2: 完成后清理半文件记录
            runCatching { onFileReceived?.invoke(sid, dest.absolutePath, written) }
            runCatching { onSaved?.invoke(dest.absolutePath) }
        } catch (e: Throwable) {
            // P2: 连接中断时保留半文件供续传（用户取消走上面的 session 检查已删文件）
            if (written > resumeOffset && resumeFileId.isNotEmpty())
                synchronized(sessions) { sessions[sid]?.partialFiles?.put(resumeFileId, dest.absolutePath) }
            else runCatching { dest.delete() }
            runCatching { onFileFailed?.invoke(sid, dest.absolutePath) }
            writeJson(socket, 500, Protocol.Response(Protocol.Error, info = e.message))
        }
    }

    /** 解析 RFC-7230 chunked 编码的请求体写入目标文件，返回总字节数；失败返回 -1。 */
    private fun writeChunkedToFile(input: InputStream, dest: File, expectedTotal: Long = 0L, onProgress: ((written: Long, total: Long) -> Unit)? = null, isCanceled: () -> Boolean = { false }): Long {
        return try {
            FileOutputStream(dest).use { out ->
                val buf = ByteArray(64 * 1024)
                val scratch = ByteArray(1)
                var total = 0L
                while (true) {
                    if (isCanceled()) return -2L
                    // 读 chunk 大小行（十六进制，直到 \n）
                    val sb = StringBuilder()
                    while (true) {
                        val n = input.read(scratch)
                        if (n <= 0) return -1L
                        val c = scratch[0].toInt().toChar()
                        if (c == '\n') break
                        if (c != '\r' && c != ' ') sb.append(c)
                    }
                    val sizeStr = sb.toString().split(';', limit = 2)[0].trim()
                    val size = sizeStr.toLongOrNull(16) ?: return -1L
                    if (size == 0L) {
                        // 末尾 trailer：读到空行结束
                        while (true) {
                            val n = input.read(scratch)
                            if (n <= 0) break
                            if (scratch[0] == '\n'.code.toByte()) break
                        }
                        out.flush()
                        return total
                    }
                    var remaining = size
                    while (remaining > 0) {
                        if (isCanceled()) return -2L
                        val want = minOf(buf.size.toLong(), remaining).toInt()
                        val n = input.read(buf, 0, want)
                        if (n <= 0) return -1L
                        out.write(buf, 0, n)
                        remaining -= n
                        total += n
                        runCatching { onProgress?.invoke(total, expectedTotal) }
                    }
                    // 每个 chunk 后跟 CRLF
                    input.read(scratch); input.read(scratch)
                }
                total
            }
        } catch (_: Throwable) { -1L }
    }

    private fun parseQuery(query: String): Map<String, String> {
        val map = HashMap<String, String>()
        query.split('&').forEach { kv ->
            val i = kv.indexOf('=')
            if (i > 0) map[kv.substring(0, i)] = kv.substring(i + 1)
        }
        return map
    }

    private fun serveCancel(socket: Socket, body: ByteArray?, query: String) {
        // LocalSend 用 query 传 sessionId；OrangeGo 自家用 body.sendId
        var sid = parseQuery(query)["sessionId"] ?: ""
        if (sid.isEmpty()) {
            sid = runCatching {
                org.json.JSONObject(String(body ?: ByteArray(0))).optString("sendId")
            }.getOrDefault("")
        }
        if (sid.isNotEmpty()) synchronized(sessions) { sessions.remove(sid) }
        runCatching { onCanceled?.invoke(sid) }
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

    /** 处理撤回命令：解析 sendId 后回调，UI 端据此把对应接收记录标记为「已撤回」。 */
    private fun serveRecall(socket: Socket, body: ByteArray?) {
        val sendId = runCatching {
            org.json.JSONObject(String(body ?: ByteArray(0))).optString("sendId")
        }.getOrDefault("")
        if (sendId.isNotEmpty()) runCatching { onRecall?.invoke(sendId) }
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

    /** 写纯 JSON 字符串响应（LocalSend /info、/register、prepare-upload 响应体不是 Protocol.Response 格式）。 */
    private fun writeRawJson(socket: Socket, status: Int, json: String) {
        val body = json.toByteArray(Charsets.UTF_8)
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

    /**
     * 文件夹发送：把携带的相对路径（相对发送根目录，`/` 分隔）净化为可安全落盘的子目录字符串。
     * 逐段校验，遇 `.`/`..` 或非法字符即返回 null（放弃目录重建，退化为存到根目录），防路径穿越。
     */
    private fun safeRelativeDir(relPath: String?): String? {
        if (relPath.isNullOrBlank()) return null
        val normalized = relPath.replace('\\', '/').trim('/')
        if (normalized.isEmpty()) return null
        // 只取目录部分（最后一个 '/' 之前），叶子文件名由 safeNameForLeaf 处理
        val dirPart = normalized.substringBeforeLast('/', "")
        if (dirPart.isEmpty()) return null // 文件在根目录，无需创建子目录
        val segments = dirPart.split('/').filter { it.isNotEmpty() }
        val cleaned = ArrayList<String>(segments.size)
        for (seg in segments) {
            if (seg == "." || seg == "..") return null // 直接拒绝，防穿越
            val c = seg.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.', ' ')
            if (c.isEmpty()) return null
            cleaned.add(if (c.length > 200) c.take(200) else c)
        }
        return cleaned.joinToString("/")
    }

    /** 相对路径的叶子文件名（含协议乱码/路径分隔符净化）。 */
    private fun safeNameForLeaf(raw: String): String {
        val decoded = runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrDefault(raw)
        val last = decoded.replace('\\', '/').trim('/').substringAfterLast('/')
        val c = last.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().trimEnd('.', ' ')
        if (c.isEmpty() || c == "." || c == "..") return sanitizeFileName(decoded)
        return if (c.length > 240) c.take(240) else c
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

    /** 服务端证书 KeyManager：复用 LsCert 的 RSA-2048 自签证书（含 serverAuth EKU）。 */
    private fun serverKeyManagers(): Array<KeyManager> {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        ks.setKeyEntry("og", LsCert.privateKey(), CharArray(0), arrayOf(LsCert.certificate()))
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(ks, CharArray(0))
        return kmf.keyManagers
    }

    /** 信任所有客户端证书（mTLS：对端自签证书不预置 CA，加密+双向认证已防窃听/篡改；fingerprint pinning 由 P0-4 叠加防 MITM）。 */
    private object TrustAllManager : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    }

    private companion object {
        const val TAG = "OrangeGO.Receive"
        /** 会话最长存活时间：超时未完成即回收，避免 sessions 无限增长。 */
        const val SESSION_TTL_MS = 15 * 60 * 1000L
    }
}