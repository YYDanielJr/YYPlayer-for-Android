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

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal fun PlayerScreen(backend: AndroidMedia3PlaybackBackend, snapshot: PlaybackSnapshot, reduceMotion: Boolean,
    onOpenOrientation: () -> Unit, favorite: Boolean,
    onFavorite: () -> Unit, onBack: () -> Unit, surfaceActive: Boolean = true, inputActive: Boolean = true,
    audioOutput: yy.daniel.yyplayerforandroid.audio.AudioOutputState? = null) {
    if (snapshot.current == null) {
        HyperPage("正在播放", onBack) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("选择一个视频开始播放") }
        }
    } else if (snapshot.isVideo) {
        YYPlayerTheme(ColorSchemeMode.Dark, MiuixTheme.colorScheme.primary) {
            VideoPlayerPage(backend, snapshot, reduceMotion, onOpenOrientation, onBack, surfaceActive, audioOutput, inputActive)
        }
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在返回音乐详情…", color = Muted) }
    }
}
@Composable
internal fun SeekControl(snapshot: PlaybackSnapshot, backend: PlaybackBackend, video: Boolean = false, reduced: Boolean = false, onDragging: (Boolean) -> Unit = {}) {
    var dragging by remember(snapshot.current?.uri) { mutableStateOf(false) }
    var value by remember(snapshot.current?.uri) { mutableFloatStateOf(snapshot.progress) }
    PlaybackSeekBar(snapshot, backend, if (video) Color.White else MiuixTheme.colorScheme.primary, reduced,
        if (dragging) value else snapshot.progress, dragging,
        onPreview = { value = it; dragging = true }, onEnd = { dragging = false; onDragging(false) },
        onBegin = { onDragging(true) })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val color = if (video) Color.White.copy(alpha = .75f) else Muted
        Text(formatTime(if (dragging) (value * snapshot.durationMs).toLong() else snapshot.positionMs), fontSize = 12.sp, color = color)
        Text(formatTime(snapshot.durationMs), fontSize = 12.sp, color = color)
    }
}

@Composable
internal fun PlaybackError(snapshot: PlaybackSnapshot, backend: PlaybackBackend) {
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
    if (LocalVideoSettingsInDrawer.current) {
        if (show) VideoDrawerPage(title, onDismiss, "取消", scrollContent = false) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(items.size) { index ->
                    DropdownImpl(item = items[index], optionSize = items.size,
                        isSelected = selectedIndex == index, index = index, dialogMode = true,
                        onSelectedIndexChange = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSelect(it)
                            onDismiss()
                        })
                }
            }
        }
        return
    }
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
            HyperActionButton(text = "取消", primary = false, compact = true,
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
internal fun MediaInfoDialog(
    snapshot: PlaybackSnapshot,
    show: Boolean = true,
    audioOutput: yy.daniel.yyplayerforandroid.audio.AudioOutputState? = null,
    onDismiss: () -> Unit,
) {
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
        if (snapshot.videoEngine == yy.daniel.yyplayerforandroid.playback.VideoEngine.MPV)
            HyperSettingRow("视频音频驱动", snapshot.videoAudioOutput ?: "未报告")
        // Only what the output controller observed is shown: an unreported value stays "未知"
        // instead of being filled in with the input format.
        val output = audioOutput
        if (output == null) {
            HyperSettingRow("音频输出", "Android 系统输出；独占与输出格式尚未验证")
        } else {
            HyperSettingRow("音频输出路径", output.decision.reasonText)
            HyperSettingRow("位准确", output.bitPerfect.label)
            HyperSettingRow(
                "实际输出格式",
                listOfNotNull(
                    output.actualSampleRate?.let { "${it / 1000f} kHz" },
                    output.actualEncoding.takeIf { it.effectiveBits > 0 }?.let { "${it.effectiveBits} bit" },
                ).joinToString(" · ").ifBlank { "未知（系统未报告硬件输出格式）" },
            )
            HyperSettingRow("独占状态", output.actualSharing.label)
        }
    }
}
