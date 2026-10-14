# U14 收回回弹与持续封面绘制

2026-10-14，`dev` 工作区，用户要求保留收回回弹。仅修改 `ui/YYPlayerApp.kt` 与记录，保留所有 U11–U13 修改；没有提交／推送，master 与 Rust 不变。

## 实现

- 恢复轻微欠阻尼弹簧（刚度 230、阻尼比 0.8、阈值 0.0001），封面位置／尺寸使用带符号进度；不在经过终点时截断回弹。
- 回弹结束后由原共享绘制层继续显示底栏小封面，不再切换另一绘制节点；首页按压位置仍跟随实测底栏位置。减少动态效果／视频保留普通底栏图。
- 背景和控件透明度独立限制，保留 U13 透明页面触摸拦截与退出；回弹中重开时对新页面锚点重新起步，避免歌词小封面锚点被直接当作大封面锚点。

## 真机证据

设备：有线小米 13 Ultra `c9e352e3`、`2304FPN6DC`、API36、`OS3.0.308.0.WMACNXM`。原有自制 QA Song 4／低幅 FLAC／纯色封面和双语歌词，始终暂停操作。

- [完整收回尾段](spring.mp4)：封面稍微缩小并越过落点，然后连续复位；末尾保持同一绘制层，未见瞬间换图。
- [收回途中重开](reopen.mp4)：大封面和歌词页收起后约 350ms 点击 `(800,2970)`，均重开封面，无“歌词与播放”弹窗，无意外播放。检查了歌词圆角小封面到首页圆形封面的过渡。
- [边界采样](cover-bounds.csv)：从 720×1600 原录屏按 60fps 重采样，在底部区域以自制粉色封面像素提取边界。可见宽度从 94px 下降到约 71px，再逐步恢复至稳定的 79px；顶部从 y1466 回到 y1448。它用于证明存在回弹与连续回落，不是原生帧率测试；视频重采样会重复帧，压缩和抗锯齿有约 1–2px 误差。早期图像超出采样区域的数据有截断，不能用于完整封面宽度分析。

两段公开录屏裁去系统状态栏，CSV 坐标仍对应原录屏。没有使用个人媒体。安装 `adb -s c9e352e3 install -r app/build/outputs/apk/debug/app-debug.apk` → Success，未清除数据；结束音乐库、4 首原顺序队列、QA Song 4 暂停。

## 检查

`./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4` → exit 0／BUILD SUCCESSFUL 1m10s，见 [日志](checks.txt)。JVM 34 项／0 失败，lint 0 error／2 既有 warning（NewerVersionAvailable、ExportedService）。`git diff --check` exit 0。

APK SHA256：`31C6A63D0E3C69C71FF4B4582F4583B8F81950F7721B735BAC48B10F85E74F25`。

本轮仅转场修改，未重跑 U13 已通过的 5 项播放 instrumentation；已知挂起的 Compose suite、release 帧率、横屏／200% 字体／TalkBack／API26–32 未重验，未增加音频输出资格。最终回弹幅度与手感待用户复核。
