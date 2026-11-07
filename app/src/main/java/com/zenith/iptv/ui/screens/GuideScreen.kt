package com.zenith.iptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.LoadState
import com.zenith.iptv.data.Media
import com.zenith.iptv.player.PlayRequest
import com.zenith.iptv.recording.Recordings
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.Chip
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Logo
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.dayText
import com.zenith.iptv.ui.timeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SLOT = 30 * 60_000L

private fun floorSlot(t: Long) = t - (t % SLOT)

@Composable
fun GuideScreen(nav: Nav) {
    val repo = Graph.repo
    val store = Graph.store
    val catalog by repo.catalog.collectAsState()
    val epgState by repo.epgState.collectAsState()
    val epg by repo.epg.collectAsState()
    val tick by repo.tick.collectAsState()
    val settings by store.settings.collectAsState()
    val data by store.profileData.collectAsState()
    val wide = com.zenith.iptv.ui.Adaptive.wide()
    val hours = if (wide) 3 else 2
    val windowMs = hours * 3_600_000L

    var start by rememberSaveable { mutableLongStateOf(floorSlot(System.currentTimeMillis())) }
    var cat by rememberSaveable { mutableStateOf(CAT_ALL) }
    var selected by remember { mutableStateOf<Pair<Media, EpgProgram>?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }

    ScreenScaffold(stringResource(R.string.tv_guide), nav, rail = Routes.GUIDE, actions = {
        Focusable(onClick = { start -= SLOT * 2 }, shape = RoundedCornerShape(50)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.earlier), tint = Z.text, modifier = Modifier.padding(8.dp))
        }
        Focusable(onClick = { start = floorSlot(System.currentTimeMillis()) }, shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccessTime, null, tint = Z.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.now), color = Z.text, fontSize = 13.sp)
            }
        }
        Focusable(onClick = { start += SLOT * 2 }, shape = RoundedCornerShape(50)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.later), tint = Z.text, modifier = Modifier.padding(8.dp))
        }
    }) {
        CatalogGate(nav) {
            val cats = remember(catalog, settings) { repo.categories(Kind.LIVE) }
            val channels = remember(cat, catalog, data.favorites, settings) {
                when (cat) {
                    CAT_ALL -> repo.items(Kind.LIVE, null).filter { m -> repo.categoryOf(m)?.let { !repo.isLocked(it) } ?: true }
                    CAT_FAV -> data.favorites.mapNotNull { repo.byKey(it) }.filter { it.kind == Kind.LIVE }
                    else -> repo.items(Kind.LIVE, cat)
                }
            }
            Column(Modifier.fillMaxSize()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = com.zenith.iptv.ui.Adaptive.rowPad()) {
                    item { Chip(stringResource(R.string.all_channels), cat == CAT_ALL, onClick = { cat = CAT_ALL }) }
                    item { Chip(stringResource(R.string.favorites), cat == CAT_FAV, onClick = { cat = CAT_FAV }) }
                    items(cats.filter { !repo.isLocked(it) }, key = { it.id }) { c -> Chip(c.name, cat == c.id, onClick = { cat = c.id }) }
                }
                // Time ruler
                val channelCol = if (wide) 200.dp else 120.dp
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(dayText(start), color = Z.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(channelCol).padding(start = 6.dp))
                    Row(Modifier.weight(1f)) {
                        for (i in 0 until hours * 2) {
                            Text(timeText(start + i * SLOT, settings.use24h), color = Z.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
                if (epgState is LoadState.Loading && epg.isEmpty()) {
                    Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = Z.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.loading_epg), color = Z.muted, fontSize = 12.sp)
                    }
                }
                if (channels.isEmpty()) EmptyState(stringResource(R.string.no_channels))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(channels, key = { it.key }) { m ->
                        @Suppress("UNUSED_EXPRESSION") tick
                        GuideRow(m, start, windowMs, now, channelCol, settings.use24h,
                            onChannel = { nav.play(PlayRequest(channels, channels.indexOf(m).coerceAtLeast(0), title = m.name)) },
                            onProgram = { p -> selected = m to p })
                    }
                }
            }
        }
    }

    selected?.let { (m, p) ->
        ProgramDialog(m, p, settings.use24h, onDismiss = { selected = null }, onWatch = { url ->
            selected = null
            if (url == null) {
                val list = repo.channels()
                nav.play(PlayRequest(list, list.indexOfFirst { it.key == m.key }.coerceAtLeast(0), title = m.name))
            } else nav.play(PlayRequest(listOf(m), urlOverride = url, title = "${m.name} · ${p.title}", catchupProgram = p))
        })
    }
}

