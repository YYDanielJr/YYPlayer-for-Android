package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun LyricStyleScreen(style: LyricStyle, onStyle: (LyricStyle) -> Unit, onBack: () -> Unit) {
    var draft by remember(style) { mutableStateOf(style) }
    var colorDialog by remember { mutableStateOf<String?>(null) }
    var hex by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun setColor(value: Long?) {
        onStyle(if (colorDialog == "active") draft.copy(activeColor = value) else draft.copy(inactiveColor = value))
        colorDialog = null
    }
    fun openColor(active: Boolean) {
        colorDialog = if (active) "active" else "inactive"
        hex = (if (active) style.activeColor else style.inactiveColor)?.let { String.format(Locale.ROOT, "#%08X", it) } ?: "#FFFFFFFF"
        error = null
    }
    HyperPage("歌词显示", onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("lyric-style-list"), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item { HyperSection("预览") }
            item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(draft.lineSpacing.dp)) {
                    Text("让文字与音乐相遇", fontSize = draft.inactiveSize.sp, lineHeight = (draft.inactiveSize * 1.35f).sp,
                        fontWeight = FontWeight(draft.inactiveWeight), color = draft.inactiveColor?.let(::Color) ?: Muted)
                    Text("这一行，是此刻的旋律", fontSize = draft.activeSize.sp, lineHeight = (draft.activeSize * 1.35f).sp,
                        fontWeight = FontWeight(draft.activeWeight), color = draft.activeColor?.let(::Color) ?: MiuixTheme.colorScheme.onSurface)
                    Text("下一句，继续随音乐流动", fontSize = draft.inactiveSize.sp, lineHeight = (draft.inactiveSize * 1.35f).sp,
                        fontWeight = FontWeight(draft.inactiveWeight), color = draft.inactiveColor?.let(::Color) ?: Muted)
                }
            } }
            for (active in listOf(true, false)) {
                item { HyperSection(if (active) "选中行" else "非选中行") }
                item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                    StyleSizeSlider(if (active) "选中行字号" else "非选中行字号", if (active) draft.activeSize else draft.inactiveSize, 14f..48f, "sp",
                        { draft = if (active) draft.copy(activeSize = it) else draft.copy(inactiveSize = it) }, { onStyle(draft) })
                    val weight = if (active) draft.activeWeight else draft.inactiveWeight
                    HyperSpinnerPreference(if (active) "选中行字重" else "非选中行字重", LyricStyle.WeightNames.map { DropdownItem(it) },
                        LyricStyle.Weights.indexOf(weight), { index -> onStyle(if (active) draft.copy(activeWeight = LyricStyle.Weights[index]) else draft.copy(inactiveWeight = LyricStyle.Weights[index])) })
                    val color = if (active) draft.activeColor else draft.inactiveColor
                    HyperSettingRow(if (active) "选中行颜色" else "非选中行颜色", color?.let { String.format(Locale.ROOT, "#%08X", it) } ?: "自动适配明暗与封面背景", onClick = { openColor(active) })
                } }
            }
            item { HyperSection("行间距") }
            item { HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                StyleSizeSlider("所有行间距", draft.lineSpacing, 0f..40f, "dp", { draft = draft.copy(lineSpacing = it) }, { onStyle(draft) })
                HyperSettingRow("恢复默认歌词样式", onClick = { onStyle(LyricStyle()) })
            } }
        }
    }
    HyperDialog(if (colorDialog == "active") "选中行颜色" else "非选中行颜色", { colorDialog = null }, show = colorDialog != null) {
        HyperSettingRow("自动适配", "随主题与封面背景变化", onClick = { setColor(null) })
        listOf("白色" to 0xffffffffL, "深灰" to 0xff242c35L, "蓝色" to 0xff3482ffL, "青绿" to 0xff18a27bL, "暖橙" to 0xffff7800L).forEach { (name, color) ->
            HyperSettingRow(name, onClick = { setColor(color) })
        }
        Text("自定义颜色", Modifier.padding(horizontal = DialogContentInset, vertical = 10.dp), fontSize = 14.sp, color = Muted)
        BasicTextField(hex, { hex = it.take(9); error = null }, Modifier.padding(horizontal = DialogContentInset).fillMaxWidth()
            .background(MiuixTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp)).padding(16.dp).semantics { contentDescription = "十六进制歌词颜色" },
            textStyle = TextStyle(color = MiuixTheme.colorScheme.onSurface, fontSize = 17.sp), singleLine = true, cursorBrush = SolidColor(MiuixTheme.colorScheme.primary))
        Text(error ?: "输入 #RRGGBB 或 #AARRGGBB", Modifier.padding(horizontal = DialogContentInset, vertical = 8.dp), color = Muted, fontSize = 13.sp)
        TextButton("应用自定义颜色", onClick = {
            val parsed = LyricStyle.parseColor(hex)
            if (parsed == null) error = "请输入有效的 6 位或 8 位十六进制颜色" else setColor(parsed)
        }, modifier = Modifier.padding(horizontal = DialogContentInset).fillMaxWidth())
    }
}

@Composable
private fun StyleSizeSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String,
    onValue: (Float) -> Unit, onFinish: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = PreferenceInset, vertical = 12.dp)) {
        Text("$label · ${value.roundToInt()} $unit", fontSize = 17.sp)
        Slider(value = value, onValueChange = { onValue(it.roundToInt().toFloat()) }, valueRange = range,
            onValueChangeFinished = onFinish, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                contentDescription = label
                // Miuix SetProgress does not invoke onValueChangeFinished; accessibility must save too.
                setProgress { target -> onValue(target.coerceIn(range)); onFinish(); true }
            })
    }
}
