package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.playback.DecoderMode
import yy.daniel.yyplayerforandroid.playback.PlaybackSnapshot

@Composable
internal fun SettingsScreen(theme: ThemeChoice, onTheme: (ThemeChoice) -> Unit,
    accent: AccentChoice, onAccent: (AccentChoice) -> Unit, reduceMotion: Boolean, onReduceMotion: (Boolean) -> Unit,
    snapshot: PlaybackSnapshot, onDecoder: (DecoderMode) -> Unit, orientation: OrientationChoice,
    onOpenOrientation: () -> Unit, onBack: () -> Unit,
    onlineMusic: Boolean = false, onOnlineMusic: (Boolean) -> Unit = {},
    onlineLyrics: Boolean = false, onOnlineLyrics: (Boolean) -> Unit = {},
    apiSummary: String = "", apiMessage: String? = null, onImportApi: () -> Unit = {}, onDownloadDirectory: () -> Unit = {},
    downloadDirectory: String = "系统 Music（默认）", onDefaultDownloadDirectory: () -> Unit = {},
    onLyricStyle: () -> Unit = {}, onSetup: () -> Unit = {}) {
    var dialog by remember { mutableStateOf<String?>(null) }
    HyperPage("设置", onBack) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 700.dp).fillMaxWidth(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 36.dp + padding.calculateBottomPadding())) {
                item { HyperSection("外观") }
                item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                    Text("主题", Modifier.padding(start = PreferenceInset, top = 20.dp, bottom = 16.dp), fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeChoice.entries.forEach { choice ->
                            key(MiuixTheme.colorScheme.background, choice) { ThemePreview(choice, theme == choice, { onTheme(choice) }, Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HyperSettingRow("强调色", trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccentChoice.entries.forEach { choice ->
                                Surface(onClick = { onAccent(choice) }, modifier = Modifier.size(48.dp).semantics { contentDescription = choice.label }, shape = CircleShape, color = Color.Transparent) {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Box(Modifier.size(28.dp).background(choice.color, CircleShape), contentAlignment = Alignment.Center) {
                                            if (accent == choice) Icon(PlayerIcons.Check, choice.label, Modifier.size(18.dp), tint = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    })
                    HyperSettingRow("减少动态效果", "简化页面切换与控件显隐", trailing = { Switch(checked = reduceMotion, onCheckedChange = onReduceMotion) })
                } }
                item { HyperSection("播放") }
                item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                    HyperSpinnerPreference(title = "视频解码", items = DecoderMode.entries.map { DropdownItem(it.title, summary = decoderSummary(it)) },
                        selectedIndex = snapshot.decoderMode.ordinal, onSelectedIndexChange = { onDecoder(DecoderMode.entries[it]) })
                    HyperSettingRow("屏幕方向", value = orientation.label, onClick = onOpenOrientation)
                    HyperSettingRow("播放手势", "长按临时 3 倍速，双指缩放、旋转与移动")
                    HyperSettingRow("歌词显示", "分别设置选中行、非选中行的字号、字重与颜色，以及行间距", onClick = onLyricStyle)
                } }
                item { HyperSection("联网服务") }
                item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                    HyperSettingRow("联网搜索歌曲", "开启后显示独立在线搜索，支持串流与下载", trailing = { Switch(checked = onlineMusic, onCheckedChange = onOnlineMusic) })
                    HyperSettingRow("联网搜索歌词", "按歌名查找候选，并为在线歌曲自动保存歌词", trailing = { Switch(checked = onlineLyrics, onCheckedChange = onOnlineLyrics) })
                    HyperSettingRow("API 配置", apiMessage ?: apiSummary.ifBlank { "正在读取…" }, value = "导入 JSON", onClick = onImportApi)
                    if (onlineMusic) HyperSettingRow("歌曲下载目录", downloadDirectory, onClick = { dialog = "download" })
                } }
                item { HyperSection("关于") }
                item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                    HyperSettingRow("YYPlayer", "本地音乐与视频播放器")
                    HyperSettingRow("使用引导", "重新设置文件访问、联网服务与外观", onClick = onSetup)
                    HyperSettingRow("当前媒体信息", "查看媒体格式与实际解码路径", onClick = { dialog = "info" })
                } }
            }
        }
    }
    HyperDialog("歌曲下载目录", { dialog = null }, show = dialog == "download") {
        HyperSettingRow("系统 Music（默认）", "歌曲保存到 Music；歌词与专辑图自动保存在应用内", onClick = { onDefaultDownloadDirectory(); dialog = null })
        HyperSettingRow("选择文件夹", "同时保存歌曲、同名歌词与专辑图", onClick = { dialog = null; onDownloadDirectory() })
    }
    MediaInfoDialog(snapshot, show = dialog == "info") { dialog = null }
}

@Composable
internal fun ThemePreview(choice: ThemeChoice, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(106.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.surfaceContainerHigh, shape),
            color = if (choice == ThemeChoice.DARK) Color(0xFF151515) else Color(0xFFF1F1F1), shape = shape) {
            Row(Modifier.fillMaxSize()) {
                @Composable fun swatch(dark: Boolean, mod: Modifier) {
                    Column(mod.fillMaxHeight().background(if (dark) Color(0xFF151515) else Color(0xFFF1F1F1)).padding(horizontal = 10.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.width(24.dp).height(4.dp).background(if (dark) Color.White else Color.Black, CircleShape))
                        Box(Modifier.fillMaxWidth().height(24.dp).background(if (dark) Color(0xFF303030) else Color.White, RoundedCornerShape(8.dp)))
                        Box(Modifier.fillMaxWidth().height(24.dp).background(if (dark) Color(0xFF303030) else Color.White, RoundedCornerShape(8.dp)))
                    }
                }
                if (choice == ThemeChoice.SYSTEM) { swatch(false, Modifier.weight(1f)); swatch(true, Modifier.weight(1f)) }
                else swatch(choice == ThemeChoice.DARK, Modifier.fillMaxWidth())
            }
        }
        Text(choice.label, Modifier.padding(top = 10.dp, bottom = 8.dp), fontSize = 13.sp,
            color = if (selected) MiuixTheme.colorScheme.primary else Muted)
    }
}
