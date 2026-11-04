package com.zenith.iptv.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zenith.iptv.Graph
import com.zenith.iptv.MainActivity
import com.zenith.iptv.R
import com.zenith.iptv.ZenithApp
import com.zenith.iptv.data.Recording
import com.zenith.iptv.data.RecordingState
import com.zenith.iptv.data.Reminder
import com.zenith.iptv.recording.Recordings

object Alarms {
    const val REC_BASE = 100_000
    const val REM_BASE = 200_000
    const val ACTION_RECORD = "com.zenith.iptv.RECORD"
    const val ACTION_REMIND = "com.zenith.iptv.REMIND"

    private fun pending(context: Context, code: Int, action: String, id: Int): PendingIntent {
        val i = Intent(context, AlarmReceiver::class.java).setAction(action).putExtra("id", id)
        return PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun set(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun scheduleRecording(context: Context, r: Recording) {
        val at = r.start.coerceAtLeast(System.currentTimeMillis() + 1000)
        set(context, at, pending(context, REC_BASE + r.id, ACTION_RECORD, r.id))
    }

    fun scheduleReminder(context: Context, r: Reminder) {
        val at = (r.start - 60_000).coerceAtLeast(System.currentTimeMillis() + 1000)
        set(context, at, pending(context, REM_BASE + r.id, ACTION_REMIND, r.id))
    }

    fun cancel(context: Context, code: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        val action = if (code >= REM_BASE) ACTION_REMIND else ACTION_RECORD
        val i = Intent(context, AlarmReceiver::class.java).setAction(action)
        val pi = PendingIntent.getBroadcast(context, code, i, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) am.cancel(pi)
    }

    fun rescheduleAll(context: Context) {
        val now = System.currentTimeMillis()
        Graph.store.recordings.value.filter { it.state == RecordingState.SCHEDULED && it.stop > now }.forEach { scheduleRecording(context, it) }
        Graph.store.reminders.value.filter { it.start > now }.forEach { scheduleReminder(context, it) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        val id = intent.getIntExtra("id", -1)
        when (intent.action) {
            Alarms.ACTION_RECORD -> {
                val rec = Graph.store.recordings.value.firstOrNull { it.id == id } ?: return
                if (rec.state == RecordingState.SCHEDULED) Recordings.start(context, id)
            }
            Alarms.ACTION_REMIND -> {
                val r = Graph.store.reminders.value.firstOrNull { it.id == id } ?: return
                notify(context, r)
            }
        }
    }

    private fun notify(context: Context, r: Reminder) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PLAY_KEY, r.mediaKey)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, r.id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, ZenithApp.CH_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(context.getString(R.string.reminder_starting, r.channelName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(Alarms.REM_BASE + r.id, n)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Graph.init(context)
        Alarms.rescheduleAll(context)
    }
}
