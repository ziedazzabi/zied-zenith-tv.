package com.zenith.iptv

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.zenith.iptv.player.Playback
import com.zenith.iptv.ui.AppNav
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.ZenithTheme
import com.zenith.iptv.ui.isTvDevice

class MainActivity : AppCompatActivity() {

    /** Item key to open from a reminder notification or an M3U link. */
    val pendingPlayKey = mutableStateOf<String?>(null)
    val pendingM3u = mutableStateOf<String?>(null)
    val inPip = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.init(this)
        val tv = isTvDevice(this)
        if (!tv) enableEdgeToEdge()
        handleIntent(intent)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)

        setContent {
            ZenithTheme {
                CompositionLocalProvider(LocalIsTv provides tv) {
                    Box(
                        Modifier.fillMaxSize().background(Z.bg)
                            .then(if (tv || inPip.value) Modifier else Modifier.safeDrawingPadding())
                    ) {
                        AppNav(activity = this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_PLAY_KEY)?.let { pendingPlayKey.value = it }
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let { pendingM3u.value = it.toString() }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Playback.active && !isTvDevice(this) && Build.VERSION.SDK_INT >= 26 &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        ) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    companion object {
        const val EXTRA_PLAY_KEY = "play_key"
    }
}
