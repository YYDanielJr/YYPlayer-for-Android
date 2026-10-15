package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.springAnimateToPage
import top.yukonga.miuix.kmp.utils.horizontalPagerSwipeOverride
import top.yukonga.miuix.kmp.utils.PagerGestureNestedScrollConnection
import yy.daniel.yyplayerforandroid.MainActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import yy.daniel.yyplayerforandroid.playback.*

internal enum class ThemeChoice(val label: String, val mode: ColorSchemeMode) {
    SYSTEM("跟随系统", ColorSchemeMode.System), LIGHT("浅色", ColorSchemeMode.Light), DARK("深色", ColorSchemeMode.Dark),
}
internal enum class AccentChoice(val label: String, val color: Color) {
    BLUE("小米蓝", Color(0xFF3482FF)), GREEN("青绿", Color(0xFF18A27B)), ORANGE("暖橙", Color(0xFFFF7800)),
}
internal enum class OrientationChoice(val label: String, val request: Int) {
    AUTO("自动", 0), PORTRAIT("竖屏", 1), LANDSCAPE("横屏", 2),
}

@Composable
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
fun YYPlayerApp(activity: MainActivity) {
    val runtime = activity.runtime
    val setupComplete by runtime.setup.completed.collectAsStateWithLifecycle()
    val playback = runtime.playback
    val preferences by runtime.preferences.collectAsStateWithLifecycle()
    val snapshot by playback.state.collectAsStateWithLifecycle()
    if (!setupComplete && !activity.musicOnBackStack) {
        SetupScreen(activity)
        return
    }
    MusicExpansionHost(activity) { music ->
        val libraryRevision by runtime.libraryRevision.collectAsStateWithLifecycle()
        var kind by rememberSaveable { mutableStateOf(MediaKind.AUDIO) }
        var library by remember { mutableStateOf(emptyList<LocalMedia>()) }
        var loading by remember { mutableStateOf(true) }
        var query by rememberSaveable { mutableStateOf("") }
        var searching by rememberSaveable { mutableStateOf(false) }
        var musicExpanded by music.expanded
        var addMusic by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        var navJob by remember { mutableStateOf<Job?>(null) }
        var hydration by remember { mutableStateOf<Job?>(null) }
        val reduceMotion = music.reduceMotion
        LaunchedEffect(runtime, libraryRevision) {
            hydration?.cancelAndJoin()
            library = withContext(Dispatchers.IO) { runtime.library.load() }; loading = false
            hydration = scope.launch { library = withContext(Dispatchers.IO) { runtime.library.refreshMetadata(library) } }
        }
        LaunchedEffect(notice) { if (notice != null) { delay(3500); notice = null } }
        fun import(documents: List<GrantedDocument>, mediaKind: MediaKind) {
            if (documents.isEmpty()) return
            scope.launch {
                hydration?.cancelAndJoin()
                loading = true
                runCatching { withContext(Dispatchers.IO) {
                    documents.forEach { it.persist(activity) }
                    runtime.library.add(documents.map { it.uri }, mediaKind)
                } }.onSuccess { library = it; notice = "已添加 ${documents.size} 个${if (mediaKind == MediaKind.AUDIO) "音频" else "视频"}" }
                    .onFailure { notice = "添加失败，请重新选择文件" }
                loading = false
            }
        }
        val audioPicker = rememberLauncherForActivityResult(LibraryDocumentPicker()) { import(it, MediaKind.AUDIO) }
        val videoPicker = rememberLauncherForActivityResult(LibraryDocumentPicker()) { import(it, MediaKind.VIDEO) }
        val directoryPicker = rememberLauncherForActivityResult(MusicTreePicker()) { selected ->
            if (selected != null) scope.launch {
                hydration?.cancelAndJoin(); loading = true
                runCatching { withContext(Dispatchers.IO) {
                    selected.persist(activity)
                    runtime.library.add(runtime.lyrics.documents.musicIn(selected.uri), MediaKind.AUDIO)
                } }.onSuccess { invalidateMediaArtwork(); library = it; notice = "音乐目录已添加，同目录歌词可自动读取" }
                    .onFailure { notice = it.message ?: "目录读取失败" }
                loading = false
            }
        }
        fun pick() { if (kind == MediaKind.AUDIO) addMusic = true else videoPicker.launch(arrayOf("video/*")) }
        fun openCurrent() { if (snapshot.isVideo) activity.openPlayer() else if (snapshot.current != null) musicExpanded = true }
        fun play(media: LocalMedia) {
            val queue = library.filter { it.kind == media.kind }
            playback.play(queue, queue.indexOfFirst { it.uri == media.uri }.coerceAtLeast(0))
            kind = media.kind
            if (media.kind == MediaKind.VIDEO) activity.openPlayer() else musicExpanded = true
        }
        fun remove(media: LocalMedia) {
            scope.launch {
                hydration?.cancelAndJoin()
                library = withContext(Dispatchers.IO) { runtime.library.remove(media) }
                runtime.removeFavorite(media.uri.toString())
                notice = "已从媒体库移除，原文件保留"
            }
        }
        fun manage(items: List<LocalMedia>, action: MusicBatchAction) {
            val keys = items.map { it.identity }.toSet()
            if (keys.isEmpty()) return
            when (action) {
                MusicBatchAction.PLAY -> { playback.play(items, 0); musicExpanded = true }
                MusicBatchAction.FAVORITE -> { runtime.setFavorites(keys, true); notice = "已收藏 ${keys.size} 首歌曲" }
                MusicBatchAction.UNFAVORITE -> { runtime.setFavorites(keys, false); notice = "已取消收藏 ${keys.size} 首歌曲" }
                MusicBatchAction.REMOVE -> scope.launch {
                    hydration?.cancelAndJoin()
                    library = withContext(Dispatchers.IO) { runtime.library.removeAll(keys) }
                    runtime.setFavorites(keys, false)
                    notice = "已移除 ${keys.size} 首歌曲，原文件保留"
                }
            }
        }
        BackHandler(searching && !musicExpanded) { searching = false; query = "" }
        val backdrop = rememberHyperBackdrop()
        val audioPage = rememberLibraryPageController()
        val videoPage = rememberLibraryPageController()
        val musicList = rememberLazyListState()
        val videoGrid = rememberLazyGridState()
        Scaffold(containerColor = MiuixTheme.colorScheme.background, contentWindowInsets = WindowInsets(0, 0, 0, 0)) {
            BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background)) {
                val wide = maxWidth >= 600.dp && maxWidth > maxHeight
                val pager = rememberPagerState(initialPage = if (kind == MediaKind.AUDIO) 0 else 1, pageCount = { 2 })
                // Title, actions and tabs are hoisted out of the pager, so only the content below the tabs slides.
                // The title follows the pager offset instead of a fixed timeline, so the fade tracks the gesture
                // and swaps the text exactly when the pages cross.
                val titleAlpha = { if (reduceMotion) 1f else 1f - (abs(pager.currentPageOffsetFraction) * 2f).coerceIn(0f, 1f) }
                val activePage = if (pager.currentPage == 0) audioPage else videoPage
                val headerTitle = if (pager.currentPage == 0) "音乐" else "视频"
                LaunchedEffect(pager.currentPage) {
                    kind = if (pager.currentPage == 0) MediaKind.AUDIO else MediaKind.VIDEO
                    query = ""
                }
                fun select(index: Int) {
                    navJob?.cancel()
                    navJob = scope.launch { if (preferences.reduceMotion) pager.scrollToPage(index) else pager.springAnimateToPage(index) }
                }
                Row(Modifier.fillMaxSize().then(music.backgroundModifier)) {
                    if (wide) LibrarySidebar(pager.currentPage, ::select, activity::openSettings)
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        HyperPage(title = headerTitle, headerAlpha = titleAlpha,
                            tabs = if (wide) null else { { LibraryTabs(pager.currentPage, ::select) } },
                            actions = {
                                LibraryHeaderActions(isMusic = pager.currentPage == 0, managing = activePage.managing,
                                    onSearch = { searching = !searching; if (!searching) query = "" },
                                    onOnline = if (preferences.onlineMusic) activity::openOnlineSearch else null,
                                    onSettings = activity::openSettings, onImport = ::pick,
                                    onExitManagement = activePage::finish)
                            }) { padding ->
                            Box(Modifier.fillMaxSize()) {
                                HorizontalPager(pager, Modifier.fillMaxSize().hyperCapture(backdrop)
                                    .horizontalPagerSwipeOverride(pager, enabled = !searching && !preferences.reduceMotion, onIntercepted = { navJob?.cancel() }),
                                    userScrollEnabled = preferences.reduceMotion && !searching,
                                    pageNestedScrollConnection = PagerGestureNestedScrollConnection) { index ->
                                    val mediaKind = if (index == 0) MediaKind.AUDIO else MediaKind.VIDEO
                                    LibraryContent(mediaKind, if (index == 0) audioPage else videoPage, musicList, videoGrid, padding,
                                        library.filter { it.kind == mediaKind }, snapshot, preferences.favorites,
                                        loading, searching, query, { query = it }, { searching = false; query = "" },
                                        onImport = ::pick, onPlay = ::play,
                                        onFavorite = { runtime.toggleFavorite(it.uri.toString()) }, onRemove = ::remove,
                                        bottomSpace = if (snapshot.current != null) 128.dp else 28.dp,
                                        onBatch = ::manage, isActive = index == pager.currentPage, collections = runtime.collections,
                                        onPlayQueue = { items, start -> if (items.isNotEmpty()) { playback.play(items, start); musicExpanded = true } },
                                        onEnqueue = { items, next -> playback.enqueue(items, next); notice = if (next) "已安排下一首播放" else "已加入播放队列" })
                                }
                                if (snapshot.current != null) LibraryMiniPlayer(snapshot, playback, backdrop, ::openCurrent,
                                    Modifier.align(Alignment.BottomCenter).widthIn(max = 520.dp).fillMaxWidth()
                                        .imePadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), reduceMotion,
                                    artworkModifier = music.artworkModifier)
                            }
                        }
                    }
                }
                if (notice != null) HyperNotice(notice!!, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 108.dp))

            }
        }
        HyperDialog("添加音乐", { addMusic = false }, show = addMusic) {
            HyperSettingRow("选择歌曲", "添加一个或多个音频文件", onClick = { addMusic = false; audioPicker.launch(arrayOf("audio/*")) })
            HyperSettingRow("选择音乐目录", "读取歌曲标签，并自动匹配同目录歌词", onClick = { addMusic = false; directoryPicker.launch(Unit) })
        }
    }
}
