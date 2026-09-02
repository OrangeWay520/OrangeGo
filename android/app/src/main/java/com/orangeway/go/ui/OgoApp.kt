package com.orangeway.go.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

private val CardShape = RoundedCornerShape(20.dp)
private val BtnShape = RoundedCornerShape(14.dp)

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
    val receivedNotice by vm.receivedNotice.collectAsState()
    val contents = remember { mutableStateListOf<Picked>() }
    // 默认打开为传输记录页（中=传输，左右分别为设备/文件）
    var tab by rememberSaveable { mutableStateOf(1) }
    var showSettings by remember { mutableStateOf(false) }
    // 设置界面是否处于二级子页：true 时设置键回主设置，false 时设置键关闭设置
    var settingsSub by remember { mutableStateOf(false) }
    // 请求 SettingsScreen 返回主设置的次数（外部发起的递增请求计数）
    var setSettingsRoot by remember { mutableStateOf(0) }
    // 当前勾选连接的目标设备（全局），贯穿 DevicesScreen / TransfersScreen / 中央悬浮发送键
    var selectedPeer by remember { mutableStateOf<Peer?>(null) }
    // 未选设备但有内容时，发送键弹出「可连接设备」选择弹窗
    var showPeerPicker by remember { mutableStateOf(false) }

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
        val now = System.currentTimeMillis()
        if (now - lastBackAt < 2000) {
            activity?.finish()
        } else {
            lastBackAt = now
            android.widget.Toast.makeText(context, toastPressAgain, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // 设置键二段式行为：未打开→进入设置；打开且处于二级子页→返回主设置；打开且在设置主界面→关闭设置
    val onSettingsKey: () -> Unit = {
        when {
            !showSettings -> { showSettings = true; settingsSub = false }
            settingsSub -> setSettingsRoot += 1
            else -> showSettings = false
        }
    }

    // 顶部横幅：订阅 ViewModel 的收到内容提醒，消费后自动消失（约 3 秒，也可点右上 × 手动关闭）
    var noticeBanner by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(receivedNotice) {
        val msg = receivedNotice
        if (msg != null) {
            vm.receivedNotice.value = null // 用后置 null：消费该提醒
            noticeBanner = msg
            delay(3000)
            noticeBanner = null // 自动消失（auto-clear）
        }
    }
    val noticeBannerText = remember(noticeBanner, lang) {
        noticeBanner?.let { n ->
            val peer = n.substringAfter(':')
            if (n.startsWith("T:"))
                tr(lang, "收到来自 $peer 的文本", "收到來自 $peer 的文字", "Received text from $peer", "$peer からテキストを受信", "$peer 님이 보낸 텍스트를 받았습니다", "Texto recibido de $peer", "Texte reçu de $peer", "Text von $peer empfangen", "Texto recebido de $peer", "Получен текст от $peer")
            else
                tr(lang, "收到来自 $peer 的文件", "收到來自 $peer 的檔案", "Received file from $peer", "$peer からファイルを受信", "$peer 님이 보낸 파일을 받았습니다", "Archivo recibido de $peer", "Fichier reçu de $peer", "Datei von $peer empfangen", "Arquivo recebido de $peer", "Получен файл от $peer")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Scaffold 不预占系统栏/IME（否则 contentWindowInsets 会吃掉键盘，输入框被推到键盘之下），
            // 底部手势条由 OgoBottomBar.navigationBarsPadding 处理，键盘由内容区 max padding 顶起。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = { OgoTopBar(onOpenSettings = onSettingsKey, settingsActive = showSettings) },
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
                            onClearTransfers = vm::clearTransfers,
                            retentionDays = vm.retentionDays.collectAsState().value,
                            onSetRetentionDays = vm::setAutoCleanupDays,
                            onBack = { showSettings = false },
                            onSubChanged = { settingsSub = it },
                            returnToRoot = setSettingsRoot
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
                                0 -> DevicesScreen(peers.values.toList(), selectedPeer) { p ->
                                    selectedPeer = p
                                }
                                1 -> TransfersScreen(transfers, peers.values.toList(), selectedPeer, vm::sendTextTo, vm::cancelSend, vm::deleteTransfer)
                                else -> FileScreen(contents, vm)
                            }
                        }
                    }
                }
            }
        }

        // 中央悬浮发送键：置于整个界面最顶层绘制，不被上方内容遮挡。
        // 位于导航栏内近似居中（偏移很小，不凸出成小块）。
        // 仅当已选中连接设备且已选好内容、且未进入设置时才点亮；否则恢复未选中外观
        val sendActive = !showSettings && selectedPeer != null && contents.isNotEmpty()
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
                    val sp = selectedPeer
                    if (sp != null && contents.isNotEmpty()) {
                        // 已选中连接设备且已选好内容：直接发送并回到传输记录界面
                        vm.sendFiles(sp, contents.map { it.uri })
                        contents.clear()
                        tab = 1
                    } else if (sp == null && contents.isNotEmpty()) {
                        // 已选好内容但未连接设备：弹出「可连接设备」选择弹窗，选一台立即发送
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

        // 收到内容顶部横幅：置于最顶层，约 3 秒自动消失，可点右上 × 手动关闭，不遮挡操作
        noticeBannerText?.let { text ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(30f)
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 10.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f).padding(start = 14.dp, top = 10.dp, bottom = 10.dp)
                    )
                    IconButton(onClick = { noticeBanner = null }) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (incoming.isNotEmpty()) IncomingDialog(incoming, vm::accept, vm::reject, vm::confirmPin)

    // 未选设备但有内容时：弹出「可连接设备」选择弹窗，点一台立即发送
    if (showPeerPicker) {
        PeerPickerDialog(
            peers.values.toList(),
            onPick = { peer ->
                vm.sendFiles(peer, contents.map { it.uri })
                contents.clear()
                showPeerPicker = false
                tab = 1
            },
            onDismiss = { showPeerPicker = false }
        )
    }
}

