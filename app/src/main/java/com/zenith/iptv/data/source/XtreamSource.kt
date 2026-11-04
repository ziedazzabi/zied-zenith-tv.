package com.zenith.iptv.data.source

import android.util.Base64
import com.zenith.iptv.data.Catalog
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Episode
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.MediaDetails
import com.zenith.iptv.data.Net
import com.zenith.iptv.data.Playlist
import com.zenith.iptv.data.Settings
import com.zenith.iptv.data.arr
import com.zenith.iptv.data.child
import com.zenith.iptv.data.int
import com.zenith.iptv.data.long
import com.zenith.iptv.data.obj
import com.zenith.iptv.data.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class XtreamSource(override val playlist: Playlist, private val settings: Settings) : Source {

    var timezone: String = ""

    private val base: String = normalizeBase(playlist.url)
    private val user = enc(playlist.username)
    private val pass = enc(playlist.password)
    private val ua get() = playlist.userAgent.ifBlank { settings.userAgent }

    private fun api(action: String = "", extra: String = ""): String {
        val sb = StringBuilder("$base/player_api.php?username=$user&password=$pass")
        if (action.isNotEmpty()) sb.append("&action=").append(action)
        if (extra.isNotEmpty()) sb.append('&').append(extra)
        return sb.toString()
    }

    private suspend fun get(url: String): JsonElement = Net.getJson(url, ua = ua)

    override suspend fun load(): Catalog = coroutineScope {
        val auth = get(api()).obj() ?: throw IOException("Server did not answer")
        val ui = auth.child("user_info").obj()
        if (ui == null || ui.str("auth") == "0") throw IOException("Login failed — check server, username and password")
        val si = auth.child("server_info").obj()
        timezone = si.str("timezone")

        val liveCats = async { get(api("get_live_categories")) }
        val vodCats = async { get(api("get_vod_categories")) }
        val serCats = async { get(api("get_series_categories")) }
        val live = async { get(api("get_live_streams")) }
        val vod = async { get(api("get_vod_streams")) }
        val series = async { get(api("get_series")) }

        val categories = ArrayList<Category>()
        fun addCats(e: JsonElement, kind: Kind) {
            e.arr()?.forEach { c ->
                val o = c.obj() ?: return@forEach
                categories += Category(o.str("category_id"), o.str("category_name").ifBlank { "—" }, kind)
            }
        }
        addCats(liveCats.await(), Kind.LIVE)
        addCats(vodCats.await(), Kind.MOVIE)
        addCats(serCats.await(), Kind.SERIES)

        val items = ArrayList<Media>(4096)
        val ext = if (settings.liveFormat == "m3u8") "m3u8" else "ts"
        live.await().arr()?.forEachIndexed { i, el ->
            val o = el.obj() ?: return@forEachIndexed
            val id = o.str("stream_id")
            items += Media(
                id = id, playlistId = playlist.id, kind = Kind.LIVE,
                name = o.str("name").trim(),
                categoryId = o.str("category_id"),
                number = o.int("num").takeIf { it > 0 } ?: (i + 1),
                logo = o.str("stream_icon"),
                url = "$base/live/$user/$pass/$id.$ext",
                epgId = o.str("epg_channel_id"),
                catchupDays = if (o.str("tv_archive") == "1") o.int("tv_archive_duration").coerceAtLeast(1) else 0,
                catchupType = "xtream",
                added = o.long("added"),
            )
        }
        vod.await().arr()?.forEach { el ->
            val o = el.obj() ?: return@forEach
            val id = o.str("stream_id")
            val cext = o.str("container_extension").ifBlank { "mp4" }
            items += Media(
                id = id, playlistId = playlist.id, kind = Kind.MOVIE,
                name = o.str("name").trim(), categoryId = o.str("category_id"),
                logo = o.str("stream_icon"), url = "$base/movie/$user/$pass/$id.$cext",
                rating = o.str("rating"), year = o.str("year"), plot = o.str("plot"),
                ext = cext, added = o.long("added"),
            )
        }
        series.await().arr()?.forEach { el ->
            val o = el.obj() ?: return@forEach
            items += Media(
                id = o.str("series_id"), playlistId = playlist.id, kind = Kind.SERIES,
                name = o.str("name").trim(), categoryId = o.str("category_id"),
                logo = o.str("cover"), rating = o.str("rating"), plot = o.str("plot"),
                year = o.str("releaseDate").ifBlank { o.str("release_date") }.take(4),
                added = o.long("last_modified"),
            )
        }

        val exp = ui.long("exp_date")
        val expTxt = if (exp > 0) SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(exp * 1000)) else "∞"
        val info = "Status: ${ui.str("status")} · Expires: $expTxt · Connections: ${ui.str("active_cons")}/${ui.str("max_connections")}"

        Catalog(
            playlistId = playlist.id, categories = categories, items = items, accountInfo = info,
            epgUrl = playlist.epgUrl.ifBlank { "$base/xmltv.php?username=$user&password=$pass" },
            serverTimezone = timezone,
        )
    }

    override suspend fun details(item: Media): MediaDetails {
        return when (item.kind) {
            Kind.MOVIE -> {
                val o = get(api("get_vod_info", "vod_id=${item.id}")).obj()
                val info = o.child("info").obj()
                infoToDetails(info, item)
            }
            Kind.SERIES -> {
                val o = get(api("get_series_info", "series_id=${item.id}")).obj()
                val info = o.child("info").obj()
                val seasons = sortedMapOf<Int, MutableList<Episode>>()
                val eps = o.child("episodes")
                val groups: List<Pair<String, JsonElement>> = when (eps) {
                    is JsonObject -> eps.entries.map { it.key to it.value }
                    else -> eps.arr()?.mapIndexed { i, e -> (i + 1).toString() to e } ?: emptyList()
                }
                for ((seasonKey, list) in groups) {
                    list.arr()?.forEach { el ->
                        val e = el.obj() ?: return@forEach
                        val season = e.int("season").takeIf { it > 0 } ?: seasonKey.toIntOrNull() ?: 1
                        val eid = e.str("id")
                        val cext = e.str("container_extension").ifBlank { "mp4" }
                        val einfo = e.child("info").obj()
                        seasons.getOrPut(season) { mutableListOf() } += Episode(
                            id = eid,
                            title = e.str("title"),
                            season = season,
                            number = e.int("episode_num"),
                            url = "$base/series/$user/$pass/$eid.$cext",
                            cover = einfo.str("movie_image"),
                            plot = einfo.str("plot"),
                            duration = einfo.str("duration"),
                        )
                    }
                }
                infoToDetails(info, item).copy(seasons = seasons.mapValues { (_, v) -> v.sortedBy { it.number } })
            }
            Kind.LIVE -> MediaDetails()
        }
    }

    private fun infoToDetails(info: JsonObject?, item: Media): MediaDetails {
        val backdrop = info?.get("backdrop_path")?.arr()?.firstOrNull()?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: ""
        return MediaDetails(
            plot = info.str("plot").ifBlank { info.str("description") }.ifBlank { item.plot },
            cast = info.str("cast").ifBlank { info.str("actors") },
            director = info.str("director"),
            genre = info.str("genre"),
            releaseDate = info.str("releasedate").ifBlank { info.str("releaseDate") },
            duration = info.str("duration"),
            rating = info.str("rating").ifBlank { item.rating },
            backdrop = backdrop,
            trailer = info.str("youtube_trailer"),
        )
    }

    override suspend fun shortEpg(item: Media): List<EpgProgram> {
        if (item.kind != Kind.LIVE) return emptyList()
        val o = get(api("get_short_epg", "stream_id=${item.id}&limit=12")).obj()
        return o.child("epg_listings").arr()?.mapNotNull { el ->
            val e = el.obj() ?: return@mapNotNull null
            val start = e.long("start_timestamp") * 1000
            val stop = e.long("stop_timestamp") * 1000
            if (start == 0L) return@mapNotNull null
            EpgProgram(start, stop, b64(e.str("title")), b64(e.str("description")))
        } ?: emptyList()
    }

    override suspend fun catchupUrl(item: Media, start: Long, stop: Long): String? {
        if (item.catchupDays <= 0) return null
        val fmt = SimpleDateFormat("yyyy-MM-dd:HH-mm", Locale.US)
        if (timezone.isNotBlank()) fmt.timeZone = TimeZone.getTimeZone(timezone)
        val minutes = ((stop - start) / 60000).coerceAtLeast(1)
        return "$base/timeshift/$user/$pass/$minutes/${fmt.format(Date(start))}/${item.id}.ts"
    }

    companion object {
        fun normalizeBase(raw: String): String {
            var u = raw.trim()
            if (!u.startsWith("http://") && !u.startsWith("https://")) u = "http://$u"
            u = u.substringBefore("/player_api.php").substringBefore("/get.php").substringBefore("/c/")
            return u.trimEnd('/')
        }

        private fun enc(s: String) = URLEncoder.encode(s.trim(), "UTF-8")

        private fun b64(s: String): String = try {
            String(Base64.decode(s, Base64.DEFAULT), Charsets.UTF_8)
        } catch (ignored: Exception) { s }
    }
}
