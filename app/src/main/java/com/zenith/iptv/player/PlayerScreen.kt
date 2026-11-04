package com.zenith.iptv.player

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.WatchProgress
import com.zenith.iptv.recording.Recordings
import com.zenith.iptv.ui.ChannelRow
import com.zenith.iptv.ui.Logo
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.timeText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(onBack: () -> Unit) {
    val req = Playback.request
    if (req == null || req.items.isEmpty()) { LaunchedEffect(Unit) { onBack() }; return }

    val ctx = LocalContext.current
    val repo = Graph.repo
    val store = Graph.store
    val settings by store.settings.collectAsState()
    val profileData by store.profileData.collectAsState()
    val tick by repo.tick.collectAsState()
    val isTv = LocalIsTv.current
    val scope = rememberCoroutineScope()

    // If this channel is already playing in the mini window, take over that player (no reload)
    val adopted = remember { MiniPlayer.takeFor(req) }
    val player = remember { adopted ?: PlayerFactory.create(ctx, lowLatency = req.isLive) }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }

    var index by remember { mutableIntStateOf(req.index) }
    var overrideUrl by remember { mutableStateOf(req.urlOverride) }
    var episodeIndex by remember { mutableIntStateOf(req.episodeIndex) }
    var title by remember { mutableStateOf(req.title) }
    val item: Media = req.items[index.coerceIn(0, req.items.lastIndex)]
    val live = overrideUrl == null && item.kind == Kind.LIVE

    var showInfo by remember { mutableStateOf(true) }
    var infoStamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showList by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var numberBuf by remember { mutableStateOf("") }
    var buffering by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retries by remember { mutableIntStateOf(0) }
    var aspect by remember { mutableStateOf(settings.aspect) }
    var sleepAt by remember { mutableLongStateOf(0L) }
    var videoInfo by remember { mutableStateOf("") }
    val rootFocus = remember { FocusRequester() }

    fun poke() { showInfo = true; infoStamp = System.currentTimeMillis() }

    fun startPlayback(url: String, startMs: Long = 0) {
        if (settings.externalPlayer) {
            runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Toast.makeText(ctx, R.string.no_external_player, Toast.LENGTH_SHORT).show() }
            onBack(); return
        }
        error = null
        player.setMediaItem(PlayerFactory.mediaItem(url, title))
        player.prepare()
        if (startMs > 0) player.seekTo(startMs)
        player.playWhenReady = true
    }

    fun play(i: Int) {
        val target = req.items.getOrNull(i) ?: return
        index = i
        overrideUrl = null
        retries = 0
        title = target.name
        poke()
        if (target.kind == Kind.LIVE) store.addRecent(target.key)
        scope.launch {
            buffering = true
            val url = try { repo.resolve(target) } catch (e: Exception) { error = e.message; return@launch }
            startPlayback(url)
        }
    }

    fun zap(delta: Int) {
        if (req.items.size <= 1) return
        val n = (index + delta + req.items.size) % req.items.size
        play(n)
    }

    fun playEpisode(ei: Int, startMs: Long = 0) {
        val series = req.series ?: return
        val ep = req.episodes.getOrNull(ei) ?: return
        episodeIndex = ei
        title = "${series.name} · S${ep.season}E${ep.number}"
        scope.launch {
            val url = try { repo.source?.resolveEpisode(series, ep) ?: ep.url } catch (e: Exception) { error = e.message; return@launch }
            overrideUrl = url
            startPlayback(url, startMs)
        }
    }

    // initial start
    LaunchedEffect(Unit) {
        Playback.active = true
        if (adopted != null) {
            buffering = adopted.playbackState == Player.STATE_BUFFERING
            adopted.playWhenReady = true
            return@LaunchedEffect
        }
        if (req.series != null && req.episodeIndex >= 0) playEpisode(req.episodeIndex, req.startPositionMs)
        else if (req.urlOverride != null) startPlayback(req.urlOverride, req.startPositionMs)
        else {
            if (item.kind == Kind.LIVE) store.addRecent(item.key)
            val url = try { repo.resolve(item) } catch (e: Exception) { error = e.message; null }
            if (url != null) startPlayback(url, req.startPositionMs)
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) { retries = 0; error = null }
                if (state == Player.STATE_ENDED) {
                    if (req.series != null && episodeIndex + 1 < req.episodes.size) playEpisode(episodeIndex + 1)
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                videoInfo = if (videoSize.height > 0) "${videoSize.width}×${videoSize.height}" else ""
            }

            override fun onPlayerError(e: PlaybackException) {
                if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition(); player.prepare(); return
                }
                val isLiveNow = overrideUrl == null && req.items.getOrNull(index)?.kind == Kind.LIVE
                if (isLiveNow && retries < 4) {
                    retries++
                    scope.launch {
                        delay(1500L * retries)
                        val url = runCatching { repo.resolve(req.items[index]) }.getOrNull()
                        if (url != null) { player.setMediaItem(PlayerFactory.mediaItem(url)); player.prepare(); player.play() }
                    }
                } else {
                    error = e.errorCodeName.removePrefix("ERROR_CODE_").replace('_', ' ').lowercase() + (e.cause?.message?.let { "\n$it" } ?: "")
                }
            }
        }
        player.addListener(listener)
        onDispose {
            saveProgress(req, player, episodeIndex)
            player.removeListener(listener)
            if (MiniPlayer.handoff) MiniPlayer.receive(player, req.copy(index = index), title.ifBlank { req.items.getOrNull(index)?.name ?: "" })
            else player.release()
            Playback.active = false
        }
    }

    // periodic VOD progress save
    LaunchedEffect(overrideUrl, index) {
        while (true) { delay(10_000); saveProgress(req, player, episodeIndex) }
    }

    // auto-hide info banner
    LaunchedEffect(infoStamp, showInfo) {
        if (showInfo) { delay(5000); if (System.currentTimeMillis() - infoStamp >= 4900) showInfo = false }
    }

    // number entry → jump to channel
    LaunchedEffect(numberBuf) {
        if (numberBuf.isEmpty()) return@LaunchedEffect
        delay(1600)
        val n = numberBuf.toIntOrNull()
        numberBuf = ""
        if (n != null) {
            val i = req.items.indexOfFirst { it.number == n }
            if (i >= 0) play(i) else Toast.makeText(ctx, ctx.getString(R.string.channel_not_found, n), Toast.LENGTH_SHORT).show()
        }
    }

    // sleep timer
    LaunchedEffect(sleepAt) {
        if (sleepAt > 0) { delay(sleepAt - System.currentTimeMillis()); player.pause(); onBack() }
    }

    /** Leaving a live channel shrinks it into the floating mini player instead of stopping it. */
    fun exit() {
        if (live && error == null && settings.miniPlayer && !settings.externalPlayer) MiniPlayer.handoff = true
        onBack()
    }

    BackHandler {
        when {
            showMenu -> showMenu = false
            showList -> showList = false
            else -> exit()
        }
    }

    LaunchedEffect(live, showList, showMenu) {
        if (live && !showList && !showMenu) runCatching { rootFocus.requestFocus() }
        if (!live && !showMenu) playerView?.requestFocus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || !live || showList || showMenu) return@onPreviewKeyEvent false
                val k = ev.key
                when {
                    k == Key.DirectionUp || k == Key.ChannelDown || k == Key.PageUp -> { zap(-1); true }
                    k == Key.DirectionDown || k == Key.ChannelUp || k == Key.PageDown -> { zap(1); true }
                    k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter || k == Key.DirectionLeft -> { showList = true; true }
                    k == Key.DirectionRight || k == Key.Menu || k == Key.Info -> { showMenu = true; true }
                    k.keyCode in Key.Zero.keyCode..Key.Nine.keyCode -> {
                        numberBuf += ((k.keyCode - Key.Zero.keyCode).toInt()).toString(); true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    this.player = player
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepScreenOn(true)
                    controllerShowTimeoutMs = 4000
                    playerView = this
                }
            },
            update = { v ->
                v.useController = !live
                v.resizeMode = when (aspect) {
                    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
                if (!live) v.isFocusable = true
            },
            modifier = Modifier.fillMaxSize(),
        )

        // touch gestures for live: tap = info, swipe = zap
        if (live && !isTv) {
            Box(
                Modifier.fillMaxSize()
                    .pointerInput(index) { detectTapGestures(onTap = { poke() }, onDoubleTap = { showList = true }) }
                    .pointerInput(index) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = { if (total > 120) zap(-1) else if (total < -120) zap(1) },
                            onVerticalDrag = { _, d -> total += d },
                        )
                    }
            )
        }

        if (buffering && error == null) CircularProgressIndicator(color = Z.accent, modifier = Modifier.align(Alignment.Center))

        error?.let { msg ->
            Column(
                Modifier.align(Alignment.Center).clip(RoundedCornerShape(12.dp)).background(Z.surface.copy(alpha = .9f)).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringResource(R.string.playback_error), color = Z.red, fontWeight = FontWeight.Bold)
                Text(msg, color = Z.muted, fontSize = 12.sp, maxLines = 4)
                if (live) Text(stringResource(R.string.zap_hint), color = Z.muted, fontSize = 12.sp)
            }
        }

        if (numberBuf.isNotEmpty()) {
            Text(
                numberBuf, color = Z.text, fontSize = 44.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(24.dp).clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = .6f)).padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }

        // Info banner (live)
        AnimatedVisibility(visible = live && showInfo && !showList, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            @Suppress("UNUSED_EXPRESSION") tick
            InfoBanner(item, videoInfo, settings.use24h, favorite = item.key in profileData.favorites)
        }

        // Touch top bar
        if (!isTv && (showInfo || !live)) {
            Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent)))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { exit() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
                Text(title.ifBlank { item.name }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (req.series != null && episodeIndex + 1 < req.episodes.size)
                    IconButton(onClick = { playEpisode(episodeIndex + 1) }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                if (live && settings.miniPlayer) IconButton(onClick = { exit() }) {
                    Icon(Icons.Default.PictureInPictureAlt, stringResource(R.string.minimize), tint = Color.White)
                }
                if (live) IconButton(onClick = { showList = true }) { Icon(Icons.AutoMirrored.Filled.List, null, tint = Color.White) }
                IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
            }
        }

        // Channel list overlay
        AnimatedVisibility(
            visible = showList, enter = slideInHorizontally { -it } + fadeIn(), exit = slideOutHorizontally { -it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelListOverlay(req.items, index, profileData.favorites.toSet(), onPick = { i -> showList = false; play(i) })
        }

        // Options menu
        if (showMenu) {
            PlayerMenu(
                player = player,
                item = item,
                live = live,
                isFavorite = item.key in profileData.favorites,
                aspect = aspect,
                sleepAt = sleepAt,
                onAspect = { a -> aspect = a; store.updateSettings { it.copy(aspect = a) } },
                onFavorite = { store.toggleFavorite(item.key) },
                onSleep = { minutes -> sleepAt = if (minutes == 0) 0L else System.currentTimeMillis() + minutes * 60_000L },
                onRecord = {
                    val (now, _) = repo.nowNext(item)
                    val stop = now?.stop ?: (System.currentTimeMillis() + 60 * 60_000L)
                    Recordings.recordNow(ctx, item, now?.title ?: item.name, stop)
                    Toast.makeText(ctx, R.string.recording_started, Toast.LENGTH_SHORT).show()
                },
                onCatchup = { p ->
                    showMenu = false
                    scope.launch {
                        val url = repo.catchup(item, p)
                        if (url == null) Toast.makeText(ctx, R.string.no_catchup, Toast.LENGTH_SHORT).show()
                        else { overrideUrl = url; title = "${item.name} · ${p.title}"; startPlayback(url) }
                    }
                },
                onBackToLive = { play(index) },
                onDismiss = { showMenu = false },
            )
        }
    }
}

