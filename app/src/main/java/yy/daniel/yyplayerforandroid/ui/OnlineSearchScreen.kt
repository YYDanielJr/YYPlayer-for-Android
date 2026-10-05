package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.*
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

/** One search surface for music and lyric candidates. Empty music landing only contains search controls. */
@Composable
internal fun OnlineSearchScreen(repository: OnlineRepository, lyricMode: Boolean, initialQuery: String,
    allowed: Boolean, busyExternal: Boolean, externalMessage: String?,
    onSong: (OnlineSong, ApiQuality, Boolean) -> Unit, onLyric: (OnlineSong) -> Unit, onBack: () -> Unit) {
    val config by repository.config.collectAsStateWithLifecycle()
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
    var selected by remember { mutableStateOf<OnlineSong?>(null) }
    var qualityVisible by remember { mutableStateOf(false) }
    var checkingQualities by remember { mutableStateOf(false) }
    var qualityOptions by remember { mutableStateOf(emptyList<QualityOption>()) }
    var download by remember { mutableStateOf(false) }
    var task by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = searching || busyExternal
    fun search(append: Boolean = false) {
        val p = provider ?: return
        if (!allowed || query.isBlank() || busyExternal) return
        task?.cancel()
        val submitted = if (append) lastQuery else query.trim()
        val requestedPage = if (append) page + 1 else 1
        keyboard?.hide(); searchMessage = null
        task = scope.launch {
            searching = true
            try {
                val results = repository.search(p.id, actualSource, submitted, requestedPage, lyricMode)
                songs = if (append) (songs + results).distinctBy { "${it.source}:${it.id}" } else results
                more = results.size >= p.pageSize; page = requestedPage; lastQuery = submitted; searched = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { searchMessage = e.message ?: "搜索失败，请重试" }
            finally { searching = false }
        }
    }
    LaunchedEffect(allowed) { if (!allowed) { task?.cancel(); songs = emptyList(); qualityVisible = false; menu = false } }
    LaunchedEffect(config, provider?.id, actualSource) {
        task?.cancel(); songs = emptyList(); searched = false; more = false; qualityVisible = false
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
    HyperPage(if (lyricMode) "联网歌词" else "在线搜索", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item {
                OnlineSearchInput(query, { query = it.take(200) }, { search() }, busy,
                    if (lyricMode) "输入歌名，搜索歌词" else "搜索在线歌曲", { menu = true })
                if (provider != null) Text("${provider.name} · ${provider.sources[actualSource].orEmpty()}", Modifier.padding(start = PageTextInset, bottom = 12.dp), color = Muted, fontSize = 12.sp)
            }
            if (providers.isEmpty()) item { Text("请先在设置中导入支持${if (lyricMode) "歌词" else "音乐"}搜索的 API 配置", Modifier.padding(28.dp), color = Muted) }
            if (busy) item { Text(if (busyExternal) "正在处理…" else "正在搜索…", Modifier.padding(horizontal = PageTextInset, vertical = 12.dp), color = Muted) }
            val message = externalMessage ?: searchMessage
            if (message != null) item { Text(message, Modifier.padding(horizontal = PageTextInset, vertical = 12.dp), color = MiuixTheme.colorScheme.primary, fontSize = 14.sp) }
            if (searched && songs.isEmpty() && !busy) item { Text("没有找到${if (lyricMode) "歌词候选" else "相关歌曲"}，试试其他歌名或来源", Modifier.padding(28.dp), color = Muted) }
            items(songs, key = { "${it.providerId}:${it.source}:${it.id}" }) { song ->
                HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 4.dp).fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = PreferenceInset, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(song.title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(listOf(song.artist.ifBlank { "未知歌手" }, song.album.ifBlank { "未知专辑" }).joinToString(" · "), color = Muted, fontSize = 13.sp, maxLines = 2)
                            if (lyricMode && song.title.trim().equals(initialQuery.trim(), true)) Text("同名优先", color = MiuixTheme.colorScheme.primary, fontSize = 12.sp)
                        }
                        if (lyricMode) TextButton("获取", onClick = { if (!busy) onLyric(song) }, enabled = !busy)
                        else {
                            HyperIconButton(PlayerIcons.Play, "播放 ${song.title}", { if (!busy) { selected = song; download = false; qualityVisible = true } })
                            HyperIconButton(PlayerIcons.Download, "下载 ${song.title}", { if (!busy) { selected = song; download = true; qualityVisible = true } })
                        }
                    }
                }
            }
            if (more && !busy) item { TextButton("加载更多", onClick = { search(true) }, modifier = Modifier.padding(horizontal = PageGutter).fillMaxWidth()) }
        }
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
            Text(if (checkingQualities) "正在检查此歌曲可用音质…" else "仅可选择服务提供的音质；下载时识别实际格式", Modifier.padding(horizontal = DialogContentInset, vertical = 8.dp), fontSize = 13.sp, color = Muted)
            qualityOptions.forEach { option ->
                HyperSettingRow(option.quality.name, option.detail, value = if (option.available) null else "不可用",
                    onClick = if (option.available) { { qualityVisible = false; if (allowed && !busy) onSong(song, option.quality, download) } } else null)
            }
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
