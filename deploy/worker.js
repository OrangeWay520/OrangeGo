/**
 * OrangeGO 专属「问题反馈 + 检查更新」Cloudflare Worker
 * ------------------------------------------------------------------
 * 端点：
 *   POST /feedback        反馈 → GitHub Issues + WxPusher（hCaptcha 人机验证）
 *   GET  /captcha         hCaptcha 人机验证 HTML 页面（PC 端 WebView2 加载）
 *   GET  /update          检查更新 → GitHub Release latest
 *   GET  /checksum?v=x    返回指定版本的 APK SHA256 校验值
 *
 * 部署（免费）：
 *   1. 打开 https://dash.cloudflare.com → Workers & Pages → 创建 Worker
 *   2. 粘贴本文件全部内容 → 部署
 *   3. 在 Worker 的「设置 → 变量和机密」中添加以下机密（Secrets）：
 *        GITHUB_TOKEN       = GitHub 个人访问令牌（Issues 写权限）
 *        GITHUB_REPO        = 目标仓库，形如 "OrangeWay520/OrangeGo"（Text）
 *        HCAPTCHA_SECRET    = hCaptcha 的 Secret Key（服务端校验）
 *        WXPUSHER_APPTOKEN  = WxPusher 应用 token（Secret，可选）
 *        WXPUSHER_UID       = 接收推送的 WxPusher UID（Text，可选）
 *
 * 说明：所有密钥只存于 Worker 环境变量，绝不写进 APK / 仓库。
 * 注意：客户端 App 提交字段名必须是 hcaptcha_token（与 FeedbackManager.kt 一致）。
 */

const WXPUSHER_API = "https://wxpusher.zjiecode.com/api/send/message";
const HCAPTCHA_VERIFY = "https://api.hcaptcha.com/siteverify";
const USER_AGENT = "OrangeGO/1.0";

// 各版本 APK 的合法 SHA256（发布时由本文件维护；App 下载后经 /checksum 取到并比对，
// 不一致即视为被篡改而拒绝安装）。发布新版 APK 时必须在下方登记对应值。
const APK_SHA256 = {
  "1.0.0": "A57A0F39548B230049CCFEF5BA42A70775DBF344204661544FB262B07DF37AC6",
};

/** JSON 响应辅助 */
function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** CORS 头（PC 端 WebView2 从 Worker 域名加载，同源不需要 CORS，但保留以备跨域调用） */
function corsHeaders() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };
}

/**
 * 用 Secret Key 调用 hCaptcha 官方接口校验 token。
 * 返回 true 表示验证通过。
 */
async function verifyHCaptcha(env, token) {
  try {
    const resp = await fetch(HCAPTCHA_VERIFY, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        secret: env.HCAPTCHA_SECRET,
        response: token,
        remoteip: "",
      }),
    });
    const data = await resp.json();
    return data.success === true;
  } catch {
    return false;
  }
}

/** GitHub Issues 通道：镜像优先，官方直连兜底 */
async function submitGithub(env, title, body) {
  const token = env.GITHUB_TOKEN;
  if (!token) return { ok: false, detail: "GitHub 未配置" };

  const repo = env.GITHUB_REPO || "OrangeWay520/OrangeGo";
  const mirror = `https://gh.llkk.cc/https://api.github.com/repos/${repo}/issues`;
  const direct = `https://api.github.com/repos/${repo}/issues`;

  const payload = JSON.stringify({ title, body });
  const headers = {
    "User-Agent": USER_AGENT,
    Authorization: `token ${token}`,
    "Content-Type": "application/json",
  };

  let lastError = "所有地址均不可用";
  for (const url of [mirror, direct]) {
    try {
      const resp = await fetch(url, { method: "POST", headers, body: payload });
      const text = await resp.text();
      if (resp.ok) {
        try {
          const issue = JSON.parse(text);
          return { ok: true, detail: issue.html_url || issue.url || "已提交到 GitHub" };
        } catch {
          return { ok: true, detail: "已提交到 GitHub" };
        }
      }
      lastError = `HTTP ${resp.status}`;
    } catch (e) {
      lastError = e.message || "网络错误";
    }
  }
  return { ok: false, detail: lastError };
}

/** WxPusher 通道（code=1000 成功）；兼容 WXPUSHER_APPTOKEN 和 WXPUSHER_TOKEN 两种变量名 */
async function submitWxpusher(env, title, body) {
  const appToken = env.WXPUSHER_APPTOKEN || env.WXPUSHER_TOKEN;
  const uid = env.WXPUSHER_UID;
  if (!appToken || !uid) return { ok: false, detail: "WxPusher 未配置" };

  const payload = JSON.stringify({
    appToken,
    content: body,
    summary: `[OrangeGO] ${title.slice(0, 12)}`,
    contentType: 1,
    uids: [uid],
  });

  try {
    const resp = await fetch(WXPUSHER_API, {
      method: "POST",
      headers: { "User-Agent": USER_AGENT, "Content-Type": "application/json" },
      body: payload,
    });
    const text = await resp.text();
    if (resp.ok) {
      let data = {};
      try {
        data = JSON.parse(text);
      } catch { /* 非 JSON 按失败处理 */ }
      if (data.code === 1000) return { ok: true, detail: "已提交到 WxPusher" };
      return { ok: false, detail: data.msg || `code=${data.code}` };
    }
    return { ok: false, detail: `HTTP ${resp.status}` };
  } catch (e) {
    return { ok: false, detail: e.message || "网络错误" };
  }
}

/** GET /checksum?v=tag：返回对应版本的 APK SHA256 校验值 */
async function checksumFor(env, params) {
  const tag = (params.get("v") || "").replace(/^v/, "");
  const file = params.get("file") || "";
  if (!tag) return json({ error: "missing v" }, 400);
  const sha = APK_SHA256[tag] || null;
  return json({ file, tag, sha256: sha });
}

