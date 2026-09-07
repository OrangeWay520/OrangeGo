package com.orangeway.go.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.platform.LocalView
import com.hcaptcha.sdk.HCaptchaCompose
import com.hcaptcha.sdk.HCaptchaConfig
import com.hcaptcha.sdk.HCaptchaEvent
import com.hcaptcha.sdk.HCaptchaRenderMode
import com.hcaptcha.sdk.HCaptchaResponse
import com.hcaptcha.sdk.HCaptchaSize
import com.hcaptcha.sdk.HCaptchaTheme
import com.orangeway.go.FeedbackManager
import com.orangeway.go.FeedbackOutcome
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import android.webkit.MimeTypeMap
import coil.compose.AsyncImage
import com.orangeway.go.AudioItem
import com.orangeway.go.BuildConfig
import com.orangeway.go.FileItem
import com.orangeway.go.InstalledApp
import com.orangeway.go.MediaItem
import com.orangeway.go.OgoViewModel
import com.orangeway.go.PendingReceive
import com.orangeway.go.R
import com.orangeway.go.TransferItem
import com.orangeway.go.core.Peer
import com.orangeway.go.core.ThumbCache
import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private val CardShape = RoundedCornerShape(20.dp)
private val BtnShape = RoundedCornerShape(14.dp)

// 统一待发动作（覆盖文件/文字），「可连接设备」多选弹窗确认后执行
private sealed interface PendingSend {
    data class Files(val uris: List<Uri>, val folders: List<java.io.File>) : PendingSend
    data class Text(val text: String) : PendingSend
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OgoApp(vm: OgoViewModel = viewModel()) {
    val peers by vm.peers.collectAsState()
    val incoming by vm.incoming.collectAsState()
    val transfers by vm.transfers.collectAsState()
    val name by vm.name.collectAsState()
    val themeMode by OgoThemeMode.mode.collectAsState()
    val lang by OgoLang.code.collectAsState()
    val autoSave by vm.autoSave.collectAsState()
    val saveHistory by vm.saveHistory.collectAsState()
    val pinEnabled by vm.pinEnabled.collectAsState()
    val pinCode by vm.pinCode.collectAsState()
    val saveToGallery by vm.saveToGallery.collectAsState()
    val autoAcceptText by vm.autoAcceptText.collectAsState()
    val integrateImages by vm.integrateImages.collectAsState()
    val receivedNotice by vm.receivedNotice.collectAsState()
    val contents = remember { mutableStateListOf<Picked>() }
    // 默认打开为传输记录页（中=传输，左右分别为设备/文件）
    var tab by rememberSaveable { mutableStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }
    // 设置界面是否处于二级子页：true 时设置键回主设置，false 时设置键关闭设置
    var settingsSub by remember { mutableStateOf(false) }
    // 请求 SettingsScreen 返回主设置的次数（外部发起的递增请求计数）
    var setSettingsRoot by remember { mutableStateOf(0) }
    // 已选目标设备集合（以 deviceId 为键），贯穿 DevicesScreen / TransfersScreen / 中央悬浮发送键；
    // 本次运行内记住，设备离线或用户在设备界面改选时更新。发送时从 peers 实时解析出当前 Peer 逐个发送。
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // 未选设备但有内容时，发送键弹出「可连接设备」选择弹窗（多选）
    var showPeerPicker by remember { mutableStateOf(false) }
    // 待发动作（覆盖文件/文字），弹窗选设备确认后执行
    var pendingSend by remember { mutableStateOf<PendingSend?>(null) }

    // 双击返回键退出应用（第一次按提示，2 秒内再按才退出），避免误触
    val activity = LocalActivity.current
    val context = LocalContext.current
    var lastBackAt by remember { mutableStateOf(0L) }
    val toastPressAgain = tr(lang, "再按一次返回键退出", "再按一次返回鍵退出", "Press back again to exit", "もう一度戻るキーで終了", "뒤로가기를 한 번 더 누르면 종료됩니다", "Pulsa atrás de nuevo para salir", "Appuyez à nouveau pour quitter", "Zum Beenden erneut zurück drücken", "Pressione voltar novamente para sair", "Нажмите назад ещё раз, чтобы выйти")
    BackHandler {
        // 设置页内按下返回：先退回主界面，不触发退出
        if (showSettings) {
            showSettings = false
            return@BackHandler
        }
        // 设备/文件界面按下返回：先回传输记录页，不触发退出
        if (tab != 1) {
            tab = 1
            return@BackHandler
        }
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            activity?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, toastPressAgain, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 设备离线联动：pruneLoop 每 5s 清理超 15s 未见广播的设备（peers 变化触发重组），
    // 当已选集合内的 deviceId 从 peers 消失时自动移除（key 是 ip:port，集合以 deviceId 去比）。
    LaunchedEffect(peers) {
        val alive = peers.values.map { it.deviceId }.toSet()
        if (selectedIds.isNotEmpty() && !selectedIds.all { it in alive })
            selectedIds = selectedIds.filter { it in alive }.toSet()
    }

    // 设置键二段式行为：未打开→进入设置；打开且处于二级子页→返回主设置；打开且在设置主界面→关闭设置
    val onSettingsKey: () -> Unit = {
        when {
            !showSettings -> { showSettings = true; settingsSub = false }
            settingsSub -> setSettingsRoot += 1
            else -> showSettings = false
        }
    }

    // 文字发送统一封装：已选设备非空→直接发给全部已选；否则转去「可连接设备」多选弹窗
    val onSendTextAll: (String) -> Unit = { text ->
        peers.values.filter { it.deviceId in selectedIds }.forEach { vm.sendTextTo(it, text) }
    }
    val onRequestPickText: (String) -> Unit = { text ->
        pendingSend = PendingSend.Text(text)
        showPeerPicker = true
    }

    // 顶部横幅：订阅 ViewModel 的收到内容提醒，消费后自动消失（约 3 秒自动淡出，也可左右滑关闭）。
    // 拆分两个 Effect：源提醒(sub)填充 message、消费源；另一个以 message 为 key 计 3 秒定时清除，
    // 避免「消费源(置 null)」导致 key 变化把计时协程取消而横幅永不消失。
    var noticeBanner by remember { mutableStateOf<String?>(null) }
    // 退出动画期间仍要渲染的原文案：进入时设置，动画播完才清空，保证淡出不是"闪没"
    var bannerRaw by remember { mutableStateOf<String?>(null) }
    // 手指按住横幅时自增，令其重新计 3 秒；松手回落也自增重计
    var bannerHoldTick by remember { mutableStateOf(0) }
    LaunchedEffect(receivedNotice) {
        val msg = receivedNotice
        if (msg != null) {
            noticeBanner = msg
            bannerRaw = msg
            vm.receivedNotice.value = null // 消费该提醒（不影响下方定时清除）
        }
    }
    LaunchedEffect(noticeBanner, bannerHoldTick) {
        val shown = noticeBanner
        if (shown != null) {
            delay(3000)
            noticeBanner = null // 自动消失（触发 AnimatedVisibility 淡出）
        }
    }

    // 更新检查：全局唯一 updater，启动即后台自动检查；hasUpdate 用于顶栏设置图标与设置「检查更新」行的红点。
    val ogoUpdater = rememberOgUpdater()
    val hasUpdate = ogoUpdater.state is OgUState.Found
    LaunchedEffect(Unit) {
        val updCtx = context
        val p = updCtx.getSharedPreferences("ogo_prefs", Context.MODE_PRIVATE)
        ogoUpdater.check(OgSource.fromId(p.getString("download_source", null)))
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Scaffold 不预占系统栏/IME（否则 contentWindowInsets 会吃掉键盘，输入框被推到键盘之下），
            // 底部手势条由 OgoBottomBar.navigationBarsPadding 处理，键盘由内容区 max padding 顶起。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { OgoTopBar(onOpenSettings = onSettingsKey, settingsActive = showSettings, hasUpdate = hasUpdate) },
            bottomBar = { OgoBottomBar(if (showSettings) -1 else tab) { showSettings = false; tab = it } }
        ) { pad ->
            // 底部预留取「导航栏高度」与「键盘高度」的较大值：
            // 键盘弹出时导航栏已被覆盖，内容只需让到键盘顶；键盘收起时在导航栏顶处平滑接管，
            // 过渡全程连续，避免输入条先落到底部再跳回原位的动画异常。
            val density = LocalDensity.current
            val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
            Box(
                Modifier.fillMaxSize().padding(
                    top = pad.calculateTopPadding(),
                    bottom = maxOf(pad.calculateBottomPadding(), imeBottomDp)
                )
            ) {
                AnimatedContent(
                    targetState = showSettings,
                    transitionSpec = {
                        if (targetState) {
                            // 进入设置：从顶部向下展开铺开
                            (slideInVertically(tween(320)) { -it } +
                                expandVertically(tween(320), expandFrom = Alignment.Top) +
                                fadeIn(tween(280)))
                                .togetherWith(
                                    shrinkVertically(tween(260), shrinkTowards = Alignment.Top) +
                                    slideOutVertically(tween(240)) { it } +
                                    fadeOut(tween(200))
                                )
                        } else {
                            // 关闭设置：从底部向上收（底部边缘向上收起并滑出顶部）
                            (slideInVertically(tween(240)) { it } +
                                fadeIn(tween(200)))
                                .togetherWith(
                                    shrinkVertically(tween(300), shrinkTowards = Alignment.Top) +
                                    slideOutVertically(tween(300)) { -it } +
                                    fadeOut(tween(220))
                                )
                        }
                    }
                ) { isSettings ->
                    if (isSettings) {
                        // 设置作为完整二级页，占满顶部任务栏与底部导航栏之间的工作区域
                        SettingsScreen(
                            currentName = name,
                            receiveDir = vm.receiveDir,
                            themeMode = themeMode,
                            lang = lang,
                            autoSave = autoSave,
                            saveHistory = saveHistory,
                            pinEnabled = pinEnabled,
                            pinCode = pinCode,
                            saveToGallery = saveToGallery,
                            autoAcceptText = autoAcceptText,
                            integrateImages = integrateImages,
                            autoSaveWhitelist = vm.autoSaveWhitelist.collectAsState().value,
                            onRename = vm::rename,
                            onChangeDir = vm::changeSaveDir,
                            onResetDir = vm::resetSaveDir,
                            onSetTheme = vm::setThemeMode,
                            onSetLanguage = vm::setLanguage,
                            onSetAutoSave = vm::setAutoSave,
                            onSetSaveHistory = vm::setSaveHistory,
                            onSetSaveToGallery = vm::setSaveToGallery,
                            onSetPinEnabled = vm::setPinEnabled,
                            onSetPinCode = vm::setPinCode,
                            onSetAutoAcceptText = vm::setAutoAcceptText,
                            onSetIntegrateImages = vm::setIntegrateImages,
                            onSetAutoSaveWhitelist = vm::setAutoSaveWhitelist,
                            onClearTransfers = vm::clearTransfers,
                            retentionDays = vm.retentionDays.collectAsState().value,
                            onSetRetentionDays = vm::setAutoCleanupDays,
                            onBack = { showSettings = false },
                            onSubChanged = { settingsSub = it },
                            returnToRoot = setSettingsRoot,
                            hasUpdate = hasUpdate
                        )
                    } else {
                        // 底部三个界面之间以横向滑块方式切换（按目标/来源顺序决定滑动方向）
                        AnimatedContent(
                            targetState = tab,
                            transitionSpec = {
                                val dir = if (targetState > initialState) 1 else -1
                                (slideInHorizontally(tween(260)) { dir * it / 2 } + fadeIn(tween(180)))
                                    .togetherWith(slideOutHorizontally(tween(260)) { -dir * it / 2 } + fadeOut(tween(160)))
                            }
                        ) { t ->
                            when (t) {
                                0 -> DevicesScreen(
                                    peers.values.toList(), selectedIds,
                                    { id ->
                                        if (id in selectedIds) selectedIds -= id else selectedIds += id
                                    },
                                    vm.favorites.collectAsState().value,
                                    vm::isFavorite, vm::toggleFavorite, vm::removeFavorite
                                )
                                1 -> TransfersScreen(transfers, peers.values.toList(), selectedIds, integrateImages,
                                    onSendText = onSendTextAll, onRequestPickText = onRequestPickText,
                                    vm::cancelSend, vm::deleteTransfer, vm::recall,
                                    onThumbKey = vm::updateThumbKey)
                                else -> FileScreen(contents, vm)
                            }
                        }
                    }
                }
            }
        }

        // 中央悬浮发送键：置于整个界面最顶层绘制，不被上方内容遮挡。
        // 位于导航栏内近似居中（偏移很小，不凸出成小块）。
        // 仅当已选中设备集合且已选好内容、且未进入设置时才点亮；否则恢复未选中外观
        val sendActive = !showSettings && selectedIds.isNotEmpty() && contents.isNotEmpty()
        val sendScale by animateFloatAsState(
            if (sendActive) 1.1f else 1f, spring(0.5f, 800f), label = "send"
        )
        val sendRaised = if (sendActive)
            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
        // 发送键反馈：按压缩小 + 点击回弹
        val sendInteraction = remember { MutableInteractionSource() }
        val sendPressed by sendInteraction.collectIsPressedAsState()
        val sendBounce = remember { Animatable(1f) }
        val sendScope = rememberCoroutineScope()
        val sendTapScale by animateFloatAsState(
            if (sendPressed) 0.86f else 1f, spring(0.5f, 900f), label = "sendpress"
        )
        Box(
            Modifier.align(Alignment.BottomCenter)
                .offset(y = (-3).dp).size(58.dp)
                .scale(sendScale * sendTapScale * sendBounce.value)
                .zIndex(10f)
                .shadow(6.dp, CircleShape)
                .background(sendRaised, CircleShape)
                .clickable(interactionSource = sendInteraction, indication = null, onClick = {
                    sendScope.launch {
                        sendBounce.snapTo(1f)
                        sendBounce.animateTo(1.12f, spring(dampingRatio = 0.25f, stiffness = 950f))
                        sendBounce.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 950f))
                    }
                    showSettings = false
                    val targets = peers.values.filter { it.deviceId in selectedIds }
                    // 拆分已选内容：普通文件（uri）与文件夹分别发送
                    val fileUris = contents.filter { it.folder == null }.map { it.uri }
                    val folders = contents.filter { it.folder != null }.map { it.folder!! }
                    fun dispatch(peer: Peer) {
                        if (fileUris.isNotEmpty()) vm.sendFiles(peer, fileUris)
                        folders.forEach { vm.sendFolder(peer, it) }
                    }
                    if (targets.isNotEmpty() && contents.isNotEmpty()) {
                        // 已选中设备集合且已选好内容：直接发给全部已选设备并回到传输记录界面
                        targets.forEach { dispatch(it) }
                        contents.clear()
                        tab = 1
                    } else if (contents.isNotEmpty()) {
                        // 已选好内容但未选设备：弹出「可连接设备」多选弹窗，选完确定后发给所选设备
                        pendingSend = PendingSend.Files(fileUris, folders)
                        showPeerPicker = true
                    } else {
                        // 无可发送内容：回到传输记录页查看进度/记录
                        tab = 1
                    }
                }),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = R.raw.og_logo_orange,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                contentScale = ContentScale.Fit
            )
        }

        // 收到内容顶部横幅：置于最顶层，约 3 秒后 2s 渐渐淡出；支持左右滑跟手关闭（淡出/位移跟手）；
            // 顶出顶栏，不遮住顶部状态栏/顶栏；无实体 × 按钮。
            // 退出动画播完（约 2s）后清空 bannerRaw，避免残留渲染内容
            LaunchedEffect(noticeBanner) {
                if (noticeBanner == null) { delay(2100); bannerRaw = null }
            }
            if (bannerRaw != null || noticeBanner != null) {
                AnimatedVisibility(
                    visible = noticeBanner != null,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { -it / 3 },
                    exit = fadeOut(tween(2000)),
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(30f)
                ) {
                    val raw = bannerRaw ?: return@AnimatedVisibility
                    val isErr = raw.startsWith("E:")
                    val peer = raw.substringAfter(':')
                    val text = if (isErr)
                        raw.substringAfter(':')
                    else if (raw.startsWith("T:"))
                        tr(lang, "收到来自 $peer 的文本", "收到來自 $peer 的文字", "Received text from $peer", "$peer からテキストを受信", "$peer 님이 보낸 텍스트를 받았습니다", "Texto recibido de $peer", "Texte reçu de $peer", "Text von $peer empfangen", "Texto recebido de $peer", "Получен текст от $peer")
                    else
                        tr(lang, "收到来自 $peer 的文件", "收到來自 $peer 的檔案", "Received file from $peer", "$peer からファイルを受信", "$peer 님이 보낸 파일을 받았습니다", "Archivo recibido de $peer", "Fichier reçu de $peer", "Datei von $peer empfangen", "Arquivo recebido de $peer", "Получен файл от $peer")
                    val bannerOff = remember { Animatable(0f) }
                    val bannerScope = rememberCoroutineScope()
                    Box(
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(top = 58.dp)
                            .padding(horizontal = 24.dp)
                            .fillMaxWidth()
                            .offset { IntOffset(bannerOff.value.toInt(), 0) }
                            .graphicsLayer { alpha = (1f - kotlin.math.abs(bannerOff.value) / 600f).coerceIn(0.3f, 1f) }
                            // 注意：key 只跟 noticeBanner，不能含 bannerHoldTick，
                            // 否则按下自增 key 变化会把手指手势协程取消，导致滑不动
                            .pointerInput(noticeBanner) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    bannerHoldTick++ // 手指按下 → 重新计时
                                    var ended = false
                                    try {
                                        drag(down.id) { change ->
                                            val dx = change.positionChange().x
                                            bannerScope.launch {
                                                bannerOff.snapTo((bannerOff.value + dx)
                                                    .coerceIn(-size.width.toFloat(), size.width.toFloat()))
                                            }
                                            change.consume()
                                        }
                                    } finally { ended = true }
                                    val w = size.width
                                    bannerScope.launch {
                                        if (kotlin.math.abs(bannerOff.value) > w / 3f) {
                                            // 过阈值：顺势滑出屏再关闭
                                            bannerOff.animateTo(if (bannerOff.value > 0) w.toFloat() else -w.toFloat(), tween(180))
                                            noticeBanner = null
                                        } else {
                                            // 未过阈值：回弹复位
                                            bannerOff.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 650f))
                                            if (ended) bannerHoldTick++ // 松手回落：重新计 3 秒
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isErr) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                            shadowElevation = 0.dp
                        ) {
                            Text(text, color = if (isErr) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
                                fontSize = 13.sp, fontWeight = if (isErr) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth())
                        }
                    }
                }
            }
        }

    if (incoming.isNotEmpty()) IncomingDialog(incoming, vm::accept, vm::reject, vm::confirmPin)

    // 未选设备但有内容时：弹出「可连接设备」多选弹窗，确定后发给所选设备并写入已选集合
    if (showPeerPicker) {
        PeerPickerDialog(
            peers = peers.values.toList(),
            initialSelected = selectedIds,
            onConfirm = { picked ->
                selectedIds = selectedIds + picked
                showPeerPicker = false
                when (val a = pendingSend) {
                    is PendingSend.Files -> peers.values.filter { it.deviceId in picked }
                        .forEach { p ->
                            if (a.uris.isNotEmpty()) vm.sendFiles(p, a.uris)
                            a.folders.forEach { vm.sendFolder(p, it) }
                        }
                    is PendingSend.Text -> peers.values.filter { it.deviceId in picked }
                        .forEach { vm.sendTextTo(it, a.text) }
                    null -> {}
                }
                pendingSend = null
                contents.clear()
                tab = 1
            },
            onDismiss = { showPeerPicker = false; pendingSend = null }
        )
    }
}

@Composable
private fun OgoTopBar(onOpenSettings: () -> Unit, settingsActive: Boolean = false, hasUpdate: Boolean = false) {
    val dark = appIsDark()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val spin = remember { Animatable(0f) }
    // 自定义紧凑顶栏：保留状态栏高度，内容区更紧凑（相比默认 TopAppBar 更矮，内容区更大）
    Box(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(52.dp)
            .background(MaterialTheme.colorScheme.background)
    ) {
        // BarLogo 相对整个顶栏水平居中（不再受右侧齿轮影响而偏左）
        AsyncImage(
            model = if (dark) R.raw.og_logo_white else R.raw.og_logo_black,
            contentDescription = "OrangeGO",
            modifier = Modifier.align(Alignment.Center).height(58.dp),
            contentScale = ContentScale.Fit
        )
        // 设置入口：点击时齿轮逆时针回旋转动（参照 HereIAm 的柔和反馈），进入设置页点亮为橙色
        Box(Modifier.align(Alignment.CenterEnd).scale(if (pressed) 0.82f else 1f)) {
            IconButton(
                onClick = {
                    onOpenSettings()
                    scope.launch { spin.animateTo(spin.value - 120f, tween(700, easing = FastOutSlowInEasing)) }
                },
                interactionSource = interaction
            ) {
                Icon(
                    Icons.Default.Settings, "设置",
                    modifier = Modifier.rotate(spin.value),
                    tint = if (settingsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            // 更新红点：当有新版本时，齿轮右上角显示一个小红点提示
            if (hasUpdate) {
                Box(
                    Modifier.size(8.dp)
                        .background(Color(0xFFE53935), CircleShape)
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = 3.dp)
                )
            }
        }
    }
}

// ===== 底部导航（窄条）：左=传输 / 中=悬浮 HIA 式发送键 / 右=文件 =====
@Composable
private fun OgoBottomBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val lang by OgoLang.code.collectAsState()
    // 底部导航文案（多语言）：左=设备 / 右=文件，中央留空放 G 发送键
    val navDevices = tr(lang, "设备", "裝置", "Devices", "デバイス", "기기", "Dispositivos", "Appareils", "Geräte", "Dispositivos", "Устройства")
    val navFiles = tr(lang, "文件", "檔案", "Files", "Files", "파일", "Archivos", "Fichiers", "Dateien", "Arquivos", "Файлы")
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
        Row(
            Modifier.fillMaxWidth()
                .height(56.dp)
                // 手势条高度由本导航条自行补足，贴在系统底部之上，避免与内容区重复 padding
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomTab(selectedTab == 0, Icons.Default.Devices, navDevices, Modifier.weight(1f)) { onSelect(0) }

            // 原中央发送键已提升到最外层顶置绘制，此处保留等宽缺口以维持对称
            Spacer(Modifier.width(88.dp))

            BottomTab(selectedTab == 2, Icons.Default.AttachFile, navFiles, Modifier.weight(1f)) { onSelect(2) }
        }
    }
}

