package com.zenith.iptv.ui

import androidx.compose.foundation.layout.Arrangement
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
    content: @Composable () -> Unit,
) {
    val isTv = LocalIsTv.current
    Column(Modifier.fillMaxSize()) {
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
        Box(Modifier.fillMaxSize().padding(horizontal = if (isTv) 24.dp else Adaptive.gutter() - 4.dp)) { content() }
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
