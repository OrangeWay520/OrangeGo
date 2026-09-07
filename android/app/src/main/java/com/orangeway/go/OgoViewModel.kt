package com.orangeway.go

import android.app.Application
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orangeway.go.core.DiscoveryManager
import com.orangeway.go.core.FingerprintStore
import com.orangeway.go.core.LsCert
import com.orangeway.go.core.LsSender
import com.orangeway.go.core.OutgoingFile
import com.orangeway.go.core.Peer
import com.orangeway.go.core.Protocol
import com.orangeway.go.core.ReceiveServer
import com.orangeway.go.core.SenderClient
import com.orangeway.go.core.ThumbCache
import com.orangeway.go.data.SettingsRepo
import com.orangeway.go.ui.OgoLang
import com.orangeway.go.ui.OgoThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CompletableFuture

data class PendingReceive(
    val id: Long,
    val deviceName: String,
    val fileCount: Int,
    val totalBytes: Long,
    private val future: CompletableFuture<String?>,
    val needsPin: Boolean = false,
    var pinAccepted: Boolean = false,
    val sendId: String = "" // 关联对端发送的 sendId，接收记录据此支持撤回
) {
    fun accept(saveDir: String) = future.complete(saveDir)
    fun reject() = future.complete(null)
}

/** 收藏设备（白名单）条目：按设备 ID 收藏（不依赖 IP）。Name/Ip 为加入时快照，用于收藏列表展示。 */
data class FavDevice(
    val id: String,
    val name: String,
    val ip: String = ""
) {
    val shortId: String get() = "ID " + (if (id.startsWith("og_")) id.removePrefix("og_") else id)
}

data class TransferItem(
    val id: Long,
    val name: String,
    val direction: String, // 发送/接收
    val target: String,
    val progress: Float = 0f,
    val state: String = "进行中",
    val isText: Boolean = false,
    val content: String = "",
    val timestamp: Long = 0L,
    val localRef: String = "", // 兼容字段：首文件的可预览地址；新数据主要走 localRefs
    val localRefs: List<String> = emptyList(), // 全部文件的可预览地址：content:// 或 file:// 字符串；空列表=未知
    val sendId: String = "", // 跨端的消息唯一标识（发送方生成，撤回/取消按它匹配）
    val thumbKeys: List<String> = emptyList(), // 已生成并落盘到 ThumbCache 的缩略图内容指纹 key（与 localRefs 对应），删除/撤回记录时据此精确回收缓存
    val progressText: String = "",
) {
    val percent: String get() = "${(progress * 100).toInt()}%"
    /** 首文件的可预览地址：优先取 localRefs，退化为旧的 localRef 字段（空串=不可预览）。 */
    val firstLocalRef: String get() = localRefs.firstOrNull() ?: localRef
}

class OgoViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = SettingsRepo(app)

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers

    private val _incoming = MutableStateFlow<List<PendingReceive>>(emptyList())
    val incoming: StateFlow<List<PendingReceive>> = _incoming

    private val _transfers = MutableStateFlow<List<TransferItem>>(emptyList())
    val transfers: StateFlow<List<TransferItem>> = _transfers

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    // 「应用」列表是否正在加载（打开瞬间避免闪现空态）
    private val _appsLoading = MutableStateFlow(false)
    val appsLoading: StateFlow<Boolean> = _appsLoading

    private val _media = MutableStateFlow<List<MediaItem>>(emptyList())
    val media: StateFlow<List<MediaItem>> = _media

    private val _audio = MutableStateFlow<List<AudioItem>>(emptyList())
    val audio: StateFlow<List<AudioItem>> = _audio

    // 「媒体 / 音频」列表是否正在加载（打开瞬间避免闪现空态）
    private val _mediaLoading = MutableStateFlow(false)
    val mediaLoading: StateFlow<Boolean> = _mediaLoading

    private val _audioLoading = MutableStateFlow(false)
    val audioLoading: StateFlow<Boolean> = _audioLoading

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    // 接收偏好（从 DataStore 加载，设置页可改）
    val autoSave = MutableStateFlow(false)
    val saveHistory = MutableStateFlow(true)
    val saveToGallery = MutableStateFlow(true)
    val pinEnabled = MutableStateFlow(false)
    val pinCode = MutableStateFlow("")
    // 自动接收文本消息（默认开，关闭时不自动显示接收文本气泡）
    val autoAcceptText = MutableStateFlow(true)
    // 图片集成发送（默认 false：多张图片各自独立气泡）
    val integrateImages = MutableStateFlow(false)
    // 传输记录自动清理：保留最近 N 天的记录（0=不自动清理）
    val retentionDays = MutableStateFlow(0)
    // 收藏设备（白名单）列表，按设备 ID 记
    private val _favorites = MutableStateFlow<List<FavDevice>>(emptyList())
    val favorites: StateFlow<List<FavDevice>> = _favorites
    // 自动保存收藏夹设备的文件（默认开启）
    val autoSaveWhitelist = MutableStateFlow(true)

    // 收到内容（文本或文件）的顶部横幅提醒；用后置 null（UI 消费后清空）。
    // 用前缀标记类型：T:=文本，F:=文件，后接对端名称。
    val receivedNotice = MutableStateFlow<String?>(null)

    val receiveDir: String get() = saveDir

    private lateinit var deviceId: String
    private var selfName: String = ""
    private var saveDir: String = ""
    private var discovery: DiscoveryManager? = null
    private var server: ReceiveServer? = null
    private var sender: SenderClient? = null
    private var lsSender: LsSender? = null
    private var idCounter = 0L
    // 接收走服务器线程、发送走主线程，两边都会取新 id；不加锁可能并发拿到同一个 id，
    // 列表 key 重复会直接崩溃，因此统一走原子自增。
    private fun nextId(): Long = synchronized(this) { ++idCounter }
    @Volatile private var lastReceiveId: Long? = null // 最近一条接收记录 id，用于回填文件路径（跨线程读写）
    // 接收批次：sendId → 批次状态。onIncoming 确认时建，onFileStarted 建记录，
    // onFileProgress 更新进度，onFileReceived 标完成；全部完成移除。
    private data class RecvBatch(val from: String, val totalFiles: Int, val totalBytes: Long = 0L, val startTime: Long = System.currentTimeMillis(), var transferId: Long = 0L, var done: Int = 0, var remainEma: Double = -1.0, val files: List<Protocol.FileMeta> = emptyList())
    private val recvBatches = HashMap<String, RecvBatch>()
    private var persistJob: kotlinx.coroutines.Job? = null

    // 传输记录持久化文件（应用私有目录，重启不丢）
    private val transfersFile: File
        get() = File(getApplication<Application>().filesDir, "transfers.json")

    init {
        // 一次性读取（紧接构造，避免字段竞态）
        runBlocking {
            val knownId = settings.deviceId.first()
            deviceId = if (knownId.isEmpty()) {
                val gen = "og_" + java.util.UUID.randomUUID().toString().take(8)
                runCatching { settings.saveDeviceId(gen) }
                gen
            } else knownId

            val knownName = settings.deviceName.first()
            selfName = if (knownName.isEmpty()) (android.os.Build.MODEL ?: "Android") else knownName
            _name.value = selfName

            val knownDir = settings.saveDir.first()
            saveDir = if (knownDir.isEmpty()) defaultSaveDir() else knownDir

            autoSave.value = settings.autoSave.first()
            saveHistory.value = settings.saveHistory.first()
            saveToGallery.value = settings.saveToGallery.first()
            pinEnabled.value = settings.pinEnabled.first()
            pinCode.value = settings.pinCode.first()
            autoAcceptText.value = settings.autoAcceptText.first()
            integrateImages.value = settings.integrateImages.first()
            retentionDays.value = settings.autoCleanupDays.first()
            autoSaveWhitelist.value = settings.autoSaveWhitelist.first()
            _favorites.value = parseFavorites(settings.favoritesJson.first())
            OgoLang.code.value = settings.language.first()
        }
        loadTransfersFromDisk()
        pruneTransfers()
        restartCore()
        pruneLoop()
        // 「应用」分类：加载完成前保持 loading，避免闪现空态
        _appsLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { _apps.value = loadApps() } finally { _appsLoading.value = false }
        }
    }

    private fun defaultSaveDir(): String {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = File(base, "OrangeGo")
        runCatching { dir.mkdirs() }
        return dir.absolutePath
    }

    private fun restartCore() {
        discovery?.dispose()
        server?.stop()
        lsSender?.dispose()

        _peers.value = emptyMap()
        deviceId = deviceId.ifEmpty { "og_" + java.util.UUID.randomUUID().toString().take(8) }

        // 初始化 LocalSend 设备身份证书（生成/加载 RSA-2048 自签证书，fingerprint 据此稳定）
        runBlocking { withContext(Dispatchers.IO) { runCatching { LsCert.init(getApplication()) } } }
        val deviceModel = android.os.Build.MODEL ?: "Android"

        discovery = DiscoveryManager(
            getApplication(), deviceId, selfName,
            localFingerprint = { runCatching { LsCert.fingerprint() }.getOrDefault("") },
            localDeviceModel = { deviceModel }
        ).also { mgr ->
            mgr.onChanged = { snapshot ->
                // 以 deviceId 为键合并，避免同一设备多地址(双通道)重复出现
                val map = _peers.value.toMutableMap()
                snapshot.forEach { map[it.deviceId] = it }
                _peers.value = map
            }
            mgr.start()
        }

        val srv = ReceiveServer(
            saveDirProvider = { saveDir },
            onIncoming = ::onIncomingBlocking,
            onSaved = null,
            onFileStarted = ::onFileStarted,
            onFileProgress = ::onFileProgress,
            onFileReceived = ::onFileReceived,
            onCanceled = ::onCanceled,
            onFileFailed = ::onFileFailed,
            onMessage = ::onTextMessage,
            onRecall = ::onRecall,
            onLsRegister = ::onLsRegister,
            localAlias = { selfName },
            localFingerprint = { runCatching { LsCert.fingerprint() }.getOrDefault("") },
            localDeviceModel = { deviceModel }
        )
        srv.start()
        server = srv

        sender = SenderClient(deviceId, selfName, FingerprintStore(getApplication()))
        lsSender = LsSender(selfName, deviceModel)
    }

    /** 收到 LocalSend 设备 POST /register：并入设备表（带 isLocalSend 标记）。 */
    private fun onLsRegister(peer: Peer) {
        val map = _peers.value.toMutableMap()
        // 同 IP 已有 OrangeGo 设备时，这台机器就是 OrangeGo，忽略其 LocalSend 兼容通告
        val existByIp = map.values.firstOrNull { it.ip == peer.ip }
        if (existByIp != null && !existByIp.isLocalSend) return
        if (existByIp != null) map.remove(existByIp.deviceId)
        map[peer.deviceId] = peer
        _peers.value = map
    }

    private fun pruneLoop() {
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                val cutoff = System.currentTimeMillis() - 15_000
                val cur = _peers.value
                val fresh = cur.filterValues { it.lastSeen >= cutoff }
                if (fresh.size != cur.size) _peers.value = fresh
            }
        }
    }

    /** 服务器线程同步调用：挂起直到 UI 决定。确认后建接收批次（不建记录，记录在 onFileStarted 建）。 */
    private fun onIncomingBlocking(payload: Protocol.SendInitPayload): String? {
        val future = CompletableFuture<String?>()
        val pinActive = pinEnabled.value && pinCode.value.isNotBlank()
        // 自动接受条件：全局自动保存（且无 PIN） 或 收藏设备白名单自动保存（且无 PIN）
        val auto = (autoSave.value || (autoSaveWhitelist.value && isFavorite(payload.deviceId))) && !pinActive
        val req: PendingReceive? = if (auto) null
            else PendingReceive(nextId(), payload.name, payload.totalFiles, payload.totalSize, future,
                needsPin = pinEnabled.value && pinCode.value.isNotBlank(), sendId = payload.sendId)
        if (req != null) _incoming.value = _incoming.value + req
        else future.complete(saveDir)
        val dir = try { future.get() } finally { if (req != null) _incoming.value = _incoming.value.filterNot { it.id == req.id } }
        if (dir != null) {
            synchronized(recvBatches) {
                recvBatches[payload.sendId] = RecvBatch(payload.name, if (payload.totalFiles > 0) payload.totalFiles else 1, payload.totalSize, files = payload.files.values.toList())
            }
        }
        return dir
    }

    fun accept(req: PendingReceive) {
        if (req.needsPin && !req.pinAccepted) return
        // 批次在 onIncomingBlocking 确认后建立；记录在 onFileStarted 建，无需此处预建。
        req.accept(saveDir)
    }
    fun reject(req: PendingReceive) = req.reject()

    // ---------- 设备收藏夹（白名单） ----------

    fun isFavorite(deviceId: String): Boolean = _favorites.value.any { it.id == deviceId }

    fun toggleFavorite(peer: Peer) {
        val list = _favorites.value.toMutableList()
        val idx = list.indexOfFirst { it.id == peer.deviceId }
        if (idx >= 0) list.removeAt(idx)
        else list.add(FavDevice(peer.deviceId, peer.name, peer.ip))
        _favorites.value = list
        persistFavorites()
    }

    fun removeFavorite(id: String) {
        _favorites.value = _favorites.value.filterNot { it.id == id }
        persistFavorites()
    }

    private fun persistFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { settings.saveFavoritesJson(serializeFavorites(_favorites.value)) }
        }
    }

    private fun serializeFavorites(list: List<FavDevice>): String {
        val arr = org.json.JSONArray()
        list.forEach { f ->
            arr.put(org.json.JSONObject().put("id", f.id).put("name", f.name).put("ip", f.ip))
        }
        return arr.toString()
    }

    private fun parseFavorites(json: String): List<FavDevice> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                FavDevice(o.optString("id"), o.optString("name"), o.optString("ip"))
            }
        }.getOrDefault(emptyList())
    }

    // ---------- 传输记录持久化 ----------

    /** init 时从私有目录加载历史传输记录；进行中的记录在进程重启后视为「失败」。 */
    private fun loadTransfersFromDisk() {
        val file = transfersFile
        if (!file.exists()) return
        val list = runCatching {
            val arr = org.json.JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i)
                TransferItem(
                    id = o.optLong("id"),
                    name = o.optString("name"),
                    direction = o.optString("direction"),
                    target = o.optString("target"),
                    progress = o.optDouble("progress", 0.0).toFloat(),
                    state = if (o.optString("state") == "进行中") "失败" else o.optString("state"),
                    isText = o.optBoolean("isText"),
                    content = o.optString("content"),
                    timestamp = o.optLong("timestamp"),
                    localRef = o.optString("localRef"),
                    localRefs = o.optJSONArray("localRefs")?.let { ja ->
                        (0 until ja.length()).mapNotNull { ja.optString(it).ifEmpty { null } }
                    } ?: emptyList(),
                    sendId = o.optString("sendId"),
                    thumbKeys = o.optJSONArray("thumbKeys")?.let { ja ->
                        (0 until ja.length()).mapNotNull { ja.optString(it).ifEmpty { null } }
                    } ?: emptyList()
                )
            }
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) {
            _transfers.value = list
            // 恢复自增 id，避免与历史记录冲突
            idCounter = maxOf(idCounter, list.maxOfOrNull { it.id } ?: 0L)
        }
    }

    /** 防抖持久化：请求后最多 300ms 内合并写入，避免发送进度高频更新频繁落盘。 */
    private fun schedulePersist() {
        if (persistJob?.isActive == true) return
        persistJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            val snap = _transfers.value.filterNot { it.id in ephemeralIds }
            withContext(Dispatchers.IO) {
                runCatching {
                    val arr = org.json.JSONArray()
                    snap.forEach { t ->
                        arr.put(org.json.JSONObject().apply {
                            put("id", t.id); put("name", t.name); put("direction", t.direction)
                            put("target", t.target); put("progress", t.progress.toDouble())
                            put("state", t.state); put("isText", t.isText); put("content", t.content)
                            put("timestamp", t.timestamp); put("localRef", t.localRef)
                            put("localRefs", org.json.JSONArray(t.localRefs)); put("sendId", t.sendId)
                            put("thumbKeys", org.json.JSONArray(t.thumbKeys))
                        })
                    }
                    transfersFile.parentFile?.mkdirs()
                    transfersFile.writeText(arr.toString())
                }
            }
        }
    }

    /** 长按删除单条传输记录（同时从持久化移除，并回收该条生成的缩略图缓存）。 */
    fun deleteTransfer(id: Long) {
        // 原子更新，避免删除瞬间刚好有接收记录追加时被旧快照覆盖
        val key = _transfers.value.firstOrNull { it.id == id }?.thumbKeys ?: emptyList()
        _transfers.update { list -> list.filterNot { it.id == id } }
        ephemeralIds.remove(id)
        if (lastReceiveId == id) lastReceiveId = null
        if (key.isNotEmpty()) viewModelScope.launch { ThumbCache.deleteKeys(getApplication(), key) }
        schedulePersist()
    }

    /** 一键清空全部传输记录（同时清空持久化与整个缩略图缓存）。 */
    fun clearTransfers() {
        _transfers.value = emptyList()
        ephemeralIds.clear()
        lastReceiveId = null
        viewModelScope.launch { ThumbCache.clearAll(getApplication()) }
        schedulePersist()
    }

    /** 自动清理：设置保留最近 N 天（0=不自动清理），并立即对现有记录执行一次清理。 */
    fun setAutoCleanupDays(v: Int) {
        retentionDays.value = v
        viewModelScope.launch { settings.saveAutoCleanupDays(v) }
        pruneTransfers()
    }

    /** 依据保留天数过滤过旧的传输记录（保留进行中的记录）。 */
    private fun pruneTransfers() {
        val days = retentionDays.value
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        var pruned = false
        val delKeys = mutableListOf<String>()
        // 原子更新：清理期间到达的接收记录不能被旧快照带回
        _transfers.update { list ->
            val keep = list.filter { it.timestamp >= cutoff || it.state == "进行中" }
            if (keep.size != list.size) {
                pruned = true
                list.filterNot { it in keep }.forEach { delKeys.addAll(it.thumbKeys) }
                keep
            } else list
        }
        if (pruned) {
            if (delKeys.isNotEmpty()) viewModelScope.launch { ThumbCache.deleteKeys(getApplication(), delKeys) }
            schedulePersist()
        }
    }

    /** PIN 校验通过后接受接收请求。 */
    fun confirmPin(req: PendingReceive, code: String) {
        if (code.isBlank() || code != pinCode.value) return
        req.pinAccepted = true
        accept(req)
    }

    // ---------- 接收偏好设置 ----------
    fun setAutoSave(v: Boolean) {
        autoSave.value = v
        viewModelScope.launch { settings.saveAutoSave(v) }
    }
    fun setSaveHistory(v: Boolean) {
        saveHistory.value = v
        viewModelScope.launch { settings.saveSaveHistory(v) }
    }
    fun setSaveToGallery(v: Boolean) {
        saveToGallery.value = v
        viewModelScope.launch { settings.saveSaveToGallery(v) }
    }
    fun setPinEnabled(v: Boolean) {
        pinEnabled.value = v
        viewModelScope.launch { settings.savePinEnabled(v) }
    }
    fun setIntegrateImages(v: Boolean) {
        integrateImages.value = v
        viewModelScope.launch { settings.saveIntegrateImages(v) }
    }
    fun setAutoSaveWhitelist(v: Boolean) {
        autoSaveWhitelist.value = v
        viewModelScope.launch { settings.saveAutoSaveWhitelist(v) }
    }
    fun setPinCode(code: String) {
        val c = code.trim()
        pinCode.value = c
        viewModelScope.launch { settings.savePinCode(c) }
    }
    /** 自动接收文本消息开关。 */
    fun setAutoAcceptText(v: Boolean) {
        autoAcceptText.value = v
        viewModelScope.launch { settings.saveAutoAcceptText(v) }
    }

    /** 本次运行内「只上屏、不落盘」的接收记录 id：关闭“保存到历史记录”时，记录仍会显示，只是不写入磁盘。 */
    private val ephemeralIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /** 文件开始接收：建"进行中"记录（首文件触发）。 */
    fun onFileStarted(sendId: String, filePath: String, total: Long, thumb: String? = null) {
        // 原子化：检查并占用 transferId 在同一锁内，避免多文件并发时竞态建多条记录
        // 同时预填所有文件缩略图：从 batch.files 解码 base64 为临时 jpg，传输中即显示全部缩略图，不待落盘
        var newId = 0L
        var from = ""
        var previewRefs = emptyList<String>()
        synchronized(recvBatches) {
            val batch = recvBatches[sendId] ?: return
            if (batch.transferId != 0L) return // 已建记录
            newId = nextId()
            batch.transferId = newId
            from = batch.from
            previewRefs = batch.files.mapIndexedNotNull { i, fm ->
                if (fm.thumb.isNullOrEmpty()) null
                else runCatching {
                    val bytes = Base64.decode(fm.thumb, Base64.NO_WRAP)
                    val isVideo = fm.fileType.startsWith("video/", ignoreCase = true) ||
                        fm.fileName.substringAfterLast('.', "").lowercase() in setOf("mp4","mov","mkv","avi","webm","3gp","flv","wmv","m4v")
                    val prefix = if (isVideo) "vthumb_" else "thumb_"
                    val tmp = java.io.File(getApplication<Application>().cacheDir, "${prefix}${sendId}_$i.jpg")
                    tmp.writeBytes(bytes)
                    tmp.absolutePath
                }.getOrNull()
            }
        }
        // v1 退化：无 files 缩略图时用首文件 thumb 参数
        val refs = if (previewRefs.isNotEmpty()) previewRefs else listOfNotNull(
            if (!thumb.isNullOrEmpty()) runCatching {
                val bytes = Base64.decode(thumb, Base64.NO_WRAP)
                val tmp = java.io.File(getApplication<Application>().cacheDir, "thumb_${sendId}.jpg")
                tmp.writeBytes(bytes)
                tmp.absolutePath
            }.getOrNull() else null
        )
        val t = TransferItem(newId, "接收文件中", "接收", from,
            progress = 0f, state = "进行中", timestamp = System.currentTimeMillis(), sendId = sendId,
            localRef = refs.firstOrNull() ?: "", localRefs = refs)
        if (!saveHistory.value) ephemeralIds.add(t.id)
        lastReceiveId = t.id
        _transfers.update { it + t }
        android.util.Log.i("OrangeGO.Recv", "onFileStarted sendId=$sendId file=$filePath refs=$refs newId=$newId")
        schedulePersist()
        if (saveToGallery.value) {
            viewModelScope.launch {
                delay(2000)
                runCatching { MediaScannerConnection.scanFile(getApplication(), arrayOf(saveDir), null, null) }
            }
        }
    }

    /** 文件接收进度更新。EMA 平滑剩余时间（消除末位抖动），显示 剩余时间 + 已传输/总大小。 */
    fun onFileProgress(sendId: String, filePath: String, written: Long, total: Long) {
        val batch = synchronized(recvBatches) { recvBatches[sendId] } ?: return
        val id = batch.transferId
        if (id == 0L) return
        val frac = if (total > 0) written.toFloat() / total else 1f
        val prog = if (batch.totalFiles > 0) (batch.done + frac) / batch.totalFiles else frac
        val clamped = prog.coerceIn(0f, 1f)
        val pt = if (clamped > 0.02f) {
            val elapsed = System.currentTimeMillis() - batch.startTime
            val remainInstant = elapsed.toDouble() * (1 - clamped) / clamped
            // EMA 平滑剩余时间（α=0.3）：消除瞬时估算回跳，趋势单调下降
            val remainEma = if (batch.remainEma < 0) remainInstant
                else 0.3 * remainInstant + 0.7 * batch.remainEma
            synchronized(recvBatches) { recvBatches[sendId]?.remainEma = remainEma }
            val transferred = (batch.totalBytes * clamped).toLong()
            "剩余 ${formatDuration(remainEma.toLong())} · ${formatBytes(transferred)}/${formatBytes(batch.totalBytes)}"
        } else ""
        updateTransfer(id) { it.copy(progress = clamped, progressText = pt) }
    }

    private fun formatBytes(b: Long): String {
        if (b < 1024) return "${b} B"
        val kb = b / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        return "%.2f GB".format(mb / 1024.0)
    }

    private fun formatDuration(ms: Long): String {
        if (ms < 0) return "--"
        val s = ms / 1000
        return when {
            s < 60 -> "${s}秒"
            s < 3600 -> "${s / 60}分${s % 60}秒"
            else -> "${s / 3600}时${(s % 3600) / 60}分"
        }
    }

    /** 单文件落盘完成：回填路径到记录 localRefs；全部文件完成则标"完成"。 */
    fun onFileReceived(sendId: String, filePath: String, size: Long) {
        val batch = synchronized(recvBatches) { recvBatches[sendId] }
        val id = batch?.transferId ?: lastReceiveId
        if (id != null && id != 0L) {
            val fname = File(filePath).name
            val idx = batch?.files?.indexOfFirst { it.fileName == fname } ?: -1
            updateTransfer(id) {
                val fileRef = "file://$filePath"
                val refs = it.localRefs.toMutableList()
                if (idx in refs.indices) {
                    refs[idx] = fileRef
                } else if (fileRef !in refs) {
                    refs.add(fileRef)
                }
                it.copy(localRefs = refs, localRef = fileRef, name = fname)
            }
            android.util.Log.i("OrangeGO.Recv", "onFileReceived sendId=$sendId file=$filePath idx=$idx localRefs=${_transfers.value.firstOrNull { it.id == id }?.localRefs}")
            // 首文件落盘时提示顶部横幅（多文件只触发一次）
            if (_transfers.value.firstOrNull { it.id == id }?.localRefs?.count { it.startsWith("file://") } == 1) {
                val from = _transfers.value.firstOrNull { it.id == id }?.target ?: ""
                receivedNotice.value = "F:$from"
            }
        }
        if (batch != null) {
            val allDone = synchronized(recvBatches) {
                val b = recvBatches[sendId] ?: return@synchronized false
                b.done++
                b.done >= b.totalFiles
            }
            if (allDone && id != null && id != 0L) {
                updateTransfer(id) { it.copy(progress = 1f, state = "完成") }
                synchronized(recvBatches) { recvBatches.remove(sendId) }
            }
        } else if (id != null) {
            updateTransfer(id) { it.copy(progress = 1f, state = "完成") }
        }
    }

    /** 对方取消发送：标"已取消" + 删除已落盘半文件 + 清理批次。 */
    fun onCanceled(sendId: String) {
        val batch = synchronized(recvBatches) { recvBatches[sendId] }
        val id = batch?.transferId
        if (id != null && id != 0L) {
            val item = _transfers.value.firstOrNull { it.id == id }
            item?.localRefs?.forEach { ref ->
                if (ref.startsWith("file://")) runCatching { File(ref.removePrefix("file://")).delete() }
            }
            updateTransfer(id) { it.copy(state = "已取消") }
        }
        synchronized(recvBatches) { recvBatches.remove(sendId) }
    }

    /** 传输失败（连接断开等）：标"失败" + 删半文件 + 清理批次。 */
    fun onFileFailed(sendId: String, filePath: String) {
        runCatching { File(filePath).delete() }
        val batch = synchronized(recvBatches) { recvBatches[sendId] }
        val id = batch?.transferId
        if (id != null && id != 0L) {
            updateTransfer(id) { it.copy(state = "失败") }
        }
        synchronized(recvBatches) { recvBatches.remove(sendId) }
    }

    fun refresh() { discovery?.broadcastNow() }

    fun rename(newName: String) {
        if (newName.isBlank() || newName == selfName) return
        selfName = newName.trim()
        _name.value = selfName
        viewModelScope.launch { settings.saveDeviceName(selfName) }
        restartCore()
    }

    fun changeSaveDir(newDir: String) {
        if (newDir.isBlank() || newDir == saveDir) return
        val f = File(newDir)
        runCatching { f.mkdirs() }
        saveDir = newDir
        viewModelScope.launch { settings.saveSaveDir(saveDir) }
        // 接收服务每次通过 saveDirProvider 读取最新 saveDir，无需重启
    }

    fun resetSaveDir() = changeSaveDir(defaultSaveDir())

    /** 应用内切换主题（0=跟随系统，1=浅色，2=深色）。 */
    fun setThemeMode(mode: Int) {
        OgoThemeMode.mode.value = mode
        viewModelScope.launch { settings.saveThemeMode(mode) }
    }

    /** 应用内切换界面语言（ISO 代码）。 */
    fun setLanguage(code: String) {
        OgoLang.code.value = code
        viewModelScope.launch { settings.saveLanguage(code) }
    }

    override fun onCleared() {
        discovery?.dispose()
        server?.stop()
    }

    // ---------- 发送 ----------
    // 进行中的发送任务（transferId → Job），供"取消"使用
    private val sendJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()

    private val imgExts = setOf("png","jpg","jpeg","bmp","gif","webp","heic","heif","dng")
    private val vidExts = setOf("mp4","mov","mkv","avi","wmv","webm","flv","m4v")

    /** 生成 base64 JPEG 缩略图（图片解码缩放 / 视频取首帧），失败返回 null。 */
    private suspend fun generateThumb(uri: Uri, isVideo: Boolean): String? = withContext(Dispatchers.IO) {
        runCatching {
            val app = getApplication<Application>()
            val maxDim = 256
            val bmp = if (isVideo) {
                val retriever = MediaMetadataRetriever()
                try { retriever.setDataSource(app, uri); retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) }
                finally { retriever.release() }
            } else {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    // 标准解码失败（如 DNG）：提取内嵌 JPEG 预览
                    com.orangeway.go.core.extractEmbeddedJpeg(app, uri)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                } else {
                    val inSample = Integer.highestOneBit(maxOf(opts.outWidth, opts.outHeight) / maxDim).coerceAtLeast(1)
                    val decodeOpts = BitmapFactory.Options().apply { inSampleSize = inSample }
                    app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
                }
            } ?: return@runCatching null
            val scaled = if (maxOf(bmp.width, bmp.height) > maxDim) {
                val ratio = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else bmp
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 60, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    fun sendFiles(peer: Peer, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val files = uris.mapNotNull { it.toOutgoing() }
        if (files.isEmpty()) return
        // LocalSend 设备走 HTTPS + mTLS 客户端；OrangeGo 自研走明文
        if (peer.isLocalSend) {
            val title = if (files.size == 1) files[0].name else "${files.size} 个文件"
            sendFilesLs(peer, files, title, uris.map { it.toString() })
            return
        }
        val target = Peer(peer.deviceId, peer.name, peer.ip, peer.port)

        // 协程内部用 coroutineContext[Job] 取到自身 Job 完成登记，供「取消」使用。
        // 不能用外层可变 holder：主线程下 Dispatchers.Main.immediate 会立即同步执行协程体到第一个挂起点，
        // 此时外层 holder 尚未赋值，`holder!!` 会抛 NPE。
        viewModelScope.launch {
            val sendId = java.util.UUID.randomUUID().toString()
            val title = if (files.size == 1) files[0].name else "${files.size} 个文件"
            val firstLocal = uris.getOrNull(0)?.toString() ?: ""
            val t = addTransfer(title, "发送", target.name, localRef = firstLocal, localRefs = uris.map { it.toString() }, sendId = sendId)
            sendJobs[t.id] = coroutineContext[kotlinx.coroutines.Job] ?: return@launch // 登记可取消的任务
            Log.i("OrangeGO.Send", "sendFiles registered job for t.id=${t.id}")
            var cancelled = false
            var failed = false
            try {
                val s = sender!!
                val filesWithThumb = files.mapIndexed { i, f ->
                    val ext = f.name.substringAfterLast('.', "").lowercase()
                    val thumb = when {
                        ext in imgExts -> generateThumb(uris[i], false)
                        ext in vidExts -> generateThumb(uris[i], true)
                        else -> null
                    }
                    if (thumb != null) f.copy(thumb = thumb) else f
                }
                val prepared = s.prepare(target.ip, target.port, sendId, filesWithThumb)
                if (prepared.tokens.isEmpty()) {
                    updateTransfer(t.id) { it.copy(progress = 1f, state = "被拒") }
                    return@launch
                }
                // P1: 并行多文件上传（OkHttp 连接池自然限流 5 并发/主机），聚合进度
                val total = files.sumOf { it.size }.toFloat()
                val perFileBytes = files.map { java.util.concurrent.atomic.AtomicLong(0) }
                val startTime = System.currentTimeMillis()
                var remainEma = -1.0
                kotlinx.coroutines.coroutineScope {
                    files.forEachIndexed { i, f ->
                        launch {
                            val token = prepared.tokens[f.id] ?: return@launch
                            s.uploadFile(target.ip, target.port, prepared.sessionId, f.id, token, f) { frac ->
                                perFileBytes[i].set((frac * f.size).toLong())
                                val aggregate = perFileBytes.sumOf { it.get() }
                                val prog = aggregate / total
                                val pt = if (prog > 0.02f && prog < 1f) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed > 0) {
                                        val remainInstant = elapsed.toDouble() * (1 - prog) / prog
                                        remainEma = if (remainEma < 0) remainInstant else 0.3 * remainInstant + 0.7 * remainEma
                                        "剩余 ${formatDuration(remainEma.toLong())} · ${formatBytes(aggregate)}/${formatBytes(total.toLong())}"
                                    } else ""
                                } else ""
                                updateTransfer(t.id) { it.copy(progress = prog, progressText = pt) }
                            }
                        }
                    }
                }
                updateTransfer(t.id) { it.copy(progress = 1f, state = "完成") }
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelled = true
                updateTransfer(t.id) { it.copy(state = "已取消") }
                Log.i("OrangeGO.Send", "发送已取消 target=${target.ip}:${target.port} id=$sendId")
            } catch (e: Exception) {
                failed = true
                Log.e("OrangeGO.Send", "sendFiles 失败 target=${target.ip}:${target.port} id=$sendId files=${files.size}", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
                receivedNotice.value = "E:发送失败：目标不可达或连接中断"
            } finally {
                if (failed || cancelled) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { sender?.cancel(target.ip, target.port, sendId) }
                }
                sendJobs.remove(t.id)
            }
        }
    }

    /** 发送文件给 LocalSend 设备（HTTPS + mTLS 客户端证书 + 指纹钉扎）。 */
    private fun sendFilesLs(peer: Peer, files: List<OutgoingFile>, title: String, localRefs: List<String>) {
        viewModelScope.launch {
            val sendId = java.util.UUID.randomUUID().toString()
            val firstLocal = localRefs.firstOrNull() ?: ""
            val t = addTransfer(title, "发送", peer.name, localRef = firstLocal, localRefs = localRefs, sendId = sendId)
            sendJobs[t.id] = coroutineContext[kotlinx.coroutines.Job] ?: return@launch
            var cancelled = false
            var failed = false
            var lsSessionId = ""
            val protocol = peer.protocol.ifEmpty { "https" }
            try {
                val ls = lsSender ?: throw IOException("LocalSend 发送器未就绪")
                val prepared = ls.prepareUpload(peer.ip, peer.port, peer.fingerprint, protocol, files)
                lsSessionId = prepared.sessionId
                if (prepared.tokens.isEmpty()) {
                    updateTransfer(t.id) { it.copy(progress = 1f, state = "被拒") }
                    return@launch
                }
                val total = files.sumOf { it.size }.coerceAtLeast(1L)
                var done = 0L
                for (f in files) {
                    val token = prepared.tokens[f.id] ?: continue
                    ls.uploadFile(peer.ip, peer.port, peer.fingerprint, protocol, prepared.sessionId, f.id, token, f) { frac ->
                        updateTransfer(t.id) { it.copy(progress = ((done + frac * f.size) / total).toFloat()) }
                    }
                    done += f.size
                }
                updateTransfer(t.id) { it.copy(progress = 1f, state = "完成") }
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelled = true
                updateTransfer(t.id) { it.copy(state = "已取消") }
                Log.i("OrangeGO.Send", "LS 发送已取消 target=${peer.ip}:${peer.port} id=$sendId")
            } catch (e: LsSender.LsSendException) {
                failed = true
                val msg = when (e.statusCode) {
                    401 -> "对方要求 PIN"
                    403 -> "对方拒绝了本次发送"
                    409 -> "对方忙（已有进行中的接收会话）"
                    429 -> "PIN 试错过多，请稍后再试"
                    else -> "发送失败 (${e.statusCode})"
                }
                updateTransfer(t.id) { it.copy(state = "失败") }
                receivedNotice.value = "E:$msg"
            } catch (e: Exception) {
                failed = true
                Log.e("OrangeGO.Send", "sendFilesLs 失败 target=${peer.ip}:${peer.port} id=$sendId", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
                receivedNotice.value = "E:发送失败：目标不可达或连接中断"
            } finally {
                if ((failed || cancelled) && lsSessionId.isNotEmpty())
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                        runCatching { lsSender?.cancel(peer.ip, peer.port, peer.fingerprint, protocol, lsSessionId) }
                    }
                sendJobs.remove(t.id)
            }
        }
    }

    /** 取消某个进行中的发送任务；未完成则中止并通知对端。 */
    fun cancelSend(id: Long) {
        val job = sendJobs.remove(id)

        Log.i("OrangeGO.Send", "cancelSend id=$id jobFound=${job != null} isActive=${job?.isActive}")
        job?.cancel()
    }

    private fun addTransfer(name: String, direction: String, target: String, localRef: String = "",
                            localRefs: List<String> = emptyList(),
                            isText: Boolean = false, content: String = "", sendId: String = ""): TransferItem {
        val t = TransferItem(nextId(), name, direction, target, timestamp = System.currentTimeMillis(),
            localRef = localRef, localRefs = localRefs, isText = isText, content = content, sendId = sendId)
        _transfers.update { it + t }
        schedulePersist()
        return t
    }

    /** 发送文字：走文本消息命令（真实聊天气泡），不打包成 txt 文件。发送成功标"完成"，失败标"失败"。 */
    fun sendTextTo(peer: Peer, text: String) {
        if (text.isBlank() || peer.ip.isEmpty()) return
        // LocalSend 协议不支持文本消息，仅支持文件互传
        if (peer.isLocalSend) {
            receivedNotice.value = "E:LocalSend 设备仅支持文件互传，不支持发送文字"
            return
        }
        val msg = Protocol.TextMessage(
            sendId = java.util.UUID.randomUUID().toString(),
            deviceId = deviceId,
            name = selfName,
            content = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        val t = addTransfer("文字", "发送", peer.name, isText = true, content = text.trim(), sendId = msg.sendId)
        viewModelScope.launch {
            val s = sender
            if (s == null) { updateTransfer(t.id) { it.copy(state = "失败") }; receivedNotice.value = "E:发送失败：目标不可达或连接中断"; return@launch }
            try {
                s.sendMessage(peer.ip, peer.port, msg)
                updateTransfer(t.id) { it.copy(state = "完成") }
            } catch (e: Exception) {
                Log.e("OrangeGO.Send", "sendTextMessage 失败 target=${peer.ip}:${peer.port}", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
                receivedNotice.value = "E:发送失败：目标不可达或连接中断"
            }
        }
    }

    /** 收到文本消息：生成接收聊天气泡记录。开关关闭时不自动接收文本。 */
    fun onTextMessage(m: Protocol.TextMessage) {
        // 开关关闭时不自动接收文本：不生成接收气泡
        if (!autoAcceptText.value) return
        val t = TransferItem(nextId(), "文字", "接收", m.name,
            progress = 1f, state = "完成", isText = true, content = m.content, timestamp = m.timestamp.let { if (it > 0) it else System.currentTimeMillis() }, sendId = m.sendId)
        lastReceiveId = t.id
        _transfers.update { it + t }
        schedulePersist()
        // 接收文本：触发顶部横幅提醒
        receivedNotice.value = "T:${m.name}"
    }

    /** 该发送记录当前是否允许撤回（仅发送方、2 分钟内、且状态未撤回）。 */
    fun canRecall(t: TransferItem): Boolean =
        t.direction == "发送" && t.state != "已撤回" &&
            t.sendId.isNotEmpty() &&
            (System.currentTimeMillis() - t.timestamp) <= 120_000L

    /** 撤回某条已发送记录：本地标「已撤回」，并向所有在线对端广播撤回命令（接收端按 sendId 匹配，只有真正的接收端会命中）。 */
    fun recall(id: Long) {
        var sendId = ""
        var recalledKeys: List<String> = emptyList()
        // 与接收线程的追加并发，读改写必须原子，否则会丢掉刚到达的接收记录
        _transfers.update { list ->
            val t = list.firstOrNull { it.id == id }
            if (t == null || !canRecall(t)) return@update list
            sendId = t.sendId
            recalledKeys = t.thumbKeys
            list.map { if (it.id == id) t.copy(state = "已撤回") else it }
        }
        if (sendId.isEmpty()) return
        if (recalledKeys.isNotEmpty()) viewModelScope.launch { ThumbCache.deleteKeys(getApplication(), recalledKeys) }
        schedulePersist()
        val snapshot = _peers.value.values.toList()
        viewModelScope.launch {
            snapshot.forEach { peer -> runCatching { sender?.sendRecall(peer.ip, peer.port, sendId) } }
        }
    }

    /** 收到对端撤回命令：把本机对应「接收」记录标记为「已撤回」（按 sendId 匹配）。 */
    fun onRecall(sendId: String) {
        if (sendId.isEmpty()) return
        var changed = false
        val delKeys = mutableListOf<String>()
        // 撤回命令走服务器线程，与主线程的新增/更新并发，读改写必须原子
        _transfers.update { list ->
            val next = list.map { t ->
                if (t.direction == "接收" && t.sendId == sendId && t.state != "已撤回") {
                    changed = true; delKeys.addAll(t.thumbKeys); t.copy(state = "已撤回")
                } else t
            }
            if (changed) next else list
        }
        if (changed) {
            if (delKeys.isNotEmpty()) viewModelScope.launch { ThumbCache.deleteKeys(getApplication(), delKeys) }
            schedulePersist()
        }
    }

    private fun updateTransfer(id: Long, block: (TransferItem) -> TransferItem) {
        // 进度/落盘回调与新增记录并发，读改写必须原子，否则新追加的记录会被旧快照覆盖掉
        _transfers.update { list -> list.map { if (it.id == id) block(it) else it } }
        schedulePersist()
    }

    /** 某条记录的媒体缩略图已写入磁盘缓存后，把其 key 记入该记录（供删除/撤回/清空时精确回收缓存）。 */
    fun updateThumbKey(id: Long, ref: String, key: String) {
        if (key.isBlank()) return
        _transfers.update { list ->
            list.map { t ->
                if (t.id != id || key in t.thumbKeys) t
                else t.copy(thumbKeys = t.thumbKeys + key)
            }
        }
        schedulePersist()
    }

    /**
     * 发送整个文件夹：递归收集根目录下全部文件，携带各自相对根目录的相对路径（`/` 分隔），
     * 接收端据此重建目录结构。文件名透传（本地真实文件名），大小来自 File.length。
     */
    fun sendFolder(peer: Peer, rootDir: java.io.File) {
        val root = rootDir.absoluteFile
        if (!root.isDirectory) return
        val target = Peer(peer.deviceId, peer.name, peer.ip, peer.port)
        val app = getApplication<Application>()

        viewModelScope.launch {
            // 收集 (OutgoingFile, 对应 file: 绝对路径) 列表，供缩略图本地引用
            val collected = kotlinx.coroutines.withContext(Dispatchers.IO) {
                val localFiles = ArrayList<java.io.File>()
                val list = ArrayList<OutgoingFile>()
                root.walkTopDown().filter { it.isFile }.forEach { f ->
                    localFiles.add(f)
                    val size = runCatching { f.length() }.getOrDefault(0L)
                    val rel = runCatching { root.toRelativeString(f.parentFile ?: root) }.getOrDefault("")
                    val relPath = if (rel.isBlank()) null
                    else "$rel/${f.name}".replace('\\', '/')
                    list.add(OutgoingFile(
                        id = java.util.UUID.randomUUID().toString(),
                        name = f.name, size = size,
                        open = { java.io.FileInputStream(f).use { it } },
                        relativePath = relPath
                    ))
                }
                list to localFiles
            }
            if (collected.first.isEmpty()) return@launch
            val files = collected.first
            val localFiles = collected.second
            val sendId = java.util.UUID.randomUUID().toString()
            // 用文件夹本身的 file:// 路径作为主引用，其余文件作为子引用（缩略图/预览用）
            val localRefs = localFiles.map { runCatching {
                androidx.core.content.FileProvider.getUriForFile(app, "com.orangeway.go.fileprovider", it).toString()
            }.getOrDefault(it.absolutePath) }
            // LocalSend 设备走 mTLS 发送（文件夹会平铺，LocalSend FileDto 无 relativePath）
            if (peer.isLocalSend) { sendFilesLs(peer, files, root.name, localRefs); return@launch }
            val t = addTransfer(root.name, "发送", target.name,
                localRef = localRefs.firstOrNull().orEmpty(), localRefs = localRefs, sendId = sendId)
            sendJobs[t.id] = coroutineContext[kotlinx.coroutines.Job] ?: return@launch
            var cancelled = false
            var failed = false
            try {
                val s = sender!!
                val filesWithThumb = files.mapIndexed { i, f ->
                    val ext = f.name.substringAfterLast('.', "").lowercase()
                    val thumb = when {
                        ext in imgExts -> generateThumb(Uri.fromFile(localFiles[i]), false)
                        ext in vidExts -> generateThumb(Uri.fromFile(localFiles[i]), true)
                        else -> null
                    }
                    if (thumb != null) f.copy(thumb = thumb) else f
                }
                val prepared = s.prepare(target.ip, target.port, sendId, filesWithThumb)
                if (prepared.tokens.isEmpty()) {
                    updateTransfer(t.id) { it.copy(progress = 1f, state = "被拒") }
                    return@launch
                }
                // P1: 并行多文件上传（OkHttp 连接池自然限流 5 并发/主机），聚合进度
                val total = files.sumOf { it.size }.toFloat()
                val perFileBytes = files.map { java.util.concurrent.atomic.AtomicLong(0) }
                val startTime = System.currentTimeMillis()
                var remainEma = -1.0
                kotlinx.coroutines.coroutineScope {
                    files.forEachIndexed { i, f ->
                        launch {
                            val token = prepared.tokens[f.id] ?: return@launch
                            s.uploadFile(target.ip, target.port, prepared.sessionId, f.id, token, f) { frac ->
                                perFileBytes[i].set((frac * f.size).toLong())
                                val aggregate = perFileBytes.sumOf { it.get() }
                                val prog = aggregate / total
                                val pt = if (prog > 0.02f && prog < 1f) {
                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (elapsed > 0) {
                                        val remainInstant = elapsed.toDouble() * (1 - prog) / prog
                                        remainEma = if (remainEma < 0) remainInstant else 0.3 * remainInstant + 0.7 * remainEma
                                        "剩余 ${formatDuration(remainEma.toLong())} · ${formatBytes(aggregate)}/${formatBytes(total.toLong())}"
                                    } else ""
                                } else ""
                                updateTransfer(t.id) { it.copy(progress = prog, progressText = pt) }
                            }
                        }
                    }
                }
                updateTransfer(t.id) { it.copy(progress = 1f, state = "完成") }
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelled = true
                updateTransfer(t.id) { it.copy(state = "已取消") }
                Log.i("OrangeGO.Send", "文件夹发送已取消 target=${target.ip}:${target.port} id=$sendId")
            } catch (e: Exception) {
                failed = true
                Log.e("OrangeGO.Send", "sendFolder 失败 target=${target.ip}:${target.port} id=$sendId files=${files.size}", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
                receivedNotice.value = "E:发送失败：目标不可达或连接中断"
            } finally {
                if (failed || cancelled) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    runCatching { sender?.cancel(target.ip, target.port, sendId) }
                }
                sendJobs.remove(t.id)
            }
        }
    }

    private fun Uri.toOutgoing(): OutgoingFile? {
        val app = getApplication<Application>()
        return runCatching {
            val cr = app.contentResolver
            var name = "file"; var size = -1L
            cr.query(this, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
            if (size < 0) size = cr.openInputStream(this)?.available()?.toLong() ?: 0L
            OutgoingFile(id = java.util.UUID.randomUUID().toString(), name = name, size = size, open = { cr.openInputStream(this)!! })
        }.getOrNull()
    }

    // ---------- 应用（打包 APK 分享） ----------
    /** 安装包扫描结果缓存：首次扫描后不再重复，切走再切回立即展示。 */
    var apkCache: List<File>? = null

    /** 首次调用扫描内部存储的 .apk 并缓存；后续直接返回缓存。 */
    suspend fun loadApks(): List<File> = withContext(Dispatchers.IO) {
        apkCache?.let { return@withContext it }
        val root = Environment.getExternalStorageDirectory()
        val out = ArrayList<File>()
        val stack = ArrayDeque<File>().also { it.addLast(root) }
        val exts = setOf("apk")
        val limit = 500
        while (stack.isNotEmpty() && out.size < limit) {
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (f in children) {
                if (f.isHidden) continue
                if (f.isDirectory) stack.addLast(f)
                else if (f.extension.lowercase(java.util.Locale.ROOT) in exts) {
                    out.add(f)
                    if (out.size >= limit) break
                }
            }
        }
        out.sortedBy { it.name }.also { apkCache = it }
    }

    private fun loadApps(): List<InstalledApp> {
        val ctx = getApplication<Application>()
        return runCatching {
            val pm = ctx.packageManager
            pm.getInstalledApplications(0).mapNotNull { info ->
                runCatching {
                    val label = pm.getApplicationLabel(info).toString()
                    val icon: Drawable? = pm.getApplicationIcon(info.packageName)
                    // 判断标准与系统设置/Play 商店一致（官方唯一权威依据）：
                    // 系统预装 = FLAG_SYSTEM；系统预装且被 OTA/商店更新 = FLAG_UPDATED_SYSTEM_APP。
                    // 注意：通过应用商店安装的第三方应用（含部分 Google 应用）会被系统标记为无 SYSTEM 的"用户应用"。
                    val system = (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                        (info.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    InstalledApp(label, info.packageName, icon,
                        info.sourceDir ?: return@runCatching null, system)
                }.getOrNull()
            }.sortedBy { it.label }
        }.getOrDefault(emptyList())
    }

    /** 把指定应用复制成 APK 到缓存，返回可发送的 content Uri。 */
    suspend fun exportApp(app: InstalledApp): Exported? = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        runCatching {
            val dir = File(ctx.cacheDir, "apk_export").apply { mkdirs() }
            val clean = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val dst = File(dir, "$clean.apk")
            File(app.apkPath).inputStream().use { inp -> dst.outputStream().use { inp.copyTo(it) } }
            val uri = FileProvider.getUriForFile(ctx, "com.orangeway.go.fileprovider", dst)
            Exported(uri, "$clean.apk")
        }.getOrNull()
    }

    // ---------- 相册媒体（图片 + 视频） ----------
    fun refreshMedia() {
        _mediaLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { _media.value = queryMedia() } finally { _mediaLoading.value = false }
        }
    }

    private data class MediaRow(
        val uri: Uri,
        val name: String,
        val isVideo: Boolean,
        val bucketId: String,
        val bucket: String,
        val dateAdded: Long = 0L
    )

    private fun queryMedia(): List<MediaItem> {
        val rows = ArrayList<MediaRow>()
        // 全量读取图片与视频，不做数量上限；同相册（相同 bucketId）的图片和视频可在 UI 侧归并
        rows += queryMediaCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        rows += queryMediaCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
        // 合并后按添加时间统一降序排序（避免图片块在前视频块在后的分块排列）
        rows.sortByDescending { it.dateAdded }
        return rows.map { MediaItem(it.uri, it.name, it.isVideo, it.bucketId, it.bucket) }
    }

    private fun queryMediaCollection(uri: Uri, isVideo: Boolean): List<MediaRow> {
        val cr = getApplication<Application>().contentResolver
        return runCatching {
            val projection = arrayOf(
                BaseColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.BUCKET_ID,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME
            )
            val out = ArrayList<MediaRow>()
            cr.query(
                uri, projection, null, null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(BaseColumns._ID)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketIdIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: if (isVideo) "video" else "image"
                    val dateAdded = if (!c.isNull(dateIdx)) c.getLong(dateIdx) else 0L
                    // BUCKET_ID 在某些版本是数字、某些是字符串，统一按字符串读取并 fallback 到行 id
                    val bucketId = c.getString(bucketIdIdx)
                        ?: MediaStore.MediaColumns.DISPLAY_NAME + "_" + id
                    val bucket = c.getString(bucketIdx) ?: "相册"
                    out.add(
                        MediaRow(
                            ContentUris.withAppendedId(uri, id), name, isVideo, bucketId, bucket, dateAdded
                        )
                    )
                }
            }
            out
        }.getOrDefault(emptyList())
    }

    // ---------- 音频 ----------
    fun refreshAudio() {
        _audioLoading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try { _audio.value = queryAudio() } finally { _audioLoading.value = false }
        }
    }

    private fun queryAudio(): List<AudioItem> {
        val cr = getApplication<Application>().contentResolver
        return runCatching {
            val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                BaseColumns._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DISPLAY_NAME
            )
            val out = ArrayList<AudioItem>()
            cr.query(uri, projection, null, null, "${MediaStore.Audio.Media.TITLE} ASC")?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(BaseColumns._ID)
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dispIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    out.add(
                        AudioItem(
                            ContentUris.withAppendedId(uri, id),
                            // 优先用文件名（含后缀），让用户看清是哪个文件
                            c.getString(dispIdx) ?: c.getString(titleIdx) ?: "音频",
                            c.getString(artistIdx) ?: "",
                            c.getLong(durIdx)
                        )
                    )
                }
            }
            out
        }.getOrDefault(emptyList())
    }
}

data class InstalledApp(val label: String, val packageName: String, val icon: Drawable?, val apkPath: String, val isSystem: Boolean)
data class Exported(val uri: Uri, val name: String)

/** 相册媒体条目（供「媒体」分类展示）。bucketId 用于把同相册的图片和视频归并。 */
data class MediaItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val bucketId: String,
    val bucket: String
)

/** 音频条目（供「音频」分类展示）。 */
data class AudioItem(val uri: Uri, val title: String, val artist: String, val durationMs: Long)

/** 文件条目（供「文件」分类浏览）。 */
data class FileItem(val uri: Uri, val name: String, val size: Long, val mimeType: String)