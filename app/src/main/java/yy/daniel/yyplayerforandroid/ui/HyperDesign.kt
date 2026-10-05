package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import yy.daniel.yyplayerforandroid.playback.PlaybackBackend
import yy.daniel.yyplayerforandroid.playback.PlaybackSnapshot

/** UI tokens and Miuix adapters live here; playback pages never define a second palette. */
internal val PageGutter = 12.dp
internal val PreferenceInset = 16.dp
internal val PageTextInset = PageGutter + PreferenceInset
internal val DialogContentInset = 24.dp
// Insets belong inside interactive rows so press feedback spans the container.
private val LocalPreferenceInset = compositionLocalOf { PreferenceInset }
internal val CardShape = RoundedCornerShape(24.dp)
internal val Muted: Color @Composable get() = MiuixTheme.colorScheme.onSurfaceVariantSummary

@Composable
internal fun HyperPage(title: String, onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    val backdrop = rememberHyperBackdrop(MiuixTheme.colorScheme.background)
    val scroll = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(containerColor = MiuixTheme.colorScheme.background, topBar = {
        TopAppBar(title = title, largeTitle = title, titlePadding = 26.dp,
            color = if (backdrop.supported) Color.Transparent else MiuixTheme.colorScheme.background,
            modifier = Modifier.hyperGlass(backdrop, RectangleShape, .82f),
            navigationIcon = { if (onBack != null) HyperIconButton(PlayerIcons.Back, "返回", onBack) },
            actions = actions, scrollBehavior = scroll)
    }) { padding ->
        Box(Modifier.fillMaxSize().hyperCapture(backdrop).nestedScroll(scroll.nestedScrollConnection)) { content(padding) }
    }
}

@Composable
internal fun HyperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = modifier, shape = CardShape, color = MiuixTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable
internal fun HyperIconButton(icon: ImageVector, description: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, filled: Boolean = false, tint: Color = MiuixTheme.colorScheme.onSurface, size: Dp = 48.dp) {
    IconButton(onClick = onClick, modifier = modifier.size(size), minHeight = size, minWidth = size, cornerRadius = size / 2,
        backgroundColor = if (filled) MiuixTheme.colorScheme.surfaceContainerHigh else Color.Transparent) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, description, Modifier.size(24.dp), tint = tint) }
    }
}

@Composable
internal fun HyperSection(title: String, trailing: String? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = PageTextInset, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 14.sp, color = Muted, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = MiuixTheme.colorScheme.primary, fontSize = 14.sp,
            modifier = if (onClick != null) Modifier.clickable(onClick = onClick).padding(8.dp) else Modifier)
    }
}

@Composable
internal fun HyperSettingRow(title: String, summary: String? = null, value: String? = null,
    onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = LocalPreferenceInset.current, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (!summary.isNullOrEmpty()) { Spacer(Modifier.height(5.dp)); Text(summary, fontSize = 14.sp, lineHeight = 20.sp, color = Muted) }
        }
        if (value != null) { Spacer(Modifier.width(12.dp)); Text(value, fontSize = 14.sp, color = Muted, maxLines = 1) }
        if (trailing != null) { Spacer(Modifier.width(16.dp)); trailing() }
        else if (onClick != null) { Spacer(Modifier.width(8.dp)); Icon(PlayerIcons.Chevron, null, Modifier.size(18.dp), tint = Muted) }
    }
}

@Composable
internal fun HyperSpinnerPreference(title: String, items: List<DropdownItem>, selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit) {
    WindowSpinnerPreference(title = title, items = items, selectedIndex = selectedIndex,
        insideMargin = PaddingValues(horizontal = LocalPreferenceInset.current, vertical = 18.dp),
        onSelectedIndexChange = onSelectedIndexChange)
}

@Composable
internal fun HyperChoice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, modifier = modifier, shape = CircleShape,
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surface) {
        Text(label, Modifier.padding(horizontal = 20.dp, vertical = 12.dp), fontSize = 14.sp,
            color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun HyperDialog(title: String, onDismiss: () -> Unit, show: Boolean = true,
    buttonText: String = "取消", content: @Composable ColumnScope.() -> Unit) {
    val maxHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } * .65f
    // Keep this node composed while show becomes false so Miuix completes its exit animation.
    WindowDialog(show = show, title = title, onDismissRequest = onDismiss, backgroundColor = MiuixTheme.colorScheme.surface,
        insideMargin = DpSize(0.dp, 24.dp)) {
        Column(Modifier.heightIn(max = maxHeight)) {
            CompositionLocalProvider(LocalPreferenceInset provides DialogContentInset) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), content = content)
            }
            Spacer(Modifier.height(16.dp))
            TextButton(text = buttonText, onClick = onDismiss,
                modifier = Modifier.padding(horizontal = DialogContentInset).fillMaxWidth())
        }
    }
}

