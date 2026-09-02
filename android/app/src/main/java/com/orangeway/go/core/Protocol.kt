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

    const val MSG_TYPE = "messageType"
    const val PATH_INIT = "/api/v1/send/init"
    const val PATH_PREPARE = "/api/localsend/v2/prepare-upload"
    const val PATH_UPLOAD = "/api/localsend/v2/upload"
    const val PATH_FILE = "/api/v1/send/file"
    const val PATH_CANCEL = "/api/v1/send/cancel"
    const val PATH_MESSAGE = "/api/v1/send/message"

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
        val sha256: String? = null
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
                })
            }
            put("files", f)
        }
    }.toString()

    fun cancelJson(p: CancelPayload): String = JSONObject().apply {
        put(MSG_TYPE, SendCancel)
        put("sendId", p.sendId)
    }.toString()

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
}