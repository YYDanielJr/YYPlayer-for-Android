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
import yy.daniel.yyplayerforandroid.audio.OutputPath
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
    outputPath: OutputPath,
    onSharing: (SharingRequest) -> Unit,
    onTransparency: (TransparencyRequest) -> Unit,
    onOutputPath: (OutputPath) -> Unit,
    onSelectDevice: (Int?) -> Unit,
    onRunSelfTest: () -> Unit,
    onBack: () -> Unit,
) {
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
                            title = "输出通道",
                            items = OutputPath.entries.map { DropdownItem(it.label, summary = it.summary) },
                            selectedIndex = outputPath.ordinal,
                            onSelectedIndexChange = { onOutputPath(OutputPath.entries[it]) },
                        )
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

                item { HyperSection("播放输出实测", trailing = if (state.negotiatedOutput != null) "已回读" else null) }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        val negotiated = state.negotiatedOutput
                        if (negotiated == null) {
                            HyperSettingRow(
                                "尚未打开输出",
                                summary = "选择「独占／位完美优先」并开始播放后，这里显示输出通道实际协商到的格式、是否请求了" +
                                    "位完美契约，以及平台是否回读确认。",
                            )
                        } else {
                            HyperSettingRow(
                                "输出通道实际格式",
                                summary = "${negotiated.format.sampleRate / 1000f} kHz · " +
                                    "${negotiated.format.channelCount} 声道 · " +
                                    "${negotiated.format.encoding.effectiveBits} bit",
                                value = negotiated.format.encoding.wire,
                            )
                            HyperSettingRow(
                                "是否有损加宽",
                                summary = if (negotiated.widened) {
                                    "源位深低于容器，由原生核心无损加宽（补零，不丢有效位、不经浮点）"
                                } else {
                                    "源容器与输出容器一致，未做任何样本转换"
                                },
                                value = if (negotiated.widened) "已加宽" else "无转换",
                            )
                            HyperSettingRow(
                                "位完美契约",
                                summary = when {
                                    !negotiated.bitPerfectRequested ->
                                        "该设备组合没有 BIT_PERFECT 契约，未向系统请求；本次按同格式共享输出"
                                    negotiated.reasonCode == "mixer_contract_refused" ->
                                        "已向系统请求，系统拒绝（返回 false），未静默改为其它格式"
                                    negotiated.bitPerfectActive -> "契约、实际 USB 路由与 API 格式一致；尚无数字捕获验证"
                                    else -> "已请求，实际路由与契约尚未同时确认"
                                },
                                value = if (negotiated.bitPerfectActive) {
                                    BitPerfectStatus.SYSTEM_REPORTED.label
                                } else {
                                    BitPerfectStatus.UNAVAILABLE.label
                                },
                            )
                            HyperSettingRow(
                                "设备指定",
                                summary = if (state.selectedDeviceId != null) {
                                    "已请求把输出固定到所选设备；平台是否照做以路由回读为准" +
                                        (if (negotiated.devicePinAccepted) "" else "（平台未确认该请求，但仍可能已按此路由）")
                                } else {
                                    "跟随系统输出"
                                },
                                value = if (negotiated.devicePinAccepted) "平台已确认" else "未确认",
                            )
                        }
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
                            "未知（AudioTrack 不提供独占证据）"
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

                item { HyperSection("输出自检") }
                item {
                    HyperCard(Modifier.padding(horizontal = PageGutter).fillMaxWidth()) {
                        HyperSettingRow(
                            title = "输出参考信号",
                            summary = buildString {
                                append("先停止歌曲输出，测试后保留暂停。以立体声 s16 参考信号协商格式：核心提供 PCM 环，应用按能力请求混音器契约，")
                                append("播放中回读实际路由与格式。信号为 2 秒静音 → 左 440Hz → 右 880Hz → 双声道")
                                append("（各 1 秒）→ 2 秒静音，用来确认左右声道与设备是否真的出声。")
                            },
                            value = if (state.selfTestRunning) "进行中…" else "开始",
                            onClick = if (state.selfTestRunning) null else onRunSelfTest,
                        )
                        state.selfTest?.let { result -> SelfTestRows(result) }
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
private fun SelfTestRows(result: yy.daniel.yyplayerforandroid.audio.OutputSelfTestResult) {
    if (!result.started) {
        HyperSettingRow("自检未完成", summary = result.reasonText, value = result.reasonCode)
        return
    }
    HyperSettingRow(
        "位完美契约",
        summary = if (result.mixerContractAccepted) {
            "系统接受了混音器请求，回读值为 " + (result.mixerContractReadBack?.let {
                "${it.sampleRate / 1000f} kHz · ${it.encoding.effectiveBits} bit" + if (it.bitPerfect) " · BIT_PERFECT" else ""
            } ?: "无（未回读）")
        } else {
            "系统未接受位完美混音器请求，本次按共享输出完成，未静默改为其它格式"
        },
        value = when {
            result.systemReportedBitPerfect -> BitPerfectStatus.SYSTEM_REPORTED.label
            result.mixerContractAccepted -> BitPerfectStatus.REQUESTED.label
            else -> BitPerfectStatus.UNAVAILABLE.label
        },
    )
    HyperSettingRow(
        "播放中实际路由",
        summary = result.route.routedDeviceNames.joinToString("、").ifBlank { "系统未回读路由设备" },
        value = if (result.route.routedIsUsb) "USB" else null,
    )
    HyperSettingRow(
        "实际输出格式",
        summary = if (result.route.trackSampleRate > 0) {
            "${result.route.trackSampleRate / 1000f} kHz · ${result.route.trackEncoding.effectiveBits} bit · 缓冲 ${result.route.bufferSizeFrames} 帧"
        } else {
            "系统未回读格式"
        },
        value = when {
            result.formatWasSubstituted -> "平台已替换"
            result.formatMatchesTrack -> "与决策一致"
            else -> "与决策不一致"
        },
    )
    if (result.formatWasSubstituted) {
        HyperSettingRow(
            "平台实际接受的格式",
            summary = "应用按决策格式请求，平台给输出流换成了 " +
                "${result.route.platformFormat?.sampleRate?.div(1000f)} kHz · " +
                "${result.route.platformFormat?.encoding?.effectiveBits} bit；" +
                "应用按平台回读记录，不把请求格式当作实际格式。",
        )
    }
    HyperSettingRow(
        "样本投递",
        summary = "写入环 ${result.framesFed} 帧 · 丢弃 ${result.framesDropped} 帧 · 输出取走 ${result.framesConsumed} 帧 · " +
            "信号期间欠载 ${result.starvingDuringSignal} 帧（开流前静音 ${result.starvingFramesAtFeedEnd} 帧计为正常）· " +
            "平台 xrun ${result.route.underrunCount ?: "未报告"}",
        value = if (result.deliveryComplete) "完整" else "有缺失",
    )
    HyperSettingRow(
        "参考信号摘要",
        summary = "SHA-256（进入环的字节，${result.decidedFormat?.encoding?.wire ?: "?"} 小端交织）：" +
            (result.digestHex ?: "未计算") + "\n" + result.toneDescription,
        value = "对比值",
    )
    result.notes.forEach { HyperSettingRow("说明", summary = it) }
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
