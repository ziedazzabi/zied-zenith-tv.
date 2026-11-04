package com.zenith.iptv.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.ColorUtils
import coil.compose.AsyncImage
import com.zenith.iptv.data.Net
import com.zenith.iptv.data.Settings
import com.zenith.iptv.data.Wallpaper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Themes built from the user's own pictures. */
object Wallpapers {
    const val PREFIX = "wp:"

    fun folder(ctx: Context) = File(ctx.filesDir, "wallpapers").apply { mkdirs() }

    fun active(s: Settings): Wallpaper? =
        if (s.theme.startsWith(PREFIX)) s.wallpapers.firstOrNull { PREFIX + it.id == s.theme } else null

    suspend fun fromUri(ctx: Context, uri: Uri, name: String): Wallpaper = withContext(Dispatchers.IO) {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot open image")
        create(ctx, bytes, name)
    }

    suspend fun fromUrl(ctx: Context, url: String, name: String): Wallpaper = withContext(Dispatchers.IO) {
        create(ctx, Net.getBytes(url.trim()), name)
    }

    fun delete(w: Wallpaper) { runCatching { File(w.file).delete() } }

    private fun create(ctx: Context, bytes: ByteArray, name: String): Wallpaper {
        // Decode at a reasonable size (max ~2560px) to keep memory low on TV boxes.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) error("Not an image")
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Not an image")

        val id = UUID.randomUUID().toString().take(8)
        val file = File(folder(ctx), "$id.jpg")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        val pal = androidx.palette.graphics.Palette.from(bmp).maximumColorCount(24).generate()
        bmp.recycle()
        val fallback = 0xFF1E293B.toInt()
        val vibrant = pal.vibrantSwatch?.rgb ?: pal.lightVibrantSwatch?.rgb ?: pal.dominantSwatch?.rgb ?: 0xFF22D3EE.toInt()
        val second = pal.lightVibrantSwatch?.rgb ?: pal.mutedSwatch?.rgb ?: pal.lightMutedSwatch?.rgb ?: vibrant
        val base = pal.darkMutedSwatch?.rgb ?: pal.darkVibrantSwatch?.rgb ?: pal.dominantSwatch?.rgb ?: fallback
        val tiles = listOfNotNull(
            pal.vibrantSwatch?.rgb, pal.darkVibrantSwatch?.rgb, pal.mutedSwatch?.rgb,
            pal.lightVibrantSwatch?.rgb, pal.darkMutedSwatch?.rgb, pal.dominantSwatch?.rgb, pal.lightMutedSwatch?.rgb,
        ).distinct().ifEmpty { listOf(vibrant) }

        return Wallpaper(
            id = id, name = name.ifBlank { "Wallpaper" }, file = file.absolutePath,
            base = base.toLong() and 0xFFFFFFFFL, accent = vibrant.toLong() and 0xFFFFFFFFL,
            accent2 = second.toLong() and 0xFFFFFFFFL, tiles = tiles.map { it.toLong() and 0xFFFFFFFFL },
        )
    }

    private fun shade(c: Int, toBlack: Float) = ColorUtils.blendARGB(c, 0xFF000000.toInt(), toBlack)

    /** Builds a full colour design from a wallpaper. Surfaces are translucent so the picture shows through. */
    fun palette(w: Wallpaper): Palette {
        val base = w.base.toInt()
        var accent = w.accent.toInt()
        // keep the accent bright enough to read on a dark background
        while (ColorUtils.calculateLuminance(accent) < 0.25) accent = ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.2f)
        val onAccent = if (ColorUtils.calculateLuminance(accent) > 0.45) Color(0xFF0A0A0A) else Color.White
        val surface = Color(shade(base, 0.55f)).copy(alpha = 0.78f)
        // tiles must be dark enough for white text
        val tiles = (w.tiles.map { it.toInt() } + List(7) { accent }).take(7).map { t ->
            var c = t
            while (ColorUtils.calculateLuminance(c) > 0.35) c = shade(c, 0.2f)
            Color(c)
        }
        return Palette(
            id = PREFIX + w.id, light = false,
            bg = Color(shade(base, 0.82f)), bgGlow = Color.Transparent,
            surface = surface,
            surface2 = Color(shade(base, 0.40f)).copy(alpha = 0.82f),
            surface3 = Color(shade(base, 0.20f)).copy(alpha = 0.90f),
            accent = Color(accent),
            accentFocus = Color(ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.3f)),
            onAccent = onAccent,
            accent2 = Color(w.accent2.toInt()),
            text = Color(0xFFF5F7FA), muted = Color(0xFFC2C9D4), line = Color.White.copy(alpha = 0.16f),
            tiles = tiles,
            swatch = listOf(Color(base), Color(accent)),
        )
    }
}

/** Full-screen picture behind the whole app, darkened so text stays readable. */
@Composable
fun WallpaperBackground(w: Wallpaper) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(w.file), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = (w.dim - 0.15f).coerceIn(0f, 1f)),
                    0.5f to Color.Black.copy(alpha = w.dim),
                    1f to Color.Black.copy(alpha = (w.dim + 0.2f).coerceIn(0f, 0.95f)),
                )
            )
        )
    }
}

object ThemeResolver {
    fun resolve(s: Settings): Palette = Wallpapers.active(s)?.let { Wallpapers.palette(it) } ?: Themes.byId(s.theme)
}
