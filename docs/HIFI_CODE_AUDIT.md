# H05：未提交 HiFi 代码审计与歌曲详情输出切换

日期：2026-10-06 至 2026-10-08；分支：dev-hifi。审计对象是本轮开始时所有 tracked diff 与未跟踪源码，包括 H02/H03/H04；并非只检查上轮提到的停摆修复。没有提交或推送。10 月 8 日按用户指定改用 `192.168.1.246:33103` 的小米 17 Pro Max。

## 审查范围

| 层 | 文件／核验点 |
| --- | --- |
| PCM 核心 | `native/core/include/yy/core/pcm.h`、`pcm.cpp`、`pcm_test.cpp`；同时检查依赖的 format、policy、ring、JSON 的约束与计数 |
| JNI | `yy_jni.cpp`、`NativeAudioCore.kt`、`NativeAudioCoreContractTest.kt`；注册签名、版本、异常、句柄、并发、buffer 窗口与 profile wire |
| Android 输出 | `AudioDeviceProbe.kt`、`AudioOutputTypes.kt`、`AudioOutputController.kt`、`PcmSink.kt`、`AudioTrackPcmSink.kt`、`OutputFormatPlanner.kt`、`NativeOutputAudioSink.kt`；API 守卫、路由、契约、时钟、短写、严格拒绝与清理 |
| 自检 | `AudioVerificationTone.kt`、`OutputSelfTest.kt`、AudioOutput 两组 device tests；fixture 编码、独立输出、取消、哈希、计数与证据等级 |
| 播放／会话 | `OutputPathRenderersFactory.kt`、`AndroidMedia3PlaybackBackend.kt`、`PlaybackBackend.kt`、`PlayerSessionService.kt`、`PlayerApplication.kt`；解码 PCM、源位深、唯一输出、替换 player、会话改绑、后台与旧回调 |
| UI／构建 | `PlayerHostActivity.kt`、`AudioOutputScreen.kt`、清单、Gradle/catalog、OnlineApiTest 构造迁移；依赖同版本、服务类型、API26 边界与真实状态投影 |
| 记录 | README、HANDOFF、STATUS、ADR0006、hifi-v1 验收 README 与日志；区分历史失败、现有能力和未验资格 |

## 实质发现及修复

| 严重度 | 原问题 | 修复与验证方法 |
| --- | --- | --- |
| P1 | JNI 从注册表取得裸指针后解锁；另一个线程 release 可以释放仍被读写使用的 ring | shared_ptr 操作 lease；JNI 普通 worker IO mutex 同时保护 Clear／多生产者；并发释放回归 |
| P1 | JNI direct buffer 只看基地址／capacity，忽略 position、limit，还允许只读目标 | Kotlin 传 remaining slice、校验直接／可写目标和 byte 范围；前后哨兵与限长测试 |
| P1 | 负数左移在 C++17 未定义；24-in-32 上方 padding 还可能导致有符号乘法溢出 | 有界乘法／无符号移位／显式符号扩展；24 位先屏蔽 padding；极值与随机测试 |
| P1 | PCM 相邻重叠缓冲覆盖尚未读的样本 | 地址范围溢出及 overlap 校验；拒绝前保持所有字节不变的测试 |
| P1 | 设备选择只重建自研通道，SYSTEM 设置实际不起作用 | SYSTEM 使用 Media3 `setPreferredAudioDevice`；两通道都保留位置、音量、意图并替换唯一 player；实测读 AudioTrack 路由 |
| P1 | 所选设备移除时自动设为 null，可能继续向扬声器播放 | 保留失联 ID；pause/stop/flush；拒绝对失联 pin 恢复播放；不宣称公开 API 可保证物理插拔瞬间零泄漏 |
| P1 | 控制器仅凭计划 expectedSharing／mixer 请求就宣布实际共享／位完美 | 控制器不再请求或清除播放 mixer，实际 sharing 保持 Unknown；只有 sink 的契约、API 格式、USB 实际路由、无欠载等证据同时满足才 SYSTEM_REPORTED |
| P1 | 自检可以与歌曲同时创建输出；自检取消和旧结果没有代次保护 | 自检前确认旧 renderer 释放，锁住播放及切换；IO finally 完成后解锁，保留原位置但暂停；取消与结果 revision |
| P1 | 自检把 s16 参考信号按当前歌曲编码解释；要求的 mixer 又在开 track 后请求 | 固定 fixture 输入 s16 stereo；开 track 前请求并精确回读 mixer；不合格契约拒绝，所有出口 finally 清理 |
| P1 | ring pump 短写／错误丢弃尾部、阻塞写、补零；关闭在调用线程 join 后即使线程存活仍 release | 非阻塞写保留 pending tail；不补造 PCM；2ms 调度，旧线程身份检查、有限临界区关闭，无 join；实际播放头确认尾部已播放后自检结束 |
| P2 | profile 仅前 32 项；Android stereo mask=12 与核心 mask=3 直接比较 | 有界 vector 读完整 profile 列表；按声道数转换 wire mask，平台 mask 保留在 adapter；第 41 项 profile 的 JNI 回归 |
| P2 | 设置／关闭／暂停后还保留旧格式与位完美证据 | nullable live report 清空，输出 revision 隔离旧 sink；页面显示 API 格式及路由，HAL/hardware Unknown |
| P2 | 重建 player 后旧 listener／analytics 能覆盖新媒体；自研不支持变速却存了请求速度 | 所有 listener 匹配当前 player；snapshot 显示实际速度，自研限定 1×；音视频混合队列明确拒绝 |
| P1 | 宽松策略有 DSP 时 core 仍给 pristineExpected；自检会丢失原 player 音量 | 撤销 DSP 下的原样期待；自检与恢复均保留软件音量，非 unity 不请求 BIT_PERFECT；自检暂停／音量／位置回归。系统速度意图在自研固定 1× 后切回系统仍保留 |
| P2 | 未实现的声道重排、encoder delay/padding 仍被接受 | 显式拒绝这些请求并使用 SYSTEM 兼容通道，不声称无缝切歌资格 |
| P1 | PCM 格式重配时直接 reset，可能丢弃旧 track 尚未播放的尾部 | 待旧尾部播放完再配置下一 PCM 格式；暂停时重配与新 buffer 不消耗的直接 sink 回归 |
| P2 | 核心 ring 构造仅判断正值，极大声道数可绕过 64MB 上限；Clear 文档错误承诺与 reader 并发安全 | 采用 IsPlausibleFormat；非法巨大声道零容量测试；公开 Clear 的 quiescence 契约，JNI 外层锁落实约束 |
| P2 | JNI 标准库分配异常可能越过边界，GetStringUTFChars 遇异常未释放 | 注册入口统一异常边界，Java 错误；UTF chars 各出口释放；JNI 开启受控异常支持，ABI 保持 v2 |
| P2 | 把启动期暂态旧路由当成拔插而直接失败 | 启动期最多等待 3s 实际路由确认，确认前不写 PCM；已建立路由失效立即停止 |
| P1 | USB 44.1kHz／24packed 请求虽接受 pin，开始写 PCM 后仍路由到旧蓝牙 | 创建 track 前同时请求设备报告的匹配 DEFAULT mixer 属性，并由 sink 负责释放；这只配置共享 mixer，不增加原样或独占资格。先前失败被保留，USB／蓝牙四组实际路由测试重跑通过 |
| P1 | format configure 等待旧尾部后，flush 丢掉待生效格式；新 buffer 可能用旧格式解释 | flush 保留 pendingFormat，由下一 buffer 在旧数据清空后落实配置；格式重配与 seek/flush 回归 |
| P1 | 暂停／flush 后的短尾部不足 AudioTrack 启动门槛，下一格式等待播放头永远不前进 | API31+ 排空时降低 start threshold；保留本机首次失败与修复后测试。API26–30 短尾部资格未取证，SYSTEM 为兼容路径 |
| P1 | 视频 StrictHardware 的 codec selector 同时过滤 FLAC 等软件音频解码器 | selector 的硬解筛选限定 video MIME；音频使用默认解码器，并在切换回归中保留 StrictHardware 偏好 |
| P1 | float 源在自研 renderer 中会被强制请求 s16；BIT mixer 下音量变化可能无效 | float 源明确拒绝并提示 SYSTEM；只有软件与系统音量已为 unity 才请求 BIT，绝不调高音量；活动 BIT 期间音量变化暂停/flush/报错。普通共享 profile 明确排除 BIT |
| P1 | 自检直到信号送完才核对指定路由，可能先把信号送给旧设备 | 开 track 后先确认稳定路由再供 PCM，供帧与排空中继续检查路由与 BIT 音量；失效 finally 关闭，不自动改到扬声器 |
| P1 | 自检时设备／方式已锁住，高级 sharing／transparency 仍可重建歌曲输出 | 两策略也加 runtime gate，backend 在自检期间拒绝所有 rebuild；新增断言确认原 player／策略不变，结束才恢复歌曲 |

