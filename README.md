<div align="center">

# OrangeGO

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="android/app/src/main/res/raw/og_logo_white.svg">
  <img alt="OrangeGO" src="android/app/src/main/res/raw/og_logo_orange.svg" width="220">
</picture>

**跨平台局域网互传 · Windows ↔ Android ↔ LocalSend**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-green.svg)]()
[![Protocol](https://img.shields.io/badge/Protocol-v2-orange.svg)](PROTOCOL.md)
[![LocalSend Compatible](https://img.shields.io/badge/LocalSend-v2%20Compatible-teal.svg)](https://github.com/localsend/protocol)

**中文** · [English](./README.en.md)

</div>

---

## 简介

OrangeGO 是一款**局域网内跨平台互传软件**，提供 Windows（WPF / .NET 10）与 Android（Kotlin / Jetpack Compose）两端原生实现，并完整兼容 [LocalSend](https://github.com/localsend/protocol) v2 协议——你可以用 OrangeGo 直接与 iOS / Android 上的 LocalSend App 互传文件，无需任何中间服务、无需联网、无需账号。

- **零配置**：同局域网自动发现，打开即用
- **跨平台**：Windows PC ↔ Android 手机 ↔ LocalSend 设备三方互通
- **端到端安全**：TLS + mTLS 双向证书 + SHA-256 指纹钉扎 + 可选 PIN
- **丰富媒体预览**：图片 / 视频 / DNG RAW / 音频 / Office 文档 / Shell 缩略图
- **多语言**：PC 端 15 种、Android 端 11 种（含跟随系统）
- **完全开源**：GPL-3.0

---

## 功能特性

### 设备发现与连接
- **三通道并行发现**：mDNS/DNS-SD（`_orangego._tcp`）+ UDP 53317 有限广播 + LocalSend `224.0.0.167` 组播
- **LocalSend 互通**：实现 `register` / `info` / `prepare-upload` / `upload` / `cancel` 全套 v2 端点
- **设备去重**：以 `deviceId` 为唯一键，三通道结果合并为一条
- **离线判定**：超时兜底（PC 端 45s/60s，Android 端 15s TTL）+ mDNS ServiceLost 即时移除

### 文件传输
- **多文件批量**：一次会话传多文件，每文件独立 token
- **文件夹发送**：保留相对路径结构，接收端安全重建子目录（防路径穿越）
- **实时进度**：开始 / 进度 / 完成回调链，EMA 平滑剩余时间
- **断点续传**：HEAD 查询已接收偏移，Content-Range 续传
- **gzip 压缩上传**：文本/代码/文档类可压缩类型自动压缩
- **chunked 流式兼容**：支持 LocalSend 客户端的 chunked 传输
- **取消**：随时取消，单会话锁语义（409 = 忙）
- **接收授权**：弹窗二次确认，不自动接受
- **同名文件**：自动追加 `(1)/(2)` 序号避免覆盖

### 文本消息
- **聊天气泡**：即时文字传输，不落盘、不建会话
- **撤回**：2 分钟内撤回，按 `sendId` 匹配标记"对方撤回了一条消息"
- **自动接收**：可选开关

### 媒体预览
- **图片**：png / jpg / jpeg / bmp / gif / ico / webp / heic / heif / dng
- **视频**：PC 端 LibVLCSharp（支持 MOV seek）/ Android 端 VideoView，缩略图叠加播放键标志
- **DNG/RAW**：退化提取内嵌 JPEG 预览（扫描 `FF D8…FF D9` 最大 JPEG 段）
- **音频**：mp3 / wav / ogg / aac / flac / m4a / wma / opus / amr
- **Office 文档**：Word / Excel / PowerPoint 品牌图标
- **其他类型**：Shell 缩略图 + 类型图标
- **缩略图磁盘缓存**：key=`sha256(name|size|mtime)`，与源路径解耦，源文件移动后仍可命中
- **缩略图随协议下发**：发送端 prepare 阶段生成，随 init 一起发给接收端，立即展示

### 收藏夹与白名单
- **设备收藏**：按设备 ID 收藏，与 IP 无关
- **自动保存白名单**：仅自动保存来自收藏夹设备的文件（默认开启）

### 设置
- 设备别名 / 收件目录（默认 `Downloads/OrangeGo`）
- 主题：跟随系统 / 浅色 / 深色
- 语言：跟随系统 / 具体语言
- 开机自启 / 最小化到托盘 / 界面动画
- 自动保存接收 / 自动保存白名单
- PIN 密码（4-6 位数字）
- 保存到历史记录 / 传输记录自动清理（N 天/月/年前）
- 图片集成显示（多图合并到一个气泡）
- 截图时保留本地窗口（PC 端）
- 保存到相册（Android 端）
- 缩略图缓存查看与清除 / 清空传输记录
- 检查更新 / 问题反馈（Android 端）

### UI 特性
- **圆形进度环**：11×11 视图框，顺时针圆弧，与完成态勾勾圆同径
- **窗口动画**：DWM 过渡动画
- **设备卡片**：首字母头像、在线状态点、收藏星标、LocalSend 徽章
- **启动画面**：Android 端 SplashActivity，深浅色适配
- **自绘标题栏**：WindowChrome，CaptionHeight=0
- **回顶悬浮键**：Android 端长列表快速回顶
- **集成网格 / 独立气泡**：两种多文件展示模式
- **EMA 平滑剩余时间**：避免末位数字频繁跳动

### 安全
- **TLS + mTLS**：全量双向证书，自签证书（PC 端 ECDSA P-256 服务器 + RSA-2048 客户端；Android 端 RSA-2048 单证书）
- **指纹钉扎**：SHA-256 指纹，TOFU（OrangeGo 之间）或按通告钉扎（对 LocalSend）
- **PIN**：对端开启 PIN 时 401 → 弹窗输入 → `?pin=` 重试，最多 3 次
- **路径穿越防护**：文件名 / 相对路径逐段净化
- **防火墙放行**：PC 端首次启动自动添加 TCP 53317 入站规则（需 UAC 提权一次）

### 多语言支持
- **PC 端**：15 种语言（ar, bn, de, en, es, fr, hi, id, it, ja, ko, pt, ru, tr, zh）+ 跟随系统
- **Android 端**：11 种（system, de, en, es, fr, ja, ko, pt, ru, zh, zhTW）

---

## 平台支持

| 平台 | 技术 | 最低版本 | 构建产物 |
|------|------|----------|----------|
| Windows | WPF / .NET 10 | Windows 10 1809+ | `OrangeGo.exe` |
| Android | Kotlin / Jetpack Compose | Android 8.0（API 26） | `app-debug.apk` / `app-release.apk` |

> 目标 SDK：Android 15（API 37） · compileSdk 37 · Kotlin 2.2.10 · AGP 9.3.2

---

## 下载与安装

### Windows
1. 从 [Releases](../../releases) 下载 `OrangeGo-windows-x64.zip`
2. 解压到任意目录，运行 `OrangeGo.exe`
3. 首次启动会请求 UAC 提权以添加防火墙规则（仅一次）

### Android
1. 从 [Releases](../../releases) 下载 `OrangeGo-android.apk`
2. 在手机上安装（需开启"允许安装未知来源应用"）
3. 首次启动需在系统设置手动开放一次"所有文件访问"权限（用于接收文件落盘）

---

## 快速开始

1. 将 OrangeGo 设备连接到**同一局域网**（Wi-Fi 或有线）
2. 打开 OrangeGo（PC 端或 Android 端）
3. 设备列表会自动显示同网段的其他 OrangeGo 与 LocalSend 设备
4. 点击目标设备 → 选择文件 / 文件夹 / 文本 → 发送
5. 接收方弹窗确认 → 文件落盘到 `Downloads/OrangeGo`（默认）

> 与 LocalSend 互通：直接在 LocalSend App 中选择 OrangeGo 设备发送即可，反之亦然。

---

## 从源码构建

### 前置要求
- Windows 10/11（构建 PC 端必需）
- .NET 10 SDK
- Android Studio（或 Android SDK + JDK 17）
- Git

### 构建 PC 端
```powershell
cd windows\OrangeGO.Windows
dotnet build -c Release
# 产物：bin\Release\net10.0-windows\OrangeGo.exe
```

> 若 `OrangeGo.exe` 正在运行，需先 `taskkill /IM OrangeGo.exe /F` 再构建（文件占用）。

### 构建 Android 端
```powershell
cd android
.\gradlew.bat assembleDebug
# 产物：app\build\outputs\apk\debug\app-debug.apk
# 安装到设备：
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 运行自测（PC 端环回协议测试）
```powershell
dotnet run --project windows\OrangeGO.Selftest
```

---

## 项目结构

```
OrangeGo/
├── windows/                          # PC 端
│   ├── OrangeGO.Windows/             # 主 WPF 项目
│   │   ├── Core/                     # 协议/发现/传输核心
│   │   ├── Localization/             # 15 份语言 xaml
│   │   ├── Theme/                    # 深浅色主题
│   │   ├── Controls/                 # 样式
│   │   ├── Assets/                   # 图标/Logo/Emoji/Office
│   │   └── *.xaml                    # 主窗口/设置/预览/截图
│   └── OrangeGO.Selftest/            # 控制台自测
├── android/                          # Android 端
│   └── app/src/main/
│       ├── java/com/orangeway/go/
│       │   ├── core/                 # 协议/发现/传输/证书
│       │   ├── data/                 # 设置持久化
│       │   └── ui/                   # Compose UI
│       └── res/                      # drawable/mipmap/values/raw
├── deploy/                           # 问题反馈 Cloudflare Worker
├── PROTOCOL.md                       # 协议规范
├── LICENSE                           # GPL-3.0
└── README.md                         # 本文件
```

---

## 协议文档

完整的协议规范见 [PROTOCOL.md](PROTOCOL.md)，包括：
- 端口分配与设备发现（mDNS / UDP / LocalSend 组播）
- 全部 HTTP 端点（OrangeGo 自研 + LocalSend v2 兼容）
- 设备身份字段与文件元数据
- 安全模型（TLS / mTLS / 指纹钉扎 / PIN）
- 错误码、时序图、已知限制

---

## 已知限制

- **LocalSend 设备不支持文本消息与撤回**：仅兼容文件互传
- **Web 端未实现**：当前仅 Windows + Android 两端
- **PC 端防火墙放行需 UAC 提权一次**
- **Android 端全盘文件访问需用户在系统设置手动开放一次**
- **Android 端 release 构建当前用 debug 签名**（正式发布前需配 release 签名）

---

## 贡献

欢迎 Issue / PR。提交前请确保：
1. 协议改动同步更新 [PROTOCOL.md](PROTOCOL.md)
2. 新增本地化文案补齐 PC 端 15 份 `Localization/*.xaml` + Android 端 `tr(...)` 调用
3. 通过 `dotnet run --project windows\OrangeGO.Selftest` 自测
4. 不引入恶意代码、不泄露密钥/证书

---

## 致谢

- [LocalSend](https://github.com/localsend/protocol) — 协议互通兼容的参考实现
- [LibVLCSharp](https://github.com/videolan/libvlcsharp) — 跨平台多媒体播放
- [Coil](https://github.com/coil-kt/coil) — Android 图片加载
- [Haukcode.Mdns](https://github.com/haukcode/Haukcode.Mdns) — .NET mDNS 实现
- [BouncyCastle](https://www.bouncycastle.org/) — Java 密码学库

---

## 许可证

[GPL-3.0](LICENSE) © OrangeWay