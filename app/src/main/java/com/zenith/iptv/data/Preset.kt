package com.zenith.iptv.data

import android.content.Context
import android.util.Log
import com.zenith.iptv.Graph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/**
 * Custom edition: servers built into the app (assets/preset.json, taken from custom/preset.json in the repo).
 * Users of that edition only type their username and password.
 * If [Preset.remoteUrl] is set, the server addresses are re-read from that file at every start, so they can be
 * changed without publishing a new version of the app.
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
    /** Online list of servers (same "servers" format) that overrides the built-in one. */
    val remoteUrl: String = "",
)

@Serializable
private data class RemoteServers(val servers: List<PresetServer> = emptyList())

object Presets {
    private const val TAG = "Presets"
    private const val PREFS = "preset_remote"
    @Volatile private var loaded = false
    @Volatile private var builtIn: Preset? = null
    private val _flow = MutableStateFlow<Preset?>(null)
    val flow: StateFlow<Preset?> = _flow
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun clean(list: List<PresetServer>) = list.filter { it.url.isNotBlank() }

    private fun load(ctx: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val b = runCatching {
                ctx.assets.open("preset.json").bufferedReader().use { Net.json.decodeFromString(Preset.serializer(), it.readText()) }
            }.getOrNull()?.let { it.copy(servers = clean(it.servers)) }?.takeIf { it.servers.isNotEmpty() }
            builtIn = b
            val cached = if (b != null) cachedServers(ctx) else null
            _flow.value = if (b != null && !cached.isNullOrEmpty()) b.copy(servers = cached) else b
            loaded = true
        }
    }

    /** The preset (with the latest known servers), or null in the standard edition. */
    fun get(ctx: Context): Preset? { load(ctx); return _flow.value }

    private fun cachedServers(ctx: Context): List<PresetServer>? = runCatching {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("servers", null) ?: return null
        clean(Net.json.decodeFromString(RemoteServers.serializer(), raw).servers).takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Reads the online server list in the background; updates saved playlists whose server moved. */
    fun refreshAsync(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch { runCatching { refresh(app) }.onFailure { Log.w(TAG, "refresh failed", it) } }
    }

    suspend fun refresh(ctx: Context) {
        load(ctx)
        val base = builtIn ?: return
        val remote = base.remoteUrl.trim()
        if (remote.isBlank()) return
        val sep = if ('?' in remote) '&' else '?'
        val text = withTimeoutOrNull(10_000) { Net.getText("$remote${sep}t=${System.currentTimeMillis() / 60_000}") } ?: return
        val servers = clean(Net.json.decodeFromString(RemoteServers.serializer(), text).servers)
        if (servers.isEmpty()) return
        val old = _flow.value?.servers.orEmpty()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("servers", Net.json.encodeToString(RemoteServers.serializer(), RemoteServers(servers))).apply()
        val next = base.copy(servers = servers)
        _flow.value = next
        migrate(next, old + base.servers)
    }

    /** Points saved playlists at the new address of their server (matched by server name). */
    private suspend fun migrate(preset: Preset, known: List<PresetServer>) {
        val store = Graph.store
        val activeId = Graph.repo.activePlaylist()?.id
        var reopen: Playlist? = null
        store.playlists.value.forEach { p ->
            val name = p.presetServer.ifBlank { known.firstOrNull { matches(it, p) }?.name.orEmpty() }
            if (name.isBlank()) return@forEach
            val s = preset.servers.firstOrNull { it.name.equals(name, true) } ?: return@forEach
            val fresh = playlist(p.id, preset, s, p.username, p.password, p.createdAt)
            val updated = p.copy(type = fresh.type, url = fresh.url, epgUrl = s.epg.ifBlank { p.epgUrl }, presetServer = s.name)
            if (updated != p) {
                store.savePlaylist(updated)
                if (updated.url != p.url && p.id == activeId) reopen = updated
            }
        }
        reopen?.let { p -> withContext(Dispatchers.Main) { Graph.repo.open(p, forceRefresh = true) } }
    }

    private fun matches(s: PresetServer, p: Playlist): Boolean {
        val u = normalize(s.url)
        return if (s.type.equals("m3u", true))
            p.url.equals(u.replace("{username}", p.username).replace("{password}", p.password), true)
        else p.url.trimEnd('/').equals(u, true)
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
            epgUrl = server.epg.trim(), createdAt = createdAt, presetServer = server.name,
        )
    }

    /** Which preset server an existing playlist belongs to (for editing). */
    fun serverIndexOf(preset: Preset, p: Playlist?): Int {
        if (p == null) return 0
        if (p.presetServer.isNotBlank()) {
            val i = preset.servers.indexOfFirst { it.name.equals(p.presetServer, true) }
            if (i >= 0) return i
        }
        return preset.servers.indexOfFirst { matches(it, p) }
    }
}
