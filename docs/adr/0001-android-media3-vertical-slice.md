# ADR 0001：Android 首个可运行纵切使用 Media3 作为临时后端

- 状态：已接受（仅首个模拟器纵切）
- 日期：2026-10-04
- 背景：用户已将本轮验收范围明确为 MuMu 上前后端打通和 UI 验收。当前机器没有小米真机/USB DAC；仓库没有任何 Android native 运行库。上游 mpv-android 不是可直接依赖的 AAR，现有 Windows 工作站也无法按其上游原生脚本闭环构建完整 mpv/FFmpeg。
- 选项：暂停 UI 等完整 C++/libmpv/FFmpeg 构建；假造播放器状态的 UI mock；或用真实 Android 播放器建立可替换适配层，打通本地音视频和解码选择。
- 决定：在 `playback` 包内定义 Android 侧 `PlaybackBackend` 命令/状态接口，并以 Media3 ExoPlayer 实现真实本地播放。由 `DefaultRenderersFactory` 的 `MediaCodecSelector` 提供 Auto、优先硬解、严格硬解、软件解码策略；严格硬解无匹配解码器时必须播放失败，不能谎报硬解。Compose/Miuix 页面只观察后端状态并发出命令。SAF URI 是媒体身份。
- 影响：S00/S01 与本地 URI→真实解码→播放器控件的模拟器验证可以先完成。ExoPlayer 同时服务音频和视频，适配层不暴露 Player/View 给页面，未来由 C++/JNI 后端替换。此实现不是最终跨平台引擎；本轮不承诺 API34 USB BIT_PERFECT、AAudio Exclusive、FFmpeg PCM 原样通路、后台 MediaSession、libmpv 字幕/滤镜或 DRM。
- 退出条件：受控 Linux native runtime 可为 `arm64-v8a` 与 `x86_64` 提供已校验的 libmpv/FFmpeg/Oboe 依赖闭包；JNI 与 TextureView 技术 gate 通过后，按设计文档 S02/S04/S06 逐步用 native 实现替换本适配层，保持 `PlaybackBackend`/UI 的意图-快照边界。Media3 Session 仅在真实后端能实现 SimpleBasePlayer 契约后接入。
- 验证：Media3 官方 API 支持自定义 `MediaCodecSelector`，`MediaCodecInfo` 暴露软件/硬件标记；最终仍以本地编译、MuMu 实际文件播放与解码器回调为证，不依赖 UI 开关文案冒充实现。

参考： [Android Media3 DefaultRenderersFactory](https://developer.android.com/reference/androidx/media3/exoplayer/DefaultRenderersFactory)、[MediaCodecInfo](https://developer.android.com/reference/androidx/media3/exoplayer/mediacodec/MediaCodecInfo)、[mpv-android](https://github.com/mpv-android/mpv-android)。
## E00 实测补充（2026-10-04）

E00 已依本 ADR 落地并在 MuMu Android 15/API 35 跑通。当前构建、5 项单元测试和 lint 均通过。Media3 1.11.1 驱动音视频播放；`PlaybackBackend` 隔离播放命令/状态，但视频的 `PlayerView` 仍作为 Android 渲染胶水，后续原生引擎还需要独立 Surface/JNI 边界。

更正严格硬件解码的无候选行为：切换前先检查候选项；如果没有符合策略的 decoder，显示可恢复错误并保留现有播放器继续播放，不销毁仍可工作的播放链路。实际解码器切换仍以 Media3 创建播放器成功及运行时轨道信息为准。

MuMu 的同一 H.264 样片已分别验证 `OMX.qcom.video.decoder.avc` 硬件路径和 `OMX.google.h264.decoder` 平台软解路径。MuMu 对后者同时返回 `hardwareAccelerated=false` 和 `softwareOnly=false`，所以 UI 明示系统标记不完整并用常见 Android 软件 codec 前缀作兼容识别。WAV 媒体轨道报告 44.1 kHz/2 声道输入。以上模拟器证据不验证 USB DAC 独占、输出采样格式或 bit-perfect；这些必须在实体 Xiaomi/Redmi + USB DAC 上单独验收。
