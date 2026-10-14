package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Text
import yy.daniel.yyplayerforandroid.playback.*

private enum class VideoPanel { TOOLS, SPEED, CUSTOM_SPEED, TRACKS, PICTURE, QUEUE, INFO, LOOP, SYNC, CHAPTERS }

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun VideoPlayerPage(backend: AndroidMedia3PlaybackBackend, snapshot: PlaybackSnapshot, reduceMotion: Boolean,
    onOrientation: () -> Unit, onBack: () -> Unit, surfaceActive: Boolean,
    audioOutput: yy.daniel.yyplayerforandroid.audio.AudioOutputState?, inputActive: Boolean = true) {
    val current = snapshot.current ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reduced = rememberMusicReduceMotion(reduceMotion)
    var panel by remember { mutableStateOf<VideoPanel?>(null) }
    val panelHistory = remember { mutableStateListOf<VideoPanel>() }
    fun openPanel(next: VideoPanel) {
        val previous = panel
        if (previous == next) return
        if (previous == null) {
            panelHistory.clear()
            // Direct toolbar shortcuts still have the tools page as their parent.
            if (next != VideoPanel.TOOLS) panelHistory.add(VideoPanel.TOOLS)
        } else panelHistory.add(previous)
        panel = next
    }
    val navigateBack: (() -> Unit)? = if (panel != null && panelHistory.isNotEmpty()) {
        { if (panelHistory.isNotEmpty()) panel = panelHistory.removeAt(panelHistory.lastIndex) }
    } else null
    var visible by remember(current.uri) { mutableStateOf(true) }
    var locked by rememberSaveable(current.uri.toString()) { mutableStateOf(false) }
    var boost by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var reset by remember { mutableIntStateOf(0) }
    var transformed by remember { mutableStateOf(false) }
    var fit by rememberSaveable(current.uri.toString()) { mutableStateOf(VideoFit.FIT) }
    var subtitleSize by rememberSaveable { mutableFloatStateOf(20f) }
    var message by remember { mutableStateOf<String?>(null) }
    var viewport by remember { mutableStateOf<VideoViewport?>(null) }
    var pendingShot by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var subtitleTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var subtitleGeneration by rememberSaveable { mutableLongStateOf(-1L) }
    var importing by remember { mutableStateOf(false) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val bitmap = pendingShot; pendingShot = null
        if (bitmap != null) scope.launch {
            try {
                if (uri != null) {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output))
                        } ?: error("无法写入所选位置")
                    }
                    message = "截图已保存"
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "保存失败：${e.message}" }
            finally { bitmap.recycle() }
        }
    }
    DisposableEffect(Unit) { onDispose { pendingShot?.recycle() } }
    val subtitlePicker = rememberLauncherForActivityResult(MusicLyricPicker()) { grant ->
        if (grant != null) scope.launch {
            importing = true
            try {
                grant.persist(context)
                val sub = importVideoSubtitle(context, grant.uri, snapshot.videoEngine == VideoEngine.MPV)
                val target = subtitleTarget?.let(android.net.Uri::parse)
                if (target == null || !backend.attachSubtitle(sub, target, subtitleGeneration)) {
                    withContext(Dispatchers.IO) { java.io.File(sub.uri.path!!).delete() }
                    message = "视频已切换，请为当前视频重新选择字幕"
                } else message = if (snapshot.videoEngine == VideoEngine.MPV) "正在载入 ${sub.name}" else "已载入 ${sub.name}"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "字幕导入失败：${e.message}" }
            finally { importing = false }
        }
    }
    LaunchedEffect(visible, snapshot.isPlaying, panel, interaction, dragging, locked, surfaceActive) {
        if (visible && snapshot.isPlaying && panel == null && !dragging && !locked && surfaceActive) {
            delay(3500); visible = false
        }
    }
    LaunchedEffect(message) { if (message != null) { delay(4500); message = null } }
    LaunchedEffect(snapshot.subtitleName) { snapshot.subtitleName?.let { message = "已载入 $it" } }
    LaunchedEffect(current.uri) { panel = null; boost = false }
    BackHandler(locked) { locked = false; visible = true; interaction++ }
    val controls = !locked && panel == null && (visible || !snapshot.isPlaying || snapshot.error != null)
    val extended = snapshot.videoEngine == VideoEngine.MPV
    LaunchedEffect(extended, subtitleSize, controls) {
        if (extended) backend.setVideoSubtitleAppearance(subtitleSize, if (controls) .28f else .08f)
    }
    Box(Modifier.fillMaxSize().background(Color.Black).testTag("video-player")
        .then(if (panel != null) Modifier.clearAndSetSemantics { } else Modifier)) {
        VideoSurface(Modifier.fillMaxSize().testTag("video-surface"), backend.exoPlayer(), backend, snapshot, reset,
            onTap = { visible = !visible; interaction++ }, onBoostChanged = { boost = it },
            surfaceActive = surfaceActive, gesturesEnabled = inputActive && !locked && panel == null,
            fit = fit, subtitleSize = subtitleSize, subtitleBottomPadding = if (controls) .28f else .08f,
            onTransformChanged = { transformed = it }, onViewReady = { viewport = it })
        AnimatedVisibility(controls, enter = fadeIn(tween(if (reduced) 0 else 180)), exit = fadeOut(tween(if (reduced) 0 else 180))) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent)))
                    .safeDrawingPadding().padding(start = 8.dp, end = 12.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    HyperIconButton(PlayerIcons.Back, "返回媒体库", onBack, tint = Color.White)
                    Text(displayTitle(current.title), Modifier.weight(1f).padding(horizontal = 8.dp), color = Color.White,
                        fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HyperIconButton(PlayerIcons.More, "播放工具", { openPanel(VideoPanel.TOOLS) }, tint = Color.White)
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .88f))))
                    .navigationBarsPadding().displayCutoutPadding().padding(start = 24.dp, end = 24.dp, top = 30.dp, bottom = 10.dp)) {
                    if (snapshot.error != null) PlaybackError(snapshot, backend)
                    SeekControl(snapshot, backend, true, reduced) { dragging = it; interaction++ }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HyperIconButton(if (snapshot.isPlaying) PlayerIcons.Pause else PlayerIcons.Play,
                            if (snapshot.isPlaying) "暂停" else "播放", { backend.togglePlayPause(); interaction++ }, tint = Color.White)
                        VideoChip("−10秒", { backend.seekBy(-10_000); interaction++ })
                        VideoChip("+10秒", { backend.seekBy(10_000); interaction++ })
                        Spacer(Modifier.weight(1f))
                        HyperIconButton(PlayerIcons.Rotate, "屏幕方向", onOrientation, tint = Color.White)
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        VideoChip(speedLabel(snapshot.speed), { openPanel(VideoPanel.SPEED) })
                        VideoChip("字幕 / 音轨", { openPanel(VideoPanel.TRACKS) })
                        VideoChip("画面", { openPanel(VideoPanel.PICTURE) })
                        VideoChip("队列", { openPanel(VideoPanel.QUEUE) })
                        VideoChip("锁定", { locked = true; visible = false })
                    }
                }
            }
        }
        // Kept outside the auto-hiding controls; reset stays available after any two-finger transform.
        if (!locked && (transformed || fit != VideoFit.FIT)) VideoChip("还原", { reset++; fit = VideoFit.FIT; interaction++ },
            Modifier.align(Alignment.CenterEnd).displayCutoutPadding().padding(16.dp).testTag("video-reset"), capsule = true)
        if (locked) VideoChip("解锁", { locked = false; visible = true; interaction++ },
            Modifier.align(Alignment.CenterStart).displayCutoutPadding().padding(16.dp), capsule = true)
        if (boost || snapshot.loopEndMs != null) Text(if (boost) "3× 快进中" else "A–B 循环", Modifier.align(Alignment.TopCenter)
            .statusBarsPadding().padding(top = 64.dp).background(Color.Black.copy(alpha = .6f), CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp)
        if (snapshot.phase == PlaybackPhase.BUFFERING) Text("正在加载…", Modifier.align(Alignment.Center), color = Color.White)
        if (message != null || importing) Text(if (importing) "正在读取字幕…" else message.orEmpty(),
            Modifier.align(Alignment.Center).padding(32.dp).background(Color.Black.copy(alpha = .8f), CardShape).padding(18.dp),
            color = Color.White, fontSize = 15.sp)
    }
    VideoSettingsHost(panel, reduced, onDismiss = { panel = null }, onNavigateBack = navigateBack) { shownPanel ->
        SpeedDialog(snapshot.speed, backend::setSpeed, shownPanel == VideoPanel.SPEED) { panel = null }
        VideoSpeedSettings(snapshot.speed, backend::setSpeed,
            shownPanel == VideoPanel.CUSTOM_SPEED) { panel = null }
        MediaInfoDialog(snapshot, shownPanel == VideoPanel.INFO, audioOutput) { panel = null }
        SelectionDialog("播放队列 · ${snapshot.queue.size}", shownPanel == VideoPanel.QUEUE,
            snapshot.queue.map { DropdownItem(displayTitle(it.title)) }, snapshot.queueIndex,
            onSelect = backend::jumpTo, onDismiss = { panel = null })
        HyperDialog("播放工具", { panel = null }, show = shownPanel == VideoPanel.TOOLS, buttonText = "完成") {
            HyperSpinnerPreference("视频后端", items = VideoEngine.entries.map { DropdownItem(it.label) },
                selectedIndex = snapshot.videoEngine.ordinal, onSelectedIndexChange = { backend.setVideoEngine(VideoEngine.entries[it]) })
            HyperSettingRow("播放倍速", "0.1×–8× · 每次调节 0.1×", value = speedLabel(snapshot.speed),
                onClick = { openPanel(VideoPanel.CUSTOM_SPEED) })
            HyperSettingRow("字幕与音轨", onClick = { openPanel(VideoPanel.TRACKS) })
            if (extended) {
                HyperSettingRow("章节", if (snapshot.chapters.isEmpty()) "当前文件没有章节" else "共 ${snapshot.chapters.size} 章", onClick = { openPanel(VideoPanel.CHAPTERS) })
                HyperSettingRow("音画与字幕同步", onClick = { openPanel(VideoPanel.SYNC) })
                HyperSettingRow("上一帧", "暂停并精确回退一帧", onClick = { backend.stepFrame(false); panel = null; visible = true })
                HyperSettingRow("下一帧", "暂停并精确前进一帧", onClick = { backend.stepFrame(true); panel = null; visible = true })
            } else HyperSettingRow("更多视频功能", "切换 libmpv 可使用章节、逐帧、同步和完整 ASS")
            HyperSettingRow("画面与方向", onClick = { openPanel(VideoPanel.PICTURE) })
            HyperSettingRow("A–B 片段循环", onClick = { openPanel(VideoPanel.LOOP) })
            HyperSpinnerPreference("播放顺序", items = PlayOrder.entries.map { DropdownItem(it.label) },
                selectedIndex = snapshot.playOrder.ordinal, onSelectedIndexChange = { backend.setPlayOrder(PlayOrder.entries[it]) })
            HyperSettingRow("保存视频截图", if (extended) "保存当前画面及字幕，不含控制按钮" else "保存当前画面，不含字幕和控制按钮", onClick = {
                val texture = viewport?.texture
                if (texture == null || !texture.isAvailable || snapshot.videoWidth == 0) message = "画面尚未就绪"
                else {
                    val scale = (1920f / maxOf(texture.width, texture.height)).coerceAtMost(1f)
                    pendingShot = texture.getBitmap((texture.width * scale).toInt().coerceAtLeast(1), (texture.height * scale).toInt().coerceAtLeast(1))
                    if (pendingShot != null) { panel = null; screenshotPicker.launch("YYPlayer-${System.currentTimeMillis()}.png") }
                    else message = "暂时无法获取画面"
                }
            })
            HyperSettingRow("媒体信息", onClick = { openPanel(VideoPanel.INFO) })
        }
        HyperDialog("画面", { panel = null }, show = shownPanel == VideoPanel.PICTURE, buttonText = "完成") {
            if (extended) HyperSpinnerPreference("平滑色阶", items = listOf(DropdownItem("关闭"), DropdownItem("开启")),
                selectedIndex = if (snapshot.deband) 1 else 0, onSelectedIndexChange = { backend.setVideoDeband(it == 1) })
            HyperSpinnerPreference("画面比例", items = VideoFit.entries.map { DropdownItem(it.label) }, selectedIndex = fit.ordinal,
                onSelectedIndexChange = { fit = VideoFit.entries[it]; reset++ })
            HyperSettingRow("屏幕方向", onClick = { panel = null; onOrientation() })
            HyperSettingRow("还原画面", "恢复比例、缩放、旋转和位置", onClick = { reset++; fit = VideoFit.FIT; panel = null })
            val decoderModes = DecoderMode.entries.filter { !extended || it != DecoderMode.STRICT_HARDWARE }
            HyperSpinnerPreference("解码方式", items = decoderModes.map { DropdownItem(it.title, summary = decoderSummary(it)) },
                selectedIndex = decoderModes.indexOf(snapshot.decoderMode).coerceAtLeast(0), onSelectedIndexChange = { backend.setDecoderMode(decoderModes[it]) })
        }
        HyperDialog("字幕与音轨", { panel = null }, show = shownPanel == VideoPanel.TRACKS, buttonText = "完成") {
            listOf(C.TRACK_TYPE_TEXT to "字幕", C.TRACK_TYPE_AUDIO to "音轨").forEach { (type, title) ->
                val tracks = snapshot.tracks.filter { it.type == type && it.supported }
                HyperSpinnerPreference(title, items = listOf(DropdownItem("关闭")) + tracks.map { DropdownItem(it.label) },
                    selectedIndex = (tracks.indexOfFirst { it.selected } + 1).coerceAtLeast(0),
                    onSelectedIndexChange = { choice ->
                        val track = tracks.getOrNull(choice - 1)
                        backend.selectTrack(type, track?.group, track?.track ?: 0, snapshot.mediaGeneration)
                    })
                if (snapshot.tracks.any { it.type == type && !it.supported }) HyperSettingRow("部分$title 暂不可用", "设备无法解码该轨道格式")
            }
            HyperSettingRow("导入字幕", snapshot.subtitleName ?: "SRT、VTT、ASS / SSA", onClick = {
                if (!importing) { subtitleTarget = current.uri.toString(); subtitleGeneration = snapshot.mediaGeneration; panel = null; subtitlePicker.launch(Unit) }
            })
            if (snapshot.subtitleName != null) HyperSettingRow("移除外置字幕", onClick = { backend.removeExternalSubtitle() })
            HyperSpinnerPreference("字幕字号", items = listOf("小", "标准", "大", "特大").map { DropdownItem(it) },
                selectedIndex = listOf(16f, 20f, 24f, 30f).indexOf(subtitleSize),
                onSelectedIndexChange = { subtitleSize = listOf(16f, 20f, 24f, 30f)[it] })
            if (extended) {
                val fonts = listOf("sans-serif", "serif", "monospace")
                HyperSpinnerPreference("字幕字体", items = listOf("无衬线", "衬线", "等宽").map { DropdownItem(it) },
                    selectedIndex = fonts.indexOf(snapshot.subtitleFont).coerceAtLeast(0),
                    onSelectedIndexChange = { backend.setVideoSubtitleFont(fonts[it], snapshot.overrideAssStyle) })
                HyperSpinnerPreference("ASS 原有样式", items = listOf(DropdownItem("保留"), DropdownItem("使用上方字体与字号")),
                    selectedIndex = if (snapshot.overrideAssStyle) 1 else 0,
                    onSelectedIndexChange = { backend.setVideoSubtitleFont(snapshot.subtitleFont, it == 1) })
                HyperSettingRow("字幕同步", "提前或延后字幕出现时间", onClick = { openPanel(VideoPanel.SYNC) })
                HyperSettingRow("字幕呈现", "libass 绘制 ASS 样式、定位及动画；保留原有样式时，字号由字幕文件决定。")
            } else HyperSettingRow("字幕兼容性", "当前为基础字幕呈现；播放工具中可切换 libmpv 使用完整 ASS。")
        }
        HyperDialog("A–B 片段循环", { panel = null }, show = shownPanel == VideoPanel.LOOP, buttonText = "完成") {
            HyperSettingRow("起点 A", snapshot.loopStartMs?.let(::formatTime) ?: "未设置")
            HyperSettingRow("终点 B", snapshot.loopEndMs?.let(::formatTime) ?: "未设置；需晚于 A 至少 0.5 秒")
            HyperSettingRow(when { snapshot.loopEndMs != null -> "关闭片段循环"; snapshot.loopStartMs != null -> "将当前位置设为 B"; else -> "将当前位置设为 A" },
                onClick = { backend.markLoop(); panel = null; visible = true; interaction++ })
            if (snapshot.loopStartMs != null) HyperSettingRow("清除标记", onClick = backend::clearLoop)
        }
        SelectionDialog("章节", shownPanel == VideoPanel.CHAPTERS,
            snapshot.chapters.map { DropdownItem(it.title, summary = formatTime(it.positionMs)) }, snapshot.chapterIndex,
            onSelect = { snapshot.chapters.getOrNull(it)?.let { chapter -> backend.seekTo(chapter.positionMs) } }, onDismiss = { panel = null })
        HyperDialog("音画与字幕同步", { panel = null }, show = shownPanel == VideoPanel.SYNC, buttonText = "完成") {
            listOf(true to "字幕", false to "音频").forEach { (subtitle, title) ->
                val ms = if (subtitle) snapshot.subtitleDelayMs else snapshot.audioDelayMs
                HyperSettingRow("$title 偏移", if (ms == 0L) "已同步 · 0 毫秒" else "${if (ms > 0) "延后" else "提前"} ${kotlin.math.abs(ms)} 毫秒")
                HyperSettingRow("$title 提前 100 毫秒", onClick = { backend.setVideoDelay(subtitle, ms - 100) })
                HyperSettingRow("$title 延后 100 毫秒", onClick = { backend.setVideoDelay(subtitle, ms + 100) })
                HyperSettingRow("还原$title 同步", onClick = { backend.setVideoDelay(subtitle, 0) })
            }
        }
    }
}

@Composable
private fun VideoChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, capsule: Boolean = false) {
    Box(modifier.heightIn(min = 48.dp).clip(CircleShape)
        .background(if (capsule) Color(0xCC262626) else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
