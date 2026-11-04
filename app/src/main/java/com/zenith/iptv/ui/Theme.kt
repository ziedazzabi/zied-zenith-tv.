package com.zenith.iptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Z {
    val bg = Color(0xFF0B0F17)
    val surface = Color(0xFF131A26)
    val surface2 = Color(0xFF1B2433)
    val surface3 = Color(0xFF243044)
    val accent = Color(0xFF22D3EE)
    val pink = Color(0xFFF472B6)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFEF4444)
    val green = Color(0xFF34D399)
    val text = Color(0xFFE6EDF7)
    val muted = Color(0xFF8A97AB)
    val line = Color(0xFF263247)
}

val LocalIsTv = staticCompositionLocalOf { false }

fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    if (ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

@Composable
fun ZenithTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Z.accent,
        onPrimary = Color(0xFF00141A),
        secondary = Z.pink,
        background = Z.bg,
        onBackground = Z.text,
        surface = Z.surface,
        onSurface = Z.text,
        surfaceVariant = Z.surface2,
        onSurfaceVariant = Z.muted,
        outline = Z.line,
        error = Z.red,
    )
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    MaterialTheme(colorScheme = scheme, typography = typo, content = content)
}
