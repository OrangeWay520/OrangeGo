# OrangeGO 局域网互传协议

OrangeGO 是一款跨平台局域网互传软件，提供 **Windows**（WPF / .NET 10）与 **Android**（Kotlin / Jetpack Compose）两端实现，并可与 [LocalSend](https://github.com/localsend/protocol) v2 协议互通。本文档定义两端通信所遵循的协议规范，**任一端的协议改动必须同步到另一端**，以保证互通。

- 协议版本：**v2**（向下兼容 v1）
- 默认端口：**53317**（UDP 发现 + TCP HTTP 同端口，便于防火墙一次性放行）
- 数据格式：JSON（UTF-8），LocalSend 兼容字段一律 **camelCase**
- 安全模型：**TLS + mTLS + 指纹钉扎 + 可选 PIN**（详见 [§5 安全模型](#五安全模型)）

---

## 一、端口分配

| 用途 | 协议 | 端口 | 说明 |
|------|------|------|------|
| OrangeGo 设备发现 | UDP | 53317 | 有限广播 `255.255.255.255:53317`，discoveryRequest/Response |
| LocalSend 组播存在通告 | UDP | 53317 | 组播组 `224.0.0.167:53317`，`MulticastMessageV2` |
| HTTP 传输（自研 + LocalSend v2 兼容） | TCP | 53317 | 监听 `0.0.0.0:53317`，TLS/mTLS |
| mDNS / DNS-SD | UDP | 5353 | 多播 `224.0.0.251:5353`，服务类型 `_orangego._tcp` |

> 同一 53317 端口同时承载 UDP 发现与 TCP HTTP 传输。OrangeGo 与 LocalSend 的 UDP 53317 必须复用同一 socket，不能再开一个绑定同端口，否则 `SocketException 10048`。

---

## 二、设备发现

发现走 **三通道并行**：mDNS/DNS-SD（标准跨端通道）+ OrangeGo UDP 广播（兜底）+ LocalSend 组播（互通兼容）。

### 2.1 mDNS / DNS-SD（OrangeGo 主通道）

- 服务类型：**`_orangego._tcp`**（多播 `224.0.0.251:5353`）
- 服务实例名：设备名
- SRV 记录：`port=53317`
- TXT 记录：

  | 字段 | 含义 |
  |------|------|
  | `id` | 设备 ID（`og_<8位随机字符串>`，全局唯一且稳定） |
  | `name` | 设备名 |
  | `v` | 协议版本（当前 `1`） |

- 三端均**广告**该服务并**浏览**同型服务，自动发现同网段设备
- 设备离线：收到 `ServiceLost` 即移除；同时用超时兜底（PC 端 OrangeGo 设备 45s、LocalSend 设备 60s；Android 端 15s TTL 未再见移除）

**地址约定**：mDNS 解析/广播来源地址仅接受**可路由的 IPv4**（排除 IPv6 链路本地 `fe80::`、回环与虚拟网卡地址），保证后续 HTTP 传输可达；取不到有效 IPv4 时跳过该通道，交由 UDP 通道兜底。

### 2.2 OrangeGo UDP 广播（兜底通道）

每台设备周期性（默认 5s）向 `255.255.255.255:53317` 发送通告，并监听同网段通告。

#### discoveryRequest（UDP）

```json
{
  "messageType": "discoveryRequest",
  "deviceId": "og_<8位随机>",
  "name": "设备名",
  "port": 53317,
  "version": "1.0"
}
```

#### discoveryResponse（UDP，单播回应）

收到请求的设备单播回应相同的设备信息，便于发现方确认端口可达：

```json
{
  "messageType": "discoveryResponse",
  "deviceId": "...",
  "name": "...",
  "port": 53317,
  "version": "1.0"
}
```

### 2.3 LocalSend 组播（互通兼容通道）

向组播组 `224.0.0.167:53317` 周期性广播 **`MulticastMessageV2`**（camelCase）：

```json
{
  "alias": "设备名",
  "version": "2.0",
  "deviceModel": "型号(可选)",
  "deviceType": "mobile|desktop",
  "fingerprint": "<证书DER的SHA-256大写十六进制>",
  "port": 53317,
  "protocol": "https",
  "download": false
}
```

> **关键流程**：LocalSend 设备收到广播后，会向对端 HTTP(S) 服务器 `POST /api/localsend/v2/register`，**只有正确应答才会被加入对端设备列表**。想"被发现"就必须实现 register 端点并正确回包。

### 2.4 设备去重

- 设备去重一律以 `deviceId` 为唯一键
  - OrangeGo 设备：`og_<8位随机>`
  - LocalSend 设备：`ogLS_<fingerprint>`（前缀区分）
- 同一设备的 mDNS / UDP / LocalSend 三通道结果合并为一条，禁止出现同名重复设备
- 同 IP 双协议通告时 OrangeGo 身份优先
- mDNS 广告必须携带 TXT `id=<deviceId>`，供对端跨通道去重（与 UDP 通告的 `deviceId` 一致）

---

## 三、设备身份字段

### 3.1 OrangeGo 设备身份

| 字段 | 类型 | 含义 |
|------|------|------|
| `messageType` | string | 报文类型 |
| `deviceId` | string | 全局唯一稳定 ID（`og_<8位随机>`） |
| `name` | string | 设备名 |
| `port` | int | HTTP 端口（53317） |
| `version` | string | 协议版本（"1.0"） |

### 3.2 LocalSend RegisterDtoV2（互通兼容，camelCase）

| 字段 | 类型 | 含义 |
|------|------|------|
| `alias` | string | 设备名 |
| `version` | string | 协议版本（"2.0"） |
| `deviceModel` | string? | 设备型号（可选） |
| `deviceType` | string | `mobile` / `desktop` / `headless` / `web`（小写） |
| `fingerprint` | string | 证书 DER 的 SHA-256 大写十六进制（无冒号） |
| `port` | int | HTTP(S) 端口 |
| `protocol` | string | `https` 或 `http` |
| `download` | bool | 是否启用 Web 下载页 |

> register 请求体、register 响应体、`GET /info` 响应体字段结构一致。

---

## 四、HTTP 传输端点

**方向约定**：发现方（得到对端 IP:port）作为 HTTP **客户端**；被发现的设备作为 HTTP **服务器**（监听 53317）。

所有请求/响应体均为 JSON（UTF-8），文件上传除外（二进制流）。OrangeGo 自研端点与 LocalSend v2 兼容端点共用同一 53317 TCP 端口。

### 4.1 OrangeGo 自研端点

#### 4.1.1 发送初始化 `POST /api/v1/send/init`

发送方在传文件前先打招呼，让接收方弹窗授权。请求体携带完整文件清单：

```json
{
  "messageType": "sendInit",
  "sendId": "uuid",
  "deviceId": "发送方deviceId",
  "name": "发送方设备名",
  "totalFiles": 3,
  "totalSize": 123456789,
  "files": {
    "fileId1": { "id": "fileId1", "fileName": "a.jpg", "size": 1024, "fileType": "image/jpeg", "sha256": "...", "relativePath": "sub/a.jpg", "thumb": "<Base64 JPEG>" },
    "fileId2": { "id": "fileId2", "fileName": "b.mp4", "size": 999999, "fileType": "video/mp4", "thumb": "<Base64 JPEG>" }
  }
}
```

响应：

| 状态码 | 响应体 | 含义 |
|--------|--------|------|
| 200 | `{"messageType":"ok","sendId":"uuid","sessionId":"...","files":{"fileId1":"token1","fileId2":"token2"}}` | 已授权，返回每文件的专属 token |
| 403 | `{"messageType":"reject","sendId":"uuid","info":"..."}` | 接收方拒绝 |
| 400 | `{"messageType":"error","info":"bad body"}` | 请求体解析失败 |
| 401 | `{"messageType":"error","info":"pin required"}` | 需要 PIN（用 `?pin=` 重试） |
| 409 | `{"messageType":"error","info":"busy"}` | 已有进行中会话（单会话锁） |
| 429 | `{"messageType":"error","info":"too many pin attempts"}` | PIN 试错过多 |

#### 4.1.2 传输文件 `POST /api/v1/send/file?sendId=<id>`

需要授权码才能访问，使用请求头传递：

| 请求头 | 含义 |
|--------|------|
| `Authorization: Bearer <token>` | 文件专属令牌（init 阶段获得） |
| `X-FileName: <URL编码的文件名>` | 文件名（OrangeGo 路径用） |
| `X-FileToken: <本次文件唯一标识>` | 文件 token |
| `X-FileSize: <字节数>` | 文件大小 |
| `Content-Range: bytes <start>-<end>/<total>` | 可选，断点续传 |
| `Content-Encoding: gzip` | 可选，gzip 压缩上传 |

请求体为文件二进制流（支持 chunked 传输）。

响应：

| 状态码 | 响应体 | 含义 |
|--------|--------|------|
| 200 | `{"messageType":"ok"}` | 接收成功 |
| 401 | `{"messageType":"expired"}` | 授权失效（需重新 init） |
| 413 | `{"messageType":"error","info":"no such file"}` | 文件未被登记 |

#### 4.1.3 取消 `POST /api/v1/send/cancel`

```json
{
  "messageType": "sendCancel",
  "sendId": "uuid"
}
```

响应 `200 OK` `{"messageType":"ok"}`。

#### 4.1.4 文本消息 `POST /api/v1/send/message`

传输一条即时文字（聊天气泡），**不落盘、不建会话**，无需 init/prepare/授权流程。

```json
{
  "messageType": "message",
  "sendId": "uuid",
  "deviceId": "sender_device_id",
  "name": "sender_name",
  "content": "要发送的文本内容",
  "timestamp": 1710000000000
}
```

响应：

| 状态码 | 响应体 | 含义 |
|--------|--------|------|
| 200 | `{"messageType":"ok"}` | 成功 |
| 400 | `{"messageType":"error","info":"bad body"}` | body 解析失败 |
| 400 | `{"messageType":"error","info":"empty content"}` | content 为空 |

> 接收端将其展示为聊天气泡而非文件记录。LocalSend 设备不支持此端点（详见 [§7 限制](#七已知限制与不适配)）。

#### 4.1.5 撤回 `POST /api/v1/send/recall`

发送方在 **2 分钟内** 撤回已成功发送的文本/文件消息。接收端按 `sendId` 匹配到对应"接收"记录并标记为已撤回（显示"对方撤回了一条消息"）。

```json
{
  "messageType": "recall",
  "sendId": "uuid"
}
```

响应 `200 OK` `{"messageType":"ok"}`。

#### 4.1.6 握手/元数据 `POST /api/v1/discover`（可选）

返回设备信息 JSON，用于 HTTP 层的端口可达性确认（与 UDP 响应同构）。

---

### 4.2 LocalSend v2 兼容端点

OrangeGo 同时实现 LocalSend v2 协议端点，使 LocalSend 客户端能直接发现并向 OrangeGo 推送文件。所有路径以 `/api/localsend/v2/` 为前缀（info 端点同时支持 v1）。

#### 4.2.1 设备信息 `GET /api/localsend/v1/info` / `GET /api/localsend/v2/info`

响应 `200 OK`：

```json
{
  "alias": "OrangeGo-PC",
  "version": "2.0",
  "deviceModel": "Windows",
  "deviceType": "desktop",
  "fingerprint": "<本机服务器证书DER的SHA-256大写十六进制>",
  "port": 53317,
  "protocol": "https",
  "download": false
}
```

#### 4.2.2 注册 `POST /api/localsend/v2/register`

LocalSend 设备收到 OrangeGo 的组播存在通告后，会向 OrangeGo POST register 把自己注册进来。OrangeGo 必须正确回包，对端才会把 OrangeGo 加入其设备列表。

请求体：`RegisterDtoV2`（同 [§3.2](#32-localsend-registerdtov2互通兼容camelcase)）

响应 `200 OK`：本机的 `RegisterDtoV2`（同 info 响应）。

#### 4.2.3 准备上传 `POST /api/localsend/v2/prepare-upload`

LocalSend 客户端在传文件前先打招呼，让接收方弹窗授权。请求体携带完整文件清单（**files 是 Map 不是数组**）：

```json
{
  "info": {
    "alias": "iPhone",
    "version": "2.0",
    "deviceModel": "iPhone 15",
    "deviceType": "mobile",
    "fingerprint": "...",
    "port": 53317,
    "protocol": "https",
    "download": false
  },
  "files": {
    "fileId1": { "id": "fileId1", "fileName": "a.jpg", "size": 1024, "fileType": "image/jpeg", "hash": "...", "preview": "<Base64>", "metadata": {} },
    "fileId2": { "id": "fileId2", "fileName": "b.mp4", "size": 999999, "fileType": "video/mp4" }
  }
}
```

响应：

| 状态码 | 响应体 | 含义 |
|--------|--------|------|
| 200 | `{"sessionId":"...","files":{"fileId1":"token1","fileId2":"token2"}}` | 已授权，返回每文件的专属 token |
| 204 | — | 无需传输 |
| 401 | `{"error":"pin required"}` | 需要 PIN（用 `?pin=` 重试） |
| 403 | `{"error":"rejected"}` | 接收方拒绝 |
| 409 | `{"error":"busy"}` | 已有进行中会话（单会话锁） |
| 429 | `{"error":"too many pin attempts"}` | PIN 试错过多 |

> OrangeGo 自研 v2 协议也走此路径（携带 files 清单含 thumb/relativePath/sha256），与 LocalSend 报文通过 `info` 字段是否存在 + `files` 是否为 Map 来区分。

#### 4.2.4 上传 `POST /api/localsend/v2/upload?sessionId=<id>&fileId=<id>&token=<token>`

文件二进制流 body（LocalSend 客户端用 **chunked 流式，无 Content-Length**）。

| 请求头 | 含义 |
|--------|------|
| `X-FileName: <URL编码的文件名>` | 可选；LocalSend 不发，靠 `fileId` 从 prepare 清单映射 |
| `X-FileSize: <字节数>` | 可选 |
| `Content-Range: bytes <start>-<end>/<total>` | 可选，断点续传 |
| `Content-Encoding: gzip` | 可选，gzip 压缩上传 |

响应：

| 状态码 | 响应体 | 含义 |
|--------|--------|------|
| 200 | `{"messageType":"ok"}` | 接收成功 |
| 401 | `{"messageType":"expired"}` | 授权失效 |
| 403 | `{"messageType":"error","info":"token mismatch"}` | token 无效 |
| 413 | `{"messageType":"error","info":"no file name"}` | 文件名缺失 |

> **chunked 路径也必须触发 `OnFileStarted/OnFileProgress/OnFileReceived` 回调**（期望大小取清单中该文件 size），否则多文件无法合并成集成气泡。

#### 4.2.5 断点续传查询 `HEAD /api/localsend/v2/upload?sessionId=<id>&fileId=<id>`

响应 `200 OK` + 头 `X-Received-Bytes: <已接收字节数>`，客户端据此决定是否带 `Content-Range` 续传。

#### 4.2.6 取消 `POST /api/localsend/v2/cancel?sessionId=<id>`

响应 `200 OK` `{"messageType":"ok"}`，清理会话。

> OrangeGo 的 cancel 端点同时兼容 query `sessionId`（LocalSend 风格）与 body `sendId`（OrangeGo 风格）。

---

### 4.3 错误码汇总

| 状态码 | 含义 |
|--------|------|
| 200 | OK |
| 204 | No Content（无需传输） |
| 400 | Bad Request（body 解析失败 / content 为空） |
| 401 | Unauthorized（token expired / PIN required） |
| 403 | Forbidden（reject / token mismatch） |
| 404 | Not Found |
| 405 | Method Not Allowed |
| 409 | Conflict（已有进行中会话，单会话锁） |
| 413 | Payload Too Large（no such file / no file name） |
| 429 | Too Many Requests（PIN 试错过多） |
| 500 | Internal Server Error |

---

## 五、安全模型

### 5.1 TLS / mTLS

- **所有连接走 HTTPS**（自签证书），无明文 HTTP 服务
- **mTLS（双向 TLS）**：服务端 `needClientAuth=true`，要求客户端证书；信任所有自签客户端证书（仅校验自洽签名 + 有效期，不校验签发链）
- PC 端：`SslStream.AuthenticateAsServerAsync` + `RemoteCertificateValidationCallback`
- Android 端：`SSLServerSocket` + `TrustAllManager`

### 5.2 自签证书

| 角色 | PC 端 | Android 端 |
|------|-------|------------|
| 服务器证书 | ECDSA P-256 自签（CN=OrangeGO, serverAuth EKU），持久化 `%AppData%\OrangeGO\server.pfx` | RSA-2048 自签（CN=LocalSend User, clientAuth+serverAuth EKU），持久化应用私有目录 `ls_identity.p12` |
| 客户端证书 | RSA-2048 自签（CN=LocalSend User, clientAuth EKU），持久化 `%AppData%\OrangeGO\client.pfx` | 复用同一证书 |

> **PC 端客户端证书必须经 PFX 导出再导入（私钥附着、Exportable）**。原因：Windows SChannel 无法把"瞬时 CNG 密钥"的证书当 mTLS 客户端凭证，握手会报 `SEC_E_UNKNOWN_CREDENTIALS`。

### 5.3 指纹钉扎

`fingerprint` = 证书 DER 的 **SHA-256，大写十六进制**（无冒号）。

- **TOFU（Trust On First Use，OrangeGo 对 OrangeGo）**：首次连接信任并存储服务端证书 SHA-256 到 FingerprintStore（按 IP），后续校验一致性，不匹配拒绝（防 MITM）
  - PC 端：`fingerprints.json`
  - Android 端：SharedPreferences `ogo_fingerprints`
- **按通告钉扎（对 LocalSend）**：按对端组播/register 通告的 `fingerprint` 校验服务端证书 DER SHA-256，不匹配抛 `CertificateException`

### 5.4 PIN 密码

- 接收方开启 PIN（设置 `RequirePin=true` + `PinCode`，4-6 位数字，空 PIN 视为未启用）
- prepare-upload 回 `401` → 发送方弹窗输入 PIN → 携带 `?pin=<URL编码>` 重试
- 最多重试 3 次；`429` 表示试错过多

### 5.5 路径穿越防护

文件名与相对路径逐段净化（`SafeFileName` / `ResolveSafeRelative` / `sanitizeFileName`），同名文件追加 `(1)/(2)` 序号避免覆盖。

### 5.6 防火墙放行

PC 端首次启动时通过 `netsh advfirewall firewall add rule` 添加 TCP 53317 入站规则（profile=private），需 UAC 提权一次。

---

## 六、文件元数据与缩略图

### 6.1 FileMeta 字段

| 字段 | 类型 | 必填 | 含义 |
|------|------|------|------|
| `id` | string | 是 | 文件唯一标识（fileId） |
| `fileName` | string | 是 | 文件名 |
| `size` | long | 是 | 字节数 |
| `fileType` | string | 是 | MIME 类型（默认 `application/octet-stream`） |
| `sha256` | string | 否 | 文件内容 SHA-256 |
| `relativePath` | string | 否 | 相对发送根目录的路径（`/` 分隔，含文件名），用于文件夹发送重建子目录 |
| `thumb` | string | 否 | 缩略图 Base64（JPEG，质量 60，256px） |
| `hash` | string | 否 | LocalSend 风格的文件 hash |
| `preview` | string | 否 | LocalSend 风格的预览图 Base64 |
| `metadata` | object | 否 | LocalSend 风格的元数据 |

### 6.2 缩略图传输方案

- **发送端生成**：发送端在 prepare 阶段生成缩略图，随 init/prepare-upload 的 files 清单一起发给接收端
- 字段名：`thumb`（OrangeGo 风格）/ `preview`（LocalSend 风格），Base64 编码的 JPEG
- 仅图片/视频生成缩略图（256px，JpegBitmapEncoder 质量 60）
- DNG/RAW 无 RAW 编解码器时退化提取内嵌 JPEG 预览（扫描 `FF D8 … FF D9` 最大 JPEG 段）
- 接收端 `OnFileStarted` 回调携带 thumb，UI 直接展示，无需接收端自行生成或额外请求
- 落盘后另有 ThumbCache 磁盘缓存（key=`sha256(name|size|mtime)`，与源路径解耦，源文件删除/移动后仍可命中）

### 6.3 文件夹发送

通过 `relativePath` 字段传递相对发送根目录的路径（`/` 分隔，含文件名），接收端安全重建子目录结构（防路径穿越）。

### 6.4 断点续传

- 客户端 `HEAD /api/localsend/v2/upload?sessionId=&fileId=` 查询已接收偏移
- 服务端响应头 `X-Received-Bytes: <字节数>`
- 客户端据此决定是否带 `Content-Range: bytes <start>-<end>/<total>` 续传
- 半文件保留，未完成会话可继续

### 6.5 gzip 压缩上传

文本/代码/文档类可压缩类型，非续传时客户端可携带 `Content-Encoding: gzip` 压缩上传，服务端自动解压。

---

## 七、已知限制与不适配

| 限制 | 说明 |
|------|------|
| LocalSend 设备不支持文本消息 | OrangeGo 的 `sendTextTo` 在对 LocalSend 设备调用时，应提示"不支持文字"而非尝试发送 |
| LocalSend 设备不支持撤回 | 撤回仅适用于 OrangeGo 对 OrangeGo |
| LocalSend 上传不带 `X-FileName` 头 | OrangeGo 接收侧不能依赖该头确定文件名，须先解析 session 再据此决定文件名 |
| 单会话锁 | 同一时刻只允许一个进行中的接收会话（409 语义），会话要主动 cancel 或完成时释放 |
| 会话 TTL | 最长存活 15 分钟（SESSION_TTL），超时未完成即回收避免字典无限增长 |
| mDNS 仅接受可路由 IPv4 | 排除 IPv6 链路本地/回环/虚拟网卡，取不到时跳过交由 UDP 兜底 |
| 缩略图随 init 下发增加体积 | Base64 JPEG 会增大 init 请求体 |

---

## 八、典型时序

### 8.1 OrangeGo → OrangeGo 文件传输

```
A (发送方)                        B (接收方)
  |                                  |
  |  UDP discoveryRequest            |
  |--------------------------------->|
  |  UDP discoveryResponse           |
  |<---------------------------------|
  |                                  |
  |  POST /api/v1/send/init          |
  |  {sendId, files:{...}}           |
  |--------------------------------->|
  |  200 {sessionId, files:{token}}  |
  |<---------------------------------|
  |                                  |
  |  POST /api/v1/send/file          |
  |  Header: Authorization, X-FileName|
  |  Body: <binary>                  |
  |--------------------------------->|
  |  200 {messageType:"ok"}          |
  |<---------------------------------|
```

### 8.2 LocalSend → OrangeGo 文件传输

```
LocalSend                         OrangeGo
  |                                  |
  |  UDP multicast presence          |
  |--------------------------------->|
  |  POST /api/localsend/v2/register |
  |  RegisterDtoV2                   |
  |--------------------------------->|
  |  200 RegisterDtoV2               |
  |<---------------------------------|
  |                                  |
  |  POST /api/localsend/v2/prepare-upload
  |  {info, files:{...}}             |
  |--------------------------------->|
  |  200 {sessionId, files:{token}}  |
  |<---------------------------------|
  |                                  |
  |  POST /api/localsend/v2/upload   |
  |  ?sessionId=&fileId=&token=      |
  |  Body: <chunked binary>          |
  |--------------------------------->|
  |  200 {messageType:"ok"}          |
  |<---------------------------------|
```

### 8.3 PIN 流程

```
发送方                            接收方
  |  POST .../prepare-upload        |
  |--------------------------------->|
  |  401 {error:"pin required"}     |
  |<---------------------------------|
  |  弹窗输入 PIN                   |
  |  POST .../prepare-upload?pin=xx |
  |--------------------------------->|
  |  200 {sessionId, files:{token}} |
  |<---------------------------------|
```

---

## 九、版本演进

- **v1**：明文 HTTP，无 TLS（已废弃，仅保留向后兼容的端点路径）
- **v2**（当前）：全量 TLS + mTLS + 指纹钉扎 + 可选 PIN，与 LocalSend v2 互通
- 协议层预留 `sendId` + `token` 扩展点，后续可叠加正式证书链与设备身份签名

---

## 十、参考实现

| 端 | 关键文件 |
|----|----------|
| PC 端协议核心 | `windows/OrangeGO.Windows/Core/Protocol.cs`、`ReceiveServer.cs`、`SenderClient.cs`、`LocalSendCompat.cs` |
| PC 端发现 | `windows/OrangeGO.Windows/Core/DeviceDiscovery.cs`、`MdnsDiscovery.cs` |
| PC 端安全 | `windows/OrangeGO.Windows/Core/FingerprintStore.cs`、`FirewallHelper.cs` |
| Android 协议核心 | `android/app/src/main/java/com/orangeway/go/core/Protocol.kt`、`ReceiveServer.kt`、`SenderClient.kt`、`LsSender.kt`、`LsCert.kt` |
| Android 发现 | `android/app/src/main/java/com/orangeway/go/core/DiscoveryManager.kt` |
| LocalSend 官方协议 | <https://github.com/localsend/protocol>（`README.md` + `v1.md`） |
| 适配技术文档 | `OrangeGo-LocalSend适配技术文档.md`（仓库根目录上一级） |
