# 字体设计工作室

作者：陈淑婷

类 Photoshop 的字体/字效设计工具：多图层文字与形状编辑、自定义字体导入、素材导入与着色、3D 立体字、路径文字、滤镜、样式预设、工程保存与 PSD 导出。

官方群：https://qm.qq.com/q/20FtspfV72

## 目录结构

```
2026textzixiao/
├── web/                  # 网页版（单文件，开箱即用）
│   └── index.html        # 直接用浏览器打开，或部署为静态网站
└── android/              # Android 壳工程（WebView 封装，可编译 APK）
    └── app/src/main/
        ├── assets/studio/index.html   # 内置的工作室页面（与 web 版同步更新）
        └── java/com/textzixiao/studio/MainActivity.kt
```

## 网页版使用

直接用浏览器打开 `web/index.html`，或把它部署到任何静态托管上。
页面内的字体/素材导入、PNG/PSD/工程文件导出均为本地操作，不上传服务器。

## Android 版构建

1. 用 Android Studio 打开 `android/` 目录，等待 Gradle 同步完成
   （需要 JDK 17；`gradle/wrapper` 下只有 properties，Studio 会自动补全 wrapper）
2. Run 按钮安装到手机，或 Build → Generate Signed Bundle / APK

Android 壳已处理：
- WebView 文件选择器（字体/素材导入）
- 页面内 blob 下载转存到系统“下载”目录（PNG/PSD/工程文件导出）
- 应用内更新：下载 GitHub Releases 的 APK 并引导安装
- 返回键回退页面历史

## 发版流程（检查更新对接）

工作室内的“设置 → 检查更新”和启动自检，读取仓库根目录的
[`version.json`](./version.json)（经由 jsDelivr/raw.githubusercontent）：

```json
{
  "version": "1.2.0",          // 与页面内 APP_VERSION / Android versionName 对齐
  "versionCode": 2,            // 与 Android versionCode 对齐
  "title": "v1.2.0 更新来啦",   // 弹窗标题
  "notes": ["更新内容1", "..."],// 弹窗里的最新动态
  "apkUrl": "https://github.com/shuting52/2026textzixiao/releases/download/v1.2.0/textzixiao-v1.2.0.apk",
  "webUrl": "https://github.com/shuting52/2026textzixiao",
  "releasesUrl": "https://github.com/shuting52/2026textzixiao/releases"
}
```

发新版时按顺序做：

1. 改 `version.json`（版本号、标题、更新日志）
2. Android：在 Android Studio 里把 `app/build.gradle.kts` 的
   `versionCode` / `versionName` 对齐，Build → Generate Signed APK，
   把 APK 传到 GitHub → Releases → 新建 `vX.Y.Z` 版本（文件名与 `apkUrl` 一致）
3. 网页：把新版页面同步到 `web/index.html` 与
   `android/app/src/main/assets/studio/index.html`（两者保持一致），commit 并 push

注意：APK 需要在你自己的 Android Studio 里打包上传，我这台机器没有 Android SDK，
编不出 APK；`apkUrl` 指向的 Releases 文件不存在时，App 内更新会弹下载失败提示。

## 更新网页内容后同步到 Android

把新版页面覆盖以下两个位置（二者保持一致）：

- `web/index.html`
- `android/app/src/main/assets/studio/index.html`

## 功能一览

- 文字图层：自定义字体导入（ttf/otf/woff）、渐变/纹理填充、描边（外/居中/双层）、
  3D 挤压（深度/方向/透视/侧面渐变）、投影/发光/浮雕/内阴影/倒影、路径文字（直线/弧形/波浪）
- 素材图层：图片导入、着色、模糊/噪点/锐化滤镜、混合模式
- 形状图层：矩形/圆角/椭圆/三角/星形/爱心/箭头/气泡/六边形/爆炸形
- 画布：1:1 默认、渐变背景、拖动/缩放/双指缩放、图层拖动摆放
- 样式预设库一键套用；设置页；关于页
- 工程文件保存/读取；PSD 导出；PNG/JPG/WebP 多分辨率导出