@Composable
private fun InfoBanner(item: Media, videoInfo: String, use24h: Boolean, favorite: Boolean) {
    val repo = Graph.repo
    val (now, next) = repo.nowNext(item)
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); clock = System.currentTimeMillis() } }
    Box(
        Modifier.fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 110.dp, height = 70.dp).clip(RoundedCornerShape(10.dp)).background(Z.surface2)) {
                Logo(item.logo, item.name, Modifier.fillMaxSize().padding(6.dp))
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.number}", color = Z.accent, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favorite) Text("  ♥", color = Z.pink)
                    Spacer(Modifier.weight(1f))
                    if (videoInfo.isNotEmpty()) Text(videoInfo, color = Z.muted, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                    Text(timeText(clock, use24h), color = Color.White, fontSize = 16.sp)
                }
                if (now != null) {
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("${timeText(now.start, use24h)} – ${timeText(now.stop, use24h)}  ", color = Z.muted, fontSize = 14.sp)
                        Text(now.title, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { now.progress() }, color = Z.accent, trackColor = Z.line, modifier = Modifier.fillMaxWidth().height(3.dp))
                } else {
                    Text(stringResource(R.string.no_epg), color = Z.muted, fontSize = 13.sp)
                }
                if (next != null) {
                    Spacer(Modifier.height(4.dp))
                    Text("${stringResource(R.string.next)}: ${timeText(next.start, use24h)}  ${next.title}", color = Z.muted, fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ChannelListOverlay(items: List<Media>, current: Int, favorites: Set<String>, onPick: (Int) -> Unit) {
    val repo = Graph.repo
    val tick by repo.tick.collectAsState()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (current - 3).coerceAtLeast(0))
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(80); runCatching { fr.requestFocus() } }
    Box(
        Modifier.fillMaxHeight().widthIn(max = 460.dp).fillMaxWidth(0.45f)
            .background(Brush.horizontalGradient(listOf(Z.bg.copy(alpha = .97f), Z.bg.copy(alpha = .85f))))
            .padding(10.dp)
    ) {
        @Suppress("UNUSED_EXPRESSION") tick
        LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, m -> m.key }) { i, m ->
                ChannelRow(
                    item = m, now = repo.nowNext(m).first, selected = i == current, favorite = m.key in favorites,
                    onClick = { onPick(i) },
                    modifier = if (i == current) Modifier.focusRequester(fr) else Modifier,
                )
            }
        }
    }
}

private fun saveProgress(req: PlayRequest, player: Player, episodeIndex: Int) {
    val dur = player.duration
    val pos = player.currentPosition
    if (dur <= 0 || pos < 5000) return
    val series = req.series
    if (series != null) {
        val ep = req.episodes.getOrNull(episodeIndex) ?: return
        Graph.store.saveProgress(WatchProgress("${series.key}#${ep.id}", pos, dur, System.currentTimeMillis(), "${series.name} S${ep.season}E${ep.number}", series.logo))
        Graph.store.saveProgress(WatchProgress(series.key, pos, dur, System.currentTimeMillis(), series.name, series.logo))
        return
    }
    val key = req.progressKey ?: return
    Graph.store.saveProgress(WatchProgress(key, pos, dur, System.currentTimeMillis(), req.title, req.image))
}
