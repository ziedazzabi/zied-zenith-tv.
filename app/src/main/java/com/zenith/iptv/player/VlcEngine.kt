package com.zenith.iptv.player

import android.content.Context
import android.net.Uri
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * VLC player used when ExoPlayer can't decode a channel's video (HEVC/MPEG-2/10-bit on devices without
 * a matching hardware decoder → "sound but no picture"). VLC falls back to software decoding.
 */
class VlcEngine(context: Context) {
    private val libVlc = LibVLC(
        context.applicationContext,
        arrayListOf("--network-caching=1500", "--http-reconnect", "--no-stats", "--audio-time-stretch"),
    )
    private val mp = MediaPlayer(libVlc)
    private var layout: VLCVideoLayout? = null

    var onBuffering: (Boolean) -> Unit = {}
    var onPlaying: () -> Unit = {}
    var onError: () -> Unit = {}
    var onEnded: () -> Unit = {}

    init {
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Buffering -> onBuffering(e.buffering < 100f)
                MediaPlayer.Event.Playing -> { onBuffering(false); onPlaying() }
                MediaPlayer.Event.EncounteredError -> onError()
                MediaPlayer.Event.EndReached -> onEnded()
                else -> {}
            }
        }
    }

    fun attach(v: VLCVideoLayout) {
        if (layout === v) return
        if (mp.vlcVout.areViewsAttached()) mp.detachViews()
        mp.attachViews(v, null, true, false)
        layout = v
    }

    fun detach() {
        if (mp.vlcVout.areViewsAttached()) mp.detachViews()
        layout = null
    }

    fun play(url: String, userAgent: String, headers: Map<String, String>, startMs: Long = 0) {
        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false) // hardware when possible, software otherwise
        media.addOption(":network-caching=1500")
        if (userAgent.isNotBlank()) media.addOption(":http-user-agent=$userAgent")
        headers["Referer"]?.let { media.addOption(":http-referrer=$it") }
        if (startMs > 0) media.addOption(":start-time=${startMs / 1000}")
        mp.media = media
        media.release()
        mp.play()
    }

    fun stop() { runCatching { mp.stop() } }
    fun pause() = mp.pause()
    fun resume() = mp.play()
    val isPlaying: Boolean get() = mp.isPlaying
    val time: Long get() = mp.time
    val length: Long get() = mp.length
    fun seekTo(ms: Long) { mp.time = ms.coerceAtLeast(0) }

    fun setAspect(mode: String) {
        mp.videoScale = when (mode) {
            "fill" -> MediaPlayer.ScaleType.SURFACE_FILL
            "zoom" -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
    }

    fun release() {
        runCatching { mp.stop() }
        detach()
        mp.setEventListener(null)
        mp.release()
        libVlc.release()
    }
}

/** Channels that needed VLC, remembered so they open directly in VLC next time. */
object VlcMemory {
    private val keys = HashSet<String>()
    private const val PREF = "vlc_keys"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("player", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (keys.isEmpty()) keys.addAll(prefs(ctx).getStringSet(PREF, emptySet()) ?: emptySet())
    }

    fun needs(key: String) = key in keys

    fun remember(ctx: Context, key: String) {
        if (keys.add(key)) prefs(ctx).edit().putStringSet(PREF, HashSet(keys)).apply()
    }

    fun forget(ctx: Context, key: String) { if (keys.remove(key)) prefs(ctx).edit().putStringSet(PREF, HashSet(keys)).apply() }

    fun clear(ctx: Context) { keys.clear(); prefs(ctx).edit().remove(PREF).apply() }
}
