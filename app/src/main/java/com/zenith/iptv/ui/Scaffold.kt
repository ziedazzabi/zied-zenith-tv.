package com.zenith.iptv.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.LoadState

@Composable
fun ScreenScaffold(
    title: String,
    nav: Nav,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    /** Current section (Routes.LIVE, MOVIES…): shows the side navigation bar on TVs / wide screens. */
    rail: String? = null,
    content: @Composable () -> Unit,
) {
    val isTv = LocalIsTv.current
    val showRail = rail != null && (isTv || Adaptive.wide())
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = if (showRail) RAIL_WIDTH else 0.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = if (isTv) 32.dp else Adaptive.gutter() - 4.dp, vertical = if (isTv) 16.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack && !isTv) {
                    Focusable(onClick = { nav.back() }, shape = CircleShape, background = androidx.compose.ui.graphics.Color.Transparent) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Z.text, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    title, color = Z.text, fontWeight = FontWeight.Bold, fontSize = if (isTv) 24.sp else 20.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
            // bottom padding keeps the last row clear of the TV's overscan
            Box(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp else Adaptive.gutter() - 4.dp).padding(bottom = if (isTv) 20.dp else 0.dp)) { content() }
        }
        if (showRail) NavRail(rail!!, nav)
    }
}

private val RAIL_WIDTH = 72.dp

private data class RailItem(val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: Int)

/**
 * TiviMate / Formuler style side bar: icons only, opens with labels when the remote reaches it (← from the content).
 */
@Composable
private fun NavRail(current: String, nav: Nav) {
    val items = listOf(
        RailItem(Routes.HOME, Icons.Default.Home, R.string.home),
        RailItem(Routes.LIVE, Icons.Default.LiveTv, R.string.live_tv),
        RailItem(Routes.MOVIES, Icons.Default.Movie, R.string.movies),
        RailItem(Routes.SERIES, Icons.Default.Theaters, R.string.series),
        RailItem(Routes.GUIDE, Icons.Default.CalendarMonth, R.string.tv_guide),
        RailItem(Routes.SEARCH, Icons.Default.Search, R.string.search),
        RailItem(Routes.SETTINGS, Icons.Default.Settings, R.string.settings),
    )
    var open by remember { mutableStateOf(false) }
    val width by androidx.compose.animation.core.animateDpAsState(if (open) 230.dp else RAIL_WIDTH, label = "rail")
    Column(
        Modifier.fillMaxHeight().width(width)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    if (open) listOf(Z.bg, Z.bg.copy(alpha = .96f)) else listOf(Z.bg.copy(alpha = .75f), Z.bg.copy(alpha = .35f))
                )
            )
            .onFocusChanged { open = it.hasFocus }
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        items.forEach { it ->
            val selected = it.route == current
            Focusable(
                onClick = {
                    if (it.route == Routes.HOME) nav.home() else if (!selected) nav.section(it.route)
                },
                selected = selected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), focusScale = 1.04f,
                background = androidx.compose.ui.graphics.Color.Transparent,
            ) { focused ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(it.icon, stringResource(it.label), tint = if (selected || focused) Z.accent else Z.muted, modifier = Modifier.size(26.dp))
                    if (open) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            stringResource(it.label), color = if (focused) Z.text else Z.muted, fontSize = 16.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Shows loading / error states until the active playlist's catalog is ready. */
@Composable
fun CatalogGate(nav: Nav, content: @Composable () -> Unit) {
    val repo = Graph.repo
    val state by repo.state.collectAsState()
    val catalog by repo.catalog.collectAsState()
    when {
        catalog != null -> content()
        state is LoadState.Error -> Column(
            Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.load_failed), color = Z.red, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text((state as LoadState.Error).message, color = Z.muted, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { repo.reload() }, colors = ButtonDefaults.buttonColors(containerColor = Z.accent)) { Text(stringResource(R.string.retry)) }
                OutlinedButton(onClick = { nav.go(Routes.PLAYLISTS) }) { Text(stringResource(R.string.playlists)) }
            }
        }
        repo.activePlaylist() == null -> Column(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text(stringResource(R.string.no_playlist), color = Z.muted)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { nav.go(Routes.addPlaylist()) }) { Text(stringResource(R.string.add_playlist)) }
        }
        else -> Column(
            Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Z.accent)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.loading_playlist, (state as? LoadState.Loading)?.message ?: ""), color = Z.muted)
        }
    }
}
