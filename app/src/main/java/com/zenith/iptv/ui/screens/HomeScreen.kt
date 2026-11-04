package com.zenith.iptv.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
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
import com.zenith.iptv.ui.Adaptive
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.DeviceClass
import com.zenith.iptv.ui.LocalDevice
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
    val wide = Adaptive.wide()
    val device = LocalDevice.current
    val gutter = Adaptive.gutter()
    val tc = Z.palette.tiles
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(20_000); clock = System.currentTimeMillis() } }

    val profile = profiles.firstOrNull { it.id == store.activeProfileId() } ?: profiles.first()
    val playlist = repo.activePlaylist()

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = gutter, vertical = if (isTv) 20.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(painterResource(R.drawable.logo_mark), "Z-Vision", modifier = Modifier.size(if (isTv) 44.dp else 34.dp))
            Spacer(Modifier.width(6.dp))
            Text("Z-", color = Z.accent, fontWeight = FontWeight.Bold, fontSize = if (isTv) 26.sp else 20.sp)
            Text("Vision", color = Z.text, fontWeight = FontWeight.Bold, fontSize = if (isTv) 26.sp else 20.sp)
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
                Tile(stringResource(R.string.live_tv), Icons.Default.LiveTv, Routes.LIVE, tc[0], counts[Kind.LIVE] ?: 0),
                Tile(stringResource(R.string.movies), Icons.Default.Movie, Routes.MOVIES, tc[1], counts[Kind.MOVIE] ?: 0),
                Tile(stringResource(R.string.series), Icons.Default.Theaters, Routes.SERIES, tc[2], counts[Kind.SERIES] ?: 0),
                Tile(stringResource(R.string.tv_guide), Icons.Default.CalendarMonth, Routes.GUIDE, tc[3]),
                Tile(stringResource(R.string.multiview), Icons.Default.GridView, Routes.MULTIVIEW, tc[4]),
                Tile(stringResource(R.string.recordings), Icons.Default.FiberManualRecord, Routes.RECORDINGS, tc[5]),
                Tile(stringResource(R.string.settings), Icons.Default.Settings, Routes.SETTINGS, tc[6]),
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
                contentPadding = PaddingValues(horizontal = gutter, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    if (device == DeviceClass.PHONE && !wide) {
                        // Phone portrait: 3 big tiles + 4 compact tiles, all visible without scrolling
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                tiles.take(3).forEach { t -> BigTile(t, Modifier.weight(1f).height(104.dp), large = false) { nav.go(t.route) } }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                tiles.drop(3).forEach { t -> SmallTile(t, Modifier.weight(1f)) { nav.go(t.route) } }
                            }
                        }
                    } else {
                        val (w, h) = when (device) {
                            DeviceClass.TV -> 200.dp to 130.dp
                            DeviceClass.TABLET -> 176.dp to 116.dp
                            DeviceClass.PHONE -> 140.dp to 96.dp
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(if (isTv) 18.dp else 12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                            items(tiles) { t -> BigTile(t, Modifier.width(w).height(h), large = device == DeviceClass.TV) { nav.go(t.route) } }
                        }
                    }
                }
                item { HomeAppsRow(nav) }
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
private fun BigTile(t: Tile, modifier: Modifier, large: Boolean, onClick: () -> Unit) {
    val strong = Z.palette.light
    Focusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), focusScale = 1.08f) { focused ->
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(
                    listOf(
                        t.color.copy(alpha = if (focused || strong) 1f else .75f),
                        t.color.copy(alpha = if (strong) .78f else .28f),
                    )
                )
            ).padding(14.dp)
        ) {
            Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(if (large) 38.dp else 28.dp))
            Column(Modifier.align(Alignment.BottomStart)) {
                Text(t.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (large) 18.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (t.count >= 0) Text("${t.count}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SmallTile(t: Tile, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier.height(76.dp), shape = RoundedCornerShape(14.dp)) {
        Column(
            Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(t.color), contentAlignment = Alignment.Center) {
                Icon(t.icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(t.label, color = Z.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ChannelStrip(list: List<Media>, onClick: (Media) -> Unit) {
    val repo = Graph.repo
    val tick by repo.tick.collectAsState()
    val cardW = when (LocalDevice.current) { DeviceClass.PHONE -> 150.dp; DeviceClass.TABLET -> 180.dp; DeviceClass.TV -> 200.dp }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(list, key = { it.key }) { m ->
            @Suppress("UNUSED_EXPRESSION") tick
            val now = repo.nowNext(m).first
            Focusable(onClick = { onClick(m) }, modifier = Modifier.width(cardW), focusScale = 1.06f) {
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

