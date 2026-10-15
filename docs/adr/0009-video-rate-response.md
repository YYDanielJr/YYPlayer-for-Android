# ADR0009：视频临时倍速的输出延迟

日期：2026-10-14。状态：已采用；13 Ultra 专项通过，其他设备／路由待验。

## 事实

用户要求 Media3 和 libmpv 长按 3×、松手恢复均跟手。小米 13 Ultra API36 上，Media3 软件变速需要排空旧 PCM；原缓冲为 500ms。平台 AudioTrack 变速候选虽响应快，但 13 Ultra 实际拒绝 7.4×（effective rate out of bounds），不能破坏既有 0.1×–8× 范围，因此未采用该候选。音乐与 native exact 路径保持原规则。

libmpv 0.41 的实际时间序列显示，speed 属性已变成 3 后，视频 time-pos 仍停留约 300ms；降速还出现时间追赶。不是 UI 倍速标签的刷新问题。固定 AAR 包含 mpv 内建 aaudio、audiotrack 驱动。上游 audiotrack 使用 75–150ms 驱动缓冲，变速时旧 PCM 与新时基会影响音画同步；仅缩短软件队列及保持 scaletempo2 不足以消除该现象。

## 候选与决定

1. 关闭音画同步或变调：不采用，不能以同步／音调退化换标签响应。
2. 自研音频输出／时钟：不采用，本任务不扩展媒体核心。
3. 采用同一固定 libmpv 的内建 AAudio 共享低延迟输出，保留 AudioTrack 初始化失败回退；缩短额外队列，使用固定保音调变速滤镜。13 Ultra 实测后采用。

AAudio 是视频输出候选，不是音乐 Oboe／独占／位准确实现；无额外引擎、FFmpeg 或来源不明 so。系统 API26 起存在 AAudio，但低版本与其他路由仍需验证。实际输出驱动必须可回读，不以请求成功宣称低延迟资格。

来源：[mpv FD 输入](https://github.com/mpv-player/mpv/blob/v0.41.0/stream/stream_file.c)、[AudioTrack 输出](https://github.com/mpv-player/mpv/blob/v0.41.0/audio/out/ao_audiotrack.c)、[AAudio 输出](https://github.com/mpv-player/mpv/blob/v0.41.0/audio/out/ao_aaudio.c)、[变速与同步](https://github.com/mpv-player/mpv/blob/v0.41.0/player/audio.c)。
## 最终实现与实测边界

Media3 仅视频将 AudioTrack PCM 目标缓冲改为 40ms，并请求 `PERFORMANCE_MODE_LOW_LATENCY`；平台输出保持 1×，继续由 Sonic 提供完整 0.1×–8× 保音调变速。仍尊重 AudioTrack 实际最小缓冲；仅缩短目标缓冲未请求低延迟时，真机降速时钟测试仍失败，不能仅从配置推断效果。音视频切换时重新配置，手指按下／松开不重建播放器。libmpv 使用 `ao=aaudio,audiotrack`、`aaudio-performance-mode=low-latency`、额外软件队列 30ms，固定 `scaletempo2=min-speed=0.1:max-speed=8` 保留音调。AAudio 采用共享默认，初始化失败可退回 AudioTrack；媒体信息展示实际 `current-ao`，不能将低延迟请求当作硬件资格。

13 Ultra 读回 `aaudio`，原生 80ms 间隔采样显示变速后持续推进，回到 1× 后没有原先的追赶跳跃；真实长按两后端均有播放时钟与松手恢复断言。详见 [U21](../validation/video-u21/README.md)。这不是所有设备、蓝牙路由、API26 或任意高码率视频的零掉帧保证；AudioTrack 回退的响应仍受其缓冲影响。音乐独占／位准确路径没有变更。

Media3 配置对照锁定 1.11.1 AAR 字节码，并参考 [AudioOutputProvider](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioTrackAudioOutputProvider.java) 和 [缓冲配置](https://github.com/androidx/media/blob/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/DefaultAudioTrackBufferSizeProvider.java)。
