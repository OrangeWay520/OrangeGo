package com.orangeway.go

import android.app.Application
import android.content.ContentUris
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orangeway.go.core.DiscoveryManager
import com.orangeway.go.core.OutgoingFile
import com.orangeway.go.core.Peer
import com.orangeway.go.core.Protocol
import com.orangeway.go.core.ReceiveServer
import com.orangeway.go.core.SenderClient
import com.orangeway.go.data.SettingsRepo
import com.orangeway.go.ui.OgoLang
import com.orangeway.go.ui.OgoThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CompletableFuture

data class PendingReceive(
    val id: Long,
    val deviceName: String,
    val fileCount: Int,
    val totalBytes: Long,
    private val future: CompletableFuture<String?>,
    val needsPin: Boolean = false,
    var pinAccepted: Boolean = false
) {
    fun accept(saveDir: String) = future.complete(saveDir)
    fun reject() = future.complete(null)
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
    val localRefs: List<String> = emptyList() // 全部文件的可预览地址：content:// 或 file:// 字符串；空列表=未知
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
    // 传输记录自动清理：保留最近 N 天的记录（0=不自动清理）
    val retentionDays = MutableStateFlow(0)

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
    private var idCounter = 0L
    private var lastReceiveId: Long? = null // 最近一条接收记录 id，用于 onSaved 回填文件路径
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
            retentionDays.value = settings.autoCleanupDays.first()
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

        _peers.value = emptyMap()
        deviceId = deviceId.ifEmpty { "og_" + java.util.UUID.randomUUID().toString().take(8) }

        discovery = DiscoveryManager(getApplication(), deviceId, selfName).also { mgr ->
            mgr.onChanged = { snapshot ->
                // 以 deviceId 为键合并，避免同一设备多地址(双通道)重复出现
                val map = _peers.value.toMutableMap()
                snapshot.forEach { map[it.deviceId] = it }
                _peers.value = map
            }
            mgr.start()
        }

        val srv = ReceiveServer(saveDirProvider = { saveDir }, onIncoming = ::onIncomingBlocking, onSaved = ::onSaved, onMessage = ::onTextMessage)
        srv.start()
        server = srv

        sender = SenderClient(deviceId, selfName)
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

    /** 服务器线程同步调用：挂起直到 UI 决定。 */
    private fun onIncomingBlocking(payload: Protocol.SendInitPayload): String? {
        val future = CompletableFuture<String?>()
        // 自动保存开启：直接保存到收件目录，无需弹窗确认
        if (autoSave.value) {
            val dir = saveDir
            if (pinEnabled.value && pinCode.value.isNotBlank()) {
                // 开启 PIN 时仍需确认，交由 UI 弹窗校验
                val req = PendingReceive(++idCounter, payload.name, payload.totalFiles, payload.totalSize, future, needsPin = true)
                _incoming.value = _incoming.value + req
                try { return future.get() } finally { _incoming.value = _incoming.value.filterNot { it.id == req.id } }
            }
            future.complete(dir)
            addReceiveRecord(payload.name)
            return dir
        }
        val req = PendingReceive(++idCounter, payload.name, payload.totalFiles, payload.totalSize, future)
        _incoming.value = _incoming.value + req
        try { return future.get() } finally { _incoming.value = _incoming.value.filterNot { it.id == req.id } }
    }

    fun accept(req: PendingReceive) {
        if (req.needsPin && !req.pinAccepted) return
        req.accept(saveDir)
        addReceiveRecord(req.deviceName)
    }
    fun reject(req: PendingReceive) = req.reject()

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
            val snap = _transfers.value
            withContext(Dispatchers.IO) {
                runCatching {
                    val arr = org.json.JSONArray()
                    snap.forEach { t ->
                        arr.put(org.json.JSONObject().apply {
                            put("id", t.id); put("name", t.name); put("direction", t.direction)
                            put("target", t.target); put("progress", t.progress.toDouble())
                            put("state", t.state); put("isText", t.isText); put("content", t.content)
                            put("timestamp", t.timestamp); put("localRef", t.localRef)
                            put("localRefs", org.json.JSONArray(t.localRefs))
                        })
                    }
                    transfersFile.parentFile?.mkdirs()
                    transfersFile.writeText(arr.toString())
                }
            }
        }
    }

    /** 长按删除单条传输记录（同时从持久化移除）。 */
    fun deleteTransfer(id: Long) {
        _transfers.value = _transfers.value.filterNot { it.id == id }
        if (lastReceiveId == id) lastReceiveId = null
        schedulePersist()
    }

    /** 一键清空全部传输记录（同时清空持久化）。 */
    fun clearTransfers() {
        _transfers.value = emptyList()
        lastReceiveId = null
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
        val cur = _transfers.value
        val keep = cur.filter { it.timestamp >= cutoff || it.state == "进行中" }
        if (keep.size != cur.size) {
            _transfers.value = keep
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

    private fun addReceiveRecord(from: String) {
        if (!saveHistory.value && !saveToGallery.value) return
        if (saveToGallery.value) {
            // 延迟等文件写完后再通知系统媒体库，使图片出现在相册
            viewModelScope.launch {
                delay(2000)
                runCatching {
                    MediaScannerConnection.scanFile(getApplication(), arrayOf(saveDir), null, null)
                }
            }
        }
        if (!saveHistory.value) return
        val t = TransferItem(++idCounter, "接收文件", "接收", from,
            progress = 1f, state = "完成", timestamp = System.currentTimeMillis())
        lastReceiveId = t.id
        _transfers.value = _transfers.value + t
        schedulePersist()
    }

    /** 接收服务器单文件落盘成功回调：把真实保存路径(file://)追加到最近一条接收记录的 localRefs（去重），并保持 name 为最新文件名（含扩展名，供预览识别类型） */
    fun onSaved(path: String) {
        val id = lastReceiveId
        if (id != null) updateTransfer(id) {
            val fileRef = "file://$path"
            val refs = if (fileRef in it.localRefs) it.localRefs else it.localRefs + fileRef
            it.copy(localRefs = refs, localRef = fileRef, name = File(path).name)
        }
        // 已保存生成记录且首次落盘时，提示顶部横幅（多文件只在首文件触发一次）
        if (id != null && _transfers.value.firstOrNull { it.id == id }?.localRefs?.size == 1) {
            val from = _transfers.value.firstOrNull { it.id == id }?.target ?: ""
            receivedNotice.value = "F:$from"
        }
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

    fun sendFiles(peer: Peer, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val files = uris.mapNotNull { it.toOutgoing() }
        if (files.isEmpty()) return
        val target = Peer(peer.deviceId, peer.name, peer.ip, peer.port)

        // 协程内部用 coroutineContext[Job] 取到自身 Job 完成登记，供「取消」使用。
        // 不能用外层可变 holder：主线程下 Dispatchers.Main.immediate 会立即同步执行协程体到第一个挂起点，
        // 此时外层 holder 尚未赋值，`holder!!` 会抛 NPE。
        viewModelScope.launch {
            val sendId = java.util.UUID.randomUUID().toString()
            val title = if (files.size == 1) files[0].name else "${files.size} 个文件"
            val firstLocal = uris.getOrNull(0)?.toString() ?: ""
            val t = addTransfer(title, "发送", target.name, localRef = firstLocal, localRefs = uris.map { it.toString() })
            sendJobs[t.id] = coroutineContext[kotlinx.coroutines.Job] ?: return@launch // 登记可取消的任务
            var cancelled = false
            try {
                val s = sender!!
                val prepared = s.prepare(target.ip, target.port, sendId, files)
                if (prepared.tokens.isEmpty()) {
                    updateTransfer(t.id) { it.copy(progress = 1f, state = "被拒") }
                    return@launch
                }
                // 只上传被接受的文件，按原顺序；每文件用专属 token
                val total = files.sumOf { it.size }
                var done = 0L
                for (f in files) {
                    val token = prepared.tokens[f.id] ?: continue // 该文件被拒绝则跳过
                    s.uploadFile(target.ip, target.port, prepared.sessionId, f.id, token, f) { frac ->
                        updateTransfer(t.id) { it.copy(progress = ((done + frac * f.size) / total).toFloat()) }
                    }
                    done += f.size
                }
                updateTransfer(t.id) { it.copy(progress = 1f, state = "完成") }
            } catch (c: kotlinx.coroutines.CancellationException) {
                cancelled = true
                updateTransfer(t.id) { it.copy(state = "已取消") }
                Log.i("OrangeGO.Send", "发送已取消 target=${target.ip}:${target.port} id=$sendId")
            } catch (e: Exception) {
                Log.e("OrangeGO.Send", "sendFiles 失败 target=${target.ip}:${target.port} id=$sendId files=${files.size}", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
            } finally {
                if (!cancelled) runCatching { sender?.cancel(target.ip, target.port, sendId) }
                sendJobs.remove(t.id)
            }
        }
    }

    /** 取消某个进行中的发送任务；未完成则中止并通知对端。 */
    fun cancelSend(id: Long) {
        sendJobs.remove(id)?.cancel()
    }

    private fun addTransfer(name: String, direction: String, target: String, localRef: String = "",
                            localRefs: List<String> = emptyList(),
                            isText: Boolean = false, content: String = ""): TransferItem {
        val t = TransferItem(++idCounter, name, direction, target, timestamp = System.currentTimeMillis(),
            localRef = localRef, localRefs = localRefs, isText = isText, content = content)
        _transfers.value = _transfers.value + t
        schedulePersist()
        return t
    }

    /** 发送文字：走文本消息命令（真实聊天气泡），不打包成 txt 文件。发送成功标"完成"，失败标"失败"。 */
    fun sendTextTo(peer: Peer, text: String) {
        if (text.isBlank() || peer.ip.isEmpty()) return
        val msg = Protocol.TextMessage(
            sendId = java.util.UUID.randomUUID().toString(),
            deviceId = deviceId,
            name = selfName,
            content = text.trim(),
            timestamp = System.currentTimeMillis()
        )
        val t = addTransfer("文字", "发送", peer.name, isText = true, content = text.trim())
        viewModelScope.launch {
            val s = sender
            if (s == null) { updateTransfer(t.id) { it.copy(state = "失败") }; return@launch }
            try {
                s.sendMessage(peer.ip, peer.port, msg)
                updateTransfer(t.id) { it.copy(state = "完成") }
            } catch (e: Exception) {
                Log.e("OrangeGO.Send", "sendTextMessage 失败 target=${peer.ip}:${peer.port}", e)
                updateTransfer(t.id) { it.copy(state = "失败") }
            }
        }
    }

    /** 收到文本消息：生成接收聊天气泡记录。开关关闭时不自动接收文本。 */
    fun onTextMessage(m: Protocol.TextMessage) {
        // 开关关闭时不自动接收文本：不生成接收气泡
        if (!autoAcceptText.value) return
        val t = TransferItem(++idCounter, "文字", "接收", m.name,
            progress = 1f, state = "完成", isText = true, content = m.content, timestamp = m.timestamp.let { if (it > 0) it else System.currentTimeMillis() })
        lastReceiveId = t.id
        _transfers.value = _transfers.value + t
        schedulePersist()
        // 接收文本：触发顶部横幅提醒
        receivedNotice.value = "T:${m.name}"
    }

    private fun updateTransfer(id: Long, block: (TransferItem) -> TransferItem) {
        _transfers.value = _transfers.value.map { if (it.id == id) block(it) else it }
        schedulePersist()
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
            OutgoingFile(id = java.util.UUID.randomUUID().toString(), name = name, size = size) { cr.openInputStream(this)!! }
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
        val bucket: String
    )

    private fun queryMedia(): List<MediaItem> {
        val rows = ArrayList<MediaRow>()
        // 全量读取图片与视频，不做数量上限；同相册（相同 bucketId）的图片和视频可在 UI 侧归并
        rows += queryMediaCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false)
        rows += queryMediaCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
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
                val bucketIdIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
                val bucketIdx = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: if (isVideo) "video" else "image"
                    // BUCKET_ID 在某些版本是数字、某些是字符串，统一按字符串读取并 fallback 到行 id
                    val bucketId = c.getString(bucketIdIdx)
                        ?: MediaStore.MediaColumns.DISPLAY_NAME + "_" + id
                    val bucket = c.getString(bucketIdx) ?: "相册"
                    out.add(
                        MediaRow(
                            ContentUris.withAppendedId(uri, id), name, isVideo, bucketId, bucket
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