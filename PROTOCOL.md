# OrangeGO 局域网互传协议

三端（Windows / Android / Web）统一遵循的通信协议。任何一端的改动必须同步到其余端，保证互通。

## 端口

- 发现（广播/监听）：**UDP 53317**

- 传输（HTTP）：**TCP 53317**

同一端口同时承载 UDP 发现和 HTTP 传输，便于防火墙放行。

## 一、设备发现

发现走 **mDNS / DNS-SD（RFC 6762 / 6763，首选）+ UDP 广播（补充）** 双通道，三端实现。

### 1. mDNS / DNS-SD（推荐，跨端互通的标准通道）

- 服务类型：**\_orangego.\_tcp**（多播 224.0.0.251:5353）

- 服务实例名：设备名

- TXT 记录：`id=<deviceId>`、`name=<设备名>`、`v=<版本>`

- SRV：`port=53317`

- 三端均**广告**该服务、并**浏览**同型服务，自动发现同网段设备

- 设备离线：收到 `ServiceLost` 即移除；同时用超时兜底（>15s 未再见移除）

示例（Haukcode.Mdns / DNS-SD 语义等价物）：

```
ServiceProfile(deviceName, "_orangego._tcp", port: 53317,
               properties: { "id": deviceId, "name": deviceName, "v": "1" })
```

### 2. UDP 广播（补充，53317 兜底）

每台设备周期性（默认 5s）向 `255.255.255.255:53317` 发送通告，并监听同网段通告。

> **去重与地址约定（各端统一）**：
>
> - 设备去重一律以 `deviceId` 为唯一键，同一设备的 mDNS 与 UDP 双通道结果合并为一条，禁止出现同名重复设备。
>
> - mDNS 解析/广播来源地址仅接受**可路由的 IPv4**（排除 IPv6 链路本地 `fe80::`、回环与虚拟网卡地址），保证后续 HTTP 传输可达；取不到有效 IPv4 时跳过该通道，交由 UDP 通道兜底。
>
> - mDNS 广告必须携带 TXT `id=<deviceId>`，供对端跨通道去重（与 UDP 通告的 `deviceId` 一致）。

### 请求（UDP/PacketType.DISCOVERY\_REQUEST）

```json
{
  "messageType": "discoveryRequest",
  "deviceId": "og_<随机字符串>",
  "name": "设备名",
  "alias": "别名",
  "port": 53317,
  "version": "1.0"
}
```

### 响应（UDP/PacketType.DISCOVERY\_RESPONSE）

收到请求的设备单播回应相同的设备信息，便于发现方确认端口可达：

```json
{
  "messageType": "discoveryResponse",
  "deviceId": "...",
  "name": "...",
  "alias": "...",
  "port": 53317,
  "version": "1.0"
}
```

- `deviceId` 全局唯一且稳定（用于会话归属与去重）。

- 设备离线判定：超过 3 个通告周期（约 15s）未再收到通告，视为离线。

## 二、传输（HTTP over TCP 53317）

**方向约定**：发现方（得到对端 IP:port）作为 HTTP **客户端**；被发现的设备作为 HTTP **服务器**（监听 53317）。

所有请求/响应体均为 JSON，字符集 UTF-8。

### 1. 发送初始化 `POST /api/v1/send/init`

发送方在传文件前先打招呼，让接收方弹窗授权。

请求体：

```json
{
  "messageType": "sendInit",
  "sendId": "uuid",
  "deviceId": "发送方deviceId",
  "name": "发送方设备名",
  "totalFiles": 3,
  "totalSize": 123456789
}
```

响应：

- `200 OK`，`{"messageType":"ok","sendId":"uuid"}` → 已授权，继续传文件

- `403`，`{"messageType":"reject","sendId":"uuid","info":"..."}` → 接收方拒绝

- `404`，`{"messageType":"error","info":"no pending send"}` → 无待处理会话

### 2. 传输文件 `POST /api/v1/send/file?sendId=<id>`

需要授权码才能访问。使用请求头传递授权：

- `Authorization: Bearer <token>`

请求体为文件二进制流。文件元数据通过请求头传递：

- `X-FileName: <URL编码的文件名>`

- `X-FileToken: <本次文件唯一标识>`

- `X-FileSize: <字节数>`

响应：

- `200 OK` `{"messageType":"ok","token":...}` → 接收成功

- `401` `{"messageType":"expired"}` → 授权失效（需重新 init）

- `413` `{"messageType":"error","info":"no such file"}` → 文件未被登记

> 说明：文件必须在 init 阶段登记（含文件名/大小清单），传输阶段按 token 核对。

### 3. 取消 `POST /api/v1/send/cancel`

请求体：

```json
{
  "messageType": "sendCancel",
  "sendId": "uuid"
}
```

响应 `200 OK` `{"messageType":"ok"}`。

### 4. 握手/元数据 GET（可选）

```http
POST /api/v1/discover
```

返回设备信息 JSON，用于 HTTP 层的端口可达性确认（与 UDP 响应同构）。

### 5. 文本消息 `POST /api/v1/send/message`

传输一条即时文字（聊天气泡），**不落盘、不是文件**，无需 init/prepare/授权流程。

请求体（`application/json`）：

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

响应 `200 OK` `{"messageType":"ok"}`。

- `400` `{"messageType":"error","info":"bad body"}` → body 解析失败

- `400` `{"messageType":"error","info":"empty content"}` → content 为空

- 约定：三端实现需同步本命令，接收端将其展示为聊天气泡而非文件记录。

## 三、安全说明（v1）

- v1 面向可信局域网：无 TLS 加密，仅做设备互认与授权确认。

- 接收方每次发送授权都需二次确认，不自动接受。

- 后续版本可叠加 TLS/自签证书与 PIN 握手，协议层预留 `sendId` + `token` 扩展点。

