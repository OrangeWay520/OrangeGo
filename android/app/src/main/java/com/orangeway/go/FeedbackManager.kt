package com.orangeway.go

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.ProxySelector
import java.util.concurrent.TimeUnit

/**
 * 问题反馈的部署配置（与 HereIAm 同款机制）。
 * 反馈会经自建 Cloudflare Worker 代理转发到 GitHub Issues + 微信推送；
 * hCaptcha 人机验证的 Secret Key 只存 Worker 环境变量，Site Key 公开用于客户端。
 * 注意：OrangeGO 需要部署自己的 Worker 后把 feedbackProxyUrl 换成自己的地址。
 */
object FeedbackConfig {
    private const val workProxyBase = "https://orangego.orangeway.workers.dev"
    const val feedbackProxyUrl = "$workProxyBase/feedback"
    /** 应用更新 APK 的 SHA256 校验接口（与 HereIAm 同款，见 deploy/worker.js 的 /checksum） */
    const val checksumUrl = "$workProxyBase/checksum"
    /** hCaptcha Site Key（公开）；Secret Key 只存服务端 Worker 环境变量 */
    const val hcaptchaSiteKey = "b20c5714-7cda-4964-9aa0-b26ce4b21a82"
}

/** 单个通道的提交结果 */
sealed interface ChannelResult {
    data class Success(val detail: String) : ChannelResult
    data class Failure(val reason: String) : ChannelResult
}

/** 反馈提交总结果：两条路线（GitHub Issues / 微信推送）各自状态 */
data class FeedbackOutcome(val github: ChannelResult, val wxpusher: ChannelResult) {
    /** 至少一条路线提交成功 */
    val anySuccess: Boolean get() = github is ChannelResult.Success || wxpusher is ChannelResult.Success
}

/**
 * 问题反馈：把反馈内容提交到自建 Cloudflare Worker 代理（方案A，与 HereIAm 一致）。
 * 网络请求在 IO 线程执行；GitHub Token / 推送密钥只存在于 Worker 环境变量。
 */
object FeedbackManager {

    private const val USER_AGENT = "OrangeGO/1.0"

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .proxySelector(ProxySelector.getDefault())
        .build()

    /** 同时提交到微信推送与 GitHub Issues（由 Worker 代发），返回两条路线各自结果 */
    suspend fun submit(title: String, body: String, captchaToken: String): FeedbackOutcome =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                    .put("title", title)
                    .put("body", body)
                    .put("hcaptcha_token", captchaToken)
                val request = Request.Builder()
                    .url(FeedbackConfig.feedbackProxyUrl)
                    .header("User-Agent", USER_AGENT)
                    .post(json.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        return@withContext FeedbackOutcome(
                            github = ChannelResult.Failure("代理服务 HTTP ${resp.code}"),
                            wxpusher = ChannelResult.Failure("代理服务 HTTP ${resp.code}")
                        )
                    }
                    val result = JSONObject(resp.body?.string().orEmpty())
                    FeedbackOutcome(
                        github = parseChannel(result.optJSONObject("github")),
                        wxpusher = parseChannel(result.optJSONObject("wxpusher"))
                    )
                }
            } catch (e: Exception) {
                val msg = e.message ?: e.javaClass.simpleName
                FeedbackOutcome(
                    github = ChannelResult.Failure(msg),
                    wxpusher = ChannelResult.Failure(msg)
                )
            }
        }

    /** 解析 Worker 返回的单通道结果 {ok, detail} */
    private fun parseChannel(obj: JSONObject?): ChannelResult {
        if (obj == null) return ChannelResult.Failure("缺少通道结果")
        val ok = obj.optBoolean("ok", false)
        val detail = obj.optString("detail", if (ok) "已提交" else "提交失败")
        return if (ok) ChannelResult.Success(detail) else ChannelResult.Failure(detail)
    }
}