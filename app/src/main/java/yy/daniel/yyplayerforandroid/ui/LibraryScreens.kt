package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.*

@Composable
internal fun LibraryScreen(kind: MediaKind, media: List<LocalMedia>, snapshot: PlaybackSnapshot,
    favorites: Set<String>, loading: Boolean, searching: Boolean, query: String, onQuery: (String) -> Unit,
    onCloseSearch: () -> Unit, onImport: () -> Unit, onSettings: () -> Unit, onPlay: (LocalMedia) -> Unit,
    onFavorite: (LocalMedia) -> Unit, onRemove: (LocalMedia) -> Unit, bottomSpace: Dp) {
    var onlyFavorites by rememberSaveable(kind) { mutableStateOf(false) }
    var menuMedia by remember { mutableStateOf<LocalMedia?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    val filtered = media.filter { (!onlyFavorites || it.uri.toString() in favorites) &&
        listOfNotNull(it.title, it.songTitle, it.artist, it.album).any { text -> text.contains(query, ignoreCase = true) } }
    val isMusic = kind == MediaKind.AUDIO
    HyperPage(if (isMusic) "音乐" else "视频", actions = {
        HyperIconButton(PlayerIcons.Tune, "设置", onSettings)
        HyperIconButton(PlayerIcons.Add, if (isMusic) "添加音乐" else "添加视频", onImport)
        Spacer(Modifier.width(10.dp))
    }) { padding ->
        if (isMusic) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomSpace)) {
                if (searching) item { SearchInput(query, onQuery, onCloseSearch, Modifier.padding(horizontal = PageGutter)) }
                item {
                    Row(Modifier.padding(horizontal = PageGutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CollectionTile("全部音乐", "${media.size} 首", PlayerIcons.Music, Color(0xFFFF4949), !onlyFavorites, { onlyFavorites = false }, Modifier.weight(1f))
                        CollectionTile("收藏", "${media.count { it.uri.toString() in favorites }} 首", PlayerIcons.Heart, Color(0xFFFFB020), onlyFavorites, { onlyFavorites = true }, Modifier.weight(1f))
                    }
                }
                item { HyperSection(if (onlyFavorites) "我的收藏" else "本地音乐", if (!onlyFavorites && filtered.isNotEmpty()) "播放全部" else null, { filtered.firstOrNull()?.let(onPlay) }) }
                if (loading) item { Text("正在读取媒体…", Modifier.padding(horizontal = 26.dp, vertical = 10.dp), color = Muted, fontSize = 14.sp) }
                if (filtered.isEmpty() && !loading) item {
                    LibraryEmpty(if (query.isNotBlank()) "没有找到相关音乐" else if (onlyFavorites) "还没有收藏" else "添加你的音乐",
                        if (onlyFavorites) "在音乐右侧菜单中，收藏喜欢的作品" else "从设备选择本地音频，建立自己的音乐库", !onlyFavorites && query.isBlank(), onImport)
                }
                itemsIndexed(filtered, key = { _, it -> it.uri.toString() }) { index, item ->
                    val shape = RoundedCornerShape(topStart = if (index == 0) 24.dp else 0.dp, topEnd = if (index == 0) 24.dp else 0.dp,
                        bottomStart = if (index == filtered.lastIndex) 24.dp else 0.dp, bottomEnd = if (index == filtered.lastIndex) 24.dp else 0.dp)
                    Surface(Modifier.padding(horizontal = PageGutter).fillMaxWidth(), shape = shape, color = MiuixTheme.colorScheme.surface) {
                        MusicRow(item, snapshot.current?.uri == item.uri, { onPlay(item) }, { menuMedia = item; menuVisible = true })
                    }
                }
            }
        } else {
            LazyVerticalGrid(GridCells.Adaptive(160.dp), Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = PageGutter, end = PageGutter, top = padding.calculateTopPadding(), bottom = bottomSpace),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (searching) item(span = { GridItemSpan(maxLineSpan) }) { SearchInput(query, onQuery, onCloseSearch, Modifier) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("本地视频", fontSize = 15.sp, color = Muted, modifier = Modifier.weight(1f))
                        Text("${media.size} 项", fontSize = 14.sp, color = Muted)
                    }
                }
                if (loading) item(span = { GridItemSpan(maxLineSpan) }) { Text("正在读取媒体…", Modifier.padding(16.dp), color = Muted) }
                if (filtered.isEmpty() && !loading) item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryEmpty(if (query.isNotBlank()) "没有找到相关视频" else "把精彩留在这里", "从设备选择本地视频，随时打开观看", query.isBlank(), onImport)
                }
                items(filtered, key = { it.uri.toString() }) { item -> VideoTile(item, snapshot.current?.uri == item.uri, { onPlay(item) }, { menuMedia = item; menuVisible = true }) }
            }
        }
    }
    val selected = menuMedia
    HyperDialog("媒体选项", { menuVisible = false }, show = menuVisible) {
        if (selected != null) {
        Text(selected.title, Modifier.padding(horizontal = DialogContentInset, vertical = 14.dp), color = Muted, fontSize = 14.sp)
        HyperSettingRow(if (selected.uri.toString() in favorites) "取消收藏" else "收藏", onClick = { onFavorite(selected); menuVisible = false })
        HyperSettingRow("播放", onClick = { onPlay(selected); menuVisible = false })
        HyperSettingRow("从媒体库移除", "不会删除设备中的原文件", onClick = { onRemove(selected); menuVisible = false })
        }
    }
}

