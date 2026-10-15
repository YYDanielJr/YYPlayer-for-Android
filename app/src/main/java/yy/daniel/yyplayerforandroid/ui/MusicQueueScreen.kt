package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.*

/** Queue identity includes occurrence, so repeated media can still be selected independently. */
private fun queueKeys(queue: List<LocalMedia>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return queue.map { item -> val n = occurrences.getOrDefault(item.identity, 0); occurrences[item.identity] = n + 1; "${item.identity}#$n" }
}

@Composable
internal fun MusicQueueScreen(snapshot: PlaybackSnapshot, backend: PlaybackBackend, onBack: () -> Unit) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var clearing by remember { mutableStateOf(false) }
    val keys = remember(snapshot.queue) { queueKeys(snapshot.queue) }
    val latestKeys by rememberUpdatedState(keys)
    val state = rememberLazyListState()
    var dragged by remember { mutableStateOf<String?>(null) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val haptics = LocalHapticFeedback.current
    fun finishSelection() { editing = false; selected = emptySet() }
    fun reorderAtPointer() {
        val from = latestKeys.indexOf(dragged)
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != dragged && fingerY >= it.offset && fingerY <= it.offset + it.size
        }
        val to = latestKeys.indexOf(target?.key)
        if (from >= 0 && to >= 0 && from != to) backend.moveQueueItem(from, to)
    }
    LaunchedEffect(keys) {
        selected = selected.intersect(keys.toSet())
        if (dragged !in keys) dragged = null
    }
    // Frame-paced edge scrolling continues while the user holds a row near an edge.
    LaunchedEffect(dragged) {
        if (dragged != null) while (isActive) {
            withFrameNanos { }
            val info = state.layoutInfo
            val delta = when {
                fingerY < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - fingerY) / edge).coerceIn(0f, 1f) * 18f
                fingerY > info.viewportEndOffset - edge -> ((fingerY - info.viewportEndOffset + edge) / edge).coerceIn(0f, 1f) * 18f
                else -> 0f
            }
            if (delta != 0f) { state.scrollBy(delta); reorderAtPointer() }
        }
    }
    BackHandler(editing) { finishSelection() }
    HyperPage("播放队列", onBack, actions = {
        HyperIconButton(if (editing) PlayerIcons.SelectAll else PlayerIcons.Edit, if (editing) "全选队列" else "选择歌曲", {
            if (editing) selected = if (selected.size == keys.size) emptySet() else keys.toSet() else editing = true
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 12.dp)) {
                HyperSpinnerPreference("播放顺序", PlayOrder.entries.map { DropdownItem(it.label) }, snapshot.playOrder.ordinal,
                    { backend.setPlayOrder(PlayOrder.entries[it]) })
            }
            Text(if (editing) "已选择 ${selected.size} 首" else "${keys.size} 首 · 长按拖动调整顺序", Modifier.padding(horizontal = 28.dp, vertical = 12.dp), fontSize = 14.sp, color = Muted)
            LazyColumn(state = state, modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (longPress != null) {
                        val point = longPress.position
                        val row = state.layoutInfo.visibleItemsInfo.firstOrNull { point.y >= it.offset && point.y < it.offset + it.size }
                        dragged = (row?.key as? String)?.takeIf { it in latestKeys }
                        dragTop = row?.offset?.toFloat() ?: 0f; fingerY = point.y
                        if (dragged != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            // Once long-press wins, consume before LazyColumn's scroll detector.
                            // Before that, taps and normal scrolling remain entirely native.
                            while (dragged != null) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                                pointer.consume()
                                if (!pointer.pressed || event.changes.count { it.pressed } != 1) break
                                val delta = pointer.position.y - pointer.previousPosition.y
                                fingerY += delta; dragTop += delta; reorderAtPointer()
                            }
                        } finally { dragged = null }
                    }
                }
            }, contentPadding = PaddingValues(horizontal = PageGutter, vertical = 4.dp)) {
                itemsIndexed(snapshot.queue, key = { index, _ -> keys[index] }) { index, track ->
                    val key = keys[index]
                    val isDragged = key == dragged
                    HyperListRow(index == 0, index == snapshot.queue.lastIndex,
                        Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (isDragged) null else spring(stiffness = 400f, dampingRatio = 1f))
                            .zIndex(if (isDragged) 1f else 0f).graphicsLayer {
                            translationY = if (isDragged) dragTop - (state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.offset ?: 0) else 0f
                            shadowElevation = if (isDragged) 12.dp.toPx() else 0f
                        }.semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("上移") { if (index > 0) backend.moveQueueItem(index, index - 1); true },
                                CustomAccessibilityAction("下移") { if (index < snapshot.queue.lastIndex) backend.moveQueueItem(index, index + 1); true },
                                CustomAccessibilityAction("选择删除") { editing = true; selected = selected + key; true })
                        }) {
                        Row(Modifier.fillMaxWidth().clickable {
                            if (editing) selected = if (key in selected) selected - key else selected + key else backend.jumpTo(index)
                        }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            MediaArtwork(track, Modifier.size(52.dp), 12.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                Text(track.musicTitle, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (index == snapshot.queueIndex) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                                Text(if (index == snapshot.queueIndex) "正在播放 · ${track.musicSubtitle}" else track.musicSubtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (editing) Checkbox(if (key in selected) ToggleableState.On else ToggleableState.Off,
                                { selected = if (key in selected) selected - key else selected + key }, Modifier.size(48.dp))
                            else Icon(PlayerIcons.Order, "长按拖动 ${track.musicTitle}", Modifier.size(24.dp), tint = Muted)
                        }
                    }
                }
                if (snapshot.queue.isEmpty()) item { Text("队列为空，从音乐库选择歌曲开始播放。", Modifier.padding(24.dp), color = Muted) }
            }
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = LocalPlaybackBottomSpace.current).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HyperActionButton(if (editing) "取消" else "清空队列", { if (editing) finishSelection() else clearing = true }, Modifier.weight(1f), primary = false, enabled = editing || keys.isNotEmpty())
                HyperActionButton(if (editing) "移除所选 (${selected.size})" else "选择歌曲", {
                    if (!editing) editing = true else {
                        // Resolve once against the current queue and remove backwards to avoid index shifts.
                        val current = queueKeys(backend.state.value.queue)
                        current.indices.filter { current[it] in selected }.asReversed().forEach(backend::removeQueueItem)
                        finishSelection()
                    }
                }, Modifier.weight(1f), enabled = if (editing) selected.isNotEmpty() else keys.isNotEmpty())
            }
        }
    }
    HyperDialog("清空播放队列", { clearing = false }, show = clearing) {
        Text("停止播放并清空队列，音乐库和歌单中的歌曲仍会保留。", Modifier.padding(24.dp), color = Muted)
        HyperActionButton("停止并清空", { backend.clearQueue(); clearing = false; finishSelection() }, Modifier.padding(horizontal = 24.dp))
    }
}
