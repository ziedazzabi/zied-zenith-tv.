package com.zenith.iptv.data.source

import android.content.Context
import android.net.Uri
import com.zenith.iptv.data.Catalog
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.Net
import com.zenith.iptv.data.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

class M3uSource(private val context: Context, override val playlist: Playlist) : Source {

    override suspend fun load(): Catalog = withContext(Dispatchers.IO) {
        val url = playlist.url.trim()
        val reader: BufferedReader = when {
            url.startsWith("content://") || url.startsWith("file://") ->
                context.contentResolver.openInputStream(Uri.parse(url))?.bufferedReader()
                    ?: throw IOException("Cannot open file")
            url.startsWith("/") -> File(url).bufferedReader()
            else -> {
                val resp = Net.client.newCall(Net.request(url, ua = playlist.userAgent)).execute()
                if (!resp.isSuccessful) { resp.close(); throw IOException("HTTP ${resp.code}") }
                val body = resp.body ?: throw IOException("Empty playlist")
                val stream = body.byteStream().buffered()
                stream.mark(4)
                val b1 = stream.read(); val b2 = stream.read()
                stream.reset()
                val input = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(stream) else stream
                BufferedReader(InputStreamReader(input, Charsets.UTF_8))
            }
        }
        reader.use { parse(it) }
    }

    private fun parse(reader: BufferedReader): Catalog {
        val cats = LinkedHashMap<String, Category>()
        val items = ArrayList<Media>(4096)
        var epgUrl = playlist.epgUrl
        var defCatchup = ""
        var defCatchupDays = 0
        var defCatchupSource = ""

        var pendingAttrs: Map<String, String>? = null
        var pendingName = ""
        var pendingGroup = ""
        var counter = 0

        var line = reader.readLine()
        while (line != null) {
            val t = line.trim()
            when {
                t.isEmpty() -> {}
                t.startsWith("#EXTM3U") -> {
                    val a = attrs(t)
                    if (epgUrl.isBlank()) epgUrl = (a["url-tvg"] ?: a["x-tvg-url"] ?: "").split(',').firstOrNull()?.trim() ?: ""
                    defCatchup = a["catchup"] ?: ""
                    defCatchupDays = a["catchup-days"]?.toIntOrNull() ?: 0
                    defCatchupSource = a["catchup-source"] ?: ""
                }
                t.startsWith("#EXTINF") -> {
                    pendingAttrs = attrs(t)
                    pendingName = nameOf(t)
                    pendingGroup = pendingAttrs["group-title"] ?: ""
                }
                t.startsWith("#EXTGRP:") -> pendingGroup = t.removePrefix("#EXTGRP:").trim()
                t.startsWith("#") -> {} // #EXTVLCOPT etc.
                else -> {
                    val a = pendingAttrs ?: emptyMap()
                    counter++
                    val kind = kindOf(t)
                    val group = pendingGroup.ifBlank { "Uncategorized" }
                    val catId = "${kind.name}:$group"
                    if (catId !in cats) cats[catId] = Category(catId, group, kind)
                    val catchup = a["catchup"] ?: a["catchup-type"] ?: defCatchup
                    val days = (a["catchup-days"] ?: a["tvg-rec"] ?: a["timeshift"])?.toIntOrNull() ?: defCatchupDays
                    items += Media(
                        id = counter.toString(),
                        playlistId = playlist.id,
                        kind = kind,
                        name = pendingName.ifBlank { a["tvg-name"] ?: "Channel $counter" },
                        categoryId = catId,
                        number = a["tvg-chno"]?.toIntOrNull() ?: counter,
                        logo = a["tvg-logo"] ?: a["logo"] ?: "",
                        url = t,
                        epgId = a["tvg-id"] ?: a["tvg-name"] ?: "",
                        catchupDays = if (catchup.isNotBlank() || days > 0) days.coerceAtLeast(if (catchup.isNotBlank()) 1 else 0) else 0,
                        catchupType = catchup,
                        catchupSource = a["catchup-source"] ?: defCatchupSource,
                    )
                    pendingAttrs = null; pendingName = ""; pendingGroup = ""
                }
            }
            line = reader.readLine()
        }
        if (items.isEmpty()) throw IOException("No channels found in this playlist")
        return Catalog(playlist.id, cats.values.toList(), items, epgUrl = epgUrl)
    }