/** GET /update：查询 GitHub Release latest，返回版本信息 */
async function latestRelease(env) {
  const token = env.GITHUB_TOKEN;
  const repo = env.GITHUB_REPO || "OrangeWay520/OrangeGo";
  if (!token) return json({ error: "GitHub 未配置" }, 500);

  let lastError = "所有地址均不可用";
  for (const api of [
    `https://gh.llkk.cc/https://api.github.com/repos/${repo}/releases/latest`,
    `https://api.github.com/repos/${repo}/releases/latest`,
  ]) {
    try {
      const resp = await fetch(api, {
        headers: { "User-Agent": USER_AGENT, Authorization: `token ${token}` },
      });
      if (!resp.ok) {
        lastError = `GitHub HTTP ${resp.status}`;
        continue;
      }
      const rel = await resp.json();
      const tag = rel.tag_name || "";
      if (!tag) return json({ error: "empty tag" }, 502);
      const asset = (rel.assets || []).find((a) => (a.name || "").endsWith(".apk"));
      return json({
        tag_name: tag,
        body: rel.body || "",
        apk_url: asset ? asset.browser_download_url : null,
        apk_name: asset ? asset.name : null,
        apk_size: asset ? asset.size : 0,
      });
    } catch (e) {
      lastError = e.message || "网络错误";
    }
  }
  return json({ error: lastError }, 502);
}

/**
 * GET /captcha：返回 hCaptcha 人机验证 HTML 页面。
 * PC 端用 WebView2 加载此页面（从 Worker 域名加载，因 hCaptcha 拒绝 about:blank/localhost origin）。
 * 含 open-callback / close-callback / getNewToken 机制：
 *   - open-callback：挑战展开时 postMessage 通知 PC 端浮层显示
 *   - close-callback：挑战关闭时 postMessage 通知 PC 端浮层隐藏
 *   - getNewToken：失败后无感刷新 token（hcaptcha.execute()）
 */
function captchaHtml() {
  const sitekey = "b20c5714-7cda-4964-9aa0-b26ce4b21a82";
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>人机验证</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html, body { width: 100%; height: 100%; background: transparent; }
    body {
      display: flex; flex-direction: column;
      align-items: flex-end; justify-content: flex-end;
      padding: 8px;
    }
    #captcha-container { width: 100%; max-width: 360px; }
    .h-captcha { transform: scale(0.95); transform-origin: bottom right; }
  </style>
  <script src="https://js.hcaptcha.com/1/api.js" async defer></script>
</head>
<body>
  <div id="captcha-container">
    <div class="h-captcha"
         data-sitekey="${sitekey}"
         data-callback="onVerify"
         data-open-callback="onOpen"
         data-close-callback="onClose"
         data-size="normal"></div>
  </div>
  <script>
    function onVerify(token) {
      window.chrome.webview.postMessage({ type: 'captcha-token', token: token });
    }
    function onOpen() {
      window.chrome.webview.postMessage({ type: 'challenge-open' });
    }
    function onClose() {
      window.chrome.webview.postMessage({ type: 'challenge-close' });
    }
    function getNewToken() {
      try {
        hcaptcha.execute().then(function(token) {
          window.chrome.webview.postMessage({ type: 'captcha-token', token: token });
        }).catch(function() {
          window.chrome.webview.postMessage({ type: 'captcha-error', error: 'execute failed' });
        });
      } catch (e) {
        window.chrome.webview.postMessage({ type: 'captcha-error', error: e.message });
      }
    }
  </script>
</body>
</html>`;
}

/** Worker 入口：路由分发 */
export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // OPTIONS 预检（CORS）
    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: corsHeaders() });
    }

    // GET /captcha：返回 hCaptcha HTML 页面（PC 端 WebView2 加载）
    if (url.pathname === "/captcha" && request.method === "GET") {
      return new Response(captchaHtml(), {
        status: 200,
        headers: { "Content-Type": "text/html; charset=utf-8", ...corsHeaders() },
      });
    }

    // GET /update：检查更新
    if (url.pathname === "/update" && request.method === "GET") {
      return latestRelease(env);
    }

    // GET /checksum?v=tag：返回 APK SHA256
    if (url.pathname === "/checksum" && request.method === "GET") {
      return checksumFor(env, url.searchParams);
    }

    // POST /feedback：问题反馈
    if (url.pathname === "/feedback" && request.method === "POST") {
      let payload;
      try {
        payload = await request.json();
      } catch {
        return json({ error: "invalid json" }, 400);
      }

      const title = String(payload.title || "").slice(0, 100);
      const body = String(payload.body || "");
      if (!title || !body) {
        return json({ error: "title/body required" }, 400);
      }

      // 人机验证（强制）：必须携带有效的 hCaptcha token 才放行。
      const token = String(payload.hcaptcha_token || "");
      if (env.HCAPTCHA_SECRET) {
        if (!token) return json({ error: "missing captcha token" }, 403);
        const ok = await verifyHCaptcha(env, token);
        if (!ok) return json({ error: "captcha verification failed" }, 403);
      }

      // 先发微信推送（快），再建 GitHub Issue（留档）；任一成功即算提交成功
      const wp = await submitWxpusher(env, title, body);
      const gh = await submitGithub(env, title, body);
      if (wp.ok || gh.ok) {
        return json({
          ok: true,
          wxpusher: wp,
          github: gh,
          detail: [wp.ok && wp.detail, gh.ok && gh.detail].filter(Boolean).join(" / "),
        });
      }
      return json({ ok: false, detail: gh.detail || wp.detail || "发送失败" }, 500);
    }

    return json({ error: "not found" }, 404);
  },
};
