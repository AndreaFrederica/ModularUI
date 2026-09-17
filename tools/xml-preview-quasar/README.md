# MUI XML Preview

这是基于 Quasar、Vue 3、TypeScript 和 Pinia 的 XML 游戏外预览工作台。预览运行时保留 ModularUI 的 XML 编译、CSS 级联、默认样式、窗口定位、拖动、元素检查和高亮行为。

## 启动

先启动只读资源服务：

```powershell
python ..\xml-preview\server.py `
  D:\Projects\sfr\smoothfont-replacement\addons\mui-xml-showcase\src\main\resources\assets\neofontrender_mui_xml_showcase\mui
```

然后在本目录执行：

```powershell
pnpm install
pnpm exec quasar dev --port 9000
```

打开 `http://127.0.0.1:9000/`。开发服务器会把 `/api` 代理到资源服务的 `8765` 端口；生产部署时需要让资源接口与预览页面处于同一源，或配置等效反向代理。

## 检查与构建

```powershell
pnpm exec eslint "./src*/**/*.{ts,js,mjs,cjs,vue}"
pnpm exec quasar build
```

`src/stores/preview.ts` 是 Pinia 状态入口，`src/lib/` 保存 XML 运行时兼容层，后续可以按工作台区域逐步拆成 Vue 组件而不改变预览行为。