@Composable
private fun CollectionTile(title: String, count: String, icon: ImageVector, color: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    HyperCard(modifier.clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = color, modifier = Modifier.size(40.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(24.dp), tint = Color.White) }
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(count, color = Muted, fontSize = 13.sp)
            }
            if (selected) Box(Modifier.size(5.dp).background(MiuixTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
        }
    }
}

@Composable
private fun MusicRow(media: LocalMedia, current: Boolean, onClick: () -> Unit, onMenu: () -> Unit) {
    val details = rememberMediaDetails(media)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 6.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        MediaArtwork(media, Modifier.size(52.dp), 16.dp)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(details.title ?: media.musicTitle, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                color = if (current) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(listOf(details.artist ?: media.artist ?: "未知歌手", details.album ?: media.album ?: "未知专辑").joinToString(" · "),
                fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text((details.durationMs ?: media.durationMs)?.let(::formatTime) ?: "—:—", fontSize = 12.sp, color = Muted,
            modifier = Modifier.padding(end = 4.dp), maxLines = 1)
        HyperIconButton(PlayerIcons.More, "${media.title} 的选项", onMenu)
    }
}

@Composable
private fun VideoTile(media: LocalMedia, current: Boolean, onPlay: () -> Unit, onMenu: () -> Unit) {
    val details = rememberMediaDetails(media)
    Column(Modifier.clickable(onClick = onPlay)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) {
            MediaArtwork(media, Modifier.fillMaxSize(), 22.dp)
            if (details.durationMs != null) Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = .55f),
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp)) { Text(formatTime(details.durationMs), Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 11.sp, color = Color.White) }
        }
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(displayTitle(media.title), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (current) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Text(if (details.width != null && details.height != null) "${details.width} × ${details.height}" else "本地视频", fontSize = 12.sp, color = Muted)
            }
            HyperIconButton(PlayerIcons.More, "${media.title} 的选项", onMenu)
        }
    }
}

@Composable
private fun LibraryEmpty(title: String, summary: String, canImport: Boolean, onImport: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 70.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(PlayerIcons.Add, null, Modifier.size(50.dp), tint = Muted.copy(alpha = .5f))
        Spacer(Modifier.height(20.dp)); Text(title, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp)); Text(summary, color = Muted, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 21.sp)
        if (canImport) { Spacer(Modifier.height(24.dp)); Button(onClick = onImport) { Text("选择文件") } }
    }
}

@Composable
private fun SearchInput(query: String, onQuery: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(modifier.fillMaxWidth().padding(vertical = 8.dp), shape = RoundedCornerShape(28.dp), color = MiuixTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlayerIcons.Search, null, Modifier.size(22.dp), tint = Muted)
            BasicTextField(query, onQuery, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp).focusRequester(focus),
                singleLine = true, textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 16.sp),
                cursorBrush = SolidColor(MiuixTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { field -> if (query.isEmpty()) Text("搜索本地媒体", color = Muted, fontSize = 16.sp); field() })
            HyperIconButton(PlayerIcons.Close, "结束搜索", { keyboard?.hide(); onClose() })
        }
    }
}