@Composable
private fun GuideRow(
    m: Media, start: Long, windowMs: Long, now: Long, channelCol: androidx.compose.ui.unit.Dp, use24h: Boolean,
    onChannel: () -> Unit, onProgram: (EpgProgram) -> Unit,
) {
    val repo = Graph.repo
    val end = start + windowMs
    val programs = repo.programs(m).filter { it.stop > start && it.start < end }
    if (programs.isEmpty()) repo.requestShortEpg(m)

    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Focusable(onClick = onChannel, modifier = Modifier.width(channelCol).fillMaxHeight(), shape = RoundedCornerShape(8.dp), focusScale = 1f) {
            Row(Modifier.padding(horizontal = 6.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 44.dp, height = 30.dp).clip(RoundedCornerShape(4.dp)).background(Z.surface2)) {
                    Logo(m.logo, m.name, Modifier.fillMaxSize().padding(2.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(m.name, color = Z.text, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(3.dp))
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            // build segments filling the window, with gaps as "no info" blocks
            var cursor = start
            val segments = ArrayList<Pair<Long, EpgProgram?>>()
            for (p in programs.sortedBy { it.start }) {
                val s = maxOf(p.start, start)
                if (s > cursor) segments += (s - cursor) to null
                val e = minOf(p.stop, end)
                if (e > maxOf(s, cursor)) segments += (e - maxOf(s, cursor)) to p
                cursor = maxOf(cursor, e)
            }
            if (cursor < end) segments += (end - cursor) to null
            for ((dur, p) in segments) {
                val w = dur.toFloat() / windowMs
                if (p == null) {
                    Box(Modifier.weight(w).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(Z.surface.copy(alpha = .5f)))
                } else {
                    val live = p.isLive(now)
                    val past = p.stop <= now
                    Focusable(
                        onClick = { onProgram(p) }, modifier = Modifier.weight(w).fillMaxHeight(), shape = RoundedCornerShape(6.dp), focusScale = 1f,
                        background = if (live) Z.accent.copy(alpha = .16f) else if (past) Z.surface.copy(alpha = .6f) else Z.surface,
                    ) {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (past && m.catchupDays > 0) { Icon(Icons.Default.History, null, tint = Z.accent, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                                if (Recordings.hasReminder(m.key, p.start)) { Icon(Icons.Default.NotificationsActive, null, tint = Z.amber, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                                if (Recordings.isScheduled(m.key, p)) { Icon(Icons.Default.FiberManualRecord, null, tint = Z.red, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(3.dp)) }
                                Text(p.title, color = if (past) Z.muted else Z.text, fontSize = 12.sp, fontWeight = if (live) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(timeText(p.start, use24h), color = Z.muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgramDialog(m: Media, p: EpgProgram, use24h: Boolean, onDismiss: () -> Unit, onWatch: (String?) -> Unit) {
    val ctx = LocalContext.current
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()
    val past = p.stop <= now
    val live = p.isLive(now)
    val future = p.start > now
    var reminder by remember { mutableStateOf(Recordings.hasReminder(m.key, p.start)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.surface,
        title = { Text(p.title, maxLines = 2) },
        text = {
            Column {
                Text("${m.name} · ${dayText(p.start)} ${timeText(p.start, use24h)} – ${timeText(p.stop, use24h)}", color = Z.accent, fontSize = 13.sp)
                if (p.desc.isNotBlank()) Text(p.desc, color = Z.text, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp), maxLines = 10, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                if (live) TextButton(onClick = { onWatch(null) }) { Text(stringResource(R.string.watch_live)) }
                if ((past || live) && m.catchupDays > 0 && p.start > now - m.catchupDays * 86_400_000L) {
                    TextButton(onClick = {
                        scope.launch {
                            val url = repo.catchup(m, p)
                            if (url == null) Toast.makeText(ctx, R.string.no_catchup, Toast.LENGTH_SHORT).show() else onWatch(url)
                        }
                    }) { Text(stringResource(if (live) R.string.watch_from_start else R.string.play_catchup)) }
                }
                if (future) TextButton(onClick = {
                    if (reminder) Recordings.removeReminder(ctx, m.key, p.start) else Recordings.addReminder(ctx, m, p)
                    reminder = !reminder
                    Toast.makeText(ctx, if (reminder) R.string.reminder_set else R.string.reminder_removed, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(if (reminder) R.string.remove_reminder else R.string.set_reminder)) }
                if (future || live) TextButton(onClick = {
                    if (live) Recordings.recordNow(ctx, m, p.title, p.stop) else Recordings.schedule(ctx, m, p)
                    Toast.makeText(ctx, if (live) R.string.recording_started else R.string.recording_scheduled, Toast.LENGTH_SHORT).show()
                    onDismiss()
                }) { Text(stringResource(if (live) R.string.record_now else R.string.schedule_recording)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    @Suppress("UNUSED_EXPRESSION") Color.Unspecified
}
