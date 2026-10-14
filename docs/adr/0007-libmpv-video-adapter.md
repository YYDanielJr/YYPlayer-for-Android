# ADR 0007：可选 libmpv 视频后端与桌面功能对齐

日期：2026-10-14。状态：已接入可选视频后端，MuMu 专项通过；不表示 S02 全部通过或默认后端迁移完成。

## 事实

用户要求继续补齐 Rust 桌面视频功能，并明确允许参考 libmpv-android、更换视频后端。Media3 纵切缺少完整 libass、精确双向逐帧和通用章节接口。`jarnedemeulemeester/libmpv-android` 是实际发布到 Maven Central 的独立库，与不是 AAR 的 mpv-android 应用不同。

候选固定为 `dev.jdtech.mpv:libmpv:1.0.0`，源码提交 `fcf6745703dc1265bca88f12fee8fc355ddf251e`，AAR SHA256 `df146592480fc8418415a06b1f1a1d6318b0088e21f52254b0e9a82b61ca8fa2`。上游 Ubuntu 发布 workflow 构建 mpv 0.41.0、FFmpeg 8.1、libass 0.17.4；包装器 MIT 不代表其所有二进制依赖均为 MIT。上游依赖按 tag 下载，完整依赖源码 SHA／许可证交付仍须进一步冻结，不能宣称可复现发布资格。

## 决定

使用固定且校验哈希的上游 Linux CI AAR，仅提取 arm64-v8a／x86_64 原生库，不使用其 Java/JNI 包装器。项目自己的 C++ worker 统一执行 mpv client API；JNI 不在主线程等待。音乐继续使用原后端；视频增加可选 libmpv，保留 Media3，未过设备与交付 gate 前不改变默认。切换先释放旧输出，完成后才准备新输出；系统 MediaSession 指向真正驱动播放的 Player 适配器。

真实装载发现 NDK28 libc++ 缺少该库需要的浮点 `from_chars` 符号。保留本项目 NDK28 编译器／头文件，CMake 显式导入 AAR 的固定 NDK29 libc++ 供两个 JNI 桥共同链接，关闭 NDK 自动附带 STL；不靠任意 pickFirst 解决版本冲突。APK 审计确认每 ABI 只有一份 libc++。MuMu 已验证 libmpv 和原 native PCM 音乐均真实装载／播放，arm64 实体装载和 API26 仍待验。

上游 `ffmpeg.sh` 明确启用了 `--enable-gpl --enable-version3`，必须按包含 GPLv3+ 组件处理，不能以包装器 MIT 宣传整体许可。本轮只提供本地调试构建；发布前仍需完成全部依赖源码版本／许可证／相应源码交付冻结，不能将 AAR hash 校验当作这一 gate 已通过。

Surface 以 Java GlobalRef 传入 `wid`，由 mpv Android EGL context 使用；Surface 替换／销毁先在 worker 销毁 VO／引擎，完成后释放 Java Surface 和 SurfaceTexture，不在主线程 join。重建保留位置、播放意图、速度和视频设置。TextureView 继续使用一次矩阵完成缩放／连续旋转；libass 在视频画布内绘制，随画面变换。

仅开放 UI 白名单命令／属性，禁止用户任意脚本、滤镜、shell；关闭配置和脚本自动加载，TLS 验证保持开启。content URI 在后台打开独立 FD lease，拒绝不可 seek 的输入并说明原因，不静默复制视频。精确逐帧直接调用 mpv frame-step／frame-back-step，不按估算 FPS seek。

播放器选项：gpu／Android EGL＋TextureView、keepaspect=no（UI 单次矩阵适配比例）、mediacodec-copy 或 software、audiotrack、keep-open、系统字体目录、libass 原样式默认保留。严格硬解在 libmpv 菜单不开放，继续使用已有 Media3 资格路径；libmpv 的 `hwdec-current` 仅作为实际路径展示，不伪造 MediaCodec hardware flag。SAF 外置字幕在后台限量复制，ASS 由 libass 原生 ACK 校验，允许纯绘图 ASS。

原生 client.h 来自 mpv v0.41.0，SHA256 `3c073236a09cb456c6e80587d5523c8771c32fb22ca014e0f99cc3d43905b75c`，保留 ISC 声明。每个打包 so 的完整 hash、SONAME、DT_NEEDED 与 ELF／ZIP 对齐见 [U16 native audit](../validation/video-u16/native-audit.json)。无自动安装脚本、WSL 构建、未知 APK 拆库或 Rust 改动。

## 验证与退出条件

必须验证两种 ABI 的 ELF 16 KB、APK ZIP 对齐、单一 libc++／FFmpeg、真实装载、章节／逐帧／偏移／ASS、会话与音视频切换、Surface 重建和旧 generation 拒绝。MuMu 只能提供相应专项证据；API26、物理设备硬解／HDR／多指／性能和许可证源码交付未验证时保持可选资格，不把其当作最终默认后端已交付。

来源：<https://github.com/jarnedemeulemeester/libmpv-android/tree/fcf6745703dc1265bca88f12fee8fc355ddf251e>；<https://repo.maven.apache.org/maven2/dev/jdtech/mpv/libmpv/1.0.0/>。
