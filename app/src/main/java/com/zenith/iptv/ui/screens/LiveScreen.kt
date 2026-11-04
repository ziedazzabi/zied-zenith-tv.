package com.zenith.iptv.ui.screens

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.player.PlayRequest
import com.zenith.iptv.player.PlayerFactory
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.ChannelRow
import com.zenith.iptv.ui.Chip
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Logo
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PinDialog
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.timeText
import kotlinx.coroutines.launch

const val CAT_ALL = "__all"
const val CAT_FAV = "__fav"
const val CAT_RECENT = "__recent"

@Composable
fun LiveScreen(nav: Nav) {
    val repo = Graph.repo
    val store = Graph.store
    val ctx = LocalContext.current
    val catalog by repo.catalog.collectAsState()
    val data by store.profileData.collectAsState()
    val settings by store.settings.collectAsState()
    val tick by repo.tick.collectAsState()
    val wide = com.zenith.iptv.ui.Adaptive.wide()
    val threePane = wide && (com.zenith.iptv.ui.LocalDevice.current == com.zenith.iptv.ui.DeviceClass.TV || com.zenith.iptv.ui.Adaptive.widthDp() >= 840)
    val isTv = LocalIsTv.current

    var selectedCat by rememberSaveable { mutableStateOf(CAT_ALL) }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    var catMenu by remember { mutableStateOf<Category?>(null) }
    var focused by remember { mutableStateOf<Media?>(null) }
    var previewing by remember { mutableStateOf<Media?>(null) }

    ScreenScaffold(stringResource(R.string.live_tv), nav, actions = {
        Focusable(onClick = { nav.go(Routes.GUIDE) }, shape = RoundedCornerShape(50)) {
            Icon(Icons.Default.ViewList, stringResource(R.string.tv_guide), tint = Z.text, modifier = Modifier.padding(10.dp))
        }
        Focusable(onClick = { nav.go(Routes.SEARCH) }, shape = RoundedCornerShape(50)) {
            Icon(Icons.Default.Search, stringResource(R.string.search), tint = Z.text, modifier = Modifier.padding(10.dp))
        }
        Focusable(onClick = { repo.reload() }, shape = RoundedCornerShape(50)) {
            Icon(Icons.Default.Refresh, stringResource(R.string.refresh), tint = Z.text, modifier = Modifier.padding(10.dp))
        }
    }) {
        CatalogGate(nav) {
            val cats = remember(catalog, settings) { repo.categories(Kind.LIVE) }
            val special = listOf(
                Category(CAT_ALL, stringResource(R.string.all_channels), Kind.LIVE),
                Category(CAT_FAV, stringResource(R.string.favorites), Kind.LIVE),
                Category(CAT_RECENT, stringResource(R.string.recent), Kind.LIVE),
            )
            val channels: List<Media> = remember(selectedCat, catalog, data.favorites, data.recentChannels, settings) {
                when (selectedCat) {
                    CAT_ALL -> repo.items(Kind.LIVE, null).filter { m -> repo.categoryOf(m)?.let { !repo.isLocked(it) } ?: true }
                    CAT_FAV -> data.favorites.mapNotNull { repo.byKey(it) }.filter { it.kind == Kind.LIVE }
                    CAT_RECENT -> data.recentChannels.mapNotNull { repo.byKey(it) }
                    else -> repo.items(Kind.LIVE, selectedCat)
                }
            }

            fun selectCat(c: Category) {
                if (c.id.startsWith("__") || !repo.isLocked(c)) selectedCat = c.id else pinFor = c
            }

            fun openFull(m: Media) {
                previewing = null
                nav.play(PlayRequest(items = channels, index = channels.indexOf(m).coerceAtLeast(0), title = m.name))
            }

            fun onChannelClick(m: Media) {
                if (threePane && previewing?.key != m.key) previewing = m else openFull(m)
            }

            fun onChannelLong(m: Media) {
                store.toggleFavorite(m.key)
                val added = m.key in store.profileData.value.favorites
                Toast.makeText(ctx, if (added) R.string.added_favorite else R.string.removed_favorite, Toast.LENGTH_SHORT).show()
            }

            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Categories
                    LazyColumn(Modifier.width(if (threePane) com.zenith.iptv.ui.Adaptive.sidePane() else 200.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(special + cats, key = { it.id + it.kind }) { c ->
                            CategoryItem(
                                c, selected = c.id == selectedCat, locked = !c.id.startsWith("__") && repo.isLocked(c),
                                count = if (c.id.startsWith("__")) -1 else repo.items(Kind.LIVE, c.id).size,
                                onClick = { selectCat(c) }, onLongClick = { if (!c.id.startsWith("__")) catMenu = c },
                            )
                        }
                    }
                    // Channels
                    ChannelList(
                        channels, Modifier.weight(1f).fillMaxHeight(), data.favorites.toSet(), previewing?.key, tick,
                        onClick = ::onChannelClick, onLong = ::onChannelLong, onFocus = { focused = it },
                    )
                    // Preview + EPG (only when there's room for a third pane)
                    if (threePane) Column(Modifier.weight(0.9f).fillMaxHeight()) {
                        PreviewPane(previewing, onOpen = { previewing?.let { openFull(it) } })
                        Spacer(Modifier.height(10.dp))
                        EpgPanel(focused ?: previewing, settings.use24h, tick)
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = com.zenith.iptv.ui.Adaptive.rowPad()) {
                        items(special + cats, key = { it.id + it.kind }) { c ->
                            val locked = !c.id.startsWith("__") && repo.isLocked(c)
                            Chip(c.name, c.id == selectedCat, onClick = { selectCat(c) }, icon = if (locked) Icons.Default.Lock else null)
                        }
                    }
                    ChannelList(
                        channels, Modifier.fillMaxSize(), data.favorites.toSet(), null, tick,
                        onClick = ::openFull, onLong = ::onChannelLong, onFocus = { focused = it },
                    )
                }
            }
        }
    }

    pinFor?.let { c ->
        PinDialog(onDismiss = { pinFor = null }) { pin ->
            if (pin == settings.parentalPin) { repo.unlock(c); selectedCat = c.id; pinFor = null }
            else Toast.makeText(ctx, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
        }
    }
    catMenu?.let { c -> CategoryMenu(c, onDismiss = { catMenu = null }) }
    @Suppress("UNUSED_EXPRESSION") isTv
}

