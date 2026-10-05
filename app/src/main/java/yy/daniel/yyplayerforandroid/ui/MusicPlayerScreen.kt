package yy.daniel.yyplayerforandroid.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable
import yy.daniel.yyplayerforandroid.playback.*
import kotlin.math.abs

/** Event-driven background, no perpetual animation or synthetic playback clock. */
@Composable
internal fun rememberMusicReduceMotion(preference: Boolean): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context, lifecycle) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        val resumed = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) enabled = ValueAnimator.areAnimatorsEnabled() }
        lifecycle.addObserver(resumed)
        onDispose { context.contentResolver.unregisterContentObserver(observer); lifecycle.removeObserver(resumed) }
    }
    return preference || !enabled
}

/** Called inside MainActivity only; video continues to use the native Activity stack. */
@Composable
internal fun MusicPlayerScreen(snapshot: PlaybackSnapshot, backend: PlaybackBackend, repository: MusicLyricsRepository,
    reduceMotion: Boolean, favorite: Boolean, onFavorite: () -> Unit, onBack: () -> Unit, onOnlineLyrics: (() -> Unit)? = null,
    lyricStyle: LyricStyle = LyricStyle()) {
    val current = snapshot.current ?: return
    val details = rememberMediaDetails(current)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var wideLyrics by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    var centered by remember { mutableStateOf(repository.centered) }
    var offset by remember { mutableLongStateOf(repository.offsetMs) }
    var revision by remember { mutableIntStateOf(0) }
    var requested by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var lyricState by remember(current.uri, snapshot.mediaGeneration) { mutableStateOf(MusicLyrics()) }
    var loading by remember(current.uri, snapshot.mediaGeneration) { mutableStateOf(true) }
    val onlineRevision by repository.revision.collectAsState()
    val latest by rememberUpdatedState(current)
    LaunchedEffect(current.uri, snapshot.mediaGeneration, revision, onlineRevision) {
        loading = true
        try { lyricState = repository.read(current) } finally { loading = false }
    }
    LaunchedEffect(notice) { if (notice != null) { delay(4000); notice = null } }
    val picker = rememberLauncherForActivityResult(MusicLyricPicker()) { result ->
        val identity = requested; requested = null
        val media = snapshot.queue.firstOrNull { it.uri.toString() == identity }
        if (result != null && media != null) scope.launch {
            importing = true
            try {
                result.persist(context)
                val read = repository.import(media, result.uri)
                if (latest.uri == media.uri) { lyricState = read; revision++ }
                notice = "歌词已添加"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = e.message ?: "歌词添加失败" }
            finally { importing = false }
        }
    }
    val treePicker = rememberLauncherForActivityResult(MusicTreePicker()) { result ->
        if (result != null) scope.launch {
            try { result.persist(context); repository.documents.rememberTree(result.uri); invalidateMediaArtwork(); revision++; notice = "已授权目录，正在查找同名歌词" }
            catch (e: Exception) { notice = e.message ?: "目录授权失败" }
        }
    }
    fun addLyrics() { menu = null; requested = current.uri.toString(); picker.launch(Unit) }
    fun select(page: Int) { pageJob?.cancel(); pageJob = scope.launch { if (reduceMotion) pager.scrollToPage(page) else pager.animateScrollToPage(page, animationSpec = tween(500, easing = FastOutSlowInEasing)) } }
    val dark = MiuixTheme.colorScheme.background.luminance() < .5f
    val foreground = if (dark) Color(0xFFF7F5F1) else Color(0xFF242C35)
    var palette by remember { mutableStateOf(details.coverColor) }
    LaunchedEffect(current.uri, details.loaded, details.coverColor) { if (details.loaded) palette = details.coverColor }
    val theme by animateColorAsState(palette, tween(if (reduceMotion) 0 else 950), label = "music colour")
    val title = details.title ?: current.musicTitle
    val subtitle = listOf(details.artist ?: current.artist ?: "未知歌手", details.album ?: current.album ?: "未知专辑").joinToString(" · ")

    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        // A full-window hit target keeps blank header/background taps out of the library below.
        // Observe without consuming: the native pager and child controls own their gestures.
        awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) }
    }.semantics { stateDescription = "音乐详情" }) {
        MusicAmbientBackground(theme, dark)
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            val wide = maxWidth > maxHeight * 1.2f
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                    MusicAction(PlayerIcons.Back, "收起音乐详情", onBack, foreground, reduceMotion,
                        modifier = Modifier.graphicsLayer { rotationZ = -90f })
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        listOf("封面", "歌词").forEachIndexed { index, label ->
                            val selected = if (wide) wideLyrics == (index == 1) else pager.currentPage == index
                            Surface(onClick = { if (wide) wideLyrics = index == 1 else select(index) }, color = if (selected) foreground.copy(alpha = .09f) else Color.Transparent,
                                shape = CircleShape, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)) {
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), contentAlignment = Alignment.Center) {
                                    Text(label, color = foreground.copy(alpha = if (selected) 1f else .5f), fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                        }
                    }
                    MusicAction(PlayerIcons.More, "歌词与播放选项", { menu = "options" }, foreground, reduceMotion)
                }
                if (wide) {
                    AnimatedContent(wideLyrics, Modifier.weight(1f).fillMaxWidth(), transitionSpec = {
                        fadeIn(tween(if (reduceMotion) 0 else 500)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 350))
                    }, label = "wide cover to lyrics") { lyricsOnly ->
                        Row(Modifier.fillMaxSize()) {
                            if (!lyricsOnly) MusicCover(current, title, subtitle, Modifier.weight(1f).fillMaxHeight(), foreground, reduceMotion, onLyrics = { wideLyrics = true })
                            MusicLyricsPane(lyricState, snapshot, backend, loading, centered, offset, reduceMotion, foreground,
                                onAdd = ::addLyrics, onAuthorize = { treePicker.launch(Unit) }, modifier = Modifier.weight(1f).fillMaxHeight(), onOnline = onOnlineLyrics, style = lyricStyle)
                        }
                    }
                } else {
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1,
                        flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(stiffness = 322.2f, dampingRatio = .9f))) { page ->
                        if (page == 0) MusicCover(current, title, subtitle, Modifier.fillMaxSize(), foreground, reduceMotion, pager, onLyrics = { select(1) })
                        else MusicLyricsPane(lyricState, snapshot, backend, loading, centered, offset, reduceMotion, foreground,
                            onAdd = ::addLyrics, onAuthorize = { treePicker.launch(Unit) }, modifier = Modifier.fillMaxSize().musicTrail(pager, 1, reduceMotion, 220f, .10f), onOnline = onOnlineLyrics, style = lyricStyle)
                    }
                }
                if (snapshot.error != null) Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(snapshot.error, Modifier.weight(1f), fontSize = 13.sp, maxLines = 2, color = foreground)
                    Text("重试", Modifier.clickable { backend.play(snapshot.queue, snapshot.queueIndex) }.padding(16.dp), color = foreground, fontSize = 14.sp)
                }
                AnimatedContent(targetState = wide || pager.settledPage == 1,
                    transitionSpec = { (fadeIn(tween(if (reduceMotion) 0 else 220)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 120)))
                        .using(SizeTransform(sizeAnimationSpec = { _, _ -> if (reduceMotion) snap() else spring(stiffness = 320f, dampingRatio = .9f) })) }, label = "music controls") { compact ->
                    MusicControls(snapshot, backend, title, subtitle, foreground, reduceMotion, compact, favorite, onFavorite,
                        onQueue = { menu = "queue" }, onSpeed = { menu = "speed" })
                }
            }
        }
        if (notice != null) Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 66.dp, start = 24.dp, end = 24.dp),
            shape = RoundedCornerShape(18.dp), color = MiuixTheme.colorScheme.surface) {
            Text(notice!!, Modifier.padding(16.dp), fontSize = 14.sp)
        }
    }
    HyperDialog("歌词与播放", { menu = null }, show = menu == "options") {
        if (current.onlineQuality != null) HyperSettingRow("在线音质", current.onlineQuality)
        if (onOnlineLyrics != null) HyperSettingRow("联网获取歌词", "按当前歌名查找，也可手动搜索", onClick = { menu = null; onOnlineLyrics() })
        HyperSettingRow("添加歌词", if (importing) "正在读取…" else "选择 LRC 或 TXT", onClick = { if (!importing) addLyrics() })
        HyperSettingRow("授权歌曲目录", "自动读取同目录的同名 LRC", onClick = { menu = null; treePicker.launch(Unit) })
        HyperSpinnerPreference("歌词对齐", listOf(DropdownItem("左对齐"), DropdownItem("居中")), if (centered) 1 else 0,
            { centered = it == 1; repository.setCentered(centered) })
        HyperSettingRow("歌词时间偏移", "正值提前显示，负值延后显示", value = "${offset / 1000f} 秒")
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton("−0.5 秒", onClick = { offset = (offset - 500).coerceAtLeast(-600_000); repository.setOffset(offset) })
            TextButton("重置", onClick = { offset = 0; repository.setOffset(0) })
            TextButton("+0.5 秒", onClick = { offset = (offset + 500).coerceAtMost(600_000); repository.setOffset(offset) })
        }
        HyperSettingRow("恢复自动歌词", "移除当前曲目的手动关联", onClick = { menu = null; scope.launch { lyricState = repository.clear(current); revision++ } })
        HyperSettingRow("播放速度", value = speedLabel(snapshot.speed), onClick = { menu = "speed" })
        HyperSettingRow("播放队列", value = "${snapshot.queue.size} 首", onClick = { menu = "queue" })
        HyperSettingRow("媒体信息", onClick = { menu = "info" })
    }
    SpeedDialog(snapshot.speed, backend::setSpeed, show = menu == "speed") { menu = null }
    SelectionDialog("播放队列 · ${snapshot.queue.size}", menu == "queue", snapshot.queue.map { DropdownItem(it.musicTitle, summary = it.musicSubtitle) },
        snapshot.queueIndex, onSelect = { backend.play(snapshot.queue, it) }, onDismiss = { menu = null })
    MediaInfoDialog(snapshot, menu == "info") { menu = null }
}

