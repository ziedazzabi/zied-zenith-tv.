package com.zenith.iptv.data.source

import com.zenith.iptv.data.Catalog
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Episode
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.MediaDetails
import com.zenith.iptv.data.Net
import com.zenith.iptv.data.Playlist
import com.zenith.iptv.data.arr
import com.zenith.iptv.data.child
import com.zenith.iptv.data.int
import com.zenith.iptv.data.long
import com.zenith.iptv.data.obj
import com.zenith.iptv.data.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** MAG / Stalker middleware portal, logged in with a MAC address. */
class StalkerSource(override val playlist: Playlist) : Source {

    private val root: String
    private var endpoint: String
    private val mac = playlist.mac.trim().uppercase()
    private var token: String = ""
    private val lock = Mutex()

    init {
        var u = playlist.url.trim()
        if (!u.startsWith("http")) u = "http://$u"
        u = u.trimEnd('/')
        root = when {
            "/stalker_portal" in u -> u.substringBefore("/stalker_portal")
            else -> u.removeSuffix("/c").substringBefore("/portal.php").substringBefore("/server/load.php")
        }
        endpoint = if ("/stalker_portal" in u) "$root/stalker_portal/server/load.php" else "$root/portal.php"
    }

    private val stbUa = playlist.userAgent.ifBlank {
        "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"
    }

    private fun headers(): Map<String, String> {
        val h = mutableMapOf(
            "X-User-Agent" to "Model: MAG250; Link: WiFi",
            "Cookie" to "mac=${URLEncoder.encode(mac, "UTF-8")}; stb_lang=en; timezone=GMT",
            "Referer" to "$root/c/",
            "Accept" to "*/*",
        )
        if (token.isNotEmpty()) h["Authorization"] = "Bearer $token"
        return h
    }

    override fun playbackHeaders(): Map<String, String> = mapOf("User-Agent" to stbUa) + headers()

    private suspend fun call(query: String, retry: Boolean = true): JsonElement? {
        ensureToken()
        val url = "$endpoint?$query&JsHttpRequest=1-xml"
        val res = try { Net.getJson(url, headers(), stbUa) } catch (e: IOException) {
            if (retry) { token = ""; return call(query, false) } else throw e
        }
        val js = res.obj()?.get("js")
        if ((js == null || (js is JsonPrimitive && js.content == "false")) && retry) {
            token = ""
            return call(query, false)
        }
        return js
    }

    private suspend fun ensureToken(): Unit = lock.withLock<Unit> {
        if (token.isNotEmpty()) return@withLock
        var hs = tryHandshake(endpoint)
        if (hs == null && endpoint.endsWith("/portal.php")) {
            val alt = "$root/stalker_portal/server/load.php"
            hs = tryHandshake(alt)
            if (hs != null) endpoint = alt
        }
        token = hs ?: throw IOException("Portal handshake failed — check the portal URL and MAC address")
        // Profile call registers the device; failures here are not fatal.
        try {
            val sn = md5(mac).take(13).uppercase()
            val devId = sha256(mac).uppercase()
            Net.getJson(
                "$endpoint?type=stb&action=get_profile&hd=1&ver=ImageDescription:%200.2.18-r23-250;&num_banks=2&sn=$sn" +
                    "&stb_type=MAG250&image_version=218&device_id=$devId&device_id2=$devId&signature=&auth_second_step=1" +
                    "&hw_version=1.7-BD-00&not_valid_token=0&JsHttpRequest=1-xml",
                headers(), stbUa,
            )
        } catch (ignored: Exception) {}
    }

    private suspend fun tryHandshake(ep: String): String? = try {
        val r = Net.getJson("$ep?type=stb&action=handshake&token=&prehash=0&JsHttpRequest=1-xml", headers(), stbUa)
        r.obj()?.child("js").obj().str("token").ifBlank { null }
    } catch (ignored: Exception) { null }

    override suspend fun load(): Catalog = coroutineScope {
        ensureToken()
        val genres = async { call("type=itv&action=get_genres") }
        val channels = async { call("type=itv&action=get_all_channels") }
        val vodCats = async { runCatching { call("type=vod&action=get_categories") }.getOrNull() }
        val serCats = async { runCatching { call("type=series&action=get_categories") }.getOrNull() }
        val account = async { runCatching { call("type=account_info&action=get_main_info") }.getOrNull() }

        val categories = ArrayList<Category>()
        genres.await().arr()?.forEach { g ->
            val o = g.obj() ?: return@forEach
            val id = o.str("id")
            if (id == "*") return@forEach
            categories += Category(id, o.str("title"), Kind.LIVE)
        }
        vodCats.await().arr()?.forEach { g ->
            val o = g.obj() ?: return@forEach
            if (o.str("id") == "*") return@forEach
            categories += Category("vod:" + o.str("id"), o.str("title"), Kind.MOVIE, lazy = true)
        }
        serCats.await().arr()?.forEach { g ->
            val o = g.obj() ?: return@forEach
            if (o.str("id") == "*") return@forEach
            categories += Category("series:" + o.str("id"), o.str("title"), Kind.SERIES, lazy = true)
        }

        val items = ArrayList<Media>()
        val data = channels.await().obj()?.child("data").arr()
        data?.forEachIndexed { i, el ->
            val o = el.obj() ?: return@forEachIndexed
            items += Media(
                id = o.str("id"), playlistId = playlist.id, kind = Kind.LIVE,
                name = o.str("name"), categoryId = o.str("tv_genre_id"),
                number = o.int("number").takeIf { it > 0 } ?: (i + 1),
                logo = fixLogo(o.str("logo")), cmd = o.str("cmd"),
                epgId = o.str("xmltv_id"),
                catchupDays = if (o.str("tv_archive") == "1" || o.int("archive") == 1)
                    (o.int("tv_archive_duration") / 24).coerceAtLeast(1) else 0,
                catchupType = "stalker",
            )
        }
        val acc = account.await().obj()
        val info = if (acc != null) "Expires: ${acc.str("phone").ifBlank { acc.str("end_date") }.ifBlank { "—" }}" else "MAC $mac"
        Catalog(playlist.id, categories, items, accountInfo = info, epgUrl = playlist.epgUrl)
    }