## 歌曲详情交互

`MusicPlayerScreen.kt` 右上 Miuix 设置图标“音频输出”，以及“歌词与播放”里的“音频输出”行，打开同页 WindowDialog。两个选项是“输出方式”和“输出设备”，直接调用同一个 PlayerRuntime。断开的指定设备仍显示“设备已断开”；测试期间不提供切换。系统路径与自研路径名称、设备列表和设置页同步。未另建播放器。

10 月 8 日最终 APK 经普通 MainActivity 启动，真实库歌曲进入详情，实际操作 Native → SYSTEM、跟随系统 → USB → A2DP，Native 页面分别回读 lifeme USB 和 OPPO；切换后 MediaSession 仍 PLAYING，position=215218、error=null，随后用媒体暂停并恢复原 Native／跟随系统偏好。界面截图见 validation H05。播放持续动画时 uiautomator 偶发 idle 失败，暂停后 dump 正常；没有把 Activity instrumentation 旧挂起算通过。

## 资格及限制

本次代码核验与回归不能替代硬件资格：AudioTrack.getFormat/getSampleRate 仅是配置的 API 格式；getAudioDevicesForAttributes 仅是预期路由。当前 DAC 无 BIT_PERFECT 组合，真实独占、BIT_PERFECT 正面案例、端到端数字捕获都不能标为通过。源码支持边界也包括整数 PCM、1×、无 encoder delay/padding 修剪、无声道重排、无 tunneling／aux effect 处理；Media3 音频 session/attributes 的额外非默认定制尚未作为公开功能提供。

API26–32 类加载、API26–30 短文件／格式尾部排空、物理拔插与焦点、锁屏长期运行、96kHz 以上输出仍需专项设备。所有实际命令、通过／失败和 APK 哈希以 STATUS 与 `validation/hifi-v1/README.md` 的 H05 记录为准。

参考契约：锁定本地 Media3 1.11.1 源码／javap；[AudioRouting](https://developer.android.com/reference/android/media/AudioRouting)、[AudioManager](https://developer.android.com/reference/android/media/AudioManager)、[Media3 AudioSink 1.11.1](https://raw.githubusercontent.com/androidx/media/1.11.1/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/audio/AudioSink.java)。

短尾部门槛依据 [AudioTrack start threshold](https://developer.android.com/reference/android/media/AudioTrack#setStartThresholdInFrames(int))，这是 API31+ 的流启动门槛设置，不是原样或独占契约。
