package com.orangeway.go.core

import org.json.JSONObject

/**
 * OrangeGO 统一互传协议模型（与 OrangeGO/PROTOCOL.md 对齐）。
 * 发现走 mDNS/DNS-SD(_orangego._tcp) + UDP 广播；传输走 HTTP，端口一致 53317。
 */
object Protocol {
    const val Port = 53317
    const val ServiceType = "_orangego._tcp"

    const val DiscoveryRequest = "discoveryRequest"
    const val DiscoveryResponse = "discoveryResponse"
    const val SendInit = "sendInit"
    const val Ok = "ok"
    const val Reject = "reject"
    const val Error = "error"
    const val Expired = "expired"
    const val SendCancel = "sendCancel"
    const val Message = "message" // 文本消息命令
    const val Recall = "recall" // 撤回命令（发送方撤回已发的文本/文件）

    const val MSG_TYPE = "messageType"
    const val PATH_INIT = "/api/v1/send/init"
    const val PATH_PREPARE = "/api/localsend/v2/prepare-upload"
    const val PATH_UPLOAD = "/api/localsend/v2/upload"
    const val PATH_FILE = "/api/v1/send/file"
    const val PATH_CANCEL = "/api/v1/send/cancel"
    const val PATH_MESSAGE = "/api/v1/send/message"
    const val PATH_RECALL = "/api/v1/send/recall"

    // LocalSend v2 兼容端点（与官方 protocol v2 对齐，字段一律 camelCase）
    const val PATH_INFO_V1 = "/api/localsend/v1/info"
    const val PATH_INFO_V2 = "/api/localsend/v2/info"
    const val PATH_REGISTER = "/api/localsend/v2/register"
    const val PATH_LS_CANCEL = "/api/localsend/v2/cancel"

    // LocalSend 组播发现（与官方 multicast 一致）
    const val LS_MULTICAST_IP = "224.0.0.167"
    const val LS_MULTICAST_PORT = 53317
    const val LS_PROTOCOL_VERSION = "2.0"

    /** 设备信息（发现通告）。 */
    data class DeviceInfo(
        val messageType: String = DiscoveryRequest,
        val deviceId: String,
        val name: String,
        val port: Int = Port,
        val version: String = "1.0"
    )

    /** 单个待传输文件元数据（v2 prepare-upload 用）。 */
    data class FileMeta(
        val id: String,
        val fileName: String,
        val size: Long,
        val fileType: String = "application/octet-stream",
        val sha256: String? = null,
        val relativePath: String? = null,
        val thumb: String? = null
    )

    /** 发送初始化请求（v2 携带完整文件清单；v1 无 files）。 */
    data class SendInitPayload(
        val messageType: String = SendInit,
        val sendId: String,
        val deviceId: String,
        val name: String,
        val totalFiles: Int,
        val totalSize: Long,
        val files: Map<String, FileMeta> = emptyMap()
    )

    /** v2 prepare-upload 响应：会话 id + 每个被接受文件的专属 token。 */
    data class PrepareResult(
        val sessionId: String,
        val tokens: Map<String, String>, // fileId → token
        val sendId: String? = null
    )

    /** 标准响应。 */
    data class Response(
        val messageType: String,
        val sendId: String? = null,
        val sessionId: String? = null,
        val token: String? = null,
        val files: Map<String, String> = emptyMap(), // fileId → token (v2)
        val info: String? = null
    )

    /** 取消请求。 */
    data class CancelPayload(val messageType: String = SendCancel, val sendId: String)

    /** 撤回请求（携带被撤回的 sendId）。 */
    data class RecallPayload(val messageType: String = Recall, val sendId: String)

    /** 文本消息负载。 */
    data class TextMessage(
        val messageType: String = Message,
        val sendId: String,
        val deviceId: String,
        val name: String,
        val content: String,
        val timestamp: Long = 0L
    )

    fun toJson(o: JSONObject): String = o.toString()

