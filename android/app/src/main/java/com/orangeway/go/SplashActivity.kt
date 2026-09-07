package com.orangeway.go

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.orangeway.go.data.SettingsRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** 启动画面：居中显示 BarLogo，随应用主题适配深浅色，随后进入主界面。 */
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // 强制沉浸式全屏：让内容从屏幕顶端开始布局，避免状态栏/刘海预留区把 logo 整体压低
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // 深色判定：主题模式（0=跟随系统，1=浅，2=深）里显式选深色或跟随系统且系统为深
        val mode = runBlocking { runCatching { SettingsRepo(this@SplashActivity).themeMode.first() }.getOrDefault(0) }
        val sysDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val dark = mode == 2 || (mode == 0 && sysDark)
        if (dark) {
            findViewById<View>(R.id.splash_root).setBackgroundColor(0xFF131110.toInt())
            runCatching {
                findViewById<ImageView>(R.id.splash_logo).setImageResource(R.drawable.og_barlogo_white)
            }
            window.statusBarColor = 0xFF131110.toInt()
            window.navigationBarColor = 0xFF131110.toInt()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(0, 0)
            finish()
        }, 1100)
    }
}