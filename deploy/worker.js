/**
 * OrangeGO 专属「问题反馈」Cloudflare Worker
 * ------------------------------------------------------------------
 * 用法：Cloudflare Workers → 创建 Worker（Hello World 模板）→ 编辑代码，
 *     全选覆盖粘贴本文件 → Save and Deploy。
 *
 * 功能：接收客户端 POST /feedback，先强制 hCaptcha 人机验证，通过后：
 *      1. 创建 GitHub Issue（留档）
 *      2. WxPusher 微信推送提醒（可选）
 *      任一路成功即视为提交成功。
 *
 * 环境变量（Cloudflare Worker → 设置 → 变量，密钥用 Secret 类型）：
 *    GITHUB_TOKEN      (Secret) GitHub 个人访问令牌（授权目标仓库 + Issues:read/write）
 *    GITHUB_REPO       (Text)   目标仓库，形如 "orange-way/OrangeGO"
 *    HCAPTCHA_SECRET   (Secret) hCaptcha 站点 Secret Key（siteverify 用）
 *    WXPUSHER_APPTOKEN (Secret) （可选）WxPusher App Token
 *    WXPUSHER_UID      (Text)   （可选）接收推送的 WxPusher UID
 *
 * 说明：所有密钥只存于 Worker 环境变量，绝不写进 APK / 仓库。
 * 注意：客户端 App 提交字段名必须是 hcaptcha_token（与 FeedbackManager.kt 一致）。
 */

const GITHUB_DIRECT = (repo) => `https://api.github.com/repos/${repo}/issues`;
const GITHUB_MIRROR  = (repo) => `https://gh.llkk.cc/https://api.github.com/repos/${repo}/issues`;
const WXPUSHER_API = "https://wxpusher.zjiecode.com/api/send/message";
const HCAPTCHA_VERIFY = "https://api.hcaptcha.com/siteverify";

const USER_AGENT = "OrangeGO/1.0";

// 各版本 APK 的合法 SHA256（发布时由本文件维护；App 下载后经 /checksum 取到并比对，
// 不一致即视为被篡改而拒绝安装）。发布新版 APK 时必须在下方登记对应值。
// 示例：{ "1.0.0": "64位十六进制大写SHA256" }
const APK_SHA256 = {
  "1.0.0": "A57A0F39548B230049CCFEF5BA42A70775DBF344204661544FB262B07DF37AC6",
};