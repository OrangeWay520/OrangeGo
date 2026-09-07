package com.orangeway.go

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import com.orangeway.go.data.SettingsRepo
import com.orangeway.go.ui.OgoLang
import com.orangeway.go.ui.OgoThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class OrangeApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        // 同步加载持久化的主题/语言，确保首个界面帧就按用户选择渲染，
        // 避免先按跟随系统（浅色）画一帧再异步切到深色造成的"闪白/由浅跳深"。
        runBlocking {
            withContext(Dispatchers.IO) {
                val sr = SettingsRepo(this@OrangeApp)
                OgoThemeMode.mode.value = runCatching { sr.themeMode.first() }.getOrDefault(0)
                OgoLang.code.value = runCatching { sr.language.first() }.getOrDefault("system")
            }
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(SvgDecoder.Factory()) }
            .build()
}