    override suspend fun catchupUrl(item: Media, start: Long, stop: Long): String? {
        if (item.catchupDays <= 0) return null
        val type = item.catchupType.lowercase()
        val src = item.catchupSource
        return when {
            type == "append" && src.isNotBlank() -> item.url + fill(src, start, stop)
            (type == "default" || type.isBlank()) && src.isNotBlank() -> fill(src, start, stop)
            type == "shift" || type == "timeshift" -> {
                val sep = if ('?' in item.url) '&' else '?'
                "${item.url}${sep}utc=${start / 1000}&lutc=${System.currentTimeMillis() / 1000}"
            }
            type == "flussonic" || type == "fs" || type == "flussonic-hls" -> {
                val dur = (stop - start) / 1000
                val u = item.url
                val i = u.lastIndexOf('/')
                if (i < 0) null else u.substring(0, i) + "/index-${start / 1000}-$dur.m3u8"
            }
            src.isNotBlank() -> fill(src, start, stop)
            else -> null
        }
    }

    companion object {
        private val attrRegex = Regex("([\\w-]+)=\"([^\"]*)\"")
        private val movieExt = listOf(".mp4", ".mkv", ".avi", ".mov", ".m4v", ".wmv", ".webm", ".mpg")

        fun attrs(line: String): Map<String, String> =
            attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

        /** Display name is the text after the last comma that is outside quotes. */
        fun nameOf(line: String): String {
            var inQuote = false
            var idx = -1
            for (i in line.indices) {
                val c = line[i]
                if (c == '"') inQuote = !inQuote
                else if (c == ',' && !inQuote) idx = i
            }
            return if (idx >= 0) line.substring(idx + 1).trim() else ""
        }

        fun kindOf(url: String): Kind {
            val path = url.substringBefore('?').lowercase()
            return when {
                "/series/" in path -> Kind.SERIES
                "/movie/" in path || movieExt.any { path.endsWith(it) } -> Kind.MOVIE
                else -> Kind.LIVE
            }
        }

        /** Fills catch-up templates: {utc} {start} {utcend} {end} {duration} {offset} {lutc} {Y}{m}{d}{H}{M}{S} and ${...}. */
        fun fill(template: String, start: Long, stop: Long): String {
            val s = start / 1000; val e = stop / 1000; val now = System.currentTimeMillis() / 1000
            val cal = SimpleDateFormat("yyyy|MM|dd|HH|mm|ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(start)).split('|')
            var r = template.replace("\${", "{")
            val map = mapOf(
                "{utc}" to "$s", "{start}" to "$s", "{timestamp}" to "$s",
                "{utcend}" to "$e", "{end}" to "$e",
                "{lutc}" to "$now", "{now}" to "$now",
                "{duration}" to "${e - s}", "{offset}" to "${now - s}",
                "{Y}" to cal[0], "{m}" to cal[1], "{d}" to cal[2], "{H}" to cal[3], "{M}" to cal[4], "{S}" to cal[5],
            )
            map.forEach { (k, v) -> r = r.replace(k, v) }
            // {utc:YmdHMS} style
            r = Regex("\\{(utc|start|end|utcend):([^}]+)\\}").replace(r) { m ->
                val t = if (m.groupValues[1] in listOf("end", "utcend")) stop else start
                val pattern = m.groupValues[2].map { ch ->
                    when (ch) { 'Y' -> "yyyy"; 'm' -> "MM"; 'd' -> "dd"; 'H' -> "HH"; 'M' -> "mm"; 'S' -> "ss"; else -> "'$ch'" }
                }.joinToString("")
                try { SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(t)) } catch (ignored: Exception) { m.value }
            }
            return r
        }
    }
}
