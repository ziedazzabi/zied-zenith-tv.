package com.zenith.iptv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.zenith.iptv.ui.DeviceClass
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalDevice
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Z
import kotlin.math.roundToInt

/** A live channel that keeps playing in a small floating window after leaving the full-screen player. */
class MiniState(val player: ExoPlayer, val request: PlayRequest, val title: String)

object MiniPlayer {
    var state by mutableStateOf<MiniState?>(null)
        private set

    /** Set by the player just before it closes, so it hands its player over instead of releasing it. */
    var handoff = false

    fun receive(player: ExoPlayer, request: PlayRequest, title: String) {
        handoff = false
        state?.let { if (it.player !== player) it.player.release() }
        state = MiniState(player, request, title)
    }

    /** Called by a new full-screen player: returns the running player if it's the same channel. */
    fun takeFor(req: PlayRequest): ExoPlayer? {
        val s = state ?: return null
        state = null
        val same = req.urlOverride == null && s.request.current?.key == req.current?.key
        return if (same) s.player else { s.player.release(); null }
    }

    fun close() {
        state?.player?.release()
        state = null
    }
}

/** Floating mini window drawn above every screen. [onExpand] returns to full screen. */
@OptIn(UnstableApi::class)
@Composable
fun MiniPlayerOverlay(visible: Boolean, onExpand: (PlayRequest) -> Unit) {
    val s = MiniPlayer.state ?: return
    if (!visible) return
    val isTv = LocalIsTv.current
    val width = when (LocalDevice.current) { DeviceClass.PHONE -> 210.dp; DeviceClass.TABLET -> 300.dp; DeviceClass.TV -> 340.dp }
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var buffering by remember(s) { mutableStateOf(s.player.playbackState == Player.STATE_BUFFERING) }

    // pause when the app goes to the background, resume when it comes back
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(s, lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_STOP) s.player.pause()
            if (e == Lifecycle.Event.ON_START) s.player.play()
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
        }
        lifecycle.addObserver(obs)
        s.player.addListener(listener)
        onDispose { lifecycle.removeObserver(obs); s.player.removeListener(listener) }
    }

    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxX = with(density) { (maxWidth - width - 32.dp).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - width * 9f / 16f - 32.dp).toPx() }.coerceAtLeast(0f)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
                .width(width)
                .aspectRatio(16f / 9f)
                .shadow(14.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, Z.accent, RoundedCornerShape(14.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        isFocusable = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        player = s.player
                    }
                },
                update = { v -> if (v.player !== s.player) v.player = s.player },
                onRelease = { v -> v.player = null },
                modifier = Modifier.fillMaxSize(),
            )
            if (buffering) CircularProgressIndicator(color = Z.accent, strokeWidth = 2.dp, modifier = Modifier.align(Alignment.Center).size(26.dp))

            if (isTv) {
                // On TV: focus it with the remote, OK = full screen, MENU = close
                Focusable(
                    onClick = { onExpand(s.request) }, onLongClick = { MiniPlayer.close() },
                    modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(14.dp),
                    background = Color.Transparent, focusedBackground = Color.White.copy(alpha = .08f), focusScale = 1f,
                ) { }
            } else {
                // Touch: tap = full screen, drag = move
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(s) { detectTapGestures(onTap = { onExpand(s.request) }) }
                        .pointerInput(s) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                dx = (dx + drag.x).coerceIn(-maxX, 0f)
                                dy = (dy + drag.y).coerceIn(-maxY, 0f)
                            }
                        }
                )
            }

            // title + buttons
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .75f), Color.Transparent)))
                    .padding(start = 8.dp, end = 2.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    s.title.ifBlank { s.request.current?.name ?: "" }, color = Color.White, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                if (!isTv) {
                    Icon(
                        Icons.Default.OpenInFull, null, tint = Color.White,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                            .pointerInput(s) { detectTapGestures(onTap = { onExpand(s.request) }) }.padding(7.dp),
                    )
                    Icon(
                        Icons.Default.Close, null, tint = Color.White,
                        modifier = Modifier.size(30.dp).clip(CircleShape)
                            .pointerInput(s) { detectTapGestures(onTap = { MiniPlayer.close() }) }.padding(6.dp),
                    )
                }
            }
        }
    }
}
