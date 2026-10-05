package yy.daniel.yyplayerforandroid

import android.os.Bundle
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*
import yy.daniel.yyplayerforandroid.online.*
import yy.daniel.yyplayerforandroid.playback.*
import yy.daniel.yyplayerforandroid.ui.*

@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
class OnlineSearchActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!runtime.preferences.value.onlineMusic) { finish(); return }
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val config by runtime.online.config.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()
            var task by remember { mutableStateOf<Job?>(null) }
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            var pending by remember { mutableStateOf<Pair<OnlineSong, ApiQuality>?>(null) }
            fun download(song: OnlineSong, quality: ApiQuality, tree: Uri?) {
                task?.cancel()
                task = scope.launch {
                    busy = true; message = "正在下载…"
                    try {
                        val result = runtime.online.download(song, quality, tree, runtime.lyrics)
                        withContext(Dispatchers.IO) { runtime.library.addDownloaded(result.media) }
                        runtime.libraryRevision.value++
                        message = result.warning ?: if (result.lyricSaved) "歌曲与歌词已下载，并加入本地音乐" else "歌曲已下载，并加入本地音乐"
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { message = e.message ?: "下载失败，可重新选择目录重试" }
                    finally { busy = false }
                }
            }
            val picker = rememberLauncherForActivityResult(DownloadTreePicker()) { result ->
                val request = pending; pending = null
                if (result != null && request != null && runtime.preferences.value.onlineMusic) {
                    try { result.persist(this); runtime.online.rememberDownloadTree(result.uri); download(request.first, request.second, result.uri) }
                    catch (e: Exception) { message = e.message ?: "无法授权下载目录" }
                }
            }
            LaunchedEffect(preferences.onlineMusic, config) {
                task?.cancel(); pending = null
                if (!preferences.onlineMusic) closePage()
            }
            AppTheme {
                OnlineSearchScreen(runtime.online, false, "", preferences.onlineMusic, busy, message,
                    onSong = { song, quality, downloading ->
                        message = null
                        if (downloading) {
                            val tree = runtime.online.downloadTree
                            if (tree == null && android.os.Build.VERSION.SDK_INT < 29) { pending = song to quality; picker.launch(Unit) } else download(song, quality, tree)
                        } else {
                            task?.cancel()
                            task = scope.launch {
                                busy = true
                                try {
                                    val resolved = runtime.online.resolve(song, quality)
                                    val cover = try { runtime.online.artwork(song) }
                                        catch (e: CancellationException) { throw e } catch (_: Exception) { null }
                                    val media = LocalMedia(resolved.url.toUri(), song.title, MediaKind.AUDIO, null,
                                        song.title, song.artist, song.album, song.durationMs, true, resolved.headers,
                                        onlineKey = "online:${song.providerId}:${song.source}:${song.id}",
                                        onlineQuality = "请求：${quality.name}；服务报告：${resolved.reportedQuality.ifBlank { "未提供" }}", artworkUri = cover)
                                    runtime.playback.play(listOf(media))
                                    runtime.fetchOnlineLyrics(song, media)
                                    openPlayer(); closePage()
                                } catch (e: CancellationException) { throw e }
                                catch (e: Exception) { message = e.message ?: "播放地址获取失败" }
                                finally { busy = false }
                            }
                        }
                    }, onLyric = {}, onBack = ::closePage)
            }
        }
    }
}

class OnlineLyricsActivity : PlayerHostActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val media = runtime.playback.state.value.current
        val generation = runtime.playback.state.value.mediaGeneration
        if (!runtime.preferences.value.onlineLyrics || media == null || media.kind != MediaKind.AUDIO) { finish(); return }
        setContent {
            val preferences by runtime.preferences.collectAsStateWithLifecycle()
            val snapshot by runtime.playback.state.collectAsStateWithLifecycle()
            val config by runtime.online.config.collectAsStateWithLifecycle()
            var busy by remember { mutableStateOf(false) }
            var message by remember { mutableStateOf<String?>(null) }
            var task by remember { mutableStateOf<Job?>(null) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(preferences.onlineLyrics, snapshot.mediaGeneration, config) {
                task?.cancel()
                if (!preferences.onlineLyrics || snapshot.mediaGeneration != generation) closePage()
            }
            AppTheme {
                OnlineSearchScreen(runtime.online, true, media.musicTitle, preferences.onlineLyrics, busy, message,
                    onSong = { _, _, _ -> }, onLyric = { candidate ->
                        task?.cancel()
                        task = scope.launch {
                            busy = true; message = null
                            try {
                                val text = runtime.online.lyric(candidate)
                                check(runtime.preferences.value.onlineLyrics && runtime.playback.state.value.mediaGeneration == generation) { "歌曲已切换" }
                                runtime.lyrics.saveOnline(media, text, selected = true)
                                message = "歌词已保存，返回后自动显示"
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { message = e.message ?: "歌词获取失败" }
                            finally { busy = false }
                        }
                    }, onBack = ::closePage)
            }
        }
    }
}
