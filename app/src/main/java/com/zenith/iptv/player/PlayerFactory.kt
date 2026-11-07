package com.zenith.iptv.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory

import com.zenith.iptv.Graph
import com.zenith.iptv.data.Net

@OptIn(UnstableApi::class)
object PlayerFactory {

    fun create(context: Context, lowLatency: Boolean = false): ExoPlayer {
        val repo = Graph.repo
        val s = repo.settings
        val http = OkHttpDataSource.Factory(Net.client)
            .setUserAgent(repo.userAgent())
            .setDefaultRequestProperties(repo.headers().filterKeys { !it.equals("User-Agent", true) })
        val dataSource = DefaultDataSource.Factory(context, http)

        val extractors = DefaultExtractorsFactory()
            // Note: FLAG_ALLOW_NON_IDR_KEYFRAMES is deliberately NOT used: it starts drawing before the first full
            // frame and shows blocky/"eaten" pictures for a few seconds after every channel change.
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setTsExtractorTimestampSearchBytes(600 * 188 * 3) // 3x TsExtractor default (600 packets of 188 bytes)
            .setConstantBitrateSeekingEnabled(true)

        val buffer = (s.bufferSeconds.coerceIn(2, 60)) * 1000
        // Live streams must be read continuously: if the player pauses reading when its buffer is full,
        // many IPTV servers drop that part of the stream and the picture breaks into blocks a few seconds later.
        // So for live, min buffer == max buffer (the loader never stops while below it).
        val liveBuffer = maxOf(buffer * 2, 40_000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ if (lowLatency) liveBuffer else maxOf(buffer, 4000),
                /* maxBufferMs */ if (lowLatency) liveBuffer else maxOf(buffer * 3, 30_000),
                /* bufferForPlaybackMs */ if (lowLatency) 1500 else 2000,
                /* bufferForPlaybackAfterRebufferMs */ if (lowLatency) 3000 else 4000,
            )
            // Movies (4K files can be 50+ Mbit/s): limit the buffer by size, otherwise tens of seconds of 4K
            // fill the memory and the picture freezes. Live keeps time priority (continuous reading, see above).
            .setPrioritizeTimeOverSizeThresholds(lowLatency)
            .setTargetBufferBytes(if (lowLatency) C.LENGTH_UNSET else 96 * 1024 * 1024)
            .build()

        val renderers = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(
                when (s.decoderMode) {
                    "software" -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                }
            )

        return ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build()
    }

    fun mediaItem(url: String, title: String = ""): MediaItem {
        val lower = url.lowercase().substringBefore('?')
        val b = MediaItem.Builder().setUri(Uri.parse(url))
        when {
            lower.endsWith(".m3u8") || "m3u8" in url.lowercase() -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
            lower.endsWith(".mpd") -> b.setMimeType(MimeTypes.APPLICATION_MPD)
            lower.endsWith(".ism") || lower.endsWith("/manifest") -> b.setMimeType(MimeTypes.APPLICATION_SS)
        }
        if (title.isNotEmpty()) b.setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
        return b.build()
    }
}
