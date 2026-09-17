# MUI XML Preview

这是一个零依赖的本地浏览器预览器，用同一套 `mui-app.json`、XML component、screen 和 CSS 资源构造 DOM。它不会启动 Minecraft，也不会执行 XML 中的 Java 表达式；`onclick` 只会调用预览适配器中明确注册的 Showcase action。

```powershell
cd D:\Projects\sfr\other_mods\ModularUI
python tools/xml-preview/server.py D:\Projects\sfr\smoothfont-replacement\addons\mui-xml-showcase\src\main\resources\assets\neofontrender_mui_xml_showcase\mui
```

Quasar/TypeScript/Pinia 的迁移工程位于 `tools/xml-preview-quasar`，使用 pnpm：

```powershell
cd D:\Projects\sfr\other_mods\ModularUI\tools\xml-preview-quasar
pnpm install
pnpm exec quasar build
```

它目前提供可构建的工作台壳层和 Pinia 布局状态；现有零依赖预览器仍是稳定运行入口，XML 引擎会在后续组件迁移中复用。

也可以从示例 addon 目录运行 `preview.ps1`。浏览器每 750ms 轮询资源快照，保存 XML/CSS/JSON 后自动刷新；错误会保留最后一版成功画面。右侧编辑器的“应用草稿”只改浏览器内存，不写回磁盘。

预览支持组件模板和 named slot、CSS `@import`/`@media` 子集、manifest stylesheet、按钮/切换/slider/text-field、DOM mutation、事件计数、模拟物品槽和进度条。没有 CSS 时会使用 ModularUI 默认主题尺寸和近似外观，包括 18×18 按钮与物品槽、56×18 文本框、控件状态和默认文字颜色；Styles 中归在 `ModularUI default theme`。勾选“检查元素”可查看节点属性并暂时拦截点击。游戏中的服务端 inventory、真实纹理、配方、同步协议和 Java native widget 仍需在 Minecraft 中验证。

工作台顶层分 Elements 和 Sources。Elements 中可搜索、折叠、用方向键遍历 XML 节点，选中后可沿画布下方的祖先路径切换；准星可从画面选取节点。右侧 Styles 显示匹配规则及来源，可临时关闭或编辑 CSS 属性，Computed 显示布局数值与 box model，State 显示模拟状态。Sources 提供资源列表和浏览器内存草稿编辑器。`Ctrl+F` 聚焦元素搜索，`Ctrl+Shift+C` 切换检查模式，`Ctrl+反引号` 切换 Console，`Esc` 退出检查模式。

中央画面默认按可用空间缩放，取消 Fit 可按游戏像素查看；拖动左右分隔条可调整面板，尺寸保存在浏览器。Console 可展开并拖动上边缘调高度。启用 Move window 后可拖动当前游戏窗口，Reset position 恢复游戏初始位置。浏览器内的样式修改和拖动位置不写入资源文件。
