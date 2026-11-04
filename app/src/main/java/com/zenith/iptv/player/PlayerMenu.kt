package com.zenith.iptv.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.dayText
import com.zenith.iptv.ui.timeText
import kotlinx.coroutines.delay
import java.util.Locale

private enum class Page { MAIN, AUDIO, SUBS, ASPECT, SLEEP, CATCHUP }

@OptIn(UnstableApi::class)
@Composable
fun PlayerMenu(
    player: Player,
    item: Media,
    live: Boolean,
    isFavorite: Boolean,
    aspect: String,
    sleepAt: Long,
    onAspect: (String) -> Unit,
    onFavorite: () -> Unit,
    onSleep: (Int) -> Unit,
    onRecord: () -> Unit,
    onCatchup: (EpgProgram) -> Unit,
    onBackToLive: () -> Unit,
    onDismiss: () -> Unit,
    usingVlc: Boolean = false,
    onSwitchEngine: () -> Unit = {},
) {
    var page by remember { mutableStateOf(Page.MAIN) }
    val use24h = Graph.store.settings.value.use24h

    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(com.zenith.iptv.ui.Adaptive.panelWidth())
                .background(Z.bg.copy(alpha = panelAlpha())).clickable(enabled = false) {}.padding(14.dp)
        ) {
            val fr = remember(page) { FocusRequester() }
            LaunchedEffect(page) { delay(60); runCatching { fr.requestFocus() } }

            val rows: List<MenuRow> = when (page) {
                Page.MAIN -> buildList {
                    add(MenuRow(stringResource(R.string.audio_track), Icons.Default.Audiotrack) { page = Page.AUDIO })
                    add(MenuRow(stringResource(R.string.subtitles), Icons.Default.Subtitles) { page = Page.SUBS })
                    add(MenuRow(stringResource(R.string.aspect_ratio), Icons.Default.AspectRatio, aspectLabel(aspect)) { page = Page.ASPECT })
                    if (item.kind == Kind.LIVE) {
                        add(MenuRow(
                            stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
                            if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder
                        ) { onFavorite() })
                        if (live) add(MenuRow(stringResource(R.string.record_now), Icons.Default.FiberManualRecord) { onRecord(); onDismiss() })
                        if (item.catchupDays > 0) add(MenuRow(stringResource(R.string.catchup), Icons.Default.History) { page = Page.CATCHUP })
                        if (!live) add(MenuRow(stringResource(R.string.back_to_live), Icons.Default.LiveTv) { onBackToLive(); onDismiss() })
                    }
                    val sleepLabel = if (sleepAt > 0) timeText(sleepAt, use24h) else stringResource(R.string.off)
                    add(MenuRow(stringResource(R.string.sleep_timer), Icons.Default.Bedtime, sleepLabel) { page = Page.SLEEP })
                    add(MenuRow(stringResource(R.string.switch_player), Icons.Default.SwapHoriz, if (usingVlc) "VLC → ExoPlayer" else "ExoPlayer → VLC") { onSwitchEngine() })
                }
                Page.AUDIO -> trackRows(player, C.TRACK_TYPE_AUDIO, stringResource(R.string.auto)) { onDismiss() }
                Page.SUBS -> trackRows(player, C.TRACK_TYPE_TEXT, stringResource(R.string.off)) { onDismiss() }
                Page.ASPECT -> listOf("fit", "fill", "zoom").map { a ->
                    MenuRow(aspectLabelRes(a), null, selected = a == aspect) { onAspect(a); onDismiss() }
                }
                Page.SLEEP -> listOf(0, 15, 30, 60, 90, 120).map { m ->
                    MenuRow(if (m == 0) stringResource(R.string.off) else stringResource(R.string.minutes_n, m), null) { onSleep(m); onDismiss() }
                }
                Page.CATCHUP -> {
                    val now = System.currentTimeMillis()
                    val from = now - item.catchupDays * 86_400_000L
                    Graph.repo.programs(item).filter { it.start in from until now }.sortedByDescending { it.start }.map { p ->
                        MenuRow("${dayText(p.start)} ${timeText(p.start, use24h)}  ${p.title}", null) { onCatchup(p) }
                    }.ifEmpty { listOf(MenuRow(stringResource(R.string.no_catchup), null) {}) }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                if (page != Page.MAIN) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Z.muted, modifier = Modifier.size(20.dp).clickable { page = Page.MAIN })
                    Spacer(Modifier.width(8.dp))
                }
                Text(item.name, color = Z.text, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(rows.size) { i ->
                    val r = rows[i]
                    Focusable(
                        onClick = r.action, selected = r.selected, shape = RoundedCornerShape(10.dp), focusScale = 1.02f,
                        background = Z.surface.copy(alpha = Z.surface.alpha * panelAlpha()),
                        modifier = Modifier.fillMaxWidth().then(if (i == 0) Modifier.focusRequester(fr) else Modifier),
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (r.icon != null) { Icon(r.icon, null, tint = Z.accent, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
                            Text(r.label, color = Z.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 14.sp)
                            if (r.value.isNotEmpty()) { Spacer(Modifier.width(8.dp)); Text(r.value, color = Z.muted, fontSize = 13.sp, maxLines = 1) }
                            if (r.selected) Icon(Icons.Default.Check, null, tint = Z.accent, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

private data class MenuRow(
    val label: String,
    val icon: ImageVector?,
    val value: String = "",
    val selected: Boolean = false,
    val action: () -> Unit,
)

@Composable
private fun aspectLabel(a: String) = aspectLabelRes(a)

@Composable
private fun aspectLabelRes(a: String): String = stringResource(
    when (a) { "fill" -> R.string.aspect_fill; "zoom" -> R.string.aspect_zoom; else -> R.string.aspect_fit }
)

@OptIn(UnstableApi::class)
private fun trackRows(player: Player, type: Int, noneLabel: String, done: () -> Unit): List<MenuRow> {
    val rows = ArrayList<MenuRow>()
    val params = player.trackSelectionParameters
    val disabled = params.disabledTrackTypes.contains(type)
    rows += MenuRow(noneLabel, null, selected = disabled || (type == C.TRACK_TYPE_AUDIO && params.overrides.values.none { it.type == type })) {
        val b = player.trackSelectionParameters.buildUpon().clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT) b.setTrackTypeDisabled(type, true)
        player.trackSelectionParameters = b.build()
        done()
    }
    for (group in player.currentTracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            val f = group.getTrackFormat(i)
            val lang = f.language?.let { Locale(it).displayLanguage }?.takeIf { it.isNotBlank() && it != "und" }
            val label = listOfNotNull(f.label, lang, f.codecs?.substringBefore('.')?.uppercase(), if (f.channelCount > 0) "${f.channelCount}ch" else null)
                .distinct().joinToString(" · ").ifBlank { "Track ${i + 1}" }
            rows += MenuRow(label, null, selected = group.isTrackSelected(i) && !disabled) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(type, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                    .build()
                done()
            }
        }
    }
    return rows
}


/** Panel opacity chosen in Settings (0.3 – 1.0). */
@Composable
fun panelAlpha(): Float {
    val s by Graph.store.settings.collectAsState()
    return (s.panelOpacity / 100f).coerceIn(0.3f, 1f)
}
