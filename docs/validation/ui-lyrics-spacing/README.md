# U10 歌词入口／行间距验收

2026-10-08；分支dev-hifi，未提交。只修改歌曲菜单导航与歌词条目布局，保留已有H02–H05／U09代码。

## 实现

- 歌曲详情右上“…” → 歌词与播放 → 歌词显示，启动既有设置Activity，返回保留歌词分页。设置与播放页投影同一LyricStyle，不另建偏好副本。
- 移除定时歌词额外上下7dp padding和48dp布局最小高度；纯文本歌词移除上下7dp padding。LazyColumn的lineSpacing现在独立控制条目间隔，字号对应1.35倍行内行高；设置预览一致。Clickable保留自动扩大触控区域及seek，强调动画保留。

## 构建

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug --no-daemon --max-workers=4
```

[checks.txt](checks.txt)：exit0，BUILD SUCCESSFUL in 1m6s。现有JVM30项0失败／0error，lint0error／1已有ExportedService warning。git diff --check exit0。APK SHA256：`a3ff42d2613b2fb93e6e818c583b88839851abd7507edada86216718a304aab7`；主／测试APK install -r均Success，不清除应用数据。

## 真机操作

小米17 Pro Max `2509FPN0BC`，Android17／SDK37，adb `192.168.1.246:33103`。正常应用UI操作，没有ActivityScenario／connectedDebugAndroidTest。使用用户已导入的本地FLAC及原定时歌词，暂停后测量同字号非选中句；不导出媒体文件／API配置。

1. 原样式选中25sp／特粗、非选中20sp／粗体、行间距0dp；[原设置](setting-original.xml)。更新前相邻句top差186px（[before.xml](before.xml)）。
2. 新版0dp相邻句top差80px（[spacing-0.xml](spacing-0.xml)、[截图](spacing-0.png)）。节点class=android.widget.Button，clickable=true，确认走定时歌词分支。
3. [菜单入口](menu.png)／[语义](menu.xml)显示歌词显示，点击可打开原设置页。滑块改40dp保存（[setting-40.xml](setting-40.xml)），返回同一歌曲歌词分页，立即生效，无需重启／切歌。
4. 新版40dp相邻句top差200px（[spacing-40.xml](spacing-40.xml)、[截图](spacing-40.png)）。同字号步进增加120px对应density3的40dp；无额外固定留白。
5. 点击第二句，播放位置到00:23，仍暂停（[seek.xml](seek.xml)）；再经快捷入口恢复0dp，字号／字重原值保持（[restored.xml](restored.xml)）。[最终页](final.xml)为歌词页00:23，显示“播放”按钮。

首个详情展开期间过早点击歌词tab未触发切页，随后依新UI状态重新点击并取得上述有效截图；早期一次uiautomator读取未取得idle状态，后续暂停UI取证均正常，不算成成功的UI检查。

## 未执行范围

纯文本歌词分支已同步移除额外padding，未单独真机截图；API26–32／200%字体／TalkBack未新增验收。已有Compose Activity instrumentation在此OEM设备会挂起，本轮未运行，以正常UI操作验收；此前H05 native输出资格与gapless限制不因本次UI修复改变。
