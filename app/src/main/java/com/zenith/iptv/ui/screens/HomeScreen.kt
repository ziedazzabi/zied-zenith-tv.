package com.zenith.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.LoadState
import com.zenith.iptv.data.Media
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Logo
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PosterCard
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.SectionTitle
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.timeText
import kotlinx.coroutines.delay

private data class Tile(val label: String, val icon: ImageVector, val route: String, val color: Color, val count: Int = -1)

@Composable
fun HomeScreen(nav: Nav) {
    val repo = Graph.repo
    val store = Graph.store
    val catalog by repo.catalog.collectAsState()
    val state by repo.state.collectAsState()
    val settings by store.settings.collectAsState()
    val data by store.profileData.collectAsState()
    val profiles by store.profiles.collectAsState()
    val isTv = LocalIsTv.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); clock = System.currentTimeMillis() } }

    val profile = profiles.firstOrNull { it.id == store.activeProfileId() } ?: profiles.first()
    val playlist = repo.activePlaylist()

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (isTv) 40.dp else 12.dp, vertical = if (isTv) 20.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("ZENITH", color = Z.text, fontWeight = FontWeight.Black, fontSize = if (isTv) 26.sp else 20.sp, letterSpacing = 3.sp)
            Text(" TV", color = Z.accent, fontWeight = FontWeight.Black, fontSize = if (isTv) 26.sp else 20.sp)
            Spacer(Modifier.width(16.dp))
            if (playlist != null) {
                Focusable(onClick = { nav.go(Routes.PLAYLISTS) }, shape = RoundedCornerShape(50)) {
                    Text(playlist.name, color = Z.muted, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            if (state is LoadState.Loading && catalog != null) {
                Spacer(Modifier.width(8.dp)); Icon(Icons.Default.Refresh, null, tint = Z.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.weight(1f))
            if (wide) Text(timeText(clock, settings.use24h), color = Z.text, fontSize = 18.sp, modifier = Modifier.padding(end = 16.dp))
            Focusable(onClick = { nav.go(Routes.SEARCH) }, shape = CircleShape) {
                Icon(Icons.Default.Search, stringResource(R.string.search), tint = Z.text, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.width(8.dp))
            Focusable(onClick = { nav.go(Routes.profiles()) }, shape = CircleShape, background = Color(profile.color)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(profile.name.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }

        CatalogGate(nav) {
            val c = catalog!!
            val counts = remember(c) { c.items.groupingBy { it.kind }.eachCount() }
            val tiles = listOf(
                Tile(stringResource(R.string.live_tv), Icons.Default.LiveTv, Routes.LIVE, Color(0xFF0EA5E9), counts[Kind.LIVE] ?: 0),
                Tile(stringResource(R.string.movies), Icons.Default.Movie, Routes.MOVIES, Color(0xFFDB2777), counts[Kind.MOVIE] ?: 0),
                Tile(stringResource(R.string.series), Icons.Default.Theaters, Routes.SERIES, Color(0xFF7C3AED), counts[Kind.SERIES] ?: 0),
                Tile(stringResource(R.string.tv_guide), Icons.Default.CalendarMonth, Routes.GUIDE, Color(0xFF059669)),
                Tile(stringResource(R.string.multiview), Icons.Default.GridView, Routes.MULTIVIEW, Color(0xFFD97706)),
                Tile(stringResource(R.string.recordings), Icons.Default.FiberManualRecord, Routes.RECORDINGS, Color(0xFFDC2626)),
                Tile(stringResource(R.string.settings), Icons.Default.Settings, Routes.SETTINGS, Color(0xFF475569)),
            )

            val recents = remember(data.recentChannels, c) { data.recentChannels.mapNotNull { repo.byKey(it) }.take(20) }
            val favChannels = remember(data.favorites, c) { data.favorites.mapNotNull { repo.byKey(it) }.filter { it.kind == Kind.LIVE } }
            val favVod = remember(data.favorites, c) { data.favorites.mapNotNull { repo.byKey(it) }.filter { it.kind != Kind.LIVE } }
            val continueWatching = remember(data.progress, c) {
                data.progress.values.sortedByDescending { it.updatedAt }
                    .mapNotNull { p -> if ('#' in p.key) null else repo.byKey(p.key)?.let { it to p } }.take(20)
            }
            val newest = remember(c) {
                c.items.asSequence().filter { it.kind == Kind.MOVIE && it.added > 0 }.sortedByDescending { it.added }.take(20).toList()
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = if (isTv) 40.dp else 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(tiles) { t -> BigTile(t, isTv) { nav.go(t.route) } }
                    }
                }
                if (continueWatching.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.continue_watching), icon = Icons.Default.PlayCircle)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(continueWatching, key = { it.first.key }) { (m, p) ->
                            PosterCard(m, onClick = { nav.open(m) }, progress = if (p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f)
                        }
                    }
                }
                if (recents.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.recent_channels), icon = Icons.Default.LiveTv)
                    ChannelStrip(recents) { m -> nav.open(m, recents) }
                }
                if (favChannels.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.favorite_channels))
                    ChannelStrip(favChannels) { m -> nav.open(m, favChannels) }
                }
                if (favVod.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.my_list))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(favVod, key = { it.key }) { m -> PosterCard(m, onClick = { nav.open(m) }, favorite = true) }
                    }
                }
                if (newest.isNotEmpty()) item {
                    SectionTitle(stringResource(R.string.recently_added))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(newest, key = { it.key }) { m -> PosterCard(m, onClick = { nav.open(m) }) }
                    }
                }
                if (c.accountInfo.isNotBlank()) item {
                    Text(c.accountInfo, color = Z.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun BigTile(t: Tile, isTv: Boolean, onClick: () -> Unit) {
    val w = if (isTv) 200.dp else 128.dp
    val h = if (isTv) 130.dp else 92.dp
    Focusable(onClick = onClick, modifier = Modifier.width(w).height(h), shape = RoundedCornerShape(16.dp), focusScale = 1.08f) { focused ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(t.color.copy(alpha = if (focused) .95f else .7f), t.color.copy(alpha = .25f)))
            ).padding(14.dp)
        ) {
            Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(if (isTv) 38.dp else 28.dp))
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(t.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTv) 18.sp else 14.sp, maxLines = 1)
                if (t.count >= 0) Text("${t.count}", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelStrip(list: List<Media>, onClick: (Media) -> Unit) {
    val repo = Graph.repo
    val tick by repo.tick.collectAsState()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(list, key = { it.key }) { m ->
            @Suppress("UNUSED_EXPRESSION") tick
            val now = repo.nowNext(m).first
            Focusable(onClick = { onClick(m) }, modifier = Modifier.width(180.dp), focusScale = 1.06f) {
                Column {
                    Box(Modifier.fillMaxWidth().height(90.dp).background(Z.surface2), contentAlignment = Alignment.Center) {
                        Logo(m.logo, m.name, Modifier.fillMaxSize().padding(14.dp))
                    }
                    Column(Modifier.padding(8.dp)) {
                        Text(m.name, color = Z.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(now?.title ?: " ", color = Z.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (now != null) {
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { now.progress() }, color = Z.accent, trackColor = Z.line,
                                modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape))
                        }
                    }
                }
            }
        }
    }
}

