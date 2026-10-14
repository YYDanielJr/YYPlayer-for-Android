package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.*

@Composable
internal fun MusicLibraryBrowser(media: List<LocalMedia>, snapshot: PlaybackSnapshot, favorites: Set<String>,
    controller: LibraryPageController, listState: LazyListState, padding: PaddingValues, bottomSpace: Dp,
    loading: Boolean, searching: Boolean, query: String, onQuery: (String) -> Unit, onCloseSearch: () -> Unit,
    onImport: () -> Unit, onFavorite: (LocalMedia) -> Unit, onRemove: (LocalMedia) -> Unit,
    onBatch: (List<LocalMedia>, MusicBatchAction) -> Unit, isActive: Boolean,
    repository: MusicCollectionRepository?, onPlay: (List<LocalMedia>, Int) -> Unit,
    onEnqueue: (List<LocalMedia>, Boolean) -> Unit) {
    val collections by repository?.state?.collectAsState() ?: remember { mutableStateOf(MusicCollections()) }
    var category by rememberSaveable { mutableIntStateOf(0) }
    var groupArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var groupAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistId by rememberSaveable { mutableStateOf<String?>(null) }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<LocalMedia?>(null) }
    var menu by remember { mutableStateOf(false) }
    var addMembers by remember { mutableStateOf<List<String>?>(null) }
    var editName by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<String?>(null) }
    var deleteList by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun update(change: (MusicCollections) -> MusicCollections) {
        scope.launch { try { repository?.update(change) } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "歌单保存失败" } }
    }
    val playlist = collections.lists.firstOrNull { it.id == playlistId }
    val groups = remember(media, category) { groupMusic(media, category == 2) }
    val group = groups.firstOrNull { it.key == (groupArtist to groupAlbum) }
    val lookup = remember(media) { media.associateBy { it.identity } }
    val tracks = when {
        onlyFavorites -> media.filter { it.identity in favorites }
        category == 3 -> playlist?.members?.mapNotNull(lookup::get).orEmpty()
        category in 1..2 -> group?.tracks.orEmpty()
        else -> media
    }
    val filtered = remember(tracks, query) { tracks.filter { item -> listOfNotNull(item.title, item.songTitle, item.artist, item.album).any { it.contains(query, true) } } }
    val grouped = category in 1..2 && groupArtist == null || category == 3 && playlistId == null && !onlyFavorites
    fun back() { groupArtist = null; groupAlbum = null; playlistId = null; onlyFavorites = false; controller.finish() }
    fun play(item: LocalMedia) { onPlay(filtered, filtered.indexOfFirst { it.identity == item.identity }.coerceAtLeast(0)) }
    val selectedTracks = tracks.filter { it.identity in controller.selectedKeys }
    fun batch(action: MusicBatchAction) { onBatch(selectedTracks, action); controller.finish() }
    LaunchedEffect(media) { controller.retain(media) }
    LaunchedEffect(isActive) { if (!isActive) controller.finish() }
    BackHandler(isActive && !controller.managing && (groupArtist != null || playlistId != null || onlyFavorites)) { back() }
    BackHandler(isActive && controller.managing) { controller.finish() }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomSpace)) {
        if (searching) item { SearchInput(query, onQuery, onCloseSearch, Modifier.padding(horizontal = PageGutter)) }
        item {
            TabRowWithContour(listOf("歌曲", "歌手", "专辑", "歌单"), category, {
                category = it; back(); scope.launch { listState.scrollToItem(0) }
            }, Modifier.fillMaxWidth().padding(horizontal = PageGutter, vertical = 8.dp), height = 40.dp)
        }
        if (grouped) {
            item { HyperSection(if (category == 3) "我的歌单" else if (category == 2) "按专辑浏览" else "按歌手浏览",
                if (category == 3) "新建歌单" else null, { renameId = null; name = ""; editName = true }) }
            if (category == 3) item {
                HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 4.dp)) {
                    HyperSettingRow("我的收藏", "${media.count { it.identity in favorites }} 首", onClick = { onlyFavorites = true })
                }
            }
            if (category == 3) items(collections.lists.filter { it.name.contains(query, true) }, key = { it.id }) { entry ->
                HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 4.dp)) {
                    val available = entry.members.count(lookup::containsKey)
                    HyperSettingRow(entry.name, "$available 首可播放 · ${entry.members.size} 首已保存", onClick = { playlistId = entry.id })
                }
            } else items(groups.filter { it.name.contains(query, true) || it.subtitle.contains(query, true) }, key = { "${it.key.first.length}:${it.key.first}${it.key.second}" }) { entry ->
                HyperCard(Modifier.padding(horizontal = PageGutter, vertical = 4.dp)) {
                    HyperSettingRow(entry.name, "${entry.subtitle} · ${entry.tracks.size} 首", onClick = { groupArtist = entry.key.first; groupAlbum = entry.key.second })
                }
            }
            if (category == 3 && collections.lists.isEmpty()) item { Text("创建歌单后，在歌曲菜单或多选管理中添加歌曲。", Modifier.padding(28.dp), color = Muted) }
        } else {
            item { HyperSection(playlist?.name ?: group?.name ?: if (onlyFavorites) "我的收藏" else "本地音乐",
                if (category != 0) "返回分类" else if (filtered.isNotEmpty()) "播放全部" else null,
                { if (category != 0) back() else onPlay(filtered, 0) }) }
            if (category != 0) item {
                Column(Modifier.padding(horizontal = PageGutter).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HyperActionButton("播放全部", { onPlay(filtered, 0) }, Modifier.weight(1f), enabled = filtered.isNotEmpty())
                        HyperActionButton("加入队列", { onEnqueue(filtered, false) }, Modifier.weight(1f), primary = false, enabled = filtered.isNotEmpty())
                    }
                    if (playlist != null) {
                        HyperCard {
                            HyperSettingRow("重命名歌单", onClick = { renameId = playlist.id; name = playlist.name; editName = true })
                            HyperSettingRow("删除歌单", onClick = { deleteList = true })
                        }
                    }
                }
            }
            if (controller.managing) item {
                HyperSection("已选择 ${controller.selectedKeys.size} 首", "全选当前列表", { controller.add(filtered.map { it.identity }) })
                HyperCard(Modifier.padding(horizontal = PageGutter)) {
                    HyperSettingRow("播放所选", onClick = { if (selectedTracks.isNotEmpty()) batch(MusicBatchAction.PLAY) })
                    HyperSettingRow("加入队列", onClick = { if (selectedTracks.isNotEmpty()) { onEnqueue(selectedTracks, false); controller.finish() } })
                    HyperSettingRow("加入歌单", onClick = { if (selectedTracks.isNotEmpty()) addMembers = selectedTracks.map { it.identity } })
                    HyperSettingRow("收藏", onClick = { batch(MusicBatchAction.FAVORITE) })
                    HyperSettingRow("取消收藏", onClick = { batch(MusicBatchAction.UNFAVORITE) })
                    if (playlist != null) HyperSettingRow("移出歌单", onClick = { val keys = controller.selectedKeys.toSet(); update { it.remove(playlist.id, keys) }; controller.finish() })
                    HyperSettingRow("从媒体库移除", onClick = { if (selectedTracks.isNotEmpty()) controller.confirmRemove = true })
                }
                Spacer(Modifier.height(16.dp))
            }
            if (filtered.isEmpty() && !loading) item { Text(if (playlist != null) "歌单中暂无可播放歌曲；可从音乐库添加，失去授权的歌曲重新导入后会恢复。" else "暂无歌曲", Modifier.padding(28.dp), color = Muted) }
            itemsIndexed(filtered, key = { _, track -> track.identity }) { index, track ->
                HyperListRow(index == 0, index == filtered.lastIndex, Modifier.padding(horizontal = PageGutter)) {
                    MusicRow(track, snapshot.current?.identity == track.identity,
                        { if (controller.managing) controller.toggle(track) else play(track) },
                        { selected = track; menu = true }, { controller.start(track) }, controller.managing, track.identity in controller.selectedKeys)
                }
            }
        }
        if (loading) item { Text("正在读取媒体…", Modifier.padding(28.dp), color = Muted) }
        if (media.isEmpty() && !loading && category == 0) item { HyperActionButton("选择文件", onImport, Modifier.padding(horizontal = PageGutter)) }
        if (message != null) item { Text(message!!, Modifier.padding(28.dp), color = Muted) }
    }
    HyperDialog("媒体选项", { menu = false }, show = menu) {
        selected?.let { track ->
            HyperSettingRow(track.musicTitle, track.musicSubtitle)
            HyperSettingRow("播放", onClick = { play(track); menu = false })
            HyperSettingRow("下一首播放", onClick = { onEnqueue(listOf(track), true); menu = false })
            HyperSettingRow("加入播放队列", onClick = { onEnqueue(listOf(track), false); menu = false })
            HyperSettingRow("加入歌单", onClick = { addMembers = listOf(track.identity); menu = false })
            HyperSettingRow(if (track.identity in favorites) "取消收藏" else "收藏", onClick = { onFavorite(track); menu = false })
            HyperSettingRow("多选管理", onClick = { controller.start(track); menu = false })
            if (playlist != null) {
                HyperSettingRow("移出歌单", "保留音乐库与原文件", onClick = { update { it.remove(playlist.id, setOf(track.identity)) }; menu = false })
                HyperSettingRow("在歌单中上移", onClick = { update { it.move(playlist.id, track.identity, -1) }; menu = false })
                HyperSettingRow("在歌单中下移", onClick = { update { it.move(playlist.id, track.identity, 1) }; menu = false })
            }
            HyperSettingRow("从媒体库移除", "保留原文件与歌单成员记录", onClick = { onRemove(track); menu = false })
        }
    }
    HyperDialog("加入歌单", { addMembers = null }, show = addMembers != null && !editName) {
        HyperSettingRow("新建歌单", onClick = { renameId = null; name = ""; editName = true })
        collections.lists.forEach { entry -> HyperSettingRow(entry.name, onClick = {
            val members = addMembers.orEmpty(); update { it.add(entry.id, members) }; addMembers = null; controller.finish()
        }) }
    }
    LaunchedEffect(editName) { nameError = null }
    HyperDialog(if (renameId == null) "新建歌单" else "重命名歌单", { editName = false }, show = editName) {
        BasicTextField(name, { name = it.take(80); nameError = null }, Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 48.dp)
            .semantics { contentDescription = "歌单名称" }, singleLine = true,
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 18.sp),
            decorationBox = { field -> if (name.isEmpty()) Text("输入歌单名称", color = Muted); field() })
        if (nameError != null) Text(nameError!!, Modifier.padding(horizontal = 24.dp), color = Muted)
        HyperActionButton("保存", {
            val id = renameId; val title = name; val members = addMembers.orEmpty()
            scope.launch { try {
                repository?.update { old -> if (id != null) old.rename(id, title) else old.create(title).let { it.add(it.lists.last().id, members) } }
                editName = false; addMembers = null; controller.finish()
            } catch (e: CancellationException) { throw e } catch (e: Exception) { nameError = e.message } }
        }, Modifier.padding(horizontal = 24.dp))
    }
    HyperDialog("删除歌单", { deleteList = false }, show = deleteList) {
        HyperSettingRow("删除 ${playlist?.name.orEmpty()}", "保留原文件、音乐库与播放队列", onClick = {
            val id = playlistId; update { it.delete(id.orEmpty()) }; deleteList = false; back()
        })
    }
    HyperDialog("移除所选歌曲", { controller.confirmRemove = false }, show = controller.confirmRemove) {
        HyperSettingRow("移除 ${controller.selectedKeys.size} 首歌曲", "设备中的原文件仍会保留", onClick = { batch(MusicBatchAction.REMOVE) })
    }
}
