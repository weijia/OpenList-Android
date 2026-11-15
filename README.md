# OpenList Android App

这是一个将 OpenList 文件管理器打包为 Android App 的项目。

## 功能特性

- ✅ **内置 OpenList 服务端** - 在 Android 设备上直接运行 OpenList 服务器
- ✅ **WebView 界面** - 使用原生 WebView 显示 OpenList 前端页面
- ✅ **后台运行** - 支持后台保活，带通知栏控制
- ✅ **开机自启** - 支持设备启动时自动运行
- ✅ **配置管理** - 可配置端口、日志级别等参数
- ✅ **多账号支持** - 管理多个 OpenList 账号配置
- ✅ **Material Design 3** - 现代化的 UI 设计

## 快速开始

### 方式一：GitHub Actions 自动构建（推荐）

1. Fork 本仓库到你的 GitHub 账号
2. 进入 Actions 页面，启用 GitHub Actions
3. 点击 "Build OpenList Android APK" 工作流 → "Run workflow"
4. 等待构建完成，下载 APK 文件

### 方式二：本地构建

#### 1. 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17 或更高版本
- Android SDK 35
- Kotlin 2.0+

#### 2. 克隆并配置

```bash
git clone <你的仓库地址>
cd OpenListApp

# 复制本地配置示例
cp local.properties.example local.properties
# 编辑 local.properties，设置你的 Android SDK 路径
```

#### 3. 下载 OpenList 二进制文件

```bash
chmod +x download-binaries.sh
./download-binaries.sh
```

这会下载以下架构的二进制文件到 `app/src/main/jniLibs/`：
- `arm64-v8a/libopenlist.so` (ARM64，大多数现代设备)
- `armeabi-v7a/libopenlist.so` (ARM32，旧设备)
- `x86_64/libopenlist.so` (x86_64，模拟器)
- `x86/libopenlist.so` (x86，旧模拟器)

#### 4. 构建 APK

**使用 Android Studio：**
1. 打开项目文件夹 `OpenListApp`
2. 等待 Gradle 同步完成（首次会自动下载 Gradle Wrapper）
3. 点击菜单 `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`

**使用命令行：**
```bash
./gradlew assembleDebug
```

APK 文件位置：`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
OpenListApp/
├── .github/workflows/           # GitHub Actions 工作流
│   └── build.yml               # 自动构建配置
├── app/
│   ├── src/main/
│   │   ├── java/com/openlist/app/
│   │   │   ├── MainActivity.kt              # 主界面（WebView）
│   │   │   ├── SettingsActivity.kt          # 设置界面
│   │   │   ├── AccountManagerActivity.kt    # 账号管理界面
│   │   │   ├── OpenListApplication.kt       # 应用入口
│   │   │   ├── service/
│   │   │   │   └── OpenListService.kt       # 后台服务
│   │   │   ├── data/
│   │   │   │   ├── SettingsDataStore.kt     # 设置存储
│   │   │   │   └── AccountDataStore.kt      # 账号存储
│   │   │   ├── receiver/
│   │   │   │   └── BootReceiver.kt          # 开机启动接收器
│   │   │   └── ui/theme/                    # Compose 主题
│   │   ├── jniLibs/                         # OpenList 二进制文件（.so）
│   │   └── res/                             # 资源文件
│   └── build.gradle.kts                     # 应用构建配置
├── build.gradle.kts                         # 项目构建配置
├── settings.gradle.kts                      # 项目设置
├── download-binaries.sh                     # 下载二进制脚本
└── gradle/
    ├── libs.versions.toml                   # 依赖版本管理
    └── wrapper/                             # Gradle Wrapper
```

## 安装和使用

### 安装 APK

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 首次启动

