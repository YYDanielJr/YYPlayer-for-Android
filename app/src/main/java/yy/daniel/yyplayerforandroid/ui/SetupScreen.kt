package yy.daniel.yyplayerforandroid.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.MainActivity
import yy.daniel.yyplayerforandroid.playback.MediaKind

/** System-style setup: every permission/online choice is optional and backed by a real result. */
@Composable
internal fun SetupScreen(activity: MainActivity) {
    val runtime = activity.runtime
    val preferences by runtime.preferences.collectAsStateWithLifecycle()
    val config by runtime.online.config.collectAsStateWithLifecycle()
    val apiMessage by runtime.online.message.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val reduced = rememberMusicReduceMotion(preferences.reduceMotion)
    fun import(files: List<GrantedDocument>, kind: MediaKind) {
        if (files.isEmpty()) return
        scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { files.forEach { it.persist(activity) }; runtime.library.add(files.map { it.uri }, kind) }
                runtime.libraryRevision.value++
                message = "已授权并添加 ${files.size} 个${if (kind == MediaKind.AUDIO) "音频" else "视频"}"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "未取得文件授权，可稍后再选择" }
            finally { busy = false }
        }
    }
    val audioPicker = rememberLauncherForActivityResult(LibraryDocumentPicker()) { import(it, MediaKind.AUDIO) }
    val videoPicker = rememberLauncherForActivityResult(LibraryDocumentPicker()) { import(it, MediaKind.VIDEO) }
    val treePicker = rememberLauncherForActivityResult(MusicTreePicker()) { result ->
        if (result != null) scope.launch {
            busy = true
            try {
                val count = withContext(Dispatchers.IO) {
                    result.persist(activity)
                    val songs = runtime.lyrics.documents.musicIn(result.uri)
                    runtime.library.add(songs, MediaKind.AUDIO); songs.size
                }
                runtime.libraryRevision.value++
                message = "目录已授权，已添加 $count 首音乐"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "目录未授权，可选择其他文件夹" }
            finally { busy = false }
        }
    }
    val configPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try { runtime.online.importConfig(uri); message = "API 配置已导入" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = "导入失败：${e.message ?: "配置不合法"}，已保留原配置" }
            finally { busy = false }
        }
    }
    BackHandler(step > 0) { if (!busy) { step--; message = null } }
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.align(Alignment.Center).widthIn(max = 560.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PlayerIcons.Music, null, Modifier.size(24.dp), tint = MiuixTheme.colorScheme.primary)
                Text("YYPlayer", Modifier.weight(1f).padding(start = 10.dp), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (step in 1..3) Text("$step / 3", fontSize = 14.sp, color = Muted)
            }
            AnimatedContent(step, Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = { fadeIn(tween(if (reduced) 0 else 300)) togetherWith fadeOut(tween(if (reduced) 0 else 180)) }, label = "setup step") { page ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp)) {
                    val title = listOf("欢迎使用\nYYPlayer", "让音乐来到身边", "连接更大的音乐世界", "选择你的外观", "准备好了")[page]
                    val summary = listOf("从一首音乐开始，按照你的习惯设置播放器。", "选择要访问的文件或文件夹。你可以随时在媒体库继续添加。", "联网功能由你决定，也可以一直作为本地播放器使用。", "跟随系统，或选择自己喜欢的明暗与色彩。", "你的设置已就绪，开始探索自己的音乐与视频。")[page]
                    Text(title, fontSize = if (page == 0) 42.sp else 32.sp, lineHeight = if (page == 0) 52.sp else 42.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(18.dp))
                    Text(summary, fontSize = 16.sp, lineHeight = 25.sp, color = Muted)
                    Spacer(Modifier.height(if (page == 0) 56.dp else 32.dp))
                    when (page) {
                        0 -> {
                            Icon(PlayerIcons.Music, null, Modifier.align(Alignment.CenterHorizontally).size(110.dp), tint = MiuixTheme.colorScheme.primary)
                            Spacer(Modifier.height(24.dp))
                            Text("音乐 · 视频 · 歌词", Modifier.align(Alignment.CenterHorizontally), fontSize = 17.sp, color = Muted)
                        }
                        1 -> {
                            HyperCard(Modifier.fillMaxWidth()) {
                                HyperSettingRow("选择音乐文件夹", "授权所选目录，并匹配同目录歌词", onClick = if (busy) null else { { treePicker.launch(Unit) } })
                                HyperSettingRow("选择歌曲文件", "可以一次选择多首歌曲", onClick = if (busy) null else { { audioPicker.launch(arrayOf("audio/*")) } })
                                HyperSettingRow("选择视频文件", "仅访问你选择的视频", onClick = if (busy) null else { { videoPicker.launch(arrayOf("video/*")) } })
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("文件访问是可选的。暂不授权也可以完成设置。", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
                        }
                        2 -> HyperCard(Modifier.fillMaxWidth()) {
                            HyperSettingRow("联网搜索歌曲", "搜索、流式播放和下载在线歌曲", trailing = { Switch(preferences.onlineMusic, runtime::setOnlineMusic) })
                            HyperSettingRow("联网搜索歌词", "搜索歌词候选，自动保存可用歌词", trailing = { Switch(preferences.onlineLyrics, runtime::setOnlineLyrics) })
                            HyperSettingRow("导入 API 配置", apiMessage ?: config?.providers?.joinToString("、") { it.name } ?: "正在读取内置配置…",
                                onClick = if (busy) null else { { configPicker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) } })
                        }
                        3 -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                ThemeChoice.entries.forEach { ThemePreview(it, preferences.theme == it, { runtime.setTheme(it) }, Modifier.weight(1f)) }
                            }
                            Spacer(Modifier.height(24.dp))
                            HyperCard(Modifier.fillMaxWidth()) {
                                HyperSpinnerPreference("强调色", AccentChoice.entries.map { DropdownItem(it.label) }, preferences.accent.ordinal, { runtime.setAccent(AccentChoice.entries[it]) })
                                HyperSettingRow("减少动态效果", "简化页面切换和交互动画", trailing = { Switch(preferences.reduceMotion, runtime::setReduceMotion) })
                            }
                        }
                        4 -> {
                            Box(Modifier.align(Alignment.CenterHorizontally).size(84.dp).background(MiuixTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(PlayerIcons.Check, null, Modifier.size(40.dp), tint = MiuixTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(32.dp))
                            HyperCard(Modifier.fillMaxWidth()) {
                                HyperSettingRow("外观", preferences.theme.label)
                                HyperSettingRow("联网歌曲", if (preferences.onlineMusic) "已启用" else "关闭")
                                HyperSettingRow("联网歌词", if (preferences.onlineLyrics) "已启用" else "关闭")
                            }
                            Spacer(Modifier.height(18.dp))
                            Text("这些选项以后都可以在设置中修改。", color = Muted, fontSize = 14.sp)
                        }
                    }
                    if (message != null) Text(message!!, Modifier.padding(top = 20.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.primary)
                    if (busy) Text("正在处理…", Modifier.padding(top = 20.dp), fontSize = 14.sp, color = Muted)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HyperActionButton(if (step > 0) "上一步" else "稍后设置", onClick = {
                    if (step > 0) { step--; message = null } else runtime.setup.finish()
                }, modifier = Modifier.weight(1f), primary = false, enabled = !busy)
                HyperActionButton(if (step == 0) "开始设置" else if (step == 4) "开始使用" else "继续",
                    onClick = { if (step == 4) runtime.setup.finish() else { step++; message = null } }, modifier = Modifier.weight(1f), enabled = !busy)
            }
        }
    }
}
