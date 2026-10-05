package yy.daniel.yyplayerforandroid.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.theme.LocalDismissState
import top.yukonga.miuix.kmp.anim.DecelerateEasing
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.theme.YYPlayerTheme

private enum class PlayerMenu { SPEED, QUEUE, INFO, VIDEO }

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerScreen(backend: AndroidMedia3PlaybackBackend, snapshot: PlaybackSnapshot, reduceMotion: Boolean,
    onOpenOrientation: () -> Unit, favorite: Boolean,
    onFavorite: () -> Unit, onBack: () -> Unit, surfaceActive: Boolean = true) {
    var menu by remember { mutableStateOf<PlayerMenu?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }
    val current = snapshot.current
    if (current == null) {
        HyperPage("正在播放", onBack) { padding -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("选择一首音乐或一个视频", fontSize = 20.sp); Spacer(Modifier.height(24.dp)); Button(onClick = onBack) { Text("返回媒体库") }
        } }
        return
    }
    @Composable fun dialogs() {
        // Always mount the native popup nodes. Removing a node at show=false cuts off its exit spring.
        SpeedDialog(snapshot.speed, backend::setSpeed, show = menu == PlayerMenu.SPEED) { menu = null }
        MediaInfoDialog(snapshot, show = menu == PlayerMenu.INFO) { menu = null }
        SelectionDialog("播放队列 · ${snapshot.queue.size}", menu == PlayerMenu.QUEUE,
            snapshot.queue.map { DropdownItem(displayTitle(it.title)) }, snapshot.queueIndex,
            onSelect = { backend.play(snapshot.queue, it) }, onDismiss = { menu = null })
        HyperDialog("播放设置", { menu = null }, show = menu == PlayerMenu.VIDEO) {
            HyperSpinnerPreference(title = "解码方式", items = DecoderMode.entries.map { DropdownItem(it.title, summary = decoderSummary(it)) },
                selectedIndex = snapshot.decoderMode.ordinal, onSelectedIndexChange = { backend.setDecoderMode(DecoderMode.entries[it]) })
            HyperSettingRow("屏幕方向", onClick = { menu = null; onOpenOrientation() })
            HyperSpinnerPreference(title = "播放速度", items = SpeedChoices.map { DropdownItem(speedLabel(it)) },
                selectedIndex = SpeedChoices.indexOf(snapshot.speed), onSelectedIndexChange = { backend.setSpeed(SpeedChoices[it]) })
            HyperSettingRow("重置画面", "恢复缩放、旋转与位置", onClick = { resetKey++; menu = null })
            HyperSettingRow("媒体信息", onClick = { menu = PlayerMenu.INFO })
            HyperSettingRow("播放队列", value = "${snapshot.queue.size} 项", onClick = { menu = PlayerMenu.QUEUE })
        }
    }
    if (snapshot.isVideo) {
        // Controls remain readable over video regardless of the library theme.
        YYPlayerTheme(ColorSchemeMode.Dark, MiuixTheme.colorScheme.primary) {
            var visible by remember(current.uri) { mutableStateOf(true) }
            var boost by remember(current.uri) { mutableStateOf(false) }
            var interaction by remember { mutableIntStateOf(0) }
            LaunchedEffect(visible, snapshot.isPlaying, menu, interaction) {
                if (visible && snapshot.isPlaying && menu == null) { delay(3500); visible = false }
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoSurface(Modifier.fillMaxSize(), backend.exoPlayer(), backend, snapshot, resetKey,
                    onTap = { visible = !visible; interaction++ }, onBoostChanged = { boost = it }, surfaceActive = surfaceActive)
                AnimatedVisibility(visible = visible || !snapshot.isPlaying, enter = fadeIn(tween(if (reduceMotion) 0 else 300, easing = DecelerateEasing(1.5f))), exit = fadeOut(tween(if (reduceMotion) 0 else 250, easing = DecelerateEasing(1.5f)))) {
                    Box(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                            .statusBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically) {
                            HyperIconButton(PlayerIcons.Back, "返回媒体库", onBack, tint = Color.White)
                            Text(displayTitle(current.title), Modifier.weight(1f).padding(horizontal = 8.dp), fontSize = 17.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            HyperIconButton(PlayerIcons.More, "播放设置", { menu = PlayerMenu.VIDEO }, tint = Color.White)
                        }
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                            .navigationBarsPadding().padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 12.dp)) {
                            if (snapshot.error != null) PlaybackError(snapshot, backend)
                            SeekControl(snapshot, backend, video = true)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                HyperIconButton(if (snapshot.isPlaying) PlayerIcons.Pause else PlayerIcons.Play, if (snapshot.isPlaying) "暂停" else "播放", { backend.togglePlayPause(); interaction++ }, tint = Color.White)
                                HyperIconButton(PlayerIcons.Previous, "上一个视频", { backend.previous(); interaction++ }, tint = Color.White)
                                HyperIconButton(PlayerIcons.Next, "下一个视频", { backend.next(); interaction++ }, tint = Color.White)
                                Spacer(Modifier.weight(1f))
                                Text(speedLabel(snapshot.speed), Modifier.clickable { menu = PlayerMenu.SPEED }.padding(16.dp), color = Color.White, fontSize = 15.sp)
                                HyperIconButton(PlayerIcons.Rotate, "屏幕方向", onOpenOrientation, tint = Color.White)
                            }
                        }
                    }
                }
                if (boost) Text("3× 快进中", Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp)
                    .background(Color.Black.copy(alpha = .55f), CircleShape).padding(horizontal = 18.dp, vertical = 10.dp), color = Color.White, fontSize = 14.sp)
                if (snapshot.phase == PlaybackPhase.BUFFERING) Text("正在加载…", Modifier.align(Alignment.Center), color = Color.White, fontSize = 15.sp)
            }
            dialogs()
        }
    } else {
        // PlayerActivity redirects audio to MainActivity; no second music page or transition here.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在返回音乐详情…", color = Muted) }
    }
}

