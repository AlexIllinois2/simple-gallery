# Simple Gallery（修改版）

<img alt="Logo" src="graphics/icon.png" width="120" />

基于 [SimpleMobileTools/Simple-Gallery](https://github.com/SimpleMobileTools/Simple-Gallery)（v6.28.1）的本地修改版本，在原版相册浏览、编辑、恢复、隐藏等功能基础上，新增了 **SQLite 数据库相册** 功能。

## 本地修改内容

### SQLite 数据库相册（核心新功能）

可将相册文件夹打包为单个 SQLite 数据库文件（`.db`），并像普通文件夹一样在应用内浏览其中的媒体：

- **文件夹转数据库**：在“设置 → 包含的文件夹”中长按文件夹，选择“转换为 sqlite db”。该文件夹及其全部子文件夹和媒体内容会被转换为一个 `.db` 文件（生成在原文件夹旁边），原文件夹和内容不会被删除。
- **可选密码加密**：转换时可选择使用密码加密（基于 [SQLCipher](https://github.com/sqlcipher/sqlcipher) `android-database-sqlcipher:4.5.4`），未加密的数据库以普通 SQLite 方式打开。
- **管理数据库相册**：在“设置 → 管理包含的 sqlite db”中添加 `.db` 文件，添加后其内容会像普通文件夹一样展示（支持无限层级子文件夹），并可查看数据库属性。
- **媒体浏览支持**：图片、视频、GIF、RAW、SVG 均可直接从数据库内浏览/播放；图片通过自定义 Glide ModelLoader 与 BitmapRegionDecoder 从数据库 BLOB 分块（512KB）加载，支持大图手势缩放。

涉及的主要新增代码：

| 文件 | 说明 |
| --- | --- |
| `app/src/main/kotlin/.../databases/GalleryDbStore.kt` | 数据库读取层（SQLCipher 加密 / 明文 SQLite），BLOB 分块读取 |
| `app/src/main/kotlin/.../helpers/GalleryDbs.kt` | `sgdb://<dbKey>/@root/...` 虚拟路径方案 |
| `app/src/main/kotlin/.../helpers/FolderToDbConverter.kt` | 文件夹 → 数据库转换 |
| `app/src/main/kotlin/.../activities/IncludedDBsActivity.kt` | 管理包含的 sqlite db |
| `app/src/main/kotlin/.../dialogs/DbPasswordDialog.kt` | 加密密码输入对话框 |
| `app/src/main/kotlin/.../dialogs/GalleryDbPropertiesDialog.kt` | 数据库属性对话框 |
| `app/src/main/kotlin/.../glide/DbModelLoader.kt` | 从数据库加载媒体的 Glide ModelLoader |
| `app/src/main/kotlin/.../helpers/DbRegionDecoder.kt` | 数据库大图区域解码 |
| `app/src/main/kotlin/.../adapters/ManageDBsAdapter.kt` | 数据库列表适配器 |

### 其他调整

- 依赖 `net.zetetic:android-database-sqlcipher:4.5.4`，并为其添加 ProGuard keep 规则。
- `simple-commons` 更新至 `ea86877113`。
- `settings.gradle.kts` 增加本地 Maven 镜像（`file:///C:/Users/45574/.gradle/local-m2`），用于承载已无法从原始仓库解析的旧工件（jcenter 已下线、Google VR SDK 已从 Google Maven 移除、部分 JitPack 构建失败）——在其他机器构建时需按注释调整该路径。

## 构建

```bash
# FOSS 版本
./gradlew assembleFossRelease

# 完整版（含 PhotoEditor SDK，需 imgly 授权配置，见 gradle/imglysdk.gradle）
./gradlew assembleProprietaryRelease
```

- 签名配置读取根目录 `keystore.properties`（模板见 `keystore.properties_sample`）。
- 编译需 JDK 17，Gradle 8.2.1 / AGP 7.4.0，compileSdk 34。

## 原版简介

Simple Gallery 带来了你在 Android 上一直缺失的全部照片浏览与编辑功能，集优雅与易用于一身。浏览、管理、裁剪和编辑照片或视频比以往更快，可恢复误删文件，或为最珍贵的照片和视频创建隐藏相册。借助高级文件格式支持与全面的个性化设置，你的相册终于可以完全按照你想要的方式工作。

**高级照片编辑器** —— 直观的手势让照片编辑轻而易举。随手裁剪、翻转、旋转、调整尺寸，或应用时尚滤镜让照片瞬间出彩。

**全格式支持** —— 支持 JPEG、PNG、MP4、MKV、RAW、SVG、GIF、全景照片、视频等众多格式。

**高度可定制** —— 从界面到底部工具栏的功能按钮，Simple Gallery 给你所需的创作自由。

**找回误删的照片和视频** —— 快速恢复任何被误删的照片和视频，既是最佳媒体相册，也是出色的照片保险箱。

**保护你的隐私** —— 使用 PIN、图案或指纹限制他人查看或编辑选定的照片和视频，可保护整个应用或锁定特定功能。

<div style="display:flex;">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_en-US.jpeg" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_en-US.jpeg" width="30%">
<img alt="App image" src="fastlane/metadata/android/en-US/images/phoneScreenshots/3_en-US.jpeg" width="30%">
</div>

上游项目基于 GPL-3.0 许可证开源，详见 [LICENSE](LICENSE)。
