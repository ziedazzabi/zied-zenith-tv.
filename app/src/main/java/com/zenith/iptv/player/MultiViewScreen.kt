package com.zenith.iptv.player

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Media
import com.zenith.iptv.ui.ChannelRow
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Z
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Watch up to four live channels at once; the focused tile has sound. */
@OptIn(UnstableApi::class)
@Composable
fun MultiViewScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    var layout by remember { mutableIntStateOf(4) }
    val players = remember { List(4) { PlayerFactory.create(ctx, lowLatency = true).apply { volume = 0f } } }
    val slots = remember { mutableStateListOf<Media?>(null, null, null, null) }
    var audioSlot by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        // pre-fill with recently watched channels
        val recents = Graph.store.profileData.value.recentChannels.mapNotNull { repo.byKey(it) }.take(layout)
        recents.forEachIndexed { i, m -> slots[i] = m; scope.launch { start(players[i], m) } }
        Playback.active = true
    }
    DisposableEffect(Unit) { onDispose { players.forEach { it.release() }; Playback.active = false } }
    LaunchedEffect(audioSlot) { players.forEachIndexed { i, p -> p.volume = if (i == audioSlot) 1f else 0f } }
    LaunchedEffect(layout) { for (i in layout until 4) players[i].stop() ; for (i in 0 until layout) slots[i]?.let { if (!players[i].isPlaying) start(players[i], it) } }

    BackHandler { if (picking >= 0) picking = -1 else onBack() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Z.text) }
            Text(stringResource(R.string.multiview), color = Z.text, modifier = Modifier.weight(1f))
            Focusable(onClick = { layout = 2 }, selected = layout == 2, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.ViewColumn, stringResource(R.string.two_screens), tint = if (layout == 2) Z.accent else Z.muted, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.width(6.dp))
            Focusable(onClick = { layout = 4 }, selected = layout == 4, shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.GridView, stringResource(R.string.four_screens), tint = if (layout == 4) Z.accent else Z.muted, modifier = Modifier.padding(8.dp))
            }
        }
        val rows = if (layout == 2) listOf(listOf(0, 1)) else listOf(listOf(0, 1), listOf(2, 3))
        Column(Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.forEach { i ->
                        Tile(
                            player = players[i], media = slots[i], hasAudio = audioSlot == i,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            onFocus = { audioSlot = i },
                            onClick = { if (slots[i] == null || audioSlot == i) picking = i else audioSlot = i },
                            onLongClick = { picking = i },
                        )
                    }
                }
            }
        }
    }

    if (picking >= 0) {
        ChannelPicker(
            onDismiss = { picking = -1 },
            onPick = { m ->
                val slot = picking
                picking = -1
                slots[slot] = m
                audioSlot = slot
                scope.launch { start(players[slot], m) }
            }
        )
    }
}

private suspend fun start(player: ExoPlayer, m: Media) {
    val url = runCatching { Graph.repo.resolve(m) }.getOrNull() ?: return
    player.setMediaItem(PlayerFactory.mediaItem(url, m.name))
    player.prepare()
    player.play()
}

@OptIn(UnstableApi::class)
@Composable
private fun Tile(
    player: ExoPlayer, media: Media?, hasAudio: Boolean, modifier: Modifier,
    onFocus: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit,
) {
    Focusable(
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, modifier = modifier,
        shape = RoundedCornerShape(8.dp), focusScale = 1f, background = Color.Black, focusedBackground = Color.Black,
    ) { focused ->
        Box(Modifier.fillMaxSize().border(if (hasAudio) 2.dp else 0.dp, if (hasAudio) Z.accent.copy(alpha = .7f) else Color.Transparent)) {
            if (media == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, null, tint = if (focused) Z.accent else Z.muted, modifier = Modifier.size(42.dp))
                    Text(stringResource(R.string.add_channel), color = Z.muted, fontSize = 13.sp)
                }
            } else {
                AndroidView(
                    factory = { c ->
                        PlayerView(c).apply {
                            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            isFocusable = false
                            this.player = player
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasAudio) { Icon(Icons.Default.VolumeUp, null, tint = Z.accent, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                    Text(media.name, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun ChannelPicker(onDismiss: () -> Unit, onPick: (Media) -> Unit) {
    val repo = Graph.repo
    var q by remember { mutableStateOf("") }
    val all = remember { repo.channels().filter { m -> repo.categoryOf(m)?.let { !repo.isLocked(it) } ?: true } }
    val list = remember(q) { if (q.isBlank()) all else all.filter { it.name.contains(q, true) } }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(100); runCatching { fr.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxWidth(0.6f).fillMaxHeight(0.9f).clip(RoundedCornerShape(16.dp)).background(Z.bg).padding(12.dp)
        ) {
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text(stringResource(R.string.search_channels)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(list.take(500), key = { it.key }) { m ->
                    ChannelRow(
                        item = m, now = null, onClick = { onPick(m) },
                        modifier = if (m == list.firstOrNull()) Modifier.focusRequester(fr) else Modifier,
                    )
                }
            }
        }
    }
}
