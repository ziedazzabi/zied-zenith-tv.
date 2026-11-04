package com.zenith.iptv.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A complete colour design for the app. */
data class Palette(
    val id: String,
    val light: Boolean,
    val bg: Color,
    val bgGlow: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val accent: Color,
    val accentFocus: Color,
    val onAccent: Color,
    val accent2: Color,
    val text: Color,
    val muted: Color,
    val line: Color,
    /** Home tile colours: Live, Movies, Series, Guide, Multi-view, Recordings, Settings. */
    val tiles: List<Color>,
    /** Two colours used for the preview swatch in the theme picker. */
    val swatch: List<Color>,
)

private fun colors(vararg v: Long) = v.map { Color(it) }

object Themes {
    val MIDNIGHT = Palette(
        id = "midnight", light = false,
        bg = Color(0xFF0B0F17), bgGlow = Color(0xFF10233A),
        surface = Color(0xFF131A26), surface2 = Color(0xFF1B2433), surface3 = Color(0xFF243044),
        accent = Color(0xFF22D3EE), accentFocus = Color(0xFF67E8F9), onAccent = Color(0xFF00141A), accent2 = Color(0xFFF472B6),
        text = Color(0xFFE6EDF7), muted = Color(0xFF8A97AB), line = Color(0xFF263247),
        tiles = colors(0xFF0EA5E9, 0xFFDB2777, 0xFF7C3AED, 0xFF059669, 0xFFD97706, 0xFFDC2626, 0xFF475569),
        swatch = colors(0xFF0B0F17, 0xFF22D3EE),
    )

    /** White, gold and royal blue, inspired by Madrid's famous all-white kit. */
    val ROYAL = Palette(
        id = "royal", light = true,
        bg = Color(0xFFF7F7FA), bgGlow = Color(0xFFFFF1CC),
        surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEEF0F6), surface3 = Color(0xFFE2E6F0),
        accent = Color(0xFFC9A227), accentFocus = Color(0xFFE0B83A), onAccent = Color(0xFF1A1300), accent2 = Color(0xFF3B2A8C),
        text = Color(0xFF0E1A3D), muted = Color(0xFF5A6482), line = Color(0xFFD6DAE6),
        tiles = colors(0xFF00529F, 0xFFC9A227, 0xFF3B2A8C, 0xFF0E1A3D, 0xFFB8860B, 0xFFA32638, 0xFF5A6482),
        swatch = colors(0xFFFFFFFF, 0xFFC9A227),
    )

    /** Night version of the royal design: deep navy with gold and purple. */
    val ROYAL_NIGHT = Palette(
        id = "royal_night", light = false,
        bg = Color(0xFF070B1F), bgGlow = Color(0xFF1C1652),
        surface = Color(0xFF0F1530), surface2 = Color(0xFF18203F), surface3 = Color(0xFF242E55),
        accent = Color(0xFFE6C15A), accentFocus = Color(0xFFF5D77E), onAccent = Color(0xFF1A1300), accent2 = Color(0xFF8B7CF6),
        text = Color(0xFFF4F1E8), muted = Color(0xFF9AA0BF), line = Color(0xFF26305A),
        tiles = colors(0xFF1E4FA8, 0xFFB8912F, 0xFF5B3FB8, 0xFF1F7A6B, 0xFFC08A1E, 0xFFA32638, 0xFF3A4370),
        swatch = colors(0xFF0F1530, 0xFFE6C15A),
    )

    /** Movie-theatre look: black, red curtain and marquee gold. */
    val CINEMA = Palette(
        id = "cinema", light = false,
        bg = Color(0xFF0A0A0A), bgGlow = Color(0xFF2A0608),
        surface = Color(0xFF151515), surface2 = Color(0xFF1F1F1F), surface3 = Color(0xFF2C2C2C),
        accent = Color(0xFFE50914), accentFocus = Color(0xFFFF3D47), onAccent = Color(0xFFFFFFFF), accent2 = Color(0xFFF5C518),
        text = Color(0xFFF5F5F5), muted = Color(0xFF9E9E9E), line = Color(0xFF2E2E2E),
        tiles = colors(0xFFB20710, 0xFFE50914, 0xFF7A0A10, 0xFFB8860B, 0xFFF5A300, 0xFF8B0000, 0xFF404040),
        swatch = colors(0xFF0A0A0A, 0xFFE50914),
    )

    /** Football-pitch greens. */
    val STADIUM = Palette(
        id = "stadium", light = false,
        bg = Color(0xFF06140D), bgGlow = Color(0xFF0D3A22),
        surface = Color(0xFF0E2017), surface2 = Color(0xFF152C20), surface3 = Color(0xFF1E3B2B),
        accent = Color(0xFFA3E635), accentFocus = Color(0xFFBEF264), onAccent = Color(0xFF0B1A00), accent2 = Color(0xFFFFFFFF),
        text = Color(0xFFEAF5EE), muted = Color(0xFF8DA898), line = Color(0xFF1F3A2B),
        tiles = colors(0xFF15803D, 0xFF65A30D, 0xFF0F766E, 0xFF166534, 0xFFCA8A04, 0xFFB91C1C, 0xFF3F5A4B),
        swatch = colors(0xFF0E2017, 0xFFA3E635),
    )

    val SUNSET = Palette(
        id = "sunset", light = false,
        bg = Color(0xFF140B1F), bgGlow = Color(0xFF3A1340),
        surface = Color(0xFF1E1230), surface2 = Color(0xFF2A1A40), surface3 = Color(0xFF382454),
        accent = Color(0xFFFB923C), accentFocus = Color(0xFFFDBA74), onAccent = Color(0xFF1F0A00), accent2 = Color(0xFFF472B6),
        text = Color(0xFFFBEFF5), muted = Color(0xFFB09BBF), line = Color(0xFF3A2A50),
        tiles = colors(0xFFEA580C, 0xFFDB2777, 0xFF9333EA, 0xFFC2410C, 0xFFE11D48, 0xFFBE123C, 0xFF5B4570),
        swatch = colors(0xFF1E1230, 0xFFFB923C),
    )

    /** True black: saves power on OLED screens. */
    val AMOLED = Palette(
        id = "amoled", light = false,
        bg = Color(0xFF000000), bgGlow = Color(0xFF000000),
        surface = Color(0xFF0C0C0C), surface2 = Color(0xFF161616), surface3 = Color(0xFF242424),
        accent = Color(0xFFFFFFFF), accentFocus = Color(0xFFE0E0E0), onAccent = Color(0xFF000000), accent2 = Color(0xFF9CA3AF),
        text = Color(0xFFF2F2F2), muted = Color(0xFF8C8C8C), line = Color(0xFF222222),
        tiles = colors(0xFF262626, 0xFF262626, 0xFF262626, 0xFF262626, 0xFF262626, 0xFF262626, 0xFF262626),
        swatch = colors(0xFF000000, 0xFFFFFFFF),
    )

    val all = listOf(MIDNIGHT, ROYAL, ROYAL_NIGHT, CINEMA, STADIUM, SUNSET, AMOLED)
    fun byId(id: String) = all.firstOrNull { it.id == id } ?: MIDNIGHT
}

