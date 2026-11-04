package com.zenith.iptv.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Streaming XMLTV reader. Only keeps channels present in the playlist and programmes inside
 * a time window, so even 200 MB guides stay light in memory.
 */
object XmltvParser {

    suspend fun load(
        url: String,
        wantedIds: Set<String>,
        fromMs: Long,
        toMs: Long,
        ua: String = "",
    ): Map<String, List<EpgProgram>> = withContext(Dispatchers.IO) {
        val resp = Net.streamClient.newCall(Net.request(url, ua = ua)).execute()
        if (!resp.isSuccessful) { resp.close(); throw IOException("EPG HTTP ${resp.code}") }
        resp.use { r ->
            val raw = BufferedInputStream(r.body!!.byteStream(), 64 * 1024)
            raw.mark(4)
            val b1 = raw.read(); val b2 = raw.read()
            raw.reset()
            val input: InputStream = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw, 64 * 1024) else raw
            parse(input, wantedIds, fromMs, toMs)
        }
    }

    fun parse(input: InputStream, wantedIds: Set<String>, fromMs: Long, toMs: Long): Map<String, List<EpgProgram>> {
        val wantedLower = wantedIds.mapTo(HashSet()) { it.lowercase() }
        // display-name → id, so playlists that use tvg-name instead of tvg-id still match
        val nameToId = HashMap<String, String>()
        val out = HashMap<String, MutableList<EpgProgram>>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var event = parser.eventType
        var currentChannelId: String? = null
        var progChannel: String? = null
        var progStart = 0L
        var progStop = 0L
        var title = ""
        var desc = ""
        var tag = ""
        var allowed: Set<String>? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    tag = parser.name
                    when (tag) {
                        "channel" -> currentChannelId = parser.getAttributeValue(null, "id")
                        "display-name" -> if (currentChannelId != null) {
                            val n = safeText(parser)
                            if (n.isNotBlank()) nameToId.putIfAbsent(n.lowercase(), currentChannelId!!.lowercase())
                            tag = ""
                        }
                        "programme" -> {
                            progChannel = parser.getAttributeValue(null, "channel")?.lowercase()
                            progStart = parseTime(parser.getAttributeValue(null, "start"))
                            progStop = parseTime(parser.getAttributeValue(null, "stop"))
                            title = ""; desc = ""
                        }
                        "title" -> if (progChannel != null && title.isEmpty()) { title = safeText(parser); tag = "" }
                        "desc" -> if (progChannel != null && desc.isEmpty()) { desc = safeText(parser); tag = "" }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "channel" -> currentChannelId = null
                        "programme" -> {
                            val ch = progChannel
                            if (allowed == null) {
                                allowed = if (wantedLower.isEmpty()) emptySet()
                                else wantedLower + wantedLower.mapNotNull { nameToId[it] }
                            }
                            val ok = allowed!!.isEmpty() || (ch != null && ch in allowed!!)
                            if (ok && ch != null && progStop > fromMs && progStart < toMs) {
                                out.getOrPut(ch) { mutableListOf() } += EpgProgram(progStart, progStop, title, desc)
                            }
                            progChannel = null
                        }
                    }
                }
            }
            event = try { parser.next() } catch (e: Exception) { break }
        }

        // Re-key by every name the playlist might use
        val result = HashMap<String, List<EpgProgram>>()
        for ((id, list) in out) {
            val sorted = list.sortedBy { it.start }
            result[id] = sorted
        }
        for ((name, id) in nameToId) {
            if (name in wantedLower && name !in result) result[id]?.let { result[name] = it }
        }
        return result
    }

    private fun safeText(p: XmlPullParser): String = try { p.nextText().trim() } catch (ignored: Exception) { "" }

    private val fmtTz = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val fmtNoTz = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)

    fun parseTime(v: String?): Long {
        if (v.isNullOrBlank()) return 0L
        val s = v.trim()
        return try {
            if (s.length > 14) synchronized(fmtTz) { fmtTz.parse(s)?.time ?: 0L }
            else synchronized(fmtNoTz) { fmtNoTz.parse(s.padEnd(14, '0'))?.time ?: 0L }
        } catch (ignored: Exception) { 0L }
    }
}