@Composable
private fun MusicAmbientBackground(theme: Color, dark: Boolean) {
    val base = if (dark) lerp(Color(0xFF11151B), theme, .17f) else lerp(Color(0xFFF4F4F1), theme, .12f)
    Canvas(Modifier.fillMaxSize()) {
        drawRect(base)
        drawRect(Brush.radialGradient(listOf(theme.copy(alpha = if (dark) .46f else .29f), Color.Transparent),
            center = Offset(size.width * .08f, size.height * .25f), radius = size.height * .75f))
        drawRect(Brush.radialGradient(listOf(lerp(theme, if (dark) Color(0xFF96B8D0) else Color.White, .38f).copy(alpha = if (dark) .21f else .65f), Color.Transparent),
            center = Offset(size.width * 1.1f, size.height * .56f), radius = size.height * .62f))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = .16f), base.copy(alpha = .8f))))
    }
}

/** Page translation is 1:1 with the finger. Springs only add small, bounded element lag. */
@Composable
private fun Modifier.musicTrail(pager: PagerState, page: Int, reduced: Boolean, stiffness: Float, travel: Float): Modifier {
    val position by remember(pager, page) { derivedStateOf { (pager.currentPage - page + pager.currentPageOffsetFraction).coerceIn(-1f, 1f) } }
    val follower by animateFloatAsState(position, if (reduced) snap() else spring(dampingRatio = .82f, stiffness = stiffness), label = "music element follower")
    return graphicsLayer {
        val lag = if (reduced) 0f else (position - follower).coerceIn(-.28f, .28f)
        translationX = lag * size.width * travel
        rotationZ = lag * 2.5f
    }
}