@Composable
private fun BottomTab(
    active: Boolean, icon: ImageVector, label: String, modifier: Modifier = Modifier,
    rotateDegrees: Int = 0, onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val rot = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }
    val pressScale by animateFloatAsState(
        if (pressed) 0.86f else 1f, spring(0.5f, 900f), label = "press"
    )
    Column(
        modifier.clickable(interactionSource = interaction, indication = null, onClick = {
                if (rotateDegrees != 0) {
                    // 旋转类反馈（如“传输”图标逆时针转半圈：rotate 为负 = 逆时针）
                    scope.launch { rot.animateTo(rot.value - rotateDegrees, tween(420, easing = FastOutSlowInEasing)) }
                } else {
                    // 回弹类反馈（如“文件”图标）
                    scope.launch {
                        bounce.snapTo(1f)
                        bounce.animateTo(1.18f, spring(dampingRatio = 0.25f, stiffness = 950f))
                        bounce.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 950f))
                    }
                }
                onClick()
            })
            .scale(pressScale)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NavIcon(icon, active, rot.value, bounce.value)
        Text(label, fontSize = 10.sp,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NavIcon(icon: ImageVector, active: Boolean, rotation: Float = 0f, bounceScale: Float = 1f) {
    val scale by animateFloatAsState(
        targetValue = if (active) 1.14f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 800f),
        label = "nav"
    )
    Icon(icon, null, modifier = Modifier.scale(scale * bounceScale).rotate(rotation),
        tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}

// ===== 传输记录（聊天气泡样式，可真实发送文字给已连接设备） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransfersScreen(
    transfers: List<TransferItem>,
    peers: List<Peer>,
    selectedIds: Set<String>,
    integrateImages: Boolean,
    onSendText: (String) -> Unit,
    onRequestPickText: (String) -> Unit,
    onCancel: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit,
    onRecall: (Long) -> Unit,
    onThumbKey: ((Long, String, String) -> Unit)? = null
) {
    var draft by remember { mutableStateOf("") }
    var previewItem by remember { mutableStateOf<TransferItem?>(null) }
    // 单独文件卡预览：记录 + 具体 ref + 名称
    var previewRefItem by remember { mutableStateOf<Triple<TransferItem, String, String>?>(null) }
    // 全屏分页图片预览：记录 + 起始页码
    var imageFull by remember { mutableStateOf<Pair<TransferItem?, Int>?>(null) }
    // 多选模式：开关 + 已选记录 id 集合
    var multiSelect by remember { mutableStateOf(false) }
    val selIds = remember { mutableStateListOf<Long>() }
    fun toggleSelect(t: TransferItem) {
        if (selIds.contains(t.id)) selIds.remove(t.id) else selIds.add(t.id)
    }
    fun exitMulti() { multiSelect = false; selIds.clear() }
    val context = LocalContext.current
    val lang by OgoLang.code.collectAsState()

    // 多语言文案（供本界面使用）
    val emptyTitle = tr(lang, "暂无传输记录", "暫無傳輸記錄", "No transfers", "転送履歴なし", "전송 기록 없음", "Sin transferencias", "Aucun transfert", "Keine Übertragungen", "Sem transferências", "Нет передач")
    val emptySub = tr(lang, "收到或发出的文件会显示在这里", "收到或發出的檔案會顯示在這裡", "Files you send or receive appear here", "受け取った・送ったファイルがここに表示されます", "보내거나 받은 파일이 여기에 표시됩니다", "Los archivos enviados o recibidos aparecen aquí", "Les fichiers envoyés ou reçus s'affichent ici", "Gesendete und empfangene Dateien erscheinen hier", "Arquivos enviados ou recebidos aparecem aqui", "Полученные и отправленные файлы отображаются здесь")
    val placeholder = tr(lang, "输入文字，发送给设备", "輸入文字，傳送給設備", "Type a message to send", "文字を入力して送信", "보낼 메시지 입력", "Escribe un mensaje para enviar", "Saisissez un message à envoyer", "Nachricht zum Senden eingeben", "Digite uma mensagem para enviar", "Введите сообщение для отправки")
    val toastNoPeer = tr(lang, "请先连接一台设备再发送", "請先連接一台設備再傳送", "Connect to a device first", "先にデバイスへ接続してください", "먼저 기기에 연결하세요", "Conéctate a un dispositivo primero", "Connectez-vous d'abord à un appareil", "Verbinde zuerst ein Gerät", "Conecte-se a um dispositivo primeiro", "Сначала подключите устройство")
    val toastFileGone = tr(lang, "该文件已删除或移动位置，无法打开", "該檔案已刪除或移動位置，無法開啟", "This file was deleted or moved, can't open", "このファイルは削除または移動されたため開けません", "이 파일이 삭제되거나 이동되어 열 수 없습니다", "El archivo fue eliminado o movido, no se puede abrir", "Ce fichier a été supprimé ou déplacé, impossible d'ouvrir", "Diese Datei wurde gelöscht oder verschoben, kann nicht geöffnet werden", "Arquivo excluído ou movido, não é possível abrir", "Файл удалён или перемещён, невозможно открыть")
    // 多选操作条
    val multiBarLabel = tr(lang, "已选择", "已選擇", "Selected", "選択数", "선택함", "Seleccionados", "Sélection", "Ausgewählt", "Selecionados", "Выбрано")
    val multiShare = tr(lang, "分享", "分享", "Share", "共有", "공유", "Compartir", "Partager", "Teilen", "Compartilhar", "Поделиться")
    val multiDelete = tr(lang, "删除", "刪除", "Delete", "削除", "삭제", "Eliminar", "Supprimer", "Löschen", "Excluir", "Удалить")
    val multiDone = tr(lang, "完成", "完成", "Done", "完了", "완료", "Listo", "Terminé", "Fertig", "Concluir", "Готово")
    val shareChooserTitle = tr(lang, "分享到", "分享到", "Share to", "共有", "공유", "Compartir con", "Partager vers", "Teilen mit", "Compartilhar com", "Поделиться")

    // 多选模式下按返回键先退出多选，而非直接退出应用。
    // BackHandler 遵循后注册先响应原则，此处多选开启时优先于外层「双击返回退出应用」。
    if (multiSelect) {
        BackHandler { exitMulti() }
    }

    // ===== 传输记录列表：反转布局（reverseLayout） =====
    // 聊天界面的标准实现：列表从底边开始排布，index 0（最新消息）天然贴在最底部，
    // 初始滚动位置即最新消息处——打开即见最新，无需任何滚动/跟随/淡入逻辑。
    val transfersListState = rememberLazyListState()
    // 保险逻辑：新消息到达（reversed 列表头部插入 index 0）且用户原本位于底部时，
    // 显式贴回最底端展示新消息；浏览历史（首项为更早消息）时不打扰。瞬时滚动，无动画。
    // 注意：插入新项后 LazyColumn 会按 key 把锚点重新对准「上一条最新消息」（其 index 变为 1），
    // 因此贴底判定用 idx <= 1，否则新消息会被挤到视口下方看不到（接收端不自动显示最新内容）。
    LaunchedEffect(transfers.size) {
        if (transfers.size > 0 && transfersListState.firstVisibleItemIndex <= 1) {
            transfersListState.scrollToItem(0)
        }
    }

    // 预解析全部记录的「图片/文件拆分 + 图片宽高比」（只读文件头，不解码像素）：
    // 让每条记录在被滚动到时立即获得稳定高度，从根上消除滚动过程中的项高跳变（卡顿/闪现）。
    LaunchedEffect(transfers.size) {
        if (transfers.isEmpty()) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            for (t in transfers) {
                if (!t.isText) {
                    val k = splitCacheKey(t)
                    if (!splitCache.containsKey(k)) splitCache[k] = computeBubbleSplit(context, t)
                }
            }
        }
    }

    // 键盘顶起：由外层内容区 max(导航栏高度, 键盘高度) padding 统一处理，输入条随键盘上移/回落。
    // 底部手势条由 OgoBottomBar.navigationBarsPadding 单独处理。
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.fillMaxSize()) {
                // 多选模式操作条：已选数量 + 分享 / 清除 / 完成
                if (multiSelect) {
                    val selTransfers = transfers.filter { it.id in selIds }
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("$multiBarLabel ${selIds.size}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { launchShare(context, shareChooserTitle, selTransfers) },
                            enabled = selTransfers.isNotEmpty()
                        ) { Text(multiShare, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium) }
                        // 删除所选记录：逐条走与长按删除一致的 deleteTransfer（同步移除持久化并回收缩略图缓存）
                        TextButton(
                            onClick = {
                                selIds.toList().forEach(onDeleteItem)
                                exitMulti()
                            },
                            enabled = selIds.isNotEmpty()
                        ) { Text(multiDelete, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium) }
                        TextButton(onClick = { exitMulti() }) {
                            Text(multiDone, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (transfers.isEmpty()) {
                        EmptyState(Icons.Default.Sync, emptyTitle, emptySub)
                    } else {
                        LazyColumn(
                            state = transfersListState,
                            modifier = Modifier.fillMaxSize(),
                            // 反转布局：index 0（最新消息）贴底，打开即在最新处；上滑看历史。
                            reverseLayout = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { listItems(transfers.asReversed(), key = { it.id }) {
                            ChatBubble(
                                it, lang, onCancel,
                                integrateImages = integrateImages,
                                onPreview = { item ->
                                if (localRefExists(context, item.firstLocalRef)) previewItem = it
                                else android.widget.Toast.makeText(context, toastFileGone, android.widget.Toast.LENGTH_SHORT).show()
                            },
                                onOpenMedia = { item, idx ->
                                    val media = itemMediaRefs(context, item, item.name).getOrNull(idx)
                                    val ref = media?.first ?: ""
                                    if (ref.isNotEmpty() && localRefExists(context, ref)) imageFull = item to idx
                                    else android.widget.Toast.makeText(context, toastFileGone, android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onDeleteItem = { onDeleteItem(it.id) },
                                multiSelect = multiSelect,
                                isSelected = it.id in selIds,
                                onToggleSelect = { toggleSelect(it) },
                                onEnterMultiSelect = { t ->
                                    if (!multiSelect) multiSelect = true
                                    val has = selIds.contains(t.id)
                                    if (has) selIds.remove(t.id) else selIds.add(t.id)
                                },
                                onExitMultiSelect = { exitMulti() },
                                onShare = { launchShare(context, shareChooserTitle, listOf(it)) },
                                onPreviewRef = { item, ref, name ->
                                    if (localRefExists(context, ref)) previewRefItem = Triple(item, ref, name)
                                    else android.widget.Toast.makeText(context, toastFileGone, android.widget.Toast.LENGTH_SHORT).show()
                                },
                                onRecall = { onRecall(it.id) },
                                onThumbKey = onThumbKey
                            )
                        } }
                    }
                }
            }
        }

        // 底部文字输入条：把文字发送给一台已连接的设备。
        // 背景用 surface（与底部导航栏一致）；键盘高度预留已由外层 max padding 处理，
        // 输入条底部正好落在键盘顶，此处不再单独 imePadding（避免高度双重叠加）。
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // 顶部/底部各留 10dp：底部留白使文本框与下方导航栏（含中央悬浮发送键）错开，
                    // 发送键顶部不再侵入文本框
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // 输入区域容器：负责圆角/背景；多行在上限(120dp)后内部滚动
                // 背景用 surfaceVariant，与输入条底色(surface)区分，保证文本框可见；默认高度与发送键一致(44dp)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp, max = 120.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        minLines = 1,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val s = draft.trim()
                        if (s.isBlank()) return@Button
                        val targets = peers.filter { it.deviceId in selectedIds }
                        if (targets.isNotEmpty()) {
                            // 已选设备集合非空：直接发给全部已选设备并清空输入
                            onSendText(s)
                            draft = ""
                        } else if (peers.isEmpty()) {
                            android.widget.Toast.makeText(context, toastNoPeer, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            // 未选设备但有可用设备：交给统一「可连接设备」多选弹窗
                            onRequestPickText(s)
                        }
                    },
                    enabled = draft.isNotBlank(),
                    shape = CircleShape,
                    // 高度/大小固定，始终贴底，不与输入框同高增长
                    modifier = Modifier.size(44.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }

    // 选择目标设备（未选设备时由顶层统一「可连接设备」多选弹窗承接，此处不再本地弹窗）

    // 文件记录预览对话框（非图片文件/打开用）
    previewItem?.let { FilePreviewDialog(it) { previewItem = null } }
    // 单个文件卡预览：携带具体 ref 与名称
    previewRefItem?.let { (item, ref, name) ->
        FilePreviewDialog(item, ref, name) { previewRefItem = null }
    }

    // 全屏分页媒体预览（微信式左右滑动，图片/视频混合）
    imageFull?.let { (itm, idx) ->
        if (itm != null) FullscreenMediaPreview(itm, idx) { imageFull = null }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatBubble(
    item: TransferItem, lang: String, onCancel: (Long) -> Unit,
    integrateImages: Boolean = false,
    onPreview: ((TransferItem) -> Unit)? = null,
    onOpenMedia: ((TransferItem, Int) -> Unit)? = null,
    onDeleteItem: ((TransferItem) -> Unit)? = null,
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: ((TransferItem) -> Unit)? = null,
    onEnterMultiSelect: ((TransferItem) -> Unit)? = null,
    onExitMultiSelect: (() -> Unit)? = null,
    onShare: ((TransferItem) -> Unit)? = null,
    onPreviewRef: ((TransferItem, String, String) -> Unit)? = null,
    onRecall: ((TransferItem) -> Unit)? = null,
    onThumbKey: ((Long, String, String) -> Unit)? = null
) {
    val mine = item.direction == "发送"
    // 已撤回：居中灰字提示，不显示气泡内容、不触发长按/预览
    if (item.state == "已撤回") {
        val recalledMsg = if (mine)
            tr(lang, "你撤回了一条消息", "你撤回了一則訊息", "You recalled a message", "メッセージを取り消しました", "메시지를 철회했습니다", "Retiraste un mensaje", "Vous avez retiré un message", "Sie haben eine Nachricht zurückgerufen", "Você retirou uma mensagem", "Вы отозвали сообщение")
        else
            tr(lang, "对方撤回了一条消息", "對方撤回了一則訊息", "The other party recalled a message", "相手がメッセージを取り消しました", "상대방이 메시지를 철회했습니다", "La otra parte retiró un mensaje", "L'autre partie a retiré un message", "Der andere hat eine Nachricht zurückgerufen", "A outra parte retirou uma mensagem", "Другая сторона отозвала сообщение")
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(recalledMsg, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
        return
    }
    val bubbleColor = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // 多选勾的橙色主题色（微信风格）
    val selectTint = Color(0xFFFF8A00)
    val shape = if (mine) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
        else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    // 发送中且完成前可取消（含发送/接收进行中），已完成/失败/被拒/已取消不再显示
    val cancellable = mine && item.state == "进行中"
    val cancelText = tr(lang, "取消发送", "取消傳送", "Cancel send", "送信をキャンセル", "전송 취소", "Cancelar envío", "Annuler l'envoi", "Senden abbrechen", "Cancelar envio", "Отменить отправку")
    val sentTo = tr(lang, "发给", "發給", "To ", "届け先 ", "보낸 대상 ", "Para ", "À ", "An ", "Para ", "Отправлено ")
    val fromLabel = tr(lang, "来自", "來自", "From ", "送信元 ", "보낸 곳 ", "De ", "De ", "Von ", "De ", "От ")
    val previewLabel = tr(lang, "预览", "預覽", "Preview", "プレビュー", "미리보기", "Previsualizar", "Aperçu", "Vorschau", "Pré-visualizar", "Предпросмотр")
    val longHint = tr(lang, "长按打开操作菜单", "長按開啟操作選單", "Long-press for actions", "長押しでメニュー", "길게 눌러 메뉴 열기", "Mantén presionado para el menú", "Maintenir pour le menu", "Gedrückt halten für Menü", "Mantenha pressionado para o menu", "Долгое нажатие — меню")
    // 长按操作菜单文案（微信式深色小菜单）
    val menuCopyTxt = tr(lang, "复制", "複製", "Copy", "コピー", "복사", "Copiar", "Copier", "Kopieren", "Copiar", "Копировать")
    val menuDeleteTxt = tr(lang, "删除", "刪除", "Delete", "削除", "삭제", "Eliminar", "Supprimer", "Löschen", "Excluir", "Удалить")
    val menuSaveTxtTxt = tr(lang, "保存为文本文件", "儲存為文字檔案", "Save as text file", "テキストファイルとして保存", "텍스트 파일로 저장", "Guardar como archivo de texto", "Enregistrer en fichier texte", "Als Textdatei speichern", "Salvar como arquivo de texto", "Сохранить как текстовый файл")
    val menuMultiTxt = tr(lang, "多选", "多選", "Select", "複数選択", "다중 선택", "Seleccionar", "Sélectionner", "Mehrfachauswahl", "Selecionar", "Выбрать")
    val menuShareTxt = tr(lang, "分享", "分享", "Share", "共有", "공유", "Compartir", "Partager", "Teilen", "Compartilhar", "Поделиться")
    val menuRecallTxt = tr(lang, "撤回", "撤回", "Recall", "取り消し", "철회", "Retirar", "Retirer", "Zurückrufen", "Retirar", "Отозвать")
    val copiedToast = tr(lang, "已复制", "已複製", "Copied", "コピーしました", "복사됨", "Copiado", "Copié", "Kopiert", "Copiado", "Скопировано")
    val ctx = LocalContext.current
    // 长按触发轻微振动反馈（微信式）
    val haptic = LocalHapticFeedback.current

    // 本地有源文件的非文字文件记录即可预览/显示缩略图（不要求完成：微信里失败的图片也显示缩略图）
    val canPreview = !item.isText && item.firstLocalRef.isNotEmpty() && onPreview != null
    // 按类型分开展示：逐个解析本地引用（IO 线程取得真实文件名/大小），图片归入多图网格，其余归入文件卡片。
    // file:// 直接取路径；content:// 查询真实显示名，从而正确区分图片与安装包等（接收/发送逻辑共用）。
    // 优先读预解析缓存（进屏幕时已后台算好），命中则首帧即拿到稳定高度，不产生先空后长的跳变。
    // key 含引用集合：接收文件落盘后 localRefs 变化会触发重算，让新保存的图片从「文件名」变回预览图。
    val split: BubbleSplit by produceState(
        initialValue = splitCache[splitCacheKey(item)] ?: BubbleSplit(emptyList(), emptyList(), emptyList()),
        splitCacheKey(item)
    ) {
        if (!item.isText) {
            val k = splitCacheKey(item)
            value = splitCache[k] ?: withContext(Dispatchers.IO) { computeBubbleSplit(ctx, item) }
                .also { splitCache[k] = it }
        }
    }
    val imageRefs = split.images
    val videoRefs = split.videos
    // 保序媒体条目（图片/视频按原始收发顺序交错，不再先图后视频），供集成网格/全屏分页索引；与 itemMediaRefs 顺序一致
    val orderedMedia = split.entries.filterIsInstance<BubbleEntry.Media>()
    val mediaRefs = orderedMedia.map { it.ref }
    val fileCards = split.cards
    val noPreview = imageRefs.isEmpty() && videoRefs.isEmpty() && fileCards.isEmpty()
    // 非集成模式且文件总数 > 1：每个文件（图片/视频/普通文件）各自独立气泡（微信式）；单文件天然走集成分支
    val separate = !integrateImages && (imageRefs.size + videoRefs.size + fileCards.size) > 1
    // 纯媒体气泡：里面只有图片/视频网格或单媒体预览，后面不再跟文件卡片或「取消发送」等内容。
    // 这类气泡四边留白必须完全一致（与电脑端 IsMediaBubble 规则对齐），否则会出现左右比上下窄/宽的不协调。
    val mediaOnly = (imageRefs.isNotEmpty() || videoRefs.isNotEmpty()) && fileCards.isEmpty()
            && !cancellable && !separate

    var menuOpen by remember { mutableStateOf(false) }
    // 记录气泡在窗口中的位置，供长按菜单精确贴边定位与箭头指向
    var bubbleBounds by remember { mutableStateOf(Rect.Zero) }

    // 统一的长按动作：多选模式退出多选；普通模式打开操作菜单。
    // 供气泡整体（combinedClickable）以及图片缩略图/文件卡片等「吃掉长按手势」的子层共用，
    // 保证气泡任意位置（含附件本体、边框、文字区）长按都能弹同一个菜单。
    val openBubbleMenu: () -> Unit = {
        if (multiSelect) onExitMultiSelect?.invoke()
        else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            menuOpen = true
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        // 消息行：勾与气泡在行内垂直居中（时间戳单独放下方，不参与居中，避免把勾带偏）
        // 整行可点：fillMaxWidth 让左右空白都进入可点区域，多选模式下点气泡外的左右空白同样切换选中（微信式）；
        // 点气泡时气泡自身的 combinedClickable 会消费事件，此处不重复触发。
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    // indication=null：去掉整行的按压阴影/涟漪反馈——非多选时点击左右空白不应有任何按压反馈；
                    // 选中状态的反馈交由 SelectCheck 勾圈和气泡不透明度变化体现。
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (multiSelect) onToggleSelect?.invoke(item) },
                    onLongClickLabel = longHint,
                    onLongClick = openBubbleMenu
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 多选勾：置于消息气泡外侧居中（微信式），不侵入气泡内部
            if (multiSelect) {
                SelectCheck(
                    selected = isSelected,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .clickable { onToggleSelect?.invoke(item) }
                )
            }
            // 发送方气泡贴右：勾在左，weight 占用剩余空间把气泡推到右侧；接收方气泡贴左不加 spacer
            if (mine) Spacer(Modifier.weight(1f))
            Box(Modifier.onGloballyPositioned { bubbleBounds = it.boundsInWindow() }) {
                // 气泡底色/圆角仅用于非「每图独立气泡」场景；独立气泡模式下交给每个图片气泡自己绘制，
                // 外层保持透明，仅依据是否选中调整整体不透明度。
                Column(
                    Modifier.widthIn(max = if (separate) 430.dp else 300.dp)
                        .combinedClickable(
                            // 普通模式点击无操作；多选模式下点击切换选择
                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) },
                            onLongClickLabel = longHint,
                            // 多选模式下长按任一气泡退出多选；普通模式长按打开操作菜单
                            onLongClick = openBubbleMenu
                        )
                        .then(
                            if (separate) Modifier.padding(bottom = 2.dp)
                            else Modifier
                                .background(bubbleColor.copy(alpha = if (isSelected) 0.65f else 1f), shape)
                                // 纯媒体气泡四边等距；文字/文件气泡沿用左右宽、上下窄的微信式留白
                                .padding(horizontal = if (mediaOnly) 8.dp else 12.dp, vertical = 8.dp)
                        ),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    if (item.isText) {
                        Text(item.content, color = textColor, fontSize = 15.sp)
                    } else {
                        // 文件卡统一点击行为：多选切换；.apk 直达安装；其余走预览弹窗（文件已被删除/移动则提示）
                        val cardClick: (FileRefCard) -> Unit = { card ->
                            if (multiSelect) onToggleSelect?.invoke(item)
                            else {
                                val cardIsApk = card.name.substringAfterLast('.', "").equals("apk", ignoreCase = true)
                                if (cardIsApk) {
                                    if (localRefExists(ctx, card.ref)) openWithApp(ctx, Uri.parse(card.ref))
                                    else onPreviewRef?.invoke(item, card.ref, card.name) // 父级收到后弹「已删除」提示
                                } else onPreviewRef?.invoke(item, card.ref, card.name)
                            }
                        }
                        if (separate) {
                            // 非集成模式且文件总数 > 1：每个文件（图片/视频/普通文件）各自独立气泡（微信式），
                            // 按原始收发顺序逐个展示（不先图后视频/文件）。
                            // 点击/长按事件交给缩略图自身处理（避免被外层空手势吞掉），点击打开全屏媒体、长按弹菜单、多选点击切换选中
                            val mediaIdxLookup = mutableMapOf<String, Int>()
                            orderedMedia.forEachIndexed { i, m -> mediaIdxLookup[m.ref] = i }
                            split.entries.forEach { entry ->
                                when (entry) {
                                    is BubbleEntry.Media -> {
                                        val idx = mediaIdxLookup[entry.ref] ?: 0
                                        if (!entry.isVideo) {
                                            Column(
                                                Modifier
                                                    .widthIn(max = 300.dp)
                                                    .padding(vertical = 2.dp)
                                                    .background(bubbleColor.copy(alpha = if (isSelected) 0.65f else 1f), shape)
                                                    .padding(6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                ChatImageThumb(
                                                    entry.ref, item.name,
                                                    onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, idx) },
                                                    onLongClick = openBubbleMenu,
                                                    onKey = { key -> onThumbKey?.invoke(item.id, entry.ref, key) })
                                            }
                                        } else {
                                            Column(
                                                Modifier
                                                    .widthIn(max = 300.dp)
                                                    .padding(vertical = 2.dp)
                                                    .background(bubbleColor.copy(alpha = if (isSelected) 0.65f else 1f), shape)
                                                    .padding(6.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                ChatVideoThumb(
                                                    entry.ref, item.name,
                                                    onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, idx) },
                                                    onLongClick = openBubbleMenu,
                                                    onKey = { key -> onThumbKey?.invoke(item.id, entry.ref, key) },
                                                    fallbackKeys = item.thumbKeys)
                                            }
                                        }
                                    }
                                    is BubbleEntry.Card -> {
                                        val card = entry.card
                                        Column(
                                            Modifier
                                                .padding(vertical = 2.dp)
                                                .background(bubbleColor.copy(alpha = if (isSelected) 0.65f else 1f), shape)
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            FileCard(
                                                card,
                                                modifier = Modifier.combinedClickable(
                                                    onClick = { cardClick(card) },
                                                    onLongClickLabel = longHint,
                                                    onLongClick = openBubbleMenu
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                        } else {
                            // 集成/单文件模式：图片+视频缩略图网格（或单媒体完整预览）+ 普通文件卡列表
                            if (imageRefs.isNotEmpty() || videoRefs.isNotEmpty()) {
                                // 集成网格体积上限：仅展示前 INTEGRATED_MEDIA_CAP 个媒体；其余计入底部汇总
                                val shownMedia = orderedMedia.take(INTEGRATED_MEDIA_CAP)
                                val hiddenMedia = orderedMedia.size - shownMedia.size
                                if (mediaRefs.size > 1) {
                                    // 集成模式多文件：图片+视频合并到同一气泡，固定较小的缩略图网格（不做完整预览），保持收发顺序
                                    FlowRow(
                                        modifier = Modifier.widthIn(max = 300.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        maxItemsInEachRow = 3
                                    ) {
                                        shownMedia.forEachIndexed { si, m ->
                                            val idx = orderedMedia.indexOf(m)
                                            if (!m.isVideo) {
                                                IntegratedThumb(
                                                    m.ref, item.name,
                                                    onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, idx) },
                                                    onLongClick = openBubbleMenu,
                                                    onKey = { key -> onThumbKey?.invoke(item.id, m.ref, key) })
                                            } else {
                                                IntegratedVideoThumb(
                                                    m.ref, item.name,
                                                    onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, idx) },
                                                    onLongClick = openBubbleMenu,
                                                    onKey = { key -> onThumbKey?.invoke(item.id, m.ref, key) },
                                                    fallbackKeys = item.thumbKeys)
                                            }
                                        }
                                    }
                                    // 纯媒体气泡不留额外底部间隙（交给统一 padding）；仅当后面还有内容时才需要分隔
                                    if (!mediaOnly || hiddenMedia > 0) Spacer(Modifier.height(6.dp))
                                } else {
                                    // 单媒体：完整预览（单个媒体不受体积上限限制）
                                    val m0 = orderedMedia[0]
                                    if (!m0.isVideo) {
                                        ChatImageThumb(
                                            mediaRefs[0], item.name,
                                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, 0) },
                                            onLongClick = openBubbleMenu,
                                            onKey = { key -> onThumbKey?.invoke(item.id, mediaRefs[0], key) })
                                    } else {
                                        ChatVideoThumb(
                                            mediaRefs[0], item.name,
                                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenMedia?.invoke(item, 0) },
                                            onLongClick = openBubbleMenu,
                                            onKey = { key -> onThumbKey?.invoke(item.id, mediaRefs[0], key) },
                                            fallbackKeys = item.thumbKeys)
                                    }
                                    if (!mediaOnly) Spacer(Modifier.height(6.dp))
                                    // 已因媒体上限被截断需显示汇总（单媒体理论上不会发生，防御性处理）
                                    if (hiddenMedia > 0) Spacer(Modifier.height(6.dp))
                                }
                            }
                            if (fileCards.isNotEmpty()) {
                                // 普通文件卡体积上限：仅展示前 INTEGRATED_CARD_CAP 个；其余计入底部汇总
                                val shownCards = fileCards.take(INTEGRATED_CARD_CAP)
                                val hiddenCards = fileCards.size - shownCards.size
                                shownCards.forEach { card ->
                                    FileCard(
                                        card,
                                        modifier = Modifier.combinedClickable(
                                            onClick = { cardClick(card) },
                                            onLongClickLabel = longHint,
                                            onLongClick = openBubbleMenu
                                        )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                            // 超出气泡体积上限：在气泡底部统一汇总显示「等 N 个文件」（微信式），
                            // 媒体网格与文件卡被截断的数量都计入；无截断则不显示
                            val hiddenTotal = (if (imageRefs.isNotEmpty() || videoRefs.isNotEmpty())
                                orderedMedia.size - orderedMedia.take(INTEGRATED_MEDIA_CAP).size else 0)
                                + (fileCards.size - fileCards.take(INTEGRATED_CARD_CAP).size)
                            if (hiddenTotal > 0) {
                                Text(
                                    text = moreFilesLabel(hiddenTotal),
                                    fontSize = 12.sp,
                                    color = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        if (noPreview) {
                            Text(item.name, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                        }
                        if (canPreview && noPreview) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                previewLabel,
                                color = if (mine) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    if (multiSelect) onToggleSelect?.invoke(item) else onPreview?.invoke(item)
                                }
                            )
                        }
                    }
                    if (item.progressText.isNotEmpty() && item.state == "进行中") {
                        Text(
                            item.progressText,
                            color = textColor.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(top = 4.dp).widthIn(min = 140.dp)
                        )
                    }
                    if (cancellable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            cancelText,
                            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                android.util.Log.i("OrangeGO.Send", "Cancel button clicked, item.id=${item.id} state=${item.state}")
                                onCancel(item.id)
                            }
                        )
                    }
                }
                // 多选选中态：半透明橙色高亮遮罩（半透明遮罩更明显）
                if (isSelected) {
                    Box(Modifier.matchParentSize().clip(shape)
                        .background(selectTint.copy(alpha = 0.22f)))
                }
                // 长按操作菜单（微信式深色小浮层：近纯黑半透明背景 + 圆角 + 细分割线，贴近气泡）
                val menuItems = mutableListOf<Triple<androidx.compose.ui.graphics.vector.ImageVector, String, () -> Unit>>()
                if (item.isText) {
                    menuItems.add(Triple(Icons.Default.ContentCopy, menuCopyTxt) {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("OrangeGO_text", item.content))
                        android.widget.Toast.makeText(ctx, copiedToast, android.widget.Toast.LENGTH_SHORT).show()
                        menuOpen = false
                    })
                }
                if (onEnterMultiSelect != null) {
                    menuItems.add(Triple(Icons.Default.DoneAll, menuMultiTxt) {
                        onEnterMultiSelect(item); menuOpen = false
                    })
                }
                if (onShare != null) {
                    menuItems.add(Triple(Icons.Default.Share, menuShareTxt) {
                        onShare(item); menuOpen = false
                    })
                }
                if (item.isText) {
                    menuItems.add(Triple(Icons.Default.Save, menuSaveTxtTxt) {
                        saveTextToTxt(ctx, item.content); menuOpen = false
                    })
                }
                // 撤回：仅发送方、2 分钟内、状态未撤回时提供（微信式）
                if (mine && item.sendId.isNotEmpty() && onRecall != null &&
                    (System.currentTimeMillis() - item.timestamp) <= 120_000L
                ) {
                    menuItems.add(Triple(Icons.AutoMirrored.Filled.ArrowBack, menuRecallTxt) {
                        onRecall(item); menuOpen = false
                    })
                }
                if (onDeleteItem != null) {
                    menuItems.add(Triple(Icons.Default.Delete, menuDeleteTxt) {
                        onDeleteItem(item); menuOpen = false
                    })
                }
                // 长按菜单：微信式「桌式小菜单」——深灰背景、水平平铺(图标上/文字下)、
                // 小三角箭头指向气泡、智能上/下方弹出、点外部关闭、弹性展开动画
                if (menuOpen && bubbleBounds != Rect.Zero) {
                    BubblePopupMenu(
                        items = menuItems,
                        anchor = bubbleBounds,
                        onDismiss = { menuOpen = false }
                    )
                }
            }
            }
            // 记录信息行（气泡框外）：统一「方向(发给/来自) → 时间 → 进度圈（传输中弧/完成对勾）」；
            // 自己发送→整行靠屏幕右侧，接收→靠左侧。
            if (item.target.isNotEmpty() || item.timestamp > 0) {
                val alignMod = if (mine) Modifier.align(Alignment.End) else Modifier.align(Alignment.Start)
                Row(
                    alignMod.padding(horizontal = 6.dp, vertical = 1.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.target.isNotEmpty()) {
                        Text(
                            if (mine) "$sentTo ${item.target}" else "$fromLabel ${item.target}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.timestamp > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatItemTime(lang, item.timestamp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    if (!item.isText && item.progress > 0f) {
                        Spacer(Modifier.width(5.dp))
                        WhatsBadge(item.progress, item.state)
                    }
                }
            }
    }
}

// ===== 微信式「桌式小菜单」：长按气泡弹出的水平平铺菜单 =====
// 深灰背景(rgba(60,60,60,0.95))、12dp 圆角、8dp 上下内边距；
// 每个条目图标在上/文字在下、约 64dp 宽、24dp 图标、11sp 文字、条目间细分割线；
// 小三角箭头指向被长按气泡；屏上半部向下弹出(箭头朝上)，下半部向上弹出(箭头朝下)；
// 点外部关闭；展开时 scale 0.8→1 弹性动画。

/** 计算菜单位置并回写箭头方向（true=菜单在气泡上方、箭头朝下指向气泡）。 */
private class BubbleMenuPositionProvider(
    private val anchor: Rect,
    private val arrowDown: MutableState<Boolean>
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val padding = 12
        val gap = 10 // 箭头尖与气泡边缘的距离(px)
        val above = anchor.center.y > windowSize.height / 2f // 气泡在下半屏 → 菜单向上弹
        arrowDown.value = above
        val rawTop = (if (above) anchor.top - gap - popupContentSize.height else anchor.bottom + gap).toInt()
        // 安全钳制：菜单尺寸可能超过窗口（如消息较多/窄屏），此时 coerceIn 的空区间会抛
        // IllegalArgumentException 导致长按弹菜单瞬间闪退（自己刚发出、含撤回项的消息菜单最长，
        // 最易触发）。当窗口放不下菜单时退化为紧贴窗口边缘，保证弹窗既不越界也不崩溃。
        val topMin = padding
        val topMax = (windowSize.height - popupContentSize.height - padding).coerceAtLeast(topMin)
        val top = rawTop.coerceIn(topMin, topMax)
        val leftMin = padding
        val leftMax = (windowSize.width - popupContentSize.width - padding).coerceAtLeast(leftMin)
        val left = (anchor.center.x - popupContentSize.width / 2f).toInt().coerceIn(leftMin, leftMax)
        return IntOffset(left, top)
    }
}

@Composable
private fun BubblePopupMenu(
    items: List<Triple<ImageVector, String, () -> Unit>>,
    anchor: Rect,
    onDismiss: () -> Unit
) {
    if (items.isEmpty()) return
    val itemWidth = 64.dp
    val arrowDown = remember { mutableStateOf(false) }
    // 展开动画：箭头处为缩放原点(在下半屏向上弹→自底部中心展开；向下弹→自顶部中心展开)
    var appear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appear = true }
    val anim by animateFloatAsState(
        targetValue = if (appear) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 900f),
        label = "menuScale"
    )
    val origin = TransformOrigin(0.5f, if (arrowDown.value) 1f else 0f)

    Popup(
        popupPositionProvider = BubbleMenuPositionProvider(anchor, arrowDown),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true)
    ) {
        Column(
            Modifier
                .graphicsLayer {
                    scaleX = anim; scaleY = anim; transformOrigin = origin; alpha = anim
                }
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (arrowDown.value) {
                // 菜单位于气泡上方：先菜单、箭头在下方朝下指向气泡
                BubbleMenuBox(items, itemWidth)
                MenuArrow(pointingUp = false)
            } else {
                // 菜单位于气泡下方：箭头在顶部朝上指向气泡，再显示菜单
                MenuArrow(pointingUp = true)
                BubbleMenuBox(items, itemWidth)
            }
        }
    }
}

