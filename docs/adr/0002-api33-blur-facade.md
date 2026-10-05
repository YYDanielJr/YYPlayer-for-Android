# ADR 0002：API33 磨砂与 API26 UI 隔离

状态：U01 前端重写采用；API26–32 的实体/模拟器启动仍待验证，不宣称兼容资格通过。

用户提供 HyperOS 4 的磨砂悬浮导航参考。Miuix 0.9.4 的 `miuix-blur-android` 声明 minSdk33，而 app 的 minSdk26 是已有产品边界。构建 manifest merger 验证了冲突。读取 Maven Central 0.9.4 sources jar 的 LayerBackdrop、RuntimeShader 和 RenderEffectCompat.android 源码，确认实际磨砂需要 Android RuntimeShader/createRuntimeShaderEffect，不能在 API26–32 调用。

决定保留 app minSdk26；对单独 blur 包添加窄 `tools:overrideLibrary`，所有 blur 入口、类型和 composable 都限定在单独的 `@RequiresApi(33) @Keep Api33MiuixBlur` 类。普通 UI 只见 `HyperBackdrop`（Modifier、Boolean、Composable 函数），API26–32 分支创建无效果 facade 和不透明表面，既不创建 shader/记录图层，也不执行能力探测类。API33+再检查 RuntimeShader 支持。@Keep 防止 release 优化把高版本入口内联进低版本 facade。

这项 manifest override 只是让可选、受版本保护的依赖被打包，不能作为低版本兼容的验收证据。MuMu API35 做实际磨砂/滚动检查；发布前必须在 API26/32 做冷启动、主题/导航/列表/播放器 classloading，以及 release/R8 路径验收。若失败，移除 blur 依赖并使用不透明回退，或单独拆分高版本发行变体；不擅自提高产品 minSdk。更多布局规则见 UI_REWRITE.md。
