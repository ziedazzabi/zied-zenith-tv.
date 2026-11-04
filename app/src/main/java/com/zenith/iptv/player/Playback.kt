package com.zenith.iptv.player

import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Episode
import com.zenith.iptv.data.Media

/** What the player screen should play. Passed through a holder instead of nav arguments. */
data class PlayRequest(
    val items: List<Media>,
    val index: Int = 0,
    /** Plays this URL instead of resolving the item (catch-up, episodes). */
    val urlOverride: String? = null,
    val title: String = "",
    val subtitle: String = "",
    /** Key under which VOD progress is saved; null for live. */
    val progressKey: String? = null,
    val startPositionMs: Long = 0,
    val image: String = "",
    val series: Media? = null,
    val episodes: List<Episode> = emptyList(),
    val episodeIndex: Int = -1,
    val catchupProgram: EpgProgram? = null,
) {
    val current: Media? get() = items.getOrNull(index)
    val isLive: Boolean get() = urlOverride == null && current?.kind == com.zenith.iptv.data.Kind.LIVE
}

object Playback {
    var request: PlayRequest? = null
    /** True while the full-screen player is showing (used for picture-in-picture). */
    @Volatile var active: Boolean = false
}
