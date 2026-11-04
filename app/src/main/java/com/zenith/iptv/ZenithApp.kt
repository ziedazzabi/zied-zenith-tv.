package com.zenith.iptv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.zenith.iptv.data.Repository
import com.zenith.iptv.data.Store

class ZenithApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        Graph.init(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REMINDERS, getString(R.string.reminders), NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_RECORDING, getString(R.string.recordings), NotificationManager.IMPORTANCE_LOW))
    }

    companion object {
        const val CH_REMINDERS = "reminders"
        const val CH_RECORDING = "recording"
    }
}

/** Tiny manual dependency container. */
object Graph {
    lateinit var store: Store
        private set
    lateinit var repo: Repository
        private set
    lateinit var app: Context
        private set

    fun init(context: Context) {
        if (::store.isInitialized) return
        app = context.applicationContext
        store = Store(app)
        repo = Repository(app, store)
    }
}
