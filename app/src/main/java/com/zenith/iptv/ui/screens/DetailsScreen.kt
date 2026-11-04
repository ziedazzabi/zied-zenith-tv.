package com.zenith.iptv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Episode
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.MediaDetails
import com.zenith.iptv.player.PlayRequest
import com.zenith.iptv.ui.Chip
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Logo
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.fmtDuration
import kotlinx.coroutines.delay

@Composable
fun DetailsScreen(nav: Nav, key: String) {
    val repo = Graph.repo
    val store = Graph.store
    val ctx = LocalContext.current
    val data by store.profileData.collectAsState()
    val isTv = LocalIsTv.current
    val wide = com.zenith.iptv.ui.Adaptive.wide()
    val item = remember(key) { repo.byKey(key) }
    if (item == null) { ScreenScaffold("", nav) { EmptyState(stringResource(R.string.nothing_here)) }; return }

    var details by remember { mutableStateOf<MediaDetails?>(null) }
    var loading by remember { mutableStateOf(true) }
    var season by remember { mutableIntStateOf(-1) }
    LaunchedEffect(key) {
        details = runCatching { repo.source?.details(item) }.getOrNull() ?: MediaDetails(plot = item.plot, rating = item.rating)
        season = details?.seasons?.keys?.firstOrNull() ?: -1
        loading = false
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(loading) { if (!loading) { delay(100); runCatching { playFocus.requestFocus() } } }

    val progress = data.progress[item.key]
    val fav = item.key in data.favorites
    val d = details

    fun playEpisode(eps: List<Episode>, i: Int, resume: Boolean) {
        val ep = eps[i]
        val p = data.progress["${item.key}#${ep.id}"]
        nav.play(PlayRequest(
            items = listOf(item), title = "${item.name} · S${ep.season}E${ep.number}",
            series = item, episodes = eps, episodeIndex = i, image = item.logo,
            startPositionMs = if (resume) p?.positionMs ?: 0L else 0L,
        ))
    }

    ScreenScaffold(item.name, nav) {
        Box(Modifier.fillMaxSize()) {
            if (d != null && d.backdrop.isNotBlank()) {
                Logo(d.backdrop, item.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f).alpha(.25f), ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Z.bg), startY = 200f)))
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                item {
                    Row(Modifier.padding(top = 8.dp)) {
                        Box(Modifier.width(if (wide) 220.dp else 120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).background(Z.surface2)) {
                            Logo(item.logo, item.name, Modifier.fillMaxSize(), ContentScale.Crop)
                        }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = Z.text, fontSize = if (wide) 28.sp else 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val rating = d?.rating?.ifBlank { item.rating } ?: item.rating
                                if (rating.isNotBlank() && rating != "0") {
                                    Icon(Icons.Default.Star, null, tint = Z.amber, modifier = Modifier.size(16.dp))
                                    Text(" $rating   ", color = Z.text, fontSize = 14.sp)
                                }
                                val year = d?.releaseDate?.take(4)?.ifBlank { null } ?: item.year
                                if (year.isNotBlank()) Text("$year   ", color = Z.muted, fontSize = 14.sp)
                                if (!d?.duration.isNullOrBlank()) Text(d!!.duration, color = Z.muted, fontSize = 14.sp)
                            }
                            if (!d?.genre.isNullOrBlank()) Text(d!!.genre, color = Z.accent, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (item.kind == Kind.MOVIE) {
                                    if (progress != null && progress.positionMs > 0) {
                                        ActionButton(stringResource(R.string.resume_at, fmtDuration(progress.positionMs)), Icons.Default.PlayArrow, primary = true, modifier = Modifier.focusRequester(playFocus)) {
                                            nav.play(PlayRequest(listOf(item), title = item.name, progressKey = item.key, startPositionMs = progress.positionMs, image = item.logo))
                                        }
                                        ActionButton(stringResource(R.string.from_start), Icons.Default.Replay) {
                                            nav.play(PlayRequest(listOf(item), title = item.name, progressKey = item.key, image = item.logo))
                                        }
                                    } else {
                                        ActionButton(stringResource(R.string.play), Icons.Default.PlayArrow, primary = true, modifier = Modifier.focusRequester(playFocus)) {
                                            nav.play(PlayRequest(listOf(item), title = item.name, progressKey = item.key, image = item.logo))
                                        }
                                    }
                                } else if (item.url.isNotBlank()) {
                                    ActionButton(stringResource(R.string.play), Icons.Default.PlayArrow, primary = true, modifier = Modifier.focusRequester(playFocus)) {
                                        nav.play(PlayRequest(listOf(item), title = item.name, progressKey = item.key, urlOverride = item.url, image = item.logo))
                                    }
                                }
                                ActionButton(
                                    stringResource(if (fav) R.string.in_my_list else R.string.add_my_list),
                                    if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    modifier = if (item.kind == Kind.SERIES && item.url.isBlank()) Modifier.focusRequester(playFocus) else Modifier,
                                ) { store.toggleFavorite(item.key) }
                                if (!d?.trailer.isNullOrBlank()) ActionButton(stringResource(R.string.trailer), Icons.Default.Videocam) {
                                    val t = d!!.trailer
                                    val url = if (t.startsWith("http")) t else "https://www.youtube.com/watch?v=$t"
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                                }
                            }
                            if (wide) PlotBlock(d, item.plot)
                        }
                    }
                    if (!wide) PlotBlock(d, item.plot)
                    if (loading) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Z.accent) }
                }

                // Seasons & episodes
                val seasons = d?.seasons ?: emptyMap()
                if (seasons.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons.keys.toList()) { s ->
                                Chip(stringResource(R.string.season_n, s), s == season, onClick = { season = s })
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    val eps = seasons[season] ?: emptyList()
                    items(eps.size, key = { eps[it].id + "_" + season }) { i ->
                        val ep = eps[i]
                        val p = data.progress["${item.key}#${ep.id}"]
                        Focusable(
                            onClick = { playEpisode(eps, i, resume = true) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), focusScale = 1.01f,
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.width(if (isTv) 160.dp else 110.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Z.surface2)) {
                                    Logo(ep.cover.ifBlank { item.logo }, ep.title, Modifier.fillMaxSize(), ContentScale.Crop)
                                    if (p != null && p.durationMs > 0) LinearProgressIndicator(
                                        progress = { p.positionMs.toFloat() / p.durationMs }, color = Z.accent, trackColor = Color.Black.copy(alpha = .5f),
                                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("${ep.number}. ${ep.title.ifBlank { stringResource(R.string.episode_n, ep.number) }}", color = Z.text, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (ep.plot.isNotBlank()) Text(ep.plot, color = Z.muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (ep.duration.isNotBlank()) Text(ep.duration, color = Z.muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlotBlock(d: MediaDetails?, fallback: String) {
    val plot = d?.plot?.ifBlank { fallback } ?: fallback
    Column(Modifier.padding(top = 16.dp)) {
        if (plot.isNotBlank()) Text(plot, color = Z.text.copy(alpha = .85f), fontSize = 14.sp, lineHeight = 21.sp)
        if (!d?.director.isNullOrBlank()) Text("${stringResource(R.string.director)}: ${d!!.director}", color = Z.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (!d?.cast.isNullOrBlank()) Text("${stringResource(R.string.cast)}: ${d!!.cast}", color = Z.muted, fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ActionButton(label: String, icon: ImageVector, primary: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Focusable(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(50),
        background = if (primary) Z.accent else Z.surface2, focusedBackground = if (primary) Z.accentFocus else Z.surface3,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (primary) Z.onAccent else Z.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = if (primary) Z.onAccent else Z.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