@Composable
private fun OgoTopBar(onOpenSettings: () -> Unit, settingsActive: Boolean = false) {
    val dark = isSystemInDarkTheme()
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
        IconButton(
            onClick = {
                onOpenSettings()
                scope.launch { spin.animateTo(spin.value - 120f, tween(700, easing = FastOutSlowInEasing)) }
            },
            interactionSource = interaction,
            modifier = Modifier.align(Alignment.CenterEnd).scale(if (pressed) 0.82f else 1f)
        ) {
            Icon(
                Icons.Default.Settings, "设置",
                modifier = Modifier.rotate(spin.value),
                tint = if (settingsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
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
    selectedPeer: Peer?,
    onSendText: (Peer, String) -> Unit,
    onCancel: (Long) -> Unit,
    onDeleteItem: (Long) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var pendingText by remember { mutableStateOf<String?>(null) }
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
    val sendTextTo = tr(lang, "发送文字到", "傳送文字到", "Send text to", "テキストを送信", "텍스트 보내기", "Enviar texto a", "Envoyer du texte à", "Text senden an", "Enviar texto para", "Отправить текст")
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
    // 多选操作条
    val multiBarLabel = tr(lang, "已选择", "已選擇", "Selected", "選択数", "선택함", "Seleccionados", "Sélection", "Ausgewählt", "Selecionados", "Выбрано")
    val multiShare = tr(lang, "分享", "分享", "Share", "共有", "공유", "Compartir", "Partager", "Teilen", "Compartilhar", "Поделиться")
    val multiClear = tr(lang, "清除", "清除", "Clear", "クリア", "비우기", "Borrar", "Effacer", "Leeren", "Limpar", "Очистить")
    val multiDone = tr(lang, "完成", "完成", "Done", "完了", "완료", "Listo", "Terminé", "Fertig", "Concluir", "Готово")
    val shareChooserTitle = tr(lang, "分享到", "分享到", "Share to", "共有", "공유", "Compartir con", "Partager vers", "Teilen mit", "Compartilhar com", "Поделиться")

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
                        TextButton(onClick = { selIds.clear() }) {
                            Text(multiClear, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) { listItems(transfers, key = { it.id }) {
                            ChatBubble(
                                it, lang, onCancel,
                                onPreview = { previewItem = it },
                                onOpenImage = { item, idx -> imageFull = item to idx },
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
                                onPreviewRef = { item, ref, name -> previewRefItem = Triple(item, ref, name) }
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
                        val sp = selectedPeer
                        if (sp != null) {
                            // 已选中连接设备：直接发给该设备并清空输入
                            onSendText(sp, s)
                            draft = ""
                        } else if (peers.isEmpty()) {
                            android.widget.Toast.makeText(context, toastNoPeer, android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            pendingText = s
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

    // 选择目标设备
    pendingText?.let { text ->
        Dialog(onDismissRequest = { pendingText = null }) {
            Surface(shape = CardShape, color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(sendTextTo, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listItems(peers, key = { it.deviceId }) { peer ->
                            Card(
                                onClick = { onSendText(peer, text); pendingText = null; draft = "" },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center) {
                                        Text(peer.name.take(1), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 文件记录预览对话框（非图片文件/打开用）
    previewItem?.let { FilePreviewDialog(it) { previewItem = null } }
    // 单个文件卡预览：携带具体 ref 与名称
    previewRefItem?.let { (item, ref, name) ->
        FilePreviewDialog(item, ref, name) { previewRefItem = null }
    }

    // 全屏分页图片预览（微信式左右滑动）
    imageFull?.let { (itm, idx) ->
        if (itm != null) FullscreenImagePreview(itm, idx) { imageFull = null }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ChatBubble(
    item: TransferItem, lang: String, onCancel: (Long) -> Unit,
    onPreview: ((TransferItem) -> Unit)? = null,
    onOpenImage: ((TransferItem, Int) -> Unit)? = null,
    onDeleteItem: ((TransferItem) -> Unit)? = null,
    multiSelect: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: ((TransferItem) -> Unit)? = null,
    onEnterMultiSelect: ((TransferItem) -> Unit)? = null,
    onExitMultiSelect: (() -> Unit)? = null,
    onShare: ((TransferItem) -> Unit)? = null,
    onPreviewRef: ((TransferItem, String, String) -> Unit)? = null
) {
    val mine = item.direction == "发送"
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
    val menuSaveTxtTxt = tr(lang, "保存为TXT", "儲存為TXT", "Save as TXT", "TXTとして保存", "TXT로 저장", "Guardar como TXT", "Enregistrer en TXT", "Als TXT speichern", "Salvar como TXT", "Сохранить как TXT")
    val menuMultiTxt = tr(lang, "多选", "多選", "Select", "複数選択", "다중 선택", "Seleccionar", "Sélectionner", "Mehrfachauswahl", "Selecionar", "Выбрать")
    val menuShareTxt = tr(lang, "分享", "分享", "Share", "共有", "공유", "Compartir", "Partager", "Teilen", "Compartilhar", "Поделиться")
    val copiedToast = tr(lang, "已复制", "已複製", "Copied", "コピーしました", "복사됨", "Copiado", "Copié", "Kopiert", "Copiado", "Скопировано")
    val ctx = LocalContext.current

    // 本地有源文件的非文字文件记录即可预览/显示缩略图（不要求完成：微信里失败的图片也显示缩略图）
    val canPreview = !item.isText && item.firstLocalRef.isNotEmpty() && onPreview != null
    // 按类型分开展示：逐个解析本地引用（IO 线程取得真实文件名/大小），图片归入多图网格，其余归入文件卡片。
    // file:// 直接取路径；content:// 查询真实显示名，从而正确区分图片与安装包等（接收/发送逻辑共用）。
    val split: BubbleSplit by produceState(initialValue = BubbleSplit(emptyList(), emptyList()), item.id) {
        if (!item.isText) {
            val base = if (item.localRefs.isNotEmpty()) item.localRefs
                else listOfNotNull(item.localRef.ifEmpty { null })
            value = withContext(Dispatchers.IO) {
                val images = mutableListOf<String>()
                val cards = mutableListOf<FileRefCard>()
                val seen = HashSet<String>()
                for (ref in base) {
                    if (ref.isBlank() || !seen.add(ref)) continue
                    val (n, s) = refNameSize(ctx, ref)
                    if (isImageExt(n)) images.add(ref) else cards.add(FileRefCard(ref, n, s))
                }
                BubbleSplit(images, cards)
            }
        }
    }
    val imageRefs = split.images
    val fileCards = split.cards
    val noPreview = imageRefs.isEmpty() && fileCards.isEmpty()

    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box {
                Column(
                    Modifier.widthIn(max = 300.dp)
                        .combinedClickable(
                            // 普通模式点击无操作；多选模式下点击切换选择
                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) },
                            onLongClickLabel = longHint,
                            // 多选模式下长按任一气泡退出多选；普通模式长按打开操作菜单
                            onLongClick = {
                                if (multiSelect) onExitMultiSelect?.invoke()
                                else menuOpen = true
                            }
                        )
                        .background(bubbleColor.copy(alpha = if (isSelected) 0.65f else 1f), shape)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = if (mine) Alignment.End else Alignment.Start
                ) {
                    if (item.isText) {
                        Text(item.content, color = textColor, fontSize = 15.sp)
                    } else {
                        if (imageRefs.isNotEmpty()) {
                            // 图片记录：同一气泡内以 2 列小网格显示多张图片缩略图（聊天样式），不显示文件名
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in imageRefs.indices step 2) {
                                    val second = if (i + 1 < imageRefs.size) imageRefs[i + 1] else null
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        ChatImageThumb(
                                            imageRefs[i], item.name,
                                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenImage?.invoke(item, i) })
                                        if (second != null) ChatImageThumb(
                                            second, item.name,
                                            onClick = { if (multiSelect) onToggleSelect?.invoke(item) else onOpenImage?.invoke(item, i + 1) })
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                        if (fileCards.isNotEmpty()) {
                            // 非图片文件卡：图片与安装包等分开气泡（微信式文件卡片），接收/发送共用
                            fileCards.forEach { card ->
                                FileCard(
                                    card,
                                    modifier = Modifier.clickable {
                                        if (multiSelect) onToggleSelect?.invoke(item)
                                        else onPreviewRef?.invoke(item, card.ref, card.name)
                                    }
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                        if (noPreview) {
                            Text(item.name, color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                        }
                        val sub = listOfNotNull(
                            if (mine) "$sentTo ${item.target}" else "$fromLabel ${item.target}",
                            item.state,
                            if (item.progress > 0f && item.progress < 1f) item.percent else null
                        ).joinToString(" · ")
                        Text(sub, color = textColor.copy(alpha = 0.75f), fontSize = 11.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    if (cancellable) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            cancelText,
                            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable { onCancel(item.id) }
                        )
                    }
                }
                // 多选选中态：半透明橙色高亮遮罩（半透明遮罩更明显）
                if (isSelected) {
                    Box(Modifier.matchParentSize().clip(shape)
                        .background(selectTint.copy(alpha = 0.22f)))
                }
                // 多选勾：气泡左上角圆形勾（选中=橙色圆底+白勾，未选中=空心圆）
                if (multiSelect) {
                    SelectCheck(
                        selected = isSelected,
                        modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                    )
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
                if (onDeleteItem != null) {
                    menuItems.add(Triple(Icons.Default.Delete, menuDeleteTxt) {
                        onDeleteItem(item); menuOpen = false
                    })
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = Color(0xF21A1A1A),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    menuItems.forEachIndexed { i, (ic, tx, act) ->
                        if (i > 0) {
                            // 项与项之间细分割线（极淡白，紧凑排版）
                            HorizontalDivider(color = Color(0x1FFFFFFF))
                        }
                        BubbleMenuRow(ic, tx, act)
                    }
                }
            }
            // 记录时间（微信式：今天 HH:mm / 昨天 HH:mm / M月d日 HH:mm），气泡外侧小灰字
            if (item.timestamp > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    formatItemTime(lang, item.timestamp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }
}

// ===== 长按菜单里的单个条目（微信式：近纯黑背景、图标与文字 12dp 间距、条目高 48dp）= ====
@Composable
private fun BubbleMenuRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
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

/** 一条传输记录里按类型拆分的结果：图片引用列表 + 非图片文件卡列表。 */
data class BubbleSplit(val images: List<String>, val cards: List<FileRefCard>)

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
                Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
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

// ===== 聊天气泡内单张图片缩略图（2 列小网格的一格） =====
@Composable
private fun ChatImageThumb(ref: String, name: String, onClick: () -> Unit) {
    AsyncImage(
        model = previewImageModel(ref),
        contentDescription = name,
        modifier = Modifier
            .size(130.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentScale = ContentScale.Crop
    )
}

// ===== 全屏分页图片预览（微信式，左右滑动 + 点任意/右上关闭） =====
@Composable
private fun FullscreenImagePreview(item: TransferItem, initialIndex: Int, onDismiss: () -> Unit) {
    if (item.isText) { onDismiss(); return }
    val refs = remember(item.id) { itemImageRefs(item, item.name) }
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
                    AsyncImage(
                        model = previewImageModel(refs[page]),
                        contentDescription = item.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
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
    val isImage = ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "svg")
    val isText = ext in setOf("txt", "log", "json", "xml", "md", "srt", "csv", "ini", "cfg", "conf", "yml", "yaml", "html", "htm", "css", "js", "kt", "java", "c", "cpp", "h", "py", "gradle", "properties")
    val isVideo = ext in setOf("mp4", "mkv", "webm", "3gp", "mov", "avi", "m4v", "ts")
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
                        isVideo && fileExists -> VideoPreview(refUri, name)
                        isAudio && fileExists -> AudioPreview(refUri, name)
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(56.dp))
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

// 视频：AndroidView 包 VideoView（零依赖）
@Composable
private fun VideoPreview(refUri: Uri?, name: String) {
    val context = LocalContext.current
    val videoUri = if (refUri?.scheme == "file") {
        runCatching { FileProvider.getUriForFile(context, "com.orangeway.go.fileprovider", File(refUri.path ?: "")) }.getOrNull()
    } else refUri
    var view: android.widget.VideoView? by remember { mutableStateOf(null) }
    var playing by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { runCatching { view?.stopPlayback() } }
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ -> playing = false; true }
                    setOnPreparedListener { p -> playing = true; p.start() }
                }
            },
            update = { vv ->
                view = vv
                if (videoUri != null) {
                    runCatching { vv.setVideoURI(videoUri) }
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
        )
        Spacer(Modifier.height(8.dp))
        IconButton(
            onClick = {
                if (playing) runCatching { view?.pause() } else runCatching { view?.start() }
                playing = !playing
            },
            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
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
    onClearTransfers: () -> Unit,
    retentionDays: Int,
    onSetRetentionDays: (Int) -> Unit,
    onBack: () -> Unit,
    onSubChanged: (Boolean) -> Unit,
    returnToRoot: Int
) {
    val context = LocalContext.current
    var editName by remember { mutableStateOf(false) }
    var pickTheme by remember { mutableStateOf(false) }
    var pickLanguage by remember { mutableStateOf(false) }
    var pickingDir by remember { mutableStateOf(false) }
    var confirmClearTransfers by remember { mutableStateOf(false) }
    var pickRetention by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var editPin by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var nameInput by remember(currentName) { mutableStateOf(currentName) }
    var pinInput by remember { mutableStateOf("") }
    // 提升滚动状态，避免进入二级页再返回时列表回到顶部
    val settingsListState = rememberLazyListState()

    // 设置页多语言文案
    val tiSettings = tr(lang, "设置", "設定", "Settings", "設定", "설정", "Ajustes", "Paramètres", "Einstellungen", "Configurações", "Настройки")
    val secGeneral = tr(lang, "通用", "通用", "General", "一般", "일반", "General", "Général", "Allgemein", "Geral", "Общие")
    val secReceive = tr(lang, "接收", "接收", "Receive", "受信", "받기", "Recepción", "Réception", "Empfang", "Recebimento", "Приём")
    val secNetwork = tr(lang, "网络", "網路", "Network", "ネットワーク", "네트워크", "Red", "Réseau", "Netzwerk", "Rede", "Сеть")
    val secOther = tr(lang, "其他", "其他", "Other", "その他", "기타", "Otros", "Autres", "Sonstiges", "Outros", "Другое")
    val rowTheme = tr(lang, "主题", "主題", "Theme", "テーマ", "테마", "Tema", "Thème", "Thema", "Tema", "Тема")
    val rowLanguage = tr(lang, "语言", "語言", "Language", "言語", "언어", "Idioma", "Langue", "Sprache", "Idioma", "Язык")
    val rowSaveDir = tr(lang, "保存目录", "儲存目錄", "Save folder", "保存先", "저장 폴더", "Carpeta de guardado", "Dossier de sauvegarde", "Speicherordner", "Pasta de salvamento", "Папка сохранения")
    val toggleAutoSave = tr(lang, "自动保存", "自動儲存", "Auto-save", "自動保存", "자동 저장", "Guardado automático", "Enregistrement auto", "Automatisch speichern", "Salvamento automático", "Автосохранение")
    val toggleAutoAcceptText = tr(lang, "自动接收文本消息", "自動接收文字訊息", "Auto-accept text messages", "テキストメッセージを自動受信", "텍스트 메시지 자동 수신", "Recibir mensajes de texto automáticamente", "Recevoir les messages texte automatiquement", "Textnachrichten automatisch empfangen", "Receber mensagens de texto automaticamente", "Автоприём текстовых сообщений")
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
    val rowAutoCleanup = tr(lang, "自动清理", "自動清理", "Auto-clean", "自動クリーンアップ", "자동 정리", "Limpieza automática", "Nettoyage auto", "Autobereinigung", "Limpeza automática", "Автоочистка")
    val clearTransfersTitle = tr(lang, "清空所有传输记录", "清空所有傳輸記錄", "Clear all transfer records", "すべての転送履歴をクリア", "모든 전송 기록 비우기", "Borrar todos los registros de transferencia", "Effacer tout l'historique de transfert", "Alle Übertragungen löschen", "Limpar todos os registros de transferência", "Очистить все записи передач")
    val clearTransfersMsg = tr(lang, "此操作将删除全部记录，且无法恢复。", "此操作將刪除全部記錄，且無法恢復。", "This will delete all records and cannot be undone.", "この操作で全ての記録が削除され、元に戻せません。", "이 작업은 모든 기록을 삭제하며 되돌릴 수 없습니다.", "Esto borrará todos los registros y no se puede deshacer.", "Cela supprimera tous les enregistrements, sans retour possible.", "Dies löscht alle Einträge und ist nicht rückgängig zu machen.", "Isso excluirá todos os registros e não poderá ser desfeito.", "Это удалит все записи безвозвратно.")
    val btnConfirm = tr(lang, "确定", "確定", "OK", "OK", "확인", "Aceptar", "OK", "OK", "OK", "ОК")
    val retentionNone = tr(lang, "不自动清理（默认）", "不自動清理（預設）", "No auto-clean (default)", "自動クリーンアップなし（初期値）", "자동 정리 안 함(기본)", "Sin limpieza automática (predeterminado)", "Pas de nettoyage auto (par défaut)", "Keine Autobereinigung (Standard)", "Sem limpeza automática (padrão)", "Без автоочистки (по умолчанию)")
    val retentionPrefix = tr(lang, "保留最近", "保留最近", "Keep last", "直近", "최근", "Conservar últimos", "Garder", "Behalten", "Manter", "Хранить")
    val retentionDaysUnit = tr(lang, "天", "天", " days", "日間", "일", " días", " jours", " Tage", " dias", " дн.")
    val clearedAllToast = tr(lang, "已清空传输记录", "已清空傳輸記錄", "Transfer records cleared", "転送履歴をクリアしました", "전송 기록을 비웠습니다", "Registros de transferencia borrados", "Historique de transfert effacé", "Übertragungen gelöscht", "Registros de transferência limpos", "Записи передач очищены")
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
    fun retentionLabel(d: Int): String = if (d <= 0) retentionNone else "$retentionPrefix $d$retentionDaysUnit"

    val subState = when {
        pickingDir -> 2; pickLanguage -> 3; showAbout -> 4; showPrivacy -> 5; else -> 1
    }
    // 是否处于二级子页（完整子页或弹窗）——上报给主界面，用于设置键的二段式行为
    val subActive = subState != 1 || editName || pickTheme || editPin || confirmClearTransfers || pickRetention
    LaunchedEffect(subActive) { onSubChanged(subActive) }
    // 响应主界面「返回设置主界面」的外部请求（设置键二段式）：把当前子页/弹窗全部置回主列表
    LaunchedEffect(returnToRoot) {
        if (returnToRoot > 0) {
            editName = false; pickTheme = false; pickLanguage = false
            pickingDir = false; showAbout = false; editPin = false; showPrivacy = false
            confirmClearTransfers = false; pickRetention = false
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

            item { SectionLabel(secNetwork) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingsRow(rowDeviceName, currentName) { editName = true }
                }
            }

            item { SectionLabel(secTransfers) }
            item {
                Card(shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    SettingsRow(rowClearTransfers, null) { confirmClearTransfers = true }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowAutoCleanup, retentionLabel(retentionDays)) { pickRetention = true }
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
                    SettingsRow(rowCheckUpdate, "v${BuildConfig.VERSION_NAME}") { openUrl(context, "$OG_GITHUB_REPO/releases") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(rowFeedback, "GitHub Issues") { openUrl(context, "$OG_GITHUB_REPO/issues/new") }
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
                    android.widget.Toast.makeText(context, clearedAllToast, android.widget.Toast.LENGTH_SHORT).show()
                }) { Text(btnConfirm, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearTransfers = false }) { Text(btnCancel) }
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
                    val opts = listOf(0 to retentionNone, 1 to retentionLabel(1), 7 to retentionLabel(7), 30 to retentionLabel(30))
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
    onClick: (() -> Unit)? = null
) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = onClick != null, onClick = onClick ?: {})
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
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
    val rowType = tr(lang, "类型", "類型", "Type", "種類", "유형", "Tipo", "Type", "Typ", "Tipo", "Тип")
    val typeValue = tr(lang, "局域网点对点传输", "區域網點對點傳輸", "LAN peer-to-peer transfer", "LANピアツーピア転送", "LAN 피어 투 피어 전송", "Transferencia P2P en LAN", "Transfert P2P en LAN", "LAN Peer-to-Peer-Übertragung", "Transferência P2P em LAN", "Локальная P2P-передача")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ChevronRight, null, modifier = Modifier.rotate(180f))
            }
            Text(tiAbout, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            AsyncImage(model = R.raw.og_logo_orange, contentDescription = "OrangeGO",
                modifier = Modifier.size(88.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.height(16.dp))
            Text("OrangeGO", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("$rowVersion v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Spacer(Modifier.height(32.dp))
            Card(shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                SettingsRow(rowVersion, "v${BuildConfig.VERSION_NAME}")
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 16.dp))
                SettingsRow(rowType, typeValue)
            }
            Spacer(Modifier.height(32.dp))
            Text("OrangeGO v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("© OrangeWay", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
private data class Picked(val uri: Uri, val name: String, val isImage: Boolean)

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
            // 清空选择：仅在有已选内容时显示，红色文字，与"发送内容"一行水平对齐
            if (contents.isNotEmpty()) {
                TextButton(onClick = { showClearConfirm = true }) {
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
        // 已选 uri 集合，供各相册统计选中数量
        val selectedUris = remember(contents) { contents.map { it.uri }.toSet() }
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
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { album = null }) { Text(backText) }
                Text(current?.name ?: "", fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${current?.items?.size ?: 0} $countItems",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            if (current != null) {
                Box(Modifier.fillMaxSize()) {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(96.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gridItems(current.items, key = { it.uri.toString() }) { item ->
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
            AsyncImage(
                model = album.items.first().uri, contentDescription = album.name,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
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
    Box(
        Modifier.aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = item.uri, contentDescription = item.name,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
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
            Text(dir.absolutePath ?: internalStorage, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif")
private val APK_EXTS = setOf("apk")

private fun isImageExt(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT)
    return ext in IMAGE_EXTS
}

private fun isImageFile(f: File): Boolean = isImageExt(f.name)

/** 判定某个本地引用是否为图片：file:// 用路径文件名扩展名；content:// 用记录的 fallbackName。 */
private fun isRefImage(ref: String, fallbackName: String): Boolean {
    val u = runCatching { Uri.parse(ref) }.getOrNull() ?: return false
    if (u.scheme == "file") {
        return isImageExt((u.path ?: "").substringAfterLast('/'))
    }
    return isImageExt(fallbackName)
}

/** 提取一条传输记录里所有可预览的图片本地引用（多张时按序返回）；无 localRefs 时退化为旧的 localRef。 */
private fun itemImageRefs(item: TransferItem, fallbackName: String): List<String> {
    val refs = if (item.localRefs.isNotEmpty()) item.localRefs
        else listOfNotNull(item.localRef.ifEmpty { null })
    return refs.filter { it.isNotBlank() && isRefImage(it, fallbackName) }
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
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(appsLoadingText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        } else if (apps.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyState(Icons.Default.Devices, emptyApps) }
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
private fun DevicesScreen(peers: List<Peer>, selectedPeer: Peer?, onSelect: (Peer?) -> Unit) {
    val lang by OgoLang.code.collectAsState()
    val tiChooseDevice = tr(lang, "选择目标设备", "選擇目標裝置", "Select target device", "転送先を選択", "대상 기기 선택", "Seleccionar dispositivo", "Sélectionner l'appareil", "Zielgerät wählen", "Selecionar dispositivo", "Выберите целевое устройство")
    val tiConnected = tr(lang, "已连接：", "已連接：", "Connected: ", "接続中：", "연결됨: ", "Conectado: ", "Connecté : ", "Verbunden: ", "Conectado: ", "Подключено: ")
    val tiNoPeers = tr(lang, "未发现附近设备", "未發現附近裝置", "No nearby devices found", "近くのデバイスが見つかりません", "근처 기기를 찾을 수 없습니다", "No se encontraron dispositivos cercanos", "Aucun appareil à proximité", "Keine Geräte in der Nähe", "Nenhum dispositivo encontrado", "Устройств поблизости не найдено")
    val tiNoPeersSub = tr(lang, "确认对端已打开 OrangeGO 并连到同一网络", "請確認對端已開啟 OrangeGO 並連到同一網路", "Make sure the peer has OrangeGO open on the same network", "相手が同一ネットワークでOrangeGOを開いていることを確認してください", "상대방이 같은 네트워크에서 OrangeGO를 열었는지 확인하세요", "Asegúrate de que el otro dispositivo tenga OrangeGO abierto en la misma red", "Vérifiez que l'autre appareil a OrangeGO ouvert sur le même réseau", "Stellen Sie sicher, dass das Gegenüber OrangeGO im selben Netzwerk geöffnet hat", "Certifique-se de que o outro dispositivo tem OrangeGO aberto na mesma rede", "Убедитесь, что на другом устройстве открыт OrangeGO в той же сети")
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Text(
            if (selectedPeer == null) tiChooseDevice else "$tiConnected${selectedPeer.name}",
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
                        DeviceTile(peer, lang, isSelected = selectedPeer?.key == peer.key) {
                            // 点击设备 = 选中连接（可再次点击取消）；发送统一走中央悬浮发送键
                            onSelect(if (selectedPeer?.key == peer.key) null else peer)
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

@Composable
private fun DeviceTile(peer: Peer, lang: String, isSelected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val onlineText = tr(lang, "在线", "在線", "Online", "オンライン", "온라인", "En línea", "En ligne", "Online", "Online", "В сети")
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(0.5f, 900f), label = "tile")
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)
                    ), contentAlignment = Alignment.Center
                ) {
                    Text(peer.initial, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(22.dp).background(
                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape
                    ), contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(peer.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(peer.ip, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
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

// ===== 未选设备发送时的「可连接设备」选择弹窗 =====
@Composable
private fun PeerPickerDialog(peers: List<Peer>, onPick: (Peer) -> Unit, onDismiss: () -> Unit) {
    val lang by OgoLang.code.collectAsState()
    val title = tr(lang, "选择可连接设备", "選擇可連接裝置", "Select a device to send", "送信先デバイスを選択", "보낼 기기 선택", "Selecciona un dispositivo", "Choisir un appareil", "Gerät zum Senden wählen", "Selecione um dispositivo", "Выберите устройство")
    val empty = tr(lang, "暂无可用设备", "暫無可用裝置", "No available devices", "利用可能なデバイスがありません", "사용 가능한 기기가 없습니다", "No hay dispositivos disponibles", "Aucun appareil disponible", "Keine verfügbaren Geräte", "Nenhum dispositivo disponível", "Нет доступных устройств")
    val btnCancel = tr(lang, "取消", "取消", "Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen", "Cancelar", "Отмена")
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
                            Card(
                                onClick = { onPick(peer) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                                    Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(btnCancel) }
                }
            }
        }
    }
}