@Composable
private fun BubbleMenuBox(items: List<Triple<ImageVector, String, () -> Unit>>, itemWidth: Dp) {
    val bg = Color(0xF25C5C5C) // 微信深灰半透明底
    val borderLine = Color(0x22FFFFFF)
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).background(bg).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.Top) {
            items.forEachIndexed { i, (ic, tx, act) ->
                if (i > 0) {
                    // 条目间细分割线
                    Box(Modifier.width(1.dp).height(44.dp).background(borderLine))
                }
                Column(
                    Modifier
                        .width(itemWidth)
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = { act() },
                            onLongClick = { },
                            onLongClickLabel = ""
                        )
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(ic, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(tx, color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
    }
}

// 小三角箭头：指向被长按气泡。pointingUp=true → 箭头在菜单顶部、尖端朝上(菜单在气泡下方)；
// pointingUp=false → 箭头在菜单底部、尖端朝下(菜单在气泡上方)。
@Composable
private fun MenuArrow(pointingUp: Boolean) {
    val color = Color(0xF25C5C5C)
    Canvas(Modifier.size(14.dp, 8.dp)) {
        val path = Path()
        if (pointingUp) {
            path.moveTo(0f, size.height)
            path.lineTo(size.width / 2f, 0f)
            path.lineTo(size.width, size.height)
        } else {
            path.moveTo(0f, 0f)
            path.lineTo(size.width / 2f, size.height)
            path.lineTo(size.width, 0f)
        }
        path.close()
        drawPath(path, color)
    }
}

// ===== 多选勾（微信风格）：选中=橙色圆底+白勾，未选中=空心圆 =====
@Composable
private fun SelectCheck(selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) Color(0xFFFF8A00) else Color(0x66000000))
            .then(if (!selected) Modifier.border(1.5.dp, Color.White.copy(alpha = 0.9f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
    }
}

// ===== 非图片文件卡片（图标 + 文件名 + 大小），微信式文件气泡，接收/发送共用 =====
data class FileRefCard(val ref: String, val name: String, val size: Long)

/** 一条记录内一个文件在气泡里的展示条目，保持原始发送/接收顺序（图片、视频、普通文件交错）。 */
sealed interface BubbleEntry {
    /** 图片或视频媒体条目；isVideo 区分展示方式。 */
    data class Media(val ref: String, val isVideo: Boolean) : BubbleEntry
    /** 非图片/视频的普通文件卡条目。 */
    data class Card(val card: FileRefCard) : BubbleEntry
}

/** 一条传输记录里按类型拆分的结果：图片引用列表 + 视频引用列表 + 非图片/视频文件卡列表 + 保序条目列表。
 *  videoUris：视频 ref → 可播放 Uri（file:// 已映射为 FileProvider content://，供播放器用）。
 *  保留 videos 原始 ref 供缩略图/全屏拼页索引使用；播放地址单独取 videoUris。 */
data class BubbleSplit(
    val images: List<String>,
    val videos: List<String>,
    val cards: List<FileRefCard>,
    val entries: List<BubbleEntry> = emptyList(),
    val videoUris: Map<String, Uri> = emptyMap()
)

/** 计算视频的 content:// (FileProvider) 播放地址；file:// 外部路径必须映射为 FileProvider Uri 才能被 VideoView 播放。
 *  失败（解析/实例化 provider 异常）返回 null，由调用方回退到原 Uri。 */
private fun videoPlayUri(context: Context, ref: String): Uri? {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
    if (u.scheme == "file") {
        return runCatching {
            FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", File(u.path ?: ""))
        }.getOrNull()
    }
    return u
}

// 记录级「图片/文件」拆分缓存：key = (记录 id, 所用引用集合)。
// 引用集合变化（如接收文件刚落盘、发送方刚选定文件）时自动失效重算，
// 避免把「无预览」的旧拆分结果一直显示成文件名。
private val splitCache = java.util.concurrent.ConcurrentHashMap<Pair<Long, List<String>>, BubbleSplit>()

/** 拆分缓存的 key：优先用 localRefs，退化到旧 localRef；引用集合变化即视为内容变化。 */
private fun splitCacheKey(item: TransferItem): Pair<Long, List<String>> =
    item.id to (if (item.localRefs.isNotEmpty()) item.localRefs
        else listOfNotNull(item.localRef.ifEmpty { null }))

/** 拆分一条传输记录的本地引用为图片/视频列表与文件卡片（IO 线程调用）；顺带解析媒体宽高比入全局缓存。 */
private fun computeBubbleSplit(context: Context, item: TransferItem): BubbleSplit {
    val base = if (item.localRefs.isNotEmpty()) item.localRefs
        else listOfNotNull(item.localRef.ifEmpty { null })
    val images = mutableListOf<String>()
    val videos = mutableListOf<String>()
    val videoUris = mutableMapOf<String, Uri>()
    val cards = mutableListOf<FileRefCard>()
    val entries = mutableListOf<BubbleEntry>()
    val seen = HashSet<String>()
    for (ref in base) {
        if (ref.isBlank() || !seen.add(ref)) continue
        val (n, s) = refNameSize(context, ref)
        when {
            n.startsWith("vthumb_") -> {
                videos.add(ref)
                entries.add(BubbleEntry.Media(ref, isVideo = true))
            }
            isImageExt(n) -> {
                images.add(ref)
                entries.add(BubbleEntry.Media(ref, isVideo = false))
                // 只读图片头部宽高（不解码像素），让缩略图在图片加载前就按真实比例占位，避免加载后高度跳变
                if (!thumbRatioCache.containsKey(ref)) imageRefRatio(context, ref)?.let { thumbRatioCache[ref] = it }
            }
            isVideoExt(n) -> {
                videos.add(ref)
                entries.add(BubbleEntry.Media(ref, isVideo = true))
                // 顺带读视频宽高比（MediaMetadataRetriever），供视频缩略图比例自适应
                if (!videoRatioCache.containsKey(ref)) videoRefRatio(context, ref)?.let { videoRatioCache[ref] = it }
                // 缓存可播放地址（file:// → FileProvider content://），供播放器直接使用
                if (!videoUris.containsKey(ref)) videoPlayUri(context, ref)?.let { videoUris[ref] = it }
            }
            else -> {
                val card = FileRefCard(ref, n, s)
                cards.add(card)
                entries.add(BubbleEntry.Card(card))
            }
        }
    }
    return BubbleSplit(images, videos, cards, entries, videoUris)
}

/** 仅解析图片文件头得到宽高比（inJustDecodeBounds，不解码像素），失败返回 null。 */
private fun imageRefRatio(context: Context, ref: String): Float? = try {
    val model = previewImageModel(ref) ?: return null
    val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    when (model) {
        is File -> android.graphics.BitmapFactory.decodeFile(model.absolutePath, opts)
        is Uri -> context.contentResolver.openInputStream(model)?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
        else -> null
    }
    val w = opts.outWidth.toFloat()
    val h = opts.outHeight.toFloat()
    if (w > 0f && h > 0f) (w / h).coerceIn(0.5f, 2.0f) else null
} catch (_: Exception) { null }

@Composable
private fun FileCard(card: FileRefCard, modifier: Modifier = Modifier) {
    // 安装包(.apk)：尝试异步加载真实应用图标；取不到时用兜底文件图标
    val isApk = card.name.isNotEmpty() &&
        card.name.substringAfterLast('.', "").equals("apk", ignoreCase = true)
    // 仅 file:// 引用能直接作为归档路径解析；content:// 走兜底图标
    val apkPath = remember(card.ref) {
        val u = runCatching { Uri.parse(card.ref) }.getOrNull()
        if (u?.scheme == "file") u.path else null
    }
    val appIcon = rememberApkIconDrawable(if (isApk) apkPath else null)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center) {
            if (appIcon != null) {
                AsyncImage(
                    model = appIcon, contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Office 文档显示官方品牌图标（Word 蓝 / Excel 绿 / PowerPoint 橙）
                val ext = card.name.substringAfterLast('.', "").lowercase()
                val officeRes = when (ext) {
                    "doc", "docx" -> R.drawable.word
                    "xls", "xlsx", "xlsm", "csv" -> R.drawable.excel
                    "ppt", "pptx", "pps", "ppsx" -> R.drawable.powerpoint
                    "pdf" -> R.drawable.pdf
                    "zip", "zipx", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "zst", "z", "lz", "arj", "iso" -> R.drawable.ic_zip
                    else -> 0
                }
                if (officeRes != 0) {
                    Image(painterResource(officeRes), null, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(card.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (card.size >= 0) formatSize(card.size) else "未知",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

/**
 * 异步加载 .apk 的真实应用图标（IO 线程解析，避免 UI 线程卡顿）。
 * 结果以 [path] 为 key 作局部缓存（remember），取不到返回 null，由调用方用兜底图标。
 */
@Composable
@Suppress("DEPRECATION")
private fun rememberApkIconDrawable(path: String?): Drawable? {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) null
            else runCatching {
                val pm = context.packageManager
                val info = pm.getPackageArchiveInfo(path, 0)?.applicationInfo ?: return@runCatching null
                info.sourceDir = path
                info.publicSourceDir = path
                info.loadIcon(pm)
            }.getOrNull()
        }
    }
    return icon
}

/** 解析单个本地引用的显示名与大小（须在 IO 线程调用）。 */
private fun refNameSize(context: Context, ref: String): Pair<String, Long> {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return "文件" to -1L
    return if (u.scheme == "file") {
        val f = File(u.path ?: "")
        (if (f.name.isNotBlank()) f.name else "文件") to (if (f.exists()) f.length() else -1L)
    } else {
        var name = u.lastPathSegment ?: "文件"
        var size = -1L
        runCatching {
            context.contentResolver.query(u, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        name to size
    }
}

/**
 * 由本地引用（file:// 或 content://）计算稳定的内容指纹 key，供 ThumbCache 命中/写入。
 * 无法得到稳定指纹时返回 null（宁可不缓存，也不算不稳定的 key）：
 *  - file:// 文件不存在 → null；
 *  - content:// 取不到修改时间且是图片/视频 → null。
 */
@Suppress("DEPRECATION")
private fun thumbKeyOf(context: Context, ref: String): String? {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
    if (u.scheme == "file") {
        val f = File(u.path ?: "")
        if (!f.isFile || f.name.isBlank()) return null
        return ThumbCache.keyOf(f.name, f.length(), f.lastModified())
    }
    // content:// — 查询真实显示名、大小、修改时间列
    var name: String? = null
    var size = -1L
    var mtime = -1L
    val ok = runCatching {
        context.contentResolver.query(u, null, null, null, null)?.use { c ->
            val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si = c.getColumnIndex(OpenableColumns.SIZE)
            val mi = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
            if (c.moveToFirst()) {
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                if (mi >= 0 && !c.isNull(mi)) mtime = c.getLong(mi) * 1000L
            }
        }
        true
    }.getOrDefault(false)
    if (!ok) return null
    val n = name ?: return null
    if (size < 0) return null
    if (mtime < 0) {
        // 取不到修改时间的媒体文件：key 不稳定，宁可不缓存（仅媒体缩略图场景）
        if (isImageExt(n) || isVideoExt(n)) return null
        mtime = System.currentTimeMillis()
    }
    return ThumbCache.keyOf(n, size, mtime)
}

/** 传输记录显示时间（微信式）：今天→HH:mm，昨天→"昨天 HH:mm"，更早→"M月d日 HH:mm"或"MM-dd HH:mm"。 */
private fun formatItemTime(lang: String, ts: Long): String {
    if (ts <= 0) return ""
    val startToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val startYest = startToday - 86_400_000L
    val hm = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
    return when {
        ts >= startToday -> hm
        ts >= startYest -> "${tr(lang, "昨天", "昨天", "Yesterday", "昨日", "어제", "Ayer", "Hier", "Gestern", "Ontem", "Вчера")} $hm"
        else -> {
            val l = if (lang == "system") systemLangCode() else lang
            val pat = if (l in setOf("zh", "zhTW", "ja", "ko")) "M月d日" else "MM-dd"
            java.text.SimpleDateFormat("$pat HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
        }
    }
}

// ===== 图片缩略图 =====
// 缓存每个图片源解析出的宽高比。LazyColumn 会在项滚出视口时销毁其 composition 状态，
// 若不缓存，滚动回来时 ratio 会先回到 1 再异步重读，造成项高跳变 → 滚动不线性 + 闪现。
private val thumbRatioCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

// ===== 聊天气泡内单张图片缩略图（等比例完整显示，不裁切；微信式：高度随图片比例自适应） =====
// onKey：缩略图已落盘缓存后回调（把 key 上报给所在传输记录）；磁盘缓存命中优先渲染，源文件删除后仍可显示。
@Composable
private fun ChatImageThumb(
    ref: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit,
    onKey: ((String) -> Unit)? = null
) {
    // 图片加载成功后读取固有宽高比，让缩略图随图片比例自适应高度，保证整图完整可见、不裁切
    var ratio by remember(ref) { mutableStateOf(thumbRatioCache[ref] ?: 1f) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 计算内容指纹 key，并优先解析磁盘缓存文件（存在则直接用缓存渲染，源文件被删也可见）
    val key = remember(ref) { thumbKeyOf(context, ref) }
    val diskFile = remember(key) { key?.let { ThumbCache.resolveFile(context, it) } }
    val model = remember(ref, diskFile) { diskFile ?: previewImageModel(ref) }
    AsyncImage(
        model = model,
        contentDescription = name,
        onSuccess = { st ->
            val d = st.result.drawable
            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.toFloat() else 0f
            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.toFloat() else 0f
            if (w > 0f && h > 0f) {
                val r = (w / h).coerceIn(0.5f, 2.0f)
                thumbRatioCache[ref] = r
                ratio = r
            }
            // 本次是从源加载（非磁盘命中）且能转成 Bitmap 时，写入磁盘缓存并上报 key
            if (diskFile == null) {
                val bmp = (d as? BitmapDrawable)?.bitmap
                if (bmp != null && key != null) {
                    scope.launch {
                        ThumbCache.put(context, key, bmp)
                        onKey?.invoke(key)
                    }
                }
            }
        },
        modifier = Modifier
            .widthIn(max = 210.dp)
            .heightIn(max = 260.dp)
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = name,
                onLongClick = onLongClick
            ),
        contentScale = ContentScale.Fit
    )
}

// ===== 集成模式的小缩略图（固定较小尺寸，填满裁剪；不做完整预览） =====
// 用于「图片集成显示」开启时：多张图片合并到一个气泡内紧凑排列。
// 磁盘缓存优先渲染；加载成功后写入磁盘缓存并上报 key。
@Composable
private fun IntegratedThumb(
    ref: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit, size: Dp = 84.dp,
    onKey: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = remember(ref) { thumbKeyOf(context, ref) }
    val diskFile = remember(key) { key?.let { ThumbCache.resolveFile(context, it) } }
    AsyncImage(
        model = remember(ref, diskFile) { diskFile ?: previewImageModel(ref) },
        contentDescription = name,
        onSuccess = { st ->
            if (diskFile == null) {
                val bmp = (st.result.drawable as? BitmapDrawable)?.bitmap
                if (bmp != null && key != null) {
                    scope.launch {
                        ThumbCache.put(context, key, bmp)
                        onKey?.invoke(key)
                    }
                }
            }
        },
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = name,
                onLongClick = onLongClick
            ),
        contentScale = ContentScale.Crop
    )
}

// ===== 视频缩略图 =====
// 用 MediaMetadataRetriever 抓首帧；结果全局缓存（LazyColumn 项销毁后不重复解码）。
private val videoThumbCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()
// 视频宽高比缓存（首帧宽/高），供缩略图比例自适应；解码失败回退 16:9。
private val videoRatioCache = java.util.concurrent.ConcurrentHashMap<String, Float>()

/** 解析视频宽高比（MediaMetadataRetriever，不解码像素），失败返回 null。 */
private fun videoRefRatio(context: Context, ref: String): Float? = runCatching {
    val mmr = MediaMetadataRetriever()
    try {
        val u = Uri.parse(ref)
        if (u.scheme == "file") mmr.setDataSource(u.path ?: "") else mmr.setDataSource(context, u)
        val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull()
        val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull()
        if (w != null && h != null && w > 0f && h > 0f) (w / h).coerceIn(0.5f, 2.2f) else null
    } finally { runCatching { mmr.release() } }
}.getOrNull()

/** 抓取视频首帧缩略图（IO 线程调用，成功时按宽 > 512 等比缩放），失败返回 null。 */
private fun loadVideoThumb(context: Context, ref: String): android.graphics.Bitmap? {
    if (videoThumbCache.size > 96) videoThumbCache.clear() // 简单上限：超量整体清空，不手动回收缓存位图
    return runCatching {
        val mmr = MediaMetadataRetriever()
        try {
            val u = Uri.parse(ref)
            if (u.scheme == "file") mmr.setDataSource(u.path ?: "") else mmr.setDataSource(context, u)
            var frame = mmr.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame == null) frame = mmr.getFrameAtTime()
            if (frame == null) return@runCatching null
            val w = frame.width
            val h = frame.height
            val bmp = if (w > 512 || h > 512) {
                val s = 512f / maxOf(w, h)
                android.graphics.Bitmap.createScaledBitmap(
                    frame, (w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), true)
            } else frame
            if (bmp !== frame) frame.recycle()
            bmp
        } finally { runCatching { mmr.release() } }
    }.getOrNull()
}

/**
 * 取视频缩略图：优先磁盘缓存（命中则直接读回，不打回 MediaMetadataRetriever）；
 * 未命中则后台 IO 抓帧，成功后写磁盘缓存并上报 key。
 */
@Composable
private fun rememberVideoThumb(ref: String, onKey: ((String) -> Unit)? = null, fallbackKeys: List<String> = emptyList()): android.graphics.Bitmap? {
    val context = LocalContext.current
    val key = remember(ref) { thumbKeyOf(context, ref) }
    var cached by remember(ref, key) { mutableStateOf(videoThumbCache[ref]) }
    LaunchedEffect(ref, key) {
        if (cached != null) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) {
            // 磁盘缓存优先：命中直接读回流式图，避免对已删除的源文件再次解码
            val diskFile = key?.let { ThumbCache.resolveFile(context, it) }
                // 原文件已删除（key 为 null）时，用 fallbackKeys（TransferItem.thumbKeys）查缓存
                ?: fallbackKeys.firstNotNullOfOrNull { fk -> ThumbCache.resolveFile(context, fk) }
            val fromDisk = diskFile?.let {
                runCatching { android.graphics.BitmapFactory.decodeFile(it.absolutePath) }.getOrNull()
            }
            if (fromDisk != null) {
                // 命中已落盘缩略图：key 幂等上报，保证该记录能回收它
                if (onKey != null && key != null) onKey(key)
                fromDisk
            } else {
                loadVideoThumb(context, ref)?.also { bmp ->
                    if (key != null) {
                        ThumbCache.put(context, key, bmp)
                        if (onKey != null) onKey(key)
                    }
                }
            }
        }
        if (bmp != null) videoThumbCache[ref] = bmp
        cached = bmp
    }
    return cached
}

// ===== 媒体缩略图统一容器：圆角裁剪 + 占位底色 + 点击/长按（图片/视频共用） =====
@Composable
private fun MediaThumbBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClickLabel = "media",
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ===== 聊天气泡内单段视频缩略图（大图式，比例自适应，中央半透明播放三角） =====
@Composable
private fun ChatVideoThumb(ref: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit, onKey: ((String) -> Unit)? = null, fallbackKeys: List<String> = emptyList()) {
    val ratio = videoRatioCache[ref] ?: (16f / 9f)
    val thumb = rememberVideoThumb(ref, onKey, fallbackKeys)
    MediaThumbBox(
        Modifier.widthIn(max = 210.dp).aspectRatio(ratio),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        if (thumb != null) {
            Image(thumb.asImageBitmap(), name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            AsyncImage(model = previewImageModel(ref), contentDescription = name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        // 中央半透明播放三角（微信式）
        Box(Modifier.size(40.dp).background(Color.Black.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    }
}

// ===== 集成模式的小视频缩略图（固定较小尺寸，填满裁剪；中央小播放三角） =====
@Composable
private fun IntegratedVideoThumb(ref: String, name: String, onClick: () -> Unit, onLongClick: () -> Unit, size: Dp = 84.dp, onKey: ((String) -> Unit)? = null, fallbackKeys: List<String> = emptyList()) {
    val thumb = rememberVideoThumb(ref, onKey, fallbackKeys)
    MediaThumbBox(
        Modifier.size(size),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        if (thumb != null) {
            Image(thumb.asImageBitmap(), name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AsyncImage(model = previewImageModel(ref), contentDescription = name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.size(26.dp).background(Color.Black.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

// ===== WhatsApp 式状态角标：传输中为细圆环进度，完成后变为「圆圈+对勾」 =====
// 小尺寸、与时间同一行；颜色沿用时间文字色（onSurfaceVariant）。
@Composable
private fun WhatsBadge(progress: Float, state: String = "", dp: Dp = 14.dp) {
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    val errColor = MaterialTheme.colorScheme.error
    // 失败/被拒/已取消：用红 X 明确区分于成功对勾，避免用户误以为传输成功
    if (state == "失败" || state == "被拒" || state == "已取消") {
        Canvas(Modifier.size(dp)) {
            val sw = 1.6.dp.toPx()
            val r = size.minDimension / 2 - sw
            val c = center
            drawCircle(color = errColor, radius = r, center = c, style = Stroke(width = sw))
            val d = r * 0.42f
            drawLine(errColor,
                Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(errColor,
                Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), strokeWidth = sw, cap = StrokeCap.Round)
        }
        return
    }
    Canvas(Modifier.size(dp)) {
        val sw = 1.6.dp.toPx()
        val r = size.minDimension / 2 - sw
        val c = center
        if (progress >= 1f) {
            // 完成/成功：空心圆 + 中间对勾
            drawCircle(color = tint, radius = r, center = c, style = Stroke(width = sw))
            val p = Path().apply {
                moveTo(c.x - r * 0.42f, c.y + r * 0.10f)
                lineTo(c.x - r * 0.10f, c.y + r * 0.34f)
                lineTo(c.x + r * 0.48f, c.y - r * 0.34f)
            }
            drawPath(p, color = tint, style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            // 传输中：底色圆环 + 进度弧
            drawArc(color = tint.copy(alpha = 0.22f), startAngle = -90f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(width = sw))
            drawArc(color = tint, startAngle = -90f, sweepAngle = 360f * progress.coerceIn(0f, 1f), useCenter = false,
                topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = sw, cap = StrokeCap.Round))
        }
    }
}

// ===== 全屏分页媒体预览（微信式，图片/视频混合，左右滑动 + 点任意/右上关闭） =====
@Composable
private fun FullscreenMediaPreview(item: TransferItem, initialIndex: Int, onDismiss: () -> Unit) {
    if (item.isText) { onDismiss(); return }
    val context = LocalContext.current
    val refs = remember(item.id) { itemMediaRefs(context, item, item.name) }
    if (refs.isEmpty()) { onDismiss(); return }
    val start = initialIndex.coerceIn(0, refs.size - 1)
    val pagerState = rememberPagerState(initialPage = start) { refs.size }

    Dialog(
        onDismissRequest = onDismiss,
        // 全屏窗口：禁用默认平台宽度约束，铺满整个屏幕
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                Box(
                    Modifier.fillMaxSize()
                        .clickable(onClick = onDismiss, indication = null,
                            interactionSource = remember { MutableInteractionSource() }),
                    contentAlignment = Alignment.Center
                ) {
                    val (ref, isVideo) = refs[page]
                    if (isVideo) {
                        // 点视频画面不关闭预览（播放器区域）：仅点击四周空白黑边或右上关闭键才退出
                        VideoPreview(videoPlayUri(context, ref), item.name)
                    } else {
                        val isDng = ref.substringAfterLast('.', "").equals("dng", ignoreCase = true)
                        if (isDng) {
                            // DNG/RAW：Coil 无法解码，提取内嵌 JPEG 预览显示
                            val bmp by produceState<android.graphics.Bitmap?>(null, ref) {
                                value = withContext(Dispatchers.IO) {
                                    runCatching {
                                        val path = Uri.parse(ref).path
                                        if (path != null) com.orangeway.go.core.extractEmbeddedJpeg(path)?.let {
                                            android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)
                                        } else null
                                    }.getOrNull()
                                }
                            }
                            val b = bmp
                            if (b != null) {
                                Image(b.asImageBitmap(), item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        } else {
                            AsyncImage(
                                model = previewImageModel(ref),
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
            // 右上关闭按钮
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(8.dp)
            ) {
                Box(Modifier.size(34.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            // 页码指示
            Text(
                "${pagerState.currentPage + 1} / ${refs.size}",
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.statusBars).padding(top = 10.dp)
            )
        }
    }
}

// ===== 传输记录文件预览对话框 =====

/** 判断本地源文件（file:// 或 content://）是否仍存在，用于打开预览前的拦截提醒。 */
private fun localRefExists(context: Context, ref: String): Boolean {
    if (ref.isBlank()) return false
    return runCatching {
        val uri = Uri.parse(ref)
        if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            f.exists() && f.isFile
        } else {
            // 只能凭「真能打开」判定存在；getType 对 content:// 删除后仍有缓存，不可作为存在依据
            context.contentResolver.openFileDescriptor(uri, "r")?.use { } != null
        }
    }.getOrDefault(false)
}

@Composable
private fun FilePreviewDialog(item: TransferItem, ref: String = item.localRef, name: String = item.name, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang by OgoLang.code.collectAsState()
    val openText = tr(lang, "用其他应用打开", "用其他應用程式開啟", "Open with", "別のアプリで開く", "다른 앱으로 열기", "Abrir con", "Ouvrir avec", "Öffnen mit", "Abrir com", "Открыть с помощью")
    val tooBigText = tr(lang, "文件过大，仅显示前 512KB", "檔案過大，僅顯示前512KB", "File too large, showing first 512KB", "ファイルが大きいため先頭512KBのみ表示", "파일이 커서 앞 512KB만 표시", "Archivo demasiado grande, se muestran primeros 512KB", "Fichier trop volumineux, 512 Ko affichés", "Datei zu groß, erste 512KB angezeigt", "Arquivo grande demais, mostrando 512KB iniciais", "Файл слишком большой, показаны первые 512 КБ")
    val loadingText = tr(lang, "加载中…", "載入中…", "Loading…", "読み込み中…", "불러오는 중…", "Cargando…", "Chargement…", "Laden…", "Carregando…", "Загрузка…")

    // 解析本地引用为可预览地址
    val refUri = runCatching { Uri.parse(ref) }.getOrNull()
    // 读取/判断存在性用：content:// 直接给 uri；file:// 解析为 File（不存在则视为不可读）
    val resolver = context.contentResolver
    val fileExists = refUri?.let { u ->
        if (u.scheme == "file") File(u.path ?: "").exists()
        else runCatching { resolver.getType(u) != null || resolver.openInputStream(u) != null }.getOrDefault(false)
    } ?: false

    val ext = (name.substringAfterLast('.', "").lowercase())
    val isImage = ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg", "dng")
    val isText = ext in setOf("txt", "log", "json", "xml", "md", "srt", "csv", "ini", "cfg", "conf", "yml", "yaml", "html", "htm", "css", "js", "kt", "java", "c", "cpp", "h", "py", "gradle", "properties")
    val isVideo = ext in VIDEO_EXTS
    val isAudio = ext in setOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus", "amr")

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp) {
            Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(bottom = 12.dp)) {
                // 标题栏
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(4.dp))

                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    when {
                        isImage && fileExists -> AsyncImage(
                            model = refUri, contentDescription = name,
                            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()
                        )
                        isText && fileExists -> TextFileBody(refUri, name, resolver, tooBigText, loadingText)
                        isVideo && fileExists -> VideoPreview(videoPlayUri(context, ref), name)
                        isAudio && fileExists -> AudioPreview(refUri, name)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Office 文档显示官方品牌图标
                            val ext = name.substringAfterLast('.', "").lowercase()
                            val officeRes = when (ext) {
                                "doc", "docx" -> R.drawable.word
                                "xls", "xlsx", "xlsm", "csv" -> R.drawable.excel
                                "ppt", "pptx", "pps", "ppsx" -> R.drawable.powerpoint
                                "pdf" -> R.drawable.pdf
                                "zip", "zipx", "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "zst", "z", "lz", "arj", "iso" -> R.drawable.ic_zip
                                else -> 0
                            }
                            if (officeRes != 0) {
                                Image(painterResource(officeRes), null, modifier = Modifier.size(56.dp))
                            } else {
                                Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(56.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(name, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // 操作区
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    if (fileExists) {
                        Button(onClick = { openWithApp(context, refUri) },
                            shape = RoundedCornerShape(18.dp)) {
                            Text(openText, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// 文本文件主体：后台读入，滚动展示
@Composable
private fun TextFileBody(refUri: Uri?, name: String, resolver: ContentResolver, tooBig: String, loading: String) {
    var content by remember { mutableStateOf<String?>(null) }
    val maxBytes = 512 * 1024
    LaunchedEffect(refUri, name) {
        if (refUri == null) { content = ""; return@LaunchedEffect }
        content = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = if (refUri.scheme == "file") {
                    File(refUri.path ?: "").takeIf { it.exists() }?.readBytes()
                } else resolver.openInputStream(refUri)?.use { it.readBytes() }
                bytes
            }.getOrNull()!!
        }.let { raw ->
            val text = if (raw.size > maxBytes) {
                "<$tooBig>\n\n".toByteArray() + raw.copyOfRange(0, maxBytes)
            } else raw
            text.toString(Charsets.UTF_8)
        }
    }
    val shown = content
    if (shown == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(loading) }
    } else {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp), contentPadding = PaddingValues(12.dp)) {
            item { Text(shown, fontSize = 13.sp, softWrap = true, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) }
        }
    }
}

// 视频：AndroidView 包 VideoView（零依赖）。播放/暂停 + 可拖动进度条 + 当前/总时长（与电脑端一致），无音量调节。
@Composable
private fun VideoPreview(refUri: Uri?, name: String, onAreaClick: () -> Unit = {}) {
    var view: android.widget.VideoView? by remember { mutableStateOf(null) }
    var playing by remember { mutableStateOf(false) }
    // 当前进度与总时长（毫秒）；-1 表示未知
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    var seekBlocked by remember { mutableStateOf(false) } // 拖动进度条期间暂停刷新，避免进度条跳回
    var videoRatio by remember { mutableStateOf(16f / 9f) } // 视频实际宽高比，onPrepared 后更新

    DisposableEffect(Unit) {
        onDispose { runCatching { view?.stopPlayback() } }
    }

    LaunchedEffect(view) {
        while (true) {
            delay(500)
            val v = view
            if (v != null) {
                val d = runCatching { v.duration.toLong() }.getOrDefault(0L)
                if (d > 0) durationMs = d
                if (!seekBlocked) {
                    val p = runCatching { v.currentPosition.toLong() }.getOrDefault(0L)
                    positionMs = p
                    val playingNow = runCatching { v.isPlaying }.getOrDefault(false)
                    if (playingNow != playing) playing = playingNow
                }
            }
        }
    }

    // 进度条拖动中被 seekTo 期间标记阻塞，落点后再恢复自动刷新
    var dragTrigger by remember { mutableStateOf(0L) }
    LaunchedEffect(dragTrigger) {
        if (dragTrigger > 0) {
            seekBlocked = true
            val v = view
            if (v != null) runCatching { v.seekTo(dragTrigger.toInt()) }
            delay(300)
            seekBlocked = false
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(videoRatio)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = {
                if (playing) runCatching { view?.pause() } else runCatching { view?.start() }
                playing = !playing
            }, indication = null,
                interactionSource = remember { MutableInteractionSource() })
    ) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ -> playing = false; true }
                    setOnPreparedListener { p ->
                        runCatching { durationMs = p.duration.toLong() }
                        val vw = p.videoWidth
                        val vh = p.videoHeight
                        if (vw > 0 && vh > 0) videoRatio = vw.toFloat() / vh
                        runCatching { p.start() }
                        playing = true
                    }
                }
            },
            update = { vv ->
                view = vv
                if (refUri != null) {
                    runCatching { vv.setVideoURI(refUri) }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // 播放键：暂停时显示在画面中央（半透明圆形），播放时隐藏；点击播放/暂停
        AnimatedVisibility(
            visible = !playing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(64.dp)
                    .clickable {
                        if (playing) runCatching { view?.pause() } else runCatching { view?.start() }
                        playing = !playing
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayArrow, null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }
        // 底部半透明渐变控制条：细进度条 + 播放/暂停 + 时间（主流移动播放器样式），无音量
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Slider(
                value = (positionMs.coerceIn(0L, durationMs.coerceAtLeast(1L)) / durationMs.coerceAtLeast(1L).toFloat())
                    .coerceIn(0f, 1f),
                onValueChange = { f -> dragTrigger = (f * durationMs.coerceAtLeast(1L)).toLong() },
                enabled = durationMs > 0,
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    thumbColor = Color.White,
                    disabledActiveTrackColor = Color.White.copy(alpha = 0.4f),
                    disabledInactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    disabledThumbColor = Color.White.copy(alpha = 0.5f)
                )
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable {
                            if (playing) runCatching { view?.pause() } else runCatching { view?.start() }
                            playing = !playing
                        }
                )
                Spacer(Modifier.weight(1f))
                val total = if (durationMs > 0) fmtDuration(durationMs) else "--:--"
                Text(
                    text = "${fmtDuration(positionMs)} / $total",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/** 毫秒 → mm:ss 或 h:mm:ss（与电脑端时长格式一致；未知返回 --:--）。 */
private fun fmtDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
    else "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

/** 气泡体积超限汇总文案「等 N 个文件」，纯 UI 文案、无需随语言文件；多语言统一处理。 */
@Composable
private fun moreFilesLabel(count: Int): String {
    val lang by OgoLang.code.collectAsState()
    return tr(lang,
        "等 $count 个文件", "等 $count 個檔案", "and $count more", "あと $count 件",
        "외 ${count}개 파일", "y $count más", "et $count autres", "und $count weitere",
        "e mais $count", "ещё $count файл(ов)")
}

// 音频：MediaPlayer + 播放/暂停
@Composable
private fun AudioPreview(refUri: Uri?, name: String) {
    val context = LocalContext.current
    if (refUri == null) return
    var playing by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val audioUri = if (refUri.scheme == "file") {
        runCatching { FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", File(refUri.path ?: "")) }.getOrNull()
    } else refUri

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.stop() }
            runCatching { player?.release() }
            player = null
        }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(8.dp))
        Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        IconButton(
            onClick = {
                if (player?.isPlaying == true) {
                    runCatching { player?.pause() }; playing = false
                } else {
                    val p = player ?: MediaPlayer().also { player = it }
                    runCatching {
                        p.reset()
                        if (audioUri != null) { p.setDataSource(context, audioUri) }
                        p.prepare()
                        p.start()
                    }
                    playing = true
                }
            },
            modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

// 用其他应用打开
private fun openWithApp(context: Context, uri: Uri?) {
    if (uri == null) return
    val target = if (uri.scheme == "file") {
        runCatching { FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", File(uri.path ?: "")) }.getOrNull()
    } else uri
    if (target == null) return
    val mime = context.contentResolver.getType(target) ?: "application/octet-stream"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(target, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
    }
}

/** 系统分享：文本走 ACTION_SEND 文本；本地文件把 file:// 换成 FileProvider URI 后按单/多文件 ACTION_SEND(MULTIPLE)。 */
private fun launchShare(context: Context, title: String, items: List<TransferItem>) {
    if (items.isEmpty()) return
    val texts = items.filter { it.isText }.map { it.content }.filter { it.isNotBlank() }
    val refs = items.filter { !it.isText }.flatMap { it.localRefs }.distinct()
    val fileUris = refs.mapNotNull { ref ->
        val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return@mapNotNull null
        if (u.scheme == "file") {
            runCatching { FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", File(u.path ?: "")) }.getOrNull()
        } else u
    }.distinct()
    // 无文本也无文件可分享：直接返回
    if (texts.isEmpty() && fileUris.isEmpty()) return

    val intent = Intent(Intent.ACTION_SEND)
    if (fileUris.isNotEmpty()) {
        val mime = if (fileUris.size == 1)
            (context.contentResolver.getType(fileUris[0]) ?: "application/octet-stream")
        else "application/octet-stream"
        if (fileUris.size == 1) {
            intent.setDataAndType(fileUris[0], mime)
            intent.putExtra(Intent.EXTRA_STREAM, fileUris[0])
        } else {
            intent.setType(mime)
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(fileUris))
        }
        // 授予读取权限（多文件时 clipData 同样需要）
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            intent.clipData = ClipData.newUri(context.contentResolver, title, fileUris[0]).apply {
                for (i in 1 until fileUris.size) addItem(ClipData.Item(fileUris[i]))
            }
        }
        if (texts.isNotEmpty()) intent.putExtra(Intent.EXTRA_TEXT, texts.joinToString("\n\n"))
    } else {
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_TEXT, texts.joinToString("\n\n"))
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun SettingsScreen(
    currentName: String,
    receiveDir: String,
    themeMode: Int,
    lang: String,
    autoSave: Boolean,
    saveHistory: Boolean,
    pinEnabled: Boolean,
    pinCode: String,
    saveToGallery: Boolean,
    autoAcceptText: Boolean,
    integrateImages: Boolean,
    autoSaveWhitelist: Boolean,
    onRename: (String) -> Unit,
    onChangeDir: (String) -> Unit,
    onResetDir: () -> Unit,
    onSetTheme: (Int) -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetAutoSave: (Boolean) -> Unit,
    onSetSaveHistory: (Boolean) -> Unit,
    onSetSaveToGallery: (Boolean) -> Unit,
    onSetPinEnabled: (Boolean) -> Unit,
    onSetPinCode: (String) -> Unit,
    onSetAutoAcceptText: (Boolean) -> Unit,
    onSetIntegrateImages: (Boolean) -> Unit,
    onSetAutoSaveWhitelist: (Boolean) -> Unit,
    onClearTransfers: () -> Unit,
    retentionDays: Int,
    onSetRetentionDays: (Int) -> Unit,
    onBack: () -> Unit,
    onSubChanged: (Boolean) -> Unit,
    returnToRoot: Int,
    hasUpdate: Boolean = false
) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var pickingDir by remember { mutableStateOf(false) }
    var confirmClearTransfers by remember { mutableStateOf(false) }
    var confirmClearThumb by remember { mutableStateOf(false) }
    // 缩略图缓存大小（进入设置时读取，清除后刷新）
    var thumbCacheBytes by remember { mutableStateOf(com.orangeway.go.core.ThumbCache.totalSizeBytes(context)) }
    val clearThumbScope = rememberCoroutineScope()
    var pickRetention by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    var pinInput by remember { mutableStateOf("") }
    // 提升滚动状态，避免进入二级页再返回时列表回到顶部
    val settingsListState = rememberLazyListState()

    // 设置页多语言文案
    val tiSettings = tr(lang, "设置", "設定", "Settings", "設定", "설정", "Ajustes", "Paramètres", "Einstellungen", "Configurações", "Настройки")
    val secGeneral = tr(lang, "通用", "通用", "General", "一般", "일반", "General", "Général", "Allgemein", "Geral", "Общие")
    val secReceive = tr(lang, "接收", "接收", "Receive", "受信", "받기", "Recepción", "Réception", "Empfang", "Recebimento", "Приём")
    val secOther = tr(lang, "其他", "其他", "Other", "その他", "기타", "Otros", "Autres", "Sonstiges", "Outros", "Другое")
    val rowTheme = tr(lang, "主题", "主題", "Theme", "テーマ", "테마", "Tema", "Thème", "Thema", "Tema", "Тема")
    val rowLanguage = tr(lang, "语言", "語言", "Language", "言語", "언어", "Idioma", "Langue", "Sprache", "Idioma", "Язык")
    val rowSaveDir = tr(lang, "保存目录", "儲存目錄", "Save folder", "保存先", "저장 폴더", "Carpeta de guardado", "Dossier de sauvegarde", "Speicherordner", "Pasta de salvamento", "Папка сохранения")
    val toggleAutoSave = tr(lang, "自动保存", "自動儲存", "Auto-save", "自動保存", "자동 저장", "Guardado automático", "Enregistrement auto", "Automatisch speichern", "Salvamento automático", "Автосохранение")
    val toggleAutoSaveWhitelist = tr(lang, "自动保存收藏夹设备的文件", "自動儲存收藏夾裝置的檔案", "Auto-save from favorite devices", "お気に入りデバイスのファイルを自動保存", "즐겨찾기 기기의 파일 자동 저장", "Guardar archivos de dispositivos favoritos", "Enregistrer les fichiers des appareils favoris", "Dateien von Lieblingsgeräten automatisch speichern", "Salvar arquivos de dispositivos favoritos", "Автосохранение файлов от избранных устройств")
    val toggleAutoAcceptText = tr(lang, "自动接收文本消息", "自動接收文字訊息", "Auto-accept text messages", "テキストメッセージを自動受信", "텍스트 메시지 자동 수신", "Recibir mensajes de texto automáticamente", "Recevoir les messages texte automatiquement", "Textnachrichten automatisch empfangen", "Receber mensagens de texto automaticamente", "Автоприём текстовых сообщений")
    val toggleIntegrateImages = tr(lang, "文件集成显示", "檔案整合顯示", "Show all files in one bubble", "ファイルをまとめて表示", "파일 통합 표시", "Mostrar todos los archivos en una burbuja", "Afficher tous les fichiers dans une bulle", "Alle Dateien in einer Blase anzeigen", "Mostrar todos os arquivos em uma bolha", "Показывать все файлы одним сообщением")
    val togglePin = tr(lang, "启用 PIN 密码", "啟用 PIN 密碼", "Enable PIN code", "PINの有効化", "PIN 활성화", "Activar PIN", "Activer le code PIN", "PIN aktivieren", "Ativar PIN", "Включить PIN-код")
    val rowPin = tr(lang, "PIN 密码", "PIN 密碼", "PIN code", "PINコード", "PIN 번호", "Código PIN", "Code PIN", "PIN-Code", "Código PIN", "PIN-код")
    val textSet = tr(lang, "已设置", "已設定", "Set", "設定済み", "설정됨", "Configurado", "Défini", "Festgelegt", "Definido", "Установлен")
    val textNotSet = tr(lang, "未设置", "未設定", "Not set", "未設定", "미설정", "No configurado", "Non défini", "Nicht festgelegt", "Não definido", "Не установлен")
    val toggleGallery = tr(lang, "保存到相册", "儲存到相簿", "Save to gallery", "アルバムに保存", "갤러리에 저장", "Guardar en galería", "Enregistrer dans la galerie", "In Galerie speichern", "Salvar na galeria", "Сохранить в галерею")
    val toggleHistory = tr(lang, "保存到历史记录", "儲存到歷史記錄", "Save to history", "履歴に保存", "기록에 저장", "Guardar en historial", "Enregistrer dans l'historique", "Im Verlauf speichern", "Salvar no histórico", "Сохранить в историю")
    val rowDeviceName = tr(lang, "设备名称", "裝置名稱", "Device name", "デバイス名", "기기 이름", "Nombre del dispositivo", "Nom de l'appareil", "Gerätename", "Nome do dispositivo", "Имя устройства")
    val rowAbout = tr(lang, "关于 OrangeGO", "關於 OrangeGO", "About OrangeGO", "OrangeGO について", "OrangeGO 정보", "Acerca de OrangeGO", "À propos d'OrangeGO", "Über OrangeGO", "Sobre OrangeGO", "О OrangeGO")
    val rowPrivacy = tr(lang, "隐私政策", "隱私權政策", "Privacy policy", "プライバシーポリシー", "개인정보 정책", "Política de privacidad", "Politique de confidentialité", "Datenschutz", "Política de privacidade", "Политика конфиденциальности")
    val rowCheckUpdate = tr(lang, "检查更新", "檢查更新", "Check for updates", "更新を確認", "업데이트 확인", "Buscar actualizaciones", "Vérifier les mises à jour", "Nach Updates suchen", "Verificar atualizações", "Проверить обновления")
    val rowFeedback = tr(lang, "问题反馈", "問題回饋", "Report an issue", "問題報告", "문제 신고", "Reportar un problema", "Signaler un problème", "Problem melden", "Relatar problema", "Сообщить о проблеме")
    val feedbackSubmitHint = tr(lang, "提交问题或反馈", "提交問題或反饋", "Submit an issue or feedback", "問題やご意見を送信", "문제나 피드백 제출", "Enviar problema o sugerencia", "Soumettre un problème ou un retour", "Problem oder Feedback senden", "Enviar problema ou feedback", "Отправить вопрос или отзыв")
    val textView = tr(lang, "查看", "查看", "View", "表示", "보기", "Ver", "Voir", "Anzeigen", "Ver", "Просмотр")
    val pinTitle = tr(lang, "设置接收 PIN 密码", "設定接收 PIN 密碼", "Set receive PIN", "受信 PIN の設定", "받기 PIN 설정", "Configurar PIN de recepción", "Définir le code PIN de réception", "Empfangs-PIN festlegen", "Definir PIN de recepção", "Установить PIN для приёма")
    val pinDesc = tr(lang, "开启后接收文件需输入本 PIN 才能接受", "開啟後接收檔案需輸入本 PIN 才能接受", "When enabled, receiving files requires entering this PIN", "有効にすると、ファイル受信時にこのPINの入力が必要です", "활성화하면 파일을 받으려면 이 PIN을 입력해야 합니다", "Cuando esté activado, recibir archivos requerirá este PIN", "Une fois activé, recevoir des fichiers nécessite ce code PIN", "Wenn aktiviert, erfordert der Empfang von Dateien diese PIN", "Quando ativado, o recebimento de arquivos exigirá este PIN", "После включения приём файлов потребует этот PIN")
    val pinPlaceholder = tr(lang, "4-6 位数字", "4-6 位數字", "4-6 digits", "4〜6桁の数字", "4-6자리 숫자", "4-6 dígitos", "4-6 chiffres", "4-6 Ziffern", "4-6 dígitos", "4-6 цифр")
    val savePin = tr(lang, "保存 PIN", "儲存 PIN", "Save PIN", "PINを保存", "PIN 저장", "Guardar PIN", "Enregistrer le PIN", "PIN speichern", "Salvar PIN", "Сохранить PIN")
    val nameDesc = tr(lang, "默认使用本机机型名，便于对端区分", "預設使用本機機型名稱，便於對端區分", "Uses the device model name by default so peers can tell devices apart", "デフォルトでは端末の機種名を使用します", "기본적으로 기기 모델명을 사용합니다", "Usa el modelo del dispositivo por defecto", "Utilise le nom du modèle par défaut", "Verwendet standardmäßig den Modellnamen", "Usa o modelo do dispositivo por padrão", "По умолчанию используется название модели устройства")
    val saveBtn = tr(lang, "保存", "儲存", "Save", "保存", "저장", "Guardar", "Enregistrer", "Speichern", "Salvar", "Сохранить")

    // 「传输」分组：清空记录 + 自动清理
    val secTransfers = tr(lang, "传输", "傳輸", "Transfers", "転送", "전송", "Transferencias", "Transferts", "Übertragungen", "Transferências", "Передача")
    val rowClearTransfers = tr(lang, "清空传输记录", "清空傳輸記錄", "Clear transfer records", "転送履歴をクリア", "전송 기록 비우기", "Borrar registros de transferencia", "Effacer l'historique de transfert", "Übertragungen löschen", "Limpar registros de transferência", "Очистить записи передач")
    val rowAutoCleanup = tr(lang, "传输记录自动清理", "傳輸記錄自動清理", "Auto-clean transfers", "転送履歴の自動クリーンアップ", "전송 기록 자동 정리", "Limpieza automática de transferencias", "Nettoyage auto des transferts", "Übertragungen automatisch bereinigen", "Limpeza automática de transferências", "Автоочистка записей передач")
    val clearTransfersTitle = tr(lang, "清空所有传输记录", "清空所有傳輸記錄", "Clear all transfer records", "すべての転送履歴をクリア", "모든 전송 기록 비우기", "Borrar todos los registros de transferencia", "Effacer tout l'historique de transfert", "Alle Übertragungen löschen", "Limpar todos os registros de transferência", "Очистить все записи передач")
    val clearTransfersMsg = tr(lang, "此操作将删除全部记录，且无法恢复。", "此操作將刪除全部記錄，且無法恢復。", "This will delete all records and cannot be undone.", "この操作で全ての記録が削除され、元に戻せません。", "이 작업은 모든 기록을 삭제하며 되돌릴 수 없습니다.", "Esto borrará todos los registros y no se puede deshacer.", "Cela supprimera tous les enregistrements, sans retour possible.", "Dies löscht alle Einträge und ist nicht rückgängig zu machen.", "Isso excluirá todos os registros e não poderá ser desfeito.", "Это удалит все записи безвозвратно.")
    val btnConfirm = tr(lang, "确定", "確定", "OK", "OK", "확인", "Aceptar", "OK", "OK", "OK", "ОК")
    val retentionNone = tr(lang, "不自动清理（默认）", "不自動清理（預設）", "No auto-clean (default)", "自動クリーンアップなし（初期値）", "자동 정리 안 함(기본)", "Sin limpieza automática (predeterminado)", "Pas de nettoyage auto (par défaut)", "Keine Autobereinigung (Standard)", "Sem limpeza automática (padrão)", "Без автоочистки (по умолчанию)")
    val cleanupAgoDay = tr(lang, "清除{0}天前", "清除{0}天前", "Clear {0} days ago", "{0}日前をクリア", "{0}일 전 지우기", "Borrar hace {0} días", "Effacer il y a {0} jours", "Vor {0} Tagen löschen", "Apagar há {0} dias", "Удалять старше {0} дн.")
    val cleanupAgoMonth = tr(lang, "清除{0}个月前", "清除{0}個月前", "Clear {0} months ago", "{0}か月前をクリア", "{0}개월 전 지우기", "Borrar hace {0} meses", "Effacer il y a {0} mois", "Vor {0} Monaten löschen", "Apagar há {0} meses", "Удалять старше {0} мес.")
    val cleanupAgoYear = tr(lang, "清除{0}年前", "清除{0}年前", "Clear {0} years ago", "{0}年前をクリア", "{0}년 전 지우기", "Borrar hace {0} años", "Effacer il y a {0} ans", "Vor {0} Jahren löschen", "Apagar há {0} anos", "Удалять старше {0} лет")
    val clearedAllToast = tr(lang, "已清空传输记录", "已清空傳輸記錄", "Transfer records cleared", "転送履歴をクリアしました", "전송 기록을 비웠습니다", "Registros de transferencia borrados", "Historique de transfert effacé", "Übertragungen gelöscht", "Registros de transferência limpos", "Записи передач очищены")
    val rowClearThumb = tr(lang, "缩略图缓存", "縮略圖緩存", "Thumbnail cache", "サムネイルキャッシュ", "썸네일 캐시", "Miniaturas en caché", "Vignettes en cache", "Miniatur-Cache", "Miniaturas em cache", "Кэш миниатюр")
    val clearThumbTitle = tr(lang, "清除缩略图缓存", "清除縮略圖緩存", "Clear thumbnail cache", "サムネイルキャッシュをクリア", "썸네일 캐시 지우기", "Borrar caché de miniaturas", "Effacer les vignettes en cache", "Miniatur-Cache löschen", "Limpar cache de miniaturas", "Очистить кэш миниатюр")
    val clearThumbMsg = tr(lang, "缓存是缩略图的副本，清除后不影响源文件；但若源文件已被删除或移动，缩略图将无法再恢复。确定清除吗？", "緩存是縮略圖的副本，清除後不影響源檔案；但若源檔案已被刪除或移動，縮略圖將無法再恢復。確定清除嗎？", "The cache is a copy of the thumbnails and clearing it does not affect source files. However, if a source file was deleted or moved, its thumbnail cannot be restored. Clear anyway?", "キャッシュは縮略図のコピーで、消去しても元ファイルには影響しません。ただし元ファイルを削除・移動すると縮略図は復元できません。消去しますか？", "캐시는 썸네일 복사본이며 지워도 원본 파일에는 영향이 없습니다. 하지만 원본 파일이 삭제되거나 이동되면 썸네일은 복원할 수 없습니다. 계속 지울까요?", "La caché es una copia de las miniaturas y borrarla no afecta a los archivos originales. Sin embargo, si el archivo fue eliminado o movido, su miniatura no podrá restaurarse. ¿Continuar?", "Le cache est une copie des miniatures et sa suppression n'affecte pas les fichiers sources. Toutefois, si un fichier source a été supprimé ou déplacé, sa miniature ne pourra plus être restaurée. Continuer ?", "Der Cache ist eine Kopie der Miniaturen; das Löschen betrifft die Quelldateien nicht. Wurde eine Quelldatei gelöscht oder verschoben, lässt sich ihre Miniatur jedoch nicht wiederherstellen. Trotzdem löschen?", "O cache é uma cópia das miniaturas e limpá-lo não afeta os arquivos de origem. No entanto, se um arquivo de origem foi excluído ou movido, sua miniatura não poderá ser restaurada. Continuar?", "Кэш — это копия миниатюр, его очистка не затрагивает исходные файлы. Но если исходный файл удалён или перемещён, его миниатюра не восстановится. Очистить?")
    val clearThumbDone = tr(lang, "已清除缩略图缓存", "已清除縮略圖緩存", "Thumbnail cache cleared", "サムネイルキャッシュをクリアしました", "썸네일 캐시를 지웠습니다", "Caché de miniaturas borrada", "Vignettes en cache effacées", "Miniatur-Cache gelöscht", "Cache de miniaturas limpo", "Кэш миниатюр очищен")
    fun thumbSizeLabel(bytes: Long): String =
        if (bytes < 1024L * 1024L) String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
    fun retentionLabel(d: Int): String = when {
        d <= 0 -> retentionNone
        d == 90 -> java.text.MessageFormat.format(cleanupAgoMonth, 3)
        d == 180 -> java.text.MessageFormat.format(cleanupAgoMonth, 6)
        d == 365 -> java.text.MessageFormat.format(cleanupAgoYear, 1)
        else -> java.text.MessageFormat.format(cleanupAgoDay, d)
    }

    val subState = when {
        pickingDir -> 2; pickLanguage -> 3; showAbout -> 4; showPrivacy -> 5; showUpdate -> 6; showFeedback -> 7; else -> 1
    }
    // 是否处于二级子页（完整子页或弹窗）——上报给主界面，用于设置键的二段式行为
    val subActive = subState != 1 || editName || pickTheme || editPin || confirmClearTransfers || confirmClearThumb || pickRetention || showUpdate || showFeedback
    LaunchedEffect(subActive) { onSubChanged(subActive) }
    // 响应主界面「返回设置主界面」的外部请求（设置键二段式）：把当前子页/弹窗全部置回主列表
    LaunchedEffect(returnToRoot) {
        if (returnToRoot > 0) {
            editName = false; pickTheme = false; pickLanguage = false
            pickingDir = false; showAbout = false; editPin = false; showPrivacy = false
            confirmClearTransfers = false; pickRetention = false; showUpdate = false; showFeedback = false
            confirmClearThumb = false
        }
    }
    // 设置内二级菜单之间以横向滑块过渡（打开与关闭均有动画）
    AnimatedContent(
        targetState = subState,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(280)) { dir * it / 2 } + fadeIn(tween(200)))
                .togetherWith(slideOutHorizontally(tween(220)) { -dir * it / 2 } + fadeOut(tween(160)))
        }
    ) { s ->
        when (s) {
            2 -> DirPickerScreen(
                initialPath = receiveDir,
                onPick = { dir -> onChangeDir(dir); pickingDir = false },
                onBack = { pickingDir = false }
            )
            3 -> LanguageScreen(current = lang, onSetLanguage = onSetLanguage, onBack = { pickLanguage = false })
            4 -> AboutScreen(onBack = { showAbout = false })
            5 -> PrivacyScreen(onBack = { showPrivacy = false })
            6 -> UpdatePage(onBack = { showUpdate = false })
            7 -> FeedbackPage(onBack = { showFeedback = false })
            1 -> Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.rotate(180f))
            }
            Text(tiSettings, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        LazyColumn(
            state = settingsListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { SectionLabel(secGeneral) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    // 设备名称置顶（从原「网络」分类移入「通用」最顶端）
                    SettingsRow(rowDeviceName, currentName) { editName = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowTheme, themeName(themeMode, lang)) { pickTheme = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowLanguage, langName(lang)) { pickLanguage = true }
                }
            }

            item { SectionLabel(secReceive) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingsRow(rowSaveDir, receiveDir, maxValueLines = 2) { pickingDir = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleAutoSave, autoSave, onSetAutoSave)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleAutoSaveWhitelist, autoSaveWhitelist, onSetAutoSaveWhitelist)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleAutoAcceptText, autoAcceptText, onSetAutoAcceptText)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(togglePin, pinEnabled) { v ->
                        if (v) {
                            onSetPinEnabled(true)
                            pinInput = ""
                            editPin = true   // 开启即引导设置 PIN
                        } else {
                            onSetPinEnabled(false)
                        }
                    }
                    if (pinEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(rowPin, if (pinCode.isNotBlank()) textSet else textNotSet) { pinInput = ""; editPin = true }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleGallery, saveToGallery, onSetSaveToGallery)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleHistory, saveHistory, onSetSaveHistory)
                }
            }

            item { SectionLabel(secTransfers) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingsRow(rowClearTransfers, null) { confirmClearTransfers = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowClearThumb, thumbSizeLabel(thumbCacheBytes)) { confirmClearThumb = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowAutoCleanup, retentionLabel(retentionDays)) { pickRetention = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsToggle(toggleIntegrateImages, integrateImages, onSetIntegrateImages)
                }
            }

            item { SectionLabel(secOther) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingsRow(rowAbout, textView) { showAbout = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowPrivacy, textView) { showPrivacy = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowCheckUpdate, "v${BuildConfig.VERSION_NAME}", badge = hasUpdate) { showUpdate = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowFeedback, feedbackSubmitHint) { showFeedback = true }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
        }
    }

    // PIN 密码设置
    if (editPin) {
        Dialog(onDismissRequest = {
            // 关闭弹窗但未成功保存 PIN 时，自动关掉“启用 PIN 密码”
            if (pinCode.isBlank()) onSetPinEnabled(false)
            editPin = false
        }) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(pinTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(pinDesc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    TextField(value = pinInput, onValueChange = { pinInput = it.filter { ch -> ch.isDigit() }.take(6) },
                        singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text(pinPlaceholder, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onSetPinCode(pinInput); editPin = false },
                        enabled = pinInput.length >= 4, shape = BtnShape, modifier = Modifier.fillMaxWidth()) { Text(savePin) }
                }
            }
        }
    }

    // 设备名称编辑
    if (editName) {
        Dialog(onDismissRequest = { editName = false }) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(rowDeviceName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(nameDesc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    TextField(value = nameInput, onValueChange = { nameInput = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onRename(nameInput); editName = false },
                        enabled = nameInput.isNotBlank() && nameInput != currentName, shape = BtnShape,
                        modifier = Modifier.fillMaxWidth()) { Text(saveBtn) }
                }
            }
        }
    }

    // 主题选择（单选）
    if (pickTheme) {
        Dialog(onDismissRequest = { pickTheme = false }) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(rowTheme, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    val themeOptions = listOf(
                        0 to tr(lang, "跟随系统", "跟隨系統", "System", "システム", "시스템", "Sistema", "Système", "System", "Sistema", "Системная"),
                        1 to tr(lang, "浅色", "淺色", "Light", "ライト", "라이트", "Claro", "Clair", "Hell", "Claro", "Светлая"),
                        2 to tr(lang, "深色", "深色", "Dark", "ダーク", "다크", "Oscuro", "Sombre", "Dunkel", "Escuro", "Тёмная")
                    )
                    themeOptions.forEach { (code, label) ->
                        Row(Modifier.fillMaxWidth().clickable { onSetTheme(code); pickTheme = false }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            if (themeMode == code) {
                                Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    // 关于（已改为独立二级菜单，见 AboutScreen）

    // 清空传输记录确认
    if (confirmClearTransfers) {
        AlertDialog(
            onDismissRequest = { confirmClearTransfers = false },
            title = { Text(clearTransfersTitle) },
            text = { Text(clearTransfersMsg) },
            confirmButton = {
                TextButton(onClick = {
                    onClearTransfers()
                    confirmClearTransfers = false
                    clearThumbScope.launch {
                        com.orangeway.go.core.ThumbCache.clearAll(context)
                        thumbCacheBytes = com.orangeway.go.core.ThumbCache.totalSizeBytes(context)
                    }
                    android.widget.Toast.makeText(context, clearedAllToast, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(btnConfirm, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTransfers = false }) { Text(btnCancel) }
            }
        )
    }

    // 清除缩略图缓存确认
    if (confirmClearThumb) {
        AlertDialog(
            onDismissRequest = { confirmClearThumb = false },
            title = { Text(clearThumbTitle) },
            text = { Text(clearThumbMsg) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearThumb = false
                    clearThumbScope.launch {
                        com.orangeway.go.core.ThumbCache.clearAll(context)
                        thumbCacheBytes = com.orangeway.go.core.ThumbCache.totalSizeBytes(context)
                    }
                    android.widget.Toast.makeText(context, clearThumbDone, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(btnConfirm, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearThumb = false }) { Text(btnCancel) }
            }
        )
    }

    // 自动清理：弹窗单选 +「取消/确定」，点确定才生效
    if (pickRetention) {
        // 进入弹窗时以当前设置为默认选中；取消不改动
        var choice by remember { mutableStateOf(retentionDays) }
        Dialog(onDismissRequest = { pickRetention = false }) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(rowAutoCleanup, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    val opts = listOf(
                        0 to retentionNone, 1 to retentionLabel(1), 7 to retentionLabel(7),
                        30 to retentionLabel(30), 90 to retentionLabel(90),
                        180 to retentionLabel(180), 365 to retentionLabel(365)
                    )
                    opts.forEach { (days, label) ->
                        Row(Modifier.fillMaxWidth().clickable { choice = days }
                            .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            BubbleRadio(selected = choice == days)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { pickRetention = false }) { Text(btnCancel) }
                        Spacer(Modifier.width(8.dp))
                        // 点确定才应用并立即生效
                        TextButton(onClick = {
                            onSetRetentionDays(choice)
                            android.widget.Toast.makeText(context, retentionLabel(choice), android.widget.Toast.LENGTH_SHORT).show()
                            pickRetention = false
                        }) { Text(btnConfirm, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
}

// ===== 单选圆点（自动清理选项）：选中=橙色实心，未选中=空心描边 =====
@Composable
private fun BubbleRadio(selected: Boolean) {
    Box(
        Modifier.size(18.dp)
            .border(1.5.dp, if (selected) Color(0xFFFF8A00)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(Modifier.size(10.dp).background(Color(0xFFFF8A00), CircleShape))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun SettingsRow(
    label: String,
    value: String?,
    maxValueLines: Int = 2,
    badge: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            // 红点：有更新时提示（如「检查更新」行）
            if (badge) {
                Box(Modifier.size(8.dp).background(Color(0xFFE53935), CircleShape))
                Spacer(Modifier.width(8.dp))
            }
            // 仅可点击行显示">"指示箭头；纯展示行（如关于页的版本/类型）不显示
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp))
            }
        }
        value?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                maxLines = maxValueLines, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                uncheckedTrackColor = MaterialTheme.colorScheme.outline
            ))
    }
}

private fun themeName(mode: Int, lang: String): String = when (mode) {
    1 -> tr(lang, "浅色", "淺色", "Light", "ライト", "라이트", "Claro", "Clair", "Hell", "Claro", "Светлая")
    2 -> tr(lang, "深色", "深色", "Dark", "ダーク", "다크", "Oscuro", "Sombre", "Dunkel", "Escuro", "Тёмная")
    else -> tr(lang, "跟随系统", "跟隨系統", "System", "システム", "시스템", "Sistema", "Système", "System", "Sistema", "Системная")
}

/** 支持的语言。system 表示跟随系统；其余按 ISO 语言代码国际标准顺序排列。 */
private val supportedLanguages = listOf(
    "system" to "跟随系统",
    "de" to "Deutsch",
    "en" to "English",
    "es" to "Español",
    "fr" to "Français",
    "ja" to "日本語",
    "ko" to "한국어",
    "pt" to "Português",
    "ru" to "Русский",
    "zh" to "简体中文",
    "zhTW" to "繁體中文"
)

/** 系统语言映射到支持的语言；若不支持则回退英语。 */
private fun systemLangCode(): String {
    val l = java.util.Locale.getDefault().language
    return if (l in setOf("zh", "en", "ja", "ko", "es", "fr", "de", "pt", "ru")) l else "en"
}

private fun langName(code: String): String = supportedLanguages.firstOrNull { it.first == code }?.second ?: code

/** 多语言文本：按当前语言取对应译文；跟随系统时按其系统语言，未命中回退中文，找不到则回退中文。 */
private fun tr(
    lang: String, zh: String, zhTW: String, en: String, ja: String, ko: String,
    es: String, fr: String, de: String, pt: String, ru: String
): String {
    val l = if (lang == "system") systemLangCode() else lang
    return when (l) {
        "zhTW" -> zhTW; "en" -> en; "ja" -> ja; "ko" -> ko; "es" -> es
        "fr" -> fr; "de" -> de; "pt" -> pt; "ru" -> ru; else -> zh
    }
}

/** 回顶悬浮键：列表滚动后淡入，点击平滑回到顶部。 */
@Composable
private fun ScrollToTopFab(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 3 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { it / 3 }
    ) {
        Box(
            Modifier.size(46.dp).shadow(6.dp, CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(R.drawable.ic_scroll_top), "回顶",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
    }
}

/** 项目列表外层装饰器：右下角放回顶键。 */
@Composable
private fun ListTopDecorator(
    visible: Boolean,
    onScrollToTop: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        content()
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp)) {
            ScrollToTopFab(visible, onScrollToTop)
        }
    }
}

/** 关于：独立二级菜单页。 */
@Composable
private fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val lang by OgoLang.code.collectAsState()
    val tiAbout = tr(lang, "关于 OrangeGO", "關於 OrangeGO", "About OrangeGO", "OrangeGO について", "OrangeGO 정보", "Acerca de OrangeGO", "À propos d'OrangeGO", "Über OrangeGO", "Sobre OrangeGO", "О OrangeGO")
    val subtitle = tr(lang, "局域网文件互传", "區域網路檔案互傳", "LAN file transfer", "LANファイル転送", "LAN 파일 전송", "Transferencia de archivos en LAN", "Transfert de fichiers en LAN", "LAN-Dateiübertragung", "Transferência de arquivos em LAN", "Локальная передача файлов")
    val rowVersion = tr(lang, "版本", "版本", "Version", "バージョン", "버전", "Versión", "Version", "Version", "Versão", "Версия")
    val authorLabel = tr(lang, "作者", "作者", "Author", "著者", "작성자", "Autor", "Auteur", "Autor", "Autor", "Автор")
    val appNameLabel = tr(lang, "应用名称", "應用名稱", "App name", "アプリ名", "앱 이름", "Nombre", "Nom de l'app", "App-Name", "Nome", "Название")
    val pkgNameLabel = tr(lang, "应用包名", "應用包名", "Package name", "パッケージ名", "패키지 이름", "Nombre del paquete", "Nom du paquet", "Paketname", "Nome do pacote", "Имя пакета")
    val systemVerLabel = tr(lang, "系统版本", "系統版本", "System version", "システムバージョン", "시스템 버전", "Versión del sistema", "Version du système", "Systemversion", "Versão do sistema", "Версия системы")
    val deviceModelLabel = tr(lang, "设备型号", "裝置型號", "Device model", "端末モデル", "기기 모델", "Modelo de dispositivo", "Modèle d'appareil", "Gerätemodell", "Modelo do aparelho", "Модель устройства")
    val context = LocalContext.current
        val pkgInfo = remember {
            try { context.packageManager.getPackageInfo(context.packageName, 0) } catch (e: Exception) { null }
        }
        val ver = pkgInfo?.versionName ?: BuildConfig.VERSION_NAME
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.rotate(180f))
                }
                Text(tiAbout, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(48.dp))
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                AsyncImage(model = R.raw.og_logo_orange, contentDescription = "OrangeGO",
                    modifier = Modifier.size(88.dp), contentScale = ContentScale.Fit)
                Spacer(Modifier.height(16.dp))
                Text("OrangeGO", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("$rowVersion v$ver", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(32.dp))
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        AppInfoRow(authorLabel, "Orange Way")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(appNameLabel, "OrangeGO")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(pkgNameLabel, context.packageName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(systemVerLabel, "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                        AppInfoRow(deviceModelLabel, "${Build.MANUFACTURER} ${Build.MODEL}")
                    }
                }
                Spacer(Modifier.height(32.dp))
                Text("© 2026 · Orange Way · OrangeGo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
}

/** 关于页信息行：左侧标签 + 右侧内容 */
@Composable
private fun AppInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
    }
}

// ===== 检查更新：复刻 HereIAm 更新页（自动检查、下载、安装 + 三端资产展示） =====
private data class OgAsset(val name: String, val url: String) {
    val isApk: Boolean get() = name.substringAfterLast('.', "").equals("apk", true)
}
/** 可选的下载源（三个镜像仓库，同一安装包）。 */
private enum class OgSource(
    val id: String,
    val label: String,
    @androidx.annotation.DrawableRes val logoRes: Int,
    val checkUrl: String,
    @androidx.annotation.DrawableRes val darkLogoRes: Int = logoRes
) {
    GITCODE("gitcode", "GitCode", R.drawable.src_gitcode, "https://api.gitcode.com/api/v5/repos/OrangeWay/OrangeGO/releases/latest"),
    GITEE("gitee", "Gitee", R.drawable.src_gitee, "https://gitee.com/api/v5/repos/orange-way/OrangeGO/releases/latest"),
    GITHUB("github", "GitHub", R.drawable.src_github, "https://api.github.com/repos/orange-way/OrangeGO/releases/latest", R.drawable.src_github_white);
    companion object {
        // 默认选第一个下载源 GitCode
        fun fromId(id: String?): OgSource = entries.firstOrNull { it.id == id } ?: GITCODE
    }
}
private data class OgRelease(val tag: String, val versionName: String, val changelog: String, val assets: List<OgAsset>)

private sealed class OgUState {
    object Idle : OgUState()
    object Checking : OgUState()
    data class Found(val r: OgRelease) : OgUState()
    object NoUpdate : OgUState()
    data class Error(val msg: String) : OgUState()
    data class Downloading(val progress: Int) : OgUState()
    data class Downloaded(val r: OgRelease) : OgUState()
    data class DownloadError(val msg: String) : OgUState()
}

/** 拉取并解析 GitHub release（解析私有仓库或公开仓库的 latest）。 */
private fun parseOgRelease(body: String): OgRelease? = runCatching {
    val j = org.json.JSONObject(body)
    val tag = j.optString("tag_name")
    val version = tag.removePrefix("v")
    val changelog = j.optString("body", "").replace("\r\n", "\n").trim()
    val assets = j.optJSONArray("assets")?.let { arr ->
        (0 until arr.length()).mapNotNull { i ->
            val a = arr.optJSONObject(i)
            val n = a?.optString("name") ?: return@mapNotNull null
            val u = a?.optString("browser_download_url") ?: return@mapNotNull null
            OgAsset(n, u)
        }
    } ?: emptyList()
    OgRelease(tag, version, changelog, assets)
}.getOrNull()

private fun makeOgUpdater(context: Context, scope: kotlinx.coroutines.CoroutineScope): OgUpdaterReporter {
    val holder = OgUpdaterHolder()
    holder.start(context, scope)
    return holder
}
@Composable
private fun rememberOgUpdater(): OgUpdaterReporter = run {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    remember { makeOgUpdater(context, scope) }
}

private sealed class OgUpdaterReporter {
    // 用 SnapshotState 暴露，顶端设置图标 / 设置「检查更新」行的红点据此自动重绘
    var state: OgUState by mutableStateOf(OgUState.Idle)
        protected set
    abstract fun check(source: OgSource)
    abstract fun downloadApk()
    abstract fun install(vararg asset: String)
    abstract fun openAsset(name: String, url: String)
}

private class OgUpdaterHolder : OgUpdaterReporter() {
    private lateinit var context: Context
    private lateinit var scope: kotlinx.coroutines.CoroutineScope
    private var release: OgRelease? = null
    private var apkFile: java.io.File? = null

    fun start(context: Context, scope: kotlinx.coroutines.CoroutineScope) { this.context = context; this.scope = scope }

    /** 从 Worker /checksum 取指定版本+APK 的权威 SHA256 校验值；取不到返回 null */
    private fun fetchChecksum(fileName: String, tag: String): String? = runCatching {
        val base = com.orangeway.go.FeedbackConfig.checksumUrl
        val query = "file=${java.net.URLEncoder.encode(fileName, "UTF-8")}&v=${java.net.URLEncoder.encode(tag, "UTF-8")}"
        val con = java.net.URL("$base?$query").openConnection() as java.net.HttpURLConnection
        con.setRequestProperty("User-Agent", "OrangeGO-Android")
        con.connectTimeout = 8000; con.readTimeout = 8000
        val body = if (con.responseCode in 200..299) con.inputStream?.bufferedReader()?.readText().orEmpty() else ""
        runCatching { con.disconnect() }
        org.json.JSONObject(body).optString("sha256").takeIf { it.length == 64 && it.matches(Regex("[0-9a-fA-F]{64}")) }
    }.getOrNull()

    override fun check(source: OgSource) {
        if (state is OgUState.Checking || state is OgUState.Downloading) return
        state = OgUState.Checking
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            // GitHub 直连可能被墙，追加一个公开镜像兜底；GitCode/Gitee 用源自带接口
            val urls = if (source == OgSource.GITHUB)
                listOf(source.checkUrl, "https://gh.llkk.cc/https://api.github.com/repos/orange-way/OrangeGO/releases/latest")
            else listOf(source.checkUrl)
            var parsed: OgRelease? = null
            for (u in urls) {
                var con: java.net.HttpURLConnection? = null
                try {
                    con = java.net.URL(u).openConnection() as java.net.HttpURLConnection
                    con.setRequestProperty("Accept", "application/vnd.github+json")
                    con.setRequestProperty("User-Agent", "OrangeGO-Android")
                    con.connectTimeout = 12000; con.readTimeout = 12000
                    if (con.responseCode in 200..299) {
                        val body = con.inputStream?.bufferedReader()?.readText().orEmpty()
                        parsed = parseOgRelease(body)
                    }
                } catch (_: Exception) { } finally { runCatching { con?.disconnect() } }
                if (parsed != null) break
            }
            if (parsed == null) { state = OgUState.Error("T"); return@launch }
            release = parsed
            val cur = BuildConfig.VERSION_NAME
            val newer = parsed!!.versionName.isNotEmpty() && parsed!!.versionName != cur
            state = if (newer) OgUState.Found(parsed!!) else OgUState.NoUpdate
        }
    }

    override fun downloadApk() {
        val r = release ?: return
        val apk = r.assets.firstOrNull { it.isApk } ?: run { state = OgUState.Error("T"); return }
        val target = java.io.File(context.cacheDir, "orangego_update.apk")
        state = OgUState.Downloading(0)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val con = java.net.URL(apk.url).openConnection() as java.net.HttpURLConnection
                con.setRequestProperty("User-Agent", "OrangeGO-Android")
                con.connectTimeout = 15000; con.readTimeout = 30000
                val total = con.contentLengthLong
                con.inputStream.use { ins ->
                    java.io.FileOutputStream(target).use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int; var done = 0L
                        while (ins.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read); done += read
                            val p = if (total > 0) (done * 100 / total).toInt() else (state as? OgUState.Downloading)?.progress ?: 0
                            state = OgUState.Downloading(p.coerceIn(0, 100))
                        }
                    }
                }
                con.disconnect()
                // SHA256 校验：向 Worker /checksum 取权威校验值（Worker 维护 APK_SHA256 表，见 deploy/worker.js）。
                // 校验值缺失或本地计算不一致即拒绝安装，保护用户安全。
                val expect = fetchChecksum(apk.name, r.tag)
                if (expect.isNullOrBlank() || !expect.equals(computeSha256(target), true)) {
                    runCatching { target.delete() }
                    state = OgUState.DownloadError("T"); return@launch
                }
                apkFile = target
                state = OgUState.Downloaded(r)
            } catch (e: Exception) {
                state = OgUState.DownloadError(e.message ?: "T")
            }
        }
    }

    override fun install(vararg asset: String) {
        val f = apkFile
        if (f == null || !f.exists() || f.length() < 1) {
            // 文件缺失/损坏：给出明确失败而非静默（需重新下载）
            state = OgUState.DownloadError("T")
            return
        }
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", f)
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: SecurityException) {
            // 未允许「安装未知应用」：引导去系统设置开启，保持已下载状态让用户开启后重试
            openInstallSourceSettings()
        } catch (e: Exception) {
            state = OgUState.DownloadError(e.message ?: "T")
        }
    }

    /** 跳转系统设置开启「允许安装未知应用」；个别设备不支持该 Intent 时退回应用详情页。 */
    private fun openInstallSourceSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            android.net.Uri.parse("package:${context.packageName}")
        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:${context.packageName}")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    override fun openAsset(name: String, url: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}

private fun computeSha256(f: java.io.File): String? = runCatching {
    val md = java.security.MessageDigest.getInstance("SHA-256")
    f.inputStream().use { ins ->
        val buf = ByteArray(64 * 1024); var read: Int
        while (ins.read(buf).also { read = it } != -1) md.update(buf, 0, read)
    }
    md.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

/** 检查更新页：仿 HereIAm，自动检查、展示三端资产、安卓侧可直接下载安装。 */
@Composable
private fun UpdatePage(onBack: () -> Unit) {
    BackHandler { onBack() }
    val lang by OgoLang.code.collectAsState()
    val context = LocalContext.current
    val updater = rememberOgUpdater()
    val versionName = remember { BuildConfig.VERSION_NAME }
    val onBackLaunch = { onBack() }
    val state = updater.state
    // 与全局一致的取语言：跟随系统时按系统语言判定
    val effLang = if (lang == "system") systemLangCode() else lang
    fun L(zh: String, en: String): String = if (effLang == "zh" || effLang == "zhTW") zh else en

    // 下载源选择（仿 HereIAm：GitCode / Gitee / GitHub 三个镜像仓库）
    val prefs = context.getSharedPreferences("ogo_prefs", Context.MODE_PRIVATE)
    var source by rememberSaveable { mutableStateOf(OgSource.fromId(prefs.getString("download_source", null)).id) }
    val curSource = OgSource.fromId(source)
    var sourceMenu by remember { mutableStateOf(false) }
    val isDark = appIsDark()
    fun switchSource(s: OgSource) {
        prefs.edit().putString("download_source", s.id).apply()
        source = s.id
        sourceMenu = false
    }
    LaunchedEffect(curSource.id) { updater.check(curSource) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackLaunch) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(L("检查更新", "Check for updates"), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp, modifier = Modifier.size(88.dp)) {
                AsyncImage(model = R.raw.og_logo_orange, contentDescription = "OrangeGO",
                    modifier = Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit)
            }
            Spacer(Modifier.height(16.dp))
            Text("OrangeGO", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(L("当前版本 v$versionName", "Current version v$versionName"),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))

            // 下载源/平台选择（仿 HereIAm：显示框 + 下拉弹层，选中项打勾）
            var displayWidth by remember { mutableStateOf<Dp?>(null) }
            val density = LocalDensity.current
            // 托盘比显示框宽：固定舒适宽度，避免源名被省略号截断
            val trayWidth = maxOf((displayWidth ?: 200.dp) + 48.dp, 232.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                Box {
                    Surface(onClick = { sourceMenu = true },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 2.dp,
                        modifier = Modifier.height(40.dp).onSizeChanged { displayWidth = with(density) { it.width.toDp() } }) {
                        Row(Modifier.padding(start = 8.dp, end = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            painterResource(if (isDark) curSource.darkLogoRes else curSource.logoRes).let { logo ->
                                Image(painter = logo, contentDescription = null, modifier = Modifier.size(26.dp))
                            }
                            Text(curSource.label, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Filled.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = sourceMenu,
                        onDismissRequest = { sourceMenu = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.width(trayWidth),
                        offset = DpOffset(x = ((displayWidth ?: trayWidth) - trayWidth) / 2f, y = 0.dp)
                    ) {
                        OgSource.entries.forEach { s ->
                            Row(
                                Modifier.fillMaxWidth().clickable { switchSource(s) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                painterResource(if (isDark) s.darkLogoRes else s.logoRes).let { logo ->
                                    Image(painter = logo, contentDescription = null, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(s.label,
                                    modifier = Modifier.weight(1f),
                                    fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    fontWeight = if (s == curSource) FontWeight.Bold else FontWeight.Normal)
                                if (s == curSource) {
                                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759),
                                        modifier = Modifier.padding(start = 8.dp).size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        is OgUState.Idle -> { Spacer(Modifier.height(20.dp)) }
                        is OgUState.Checking -> {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(L("正在检查更新…", "Checking for updates…"), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is OgUState.NoUpdate -> {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(L("已是最新版本", "Up to date"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("v$versionName", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text(L("可从设置返回", "You are on the latest version"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is OgUState.Error -> {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(L("检查失败，请稍后重试", "Check failed, try again later"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(onClick = { updater.check(curSource) }) { Text(L("重试", "Retry")) }
                        }
                        is OgUState.Found -> {
                            val r = state.r
                            Text(L("发现新版本", "New version available"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("v${r.versionName}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            Text(L("当前 v$versionName", "Current v$versionName"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (r.changelog.isNotBlank()) {
                                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                Spacer(Modifier.height(12.dp))
                                Text(L("更新日志", "Changelog"), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(r.changelog, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp)
                            }
                            Spacer(Modifier.height(20.dp))
                            // 三个下载源都是同一安装包：主操作 = 应用内下载并安装 APK，其余资产列出直下
                            val apk = r.assets.firstOrNull { it.isApk }
                            if (apk != null) {
                                Button(onClick = { updater.downloadApk() },
                                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                    Text(L("立即下载并安装", "Download & install"), fontSize = 16.sp)
                                }
                            } else if (r.assets.isEmpty()) {
                                OutlinedButton(onClick = {
                                    updater.openAsset("release", "https://github.com/orange-way/OrangeGO/releases/tag/${r.tag}")
                                }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                    Text(L("打开发布页", "Open release"), fontSize = 15.sp)
                                }
                            }
                            if (r.assets.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 16.dp))
                                r.assets.forEach { a ->
                                    OutlinedButton(onClick = { updater.openAsset(a.name, a.url) },
                                        modifier = Modifier.fillMaxWidth().height(42.dp), shape = RoundedCornerShape(12.dp)) {
                                        Text(a.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            TextButton(onClick = onBackLaunch) { Text(L("稍后", "Later")) }
                        }
                        is OgUState.Downloading -> {
                            CircularProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp)
                            Spacer(Modifier.height(12.dp))
                            Text(L("正在下载…", "Downloading…"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("${state.progress}%", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        is OgUState.Downloaded -> {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(L("下载完成", "Download complete"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(L("点击下方按钮安装（需允许安装未知应用）", "Tap below to install"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { updater.install() },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Text(L("安装", "Install"), fontSize = 16.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onBackLaunch) { Text(L("稍后", "Later")) }
                        }
                        is OgUState.DownloadError -> {
                            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(L("下载失败", "Download failed"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { updater.downloadApk() },
                                modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                                Text(L("重试", "Retry"), fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ===== 问题反馈（复刻 HereIAm：标题+内容 → hCaptcha 人机验证 → 提交） =====
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedbackPage(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val lang by OgoLang.code.collectAsState()
    val effLang = if (lang == "system") systemLangCode() else lang
    fun L(zh: String, en: String): String = if (effLang == "zh" || effLang == "zhTW") zh else en
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<FeedbackOutcome?>(null) }
    val showCaptcha = remember { mutableStateOf(false) }
    val pendingTitle = remember { mutableStateOf("") }
    val pendingContent = remember { mutableStateOf("") }

    // 自动附带设备信息，便于排查问题（紧贴描述下一行）
    val deviceInfo = listOf(
        "${android.os.Build.BRAND} ${android.os.Build.MODEL}（${android.os.Build.MANUFACTURER}）",
        "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
        "v${BuildConfig.VERSION_NAME} (versionCode ${BuildConfig.VERSION_CODE})",
        android.os.Build.SUPPORTED_ABIS.joinToString(", "),
        "${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}",
        if (appIsDark()) L("深色", "Dark") else L("浅色", "Light")
    ).joinToString("\n")

    // 真正提交（hCaptcha 验证通过后携带 token）
    val doSubmit: (String) -> Unit = { token ->
        val titleText = pendingTitle.value
        val bodyText = pendingContent.value
        submitting = true
        scope.launch {
            val result = FeedbackManager.submit(titleText.trim(), bodyText.trim(), token)
            submitting = false
            outcome = result
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(L("问题反馈", "Report an issue"), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        L("遇到问题或想提建议？写下具体现象和操作步骤，方便作者更快定位处理。", "Encountering an issue or want to suggest something? Describe what happened and the steps you took, so we can locate and fix it faster."),
                        fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(L("问题标题", "Issue title")) },
                        placeholder = { Text(L("一句话概括问题", "Summarize the issue in one line")) },
                        singleLine = true, shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = content, onValueChange = { content = it },
                        modifier = Modifier.fillMaxWidth().height(200.dp),
                        label = { Text(L("问题描述", "Describe the issue")) },
                        placeholder = { Text(L("详细描述复现步骤/具体现象", "Describe in detail how to reproduce")) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (submitting) return@Button
                            if (title.isBlank() || content.isBlank()) {
                                android.widget.Toast.makeText(context, L("请填写标题和内容", "Please fill in title and content"), android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            pendingTitle.value = title.trim()
                            pendingContent.value = content.trim() + "\n---\n" + deviceInfo
                            showCaptcha.value = true
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        if (submitting) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.surface)
                                Spacer(Modifier.width(10.dp))
                                Text(L("提交中…", "Submitting…"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text(L("提交反馈", "Submit"), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (!submitting) {
                        TextButton(onClick = { title = ""; content = "" },
                            modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text(L("清空输入", "Clear"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // 提交结果窗口
    outcome?.let { o ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            title = {
                Text(
                    if (o.anySuccess) L("已发送", "Sent") else L("发送失败", "Send failed"),
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (o.anySuccess) L("感谢你的反馈，我们会尽快处理。", "Thanks for your feedback, we will handle it soon.")
                    else L("提交失败，请稍后重试。", "Failed to submit, please retry later."),
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    outcome = null
                    if (o.anySuccess) onBack()
                }) { Text(L("确定", "OK")) }
            }
        )
    }

    // 人机验证弹窗
    if (showCaptcha.value) {
        FeedbackCaptchaDialog(
            onToken = { token -> showCaptcha.value = false; doSubmit(token) },
            onDismiss = { showCaptcha.value = false }
        )
    }
}

/** 人机验证弹窗：不调暗屏幕，内嵌 hCaptcha checkbox，验证成功回传 token 后提交。 */
@Composable
private fun FeedbackCaptchaDialog(
    onToken: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val dark = appIsDark()
    val lang by OgoLang.code.collectAsState()
    val effLang = if (lang == "system") systemLangCode() else lang
    fun L(zh: String, en: String): String = if (effLang == "zh" || effLang == "zhTW") zh else en
    var done by remember { mutableStateOf(false) }
    // 是否已触发图形挑战（Opened 事件）：是则把弹窗加高以完整展示挑战
    var challengeOpen by remember { mutableStateOf(false) }
    val config = remember(dark) {
        com.hcaptcha.sdk.HCaptchaConfig.builder()
            .siteKey(com.orangeway.go.FeedbackConfig.hcaptchaSiteKey)
            .theme(if (dark) HCaptchaTheme.DARK else HCaptchaTheme.LIGHT)
            .size(HCaptchaSize.NORMAL)
            // EMBEDDED：全部渲染在自建弹窗内（无独立遮罩）；容器居中且不超出屏幕，避免右缘裁切
            .renderMode(HCaptchaRenderMode.EMBEDDED)
            .build()
    }
    Dialog(
        onDismissRequest = { if (!done) { done = true; onDismiss() } },
        // 用平台默认宽度（不超屏幕），配合下方固定宽度容器居中，避免内嵌组件横向溢出需滑动
        properties = DialogProperties()
    ) {
        // 不调暗屏幕：清除对话框窗口默认的半透明黑色变暗背景
        val dialogView = LocalView.current
        SideEffect {
            (dialogView.parent as? DialogWindowProvider)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, if (dark) Color(0xFF3A3E4A) else Color(0xFFDFE2EA)),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                Modifier.padding(start = 0.dp, end = 0.dp, top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(L("人机验证", "Human verification"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(L("请完成下方验证后才能提交", "Complete the verification below to submit"),
                    fontSize = 13.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 0.dp, bottom = 4.dp))
                // checkbox 时用 150dp 高；触发图形挑战后加高至 420dp，完整容纳挑战且不裁切
                Box(Modifier.fillMaxWidth().height(if (challengeOpen) 420.dp else 150.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.graphicsLayer {
                            val s = 0.86f
                            scaleX = s; scaleY = s
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                    ) {
                        HCaptchaCompose(config = config) { result ->
                        when (result) {
                            is HCaptchaResponse.Success -> {
                                if (!done && result.token.isNotEmpty()) { done = true; onToken(result.token) }
                            }
                            is HCaptchaResponse.Failure -> {
                                if (!done) {
                                    done = true
                                    android.widget.Toast.makeText(context, L("人机验证失败，请重试", "Verification failed, retry"), android.widget.Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            }
                            is HCaptchaResponse.Event -> {
                                // 图形挑战弹出时加高弹窗以完整展示
                                if (result.event == HCaptchaEvent.Opened) challengeOpen = true
                            }
                        }
                    }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { if (!done) { done = true; onDismiss() } }) { Text(L("取消", "Cancel")) }
                }
            }
        }
    }
}

/** 语言：独立二级菜单页（按国际标准顺序）。 */
@Composable
private fun LanguageScreen(current: String, onSetLanguage: (String) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val lang by OgoLang.code.collectAsState()
    val tiLanguage = tr(lang, "语言", "語言", "Language", "言語", "언어", "Idioma", "Langue", "Sprache", "Idioma", "Язык")
    val systemLabel = tr(lang, "跟随系统", "跟隨系統", "System", "システム", "시스템", "Sistema", "Système", "System", "Sistema", "Системная")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.rotate(180f))
            }
            Text(tiLanguage, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listItems(supportedLanguages, key = { it.first }) { (code, name) ->
                // system 项需随界面语言切换
                val label = if (code == "system") systemLabel else name
                Card(
                    onClick = { onSetLanguage(code); onBack() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (code == current) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        if (code == current) {
                            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/** 隐私政策：独立二级菜单页。 */
@Composable
private fun PrivacyScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.rotate(180f))
            }
            Text("隐私政策", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text("OrangeGO 隐私政策", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            privatePolicySections.forEach { section ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(section.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(section.body, color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp, lineHeight = 22.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private data class PrivacySection(val title: String, val body: String)

private val privatePolicySections = listOf(
    PrivacySection(
        "一、信息收集",
        "OrangeGO 是一款局域网点对点文件互传工具。为完成设备发现与传输，应用会在局域网内广播并读取附近设备的基本信息（设备名、IP 地址、端口），此类信息仅用于当前网络内的配对与传输，不上传至任何远程服务器。"
    ),
    PrivacySection(
        "二、数据存储",
        "应用会在本机保存少量必要设置（设备别名、保存目录、主题、接收偏好、PIN 密码）。所有数据仅存储在你自己的设备本地，不会同步或上传到云端。"
    ),
    PrivacySection(
        "三、文件传输",
        "文件与文字仅通过局域网在两端设备之间直接传输，内容不经由任何第三方服务器中转。请仅在可信网络与可信设备之间使用。"
    ),
    PrivacySection(
        "四、权限说明",
        "应用会申请网络、发现设备、媒体读取、以及可能的“所有文件访问”权限，用于浏览、选择与接收文件。你可随时在系统设置中关闭相应权限。"
    ),
    PrivacySection(
        "五、PIN 密码",
        "若你开启“启用 PIN 密码”，PIN 仅保存在本机并用于接收文件时的本地校验，不会传输给发送方。"
    ),
    PrivacySection(
        "六、第三方",
        "本应用不使用第三方统计、广告或数据追踪服务，也不会将你的设备信息用于任何商业化用途。"
    ),
    PrivacySection(
        "七、联系我们",
        "如对本隐私政策有任何疑问，请通过项目开源仓库与我们联系。我们会根据法律法规及产品迭代不断更新本政策。"
    )
)

/** 内置保存目录选择器：逐级浏览文件夹，点“选择此文件夹”确认。 */
@Composable
private fun DirPickerScreen(initialPath: String, onPick: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lang by OgoLang.code.collectAsState()
    val permTitle = tr(lang, "需要「所有文件访问」权限", "需要「所有檔案存取」權限", "Full file access required", "「すべてのファイルへのアクセス」権限が必要です", "모든 파일 액세스 권한 필요", "Se requiere acceso a todos los archivos", "Accès à tous les fichiers requis", "Voller Dateizugriff erforderlich", "Requer acesso a todos os arquivos", "Требуется полный доступ к файлам")
    val permSubDir = tr(lang, "用于浏览并选择保存目录", "用於瀏覽並選擇儲存目錄", "To browse and select the save folder", "保存先を閲覧・選択するため", "저장 폴더를 탐색하고 선택하기 위함", "Para explorar y seleccionar la carpeta de guardado", "Pour parcourir et sélectionner le dossier de sauvegarde", "Zum Durchsuchen und Auswählen des Speicherordners", "Para navegar e selecionar a pasta de salvamento", "Для просмотра и выбора папки сохранения")
    val btnEnable = tr(lang, "前往开启", "前往開啟", "Grant", "許可する", "권한 부여", "Conceder", "Autoriser", "Erteilen", "Conceder", "Разрешить")
    val tiPickDir = tr(lang, "选择保存目录", "選擇儲存目錄", "Choose save folder", "保存先を選択", "저장 폴더 선택", "Elegir carpeta de guardado", "Choisir le dossier de sauvegarde", "Speicherordner wählen", "Escolher pasta de salvamento", "Выбрать папку сохранения")
    val btnPickThis = tr(lang, "选择此文件夹", "選擇此資料夾", "Choose this folder", "このフォルダを選択", "이 폴더 선택", "Elegir esta carpeta", "Choisir ce dossier", "Diesen Ordner wählen", "Escolher esta pasta", "Выбрать эту папку")
    val emptyDirTitle = tr(lang, "此目录无子文件夹", "此目錄無子資料夾", "No sub-folders", "サブフォルダがありません", "하위 폴더 없음", "Sin subcarpetas", "Aucun sous-dossier", "Keine Unterordner", "Sem subpastas", "Нет вложенных папок")
    val emptyDirSub = tr(lang, "可直接点右上角「选择此文件夹」", "可直接點右上角「選擇此資料夾」", "You can tap 'Choose this folder' in the top right", "右上の「このフォルダを選択」をタップできます", "오른쪽 위 '이 폴더 선택'을 탭하세요", "Puedes tocar 'Elegir esta carpeta' en la esquina superior derecha", "Vous pouvez toucher « Choisir ce dossier » en haut à droite", "Sie können oben rechts \u201eDiesen Ordner wählen\u201c antippen", "Você pode tocar em 'Escolher esta pasta' no canto superior direito", "Вы можете нажать «Выбрать эту папку» в правом верхнем углу")
    var current by remember {
        mutableStateOf(
            if (initialPath.isNotBlank()) File(initialPath).takeIf { it.isDirectory } else null
        )
    }
    var stack by remember { mutableStateOf<List<File>>(emptyList()) }

    // 在此选目录界面按返回键：先回到上级，到根后再退就返回设置菜单（而非首页）
    BackHandler { onBack() }

    val granted by produceState(initialValue = hasManageStorage()) {
        while (true) { value = hasManageStorage(); delay(1500) }
    }

    if (!granted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).background(MaterialTheme.colorScheme.secondaryContainer, CardShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(permTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(permSubDir, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.height(18.dp))
            Button(onClick = { openManageStorageSettings(context) }, shape = BtnShape) { Text(btnEnable) }
        }
        return
    }

    val dir = current ?: Environment.getExternalStorageDirectory()
    val subDirs = remember(dir) {
        dir.listFiles()?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
            ?: emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(tiPickDir, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onPick(dir.absolutePath) }) { Text(btnPickThis) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (stack.isEmpty()) { current = null; stack = emptyList() }
                else {
                    val parent = stack.last()
                    stack = stack.dropLast(1)
                    current = if (parent == Environment.getExternalStorageDirectory()) null else parent
                }
            }, enabled = current != null) {
                Icon(Icons.Default.ChevronRight, null,
                    modifier = Modifier.rotate(180f).size(22.dp),
                    tint = if (current != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
            Text(dir.absolutePath, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = { current = null; stack = emptyList() }, enabled = current != null) {
                Icon(Icons.Default.Home, null,
                    tint = if (current != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
        if (subDirs.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                EmptyState(Icons.Default.Folder, emptyDirTitle, emptyDirSub)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listItems(subDirs, key = { it.absolutePath }) { f ->
                    FileNodeRow(f) {
                        stack = stack + dir
                        current = f
                    }
                }
            }
        }
    }
}

// ===== 通用组件 =====
@Composable
private fun EmptyState(icon: ImageVector?, title: String, desc: String? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(76.dp).background(MaterialTheme.colorScheme.secondaryContainer, CardShape),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Icon(icon, null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        if (desc != null) {
            Spacer(Modifier.height(4.dp))
            Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

// ===== 设备页（先选内容 → 再选设备 → 发送） =====
private data class Picked(val uri: Uri, val name: String, val isImage: Boolean, val folder: java.io.File? = null)

private fun queryDisplayName(cr: ContentResolver, uri: Uri): String {
    var name = "file"
    runCatching {
        cr.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && idx >= 0) name = c.getString(idx) ?: name
        }
    }
    return name
}

@Composable
private fun FileScreen(contents: SnapshotStateList<Picked>, vm: OgoViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by OgoLang.code.collectAsState()
    val apps by vm.apps.collectAsState()
    val appsLoading by vm.appsLoading.collectAsState()
    val mediaList by vm.media.collectAsState()
    val audioList by vm.audio.collectAsState()
    val mediaLoading by vm.mediaLoading.collectAsState()
    val audioLoading by vm.audioLoading.collectAsState()
    // 文件分类（多语言）
    val catApps = tr(lang, "应用", "應用", "Apps", "アプリ", "앱", "Aplicaciones", "Applications", "Apps", "Aplicativos", "Приложения")
    val catApk = tr(lang, "安装包", "安裝包", "APKs", "APK", "APK", "Instaladores", "Installateurs", "APKs", "Instaladores", "Установщики")
    val catMedia = tr(lang, "媒体", "媒體", "Media", "メディア", "미디어", "Multimedia", "Médias", "Medien", "Mídia", "Медиа")
    val catAudio = tr(lang, "音频", "音訊", "Audio", "オーディオ", "오디오", "Áudio", "Audio", "Audio", "Áudio", "Аудио")
    val catFiles = tr(lang, "文件", "檔案", "Files", "ファイル", "파일", "Archivos", "Fichiers", "Dateien", "Arquivos", "Файлы")
    val cats = listOf(catApps, catApk, catMedia, catAudio, catFiles)
    val tiSendContent = tr(lang, "发送内容", "傳送內容", "Content to send", "送信内容", "보낼 콘텐츠", "Contenido a enviar", "Contenu à envoyer", "Zu sendender Inhalt", "Conteúdo a enviar", "Содержимое для отправки")
    val btnClear = tr(lang, "清空选择", "清空選擇", "Clear selection", "選択をクリア", "선택 지우기", "Vaciar selección", "Effacer la sélection", "Auswahl löschen", "Limpar seleção", "Очистить выбор")
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
    val clearConfirmTitle = tr(lang, "确定要清空当前已选", "確定要清空目前已選", "Clear the currently selected", "選択中の内容をクリアしますか", "현재 선택 항목을 지우시겠습니까", "¿Vaciar lo seleccionado actualmente?", "Effacer la sélection actuelle ?", "Aktuelle Auswahl löschen?", "Limpar a seleção atual?", "Очистить текущий выбор")
    val clearConfirmCount = tr(lang, "项内容吗？", "項內容嗎？", " items?", "項目の内容をクリアしますか？", "항목의 내용입니까?", " elementos?", " éléments ?", " Elemente?", " itens?", " элементы?")
    var cat by remember { mutableStateOf(0) }
    var showClearConfirm by remember { mutableStateOf(false) }
    val mimes = when (cat) {
        2 -> arrayOf("image/*", "video/*")
        3 -> arrayOf("audio/*")
        4 -> arrayOf("*/*")
        else -> emptyArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val cr = context.contentResolver
        uris.forEach { uri ->
            contents += Picked(uri, queryDisplayName(cr, uri), (cr.getType(uri) ?: "").startsWith("image/"))
        }
    }

    // 「媒体」分类：切进来时申请相册权限并加载图片/视频缩略图
    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) vm.refreshMedia()
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) vm.refreshAudio()
    }
    LaunchedEffect(cat) {
        when (cat) {
            2 -> mediaPermLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
            3 -> audioPermLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tiSendContent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("（${contents.size}）", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            // 清空选择：仅在有已选内容时显示，红色文字，与"发送内容"一行水平对齐。
            // 用纯 Text + clickable 而非 TextButton，避免 TextButton 默认 40dp minHeight 把整行撑高，
            // 导致内容整体下移、压缩显示区域。
            if (contents.isNotEmpty()) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 6.dp)
                ) {
                    Text(btnClear, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Spacer(Modifier.height(12.dp))

        // LanShare 风格分类
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cats.size) { i ->
                CategoryChip(cats[i], selected = i == cat) { cat = i }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 文件分类之间以横向滑块方式切换
        AnimatedContent(
            targetState = cat,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(240)) { dir * it / 2 } + fadeIn(tween(160)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -dir * it / 2 } + fadeOut(tween(140)))
            }
        ) { c ->
            when (c) {
                0 -> AppsGrid(apps, appsLoading, contents)
                1 -> ApkScreen(contents, vm)
                2 -> MediaScreen(mediaList, mediaLoading, contents) { item ->
                    val exists = contents.any { it.uri == item.uri }
                    if (exists) contents.removeAll { it.uri == item.uri }
                    else contents += Picked(item.uri, item.name, !item.isVideo)
                }
                3 -> AudioScreen(audioList, audioLoading, contents)
                4 -> FileBrowserScreen(contents)
                else -> CategoryOpen(mimes) { launcher.launch(mimes) }
            }
        }
    }
    // 清空选择确认弹窗
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(btnClear) },
            text = { Text("$clearConfirmTitle ${contents.size} $clearConfirmCount") },
            confirmButton = {
                TextButton(onClick = {
                    contents.clear()
                    showClearConfirm = false
                }) { Text(btnClear, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text(btnCancel) }
            }
        )
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
    }
}

// ===== 安装包：扫描 /storage 下所有 .apk 文件并列出，点击加入发送 =====
@Composable
private fun ApkScreen(contents: SnapshotStateList<Picked>, vm: OgoViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lang by OgoLang.code.collectAsState()
    val titleApk = tr(lang, "安装包", "安裝包", "Installer packages", "インストーラー", "설치 패키지", "Paquetes de instalación", "Packages d'installation", "Installationspakete", "Pacotes de instalação", "Установочные пакеты")
    val emptyApkTitle = tr(lang, "未找到安装包", "未找到安裝包", "No APK files found", "APKが見つかりません", "APK 파일을 찾을 수 없습니다", "No se encontraron archivos APK", "Aucun fichier APK", "Keine APK-Dateien", "Nenhum arquivo APK", "Файлы APK не найдены")
    val emptyApkSub = tr(lang, "在存储中未搜到 .apk 文件", "在儲存中未搜到 .apk 檔案", "No .apk files found on storage", "ストレージに.apkがありません", "저장소에 .apk 파일이 없습니다", "No hay archivos .apk en el almacenamiento", "Aucun fichier .apk sur le stockage", "Keine .apk-Dateien im Speicher gefunden", "Nenhum arquivo .apk no armazenamento", "Файлы .apk в хранилище не найдены")
    val scanningApk = tr(lang, "正在扫描安装包…", "正在掃描安裝包…", "Scanning APK files…", "APKをスキャン中…", "APK 파일 스캔 중…", "Escaneando archivos APK…", "Analyse des fichiers APK…", "Scanne APK-Dateien…", "Escaneando arquivos APK…", "Сканирование файлов APK…")
    val permApkTitle = tr(lang, "需要「所有文件访问」权限", "需要「所有檔案存取」權限", "Full file access required", "「すべてのファイルへのアクセス」権限が必要です", "모든 파일 액세스 권한 필요", "Se requiere acceso a todos los archivos", "Accès à tous les fichiers requis", "Voller Dateizugriff erforderlich", "Requer acesso a todos os arquivos", "Требуется полный доступ к файлам")
    val permApkSub = tr(lang, "用于在存储中扫描安装包文件", "用於在儲存中掃描安裝包檔案", "To scan for APK files on storage", "ストレージのAPKを検索するため", "저장소에서 APK 파일을 찾기 위함", "Para escanear archivos APK del almacenamiento", "Pour analyser les fichiers APK du stockage", "Zum Scannen von APK-Dateien im Speicher", "Para escanear arquivos APK no armazenamento", "Для поиска файлов APK в хранилище")
    val btnApkEnable = tr(lang, "前往开启", "前往開啟", "Grant", "許可する", "권한 부여", "Conceder", "Autoriser", "Erteilen", "Conceder", "Разрешить")
    val countApk = tr(lang, "个", "個", " APKs", "個", "개", " APKs", " APK", " APKs", " APK", " шт.")

    val granted by produceState(initialValue = hasManageStorage()) {
        while (true) { value = hasManageStorage(); delay(1500) }
    }
    var apks by remember { mutableStateOf<List<File>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    LaunchedEffect(granted) {
        if (!granted) { apks = emptyList(); scanning = false; return@LaunchedEffect }
        // 已有缓存：直接展示，不重新扫描
        if (vm.apkCache != null) { apks = vm.apkCache ?: emptyList(); scanning = false; return@LaunchedEffect }
        scanning = true
        apks = vm.loadApks()
        scanning = false
    }

    // 逐个解析 apk 的应用图标（IO 线程解析，避免卡 UI；以绝对路径为键缓存）。
    // 解析前或解析失败时列表项显示兜底通用文件图标。
    var apkIcons by remember { mutableStateOf<Map<String, Drawable?>>(emptyMap()) }
    LaunchedEffect(apks) {
        if (apks.isEmpty()) return@LaunchedEffect
        val pm = context.packageManager
        val resolved = withContext(Dispatchers.IO) {
            apks.associate { f ->
                val icon = runCatching {
                    val ai = pm.getPackageArchiveInfo(f.absolutePath, 0)?.applicationInfo?.also {
                        it.sourceDir = f.absolutePath
                        it.publicSourceDir = f.absolutePath
                    }
                    ai?.loadIcon(pm)
                }.getOrNull()
                f.absolutePath to icon
            }
        }
        apkIcons = resolved
    }

    if (!granted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).background(MaterialTheme.colorScheme.secondaryContainer, CardShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(permApkTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(permApkSub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(18.dp))
            Button(onClick = { openManageStorageSettings(context) }, shape = BtnShape) { Text(btnApkEnable) }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(titleApk, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text("${apks.size}$countApk", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                scanning -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(scanningApk, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
                apks.isEmpty() -> EmptyState(Icons.Default.Folder, emptyApkTitle, emptyApkSub)
                else -> Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listItems(apks, key = { it.absolutePath }) { f ->
                            val uri = runCatching {
                                FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", f)
                            }.getOrNull()
                            val selected = uri != null && contents.any { it.uri == uri }
                            ApkListItem(f, apkIcons[f.absolutePath], selected) {
                                if (uri != null) {
                                    if (selected) contents.removeAll { it.uri == uri }
                                    else contents += Picked(uri, f.name, isImageFile(f))
                                }
                            }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                        ScrollToTopFab(listState.firstVisibleItemIndex > 0) {
                            scope.launch { listState.animateScrollToItem(0) }
                        }
                    }
                }
            }
        }
    }
}

private data class AlbumItem(val id: String, val name: String, val items: List<MediaItem>) {
    val photoCount: Int get() = items.count { !it.isVideo }
    val videoCount: Int get() = items.size - photoCount
}

/** 安装包列表项：显示 apk 应用图标（解析失败用兜底通用文件图标）+ 文件名 + 大小。 */
@Composable
private fun ApkListItem(f: File, icon: Drawable?, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                if (icon != null) {
                    AsyncImage(model = icon, contentDescription = f.name,
                        modifier = Modifier.size(36.dp), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatSize(f.length()), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MediaScreen(items: List<MediaItem>, loading: Boolean, contents: SnapshotStateList<Picked>, onToggle: (MediaItem) -> Unit) {
    val albumListState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val lang by OgoLang.code.collectAsState()
    val emptyMediaTitle = tr(lang, "相册暂无内容", "相簿暫無內容", "No album content", "アルバムにコンテンツがありません", "앨범에 콘텐츠가 없습니다", "El álbum está vacío", "L'album est vide", "Kein Album-Inhalt", "Álbum sem conteúdo", "В альбоме нет содержимого")
    val emptyMediaSub = tr(lang, "授予照片/视频权限后可勾选发送", "授予照片/影片權限後可勾選傳送", "Grant photo/video permission to select and send", "写真・動画の権限を許可すると選択して送信できます", "사진/동영상 권한을 허용하면 선택하여 보낼 수 있습니다", "Concede permiso de fotos/vídeo para seleccionar y enviar", "Autorisez la permission photos/vidéo pour sélectionner et envoyer", "Erteilen Sie Foto-/Video-Berechtigung zum Auswählen und Senden", "Conceda permissão de fotos/vídeos para selecionar e enviar", "Разрешите доступ к фото/видео для выбора и отправки")
    val loadingMediaText = tr(lang, "加载中…", "載入中…", "Loading…", "読み込み中…", "불러오는 중…", "Cargando…", "Chargement…", "Laden…", "Carregando…", "Загрузка…")
    val albums = remember(items) {
        // 用 bucketId 归并：同一相册的图片和视频合并到一起
        items.groupBy { it.bucketId }
            .map { (id, list) -> AlbumItem(id, list.first().bucket, list) }
            .sortedByDescending { it.items.size }
    }
    var album by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = album != null) { album = null }

    if (albums.isEmpty()) {
        if (loading && items.isEmpty()) {
            // 加载完成前不闪空态：显示加载中
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(loadingMediaText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
                EmptyState(Icons.Default.Image, emptyMediaTitle, emptyMediaSub)
            }
        }
        return
    }

    if (album == null) {
        // 先展示相册列表（封面 + 名称 + 数量）
        // 已选 uri 集合，供各相册统计选中数量（直接读 contents 以响应 clear()，不用 remember 缓存）
        val selectedUris = contents.map { it.uri }.toSet()
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = albumListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listItems(albums, key = { it.id }) { a ->
                    val selCount = a.items.count { it.uri in selectedUris }
                    AlbumCard(a, selCount) { album = a.id }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                ScrollToTopFab(albumListState.firstVisibleItemIndex > 0) {
                    scope.launch { albumListState.animateScrollToItem(0) }
                }
            }
        }
    } else {
        // 进入某个相册后展示内部图片/视频
        val current = albums.firstOrNull { it.id == album }
        val backText = tr(lang, "返回", "返回", "Back", "戻る", "뒤로", "Volver", "Retour", "Zurück", "Voltar", "Назад")
        val countItems = tr(lang, "项", "項", " items", "項目", "개", " elementos", " éléments", " Elemente", " itens", " элементов")
        val videoOnlyLabel = tr(lang, "只看视频", "只看影片", "Videos", "動画のみ", "동영상만", "Solo vídeos", "Vidéos seul", "Nur Videos", "Só vídeos", "Только видео")
        var videoOnly by remember(album) { mutableStateOf(false) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { album = null }) { Text(backText) }
                Text(current?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${current?.items?.size ?: 0} $countItems",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                // 只看视频切换按钮
                TextButton(onClick = { videoOnly = !videoOnly }) {
                    Text(videoOnlyLabel, fontSize = 12.sp,
                        color = if (videoOnly) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (videoOnly) FontWeight.Bold else FontWeight.Normal)
                }
            }
            if (current != null) {
                val displayItems = if (videoOnly) current.items.filter { it.isVideo } else current.items
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gridItems(displayItems, key = { it.uri.toString() }) { item ->
                            val selected = contents.any { it.uri == item.uri }
                            MediaTile(item, selected) { onToggle(item) }
                        }
                    }
                    Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                        ScrollToTopFab(gridState.firstVisibleItemIndex > 0) {
                            scope.launch { gridState.animateScrollToItem(0) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(album: AlbumItem, selectedCount: Int, onClick: () -> Unit) {
    val lang by OgoLang.code.collectAsState()
    val photoLabel = tr(lang, "图片", "圖片", "Photos", "写真", "사진", "Fotos", "Photos", "Fotos", "Fotos", "Фото")
    val videoLabel = tr(lang, "视频", "影片", "Videos", "動画", "동영상", "Vídeos", "Vidéos", "Videos", "Vídeos", "Видео")
    val selLabel = tr(lang, "已选", "已選", "selected", "選択中", "선택", "seleccionado", "sélectionné", "ausgewählt", "selecionado", "выбрано")
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selectedCount > 0) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val first = album.items.first()
            val videoThumb = if (first.isVideo) rememberVideoThumb(first.uri.toString()) else null
            if (first.isVideo && videoThumb != null) {
                Image(videoThumb.asImageBitmap(), contentDescription = album.name,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                AsyncImage(
                    model = first.uri, contentDescription = album.name,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(album.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(
                        album.photoCount.takeIf { it > 0 }?.let { "$photoLabel $it" },
                        album.videoCount.takeIf { it > 0 }?.let { "$videoLabel $it" }
                    ).filterNotNull().joinToString(" · ") + if (selectedCount > 0) " · $selLabel $selectedCount" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
            }
            // 已选数量徽标
            if (selectedCount > 0) {
                Box(
                    Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$selectedCount", color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaTile(item: MediaItem, selected: Boolean, onClick: () -> Unit) {
    // 视频缩略图：Coil 默认不支持 content://video URI 解码，用 MediaMetadataRetriever 抓首帧
    val videoThumb = if (item.isVideo) rememberVideoThumb(item.uri.toString()) else null
    Box(
        Modifier.aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        if (item.isVideo && videoThumb != null) {
            Image(videoThumb.asImageBitmap(), item.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            AsyncImage(
                model = item.uri, contentDescription = item.name,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        if (selected) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f)))
            Box(Modifier.matchParentSize().padding(4.dp), contentAlignment = Alignment.TopEnd) {
                Box(
                    Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp))
                }
            }
        }
        if (item.isVideo) {
            Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.size(26.dp))
            }
        }
    }
}

@Composable
private fun AudioScreen(items: List<AudioItem>, loading: Boolean, contents: SnapshotStateList<Picked>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lang by OgoLang.code.collectAsState()
    val emptyAudioTitle = tr(lang, "暂无音频", "暫無音訊", "No audio", "オーディオがありません", "오디오 없음", "Sin audio", "Aucun audio", "Keine Audiodateien", "Sem áudio", "Нет аудио")
    val emptyAudioSub = tr(lang, "授予音频权限后即可显示设备中的音乐", "授予音訊權限後即可顯示裝置中的音樂", "Grant audio permission to show music on this device", "オーディオの権限を許可すると端末の音楽が表示されます", "오디오 권한을 허용하면 기기의 음악이 표시됩니다", "Concede permiso de audio para mostrar la música del dispositivo", "Autorisez la permission audio pour afficher la musique", "Erteilen Sie Audio-Berechtigung, um Musik anzuzeigen", "Conceda permissão de áudio para mostrar a música", "Разрешите доступ к аудио, чтобы показать музыку")
    val loadingAudioText = tr(lang, "加载中…", "載入中…", "Loading…", "読み込み中…", "불러오는 중…", "Cargando…", "Chargement…", "Laden…", "Carregando…", "Загрузка…")
    val player = remember { MediaPlayer() }
    var current by remember { mutableStateOf<AudioItem?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    fun playOrPause() {
        val c = current ?: return
        if (playing) {
            player.pause(); playing = false
        } else {
            runCatching {
                if (duration > 0 && position >= duration) { player.seekTo(0); position = 0 }
                player.start(); playing = true
            }
        }
    }

    fun play(item: AudioItem) {
        if (current?.uri == item.uri) { playOrPause(); return }
        runCatching {
            player.reset()
            player.setDataSource(context, item.uri)
            player.prepare()
            duration = player.duration.toLong().coerceAtLeast(0)
            position = 0
            current = item
            player.start(); playing = true
        }
    }

    // 播放期间轮询刷新进度
    LaunchedEffect(current, playing) {
        while (isActive && playing) {
            kotlinx.coroutines.delay(500)
            position = if (player.isPlaying) player.currentPosition.toLong() else position
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (current != null) {
            AudioPlayerBar(
                item = current!!, playing = playing, position = position, duration = duration,
                onPlayPause = ::playOrPause,
                onSeek = { ms ->
                    position = ms
                    runCatching { player.seekTo(ms.toInt()) }
                },
                onClose = {
                    runCatching { player.stop() }
                    playing = false; current = null; position = 0; duration = 0
                }
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                if (loading) {
                    // 加载完成前不闪空态：显示加载中
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(loadingAudioText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        EmptyState(Icons.Default.MusicNote, emptyAudioTitle, emptyAudioSub)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listItems(items, key = { it.uri.toString() }) { item ->
                        val selected = contents.any { it.uri == item.uri }
                        AudioCard(
                            item = item,
                            visible = item.uri == current?.uri,
                            playing = item.uri == current?.uri && playing,
                            selected = selected,
                            onPlay = { play(item) },
                            onAdd = {
                                if (selected) contents.removeAll { it.uri == item.uri }
                                else contents += Picked(item.uri, item.title, false)
                            }
                        )
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                ScrollToTopFab(listState.firstVisibleItemIndex > 0) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
        }
    }
}

@Composable
private fun AudioPlayerBar(
    item: AudioItem, playing: Boolean, position: Long, duration: Long,
    onPlayPause: () -> Unit, onSeek: (Long) -> Unit, onClose: () -> Unit
) {
    val lang by OgoLang.code.collectAsState()
    val previewing = tr(lang, "正在预览", "正在預覽", "Previewing", "プレビュー中", "미리보기 중", "Reproduciendo", "En cours de lecture", "Vorschau", "Reproduzindo", "Воспроизведение")
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 醒目的大播放/暂停键
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlayPause),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                        tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(previewing, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    item.artist.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            // 可拖动进度条
            Slider(
                value = progress,
                onValueChange = { f -> onSeek((f * if (duration > 0) duration else 0).toLong()) },
                modifier = Modifier.fillMaxWidth()
            )
            Row(Modifier.fillMaxWidth()) {
                Text(formatDuration(position), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(duration), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AudioCard(item: AudioItem, visible: Boolean, playing: Boolean, selected: Boolean,
                      onPlay: () -> Unit, onAdd: () -> Unit) {
    Card(
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                visible -> MaterialTheme.colorScheme.primaryContainer
                selected -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(item.artist.ifBlank { null }, formatDuration(item.durationMs)).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                    tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(6.dp))
            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun FileBrowserScreen(contents: SnapshotStateList<Picked>) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val lang by OgoLang.code.collectAsState()
    val permTitle = tr(lang, "需要「所有文件访问」权限", "需要「所有檔案存取」權限", "Full file access required", "「すべてのファイルへのアクセス」権限が必要です", "모든 파일 액세스 권한 필요", "Se requiere acceso a todos los archivos", "Accès à tous les fichiers requis", "Voller Dateizugriff erforderlich", "Requer acesso a todos os arquivos", "Требуется полный доступ к файлам")
    val permSub = tr(lang, "用于在应用内浏览整个内部存储的文件并选择发送", "用於在應用內瀏覽整個內部儲存的檔案並選擇傳送", "To browse and select files across internal storage", "内部ストレージ全体を閲覧・選択して送信するため", "내부 저장소의 파일을 탐색하고 선택하여 보내기 위함", "Para explorar y seleccionar archivos del almacenamiento interno", "Pour parcourir et sélectionner les fichiers du stockage interne", "Zum Durchsuchen und Auswählen von Dateien im internen Speicher", "Para navegar e selecionar arquivos no armazenamento interno", "Для просмотра и выбора файлов во внутреннем хранилище")
    val btnEnable = tr(lang, "前往开启", "前往開啟", "Grant", "許可する", "권한 부여", "Conceder", "Autoriser", "Erteilen", "Conceder", "Разрешить")
    val placeholderSearch = tr(lang, "全局搜索文件", "全域搜尋檔案", "Search all files", "全体を検索", "파일 전체 검색", "Buscar todos los archivos", "Rechercher tous les fichiers", "Alle Dateien durchsuchen", "Pesquisar todos os arquivos", "Поиск всех файлов")
    val searchingText = tr(lang, "正在搜索…", "正在搜尋…", "Searching…", "検索中…", "검색 중…", "Buscando…", "Recherche…", "Suche…", "Pesquisando…", "Поиск…")
    val emptySearchTitle = tr(lang, "未找到匹配文件", "未找到相符檔案", "No matching files", "一致するファイルがありません", "일치하는 파일 없음", "No hay archivos coincidentes", "Aucun fichier correspondant", "Keine passenden Dateien", "Nenhum arquivo correspondente", "Совпадений не найдено")
    val emptySearchSub = tr(lang, "换个关键词试试", "換個關鍵字試試", "Try a different keyword", "別のキーワードをお試しください", "다른 키워드를 사용해 보세요", "Prueba otra palabra clave", "Essayez un autre mot-clé", "Versuchen Sie ein anderes Stichwort", "Tente outra palavra-chave", "Попробуйте другое слово")
    val emptyDirTitle = tr(lang, "此目录为空", "此目錄為空", "This folder is empty", "このフォルダは空です", "이 폴더는 비어 있습니다", "Esta carpeta está vacía", "Ce dossier est vide", "Dieser Ordner ist leer", "Esta pasta está vazia", "Папка пуста")
    val emptyDirSub = tr(lang, "上级目录可能有文件", "上層目錄可能有檔案", "Files may be in a parent folder", "親フォルダにファイルがあるかもしれません", "상위 폴더에 파일이 있을 수 있습니다", "Puede haber archivos en una carpeta superior", "Des fichiers peuvent se trouver dans un dossier parent", "Dateien könnten in einem übergeordneten Ordner sein", "Pode haver arquivos em uma pasta superior", "Файлы могут быть в родительской папке")
    val internalStorage = tr(lang, "内部存储", "內部儲存", "Internal storage", "内部ストレージ", "내부 저장소", "Almacenamiento interno", "Stockage interne", "Interner Speicher", "Armazenamento interno", "Внутреннее хранилище")
    var current by remember { mutableStateOf<File?>(null) }   // null 表示内部存储根目录
    var stack by remember { mutableStateOf<List<File>>(emptyList()) }
    var query by remember { mutableStateOf("") }

    // 进入文件夹后按返回键返回上一级；回到根目录才放行给外部
    BackHandler(enabled = stack.isNotEmpty() || current != null) {
        if (stack.isEmpty()) current = null
        else {
            val parent = stack.last()
            stack = stack.dropLast(1)
            // 回到根目录时归一化为 null，保证顶部返回键/首页键熄灭
            current = if (parent == Environment.getExternalStorageDirectory()) null else parent
        }
    }

    // 轮询「所有文件访问」授权状态，从系统设置返回后自动刷新
    val granted by produceState(initialValue = hasManageStorage()) {
        while (true) { value = hasManageStorage(); delay(1500) }
    }

    val dir = current ?: Environment.getExternalStorageDirectory()
    // 地址栏显示内容：根目录显示「内部存储」，否则只显示相对于内部存储的路径（省略 /storage/emulated/0）
    val displayPath = remember(current) {
        val root = Environment.getExternalStorageDirectory().absolutePath
        val abs = dir.absolutePath
        if (abs == root) internalStorage
        else abs.removePrefix(root).takeIf { it.startsWith("/") && it.isNotBlank() } ?: abs.removePrefix("/")
    }
    val entries = remember(dir) {
        dir.listFiles()?.filter { !it.isHidden }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(java.util.Locale.ROOT) }))
            ?: emptyList()
    }
    // 全局搜索：输入关键词后在内部存储递归查找文件名（不区分大小写）
    var searching by remember { mutableStateOf(false) }
    val globalResults = remember { mutableStateOf<List<File>>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            globalResults.value = emptyList(); searching = false; return@LaunchedEffect
        }
        searching = true
        globalResults.value = withContext(Dispatchers.IO) {
            searchFiles(Environment.getExternalStorageDirectory(), query.trim(), 300)
        }
        searching = false
    }
    val shown = if (query.isBlank()) entries else globalResults.value

    if (!granted) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(76.dp).background(MaterialTheme.colorScheme.secondaryContainer, CardShape),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(permTitle, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(permSub, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 32.dp))
            Spacer(Modifier.height(18.dp))
            Button(onClick = { openManageStorageSettings(context) }, shape = BtnShape) { Text(btnEnable) }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (stack.isEmpty()) { current = null; stack = emptyList() }
                else {
                    val parent = stack.last()
                    stack = stack.dropLast(1)
                    current = if (parent == Environment.getExternalStorageDirectory()) null else parent
                }
            }, enabled = stack.isNotEmpty() || current != null) {
                Icon(Icons.Default.ChevronRight, null,
                    modifier = Modifier.rotate(180f).size(22.dp),
                    tint = if (stack.isNotEmpty() || current != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
            Text(displayPath, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            // 选择当前文件夹：放在返回首页键左侧（仅进入子目录后可用），
            // 点击加入待发送区（可与文件混选），再次点击取消选择
            val folderSelected = current != null && contents.any { it.folder == dir }
            IconButton(
                onClick = {
                    if (!folderSelected) {
                        val uri = runCatching {
                            FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", dir)
                        }.getOrNull()
                        contents += Picked(uri ?: Uri.EMPTY, dir.name, false, folder = dir)
                    } else {
                        contents.removeAll { it.folder == dir }
                    }
                },
                enabled = current != null
            ) {
                // 文件夹图标，颜色与返回首页键一致（onSurface）
                Icon(painterResource(R.drawable.ic_folder_flat), null,
                    tint = if (folderSelected) MaterialTheme.colorScheme.primary
                    else if (current != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = { current = null; stack = emptyList() },
                enabled = current != null) {
                // 根目录时灰置不可点；进入子目录后才可回到根
                Icon(Icons.Default.Home, null,
                    tint = if (current != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
        // 搜索当前目录内文件（按文件名过滤）
        TextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(placeholderSearch, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) },
            trailingIcon = if (query.isNotEmpty()) {
                ({ IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null) } })
            } else null,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize()) {
                if (searching) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Sync, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(searchingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    EmptyState(Icons.Default.Folder,
                        if (query.isNotBlank()) emptySearchTitle else emptyDirTitle,
                        if (query.isNotBlank()) emptySearchSub else emptyDirSub)
                }
            }
        } else {
            Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listItems(shown, key = { it.absolutePath }) { f ->
                    val selUri = runCatching {
                        FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", f)
                    }.getOrNull()
                    val selected = selUri != null && contents.any { it.uri == selUri }
                    FileNodeRow(f, showParent = query.isNotBlank(), selected = selected) {
                        if (f.isDirectory) {
                            query = ""             // 搜索结果点进目录时退出搜索、进入浏览
                            stack = stack + dir
                            current = f
                        } else if (selUri != null) {
                            if (selected) contents.removeAll { it.uri == selUri }
                            else contents += Picked(selUri, f.name, isImageFile(f))
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 12.dp)) {
                ScrollToTopFab(listState.firstVisibleItemIndex > 0) {
                    scope.launch { listState.animateScrollToItem(0) }
                }
            }
            }
        }
    }
}

@Composable
private fun FileNodeRow(f: File, showParent: Boolean = false, selected: Boolean = false, onClick: () -> Unit) {
    val lang by OgoLang.code.collectAsState()
    val folderLabel = tr(lang, "文件夹", "資料夾", "Folder", "フォルダ", "폴더", "Carpeta", "Dossier", "Ordner", "Pasta", "Папка")
    val isDir = f.isDirectory
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Icon(
                    if (isDir) Icons.Default.Folder else Icons.Default.InsertDriveFile, null,
                    tint = if (isDir) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(f.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isDir) folderLabel
                    else if (showParent) f.parentFile?.path ?: ""
                    else formatSize(f.length()),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (isDir) Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun hasManageStorage(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

// OrangeGO 开源仓库（检查更新/问题反馈均跳转到 GitHub，URL 与 HereIAm 同源组织）
private const val OG_GITHUB_REPO = "https://github.com/orange-way/OrangeGO"

private fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

private fun openManageStorageSettings(context: Context) {
    runCatching {
        val pkg = context.packageName
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            .setData(Uri.parse("package:$pkg"))
        context.startActivity(intent)
    }
}

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "dng")
private val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "3gp", "3g2", "mov", "avi", "m4v", "ts", "wmv", "flv", "mpeg", "mpg", "ogv")
private val APK_EXTS = setOf("apk")

// 文件集成显示气泡体积上限（微信式）：媒体缩略图网格至多 3 行共 9 个，普通文件卡至多 6 个；
// 超过后仅展示前若干个，并在底部汇总显示「等 N 个文件」，避免单个气泡体积过大。
private const val INTEGRATED_MEDIA_CAP = 9
private const val INTEGRATED_CARD_CAP = 6

private fun isImageExt(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return ext in IMAGE_EXTS
}

private fun isVideoExt(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return ext in VIDEO_EXTS
}

private fun isImageFile(f: File): Boolean = isImageExt(f.name)

/** 判定某个本地引用是否为图片：file:// 用路径文件名扩展名；content:// 用真实显示名（fallbackName 对"3 个文件"等多文件标题不可靠）。 */
private fun isRefImage(context: Context, ref: String, fallbackName: String): Boolean {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return false
    if (u.scheme == "file") {
        return isImageExt((u.path ?: "").substringAfterLast('/'))
    }
    val (name, _) = refNameSize(context, ref)
    return isImageExt(name)
}

/** 判定某个本地引用是否为视频：file:// 用路径文件名扩展名；content:// 用真实显示名。 */
private fun isRefVideo(context: Context, ref: String, fallbackName: String): Boolean {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return false
    if (u.scheme == "file") {
        return isVideoExt((u.path ?: "").substringAfterLast('/'))
    }
    val (name, _) = refNameSize(context, ref)
    return isVideoExt(name)
}

/** 提取一条传输记录里全部可预览的媒体引用（图片在前、视频在后），供全屏分页预览；
 *  按真实文件名判定，返回 (引用, 是否视频)。无 localRefs 时退化为旧的 localRef。 */
private fun itemMediaRefs(context: Context, item: TransferItem, fallbackName: String): List<Pair<String, Boolean>> {
    val refs = if (item.localRefs.isNotEmpty()) item.localRefs
        else listOfNotNull(item.localRef.ifEmpty { null })
    val out = mutableListOf<Pair<String, Boolean>>()
    for (ref in refs) {
        if (ref.isBlank()) continue
        when {
            isRefImage(context, ref, fallbackName) -> out.add(ref to false)
            isRefVideo(context, ref, fallbackName) -> out.add(ref to true)
        }
    }
    return out
}

/** 在内部存储中递归扫描 .apk 安装包（忽略隐藏目录，限制数量）。 */
private fun scanApkFiles(limit: Int = 500): List<File> {
    val root = Environment.getExternalStorageDirectory()
    val out = ArrayList<File>()
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty() && out.size < limit) {
        val dir = stack.removeLast()
        val children = dir.listFiles() ?: continue
        for (f in children) {
            if (f.isHidden) continue
            if (f.isDirectory) stack.addLast(f)
            else if (f.extension.lowercase(java.util.Locale.ROOT) in APK_EXTS) {
                out.add(f)
                if (out.size >= limit) break
            }
        }
    }
    return out.sortedBy { it.name }
}

/** 把 localRef 解析为 Coil 可直接加载的图片源（content:// 用 Uri，file:// 用 File）。 */
private fun previewImageModel(ref: String): Any? {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return null
    return if (u.scheme == "file") runCatching { File(u.path ?: "") }.getOrNull() ?: u else u
}

/** 把一段文字保存为 TXT 文件（长按文本气泡触发）。优先公共下载目录；无权限失败则回退到应用专属外部目录。 */
private fun saveTextToTxt(context: Context, content: String) {
    data class Out(val file: File?, val private: Boolean)
    val out = runCatching {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val f = File(dir, "OrangeGo_文字_${System.currentTimeMillis()}.txt")
        f.writeText(content)
        Out(f, private = false)
    }.getOrElse {
        runCatching {
            val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "文字")
            dir.mkdirs()
            val f = File(dir, "文字_${System.currentTimeMillis()}.txt")
            f.writeText(content)
            Out(f, private = true)
        }.getOrNull()
    }
    if (out?.file != null) {
        val msg = if (out.private) "已保存：${out.file.absolutePath}\n(应用专属目录，卸载即删除)" else "已保存到 ${out.file.absolutePath}"
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
    } else {
        android.widget.Toast.makeText(context, "保存失败", android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 在内部存储中递归查找文件名包含关键词的文件（忽略隐藏目录，限制数量）。 */
private fun searchFiles(root: File, keyword: String, limit: Int): List<File> {
    val out = ArrayList<File>()
    val stack = ArrayDeque<File>()
    stack.addLast(root)
    while (stack.isNotEmpty() && out.size < limit) {
        val dir = stack.removeLast()
        val children = dir.listFiles() ?: continue
        for (f in children) {
            if (f.isHidden) continue
            if (f.isDirectory) stack.addLast(f)
            else if (f.name.contains(keyword, ignoreCase = true)) {
                out.add(f)
                if (out.size >= limit) break
            }
        }
    }
    return out.sortedBy { it.name }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, total / 60, total % 60)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes >= 1024 * 1024 -> "%.1f MB".format(java.util.Locale.ROOT, bytes / 1024.0 / 1024.0)
    else -> "%.0f KB".format(java.util.Locale.ROOT, bytes / 1024.0)
}

@Composable
private fun CategoryOpen(mimes: Array<String>, onPick: () -> Unit) {
    val lang by OgoLang.code.collectAsState()
    val pickerLabel = tr(lang, "从系统选择器挑选", "從系統選擇器挑選", "Pick from the system picker", "システムのセレクタから選択", "시스템 선택기에서 선택", "Seleccionar desde el selector del sistema", "Choisir depuis le sélecteur système", "Über den System-Picker auswählen", "Escolher do seletor do sistema", "Выбрать через системный селектор")
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 26.dp), horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(pickerLabel, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AppsGrid(allApps: List<InstalledApp>, loading: Boolean, contents: SnapshotStateList<Picked>) {
    val scope = rememberCoroutineScope()
    val vm: OgoViewModel = viewModel()
    val listState = rememberLazyListState()
    val lang by OgoLang.code.collectAsState()
    val userAppsLabel = tr(lang, "用户应用", "使用者應用", "User apps", "ユーザーアプリ", "사용자 앱", "Apps de usuario", "Apps utilisateur", "Benutzer-Apps", "Aplicativos do usuário", "Пользовательские приложения")
    val appsLoadingText = tr(lang, "加载中…", "載入中…", "Loading…", "読み込み中…", "불러오는 중…", "Cargando…", "Chargement…", "Laden…", "Carregando…", "Загрузка…")
    val showSystemLabel = tr(lang, "显示系统应用", "顯示系統應用", "Show system apps", "システムアプリを表示", "시스템 앱 표시", "Mostrar apps del sistema", "Afficher les apps système", "System-Apps anzeigen", "Mostrar aplicativos do sistema", "Показать системные приложения")
    val emptyApps = tr(lang, "暂无用户应用", "暫無使用者應用", "No user apps", "ユーザーアプリがありません", "사용자 앱 없음", "Sin apps de usuario", "Aucune app utilisateur", "Keine Benutzer-Apps", "Nenhum aplicativo do usuário", "Нет пользовательских приложений")
    val countLabel = tr(lang, "个", "個", " apps", "個", "개", " apps", " apps", " Apps", " apps", " шт.")
    val exportAdd = tr(lang, "导出并加入", "匯出並加入", "Export and add", "エクスポートして追加", "내보내기 후 추가", "Exportar y añadir", "Exporter et ajouter", "Exportieren und hinzufügen", "Exportar e adicionar", "Экспортировать и добавить")
    // 默认只显示用户应用，开关开启后把系统应用也一起显示
    var showSystem by remember { mutableStateOf(false) }
    val apps = if (showSystem) allApps else allApps.filterNot { it.isSystem }
    // 导出后 Picked 文件名固定为 “标签.apk”，据此判断是否已勾选
    fun cleanName(app: InstalledApp): String {
        val clean = app.label.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "$clean.apk"
    }
    ListTopDecorator(
        visible = listState.firstVisibleItemIndex > 0,
        onScrollToTop = { scope.launch { listState.animateScrollToItem(0) } }
    ) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(userAppsLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            Text("${apps.size} $countLabel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(showSystemLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Switch(
                checked = showSystem,
                onCheckedChange = { showSystem = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        if (apps.isEmpty() && loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(appsLoadingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else if (apps.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) { EmptyState(Icons.Default.Devices, emptyApps) }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listItems(apps, key = { it.packageName }) { app ->
                    val name = cleanName(app)
                    val selected = contents.any { it.name == name }
                    Card(
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(Modifier.fillMaxWidth().clickable {
                                if (selected) contents.removeAll { it.name == name }
                                else {
                                    // 立即打钩：先放占位项，导出完成后替换为真实 URI
                                    if (contents.none { it.name == name }) contents += Picked(Uri.EMPTY, name, false)
                                    scope.launch {
                                        val ex = vm.exportApp(app)
                                        if (ex != null) {
                                            // 仅当用户仍处于勾选状态（占位项还在）时替换为真实 URI
                                            if (contents.any { it.name == name }) {
                                                contents.removeAll { it.name == name }
                                                if (contents.none { it.uri == ex.uri }) contents += Picked(ex.uri, ex.name, false)
                                            }
                                        } else {
                                            // 导出失败则撤销占位
                                            contents.removeAll { it.name == name }
                                        }
                                    }
                                }
                            }.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .padding(3.dp)
                            ) {
                                app.icon?.let { AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(app.packageName,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            else Icon(Icons.Default.Add, exportAdd, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun DevicesScreen(
    peers: List<Peer>, selectedIds: Set<String>, onToggle: (String) -> Unit,
    favorites: List<com.orangeway.go.FavDevice>, isFavorite: (String) -> Boolean,
    onToggleFavorite: (Peer) -> Unit, onRemoveFavorite: (String) -> Unit
) {
    val lang by OgoLang.code.collectAsState()
    val tiChooseDevice = tr(lang, "选择设备", "選擇裝置", "Select devices", "デバイス選択", "기기 선택", "Seleccionar dispositivos", "Sélectionner les appareils", "Geräte auswählen", "Selecionar dispositivos", "Выберите устройства")
    val tiConnectedN = tr(lang, "已选择 %1\$d", "已選擇 %1\$d", "Selected %1\$d", "選択 %1\$d", "선택 %1\$d", "Seleccionados %1\$d", "Sélectionnés %1\$d", "Ausgewählt %1\$d", "Selecionados %1\$d", "Выбрано %1\$d")
    val tiNoPeers = tr(lang, "未发现附近设备", "未發現附近裝置", "No nearby devices found", "近くのデバイスが見つかりません", "근처 기기를 찾을 수 없습니다", "No se encontraron dispositivos cercanos", "Aucun appareil à proximité", "Keine Geräte in der Nähe", "Nenhum dispositivo encontrado", "Устройств поблизости не найдено")
    val tiNoPeersSub = tr(lang, "确认对端已打开 OrangeGO 并连到同一网络", "請確認對端已開啟 OrangeGO 並連到同一網路", "Make sure the peer has OrangeGO open on the same network", "相手が同一ネットワークでOrangeGOを開いていることを確認してください", "상대방이 같은 네트워크에서 OrangeGO를 열었는지 확인하세요", "Asegúrate de que el otro dispositivo tenga OrangeGO abierto en la misma red", "Vérifiez que l'autre appareil a OrangeGO ouvert sur le même réseau", "Stellen Sie sicher, dass das Gegenüber OrangeGO im selben Netzwerk geöffnet hat", "Certifique-se de que o outro dispositivo tem OrangeGO aberto na mesma rede", "Убедитесь, что на другом устройстве открыт OrangeGO в той же сети")
    val tiFavTitle = tr(lang, "收藏设备", "收藏裝置", "Favorite devices", "お気に入りデバイス", "즐겨찾기 기기", "Dispositivos favoritos", "Appareils favoris", "Lieblingsgeräte", "Dispositivos favoritos", "Избранные устройства")
    val tiFavEmpty = tr(lang, "暂无收藏设备", "暫無收藏裝置", "No favorite devices", "お気に入りデバイスなし", "즐겨찾기 기기 없음", "No hay dispositivos favoritos", "Aucun appareil favori", "Keine Lieblingsgeräte", "Nenhum dispositivo favorito", "Нет избранных устройств")
    var favExpanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            if (selectedIds.isEmpty()) tiChooseDevice else tiConnectedN.format(selectedIds.size),
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (peers.isEmpty()) {
                EmptyState(Icons.Default.Devices, tiNoPeers, tiNoPeersSub)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(158.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gridItems(peers, key = { it.deviceId }) { peer ->
                        DeviceTile(peer, lang, isSelected = peer.deviceId in selectedIds,
                            isFavorite = isFavorite(peer.deviceId),
                            onClick = { onToggle(peer.deviceId) },
                            onToggleFavorite = { onToggleFavorite(peer) }
                        )
                    }
                }
            }
        }
        // 收藏夹折叠面板（对齐电脑端设备页底部折叠面板）
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { favExpanded = !favExpanded }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(tiFavTitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Icon(
                        if (favExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)
                    )
                }
                if (favExpanded) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    if (favorites.isEmpty()) {
                        Text(tiFavEmpty, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                    } else {
                        FlowRow(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            favorites.forEach { fav ->
                                Row(
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(fav.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 140.dp))
                                        Text(fav.shortId, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        Icons.Default.Close, contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                                            onClick = { onRemoveFavorite(fav.id) }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddContentTile(onClick: () -> Unit, lang: String) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(tr(lang, "选择文件或图片", "選擇檔案或圖片", "Choose files or images", "ファイルや画像を選択", "파일 또는 이미지 선택", "Elige archivos o imágenes", "Choisissez des fichiers ou images", "Dateien oder Bilder auswählen", "Escolha arquivos ou imagens", "Выберите файлы или изображения"),
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddMiniTile(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(72.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Material 风格圆润星形（对齐 PC 端 FavTileStyle 的 Path data），支持填充/描边切换。 */
@Composable
private fun RoundedStar(filled: Boolean, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val density = LocalDensity.current
    val starSize = 18.dp
    val starPx = with(density) { starSize.toPx() }
    val s = starPx / 24f
    val path = remember(s) {
        Path().apply {
            moveTo(12f * s, 17.27f * s)
            lineTo(18.18f * s, 21f * s)
            lineTo(16.54f * s, 13.97f * s)
            lineTo(22f * s, 9.24f * s)
            lineTo(14.81f * s, 8.63f * s)
            lineTo(12f * s, 2f * s)
            lineTo(9.19f * s, 8.63f * s)
            lineTo(2f * s, 9.24f * s)
            lineTo(7.46f * s, 13.97f * s)
            lineTo(5.82f * s, 21f * s)
            close()
        }
    }
    val strokeW = 1.7f * s
    val clickMod = if (onClick != null) modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick
    ) else modifier
    Canvas(modifier = clickMod.size(starSize)) {
        if (filled) drawPath(path, color = tint)
        drawPath(path, color = tint, style = Stroke(width = strokeW, join = StrokeJoin.Round, miter = 1f))
    }
}

@Composable
private fun DeviceTile(peer: Peer, lang: String, isSelected: Boolean, isFavorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val onlineText = tr(lang, "在线", "在線", "Online", "オンライン", "온라인", "En línea", "En ligne", "Online", "Online", "В сети")
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.5f, 900f), label = "tile")
    // LocalSend 设备对齐电脑端 DeviceTileStyle DataTrigger：头像/状态点切换为 LocalSend 青绿色（Material Teal）
    val lsAvatarBg = Color(0xFF00897B)
    val lsStatusDot = Color(0xFF26A69A)
    Card(
        onClick = onClick, interactionSource = interaction,
        modifier = Modifier.scale(scale),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Box(
                    Modifier.size(40.dp).background(
                        if (peer.isLocalSend) lsAvatarBg
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        RoundedCornerShape(12.dp)
                    ), contentAlignment = Alignment.Center
                ) {
                    Text(peer.initial,
                        color = if (peer.isLocalSend) Color.White else MaterialTheme.colorScheme.primary,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                // LocalSend 徽章：官方图标 + LocalSend 文字，底部与头像底部水平对齐（对齐电脑端 LocalSendTag VerticalAlignment=Bottom）
                if (peer.isLocalSend) {
                    Spacer(Modifier.width(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_localsend),
                            contentDescription = "LocalSend",
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("LocalSend", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(Modifier.weight(1f))
                // 收藏星标：Material 圆润星形（对齐 PC 端 FavTileStyle），顶端与头像顶边对齐，右边距=顶部距
                RoundedStar(
                    filled = isFavorite,
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.offset(y = -22.dp),
                    onClick = onToggleFavorite
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peer.ip, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(
                    if (peer.isLocalSend) lsStatusDot else MaterialTheme.colorScheme.primary, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(onlineText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

// ===== 接收确认（悬浮层） =====
@Composable
private fun IncomingDialog(
    list: List<PendingReceive>,
    onAccept: (PendingReceive) -> Unit,
    onReject: (PendingReceive) -> Unit,
    onConfirmPin: (PendingReceive, String) -> Unit
) {
    val lang by OgoLang.code.collectAsState()
    val title = tr(lang, "收到文件请求", "收到檔案請求", "Incoming file request", "ファイルリクエストを受信", "파일 요청 수신", "Solicitud de archivos recibida", "Demande de fichiers entrante", "Eingehende Dateianfrage", "Solicitação de arquivos recebida", "Входящий запрос на файлы")
    Dialog(onDismissRequest = { }) {
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) { listItems(list, key = { it.id }) { IncomingCard(it, onAccept, onReject, onConfirmPin) } }
            }
        }
    }
}

@Composable
private fun IncomingCard(
    req: PendingReceive,
    onAccept: (PendingReceive) -> Unit,
    onReject: (PendingReceive) -> Unit,
    onConfirmPin: (PendingReceive, String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val lang by OgoLang.code.collectAsState()
    val sendFiles = tr(lang, "要发送", "要傳送", " wants to send", "から送信", "가 보내려 함", " quiere enviar", " veut envoyer", " möchte senden", " quer enviar", " хочет отправить")
    val fileCount = tr(lang, "个文件", "個檔案", " files", "個のファイル", "개 파일", " archivos", " fichiers", " Dateien", " arquivos", " файлов")
    val totalLabel = tr(lang, "共", "共", "Total ", "合計 ", "총 ", "Total ", "Total ", "Gesamt ", "Total ", "Всего ")
    val pinHint = tr(lang, "输入接收 PIN", "輸入接收 PIN", "Enter receive PIN", "受信PINを入力", "받기 PIN 입력", "Introduce el PIN de recepción", "Saisissez le code PIN de réception", "Empfangs-PIN eingeben", "Digite o PIN de recepção", "Введите PIN для приёма")
    val btnReject = tr(lang, "拒绝", "拒絕", "Reject", "拒否", "거부", "Rechazar", "Refuser", "Ablehnen", "Rejeitar", "Отклонить")
    val btnConfirm = tr(lang, "确认接收", "確認接收", "Accept", "受信確認", "받기 확인", "Aceptar", "Accepter", "Empfangen", "Aceitar", "Принять")
    val btnAccept = tr(lang, "接受", "接受", "Accept", "受信", "수락", "Aceptar", "Accepter", "Akzeptieren", "Aceitar", "Принять")
    Card(
        modifier = Modifier.fillMaxWidth(), shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(req.deviceName.take(1).uppercase(), color = MaterialTheme.colorScheme.primary,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("「${req.deviceName}」$sendFiles ${req.fileCount}$fileCount",
                    fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text("$totalLabel${formatBytes(req.totalBytes)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        if (req.needsPin) {
            TextField(
                value = pin, onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(6) },
                singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(pinHint) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 8.dp)) {
            OutlinedButton(onClick = { onReject(req) }, shape = BtnShape, modifier = Modifier.weight(1f)) { Text(btnReject) }
            Spacer(Modifier.width(10.dp))
            if (req.needsPin) {
                Button(onClick = { onConfirmPin(req, pin) }, enabled = pin.length >= 4,
                    shape = BtnShape, modifier = Modifier.weight(1f)) { Text(btnConfirm) }
            } else {
                Button(onClick = { onAccept(req) }, shape = BtnShape, modifier = Modifier.weight(1f)) { Text(btnAccept) }
            }
        }
    }
}

private fun formatBytes(b: Long): String {
    val gb = 1024L * 1024 * 1024
    val mb = 1024L * 1024
    val kb = 1024L
    return when {
        b >= gb -> "%.1f GB".format(b / gb.toDouble())
        b >= mb -> "%.1f MB".format(b / mb.toDouble())
        b >= kb -> "%.1f KB".format(b / kb.toDouble())
        else -> "$b B"
    }
}

// ===== 未选设备发送时的「可连接设备」多选弹窗 =====
@Composable
private fun PeerPickerDialog(
    peers: List<Peer>,
    initialSelected: Set<String>,
    onConfirm: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val lang by OgoLang.code.collectAsState()
    val title = tr(lang, "选择可连接设备", "選擇可連接裝置", "Select devices to send", "送信先デバイスを選択", "보낼 기기 선택", "Selecciona dispositivos", "Choisir des appareils", "Geräte zum Senden wählen", "Selecione dispositivos", "Выберите устройства")
    val empty = tr(lang, "暂无可用设备", "暫無可用裝置", "No available devices", "利用可能なデバイスがありません", "사용 가능한 기기가 없습니다", "No hay dispositivos disponibles", "Aucun appareil disponible", "Keine verfügbaren Geräte", "Nenhum dispositivo disponível", "Нет доступных устройств")
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
    val btnConfirm = tr(lang, "发送到此设备", "發送到此設備", "Send to this device", "このデバイスに送信", "이 기기로 보내기", "Enviar a este dispositivo", "Envoyer à cet appareil", "An dieses Gerät senden", "Enviar para este dispositivo", "Отправить на это устройство")
    // 本地选择集合：点卡片切换，点「确定」才写回全局并发送
    var sel by remember { mutableStateOf(initialSelected) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                if (peers.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listItems(peers, key = { it.deviceId }) { peer ->
                            val checked = peer.deviceId in sel
                            Card(
                                onClick = {
                                    sel = if (checked) sel - peer.deviceId else sel + peer.deviceId
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (checked) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text(peer.name.take(1), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(peer.ip, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(btnCancel) }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(enabled = sel.isNotEmpty(), onClick = { onConfirm(sel) }) { Text(btnConfirm) }
                }
            }
        }
    }
}