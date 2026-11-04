package com.zenith.iptv.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.ui.DeviceClass
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalDevice
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.SectionTitle
import com.zenith.iptv.ui.Z
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A popular streaming / music app we know how to find. */
data class KnownApp(
    val name: String,
    /** Package names (phone and TV editions). */
    val packages: List<String>,
    /** Words matched against installed app names when the package differs. "=" prefix = exact match. */
    val keywords: List<String>,
    val color: Long,
    /** Google Play id to open when not installed; null = search by name. */
    val storeId: String? = null,
)

data class InstalledApp(val pkg: String, val label: String)

object AppLauncher {
    val known = listOf(
        KnownApp("Netflix", listOf("com.netflix.mediaclient", "com.netflix.ninja"), listOf("netflix"), 0xFFB20710, "com.netflix.mediaclient"),
        KnownApp("Prime Video", listOf("com.amazon.avod.thirdpartyclient", "com.amazon.amazonvideo.livingroom", "com.amazon.avod"), listOf("prime video"), 0xFF0F79AF, "com.amazon.avod.thirdpartyclient"),
        KnownApp("Disney+", listOf("com.disney.disneyplus"), listOf("disney"), 0xFF0E2A80, "com.disney.disneyplus"),
        KnownApp("Shahid", listOf("net.mbc.shahid", "net.mbc.shahidTV"), listOf("shahid", "شاهد"), 0xFF1B8A4B, "net.mbc.shahid"),
        KnownApp("OSN+", listOf("com.osn.go", "com.osn.osnplus"), listOf("osn"), 0xFF3A2D7A),
        KnownApp("Max", listOf("com.wbd.stream", "com.hbo.hbonow"), listOf("=max", "hbo"), 0xFF1F2A9C, "com.wbd.stream"),
        KnownApp("Apple TV", listOf("com.apple.atve.androidtv.appletv", "com.apple.atve.amazon.appletv"), listOf("apple tv"), 0xFF2B2B2B),
        KnownApp("YouTube", listOf("com.google.android.youtube", "com.google.android.youtube.tv", "com.google.android.youtube.googletv"), listOf("=youtube"), 0xFFCC0000, "com.google.android.youtube"),
        KnownApp("YouTube Kids", listOf("com.google.android.apps.youtube.kids"), listOf("youtube kids"), 0xFFD93025, "com.google.android.apps.youtube.kids"),
        KnownApp("STARZPLAY", listOf("com.parsifal.starz"), listOf("starz"), 0xFF1D3557, "com.parsifal.starz"),
        KnownApp("beIN", emptyList(), listOf("bein"), 0xFF5B2A86),
        KnownApp("TOD", emptyList(), listOf("=tod"), 0xFF6A1B9A),
        KnownApp("Spotify", listOf("com.spotify.music", "com.spotify.tv.android"), listOf("spotify"), 0xFF1AA34A, "com.spotify.music"),
        KnownApp("Anghami", listOf("com.anghami"), listOf("anghami", "أنغامي"), 0xFF6C2BD9, "com.anghami"),
        KnownApp("YouTube Music", listOf("com.google.android.apps.youtube.music"), listOf("youtube music"), 0xFFB71C1C, "com.google.android.apps.youtube.music"),
        KnownApp("Twitch", listOf("tv.twitch.android.app"), listOf("twitch"), 0xFF6441A5, "tv.twitch.android.app"),
        KnownApp("Plex", listOf("com.plexapp.android"), listOf("plex"), 0xFF8A6A00, "com.plexapp.android"),
        KnownApp("Kodi", listOf("org.xbmc.kodi"), listOf("kodi"), 0xFF1C6E8C, "org.xbmc.kodi"),
        KnownApp("VLC", listOf("org.videolan.vlc"), listOf("vlc"), 0xFFE07B00, "org.videolan.vlc"),
    )