@Composable
internal fun FloatingLibraryNavigation(selected: Int, onSelect: (Int) -> Unit, searching: Boolean, onSearch: () -> Unit, backdrop: HyperBackdrop) {
    // Restore the official icon-only floating bar after the user's U04 feedback.
    // The bar owns its bottom safe spacing. The caller must not add the same insets again.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(180.dp)) {
            FloatingNavigationBar(modifier = Modifier.hyperGlass(backdrop, RoundedCornerShape(FloatingToolbarDefaults.CornerRadius), alpha = .6f, navigationStyle = true),
                color = if (backdrop.supported) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
                defaultWindowInsetsPadding = false, shadowElevation = 0.dp) {
                listOf("音乐" to PlayerIcons.Music, "视频" to PlayerIcons.Video).forEachIndexed { index, (label, icon) ->
                    FloatingNavigationBarItem(selected = selected == index, onClick = { onSelect(index) }, icon = icon, label = label)
                }
            }
        }
        Surface(onClick = onSearch, modifier = Modifier.size(52.dp).hyperGlass(backdrop, CircleShape, alpha = .6f, navigationStyle = true), shape = CircleShape,
            color = if (backdrop.supported) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(if (searching) PlayerIcons.Close else PlayerIcons.Search, if (searching) "关闭搜索" else "搜索媒体", Modifier.size(26.dp))
            }
        }
    }
}

@Composable
internal fun LibrarySidebar(selected: Int, onSelect: (Int) -> Unit, onSettings: () -> Unit) {
    HyperCard(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).width(180.dp).fillMaxHeight()) {
        Text("YYPlayer", Modifier.padding(22.dp), fontSize = 23.sp, fontWeight = FontWeight.Medium)
        Text("媒体库", Modifier.padding(start = 22.dp, top = 24.dp, bottom = 10.dp), color = Muted, fontSize = 13.sp)
        listOf("音乐" to PlayerIcons.Music, "视频" to PlayerIcons.Video).forEachIndexed { index, (label, icon) ->
            Surface(onClick = { onSelect(index) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
                shape = RoundedCornerShape(18.dp), color = if (selected == index) MiuixTheme.colorScheme.surfaceContainerHigh else Color.Transparent) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, Modifier.size(24.dp)); Spacer(Modifier.width(12.dp)); Text(label) }
            }
        }
        Spacer(Modifier.weight(1f))
        HyperSettingRow("设置", onClick = onSettings)
    }
}

@Composable
internal fun LibraryMiniPlayer(snapshot: PlaybackSnapshot, backend: PlaybackBackend, backdrop: HyperBackdrop, onOpen: () -> Unit) {
    val details = rememberMediaDetails(snapshot.current)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().hyperGlass(backdrop, CardShape, .88f), shape = CardShape,
        color = if (backdrop.supported) Color.Transparent else MiuixTheme.colorScheme.surface) {
        Column {
            Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                MediaArtwork(snapshot.current, Modifier.size(46.dp), radius = 15.dp)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(snapshot.current?.let { if (snapshot.isVideo) displayTitle(it.title) else details.title ?: it.musicTitle }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(if (!snapshot.isVideo) listOf(details.artist ?: "未知歌手", details.album ?: "未知专辑").joinToString(" · ") else if (snapshot.isPlaying) "正在播放" else "已暂停", fontSize = 12.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HyperIconButton(if (snapshot.isPlaying) PlayerIcons.Pause else PlayerIcons.Play, if (snapshot.isPlaying) "暂停" else "播放", backend::togglePlayPause)
                HyperIconButton(PlayerIcons.Next, "下一首", backend::next)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(MiuixTheme.colorScheme.surfaceContainerHigh)) {
                Box(Modifier.fillMaxWidth(snapshot.progress).fillMaxHeight().background(MiuixTheme.colorScheme.primary))
            }
        }
    }
}

@Composable
internal fun HyperNotice(text: String, modifier: Modifier) {
    HyperCard(modifier.padding(horizontal = 24.dp).widthIn(max = 440.dp)) { Text(text, Modifier.padding(20.dp), fontSize = 14.sp) }
}

internal fun displayTitle(title: String) = title.substringBeforeLast('.', title)
internal fun formatTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}

/** All interface glyphs come from the locked Miuix icon modules; no custom vectors. */
internal object PlayerIcons {
    val Music = MiuixIcons.Regular.Music
    val Video = MiuixIcons.Regular.Play
    val Search = MiuixIcons.Regular.Search
    val Add = MiuixIcons.Regular.Add
    val Back = MiuixIcons.Regular.ChevronBackward
    val Chevron = MiuixIcons.Regular.ChevronForward
    val Close = MiuixIcons.Regular.Close
    val Tune = MiuixIcons.Regular.Settings
    val More = MiuixIcons.Regular.More
    val Play = MiuixIcons.Regular.Play
    val Pause = MiuixIcons.Regular.Pause
    val Next = MiuixIcons.Regular.ChevronForward
    val Previous = MiuixIcons.Regular.ChevronBackward
    val Heart = MiuixIcons.Regular.Favorites
    val Queue = MiuixIcons.Regular.Playlist
    val Rotate = MiuixIcons.Regular.RotateLeft
    val Check = MiuixIcons.Regular.Ok
}
