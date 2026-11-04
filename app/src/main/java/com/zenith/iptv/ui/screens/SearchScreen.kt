package com.zenith.iptv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.ChannelRow
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PosterCard
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.SectionTitle
import com.zenith.iptv.ui.Z
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(nav: Nav) {
    val repo = Graph.repo
    val data by Graph.store.profileData.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Media>>(emptyList()) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { fr.requestFocus() } }
    LaunchedEffect(q) {
        delay(300)
        results = withContext(Dispatchers.Default) { repo.search(q) }
    }

    ScreenScaffold(stringResource(R.string.search), nav) {
        CatalogGate(nav) {
            val channels = results.filter { it.kind == Kind.LIVE }
            val movies = results.filter { it.kind == Kind.MOVIE }
            val series = results.filter { it.kind == Kind.SERIES }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                item {
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Z.muted) },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Z.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (q.length >= 2 && results.isEmpty()) item { EmptyState(stringResource(R.string.no_results)) }
                if (movies.isNotEmpty()) item {
                    SectionTitle("${stringResource(R.string.movies)} (${movies.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = com.zenith.iptv.ui.Adaptive.rowPad()) {
                        items(movies, key = { it.key }) { m -> PosterCard(m, onClick = { nav.open(m) }, favorite = m.key in data.favorites) }
                    }
                }
                if (series.isNotEmpty()) item {
                    SectionTitle("${stringResource(R.string.series)} (${series.size})")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = com.zenith.iptv.ui.Adaptive.rowPad()) {
                        items(series, key = { it.key }) { m -> PosterCard(m, onClick = { nav.open(m) }, favorite = m.key in data.favorites) }
                    }
                }
                if (channels.isNotEmpty()) {
                    item { SectionTitle("${stringResource(R.string.live_tv)} (${channels.size})") }
                    items(channels, key = { it.key }) { m ->
                        ChannelRow(m, repo.nowNext(m).first, onClick = { nav.open(m, channels) }, favorite = m.key in data.favorites)
                    }
                }
            }
        }
    }
}
