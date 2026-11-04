package com.zenith.iptv.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Category
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.ui.CatalogGate
import com.zenith.iptv.ui.Chip
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PinDialog
import com.zenith.iptv.ui.PosterCard
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z

private const val CAT_CONTINUE = "__continue"

@Composable
fun VodScreen(nav: Nav, kind: Kind) {
    val repo = Graph.repo
    val store = Graph.store
    val ctx = LocalContext.current
    val catalog by repo.catalog.collectAsState()
    val data by store.profileData.collectAsState()
    val settings by store.settings.collectAsState()
    val tick by repo.tick.collectAsState()
    val isTv = LocalIsTv.current
    val wide = LocalConfiguration.current.screenWidthDp >= 720

    var selectedCat by rememberSaveable(kind) { mutableStateOf(CAT_ALL) }
    var sort by rememberSaveable { mutableStateOf("default") }
    var pinFor by remember { mutableStateOf<Category?>(null) }
    var catMenu by remember { mutableStateOf<Category?>(null) }
    var loadingLazy by remember { mutableStateOf(false) }

    val title = stringResource(if (kind == Kind.MOVIE) R.string.movies else R.string.series)
    ScreenScaffold(title, nav) {
        CatalogGate(nav) {
            val cats = remember(catalog, settings) { repo.categories(kind) }
            val special = listOf(
                Category(CAT_ALL, stringResource(R.string.all), kind),
                Category(CAT_FAV, stringResource(R.string.my_list), kind),
                Category(CAT_CONTINUE, stringResource(R.string.continue_watching), kind),
            )
            val selected = cats.firstOrNull { it.id == selectedCat }

            LaunchedEffect(selectedCat) {
                if (selected != null && selected.lazy) { loadingLazy = true; repo.loadLazy(selected); loadingLazy = false }
            }

            val vodItems: List<Media> = remember(selectedCat, catalog, data, sort, tick, settings) {
                val base = when (selectedCat) {
                    CAT_ALL -> repo.items(kind, null).filter { m -> repo.categoryOf(m)?.let { !repo.isLocked(it) } ?: true }
                    CAT_FAV -> data.favorites.mapNotNull { repo.byKey(it) }.filter { it.kind == kind }
                    CAT_CONTINUE -> data.progress.values.sortedByDescending { it.updatedAt }.mapNotNull { repo.byKey(it.key) }.filter { it.kind == kind }
                    else -> repo.items(kind, selectedCat)
                }
                when (sort) {
                    "az" -> base.sortedBy { it.name.lowercase() }
                    "new" -> base.sortedByDescending { it.added.takeIf { a -> a > 0 } ?: it.year.toLongOrNull() ?: 0 }
                    "rating" -> base.sortedByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                    else -> base
                }
            }

            fun selectCat(c: Category) { if (c.id.startsWith("__") || !repo.isLocked(c)) selectedCat = c.id else pinFor = c }

            val grid: @Composable (Modifier) -> Unit = { mod ->
                Column(mod) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        val sorts = listOf(
                            "default" to R.string.sort_default, "az" to R.string.sort_az,
                            "new" to R.string.sort_newest, "rating" to R.string.sort_rating,
                        )
                        items(sorts) { (k, res) -> Chip(stringResource(res), sort == k, onClick = { sort = k }) }
                    }
                    when {
                        loadingLazy -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Z.accent) }
                        vodItems.isEmpty() -> EmptyState(stringResource(R.string.nothing_here))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (isTv) 150.dp else 110.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(6.dp, 6.dp, 6.dp, 32.dp),
                        ) {
                            items(vodItems, key = { it.key }) { m ->
                                val p = data.progress[m.key]
                                PosterCard(
                                    m, width = if (isTv) 150.dp else 110.dp,
                                    onClick = { nav.open(m) },
                                    favorite = m.key in data.favorites,
                                    progress = if (p != null && p.durationMs > 0) p.positionMs.toFloat() / p.durationMs else 0f,
                                    onLongClick = {
                                        store.toggleFavorite(m.key)
                                        Toast.makeText(ctx, if (m.key in store.profileData.value.favorites) R.string.added_my_list else R.string.removed_my_list, Toast.LENGTH_SHORT).show()
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LazyColumn(Modifier.width(240.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(special + cats, key = { it.id }) { c ->
                            CategoryItem(
                                c, selected = c.id == selectedCat, locked = !c.id.startsWith("__") && repo.isLocked(c),
                                count = if (c.id.startsWith("__") || c.lazy) -1 else repo.items(kind, c.id).size,
                                onClick = { selectCat(c) }, onLongClick = { if (!c.id.startsWith("__")) catMenu = c },
                            )
                        }
                    }
                    grid(Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 4.dp)) {
                        items(special + cats, key = { it.id }) { c ->
                            Chip(c.name, c.id == selectedCat, onClick = { selectCat(c) },
                                icon = if (!c.id.startsWith("__") && repo.isLocked(c)) Icons.Default.Lock else null)
                        }
                    }
                    grid(Modifier.fillMaxSize().padding(top = 2.dp))
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
}
