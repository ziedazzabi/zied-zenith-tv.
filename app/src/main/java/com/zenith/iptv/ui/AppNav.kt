package com.zenith.iptv.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zenith.iptv.Graph
import com.zenith.iptv.MainActivity
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.player.MultiViewScreen
import com.zenith.iptv.player.PlayRequest
import com.zenith.iptv.player.Playback
import com.zenith.iptv.player.PlayerScreen
import com.zenith.iptv.ui.screens.AddPlaylistScreen
import com.zenith.iptv.ui.screens.AppsScreen
import com.zenith.iptv.ui.screens.DetailsScreen
import com.zenith.iptv.ui.screens.GuideScreen
import com.zenith.iptv.ui.screens.HomeScreen
import com.zenith.iptv.ui.screens.LiveScreen
import com.zenith.iptv.ui.screens.PlaylistsScreen
import com.zenith.iptv.ui.screens.ProfilesScreen
import com.zenith.iptv.ui.screens.RecordingsScreen
import com.zenith.iptv.ui.screens.SearchScreen
import com.zenith.iptv.ui.screens.SettingsScreen
import com.zenith.iptv.ui.screens.VodScreen

object Routes {
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val SERIES = "series"
    const val GUIDE = "guide"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val PLAYLISTS = "playlists"
    const val ADD_PLAYLIST = "add_playlist?id={id}"
    const val PROFILES = "profiles?pick={pick}"
    const val PICK_PROFILE = "pick_profile"
    const val RECORDINGS = "recordings"
    const val DETAILS = "details/{key}"
    const val PLAYER = "player"
    const val MULTIVIEW = "multiview"
    const val APPS = "apps"

    fun addPlaylist(id: String = "") = "add_playlist?id=${Uri.encode(id)}"
    fun profiles(pick: Boolean = false) = "profiles?pick=$pick"
    fun details(key: String) = "details/${Uri.encode(key)}"
}

/** Navigation helpers shared by screens. */
class Nav(val controller: NavController) {
    fun back() { if (!controller.popBackStack()) controller.navigate(Routes.HOME) }
    fun go(route: String) = controller.navigate(route) { launchSingleTop = true }

    /** Goes to home and clears everything behind it (after first setup / profile pick). */
    fun home() = controller.navigate(Routes.HOME) {
        popUpTo(controller.graph.id) { inclusive = true }
        launchSingleTop = true
    }

    fun play(request: PlayRequest) {
        Playback.request = request
        controller.navigate(Routes.PLAYER) { launchSingleTop = true }
    }

    /** Opens any item: channels play immediately, movies & series show their details page. */
    fun open(item: Media, list: List<Media> = listOf(item)) {
        when (item.kind) {
            Kind.LIVE -> {
                val l = list.filter { it.kind == Kind.LIVE }.ifEmpty { listOf(item) }
                play(PlayRequest(items = l, index = l.indexOfFirst { it.key == item.key }.coerceAtLeast(0), title = item.name))
            }
            else -> go(Routes.details(item.key))
        }
    }
}

@Composable
fun AppNav(activity: MainActivity) {
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val store = Graph.store
    val repo = Graph.repo

    val start = remember {
        when {
            store.playlists.value.isEmpty() -> Routes.ADD_PLAYLIST
            store.profiles.value.size > 1 -> Routes.PICK_PROFILE
            else -> Routes.HOME
        }
    }

    LaunchedEffect(Unit) {
        if (repo.catalog.value == null) repo.activePlaylist()?.let { repo.open(it) }
    }

    // Reminder notification → play that channel
    val pendingKey = activity.pendingPlayKey.value
    LaunchedEffect(pendingKey, repo.catalog.value) {
        val key = pendingKey ?: return@LaunchedEffect
        val item = repo.byKey(key) ?: return@LaunchedEffect
        activity.pendingPlayKey.value = null
        nav.open(item, repo.channels())
    }
    // "Open with" an .m3u link → prefill the add-playlist form
    val pendingM3u = activity.pendingM3u.value
    LaunchedEffect(pendingM3u) {
        if (pendingM3u != null) { PrefillHolder.m3u = pendingM3u; activity.pendingM3u.value = null; nav.go(Routes.addPlaylist()) }
    }

    NavHost(controller, startDestination = start) {
        composable(Routes.HOME) { HomeScreen(nav) }
        composable(Routes.PICK_PROFILE) { ProfilesScreen(nav, true) }
        composable(Routes.LIVE) { LiveScreen(nav) }
        composable(Routes.MOVIES) { VodScreen(nav, Kind.MOVIE) }
        composable(Routes.SERIES) { VodScreen(nav, Kind.SERIES) }
        composable(Routes.GUIDE) { GuideScreen(nav) }
        composable(Routes.SEARCH) { SearchScreen(nav) }
        composable(Routes.SETTINGS) { SettingsScreen(nav, activity) }
        composable(Routes.PLAYLISTS) { PlaylistsScreen(nav) }
        composable(Routes.RECORDINGS) { RecordingsScreen(nav) }
        composable(Routes.PLAYER) { PlayerScreen(onBack = { nav.back() }) }
        composable(Routes.APPS) { AppsScreen(nav) }
        composable(Routes.MULTIVIEW) { MultiViewScreen(onBack = { nav.back() }) }
        composable(
            Routes.ADD_PLAYLIST,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })
        ) { e -> AddPlaylistScreen(nav, e.arguments?.getString("id").orEmpty()) }
        composable(
            Routes.PROFILES,
            arguments = listOf(navArgument("pick") { type = NavType.BoolType; defaultValue = false })
        ) { e -> ProfilesScreen(nav, e.arguments?.getBoolean("pick") ?: false) }
        composable(
            Routes.DETAILS,
            arguments = listOf(navArgument("key") { type = NavType.StringType })
        ) { e -> DetailsScreen(nav, Uri.decode(e.arguments?.getString("key").orEmpty())) }
    }
}

object PrefillHolder {
    var m3u: String? = null
}
