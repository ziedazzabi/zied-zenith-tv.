package com.zenith.iptv.data

import android.content.Context
import kotlinx.serialization.Serializable

/**
 * Custom edition: servers built into the app (assets/preset.json, taken from custom/preset.json in the repo).
 * Users of that edition only type their username and password.
 */
@Serializable
data class PresetServer(
    val name: String = "",
    val url: String = "",
    /** "xtream" (default) or "m3u" — an m3u url may contain {username} and {password}. */
    val type: String = "xtream",
    val epg: String = "",
)

@Serializable
data class Preset(
    val appName: String = "",
    val providerName: String = "",
    val servers: List<PresetServer> = emptyList(),
    /** When true the normal "add playlist" form is still reachable (Advanced). */
    val allowOtherSources: Boolean = false,
)

object Presets {
    @Volatile private var loaded = false
    @Volatile private var value: Preset? = null

    /** The built-in preset, or null in the standard edition. */
    fun get(ctx: Context): Preset? {
        if (loaded) return value
        value = runCatching {
            ctx.assets.open("preset.json").bufferedReader().use { Net.json.decodeFromString(Preset.serializer(), it.readText()) }
        }.getOrNull()?.let { p -> p.copy(servers = p.servers.filter { it.url.isNotBlank() }) }?.takeIf { it.servers.isNotEmpty() }
        loaded = true
        return value
    }

    fun normalize(url: String): String {
        val t = url.trim().trimEnd('/')
        return if (t.contains("://")) t else "http://$t"
    }

    /** Builds the playlist for a preset server + credentials. */
    fun playlist(id: String, preset: Preset, server: PresetServer, user: String, pass: String, createdAt: Long): Playlist {
        val m3u = server.type.equals("m3u", true)
        val url = if (m3u) normalize(server.url).replace("{username}", user).replace("{password}", pass) else normalize(server.url)
        val name = listOf(preset.providerName, server.name).filter { it.isNotBlank() }.distinct().joinToString(" · ").ifBlank { user }
        return Playlist(
            id = id, name = name,
            type = if (m3u) SourceType.M3U else SourceType.XTREAM,
            url = url, username = user, password = pass,
            epgUrl = server.epg.trim(), createdAt = createdAt,
        )
    }

    /** Which preset server an existing playlist belongs to (for editing). */
    fun serverIndexOf(preset: Preset, p: Playlist?): Int {
        if (p == null) return 0
        val i = preset.servers.indexOfFirst { s ->
            val u = normalize(s.url)
            p.url.trimEnd('/').equals(u, true) || (s.type.equals("m3u", true) && p.url.startsWith(u.substringBefore('{'), true))
        }
        return if (i < 0) -1 else i
    }
}