    fun deviceJson(info: DeviceInfo): String = JSONObject().apply {
        put(MSG_TYPE, info.messageType)
        put("deviceId", info.deviceId)
        put("name", info.name)
        put("port", info.port)
        put("version", info.version)
    }.toString()

    fun deviceFromRaw(raw: String): DeviceInfo? = runCatching {
        val j = JSONObject(raw)
        DeviceInfo(
            messageType = j.optString(MSG_TYPE),
            deviceId = j.optString("deviceId"),
            name = j.optString("name"),
            port = j.optInt("port", Port),
            version = j.optString("version", "1.0")
        )
    }.getOrNull()

    fun initJson(p: SendInitPayload): String = JSONObject().apply {
        put(MSG_TYPE, SendInit)
        put("sendId", p.sendId)
        put("deviceId", p.deviceId)
        put("name", p.name)
        put("totalFiles", p.totalFiles)
        put("totalSize", p.totalSize)
        if (p.files.isNotEmpty()) {
            val f = JSONObject()
            p.files.forEach { (id, meta) ->
                f.put(id, JSONObject().apply {
                    put("id", id)
                    put("fileName", meta.fileName)
                    put("size", meta.size)
                    put("fileType", meta.fileType)
                    meta.sha256?.let { put("sha256", it) }
                    meta.relativePath?.let { put("relativePath", it) }
                    meta.thumb?.let { put("thumb", it) }
                })
            }
            put("files", f)
        }
    }.toString()

    fun cancelJson(p: CancelPayload): String = JSONObject().apply {
        put(MSG_TYPE, SendCancel)
        put("sendId", p.sendId)
    }.toString()

    fun recallJson(p: RecallPayload): String = JSONObject().apply {
        put(MSG_TYPE, Recall)
        put("sendId", p.sendId)
    }.toString()

    fun recallFromRaw(raw: String): RecallPayload? = runCatching {
        val j = JSONObject(raw)
        RecallPayload(messageType = j.optString(MSG_TYPE), sendId = j.optString("sendId"))
    }.getOrNull()

    fun textJson(m: TextMessage): String = JSONObject().apply {
        put(MSG_TYPE, Message)
        put("sendId", m.sendId)
        put("deviceId", m.deviceId)
        put("name", m.name)
        put("content", m.content)
        put("timestamp", m.timestamp)
    }.toString()

    fun textFromRaw(raw: String): TextMessage? = runCatching {
        val j = JSONObject(raw)
        TextMessage(
            messageType = j.optString(MSG_TYPE),
            sendId = j.optString("sendId"),
            deviceId = j.optString("deviceId"),
            name = j.optString("name"),
            content = j.optString("content"),
            timestamp = j.optLong("timestamp")
        )
    }.getOrNull()

    fun responseFromRaw(raw: String): Response? = runCatching {
        val j = JSONObject(raw)
        val files = LinkedHashMap<String, String>()
        j.optJSONObject("files")?.keys()?.forEach { id -> files[id] = j.optJSONObject("files").optString(id) }
        Response(
            messageType = j.optString(MSG_TYPE),
            sendId = j.optString("sendId").ifEmpty { null },
            sessionId = j.optString("sessionId").ifEmpty { null },
            token = j.optString("token").ifEmpty { null },
            files = files,
            info = j.optString("info").ifEmpty { null }
        )
    }.getOrNull()

    // ============ LocalSend v2 DTO（camelCase，与官方 serde rename_all=camelCase 对齐） ============

    /** LocalSend 设备身份（RegisterDtoV2 / MulticastMessageV2 共用此结构）。 */
    data class LsRegisterDto(
        val alias: String = "",
        val version: String = LS_PROTOCOL_VERSION,
        val deviceModel: String? = null,
        val deviceType: String? = null,
        val fingerprint: String = "",
        val port: Int = Port,
        val protocol: String = "https",
        val download: Boolean = false
    )