    override suspend fun loadCategory(category: Category, page: Int): List<Media> {
        val (type, cat) = category.id.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        val kind = if (type == "series") Kind.SERIES else Kind.MOVIE
        val js = call("type=$type&action=get_ordered_list&category=$cat&genre=$cat&sortby=added&p=$page").obj()
        return js?.child("data").arr()?.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val seriesList = o.child("series").arr()?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            Media(
                id = o.str("id"), playlistId = playlist.id,
                kind = if (seriesList.isNotEmpty()) Kind.SERIES else kind,
                name = o.str("name"), categoryId = category.id,
                logo = fixLogo(o.str("screenshot_uri").ifBlank { o.str("cover_big") }),
                cmd = o.str("cmd"), rating = o.str("rating_imdb").ifBlank { o.str("rating_kinopoisk") },
                year = o.str("year"), plot = o.str("description"),
                ext = seriesList.joinToString(","), // episode numbers for MAG-style series
            )
        } ?: emptyList()
    }

    override suspend fun details(item: Media): MediaDetails {
        if (item.kind != Kind.SERIES) return MediaDetails(plot = item.plot, rating = item.rating)
        val numbers = item.ext.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (numbers.isNotEmpty()) {
            val eps = numbers.map { n -> Episode(id = "$n", title = "Episode $n", season = 1, number = n, cmd = item.cmd) }
            return MediaDetails(plot = item.plot, rating = item.rating, seasons = mapOf(1 to eps))
        }
        // Newer portals: seasons are separate entries under the movie id
        val js = call("type=series&action=get_ordered_list&movie_id=${enc(item.id)}&season_id=0&episode_id=0").obj()
        val seasons = linkedMapOf<Int, List<Episode>>()
        js?.child("data").arr()?.forEachIndexed { si, el ->
            val s = el.obj() ?: return@forEachIndexed
            val sNum = s.int("season_number").takeIf { it > 0 } ?: (si + 1)
            val epNums = s.child("series").arr()?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() } ?: emptyList()
            seasons[sNum] = epNums.map { n -> Episode("$n", "Episode $n", sNum, n, cmd = s.str("cmd")) }
        }
        return MediaDetails(plot = item.plot, rating = item.rating, seasons = seasons)
    }

    private suspend fun createLink(type: String, cmd: String, series: Int? = null): String {
        val extra = if (series != null) "&series=$series" else ""
        val js = call("type=$type&action=create_link&cmd=${enc(cmd)}$extra&forced_storage=undefined&disable_ad=0").obj()
        val link = js.str("cmd").ifBlank { cmd }
        return cleanCmd(link)
    }

    override suspend fun resolve(item: Media): String = when (item.kind) {
        Kind.LIVE -> createLink("itv", item.cmd)
        else -> createLink("vod", item.cmd)
    }

    override suspend fun resolveEpisode(series: Media, episode: Episode): String =
        createLink("vod", episode.cmd.ifBlank { series.cmd }, episode.number)

    override suspend fun shortEpg(item: Media): List<EpgProgram> {
        val js = call("type=itv&action=get_short_epg&ch_id=${item.id}&size=10")
        return js.arr()?.mapNotNull { el ->
            val o = el.obj() ?: return@mapNotNull null
            val start = o.long("start_timestamp") * 1000
            val stop = o.long("stop_timestamp") * 1000
            if (start == 0L) null else EpgProgram(start, stop, o.str("name"), o.str("descr"))
        } ?: emptyList()
    }

    override suspend fun catchupUrl(item: Media, start: Long, stop: Long): String? {
        if (item.catchupDays <= 0) return null
        val live = resolve(item)
        // Most portals accept flussonic/xtream style archive params on the live link
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        val minutes = ((stop - start) / 60000).coerceAtLeast(1)
        return if ("/live/" in live) {
            live.replace("/live/", "/timeshift/").let { u ->
                val parts = u.substringAfter("/timeshift/").split('/')
                if (parts.size >= 3) u.substringBefore("/timeshift/") + "/timeshift/${parts[0]}/${parts[1]}/$minutes/${fmt.format(Date(start))}/${parts[2].substringBefore('.')}.ts" else u
            }
        } else {
            val sep = if ('?' in live) '&' else '?'
            "$live${sep}utc=${start / 1000}&lutc=${System.currentTimeMillis() / 1000}"
        }
    }

    private fun fixLogo(l: String): String = when {
        l.isBlank() -> ""
        l.startsWith("http") -> l
        l.startsWith("/") -> root + l
        else -> "$root/stalker_portal/misc/logos/320/$l"
    }

    companion object {
        fun cleanCmd(c: String): String = c.trim().removePrefix("ffmpeg ").removePrefix("ffrt ").removePrefix("auto ").trim()
        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
        private fun md5(s: String) = hash("MD5", s)
        private fun sha256(s: String) = hash("SHA-256", s)
        private fun hash(alg: String, s: String) = MessageDigest.getInstance(alg).digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

private fun JsonObject.has(k: String) = containsKey(k)
