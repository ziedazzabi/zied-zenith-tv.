package com.zenith.iptv.data

import kotlinx.serialization.Serializable

@Serializable
enum class SourceType { XTREAM, M3U, STALKER }

@Serializable
enum class Kind { LIVE, MOVIE, SERIES }

/** A playlist / provider the user added (Xtream login, M3U link or file, or a MAC portal). */
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String = "",
    val password: String = "",
    val mac: String = "",
    val epgUrl: String = "",
    val userAgent: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val kind: Kind,
    /** True when the items of this category must be fetched on demand (Stalker VOD). */
    val lazy: Boolean = false,
)

/**
 * One playable or browsable entry: a live channel, a movie, or a series.
 * [url] is filled when it can be played directly; otherwise the source resolves it.
 */
@Serializable
data class Media(
    val id: String,
    val playlistId: String,
    val kind: Kind,
    val name: String,
    val categoryId: String,
    val number: Int = 0,
    val logo: String = "",
    val url: String = "",
    val epgId: String = "",
    val catchupDays: Int = 0,
    val catchupType: String = "",
    val catchupSource: String = "",
    val rating: String = "",
    val year: String = "",
    val plot: String = "",
    val ext: String = "",
    /** Stalker command or other provider-specific data. */
    val cmd: String = "",
    val added: Long = 0,
) {
    val key: String get() = "$playlistId|${kind.name}|$id"
}

@Serializable
data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val url: String = "",
    val cmd: String = "",
    val cover: String = "",
    val plot: String = "",
    val duration: String = "",
)

@Serializable
data class MediaDetails(
    val plot: String = "",
    val cast: String = "",
    val director: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val duration: String = "",
    val rating: String = "",
    val backdrop: String = "",
    val trailer: String = "",
    val seasons: Map<Int, List<Episode>> = emptyMap(),
)

@Serializable
data class EpgProgram(
    val start: Long,
    val stop: Long,
    val title: String,
    val desc: String = "",
) {
    fun isLive(now: Long = System.currentTimeMillis()) = now in start until stop
    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (stop <= start) 0f else ((now - start).toFloat() / (stop - start)).coerceIn(0f, 1f)
}

@Serializable
data class Catalog(
    val playlistId: String,
    val categories: List<Category> = emptyList(),
    val items: List<Media> = emptyList(),
    val accountInfo: String = "",
    val epgUrl: String = "",
    val serverTimezone: String = "",
    val loadedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val color: Long,
    val kids: Boolean = false,
)

@Serializable
data class WatchProgress(val key: String, val positionMs: Long, val durationMs: Long, val updatedAt: Long, val title: String = "", val image: String = "")

@Serializable
data class ProfileData(
    val favorites: List<String> = emptyList(),
    val recentChannels: List<String> = emptyList(),
    val progress: Map<String, WatchProgress> = emptyMap(),
)

@Serializable
data class Reminder(
    val id: Int,
    val mediaKey: String,
    val channelName: String,
    val title: String,
    val start: Long,
)

@Serializable
enum class RecordingState { SCHEDULED, RECORDING, DONE, FAILED, CANCELLED }

@Serializable
data class Recording(
    val id: Int,
    val mediaKey: String,
    val channelName: String,
    val title: String,
    val start: Long,
    val stop: Long,
    val filePath: String = "",
    val state: RecordingState = RecordingState.SCHEDULED,
    val sizeBytes: Long = 0,
    val error: String = "",
)

@Serializable
data class Settings(
    val language: String = "",
    val activePlaylistId: String = "",
    val activeProfileId: String = "",
    val liveFormat: String = "ts",
    val userAgent: String = "",
    val bufferSeconds: Int = 15,
    val aspect: String = "fit",
    val parentalPin: String = "0000",
    val lockedCategories: List<String> = emptyList(),
    val autoLockAdult: Boolean = true,
    val epgOffsetMinutes: Int = 0,
    val use24h: Boolean = true,
    val autoPlayLastChannel: Boolean = false,
    val externalPlayer: Boolean = false,
    val hiddenCategories: List<String> = emptyList(),
    val catalogMaxAgeHours: Int = 12,
    val decoderMode: String = "auto",
)
