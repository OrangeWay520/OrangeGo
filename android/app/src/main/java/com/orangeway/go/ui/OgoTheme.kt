package com.orangeway.go.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.orangeway.go.data.SettingsRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/** 全局主题模式：0=跟随系统，1=浅色，2=深色。 */
object OgoThemeMode {
    val mode = MutableStateFlow(0)
}

/** 全局界面语言（ISO 代码，"system" 表示跟随系统）。 */
object OgoLang {
    val code = MutableStateFlow("system")
}

/** 主题色（浅色/深色两套，默认跟随系统）。 */
object OgoColors {
    val Success = Color(0xFF22C55E)
    val Danger = Color(0xFFE5484D)
}

private val LightColors = lightColorScheme(
    primary = Color(0xFFF97316),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6D3),
    onPrimaryContainer = Color(0xFF5A2B0B),
    secondary = Color(0xFFD97706),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDDD),
    onSecondaryContainer = Color(0xFFF97316),
    background = Color(0xFFF6F5F3),
    onBackground = Color(0xFF211B16),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF211B16),
    surfaceVariant = Color(0xFFF0ECE7),
    onSurfaceVariant = Color(0xFF6E625A),
    outline = Color(0xFFE2DBD4),
    surfaceTint = Color(0xFFF97316),
    error = Color(0xFFE5484D),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF3A1700),
    primaryContainer = Color(0xFF45210A),
    onPrimaryContainer = Color(0xFFFFDBC2),
    secondary = Color(0xFFFFB360),
    onSecondary = Color(0xFF3D1D00),
    secondaryContainer = Color(0xFF45210A),
    onSecondaryContainer = Color(0xFFFB923C),
    background = Color(0xFF131110),
    onBackground = Color(0xFFF2EBE4),
    surface = Color(0xFF1C1A18),
    onSurface = Color(0xFFF2EBE4),
    surfaceVariant = Color(0xFF2A2621),
    onSurfaceVariant = Color(0xFFA99B90),
    outline = Color(0xFF37322C),
    surfaceTint = Color(0xFFFB923C),
    error = Color(0xFFF0716F),
    onError = Color(0xFF3B090A)
)

@Composable
fun OgoTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        runCatching {
            val saved = SettingsRepo(context).themeMode.first()
            if (saved != OgoThemeMode.mode.value) OgoThemeMode.mode.value = saved
        }
    }
    val mode by OgoThemeMode.mode.collectAsState()
    val dark = appIsDark(mode)
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}

/** 应用当前是否处于深色（遵循全局主题：1=浅，2=深，0=跟随系统）。 */
@Composable
fun appIsDark(): Boolean {
    val mode by OgoThemeMode.mode.collectAsState()
    return appIsDark(mode)
}

@Composable
private fun appIsDark(mode: Int): Boolean {
    val darkSystem = isSystemInDarkTheme()
    return when (mode) {
        1 -> false
        2 -> true
        else -> darkSystem
    }
}