1. 打开 OpenList App
2. 应用会自动启动 OpenList 服务
3. WebView 会加载本地服务器页面 (http://127.0.0.1:5244)
4. 首次使用需要在设置中配置存储源

### 配置 OpenList

1. 点击右上角菜单 → **设置**
   - 修改服务器端口
   - 配置开机自启
   - 设置日志级别

2. 点击右上角菜单 → **账号管理**
   - 添加多个 OpenList 配置
   - 快速切换不同配置

## 技术实现

### 核心原理：绕过 Android 10+ SELinux W^X 限制

Android 10+ 禁止从应用数据目录执行原生二进制文件。解决方案：

1. 将 OpenList 二进制重命名为 `libopenlist.so`
2. 放入 `jniLibs/<abi>/` 目录
3. Android 安装时提取到 `nativeLibraryDir`（具有 `exec_type` SELinux 标签）
4. 通过 `ProcessBuilder` 执行

需要配合：
- `AndroidManifest.xml`: `android:extractNativeLibs="true"`
- `build.gradle.kts`: `useLegacyPackaging = true`

### 架构说明

1. **服务端运行**：使用 `ProcessBuilder` 启动 OpenList 二进制文件
2. **前端显示**：使用 Android WebView 加载本地服务器
3. **后台保活**：使用前台服务 + WakeLock 防止被系统杀死
4. **数据存储**：使用 DataStore 保存配置和账号信息
5. **架构适配**：根据设备 CPU 架构自动选择对应二进制文件

### 权限说明

- `INTERNET`：网络访问
- `FOREGROUND_SERVICE`：前台服务
- `POST_NOTIFICATIONS`：显示通知
- `RECEIVE_BOOT_COMPLETED`：开机自启
- `WAKE_LOCK`：保持 CPU 运行

## GitHub Actions 工作流

### 触发条件

| 触发方式 | 说明 |
|---------|------|
| push 到 main | 自动构建，并创建 GitHub Release |
| Pull Request | 仅构建验证，不上传 Release |
| 手动触发 | 可指定 OpenList 版本 tag |

### 构建输出

- **Debug APK**: 带调试信息，适合测试
- **Release APK**: 优化后的版本

### 使用自定义 OpenList 版本

在手动触发时，可以指定 OpenList 的 release tag（如 `beta`、`v3.0.0` 等）。

## 注意事项

1. **存储空间**：OpenList 需要一定的存储空间用于缓存和数据库
2. **电池优化**：建议将 App 加入电池优化白名单以保证后台运行
3. **端口占用**：默认使用 5244 端口，如果被占用可在设置中修改
4. **数据安全**：账号密码使用 Android 密钥库加密存储

## 自定义开发

### 修改服务器参数

编辑 `OpenListService.kt` 中的 `startServer()` 函数：

```kotlin
val pb = ProcessBuilder(
    binaryFile.absolutePath,
    "server",
    "--data", dataDir.absolutePath,
    "--force-bin-dir"
    // 添加其他参数
)
```

### 添加新功能

1. 在 `data/` 包中添加数据模型
2. 在 UI 包中添加 Compose 界面
3. 在 `AndroidManifest.xml` 中注册新 Activity

## 许可证

本项目遵循 AGPL-3.0 许可证，与 OpenList 保持一致。

## 问题反馈

如有问题，请提交 Issue 或联系开发者。

## 本地构建包含 zip-browser（/zb）的 APK

官方 `beta` Release 里的 Android 二进制是旧版本（不含 `/zb`），且 OpenList 仓库的
`Beta Release builds` 工作流最近的运行是失败的，release 资产没有刷新，所以直接跑
`download-binaries.sh` 拿到的仍是 5 月的旧二进制。要在本地打出带 `/zb` 的 APK，按下面做：

### 1. 交叉编译四个 ABI 的二进制

在 `C:\codes\go\OpenList`（已含 `server/zb` 与 `public/zb`）里，把产物直接写进 `jniLibs`
（该目录被 `.gitignore` 忽略，不会进仓库）：

```powershell
$env:GOROOT='C:\codes\_gosdk\go'; $env:PATH="$env:GOROOT\bin;"+$env:PATH
$env:GOTOOLCHAIN='local'; $env:GOSUMDB='off'; $env:CGO_ENABLED='0'
$env:GOOS='android'; $env:GOARCH='arm64'
go build -o C:\codes\go\OpenList-Android\app\src\main\jniLibs\arm64-v8a\libopenlist.so .

# arm / 386 / amd64 在 Go 里必须外部链接（cgo），用 NDK 的 clang 包装器：
$env:CGO_ENABLED='1'
$bin='C:\Android\Sdk\ndk\27.0.12077973\toolchains\llvm\prebuilt\windows-x86_64\bin'
$env:GOARCH='arm';   $env:GOARM='7'; $env:CC="$bin\armv7a-linux-androideabi24-clang.cmd"
go build -o ..\OpenList-Android\app\src\main\jniLibs\armeabi-v7a\libopenlist.so .
$env:GOARCH='386';   $env:CC="$bin\i686-linux-android24-clang.cmd"
go build -o ..\OpenList-Android\app\src\main\jniLibs\x86\libopenlist.so .
$env:GOARCH='amd64'; $env:CC="$bin\x86_64-linux-android24-clang.cmd"
go build -o ..\OpenList-Android\app\src\main\jniLibs\x86_64\libopenlist.so .
```

本机实测产物大小：arm64 202.0 MB、arm 198.9 MB、x86 208.3 MB、x86_64 209.3 MB
（内含 `public/zb` 前端与 `public/dist` 后台前端）。

### 2. 准备 SDK / JDK / Gradle

- JDK 17（AGP 不接受 JDK 25）：`C:\Android\jdk17\jdk-17.0.21+9`
- Android SDK：`C:\Android\Sdk`（platform-tools、platforms;android-35、build-tools;35.0.0、ndk;27.0.12077973）
- `local.properties` 写 `sdk.dir=C:\\Android\\Sdk`
- Gradle 8.11.1：`C:\Android\gradle-8.11.1`（本仓库没有 `gradle-wrapper.jar`，用这个）

```powershell
$env:JAVA_HOME='C:\Android\jdk17\jdk-17.0.21+9'
$env:ANDROID_HOME='C:\Android\Sdk'
$env:GRADLE_OPTS='-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=29758'
C:\Android\gradle-8.11.1\bin\gradle.bat --no-daemon assembleDebug
```

产物：`app\build\outputs\apk\debug\app-debug.apk`（本次实测 269.5 MB，四个 ABI 全带）。

### 3. 验证 APK 里确实有 /zb

```powershell
C:\Android\Sdk\build-tools\35.0.0\aapt2.exe dump badging app\build\outputs\apk\debug\app-debug.apk
# package versionCode=2 versionName=1.1.0
# native-code: 'arm64-v8a' 'armeabi-v7a' 'x86' 'x86_64'
```

二进制里可检出 `__ZB_BASE__`、`zb-token`、`@manage`、`RemoteStorage` 等字符串，说明 zb 前端、
内嵌后台前端与新驱动都在。

注意：`jniLibs` 被忽略，CI（`.github/workflows/build.yml`）仍然从 `beta` Release 下载二进制；
在 release 资产更新前推送 main 会让 CI 用旧二进制重打包，所以本次改动只在本地提交、未推送。

## 1.2.0：隐藏地址栏、直开 /zb、启动即自动登录

- 地址栏已移除，写入 URL 的入口不再提供；WebView 直接打开 `http://127.0.0.1:5244/zb/`，系统返回键交给 WebView 历史。
- App 启动时 `OpenListService` 会给服务进程加 `OPENLIST_ADMIN_PASSWORD=admin888`：这个 OpenList 分支在库里没有 admin 时才会用它建号（否则一直等 Web 安装向导，Android 上就没人建号，zb 也登不进去）。
- 主界面 `LaunchedEffect` 轮询 `/api/public/settings` 等服务就绪，再 `POST /zb/api/login`（admin/admin888）拿 30 天有效的 `zb_token`，写进 WebView Cookie 后 `reload()`，所以打开页面就是已登录状态。
- 若已有数据库里的 admin 密码不是 `admin888`（例如曾在浏览器向导里改过），更多菜单提供「重置管理员密码 (admin888)」：停服 → `openlist admin set admin888` → 起服。
- 版本：versionCode 4 / versionName 1.2.0；只改 Kotlin，jniLibs 无需重编。