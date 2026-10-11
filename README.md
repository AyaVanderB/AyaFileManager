# Aya File Manager · Aya 文件管理器

一款使用 Kotlin 和 Jetpack Compose 开发的 Android 本地文件管理器。借鉴 Windows 资源管理器的导航逻辑，以紧凑列表、清晰路径和本地搜索，减少在手机上查找与整理文件的操作成本。

> 当前为开发版本。基础浏览与文件操作已实现，多选、压缩解压等功能仍在规划中。

## 功能

### 浏览与导航

- 默认紧凑详情列表，显示文件名、修改时间、类型和大小。
- 支持小图标网格与列表切换，并保存视图偏好。
- 可点击的路径层级、前进、后退和返回上级目录。
- 下载、相机、文档常用目录快捷入口。
- 支持输入绝对路径，打开文件所在目录，复制完整路径。
- 按名称、修改时间或大小排序；目录浏览时文件夹优先。
- 可切换显示以点号开头的隐藏条目。
- 图片缩略图、文件类型图标和系统深浅色主题。

### 本地搜索

- 使用 Room 保存本地文件索引。
- 支持文件名包含、文件名前缀、拼音包含、拼音首字母和后缀精确搜索。
- 按图片、视频、音频、文档、压缩包和安装包筛选。
- 大文件筛选（100 MiB 及以上）和最近 7 天修改筛选。
- 独立的最近修改页面：今天、近一周、近一个月。
- 手动重建索引；搜索范围取决于已授予的存储权限。

### 文件操作

- 新建文件夹、重命名。
- 长按文件或文件夹，选择复制或移动，再前往目标目录粘贴。
- 分享文件，调用已安装的应用打开文件。
- 同名目标不会被覆盖；禁止将文件夹粘贴到自身或子目录。

## 使用方式

1. 首次启动时，按需授权媒体读取或全部文件访问。
2. 浏览完整目录、收录非媒体文件和执行文件写入操作，需要相应存储访问权限。
3. 点击文件夹进入，点击文件选择应用打开；长按条目显示操作菜单。
4. 在搜索框输入关键词，展开筛选可切换搜索模式与文件类别。
5. 手机中的文件发生变化后，可刷新目录；搜索结果未更新时，在菜单中重建搜索索引。

## 构建

### 环境要求

- 支持 Android Gradle Plugin 9.1.1 的 Android Studio。
- JDK 21：项目的 Gradle Daemon JVM 配置指定此版本。
- Android SDK Platform 36.1，与项目 `compileSdk` 配置一致。
- Android 8.0（API 26）及以上设备；目标 SDK 为 36。

### Android Studio

1. 克隆或下载仓库，用 Android Studio 打开项目根目录。
2. 根据 IDE 提示安装 SDK 并完成 Gradle Sync。
3. 选择模拟器或连接 Android 设备，运行 `app`。

`local.properties` 是本机 SDK 路径配置，不提交到仓库；Android Studio 通常会为本机创建该文件。

### 命令行

Windows PowerShell：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest lintDebug
```

macOS / Linux：

```bash
chmod +x gradlew
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
```

调试 APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

调试 APK 用于开发与测试。对外正式发布应生成使用自己的签名密钥签署的 Release APK；签名密钥不应提交到仓库。

## 技术与目录

| 模块 | 技术 / 职责 |
| --- | --- |
| 界面 | Jetpack Compose、Material 3 |
| 状态管理 | ViewModel、Kotlin Coroutines、Flow |
| 本地索引 | Room、FTS、KSP |
| 文件来源 | 系统 MediaStore、直接目录读取 |
| 缩略图 | Coil |
| 文件打开与分享 | Android Intent、FileProvider |

```text
app/src/main/java/com/example/ayafilemanager/
├── data/          # 数据库、索引、搜索与目录读取
├── domain/        # 文件模型与仓库接口
├── permission/    # 存储权限管理
├── ui/explorer/   # 浏览界面、状态、文件操作
└── ui/theme/      # 主题与排版
app/src/test/      # 本地单元测试
app/schemas/       # Room 数据库结构
```

## 权限与数据

- 文件索引保存在本机，当前应用不包含文件上传功能。
- 媒体读取权限用于建立可访问媒体的索引。
- 全部文件访问权限用于浏览共享存储目录与收录非媒体文件；它不等于访问 Android 系统的所有目录。
- 文件分享和打开通过系统 Intent 向用户选择的应用提供临时读取权限。

## 当前限制

- 尚未实现多选批量操作、删除 / 回收站、压缩解压、网络存储或云盘。
- 移动使用文件系统重命名，暂不支持跨存储卷移动。
- 递归复制不支持符号链接。
- Android 系统保护目录（例如部分 `Android/data`、`Android/obb` 路径）仍可能无法访问。
- 普通搜索默认最多返回 300 项；最近修改页面不使用这一上限。
- 拼音转写依赖 Android 10（API 29）及以上的平台 ICU；较旧系统保留原文字归一化，中文拼音搜索能力受限。
- 隐藏文件开关控制条目名称以点号开头的文件与文件夹，并非完整的系统隐藏属性管理。
- 当前已通过 Debug 构建、单元测试和 Android Lint；尚未完成完整真机兼容性及视觉验收。

## 后续计划

- [ ] 多选与批量操作
- [ ] 常用目录收藏
- [ ] ZIP 压缩与解压
- [ ] 文件删除与回收站
- [ ] 大文件分析与存储统计
- [ ] 完善平板 / 横屏布局与无障碍体验

## 贡献

欢迎通过 Issue 提交问题或建议。报告问题时请附上 Android 版本、应用版本、复现步骤，以及相关截图或日志（隐去个人文件路径和敏感内容）。

提交代码前建议执行：

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## 许可证

当前仓库尚未指定开源许可证。如需以开源项目发布，请由项目作者选择许可证并添加 `LICENSE` 文件；公开源码本身不等于授权他人使用、修改或分发。