    /** All launchable apps on this device (phone and TV launchers), except Zenith itself. */
    fun installed(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val result = LinkedHashMap<String, InstalledApp>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            runCatching { pm.queryIntentActivities(q, 0) }.getOrDefault(emptyList()).forEach { ri ->
                val pkg = ri.activityInfo.packageName
                if (pkg != ctx.packageName && pkg !in result) result[pkg] = InstalledApp(pkg, ri.loadLabel(pm).toString())
            }
        }
        return result.values.sortedBy { it.label.lowercase() }
    }

    /** Installed package for a known app, or null. */
    fun find(k: KnownApp, installed: List<InstalledApp>): String? {
        val pkgs = installed.associateBy { it.pkg }
        k.packages.firstOrNull { it in pkgs }?.let { return it }
        return installed.firstOrNull { a ->
            val l = a.label.lowercase().trim()
            k.keywords.any { kw -> if (kw.startsWith("=")) l == kw.drop(1) else kw in l }
        }?.pkg
    }

    fun launch(ctx: Context, pkg: String, tv: Boolean): Boolean {
        val pm = ctx.packageManager
        val intent = (if (tv) pm.getLeanbackLaunchIntentForPackage(pkg) else null)
            ?: pm.getLaunchIntentForPackage(pkg)
            ?: pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: return false
        return try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    fun openStore(ctx: Context, k: KnownApp, tv: Boolean) {
        val q = Uri.encode(k.name)
        val tries = buildList {
            if (k.storeId != null && !tv) add("market://details?id=${k.storeId}")
            add("market://search?q=$q&c=apps")
            if (k.storeId != null) add("market://details?id=${k.storeId}")
            if (k.storeId != null) add("amzn://apps/android?p=${k.storeId}")
            add("https://play.google.com/store/search?q=$q&c=apps")
        }
        for (u in tries) {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return }
            catch (e: ActivityNotFoundException) { /* try next */ }
        }
        Toast.makeText(ctx, R.string.no_store, Toast.LENGTH_LONG).show()
    }

    /** App icon, or the 16:9 TV banner when [banner] is true and the app has one. */
    fun image(ctx: Context, pkg: String, banner: Boolean): ImageBitmap? = runCatching {
        val pm = ctx.packageManager
        val d = (if (banner) pm.getApplicationBanner(pkg) ?: runCatching {
            pm.getActivityBanner(pm.getLeanbackLaunchIntentForPackage(pkg) ?: Intent())
        }.getOrNull() else null)
        if (d != null) d.toBitmap(320, 180).asImageBitmap()
        else pm.getApplicationIcon(pkg).toBitmap(192, 192).asImageBitmap()
    }.getOrNull()

    /** Apps shown on the home screen: the user's pins, or every installed popular app by default. */
    fun homeApps(ctx: Context, pinned: List<String>?, installed: List<InstalledApp>): List<InstalledApp> {
        val byPkg = installed.associateBy { it.pkg }
        val pkgs = pinned ?: known.mapNotNull { find(it, installed) }
        return pkgs.mapNotNull { byPkg[it] }.distinctBy { it.pkg }
    }

    fun togglePin(ctx: Context, pkg: String, installed: List<InstalledApp>) {
        val store = Graph.store
        val current = store.settings.value.pinnedApps ?: homeApps(ctx, null, installed).map { it.pkg }
        val next = if (pkg in current) current - pkg else current + pkg
        store.updateSettings { it.copy(pinnedApps = next) }
        Toast.makeText(ctx, if (pkg in next) R.string.pinned_home else R.string.unpinned_home, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberInstalledApps(): List<InstalledApp>? {
    val ctx = LocalContext.current
    val state by produceState<List<InstalledApp>?>(null) { value = withContext(Dispatchers.IO) { AppLauncher.installed(ctx) } }
    return state
}

/** Card for one app: real icon/banner when installed, coloured name tile otherwise. */
@Composable
fun AppCard(
    name: String,
    pkg: String?,
    width: Dp,
    color: Long = 0xFF334155,
    pinned: Boolean = false,
    notInstalled: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val tv = LocalIsTv.current
    val img by produceState<ImageBitmap?>(null, pkg) {
        value = if (pkg == null) null else withContext(Dispatchers.IO) { AppLauncher.image(ctx, pkg, tv) }
    }
    val wide = img != null && img!!.width > img!!.height
    Focusable(onClick = onClick, onLongClick = onLongClick, modifier = Modifier.width(width), focusScale = 1.08f, shape = RoundedCornerShape(14.dp)) {
        Column {
            Box(
                Modifier.fillMaxWidth().aspectRatio(16f / 10f)
                    .background(if (img != null && !wide) Z.surface2 else Color(color.toInt())),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = img
                when {
                    bmp != null && wide -> Image(bmp, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    bmp != null -> Image(bmp, name, Modifier.fillMaxSize(0.62f), contentScale = ContentScale.Fit)
                    else -> Box(
                        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(color.toInt()), Color(color.toInt()).copy(alpha = .55f)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.Center,
                            maxLines = 2, modifier = Modifier.padding(6.dp))
                    }
                }
                if (notInstalled) Box(
                    Modifier.align(Alignment.TopEnd).padding(5.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 5.dp, vertical = 2.dp)
                ) { Icon(Icons.Default.Download, stringResource(R.string.install), tint = Color.White, modifier = Modifier.size(14.dp)) }
                if (pinned) Icon(Icons.Default.PushPin, null, tint = Z.accent,
                    modifier = Modifier.align(Alignment.TopStart).padding(5.dp).size(16.dp))
            }
            Text(name, color = Z.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

/** Row of app shortcuts for the home screen. */
@Composable
fun HomeAppsRow(nav: Nav) {
    val ctx = LocalContext.current
    val tv = LocalIsTv.current
    val settings by Graph.store.settings.collectAsState()
    val installed = rememberInstalledApps() ?: return
    val apps = remember(installed, settings.pinnedApps) { AppLauncher.homeApps(ctx, settings.pinnedApps, installed) }
    val w = cardWidth()
    SectionTitle(stringResource(R.string.my_apps), icon = Icons.Default.Apps)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = com.zenith.iptv.ui.Adaptive.rowPad()) {
        items(apps, key = { it.pkg }) { a ->
            AppCard(a.label, a.pkg, w,
                onClick = { AppLauncher.launch(ctx, a.pkg, tv) },
                onLongClick = { AppLauncher.togglePin(ctx, a.pkg, installed) })
        }
        item {
            Focusable(onClick = { nav.go(Routes.APPS) }, modifier = Modifier.width(w), focusScale = 1.08f, shape = RoundedCornerShape(14.dp)) {
                Column {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Z.surface2), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Add, null, tint = Z.accent, modifier = Modifier.size(34.dp))
                    }
                    Text(stringResource(R.string.all_apps), color = Z.text, fontSize = 12.sp, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun cardWidth(): Dp = when (LocalDevice.current) {
    DeviceClass.PHONE -> 112.dp; DeviceClass.TABLET -> 140.dp; DeviceClass.TV -> 170.dp
}

@Composable
fun AppsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val tv = LocalIsTv.current
    val settings by Graph.store.settings.collectAsState()
    var refresh by remember { mutableStateOf(0) }
    val installed by produceState<List<InstalledApp>?>(null, refresh) { value = withContext(Dispatchers.IO) { AppLauncher.installed(ctx) } }
    // re-scan when coming back from the store
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(5000); refresh++ } }
    val w = cardWidth()

    ScreenScaffold(stringResource(R.string.apps), nav) {
        val list = installed
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Z.accent) }
        } else {
        val pinned = settings.pinnedApps ?: AppLauncher.homeApps(ctx, null, list).map { it.pkg }
        val knownResolved = AppLauncher.known.map { it to AppLauncher.find(it, list) }
        val knownPkgs = knownResolved.mapNotNull { it.second }.toSet()
        val others = list.filter { it.pkg !in knownPkgs }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(w),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.apps_hint), color = Z.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    SectionTitle(stringResource(R.string.popular_apps), icon = Icons.Default.Star)
                }
            }
            items(knownResolved, key = { "k:" + it.first.name }) { (k, pkg) ->
                AppCard(
                    k.name, pkg, w, color = k.color, pinned = pkg != null && pkg in pinned, notInstalled = pkg == null,
                    onClick = { if (pkg == null || !AppLauncher.launch(ctx, pkg, tv)) AppLauncher.openStore(ctx, k, tv) },
                    onLongClick = { if (pkg != null) AppLauncher.togglePin(ctx, pkg, list) else AppLauncher.openStore(ctx, k, tv) },
                )
            }
            if (others.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column { Spacer(Modifier.height(6.dp)); SectionTitle(stringResource(R.string.installed_apps), icon = Icons.Default.Apps) }
                }
                items(others, key = { "a:" + it.pkg }) { a ->
                    AppCard(
                        a.label, a.pkg, w, pinned = a.pkg in pinned,
                        onClick = { AppLauncher.launch(ctx, a.pkg, tv) },
                        onLongClick = { AppLauncher.togglePin(ctx, a.pkg, list) },
                    )
                }
            }
        }
        }
    }
}