@Composable
private fun MusicCover(current: LocalMedia, title: String, subtitle: String, modifier: Modifier, foreground: Color, reduceMotion: Boolean,
    pager: PagerState? = null, onLyrics: () -> Unit) {
    val incoming = rememberMediaDetails(current)
    var displayed by remember { mutableStateOf(current) }
    LaunchedEffect(current, incoming.loaded) { if (incoming.loaded) displayed = current }
    val interactions = remember { MutableInteractionSource() }
    val tilt = remember(reduceMotion) { if (reduceMotion) null else TiltFeedback() }
    val shape = RoundedCornerShape(24.dp)
    BoxWithConstraints(modifier) {
        val coverSize = minOf(maxWidth - 64.dp, maxHeight * .69f, 420.dp).coerceAtLeast(88.dp)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val artModifier = Modifier.size(coverSize).then(if (pager != null) Modifier.musicTrail(pager, 0, reduceMotion, 340f, .2f) else Modifier)
                .pressable(interactions, indication = tilt, delay = null)
                .shadow(24.dp, shape, clip = false).clip(shape).background(MiuixTheme.colorScheme.surfaceContainerHigh)
                .clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onLyrics)
                .semantics { contentDescription = "专辑封面，切换到歌词" }
            // One persistent rounded layer owns the shadow. Only the artwork fades inside it.
            Box(artModifier) {
                AnimatedContent(displayed, modifier = Modifier.fillMaxSize(), transitionSpec = {
                    (fadeIn(tween(if (reduceMotion) 0 else 450)) togetherWith fadeOut(tween(if (reduceMotion) 0 else 350)))
                        .using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))
                }, label = "music cover") { media -> MediaArtwork(media, Modifier.fillMaxSize(), 24.dp) }
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().then(if (pager != null) Modifier.musicTrail(pager, 0, reduceMotion, 210f, .30f) else Modifier)) {
                Text(title, color = foreground, fontSize = 27.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(subtitle, color = foreground.copy(alpha = .64f), fontSize = 15.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MusicLyricsPane(state: MusicLyrics, snapshot: PlaybackSnapshot, backend: PlaybackBackend, loading: Boolean, centered: Boolean,
    offset: Long, reduced: Boolean, foreground: Color, onAdd: () -> Unit, onAuthorize: () -> Unit, modifier: Modifier,
    onOnline: (() -> Unit)? = null, style: LyricStyle = LyricStyle()) {
    val lyrics = state.lyrics
    val plainLines by produceState(emptyList<String>(), lyrics.plain) {
        value = withContext(Dispatchers.Default) { lyrics.plain.trim().lines() }
    }
    val active = lyrics.active(snapshot.positionMs, offset)
    val list = key(snapshot.current?.uri, snapshot.mediaGeneration) { rememberLazyListState() }
    val dragging by list.interactionSource.collectIsDraggedAsState()
    var browsing by remember(snapshot.current?.uri) { mutableStateOf(false) }
    LaunchedEffect(dragging) { if (dragging) browsing = true else if (browsing) { delay(4000); browsing = false } }
    BoxWithConstraints(modifier) {
        val padding = maxHeight * .36f
        val focusIndex = if (active >= 0) lyrics.lines.indexOfFirst { it.milliseconds == lyrics.lines[active].milliseconds } else -1
        LaunchedEffect(focusIndex, browsing, snapshot.current?.uri, snapshot.mediaGeneration, style) {
            if (focusIndex >= 0 && !browsing) {
                if (reduced) list.scrollToItem(focusIndex) else list.animateScrollToItem(focusIndex)
            }
        }
        LaunchedEffect(snapshot.current?.uri, state) { if (focusIndex < 0) list.scrollToItem(0) }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在读取歌词…", color = foreground.copy(alpha = .55f), fontSize = 16.sp) }
            lyrics.lines.isEmpty() && lyrics.plain.isBlank() -> Column(Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("无歌词", color = foreground.copy(alpha = .55f), fontWeight = FontWeight.SemiBold, fontSize = 30.sp)
                Spacer(Modifier.height(18.dp))
                Text(state.warning ?: "让文字与音乐相遇", color = foreground.copy(alpha = .5f), fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text("添加歌词", Modifier.clip(CircleShape).background(foreground.copy(alpha = .09f)).clickable(onClick = onAdd).padding(horizontal = 24.dp, vertical = 15.dp),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, color = foreground)
                Text("授权同目录歌词", Modifier.clickable(onClick = onAuthorize).padding(16.dp), color = foreground.copy(alpha = .65f), fontSize = 14.sp)
                if (onOnline != null) Text("联网获取歌词", Modifier.clickable(onClick = onOnline).padding(16.dp), color = foreground, fontSize = 14.sp)
            }
            else -> {
                LazyColumn(state = list, contentPadding = PaddingValues(start = 32.dp, end = 32.dp, top = padding, bottom = maxHeight - padding),
                    verticalArrangement = Arrangement.spacedBy(style.lineSpacing.dp), modifier = Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(Brush.verticalGradient(0f to Color.Transparent, .08f to Color.Black, .88f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                    }) {
                    if (lyrics.lines.isNotEmpty()) items(lyrics.lines.size, key = { it }) { index ->
                        val line = lyrics.lines[index]
                        val selected = active >= 0 && line.milliseconds == lyrics.lines[active].milliseconds
                        val lineColor by animateColorAsState(if (selected) style.activeColor?.let(::Color) ?: foreground else
                            style.inactiveColor?.let(::Color) ?: foreground.copy(alpha = if (index < active) .35f else .46f),
                            tween(if (reduced) 0 else 350), label = "lyric emphasis")
                        val fontSize by animateFloatAsState(if (selected) style.activeSize else style.inactiveSize, tween(if (reduced) 0 else 200), label = "lyric font size")
                        val scale by animateFloatAsState(if (selected) 1f else .96f, if (reduced) snap() else spring(stiffness = 240f, dampingRatio = .85f), label = "lyric line spring")
                        Text(line.text.ifBlank { "•••" }, Modifier.fillMaxWidth().graphicsLayer {
                            scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(if (centered) .5f else 0f, .5f)
                        }.semantics { stateDescription = if (selected) "当前歌词，点按跳转" else "点按跳转到 ${formatTime(line.milliseconds)}" }
                            .clickable(role = Role.Button) { browsing = false; backend.seekTo((line.milliseconds - offset).coerceAtLeast(0)) }
                            .padding(vertical = 7.dp).heightIn(min = 48.dp),
                            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                            fontSize = fontSize.sp, lineHeight = (fontSize * 1.35f).sp,
                            fontWeight = FontWeight(if (selected) style.activeWeight else style.inactiveWeight), color = lineColor)
                    } else items(plainLines.size) { index ->
                        Text(plainLines[index], Modifier.fillMaxWidth().padding(vertical = 7.dp), fontSize = style.inactiveSize.sp,
                            lineHeight = (style.inactiveSize * 1.35f).sp, fontWeight = FontWeight(style.inactiveWeight),
                            color = style.inactiveColor?.let(::Color) ?: foreground.copy(alpha = .82f), textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                    }
                }
                if (browsing) Text("回到当前歌词", Modifier.align(Alignment.BottomCenter).clip(CircleShape).background(foreground.copy(alpha = .1f))
                    .clickable { browsing = false }.padding(horizontal = 18.dp, vertical = 14.dp), color = foreground, fontSize = 13.sp)
                if (state.warning != null) Text(state.warning, Modifier.align(Alignment.TopCenter).padding(16.dp), color = foreground.copy(alpha = .65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MusicControls(snapshot: PlaybackSnapshot, backend: PlaybackBackend, title: String, subtitle: String, foreground: Color,
    reduced: Boolean, compact: Boolean, favorite: Boolean, onFavorite: () -> Unit, onQueue: () -> Unit, onSpeed: () -> Unit) {
    var dragging by remember(snapshot.current?.uri, snapshot.mediaGeneration) { mutableStateOf(false) }
    var value by remember(snapshot.current?.uri, snapshot.mediaGeneration) { mutableFloatStateOf(0f) }
    val progress = if (dragging) value else snapshot.progress
    Column(Modifier.fillMaxWidth().padding(horizontal = if (compact) 24.dp else 32.dp).padding(top = 4.dp, bottom = if (compact) 12.dp else 8.dp)) {
        if (compact) Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArtwork(snapshot.current, Modifier.size(44.dp), 12.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 12.sp, color = foreground.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MusicAction(PlayerIcons.Queue, "播放队列", onQueue, foreground, reduced)
        }
        MusicSeekBar(snapshot, backend, foreground, reduced, progress, dragging, { value = it; dragging = true }, { dragging = false })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (dragging) (value * snapshot.durationMs).toLong() else snapshot.positionMs), fontSize = 11.sp, color = foreground.copy(alpha = .5f))
            Text(if (snapshot.durationMs > 0) formatTime(snapshot.durationMs) else "—:—", fontSize = 11.sp, color = foreground.copy(alpha = .5f))
        }
        Row(Modifier.fillMaxWidth().padding(top = if (compact) 2.dp else 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly) {
            if (compact) MusicAction(PlayerIcons.Heart, if (favorite) "取消收藏" else "收藏", onFavorite,
                if (favorite) MiuixTheme.colorScheme.primary else foreground.copy(alpha = .7f), reduced)
            MusicAction(PlayerIcons.Previous, "上一首", backend::previous, foreground, reduced, size = if (compact) 48 else 56)
            MusicAction(if (snapshot.isPlaying) PlayerIcons.Pause else PlayerIcons.Play, if (snapshot.isPlaying) "暂停" else "播放", backend::togglePlayPause,
                foreground, reduced, size = if (compact) 52 else 72, filled = true)
            MusicAction(PlayerIcons.Next, "下一首", backend::next, foreground, reduced, size = if (compact) 48 else 56)
            if (compact) Text(speedLabel(snapshot.speed), Modifier.clickable(onClick = onSpeed).padding(14.dp), fontSize = 12.sp, color = foreground.copy(alpha = .7f))
        }
        if (!compact) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(speedLabel(snapshot.speed), Modifier.clickable(onClick = onSpeed).padding(16.dp), fontSize = 14.sp, color = foreground.copy(alpha = .6f))
            MusicAction(PlayerIcons.Heart, if (favorite) "取消收藏" else "收藏", onFavorite,
                if (favorite) MiuixTheme.colorScheme.primary else foreground.copy(alpha = .6f), reduced)
            MusicAction(PlayerIcons.Queue, "播放队列", onQueue, foreground.copy(alpha = .6f), reduced)
        }
    }
}

@Composable
private fun MusicSeekBar(snapshot: PlaybackSnapshot, backend: PlaybackBackend, color: Color, reduced: Boolean,
    progress: Float, dragging: Boolean, onPreview: (Float) -> Unit, onEnd: () -> Unit) {
    val latest by rememberUpdatedState(snapshot)
    val height by animateFloatAsState(if (dragging) 6f else 3f, if (reduced) snap() else spring(stiffness = 500f, dampingRatio = .8f), label = "seek track spring")
    val radius by animateFloatAsState(if (dragging) 8f else 4f, if (reduced) snap() else spring(stiffness = 500f, dampingRatio = .8f), label = "seek thumb spring")
    val identity = snapshot.current?.uri
    val generation = snapshot.mediaGeneration
    Canvas(Modifier.fillMaxWidth().height(48.dp).semantics {
        progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
        stateDescription = "播放进度 ${formatTime(latest.positionMs)}，共 ${formatTime(latest.durationMs)}"
        if (snapshot.durationMs > 0) setProgress { requested -> backend.seekTo((requested.coerceIn(0f, 1f) * latest.durationMs).toLong()); true }
    }.pointerInput(identity, generation, snapshot.durationMs > 0) {
        if (snapshot.durationMs <= 0) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            var moved = false; var cancelled = false; var position = down.position
            try {
                while (true) {
                    val event = awaitPointerEvent(); val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed || event.changes.count { it.pressed } > 1) { cancelled = true; break }
                    position = change.position
                    if (!change.pressed) { change.consume(); break }
                    val delta = position - down.position
                    if (!moved && abs(delta.y) > viewConfiguration.touchSlop && abs(delta.y) > abs(delta.x)) { cancelled = true; break }
                    if (abs(delta.x) > viewConfiguration.touchSlop || moved) {
                        moved = true; change.consume(); onPreview((position.x / size.width).coerceIn(0f, 1f))
                    }
                }
                if (!cancelled && latest.current?.uri == identity && latest.mediaGeneration == generation)
                    backend.seekTo(((position.x / size.width).coerceIn(0f, 1f) * latest.durationMs).toLong())
            } finally { onEnd() }
        }
    }) {
        val track = height.dp.toPx(); val corner = CornerRadius(track / 2)
        drawRoundRect(color.copy(alpha = .16f), Offset(0f, center.y - track / 2), Size(size.width, track), corner)
        drawRoundRect(color.copy(alpha = .7f), Offset(0f, center.y - track / 2), Size(size.width * progress, track), corner)
        if (snapshot.durationMs > 0) drawCircle(color.copy(alpha = .9f), radius.dp.toPx(), Offset(size.width * progress, center.y))
    }
}

@Composable
private fun MusicAction(icon: ImageVector, description: String, onClick: () -> Unit, foreground: Color, reduced: Boolean,
    modifier: Modifier = Modifier, size: Int = 48, filled: Boolean = false) {
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .91f else 1f, if (reduced) snap() else spring(dampingRatio = .68f, stiffness = 480f), label = "music press spring")
    Box(modifier.size(size.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape)
        .background(if (filled) foreground else Color.Transparent).clickable(interactionSource = interactions, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(if (filled) 28.dp else 24.dp), tint = if (filled) {
            if (foreground.luminance() > .5f) Color(0xFF20252B) else Color(0xFFFAFAF7)
        } else foreground)
    }
}
