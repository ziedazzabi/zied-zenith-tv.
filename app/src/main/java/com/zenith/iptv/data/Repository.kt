package com.zenith.iptv.data

import android.content.Context
import com.zenith.iptv.data.source.Source
import com.zenith.iptv.data.source.XtreamSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

sealed interface LoadState {
    data object Idle : LoadState
    data class Loading(val message: String) : LoadState
    data object Ready : LoadState
    data class Error(val message: String) : LoadState
}

/** Holds the active playlist's catalog, EPG and lookup indexes. */
class Repository(private val context: Context, val store: Store) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<LoadState>(LoadState.Idle)
    val state: StateFlow<LoadState> = _state

    private val _catalog = MutableStateFlow<Catalog?>(null)
    val catalog: StateFlow<Catalog?> = _catalog

    private val _epg = MutableStateFlow<Map<String, List<EpgProgram>>>(emptyMap())
    val epg: StateFlow<Map<String, List<EpgProgram>>> = _epg

    private val _epgState = MutableStateFlow<LoadState>(LoadState.Idle)
    val epgState: StateFlow<LoadState> = _epgState

    /** Bumped whenever lazily fetched data (short EPG, lazy categories) changes, so UI recomposes. */
    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    var source: Source? = null
        private set

    private var byKey: Map<String, Media> = emptyMap()
    private var byCategory: Map<String, List<Media>> = emptyMap()
    private val lazyItems = ConcurrentHashMap<String, List<Media>>()
    private val shortEpg = ConcurrentHashMap<String, List<EpgProgram>>()
    private val shortEpgInFlight = ConcurrentHashMap.newKeySet<String>()
    private val unlocked = HashSet<String>()
    private var loadJob: Job? = null

    val settings get() = store.settings.value

    fun activePlaylist(): Playlist? {
        val all = store.playlists.value
        return all.firstOrNull { it.id == settings.activePlaylistId } ?: all.firstOrNull()
    }

    /** Opens a playlist: shows the cached catalog instantly, refreshes in background when stale. */
    fun open(playlist: Playlist, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        store.updateSettings { it.copy(activePlaylistId = playlist.id) }
        val src = Source.create(context, playlist, settings)
        source = src
        lazyItems.clear(); shortEpg.clear(); unlocked.clear()
        _epg.value = emptyMap()
        loadJob = scope.launch {
            val cached = if (forceRefresh) null else store.loadCatalog(playlist.id)
            if (cached != null) {
                setCatalog(cached)
                _state.value = LoadState.Ready
                val ageH = (System.currentTimeMillis() - cached.loadedAt) / 3_600_000
                if (ageH >= settings.catalogMaxAgeHours) refresh(src, silent = true)
            } else {
                _catalog.value = null
                refresh(src, silent = false)
            }
            loadEpg()
        }
    }

    fun reload() { activePlaylist()?.let { open(it, forceRefresh = true) } }

    private suspend fun refresh(src: Source, silent: Boolean) {
        if (!silent) _state.value = LoadState.Loading(src.playlist.name)
        try {
            val c = withContext(Dispatchers.IO) { src.load() }
            setCatalog(c)
            store.saveCatalog(c)
            _state.value = LoadState.Ready
        } catch (e: OutOfMemoryError) {
            System.gc()
            if (!silent || _catalog.value == null) _state.value = LoadState.Error(context.getString(com.zenith.iptv.R.string.out_of_memory))
        } catch (e: Exception) {
            if (!silent || _catalog.value == null) _state.value = LoadState.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun setCatalog(c: Catalog) {
        (source as? XtreamSource)?.let { if (it.timezone.isBlank()) it.timezone = c.serverTimezone }
        byKey = c.items.associateBy { it.key }
        byCategory = c.items.groupBy { "${it.kind}:${it.categoryId}" }
        // O(1) category lookups (were a linear search per item → slow screens on big playlists)
        catByKindId = c.categories.associateBy { "${it.kind}:${it.id}" }
        catById = c.categories.associateBy { it.id }
        adultCache.clear()
        _catalog.value = c
    }

    fun loadEpg(force: Boolean = false) {
        val c = _catalog.value ?: return
        val url = c.epgUrl
        if (url.isBlank()) return
        if (!force && _epg.value.isNotEmpty()) return
        scope.launch {
            _epgState.value = LoadState.Loading("EPG")
            try {
                val ids = c.items.asSequence().filter { it.kind == Kind.LIVE }
                    .flatMap { sequenceOf(it.epgId, it.name) }.filter { it.isNotBlank() }.toSet()
                val now = System.currentTimeMillis()
                val maxCatchup = c.items.maxOfOrNull { it.catchupDays } ?: 0
                val from = now - (maxCatchup.coerceIn(1, 7)) * 86_400_000L
                val map = XmltvParser.load(url, ids, from, now + 4 * 86_400_000L, source?.playlist?.userAgent ?: "")
                _epg.value = map
                _epgState.value = LoadState.Ready
            } catch (e: Throwable) {
                if (e is OutOfMemoryError) System.gc()
                _epgState.value = LoadState.Error(e.message ?: "EPG error")
            }
        }
    }

    // ---- lookups ----
    fun byKey(key: String): Media? = byKey[key] ?: lazyItems.values.asSequence().flatten().firstOrNull { it.key == key }

    fun isLocked(cat: Category): Boolean {
        val s = settings
        val key = categoryKey(cat)
        if (key in unlocked) return false
        if (key in s.lockedCategories) return true
        return s.autoLockAdult && isAdultName(cat.name)
    }

    fun unlock(cat: Category) { unlocked += categoryKey(cat) }
    fun categoryKey(cat: Category) = "${activePlaylist()?.id}|${cat.kind}|${cat.id}"

    fun categories(kind: Kind): List<Category> {
        val c = _catalog.value ?: return emptyList()
        val kids = store.activeProfile().kids
        val hidden = settings.hiddenCategories.toSet()
        return c.categories.filter { it.kind == kind && categoryKey(it) !in hidden && !(kids && isLockedIgnoringSession(it)) }
    }

    private fun isLockedIgnoringSession(cat: Category): Boolean =
        categoryKey(cat) in settings.lockedCategories || (settings.autoLockAdult && isAdultName(cat.name))

    /** True when the item's category is locked or hidden (even if unlocked for this session). */
    fun isRestricted(item: Media): Boolean {
        val cat = categoryOf(item) ?: return false
        return isLockedIgnoringSession(cat) || categoryKey(cat) in settings.hiddenCategories
    }

    fun allCategories(kind: Kind): List<Category> = _catalog.value?.categories?.filter { it.kind == kind } ?: emptyList()

    @Volatile private var catByKindId: Map<String, Category> = emptyMap()
    @Volatile private var catById: Map<String, Category> = emptyMap()
    private val adultCache = ConcurrentHashMap<String, Boolean>()
    private fun isAdultName(name: String): Boolean = adultCache.getOrPut(name) { ADULT.containsMatchIn(name) }

    fun categoryOf(item: Media): Category? = catByKindId["${item.kind}:${item.categoryId}"] ?: catById[item.categoryId]

    fun items(kind: Kind, categoryId: String?): List<Media> {
        val c = _catalog.value ?: return emptyList()
        if (categoryId == null) {
            val visible = categories(kind).mapTo(HashSet()) { it.id }
            return c.items.filter { it.kind == kind && (it.categoryId in visible || it.categoryId.isBlank()) }
        }
        return byCategory["$kind:$categoryId"] ?: lazyItems[categoryId] ?: emptyList()
    }

    /** Items for lazy categories (portal VOD); fetched once and cached for the session. */
    suspend fun loadLazy(cat: Category): List<Media> {
        lazyItems[cat.id]?.let { return it }
        val src = source ?: return emptyList()
        val all = ArrayList<Media>()
        for (page in 1..15) {
            val p = try { src.loadCategory(cat, page) } catch (ignored: Exception) { break }
            if (p.isEmpty()) break
            all += p
            if (p.size < 10) break
        }
        lazyItems[cat.id] = all
        _tick.value++
        return all
    }

    fun channels(): List<Media> = _catalog.value?.items?.filter { it.kind == Kind.LIVE } ?: emptyList()

    fun search(q: String, limit: Int = 200): List<Media> {
        val query = q.trim().lowercase()
        if (query.length < 2) return emptyList()
        val kids = store.activeProfile().kids
        val lockedCats = if (kids) _catalog.value?.categories?.filter { isLockedIgnoringSession(it) }?.mapTo(HashSet()) { "${it.kind}:${it.id}" } ?: emptySet() else emptySet()
        val base = (_catalog.value?.items ?: emptyList()) + lazyItems.values.flatten()
        return base.asSequence()
            .filter { it.name.lowercase().contains(query) && "${it.kind}:${it.categoryId}" !in lockedCats }
            .sortedBy { if (it.name.lowercase().startsWith(query)) 0 else 1 }
            .take(limit).toList()
    }

    // ---- EPG ----
    fun programs(item: Media): List<EpgProgram> {
        val offset = settings.epgOffsetMinutes * 60_000L
        val map = _epg.value
        val list = map[item.epgId.lowercase()]?.takeIf { item.epgId.isNotBlank() }
            ?: map[item.name.lowercase()]
            ?: shortEpg[item.key]
            ?: emptyList()
        return if (offset == 0L) list else list.map { it.copy(start = it.start + offset, stop = it.stop + offset) }
    }

    fun nowNext(item: Media): Pair<EpgProgram?, EpgProgram?> {
        val now = System.currentTimeMillis()
        val list = programs(item)
        val i = list.indexOfFirst { it.isLive(now) }
        if (i < 0) {
            requestShortEpg(item)
            return null to list.firstOrNull { it.start > now }
        }
        return list[i] to list.getOrNull(i + 1)
    }

    /** Fetches provider EPG for channels the XMLTV guide does not cover. */
    fun requestShortEpg(item: Media) {
        if (item.kind != Kind.LIVE || shortEpg.containsKey(item.key) || !shortEpgInFlight.add(item.key)) return
        if (_epg.value.isNotEmpty() && _epg.value.containsKey(item.epgId.lowercase())) return
        val src = source ?: return
        scope.launch {
            val list = try { withContext(Dispatchers.IO) { src.shortEpg(item) } } catch (ignored: Exception) { emptyList() }
            shortEpg[item.key] = list
            shortEpgInFlight.remove(item.key)
            if (list.isNotEmpty()) _tick.value++
        }
    }

    // ---- playback ----
    suspend fun resolve(item: Media): String = withContext(Dispatchers.IO) { source?.resolve(item) ?: item.url }

    suspend fun catchup(item: Media, p: EpgProgram): String? = withContext(Dispatchers.IO) {
        val offset = settings.epgOffsetMinutes * 60_000L
        source?.catchupUrl(item, p.start - offset, p.stop - offset)
    }

    fun headers(): Map<String, String> = source?.playbackHeaders() ?: emptyMap()

    fun userAgent(): String = activePlaylist()?.userAgent?.ifBlank { null } ?: settings.userAgent.ifBlank { Net.DEFAULT_UA }

    companion object {
        val ADULT = Regex("(?i)(adult|xxx|18\\+|\\+18|porn|for adults|erotic|\\bsex)")
    }
}