@Composable
private fun SeekControl(snapshot: PlaybackSnapshot, backend: PlaybackBackend, video: Boolean = false) {
    var dragging by remember(snapshot.current?.uri) { mutableStateOf(false) }
    var value by remember(snapshot.current?.uri) { mutableFloatStateOf(snapshot.progress) }
    Slider(value = if (dragging) value else snapshot.progress, onValueChange = { value = it; dragging = true },
        onValueChangeFinished = { if (snapshot.durationMs > 0) backend.seekTo((value * snapshot.durationMs).toLong()); dragging = false }, enabled = snapshot.durationMs > 0)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val color = if (video) Color.White.copy(alpha = .75f) else Muted
        Text(formatTime(if (dragging) (value * snapshot.durationMs).toLong() else snapshot.positionMs), fontSize = 12.sp, color = color)
        Text(formatTime(snapshot.durationMs), fontSize = 12.sp, color = color)
    }
}

@Composable
private fun PlaybackError(snapshot: PlaybackSnapshot, backend: PlaybackBackend) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(snapshot.error.orEmpty(), Modifier.weight(1f), color = MiuixTheme.colorScheme.error, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("重试", Modifier.clickable { backend.play(snapshot.queue, snapshot.queueIndex.coerceAtLeast(0)) }.padding(12.dp), color = MiuixTheme.colorScheme.primary, fontSize = 14.sp)
    }
}

internal fun speedLabel(speed: Float) = "${if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()}×"

internal val SpeedChoices = listOf(.5f, 1f, 1.25f, 1.5f, 2f, 3f)

internal fun decoderSummary(mode: DecoderMode) = when (mode) {
    DecoderMode.AUTO -> "由系统选择可用解码器"
    DecoderMode.PREFER_HARDWARE -> "优先使用硬件，必要时回退"
    DecoderMode.STRICT_HARDWARE -> "没有硬件解码器时保持当前播放"
    DecoderMode.SOFTWARE -> "使用可用的平台软件解码器"
}

@Composable
internal fun SelectionDialog(title: String, show: Boolean, items: List<DropdownItem>, selectedIndex: Int,
    onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    // Same layout/rows as upstream WindowDropdownDialog, with the surface override
    // its public API lacks. Keep native selection feedback and native window motion.
    val haptics = LocalHapticFeedback.current
    WindowDialog(show = show, title = title, backgroundColor = MiuixTheme.colorScheme.surface,
        insideMargin = DpSize(0.dp, 24.dp), onDismissRequest = onDismiss) {
        val dismiss = LocalDismissState.current
        Layout(content = {
            LazyColumn {
                items(items.size) { index ->
                    DropdownImpl(item = items[index], optionSize = items.size,
                        isSelected = selectedIndex == index, index = index, dialogMode = true,
                        onSelectedIndexChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSelect(it)
                            dismiss?.invoke()
                        })
                }
            }
            TextButton(text = "取消", minHeight = 50.dp,
                modifier = Modifier.padding(start = 24.dp, top = 12.dp, end = 24.dp).fillMaxWidth(),
                onClick = { dismiss?.invoke() })
        }) { measurables, constraints ->
            val button = measurables[1].measure(constraints.copy(minHeight = 0))
            val list = measurables[0].measure(constraints.copy(minHeight = 0,
                maxHeight = (constraints.maxHeight - button.height).coerceAtLeast(0)))
            layout(constraints.maxWidth, list.height + button.height) {
                list.place(0, 0)
                button.place(0, list.height)
            }
        }
    }
}

@Composable
internal fun SpeedDialog(speed: Float, onSpeed: (Float) -> Unit, show: Boolean = true, onDismiss: () -> Unit) {
    SelectionDialog("播放速度", show, SpeedChoices.map { DropdownItem(speedLabel(it)) }, SpeedChoices.indexOf(speed),
        onSelect = { onSpeed(SpeedChoices[it]) }, onDismiss = onDismiss)
}

@Composable
internal fun MediaInfoDialog(snapshot: PlaybackSnapshot, show: Boolean = true, onDismiss: () -> Unit) {
    HyperDialog("媒体信息", onDismiss, show = show, buttonText = "完成") {
        HyperSettingRow("文件", snapshot.current?.title ?: "尚未播放")
        if (snapshot.isVideo) {
            HyperSettingRow("视频尺寸", if (snapshot.videoHeight > 0) "${snapshot.videoWidth} × ${snapshot.videoHeight}" else "未报告")
            HyperSettingRow("当前解码器", snapshot.videoDecoderName ?: "未报告")
            val kind = when {
                snapshot.videoDecoderHardwareFlag == true -> "系统标记硬件"
                snapshot.videoDecoderSoftwareFlag == true -> "系统标记软件"
                snapshot.videoDecoderName?.let(DecoderSelectionPolicy::isKnownPlatformSoftwareCodec) == true -> "平台软解（系统标记不完整）"
                else -> "系统标记不完整"
            }
            HyperSettingRow("解码路径", kind)
        }
        HyperSettingRow("音频输入", listOfNotNull(snapshot.audioSampleRate?.let { "${it / 1000f} kHz" }, snapshot.audioChannels?.let { "$it 声道" }).joinToString(" · ").ifBlank { "未报告" })
        HyperSettingRow("音频解码器", snapshot.audioDecoderName ?: "未报告")
        HyperSettingRow("音频输出", "Android 系统输出；独占与输出格式尚未验证")
    }
}
