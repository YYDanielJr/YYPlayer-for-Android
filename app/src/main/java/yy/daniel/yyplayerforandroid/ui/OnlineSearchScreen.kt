package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.online.*

/** One search surface for music and lyric candidates, sharing the existing runtime and API gates. */
@Composable
internal fun OnlineSearchScreen(repository: OnlineRepository, lyricMode: Boolean, initialQuery: String,
    allowed: Boolean, busyExternal: Boolean, externalMessage: String?,
    onSong: (OnlineSong, ApiQuality, Boolean) -> Unit, onLyric: (OnlineSong) -> Unit, onBack: () -> Unit) {
    val config by repository.config.collectAsStateWithLifecycle()
    val history by repository.searchHistory.state.collectAsStateWithLifecycle()
    val providers = config?.providers.orEmpty().filter { if (lyricMode) it.lyricSearch != null && it.lyric != null else it.search != null && it.stream != null }
    var providerId by rememberSaveable { mutableStateOf("") }
    val provider = providers.firstOrNull { it.id == providerId } ?: providers.firstOrNull()
    var source by rememberSaveable { mutableStateOf("") }
    val actualSource = source.takeIf { it in provider?.sources.orEmpty() } ?: provider?.sources?.keys?.firstOrNull().orEmpty()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var songs by remember { mutableStateOf(emptyList<OnlineSong>()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var lastQuery by remember { mutableStateOf("") }
    var more by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var historyVisible by remember { mutableStateOf(false) }
    var songActions by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<OnlineSong?>(null) }
    var qualityVisible by remember { mutableStateOf(false) }
    var checkingQualities by remember { mutableStateOf(false) }
    var qualityOptions by remember { mutableStateOf(emptyList<QualityOption>()) }
    var download by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    var searchRevision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = searching || busyExternal
    fun search(append: Boolean = false) {
        val p = provider ?: return
        if (!allowed || query.isBlank() || busyExternal) return
        task?.cancel()
        val revision = ++searchRevision
        val submitted = if (append) lastQuery else query.trim()
        val requestedPage = if (append) page + 1 else 1
        keyboard?.hide(); searchMessage = null; searching = true
        if (!append) {
            songs = emptyList(); more = false; searched = true
            if (!lyricMode) repository.searchHistory.record(submitted)
        }
        task = scope.launch {
            try {
                val results = repository.search(p.id, actualSource, submitted, requestedPage, lyricMode)
                if (revision != searchRevision) return@launch
                songs = if (append) (songs + results).distinctBy { "${it.source}:${it.id}" } else results
                more = results.size >= p.pageSize; page = requestedPage; lastQuery = submitted; searched = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (revision == searchRevision) searchMessage = e.message ?: "搜索失败，请重试" }
            finally { if (revision == searchRevision) searching = false }
        }
    }
    LaunchedEffect(allowed) { if (!allowed) { ++searchRevision; task?.cancel(); searching = false; songs = emptyList(); qualityVisible = false; menu = false; songActions = false } }
    LaunchedEffect(config, provider?.id, actualSource) {
        ++searchRevision; task?.cancel(); searching = false; songs = emptyList(); searched = false; more = false; qualityVisible = false; songActions = false
        if (lyricMode && query.isNotBlank() && allowed) search()
    }
    LaunchedEffect(qualityVisible, selected, download, config, allowed) {
        qualityOptions = emptyList()
        val song = selected
        if (qualityVisible && song != null && allowed) {
            checkingQualities = true
            try { qualityOptions = repository.qualityOptions(song, download) }
            finally { checkingQualities = false }
        }
    }
    HyperPage(if (lyricMode) "联网歌词" else "在线搜索", onBack, actions = {
        if (!lyricMode) HyperIconButton(PlayerIcons.More, "管理搜索历史", { historyVisible = true })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                OnlineSearchInput(query, { query = it.take(200) }, { search() }, busy,
                    if (lyricMode) "输入歌名，搜索歌词" else "搜索在线歌曲", { menu = true })
                if (provider != null) Text("${provider.name} · ${provider.sources[actualSource].orEmpty()}", Modifier.padding(start = PageTextInset, bottom = 12.dp), color = Muted, fontSize = 12.sp)
            Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (providers.isEmpty()) item { Text("请先在设置中导入支持${if (lyricMode) "歌词" else "音乐"}搜索的 API 配置", Modifier.padding(28.dp), color = Muted) }
            if (!lyricMode && !busy && (!searched || query.isBlank())) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = PageTextInset, end = PreferenceInset), verticalAlignment = Alignment.CenterVertically) {
                        Text("搜索历史", Modifier.weight(1f), fontSize = 14.sp, color = Muted)
                        HyperIconButton(PlayerIcons.Edit, "管理搜索历史", { historyVisible = true })
                    }
                    if (history.queries.isEmpty()) Text(if (history.keep) "搜索过的歌名会显示在这里" else "已关闭搜索历史记录",
                        Modifier.padding(horizontal = PageTextInset, vertical = 8.dp), fontSize = 14.sp, color = Muted)
                    FlowRow(Modifier.padding(horizontal = PageTextInset), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        history.queries.forEach { term ->
                            Surface(onClick = { query = term; search() }, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(18.dp),
                                color = MiuixTheme.colorScheme.surfaceContainerHigh) {
                                Text(term, Modifier.padding(horizontal = 16.dp, vertical = 14.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            val message = externalMessage ?: searchMessage
            if (message != null && !busy) item { Text(message, Modifier.padding(horizontal = PageTextInset, vertical = 12.dp), color = MiuixTheme.colorScheme.primary, fontSize = 14.sp) }
            if (searched && songs.isEmpty() && !busy && message == null) item { Text("没有找到${if (lyricMode) "歌词候选" else "相关歌曲"}，试试其他歌名或来源", Modifier.padding(28.dp), color = Muted) }
            items(if (!lyricMode && query.isBlank()) emptyList() else songs, key = { "${it.providerId}:${it.source}:${it.id}" }) { song ->
                HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 4.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy, onClickLabel = if (lyricMode) "获取 ${song.title} 歌词" else "播放 ${song.title}") {
                        try { if (lyricMode) onLyric(song) else onSong(song, repository.defaultPlaybackQuality(song), false) }
                        catch (e: Exception) { searchMessage = e.message ?: "此歌曲暂无可播放音质" }
                    }.padding(start = PreferenceInset, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!lyricMode) {
                            OnlineSongArtwork(repository, song, allowed, config)
                            Spacer(Modifier.width(12.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(listOf(song.artist.ifBlank { "未知歌手" }, song.album.ifBlank { "未知专辑" }).joinToString(" · "), color = Muted, fontSize = 13.sp, maxLines = 2)
                            if (lyricMode && song.title.trim().equals(initialQuery.trim(), true)) Text("同名优先", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        if (lyricMode) Icon(PlayerIcons.Chevron, null, Modifier.size(24.dp), tint = Muted)
                        else {
                            HyperIconButton(PlayerIcons.More, "${song.title} 的选项", { if (!busy) { selected = song; songActions = true } })
                        }
                    }
                }
            }
            if (more && !busy) item { HyperActionButton("加载更多", onClick = { search(true) }, modifier = Modifier.padding(horizontal = PageGutter), primary = false) }
            }
            if (busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator(Modifier.semantics { contentDescription = if (searching) "正在搜索" else "正在处理" })
            }
            }
        }
    }
    HyperDialog("搜索历史", { historyVisible = false }, show = historyVisible) {
        HyperSettingRow("保留搜索历史", "关闭后不新增记录，已有记录可单独清除", trailing = {
            Switch(checked = history.keep, onCheckedChange = repository.searchHistory::setKeep)
        })
        HyperSettingRow("清除全部历史", onClick = if (history.queries.isEmpty()) null else repository.searchHistory::clear)
        history.queries.forEach { term ->
            HyperSettingRow(term, trailing = { HyperIconButton(PlayerIcons.Close, "删除历史 $term", { repository.searchHistory.remove(term) }) })
        }
    }
    HyperDialog(selected?.title ?: "歌曲选项", { songActions = false }, show = songActions) {
        HyperSettingRow("选择播放音质", onClick = { songActions = false; download = false; qualityVisible = true })
        HyperSettingRow("下载歌曲", onClick = { songActions = false; download = true; qualityVisible = true })
    }
    HyperDialog("搜索来源", { menu = false }, show = menu) {
        providers.forEach { p -> HyperSettingRow(p.name, value = if (p.id == provider?.id) "已选择" else null,
            onClick = { providerId = p.id; source = p.sources.keys.first(); menu = false }) }
        provider?.sources?.forEach { (id, name) -> HyperSettingRow(name, value = if (id == actualSource) "已选择" else null, onClick = { source = id; menu = false }) }
    }
    HyperDialog(if (download) "下载音质" else "播放音质", { qualityVisible = false }, show = qualityVisible) {
        val song = selected
        if (song != null) {
            Text(song.title, Modifier.padding(horizontal = DialogContentInset, vertical = 12.dp), fontSize = 14.sp, color = Muted)
            if (checkingQualities) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() }
            else Text("仅可选择服务提供的音质；下载时识别实际格式", Modifier.padding(horizontal = DialogContentInset, vertical = 8.dp), fontSize = 13.sp, color = Muted)
            qualityOptions.forEach { option ->
                HyperSettingRow(option.quality.name, option.detail, value = if (option.available) null else "不可用",
                    onClick = if (option.available) { { qualityVisible = false; if (allowed && !busy) onSong(song, option.quality, download) } } else null)
            }
        }
    }
}

@Composable
private fun OnlineSongArtwork(repository: OnlineRepository, song: OnlineSong, allowed: Boolean, config: ApiConfig?) {
    key(repository, song, allowed, config) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, repository, song, allowed, config) {
        if (allowed) value = try { repository.thumbnail(song) }
        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "${song.album.ifBlank { song.title }} 专辑封面", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(PlayerIcons.Music, null, Modifier.size(24.dp), tint = Muted)
    }
    }
}

@Composable
private fun OnlineSearchInput(query: String, onQuery: (String) -> Unit, onSearch: () -> Unit,
    busy: Boolean, hint: String, onSources: () -> Unit) {
    Surface(Modifier.padding(horizontal = PageGutter, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = MiuixTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(start = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(PlayerIcons.Search, null, Modifier.size(22.dp), tint = Muted)
            BasicTextField(query, onQuery, Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 16.dp), singleLine = true,
                textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { field -> if (query.isEmpty()) Text(hint, color = Muted, fontSize = 16.sp); field() })
            HyperIconButton(PlayerIcons.Tune, "选择 API 与来源", onSources)
            HyperIconButton(PlayerIcons.Search, if (busy) "正在搜索" else "搜索", { if (!busy) onSearch() })
        }
    }
}
