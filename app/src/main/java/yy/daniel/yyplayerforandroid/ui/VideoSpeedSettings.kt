package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import yy.daniel.yyplayerforandroid.playback.VideoEngine
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun VideoSpeedSettings(speed: Float, engine: VideoEngine, onSpeed: (Float) -> Unit,
    show: Boolean, strictHardware: Boolean, onDismiss: () -> Unit) {
    val maximum = if (engine == VideoEngine.MEDIA3 && strictHardware) 8f else 10f
    var draft by remember(speed, show) { mutableFloatStateOf(speed.coerceIn(.1f, maximum)) }
    fun apply(value: Float) {
        draft = (value * 10).roundToInt().coerceIn(1, (maximum * 10).toInt()) / 10f
        onSpeed(draft)
    }
    HyperDialog("播放倍速", onDismiss, show, buttonText = "完成") {
        Column(Modifier.fillMaxWidth().padding(horizontal = DialogContentInset, vertical = 12.dp)) {
            Text(speedLabel(draft), fontSize = 28.sp)
            Spacer(Modifier.height(12.dp))
            Slider(value = draft, onValueChange = { draft = (it * 10).roundToInt().coerceIn(1, (maximum * 10).toInt()) / 10f },
                valueRange = .1f..maximum, onValueChangeFinished = { apply(draft) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                    contentDescription = "自由调节播放倍速"
                    // Miuix's accessibility SetProgress does not invoke onValueChangeFinished.
                    setProgress { target -> if (target.isFinite()) { apply(target); true } else false }
                })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0.1×", color = Muted, fontSize = 14.sp)
                Text(speedLabel(maximum), color = Muted, fontSize = 14.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HyperActionButton("减 0.1×", { apply((ceil(draft * 10).toInt() - 1) / 10f) },
                    modifier = Modifier.weight(1f), primary = false, enabled = draft > .1f)
                HyperActionButton("加 0.1×", { apply((floor(draft * 10).toInt() + 1) / 10f) },
                    modifier = Modifier.weight(1f), primary = false, enabled = draft < maximum)
            }
        }
        HyperSettingRow("恢复正常速度", "1×", onClick = { apply(1f) })
        HyperSettingRow("调节方式", "拖动后松手生效，也可使用加减按钮精调；外侧快捷档位保持不变。")
        if (engine == VideoEngine.MEDIA3) HyperSettingRow("高速播放", if (strictHardware)
            "仅硬解模式下最高 8×；如需更高速度，请先在画面设置中切换解码方式。"
            else "超过 8× 将切换到 libmpv；保留播放位置和暂停状态。")
    }
}