/** Current colours. Reading these inside a composable recomposes it when the theme changes. */
object Z {
    var palette by mutableStateOf(Themes.MIDNIGHT)
    val bg get() = palette.bg
    val bgGlow get() = palette.bgGlow
    val surface get() = palette.surface
    val surface2 get() = palette.surface2
    val surface3 get() = palette.surface3
    val accent get() = palette.accent
    val accentFocus get() = palette.accentFocus
    val onAccent get() = palette.onAccent
    val pink get() = palette.accent2
    val text get() = palette.text
    val muted get() = palette.muted
    val line get() = palette.line
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFEF4444)
    val green = Color(0xFF34D399)
}

/** Which layout the UI uses. */
enum class DeviceClass { PHONE, TABLET, TV }

val LocalIsTv = staticCompositionLocalOf { false }
val LocalDevice = staticCompositionLocalOf { DeviceClass.PHONE }

fun isTvDevice(context: Context): Boolean {
    val ui = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    if (ui.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
}

/** Layout helpers that adapt to phone / tablet / TV. */
object Adaptive {
    /** True when there's room for side-by-side panes (tablet, TV, or a phone in landscape). */
    @Composable
    fun wide(): Boolean = LocalDevice.current != DeviceClass.PHONE || widthDp() >= 600

    /** Usable screen width in dp, taking the interface-size setting into account. */
    @Composable
    fun widthDp(): Int {
        val cfg = LocalConfiguration.current
        val sys = LocalContext.current.resources.displayMetrics.density
        return (cfg.screenWidthDp * sys / LocalDensity.current.density).toInt()
    }

    @Composable
    fun gutter(): Dp = when (LocalDevice.current) {
        DeviceClass.PHONE -> 12.dp; DeviceClass.TABLET -> 22.dp; DeviceClass.TV -> 40.dp
    }

    @Composable
    fun poster(): Dp = when (LocalDevice.current) {
        DeviceClass.PHONE -> 112.dp; DeviceClass.TABLET -> 140.dp; DeviceClass.TV -> 150.dp
    }

    @Composable
    fun sidePane(): Dp = when (LocalDevice.current) {
        DeviceClass.PHONE -> 220.dp; DeviceClass.TABLET -> 250.dp; DeviceClass.TV -> 290.dp
    }
}

fun resolveDevice(layoutMode: String, realTv: Boolean, smallestWidthDp: Int): DeviceClass = when (layoutMode) {
    "phone" -> DeviceClass.PHONE
    "tablet" -> DeviceClass.TABLET
    "tv" -> DeviceClass.TV
    else -> when {
        realTv -> DeviceClass.TV
        smallestWidthDp >= 600 -> DeviceClass.TABLET
        else -> DeviceClass.PHONE
    }
}

@Composable
fun ZenithTheme(
    layoutMode: String = "auto",
    uiScale: Float = 1f,
    realTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val p = Z.palette
    val cfg = LocalConfiguration.current
    val device = resolveDevice(layoutMode, realTv, cfg.smallestScreenWidthDp)

    val scheme = if (p.light) lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, error = Z.red,
    ) else darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2,
        background = p.bg, onBackground = p.text, surface = p.surface, onSurface = p.text,
        surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.line, error = Z.red,
    )
    val base = Typography()
    val typo = base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )
    val d = LocalDensity.current
    val scaled = Density(d.density * uiScale.coerceIn(0.7f, 1.5f), d.fontScale)
    CompositionLocalProvider(
        LocalDevice provides device,
        LocalIsTv provides (device == DeviceClass.TV),
        LocalDensity provides scaled,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typo, content = content)
    }
}
