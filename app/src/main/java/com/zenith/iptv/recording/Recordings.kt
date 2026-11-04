package com.zenith.iptv.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.zenith.iptv.Graph
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.Recording
import com.zenith.iptv.data.RecordingState
import com.zenith.iptv.data.Reminder
import com.zenith.iptv.reminders.Alarms
import java.io.File

object Recordings {

    fun folder(context: Context): File =
        (context.getExternalFilesDir("Recordings") ?: File(context.filesDir, "Recordings")).apply { mkdirs() }

    private fun nextId(): Int = ((Graph.store.recordings.value.maxOfOrNull { it.id } ?: 0) + 1)

    fun recordNow(context: Context, item: Media, title: String, stopAt: Long) {
        val rec = Recording(
            id = nextId(), mediaKey = item.key, channelName = item.name, title = title,
            start = System.currentTimeMillis(), stop = stopAt.coerceAtLeast(System.currentTimeMillis() + 60_000),
            state = RecordingState.SCHEDULED,
        )
        Graph.store.updateRecordings { it + rec }
        start(context, rec.id)
    }

    fun schedule(context: Context, item: Media, p: EpgProgram) {
        val rec = Recording(
            id = nextId(), mediaKey = item.key, channelName = item.name, title = p.title,
            start = p.start - 60_000, stop = p.stop + 2 * 60_000, state = RecordingState.SCHEDULED,
        )
        Graph.store.updateRecordings { it + rec }
        Alarms.scheduleRecording(context, rec)
    }

    fun isScheduled(mediaKey: String, p: EpgProgram): Boolean = Graph.store.recordings.value.any {
        it.mediaKey == mediaKey && it.state in listOf(RecordingState.SCHEDULED, RecordingState.RECORDING) &&
            it.start <= p.start && it.stop >= p.stop
    }

    fun start(context: Context, id: Int) {
        val i = Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START).putExtra("id", id)
        ContextCompat.startForegroundService(context, i)
    }

    fun stop(context: Context, id: Int) {
        val rec = Graph.store.recordings.value.firstOrNull { it.id == id } ?: return
        if (rec.state == RecordingState.RECORDING) {
            context.startService(Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP).putExtra("id", id))
        } else if (rec.state == RecordingState.SCHEDULED) {
            Alarms.cancel(context, Alarms.REC_BASE + id)
            Graph.store.updateRecordings { list -> list.map { if (it.id == id) it.copy(state = RecordingState.CANCELLED) else it } }
        }
    }

    fun delete(context: Context, id: Int) {
        stop(context, id)
        val rec = Graph.store.recordings.value.firstOrNull { it.id == id } ?: return
        if (rec.filePath.isNotBlank()) File(rec.filePath).delete()
        Graph.store.updateRecordings { list -> list.filterNot { it.id == id } }
    }

    // ---- reminders ----
    fun addReminder(context: Context, item: Media, p: EpgProgram) {
        val store = Graph.store
        val id = (store.reminders.value.maxOfOrNull { it.id } ?: 0) + 1
        val r = Reminder(id, item.key, item.name, p.title, p.start)
        store.saveReminders(store.reminders.value.filter { it.start > System.currentTimeMillis() } + r)
        Alarms.scheduleReminder(context, r)
    }

    fun removeReminder(context: Context, mediaKey: String, start: Long) {
        val store = Graph.store
        val r = store.reminders.value.firstOrNull { it.mediaKey == mediaKey && it.start == start } ?: return
        Alarms.cancel(context, Alarms.REM_BASE + r.id)
        store.saveReminders(store.reminders.value - r)
    }

    fun hasReminder(mediaKey: String, start: Long) = Graph.store.reminders.value.any { it.mediaKey == mediaKey && it.start == start }
}