    /** LocalSend prepare-upload 请求中的单文件描述。 */
    data class LsFileDto(
        val id: String,
        val fileName: String,
        val size: Long,
        val fileType: String? = null
    )

    /** 构建 /info 与 /register 响应体（RegisterResponseDtoV2，camelCase）。 */
    fun buildLsInfoJson(alias: String, fingerprint: String, deviceModel: String, deviceType: String = "mobile"): String =
        JSONObject().apply {
            put("alias", alias)
            put("version", LS_PROTOCOL_VERSION)
            put("deviceModel", deviceModel)
            put("deviceType", deviceType)
            put("fingerprint", fingerprint)
            put("download", false)
        }.toString()

    /** 构建组播存在报文（MulticastMessageV2，camelCase；TLS 接收故 protocol=https）。 */
    fun buildLsPresenceJson(alias: String, fingerprint: String, deviceModel: String, port: Int, deviceType: String = "mobile"): String =
        JSONObject().apply {
            put("alias", alias)
            put("version", LS_PROTOCOL_VERSION)
            put("deviceModel", deviceModel)
            put("deviceType", deviceType)
            put("fingerprint", fingerprint)
            put("port", port)
            put("protocol", "https")
            put("download", false)
        }.toString()

    /** 解析 LocalSend 组播存在报文 / register 请求体；非 LocalSend 格式返回 null。 */
    fun parseLsDevice(raw: String): LsRegisterDto? = runCatching {
        val j = JSONObject(raw)
        if (!j.has("fingerprint")) return null
        LsRegisterDto(
            alias = j.optString("alias"),
            version = j.optString("version", LS_PROTOCOL_VERSION),
            deviceModel = j.optString("deviceModel").ifEmpty { null },
            deviceType = j.optString("deviceType").ifEmpty { null },
            fingerprint = j.optString("fingerprint"),
            port = j.optInt("port", Port),
            protocol = j.optString("protocol", "https"),
            download = j.optBoolean("download", false)
        )
    }.getOrNull()

    /** 判别 prepare-upload 请求体是否为 LocalSend 报文：根含 info 且 files 为对象（Map）。 */
    fun isLocalSendPrepare(raw: String): Boolean = runCatching {
        val j = JSONObject(raw)
        j.has("info") && j.has("files") && j.optJSONObject("files") != null
    }.getOrDefault(false)

    /** 解析 LocalSend prepare-upload 请求体为 (info, files)。 */
    fun parseLsPrepare(raw: String): Pair<LsRegisterDto, Map<String, LsFileDto>>? = runCatching {
        val j = JSONObject(raw)
        val infoObj = j.optJSONObject("info") ?: return null
        val filesObj = j.optJSONObject("files") ?: return null
        val info = LsRegisterDto(
            alias = infoObj.optString("alias"),
            version = infoObj.optString("version", LS_PROTOCOL_VERSION),
            deviceModel = infoObj.optString("deviceModel").ifEmpty { null },
            deviceType = infoObj.optString("deviceType").ifEmpty { null },
            fingerprint = infoObj.optString("fingerprint"),
            port = infoObj.optInt("port", Port),
            protocol = infoObj.optString("protocol", "https"),
            download = infoObj.optBoolean("download", false)
        )
        val files = LinkedHashMap<String, LsFileDto>()
        filesObj.keys().forEach { id ->
            val f = filesObj.getJSONObject(id)
            files[id] = LsFileDto(
                id = id,
                fileName = f.optString("fileName"),
                size = f.optLong("size"),
                fileType = f.optString("fileType").ifEmpty { null }
            )
        }
        info to files
    }.getOrNull()

    /** 构建 LocalSend prepare-upload 响应：{sessionId, files:{fileId→token}}（camelCase）。 */
    fun buildLsPrepareResponseJson(sessionId: String, tokens: Map<String, String>): String =
        JSONObject().apply {
            put("sessionId", sessionId)
            val f = JSONObject()
            tokens.forEach { (id, tok) -> f.put(id, tok) }
            put("files", f)
        }.toString()
}