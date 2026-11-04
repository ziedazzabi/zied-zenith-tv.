package com.zenith.iptv.recording

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.zenith.iptv.Graph
import com.zenith.iptv.MainActivity
import com.zenith.iptv.R
import com.zenith.iptv.ZenithApp
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Net
import com.zenith.iptv.data.Recording
import com.zenith.iptv.data.RecordingState
import com.zenith.iptv.data.source.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Records live streams (raw TS or HLS segments) to MPEG-TS files. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Int, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Graph.init(this)
        goForeground()
        val id = intent?.getIntExtra("id", -1) ?: -1
        when (intent?.action) {
            ACTION_START -> if (id >= 0 && !jobs.containsKey(id)) jobs[id] = scope.launch { runRecording(id) }.also { it.invokeOnCompletion { _ -> jobs.remove(id); stopIfIdle() } }
            ACTION_STOP -> jobs[id]?.cancel() ?: stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun goForeground() {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, ZenithApp.CH_RECORDING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.recording_in_progress))
            .setContentText(Graph.store.recordings.value.filter { it.state == RecordingState.RECORDING }.joinToString { it.channelName })
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    private fun stopIfIdle() {
        if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun update(id: Int, block: (Recording) -> Recording) =
        Graph.store.updateRecordings { list -> list.map { if (it.id == id) block(it) else it } }

    private suspend fun runRecording(id: Int) {
        val rec = Graph.store.recordings.value.firstOrNull { it.id == id } ?: return
        val (playlistId, _, itemId) = rec.mediaKey.split('|').let { Triple(it.getOrElse(0) { "" }, it.getOrElse(1) { "" }, it.getOrElse(2) { "" }) }
        val playlist = Graph.store.playlists.value.firstOrNull { it.id == playlistId }
        if (playlist == null) { update(id) { it.copy(state = RecordingState.FAILED, error = "Playlist removed") }; return }

        val repo = Graph.repo
        val item = repo.byKey(rec.mediaKey) ?: Graph.store.loadCatalog(playlistId)?.items?.firstOrNull { it.kind == Kind.LIVE && it.id == itemId }
        if (item == null) { update(id) { it.copy(state = RecordingState.FAILED, error = "Channel not found") }; return }

        val source = if (repo.activePlaylist()?.id == playlistId && repo.source != null) repo.source!! else Source.create(this, playlist, Graph.store.settings.value)
        val safe = (rec.channelName + "_" + rec.title).replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(80)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        val file = File(Recordings.folder(this), "${stamp}_$safe.ts")
        update(id) { it.copy(state = RecordingState.RECORDING, filePath = file.absolutePath, start = System.currentTimeMillis()) }
        goForeground()

        val headers = source.playbackHeaders().filterKeys { !it.equals("User-Agent", true) }
        val ua = source.playbackHeaders()["User-Agent"] ?: repo.userAgent()
        var failures = 0
        try {
            FileOutputStream(file, true).use { out ->
                while (scope.isActive && System.currentTimeMillis() < rec.stop && failures < 8) {
                    try {
                        val url = source.resolve(item)
                        if (url.contains(".m3u8", true)) recordHls(url, headers, ua, out, rec.stop, id)
                        else recordRaw(url, headers, ua, out, rec.stop, id)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failures++
                        delay(3000)
                    }
                }
            }
            val size = file.length()
            update(id) { it.copy(state = if (size > 0) RecordingState.DONE else RecordingState.FAILED, sizeBytes = size, stop = System.currentTimeMillis(), error = if (size > 0) "" else "No data received") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            val size = file.length()
            update(id) { it.copy(state = if (size > 0) RecordingState.DONE else RecordingState.CANCELLED, sizeBytes = size, stop = System.currentTimeMillis()) }
        } catch (e: Exception) {
            update(id) { it.copy(state = RecordingState.FAILED, error = e.message ?: "Error", sizeBytes = file.length()) }
        }
    }

    private suspend fun recordRaw(url: String, headers: Map<String, String>, ua: String, out: OutputStream, stopAt: Long, id: Int) {
        val call = Net.streamClient.newCall(Net.request(url, headers, ua))
        val resp = call.execute()
        resp.use { r ->
            if (!r.isSuccessful) throw java.io.IOException("HTTP ${r.code}")
            val input = r.body!!.byteStream()
            val buf = ByteArray(64 * 1024)
            var written = 0L
            var lastUpdate = System.currentTimeMillis()
            while (System.currentTimeMillis() < stopAt) {
                if (!scope.isActive || jobs[id]?.isCancelled == true) { call.cancel(); throw kotlinx.coroutines.CancellationException() }
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                written += n
                if (System.currentTimeMillis() - lastUpdate > 5000) {
                    lastUpdate = System.currentTimeMillis()
                    val w = written
                    update(id) { it.copy(sizeBytes = it.sizeBytes + w) }
                    written = 0
                }
            }
        }
    }

    private suspend fun recordHls(url: String, headers: Map<String, String>, ua: String, out: OutputStream, stopAt: Long, id: Int) {
        var mediaUrl = url
        var text = Net.getText(mediaUrl, headers, ua)
        if ("#EXT-X-STREAM-INF" in text) {
            // master playlist: take the highest bandwidth variant
            val lines = text.lines()
            var best = -1L; var bestUri: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val uri = lines.getOrNull(i + 1)?.trim()
                    if (uri != null && !uri.startsWith("#") && bw > best) { best = bw; bestUri = uri }
                }
            }
            mediaUrl = resolveUri(mediaUrl, bestUri ?: throw java.io.IOException("Empty master playlist"))
            text = Net.getText(mediaUrl, headers, ua)
        }
        val seen = LinkedHashSet<String>()
        while (System.currentTimeMillis() < stopAt) {
            if (jobs[id]?.isCancelled == true) throw kotlinx.coroutines.CancellationException()
            val target = Regex("#EXT-X-TARGETDURATION:(\\d+)").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 6L
            val segs = text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            for (s in segs) {
                val abs = resolveUri(mediaUrl, s)
                if (!seen.add(abs)) continue
                val bytes = Net.getBytes(abs, headers, ua)
                out.write(bytes)
                update(id) { it.copy(sizeBytes = it.sizeBytes + bytes.size) }
                if (System.currentTimeMillis() >= stopAt) return
            }
            while (seen.size > 500) seen.remove(seen.first())
            if ("#EXT-X-ENDLIST" in text) return
            delay((target * 1000L / 2).coerceIn(1000L, 6000L))
            text = Net.getText(mediaUrl, headers, ua)
        }
    }

    private fun resolveUri(base: String, rel: String): String =
        if (rel.startsWith("http")) rel else base.toHttpUrlOrNull()?.resolve(rel)?.toString() ?: rel

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val NOTIF_ID = 4242
    }
}
