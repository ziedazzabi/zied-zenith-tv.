package com.zenith.iptv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.R
import com.zenith.iptv.ui.Z

/** Formats milliseconds as 1:02:03 / 4:05. */
fun fmtTime(ms: Long): String {
    val t = (ms.coerceAtLeast(0) / 1000)
    val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Controls for movies / episodes, usable with the remote and by touch:
 * seek bar (← → = 10 s), back 10 s, play / pause, forward 10 s, previous / next episode, options.
 */
@Composable
fun VodControls(
    title: String,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
    hasPrev: Boolean,
    hasNext: Boolean,
    isTv: Boolean,
    playFocus: FocusRequester,
    onToggle: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMenu: () -> Unit,
    onBack: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // title
        Row(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                .padding(horizontal = if (isTv) 40.dp else 8.dp, vertical = if (isTv) 26.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isTv) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(
                title, color = Color.White, fontSize = if (isTv) 24.sp else 17.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        // seek bar + buttons
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .9f))))
                .padding(horizontal = if (isTv) 48.dp else 16.dp, vertical = if (isTv) 28.dp else 14.dp),
        ) {
            // the time line always runs left → right (also in Arabic)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { SeekBar(positionMs, durationMs, onSeekBy, onSeekTo) }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text(fmtTime(positionMs), color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                if (durationMs > 0) Text(
                    "${fmtTime(durationMs)}  (−${fmtTime(durationMs - positionMs)})",
                    color = Z.muted, fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(if (isTv) 14.dp else 8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasPrev) CtlButton(Icons.Default.SkipPrevious, stringResource(R.string.prev_episode), onClick = onPrev)
                CtlButton(Icons.Default.Replay10, stringResource(R.string.rewind_10), onClick = { onSeekBy(-10_000) })
                CtlButton(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play),
                    big = true, focus = playFocus, onClick = onToggle,
                )
                CtlButton(Icons.Default.Forward10, stringResource(R.string.forward_10), onClick = { onSeekBy(10_000) })
                if (hasNext) CtlButton(Icons.Default.SkipNext, stringResource(R.string.next_episode), label = true, onClick = onNext)
                CtlButton(Icons.Default.Settings, stringResource(R.string.options), onClick = onMenu)
            }
        }
    }
}

@Composable
private fun CtlButton(
    icon: ImageVector, desc: String, big: Boolean = false, label: Boolean = false,
    focus: FocusRequester? = null, onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val size = if (big) 72.dp else 56.dp
    Row(
        (if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .scale(if (focused) 1.12f else 1f)
            .clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color.White.copy(alpha = .14f))
            .clickable(interactionSource = src, indication = null, onClick = onClick)
            .padding(horizontal = if (label) 18.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = if (focused) Color.Black else Color.White, modifier = Modifier.size(if (big) 42.dp else 30.dp))
        }
        if (label) Text(desc, color = if (focused) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeekBy: (Long) -> Unit, onSeekTo: (Long) -> Unit) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(28.dp)
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (ev.key) {
                    Key.DirectionLeft -> { onSeekBy(-10_000); true }
                    Key.DirectionRight -> { onSeekBy(10_000); true }
                    else -> false
                }
            }
            .focusable(interactionSource = src)
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeekTo((o.x / size.width * durationMs).toLong()) }
            }
            .pointerInput(durationMs) {
                var x = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> x = o.x },
                    onDragEnd = { if (durationMs > 0) onSeekTo((x / size.width * durationMs).toLong()) },
                    onHorizontalDrag = { _, d -> x = (x + d).coerceIn(0f, size.width.toFloat()) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val h = if (focused) 10.dp else 6.dp
        Box(Modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .25f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Z.accent))
        }
        val knob = if (focused) 22.dp else 14.dp
        Box(
            Modifier.offset(x = (maxWidth - knob) * frac).size(knob).clip(CircleShape)
                .background(if (focused) Color.White else Z.accent)
                .border(if (focused) 3.dp else 0.dp, Z.accent, CircleShape)
        )
    }
}
