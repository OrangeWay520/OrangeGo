<div align="center">

# OrangeGO

<picture>
  <source media="(prefers-color-scheme: dark)" srcset="android/app/src/main/res/raw/og_logo_white.svg">
  <img alt="OrangeGO" src="android/app/src/main/res/raw/og_logo_orange.svg" width="220">
</picture>

**Cross-platform LAN transfer · Windows ↔ Android ↔ LocalSend**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Android-green.svg)]()
[![Protocol](https://img.shields.io/badge/Protocol-v2-orange.svg)](PROTOCOL.md)
[![LocalSend Compatible](https://img.shields.io/badge/LocalSend-v2%20Compatible-teal.svg)](https://github.com/localsend/protocol)

[中文](./README.md) · **English**

</div>

---

## Introduction

OrangeGO is a **cross-platform LAN transfer app** with native implementations for Windows (WPF / .NET 10) and Android (Kotlin / Jetpack Compose). It is fully compatible with the [LocalSend](https://github.com/localsend/protocol) v2 protocol — you can transfer files directly between OrangeGo and the LocalSend App on iOS / Android, with no intermediate server, no internet, and no account required.

- **Zero configuration** — auto-discovery on the same LAN, just open and use
- **Cross-platform** — Windows PC ↔ Android phone ↔ LocalSend devices, three-way interop
- **End-to-end security** — TLS + mTLS mutual certificates + SHA-256 fingerprint pinning + optional PIN
- **Rich media preview** — images / videos / DNG RAW / audio / Office documents / Shell thumbnails
- **Multilingual** — 15 languages on PC, 11 on Android (including follow-system)
- **Fully open source** — GPL-3.0

---

## Features

### Device discovery & connection
- **Three parallel discovery channels**: mDNS/DNS-SD (`_orangego._tcp`) + UDP 53317 limited broadcast + LocalSend `224.0.0.167` multicast
- **LocalSend interop**: full v2 endpoint suite — `register` / `info` / `prepare-upload` / `upload` / `cancel`
- **Device dedup**: unique key by `deviceId`, three-channel results merged into one entry
- **Offline detection**: timeout fallback (45s/60s on PC, 15s TTL on Android) + instant removal on mDNS ServiceLost

### File transfer
- **Multi-file batch**: one session, many files, each with its own token
- **Folder send**: preserves relative path structure, safely rebuilt on receiver (path-traversal safe)
- **Real-time progress**: start / progress / complete callback chain, EMA-smoothed remaining time
- **Resumable upload**: HEAD queries received offset, Content-Range resumes
- **gzip upload**: auto-compresses compressible types (text/code/docs)
- **Chunked streaming**: supports LocalSend client's chunked transfer
- **Cancel**: cancel anytime, single-session lock semantics (409 = busy)
- **Receive authorization**: popup confirmation, never auto-accept
- **Name collision**: auto-appends `(1)/(2)` suffix to avoid overwrite

### Text messaging
- **Chat bubbles**: instant text, not persisted, no session created
- **Recall**: recall within 2 minutes, matched by `sendId`, shown as "message recalled"
- **Auto-accept**: optional toggle

### Media preview
- **Images**: png / jpg / jpeg / bmp / gif / ico / webp / heic / heif / dng
- **Video**: LibVLCSharp on PC (MOV seek supported) / VideoView on Android, thumbnail with play icon overlay
- **DNG/RAW**: fallback extracts embedded JPEG preview (scans `FF D8…FF D9` largest JPEG segment)
- **Audio**: mp3 / wav / ogg / aac / flac / m4a / wma / opus / amr
- **Office documents**: Word / Excel / PowerPoint brand icons
- **Other types**: Shell thumbnails + type icons
- **Thumbnail disk cache**: key=`sha256(name|size|mtime)`, decoupled from source path, survives move/delete
- **Thumbnails sent over protocol**: generated on sender during prepare, sent with init, shown immediately on receiver

### Favorites & whitelist
- **Device favorites**: by device ID, independent of IP
- **Auto-save whitelist**: only auto-saves files from favorited devices (enabled by default)

### Settings
- Device alias / save directory (default `Downloads/OrangeGo`)
- Theme: follow system / light / dark
- Language: follow system / specific language
- Autostart / minimize to tray / UI animations
- Auto-save received / auto-save whitelist
- PIN password (4-6 digits)
- Save to history / auto-cleanup transfer records (N days/months/years ago)
- Image integration display (multi-image merged into one bubble)
- Keep window during screenshot (PC)
- Save to gallery (Android)
- Thumbnail cache view & clear / clear transfer records
- Check for updates / send feedback (Android)

### UI highlights
- **Circular progress ring**: 11×11 view box, clockwise arc, same diameter as the completion checkmark circle
- **Window animations**: DWM transition animations
- **Device cards**: initial-letter avatar, online status dot, favorite star, LocalSend badge
- **Splash screen**: Android SplashActivity, light/dark adapted
- **Custom title bar**: WindowChrome, CaptionHeight=0
- **Scroll-to-top FAB**: Android long-list quick return
- **Integrated grid / standalone bubbles**: two multi-file display modes
- **EMA-smoothed remaining time**: avoids last-digit jitter

### Security
- **TLS + mTLS**: full mutual certificates, self-signed (PC: ECDSA P-256 server + RSA-2048 client; Android: single RSA-2048)
- **Fingerprint pinning**: SHA-256 fingerprint, TOFU (between OrangeGo) or pin-by-announcement (to LocalSend)
- **PIN**: when peer enables PIN → 401 → popup input → retry with `?pin=`, up to 3 attempts
- **Path-traversal protection**: filename / relative path sanitized segment by segment
- **Firewall**: PC adds TCP 53317 inbound rule on first start (UAC elevation once)

### Multilingual support
- **PC**: 15 languages (ar, bn, de, en, es, fr, hi, id, it, ja, ko, pt, ru, tr, zh) + follow system
- **Android**: 11 (system, de, en, es, fr, ja, ko, pt, ru, zh, zhTW)

---

## Platform support

| Platform | Tech | Minimum | Build output |
|----------|------|---------|--------------|
| Windows | WPF / .NET 10 | Windows 10 1809+ | `OrangeGo.exe` |
| Android | Kotlin / Jetpack Compose | Android 8.0 (API 26) | `app-debug.apk` / `app-release.apk` |

> Target SDK: Android 15 (API 37) · compileSdk 37 · Kotlin 2.2.10 · AGP 9.3.2

---

## Download & install

### Windows
1. Download `OrangeGo-windows-x64.zip` from [Releases](../../releases)
2. Extract to any folder and run `OrangeGo.exe`
3. First launch requests UAC elevation to add a firewall rule (once only)

### Android
1. Download `OrangeGo-android.apk` from [Releases](../../releases)
2. Install on phone (enable "allow unknown sources" first)
3. First launch asks you to grant "all files access" once in system settings (for receiving files to disk)

---

## Quick start

1. Connect OrangeGo devices to the **same LAN** (Wi-Fi or wired)
2. Open OrangeGo (PC or Android)
3. The device list auto-shows other OrangeGo and LocalSend devices on the same network
4. Click a target device → choose files / folder / text → send
5. Receiver confirms in popup → file saved to `Downloads/OrangeGo` (default)

> LocalSend interop: just pick an OrangeGo device in the LocalSend app and send, or vice versa.

---

## Build from source

### Prerequisites
- Windows 10/11 (required to build the PC client)
- .NET 10 SDK
- Android Studio (or Android SDK + JDK 17)
- Git

### Build PC client
```powershell
cd windows\OrangeGO.Windows
dotnet build -c Release
# Output: bin\Release\net10.0-windows\OrangeGo.exe
```

> If `OrangeGo.exe` is running, run `taskkill /IM OrangeGo.exe /F` before building (file lock).

### Build Android client
```powershell
cd android
.\gradlew.bat assembleDebug
# Output: app\build\outputs\apk\debug\app-debug.apk
# Install to device:
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Run self-test (PC loopback protocol test)
```powershell
dotnet run --project windows\OrangeGO.Selftest
```

---

## Project structure

```
OrangeGo/
├── windows/                          # PC client
│   ├── OrangeGO.Windows/             # main WPF project
│   │   ├── Core/                     # protocol/discovery/transfer core
│   │   ├── Localization/             # 15 language xaml files
│   │   ├── Theme/                    # light/dark themes
│   │   ├── Controls/                 # styles
│   │   ├── Assets/                   # icons/logo/emoji/office
│   │   └── *.xaml                    # main/settings/preview/screenshot windows
│   └── OrangeGO.Selftest/            # console self-test
├── android/                          # Android client
│   └── app/src/main/
│       ├── java/com/orangeway/go/
│       │   ├── core/                 # protocol/discovery/transfer/certs
│       │   ├── data/                 # settings persistence
│       │   └── ui/                   # Compose UI
│       └── res/                      # drawable/mipmap/values/raw
├── deploy/                           # feedback Cloudflare Worker
├── PROTOCOL.md                       # protocol spec
├── LICENSE                           # GPL-3.0
└── README.md                         # this file
```

---

## Protocol documentation

Full protocol spec in [PROTOCOL.md](PROTOCOL.md), including:
- Port allocation & device discovery (mDNS / UDP / LocalSend multicast)
- All HTTP endpoints (OrangeGo native + LocalSend v2 compat)
- Device identity fields & file metadata
- Security model (TLS / mTLS / fingerprint pinning / PIN)
- Error codes, sequence diagrams, known limitations

---

## Known limitations

- **LocalSend devices don't support text messages or recall**: only file transfer is compatible
- **No web client**: currently Windows + Android only
- **PC firewall rule requires UAC elevation once**
- **Android all-files access must be granted manually in system settings once**
- **Android release build currently uses debug signing** (configure release signing before production release)

---

## Contributing

Issues / PRs welcome. Before submitting, please ensure:
1. Protocol changes update [PROTOCOL.md](PROTOCOL.md)
2. New localized strings are added to all 15 PC `Localization/*.xaml` files + Android `tr(...)` calls
3. `dotnet run --project windows\OrangeGO.Selftest` passes
4. No malicious code, no leaked keys/certificates

---

## Acknowledgements

- [LocalSend](https://github.com/localsend/protocol) — reference implementation for protocol interop
- [LibVLCSharp](https://github.com/videolan/libvlcsharp) — cross-platform multimedia playback
- [Coil](https://github.com/coil-kt/coil) — Android image loading
- [Haukcode.Mdns](https://github.com/haukcode/Haukcode.Mdns) — .NET mDNS implementation
- [BouncyCastle](https://www.bouncycastle.org/) — Java cryptography library

---

## License

[GPL-3.0](LICENSE) © OrangeWay