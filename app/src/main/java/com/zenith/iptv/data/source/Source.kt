package com.zenith.iptv.data.source

import android.content.Context
import com.zenith.iptv.data.Catalog
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Episode
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.MediaDetails
import com.zenith.iptv.data.Playlist
import com.zenith.iptv.data.Settings
import com.zenith.iptv.data.SourceType

/** Common contract every provider type implements. */
interface Source {
    val playlist: Playlist

    /** Downloads the full catalog (categories + items, lazy categories may be empty). */
    suspend fun load(): Catalog

    /** Items of a category flagged lazy (only some portals need this). */
    suspend fun loadCategory(category: Category, page: Int = 1): List<Media> = emptyList()

    /** Final playable URL for an item (some providers create short-lived links). */
    suspend fun resolve(item: Media): String = item.url

    suspend fun resolveEpisode(series: Media, episode: Episode): String = episode.url

    suspend fun details(item: Media): MediaDetails = MediaDetails(plot = item.plot, rating = item.rating)

    /** Now/next style EPG straight from the provider, used when no XMLTV entry exists. */
    suspend fun shortEpg(item: Media): List<EpgProgram> = emptyList()

    /** Catch-up / replay URL for a past programme, or null if the channel has none. */
    suspend fun catchupUrl(item: Media, start: Long, stop: Long): String? = null

    /** Extra HTTP headers the player must send (portals check user-agent / cookies). */
    fun playbackHeaders(): Map<String, String> = emptyMap()

    companion object {
        fun create(context: Context, playlist: Playlist, settings: Settings): Source = when (playlist.type) {
            SourceType.XTREAM -> XtreamSource(playlist, settings)
            SourceType.M3U -> M3uSource(context, playlist)
            SourceType.STALKER -> StalkerSource(playlist)
        }
    }
}
