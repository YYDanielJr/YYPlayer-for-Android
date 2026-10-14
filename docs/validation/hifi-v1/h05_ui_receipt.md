# H05 歌曲详情输出控制：真实界面验收

2026-10-08，小米 17 Pro Max / 2509FPN0BC，SDK37、OS4.0.0.44.XPBCNXM，adb `192.168.1.246:33103`。
APK SHA256：`1219906f9f6b00dcfe21e9c904322a89b85228eaacdd787e94dd4898bc7d8734`。

使用 `adb install -r` 保留数据、`am start -n yy.daniel.yyplayerforandroid/.MainActivity` 正常启动，从真实媒体库点选既有 24-bit FLAC《编织光流 (For Your Name)》，自动进入歌曲详情。没有使用 Preview/mock 或新播放器。

实际操作：

1. 右上图标 contentDescription 为“音频输出”，打开同页 WindowDialog；“输出方式／输出设备”均可点选，中文和取消按钮完整可见。截图 [h05_music_output](shots/h05_music_output.png)。
2. 输出方式下拉真实出现“系统输出”和“自研输出（原样优先）”，选择系统后主弹窗立刻显示“系统输出”；再改回自研，当前播放未回到开头。
3. 设备下拉出现跟随系统、手机设备、OPPO SCO/A2DP、USB；明确选 A2DP 而非蓝牙通话。选择 USB 后主弹窗显示 lifeme，并回读 44100Hz/24bit/stereo；选择 A2DP 后回读 OPPO。
4. A2DP 上由自研改到系统，关闭弹窗后真实 MediaSession 为本应用的 media button session：`active=true state=PLAYING(3), position=215218, speed=1.0, error=null`。SYSTEM 页面没有用预期路由冒充读回，显示“路由待回读”；其实际 AudioTrack USB／BT 路由由 OutputAuditDeviceTest 独立验证。
5. 媒体暂停后详情显示 03:37 / 04:34、“播放”按钮；恢复操作前的自研／跟随系统选择。未清除库和偏好，未改变系统音量。

证据通过 `uiautomator dump /sdcard/yyplayer-h05-window.xml` 和 `adb pull` 检查真实节点，点击使用当次 dump 的 bounds；XML 临时收在 ignored `docs/validation/acl-recovery/h05/ui-*.xml`。持续播放动画时 dump 偶发 `could not get idle state`，暂停后正常；不把该失败的旧 XML 当新状态。截图用 `screencap -p`，只保存本应用音乐输出页。

本次没有宣称 Compose Activity instrumentation、物理耳机按键、200% 字体、TalkBack 或暗色截图验收通过。真实 service/controller 的控制与后台连续播放由组合回归另行验证。