@Composable
fun CategoryItem(c: Category, selected: Boolean, locked: Boolean, count: Int, onClick: () -> Unit, onLongClick: () -> Unit) {
    Focusable(onClick = onClick, onLongClick = onLongClick, selected = selected, modifier = Modifier.fillMaxWidth(), focusScale = 1.02f, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            when (c.id) {
                CAT_FAV -> Icon(Icons.Default.Favorite, null, tint = Z.pink, modifier = Modifier.size(16.dp).padding(end = 2.dp))
                CAT_RECENT -> Icon(Icons.Default.History, null, tint = Z.accent, modifier = Modifier.size(16.dp))
            }
            if (c.id == CAT_FAV || c.id == CAT_RECENT) Spacer(Modifier.width(8.dp))
            Text(c.name, color = if (selected) Z.accent else Z.text, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (locked) Icon(Icons.Default.Lock, null, tint = Z.amber, modifier = Modifier.size(14.dp))
            else if (count >= 0) Text("$count", color = Z.muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ChannelList(
    channels: List<Media>, modifier: Modifier, favorites: Set<String>, previewKey: String?, tick: Int,
    onClick: (Media) -> Unit, onLong: (Media) -> Unit, onFocus: (Media) -> Unit,
) {
    val repo = Graph.repo
    val state = rememberLazyListState()
    if (channels.isEmpty()) { EmptyState(stringResource(R.string.no_channels), modifier); return }
    LazyColumn(modifier, state = state, verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(channels, key = { it.key }) { m ->
            @Suppress("UNUSED_EXPRESSION") tick
            ChannelRow(
                item = m, now = repo.nowNext(m).first, selected = m.key == previewKey, favorite = m.key in favorites,
                onClick = { onClick(m) }, onLongClick = { onLong(m) }, onFocus = { onFocus(m) },
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPane(item: Media?, onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { PlayerFactory.create(ctx, lowLatency = true) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    androidx.compose.runtime.LaunchedEffect(item?.key) {
        if (item == null) { player.stop(); return@LaunchedEffect }
        com.zenith.iptv.player.MiniPlayer.close() // avoid two channels playing sound at once
        scope.launch {
            val url = runCatching { Graph.repo.resolve(item) }.getOrNull() ?: return@launch
            player.setMediaItem(PlayerFactory.mediaItem(url, item.name)); player.prepare(); player.play()
        }
    }
    Focusable(onClick = onOpen, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f), background = Color.Black, focusedBackground = Color.Black, focusScale = 1f) {
        Box(Modifier.fillMaxSize()) {
            if (item == null) {
                Text(stringResource(R.string.preview_hint), color = Z.muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center).padding(16.dp))
            } else {
                AndroidView(factory = { c ->
                    PlayerView(c).apply {
                        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false; isFocusable = false; this.player = player
                    }
                }, modifier = Modifier.fillMaxSize())
                Text(
                    item.name, color = Color.White, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EpgPanel(item: Media?, use24h: Boolean, tick: Int) {
    if (item == null) return
    val repo = Graph.repo
    @Suppress("UNUSED_EXPRESSION") tick
    val now = System.currentTimeMillis()
    val programs = repo.programs(item).filter { it.stop > now }.take(8)
    if (programs.isEmpty()) repo.requestShortEpg(item)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Z.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 54.dp, height = 34.dp).clip(RoundedCornerShape(6.dp)).background(Z.surface2)) {
                Logo(item.logo, item.name, Modifier.fillMaxSize().padding(3.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(item.name, color = Z.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        if (programs.isEmpty()) Text(stringResource(R.string.no_epg), color = Z.muted, fontSize = 13.sp)
        programs.forEachIndexed { i, p ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(timeText(p.start, use24h), color = if (i == 0) Z.accent else Z.muted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                Column {
                    Text(p.title, color = if (i == 0) Z.text else Z.muted, fontSize = 13.sp, fontWeight = if (i == 0) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (i == 0 && p.desc.isNotBlank()) Text(p.desc, color = Z.muted, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Long-press menu on a category: lock/unlock with PIN, or hide it. */
@Composable
fun CategoryMenu(c: Category, onDismiss: () -> Unit) {
    val repo = Graph.repo
    val store = Graph.store
    val ctx = LocalContext.current
    val key = repo.categoryKey(c)
    val locked = key in store.settings.value.lockedCategories
    var askPin by remember { mutableStateOf(false) }
    if (askPin) {
        PinDialog(onDismiss = onDismiss) { pin ->
            if (pin == store.settings.value.parentalPin) {
                store.updateSettings { s -> s.copy(lockedCategories = if (locked) s.lockedCategories - key else s.lockedCategories + key) }
                onDismiss()
            } else Toast.makeText(ctx, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.surface,
        title = { Text(c.name) },
        text = {
            Column {
                TextButton(onClick = { askPin = true }) { Text(stringResource(if (locked) R.string.unlock_category else R.string.lock_category)) }
                TextButton(onClick = {
                    store.updateSettings { it.copy(hiddenCategories = it.hiddenCategories + key) }
                    onDismiss()
                }) { Text(stringResource(R.string.hide_category)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
