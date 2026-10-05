package yy.daniel.yyplayerforandroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import yy.daniel.yyplayerforandroid.audio.ActualSharing
import yy.daniel.yyplayerforandroid.audio.AudioOutputChannel
import yy.daniel.yyplayerforandroid.audio.AudioOutputState
import yy.daniel.yyplayerforandroid.audio.BitPerfectStatus
import yy.daniel.yyplayerforandroid.audio.SharingRequest
import yy.daniel.yyplayerforandroid.audio.TransparencyRequest
import kotlinx.coroutines.delay

/**
 * Output strategy and device selection.
 *
 * Every line here is evidence: what the user asked for, what the core decided, and what the opened
 * output actually reported. A refusal is shown as a refusal with its reason, and a request that the
 * platform accepted is labelled "系统报告" instead of "已验证".
 */
@Composable
internal fun AudioOutputScreen(
    state: AudioOutputState,
    sharing: SharingRequest,
    transparency: TransparencyRequest,
    onSharing: (SharingRequest) -> Unit,
    onTransparency: (TransparencyRequest) -> Unit,
    onSelectDevice: (Int?) -> Unit,
    onBack: () -> Unit,
) {
    // Re-read the device list and re-evaluate periodically: this catches hot-plug and route changes
    // without pretending to know the hardware state in between.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            tick++
        }
    }
    HyperPage("音频输出", onBack) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 36.dp + padding.calculateBottomPadding()),
            ) {
                item { HyperSection("输出策略") }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        HyperSpinnerPreference(
                            title = "流独占",
                            items = SharingRequest.entries.map { DropdownItem(it.label, summary = sharingSummary(it)) },
                            selectedIndex = sharing.ordinal,
                            onSelectedIndexChange = { onSharing(SharingRequest.entries[it]) },
                        )
                        HyperSpinnerPreference(
                            title = "样本原样",
                            items = TransparencyRequest.entries.map { DropdownItem(it.label, summary = transparencySummary(it)) },
                            selectedIndex = transparency.ordinal,
                            onSelectedIndexChange = { onTransparency(TransparencyRequest.entries[it]) },
                        )
                        HyperSettingRow(
                            "当前计划",
                            summary = state.decision.reasonText,
                            value = state.decision.reasonCode,
                        )
                    }
                }

                item { HyperSection("输出设备", trailing = if (state.followDefault) "自动" else "已指定") }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        HyperSettingRow(
                            title = "自动选择",
                            summary = "跟随系统当前输出，插入 USB DAC 时由系统决定路由",
                            trailing = { if (state.followDefault) Icon(PlayerIcons.Check, "已选择") },
                            onClick = { onSelectDevice(null) },
                        )
                        if (state.channels.isEmpty()) {
                            HyperSettingRow("未发现输出设备", "系统未报告任何可用的音频输出")
                        }
                        state.channels.forEach { channel ->
                            DeviceRow(
                                channel = channel,
                                state = state,
                                onSelect = { onSelectDevice(channel.id) },
                            )
                        }
                    }
                }

                item { HyperSection("实际状态", trailing = if (state.mixerAttributesActive) "混音器已配置" else null) }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        val sharingText = if (state.actualSharing == ActualSharing.UNKNOWN) {
                            "未知（未打开输出）"
                        } else {
                            state.actualSharing.label
                        }
                        HyperSettingRow(
                            "实际流共享",
                            summary = "AudioTrack 路径无法证明独占，只有 AAudio 流可读回实际共享模式",
                            value = sharingText,
                        )
                        HyperSettingRow(
                            "位准确状态",
                            summary = bitPerfectSummary(state.bitPerfect),
                            value = state.bitPerfect.label,
                        )
                        HyperSettingRow(
                            "实际输出格式",
                            summary = listOfNotNull(
                                state.actualSampleRate?.let { "${it / 1000f} kHz" },
                                state.actualEncoding.takeIf { it.effectiveBits > 0 }?.let { it.effectiveBits.toString() + " bit" },
                            ).joinToString(" · ").ifBlank { "未知（系统未报告）" },
                        )
                        HyperSettingRow(
                            "请求的格式",
                            summary = state.decision.format?.let {
                                "${it.sampleRate / 1000f} kHz · ${it.channelCount} 声道 · ${it.encoding.effectiveBits} bit"
                            } ?: "尚未确定",
                        )
                        HyperSettingRow(
                            "路由设备",
                            summary = state.routedChannel?.productName ?: "未确认（播放开始后系统才报告）",
                        )
                        state.message?.let { HyperSettingRow("说明", summary = it) }
                    }
                }

                item { HyperSection("能力说明") }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        HyperSettingRow(
                            "USB 位完美",
                            summary = "Android 14（API 34）起，只有 USB 设备可以请求 BIT_PERFECT 混音器属性；" +
                                "该能力由厂商 Audio HAL 决定，系统不会报告“已真正位准确”。",
                        )
                        HyperSettingRow(
                            "数字捕获",
                            summary = "端到端位准确需要外接数字录音验证，应用内状态最高只到“系统报告”，不会显示“已验证”。",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(channel: AudioOutputChannel, state: AudioOutputState, onSelect: () -> Unit) {
    val selected = state.selectedDeviceId == channel.id
    val profiles = channel.profiles
    val detail = buildString {
        append(channel.typeLabel)
        if (channel.sampleRates.isNotEmpty()) {
            append(" · ")
            append(channel.sampleRates.joinToString("/") { "${it / 1000f}k" })
        }
        when {
            channel.hasBitPerfectContract -> append(" · 位完美组合 ${channel.bitPerfectProfiles.size} 个")
            channel.isUsb -> append(" · 无位完美组合")
        }
    }
    HyperSettingRow(
        title = channel.productName,
        summary = detail,
        trailing = { if (selected) Icon(PlayerIcons.Check, "已选择") },
        onClick = onSelect,
    )
    if (selected && profiles.isNotEmpty()) {
        Column(Modifier.padding(start = PreferenceInset, end = PreferenceInset, bottom = 12.dp)) {
            profiles.take(4).forEach { profile ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "${profile.sampleRate / 1000f} kHz · ${profile.encoding.effectiveBits} bit",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        if (profile.bitPerfect) "BIT_PERFECT" else "可混音",
                        fontSize = 13.sp,
                        color = if (profile.bitPerfect) MiuixTheme.colorScheme.primary else Muted,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

private fun sharingSummary(request: SharingRequest): String = when (request) {
    SharingRequest.SHARED -> "与其它应用共享输出，兼容性最好"
    SharingRequest.PREFER_EXCLUSIVE -> "设备允许时使用独占流，否则同设备共享"
    SharingRequest.STRICT_EXCLUSIVE -> "未获得独占就停止，不静默回退"
}

private fun transparencySummary(request: TransparencyRequest): String = when (request) {
    TransparencyRequest.COMPATIBLE -> "允许软件音量、均衡器等处理"
    TransparencyRequest.PREFER_UNPROCESSED -> "尽量不处理；无法原样时说明原因"
    TransparencyRequest.STRICT_UNPROCESSED -> "不能原样输出就拒绝播放"
}

private fun bitPerfectSummary(status: BitPerfectStatus): String = when (status) {
    BitPerfectStatus.UNKNOWN -> "尚未评估"
    BitPerfectStatus.UNAVAILABLE -> "该设备组合没有系统位完美契约"
    BitPerfectStatus.REQUESTED -> "已向系统请求，等待打开输出后确认"
    BitPerfectStatus.SYSTEM_REPORTED -> "系统接受了位完美混音器契约并按该格式输出"
    BitPerfectStatus.CAPTURE_VERIFIED -> "已通过数字捕获验证（应用不会自行设置该状态）"
}
