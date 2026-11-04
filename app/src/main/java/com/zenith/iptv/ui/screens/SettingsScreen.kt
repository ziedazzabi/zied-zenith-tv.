package com.zenith.iptv.ui.screens

import android.widget.Toast
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.zenith.iptv.data.Wallpaper
import com.zenith.iptv.ui.Wallpapers
import java.io.File
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import com.zenith.iptv.ui.Palette as ThemePalette
import com.zenith.iptv.ui.Themes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import com.zenith.iptv.BuildConfigInfo
import com.zenith.iptv.Graph
import com.zenith.iptv.MainActivity
import com.zenith.iptv.R
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PinDialog
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.SectionTitle
import com.zenith.iptv.ui.Z

private class Choice(val title: String, val options: List<Pair<String, String>>, val current: String, val onPick: (String) -> Unit)

@Composable
fun SettingsScreen(nav: Nav, activity: MainActivity) {
    val ctx = LocalContext.current
    val store = Graph.store
    val repo = Graph.repo
    val s by store.settings.collectAsState()
    val catalog by repo.catalog.collectAsState()
    var choice by remember { mutableStateOf<Choice?>(null) }
    var pinStep by remember { mutableStateOf(0) } // 1 = verify old, 2 = enter new
    var themePicker by remember { mutableStateOf(false) }

    val langs = listOf("" to stringResource(R.string.lang_system), "en" to "English", "ar" to "العربية", "fr" to "Français")
    val off = stringResource(R.string.off)

    ScreenScaffold(stringResource(R.string.settings), nav) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item { SectionTitle(stringResource(R.string.appearance)) }
            item {
                SettingRow(Icons.Default.Palette, stringResource(R.string.theme), Wallpapers.active(s)?.name ?: themeName(s.theme)) { themePicker = true }
            }
            Wallpapers.active(s)?.let { w ->
                item {
                    val t = stringResource(R.string.dimming)
                    val opts = listOf(
                        "0.3" to stringResource(R.string.dim_light), "0.45" to stringResource(R.string.dim_medium),
                        "0.6" to stringResource(R.string.dim_strong), "0.75" to stringResource(R.string.dim_max),
                    )
                    val cur = opts.minByOrNull { kotlin.math.abs(it.first.toFloat() - w.dim) }!!.first
                    SettingRow(Icons.Default.Brightness6, t, opts.first { it.first == cur }.second) {
                        choice = Choice(t, opts, cur) { v ->
                            store.updateSettings { st -> st.copy(wallpapers = st.wallpapers.map { if (it.id == w.id) it.copy(dim = v.toFloat()) else it }) }
                        }
                    }
                }
            }
            item {
                val t = stringResource(R.string.layout_mode)
                val opts = listOf(
                    "auto" to stringResource(R.string.layout_auto), "phone" to stringResource(R.string.layout_phone),
                    "tablet" to stringResource(R.string.layout_tablet), "tv" to stringResource(R.string.layout_tv),
                )
                SettingRow(Icons.Default.Devices, t, opts.firstOrNull { it.first == s.layoutMode }?.second ?: "") {
                    choice = Choice(t, opts, s.layoutMode) { v -> store.updateSettings { it.copy(layoutMode = v) } }
                }
            }
            item {
                val t = stringResource(R.string.ui_size)
                val opts = listOf(
                    "0.85" to stringResource(R.string.size_small), "1.0" to stringResource(R.string.size_normal),
                    "1.15" to stringResource(R.string.size_large), "1.3" to stringResource(R.string.size_xlarge),
                )
                val cur = "%.2f".format(java.util.Locale.US, s.uiScale).trimEnd('0').let { if (it.endsWith('.')) it + "0" else it }
                SettingRow(Icons.Default.FormatSize, t, opts.firstOrNull { it.first == cur }?.second ?: "${(s.uiScale * 100).toInt()}%") {
                    choice = Choice(t, opts, cur) { v -> store.updateSettings { it.copy(uiScale = v.toFloat()) } }
                }
            }

            item { SectionTitle(stringResource(R.string.general)) }
            item {
                val langTitle = stringResource(R.string.language)
                SettingRow(Icons.Default.Language, langTitle, langs.firstOrNull { it.first == s.language }?.second ?: "") {
                    choice = Choice(langTitle, langs, s.language) { tag ->
                        store.updateSettings { it.copy(language = tag) }
                        AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
                    }
                }
            }
            item { SettingRow(Icons.Default.PlaylistPlay, stringResource(R.string.playlists), repo.activePlaylist()?.name ?: "") { nav.go(Routes.PLAYLISTS) } }
            item { SettingRow(Icons.Default.AccountCircle, stringResource(R.string.profiles), store.activeProfile().name) { nav.go(Routes.profiles()) } }

            item { SectionTitle(stringResource(R.string.parental_control)) }
            item { SettingRow(Icons.Default.Password, stringResource(R.string.change_pin), "••••") { pinStep = 1 } }
            item { ToggleRow(Icons.Default.Lock, stringResource(R.string.auto_lock_adult), s.autoLockAdult) { v -> store.updateSettings { it.copy(autoLockAdult = v) } } }
            item {
                SettingRow(Icons.Default.Lock, stringResource(R.string.locked_categories), "${s.lockedCategories.size}") {
                    Toast.makeText(ctx, R.string.lock_hint, Toast.LENGTH_LONG).show()
                }
            }
            if (s.hiddenCategories.isNotEmpty()) item {
                SettingRow(Icons.Default.Visibility, stringResource(R.string.show_hidden_categories), "${s.hiddenCategories.size}") {
                    store.updateSettings { it.copy(hiddenCategories = emptyList()) }
                }
            }

            item { SectionTitle(stringResource(R.string.player)) }
            item {
                val t = stringResource(R.string.live_format)
                val opts = listOf("ts" to "MPEG-TS (.ts)", "m3u8" to "HLS (.m3u8)")
                SettingRow(Icons.Default.SettingsInputAntenna, t, opts.first { it.first == s.liveFormat }.second) {
                    choice = Choice(t, opts, s.liveFormat) { v -> store.updateSettings { it.copy(liveFormat = v) }; repo.reload() }
                }
            }
            item {
                val t = stringResource(R.string.buffer_size)
                val opts = listOf(3, 5, 10, 15, 30, 60).map { "$it" to "$it s" }
                SettingRow(Icons.Default.Timer, t, "${s.bufferSeconds} s") {
                    choice = Choice(t, opts, "${s.bufferSeconds}") { v -> store.updateSettings { it.copy(bufferSeconds = v.toInt()) } }
                }
            }
            item {
                val t = stringResource(R.string.aspect_ratio)
                val opts = listOf("fit" to stringResource(R.string.aspect_fit), "fill" to stringResource(R.string.aspect_fill), "zoom" to stringResource(R.string.aspect_zoom))
                SettingRow(Icons.Default.AspectRatio, t, opts.first { it.first == s.aspect }.second) {
                    choice = Choice(t, opts, s.aspect) { v -> store.updateSettings { it.copy(aspect = v) } }
                }
            }
            item {
                val t = stringResource(R.string.decoder)
                val opts = listOf("auto" to stringResource(R.string.decoder_auto), "software" to stringResource(R.string.decoder_software))
                SettingRow(Icons.Default.Memory, t, opts.firstOrNull { it.first == s.decoderMode }?.second ?: "") {
                    choice = Choice(t, opts, s.decoderMode) { v -> store.updateSettings { it.copy(decoderMode = v) } }
                }
            }
            item {
                val t = stringResource(R.string.player_engine)
                val opts = listOf(
                    "auto" to stringResource(R.string.engine_auto_vlc),
                    "exo" to "ExoPlayer",
                    "vlc" to "VLC",
                )
                SettingRow(Icons.Default.SettingsInputAntenna, t, opts.firstOrNull { it.first == s.playerEngine }?.second ?: "") {
                    choice = Choice(t, opts, s.playerEngine) { v -> store.updateSettings { it.copy(playerEngine = v) } }
                }
            }
            item { ToggleRow(Icons.Default.PictureInPictureAlt, stringResource(R.string.mini_player), s.miniPlayer) { v -> store.updateSettings { it.copy(miniPlayer = v) } } }
            item { ToggleRow(Icons.Default.OpenInNew, stringResource(R.string.external_player), s.externalPlayer) { v -> store.updateSettings { it.copy(externalPlayer = v) } } }

            item { SectionTitle(stringResource(R.string.tv_guide)) }
            item {
                val t = stringResource(R.string.epg_offset)
                val opts = (-12..12).map { h -> "${h * 30}" to (if (h == 0) off else "%+.1f h".format(h / 2.0)) }
                SettingRow(Icons.Default.Schedule, t, opts.firstOrNull { it.first == "${s.epgOffsetMinutes}" }?.second ?: "${s.epgOffsetMinutes} min") {
                    choice = Choice(t, opts, "${s.epgOffsetMinutes}") { v -> store.updateSettings { it.copy(epgOffsetMinutes = v.toInt()) } }
                }
            }
            item { ToggleRow(Icons.Default.Schedule, stringResource(R.string.clock_24h), s.use24h) { v -> store.updateSettings { it.copy(use24h = v) } } }
            item {
                SettingRow(Icons.Default.CalendarMonth, stringResource(R.string.reload_epg), catalog?.epgUrl?.take(40) ?: "") {
                    repo.loadEpg(force = true)
                    Toast.makeText(ctx, R.string.loading_epg, Toast.LENGTH_SHORT).show()
                }
            }
            item {
                val t = stringResource(R.string.auto_refresh)
                val opts = listOf(6, 12, 24, 72).map { "$it" to "$it h" }
                SettingRow(Icons.Default.Refresh, t, "${s.catalogMaxAgeHours} h") {
                    choice = Choice(t, opts, "${s.catalogMaxAgeHours}") { v -> store.updateSettings { it.copy(catalogMaxAgeHours = v.toInt()) } }
                }
            }

            item { SectionTitle(stringResource(R.string.about)) }
            if (!catalog?.accountInfo.isNullOrBlank()) item { SettingRow(Icons.Default.AccountCircle, stringResource(R.string.account), catalog!!.accountInfo) {} }
            item { SettingRow(Icons.Default.Favorite, stringResource(R.string.support_app), "PayPal · Neteller") { nav.go(Routes.ABOUT) } }
            item { SettingRow(Icons.Default.Info, stringResource(R.string.about_app), stringResource(R.string.idea_dev) + ": " + DEVELOPER + " · " + BuildConfigInfo.version(ctx)) { nav.go(Routes.ABOUT) } }
            item { SettingRow(Icons.Default.Email, stringResource(R.string.contact), CONTACT_EMAIL) { nav.go(Routes.ABOUT) } }
        }
    }

    choice?.let { c ->
        AlertDialog(
            onDismissRequest = { choice = null }, containerColor = Z.surface,
            title = { Text(c.title) },
            text = {
                LazyColumn {
                    items(c.options.size) { i ->
                        val (k, label) = c.options[i]
                        Focusable(onClick = { c.onPick(k); choice = null }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), selected = k == c.current, focusScale = 1.01f) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, color = Z.text, modifier = Modifier.weight(1f))
                                if (k == c.current) Icon(Icons.Default.Check, null, tint = Z.accent)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choice = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    if (themePicker) ThemePicker(s, onDismiss = { themePicker = false }) { id ->
        store.updateSettings { it.copy(theme = id) }
    }

    when (pinStep) {
        1 -> PinDialog(title = stringResource(R.string.current_pin), onDismiss = { pinStep = 0 }) { pin ->
            if (pin == s.parentalPin) pinStep = 2 else Toast.makeText(ctx, R.string.wrong_pin, Toast.LENGTH_SHORT).show()
        }
        2 -> PinDialog(title = stringResource(R.string.new_pin), onDismiss = { pinStep = 0 }) { pin ->
            if (pin.length >= 4) {
                store.updateSettings { it.copy(parentalPin = pin) }; pinStep = 0
                Toast.makeText(ctx, R.string.pin_changed, Toast.LENGTH_SHORT).show()
            } else Toast.makeText(ctx, R.string.pin_too_short, Toast.LENGTH_SHORT).show()
        }
    }
    @Suppress("UNUSED_EXPRESSION") activity
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Z.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, color = Z.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (value.isNotEmpty()) Text(value, color = Z.muted, fontSize = 13.sp, maxLines = 2, modifier = Modifier.widthIn(max = 320.dp))
        }
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Focusable(onClick = { onChange(!value) }, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f, shape = RoundedCornerShape(10.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Z.accent, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, color = Z.text, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = null, colors = SwitchDefaults.colors(checkedTrackColor = Z.accent))
        }
    }
}

@Composable
fun themeName(id: String): String = stringResource(
    when (id) {
        "royal" -> R.string.theme_royal
        "royal_night" -> R.string.theme_royal_night
        "cinema" -> R.string.theme_cinema
        "stadium" -> R.string.theme_stadium
        "sunset" -> R.string.theme_sunset
        "amoled" -> R.string.theme_amoled
        else -> R.string.theme_midnight
    }
)

@Composable
private fun ThemePicker(s: com.zenith.iptv.data.Settings, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val store = Graph.store
    val scope = rememberCoroutineScope()
    val current = s.theme
    var busy by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Wallpaper?>(null) }
    val defaultName = stringResource(R.string.my_picture) + " " + (s.wallpapers.size + 1)
    val failed = stringResource(R.string.wallpaper_failed)

    fun add(block: suspend () -> Wallpaper) {
        busy = true
        scope.launch {
            try {
                val w = block()
                store.updateSettings { it.copy(wallpapers = it.wallpapers + w, theme = Wallpapers.PREFIX + w.id) }
                Toast.makeText(ctx, R.string.wallpaper_added, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(ctx, failed + ": " + (e.message ?: ""), Toast.LENGTH_LONG).show()
            } finally { busy = false }
        }
    }

    val getContent = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) add { Wallpapers.fromUri(ctx, uri, defaultName) }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) add { Wallpapers.fromUri(ctx, uri, defaultName) }
    }
    fun pickFromDevice() {
        try { getContent.launch("image/*") } catch (e: ActivityNotFoundException) {
            try { openDoc.launch(arrayOf("image/*")) } catch (e2: ActivityNotFoundException) {
                Toast.makeText(ctx, R.string.no_file_picker, Toast.LENGTH_LONG).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Z.surface3,
        title = { Text(stringResource(R.string.theme)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.my_pictures), color = Z.text, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.my_pictures_hint), color = Z.muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddTile(Icons.Default.AddPhotoAlternate, stringResource(R.string.add_from_device), Modifier.weight(1f)) { pickFromDevice() }
                    AddTile(Icons.Default.Link, stringResource(R.string.add_from_link), Modifier.weight(1f)) { linkDialog = true }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Z.accent)
                s.wallpapers.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { w ->
                            val id = Wallpapers.PREFIX + w.id
                            Focusable(
                                onClick = { onPick(id) }, onLongClick = { toDelete = w }, selected = id == current, focusScale = 1.05f,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(10.dp)).background(Z.surface2)) {
                                        AsyncImage(model = File(w.file), contentDescription = w.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                        Row(Modifier.align(Alignment.BottomEnd).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                            listOf(w.base, w.accent, w.accent2).forEach { c ->
                                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c.toInt())).border(1.dp, Color.White.copy(alpha = .7f), CircleShape))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(w.name, color = Z.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                        if (id == current) Icon(Icons.Default.Check, null, tint = Z.accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (s.wallpapers.isNotEmpty()) Text(stringResource(R.string.delete_hint), color = Z.muted, fontSize = 11.sp)

                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.color_themes), color = Z.text, fontWeight = FontWeight.Bold)
                Themes.all.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { p ->
                            Focusable(
                                onClick = { onPick(p.id) }, selected = p.id == current, focusScale = 1.05f,
                                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    ThemePreview(p, Modifier.fillMaxWidth().height(78.dp))
                                    Spacer(Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(themeName(p.id), color = Z.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                        if (p.id == current) Icon(Icons.Default.Check, null, tint = Z.accent, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )

    if (linkDialog) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { linkDialog = false }, containerColor = Z.surface3,
            title = { Text(stringResource(R.string.add_from_link)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.image_link)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.image_link_hint), color = Z.muted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(enabled = url.startsWith("http"), onClick = {
                    val u = url; val n = name.ifBlank { defaultName }
                    linkDialog = false
                    add { Wallpapers.fromUrl(ctx, u, n) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { linkDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null }, containerColor = Z.surface3,
            title = { Text(w.name) },
            confirmButton = {
                TextButton(onClick = {
                    Wallpapers.delete(w)
                    store.updateSettings { st ->
                        st.copy(wallpapers = st.wallpapers.filter { it.id != w.id }, theme = if (st.theme == Wallpapers.PREFIX + w.id) "midnight" else st.theme)
                    }
                    toDelete = null
                }) { Text(stringResource(R.string.delete), color = Z.red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AddTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier.height(64.dp), shape = RoundedCornerShape(14.dp), background = Z.surface2) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Z.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, color = Z.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
        }
    }
}

/** Miniature drawing of the home screen in a given theme. */
@Composable
private fun ThemePreview(p: ThemePalette, modifier: Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(p.bgGlow, p.bg)))
            .border(1.dp, p.line, RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.text))
                Spacer(Modifier.width(3.dp))
                Box(Modifier.width(10.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                p.tiles.take(3).forEach { c -> Box(Modifier.weight(1f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(c)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2))
                Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(p.surface2))
                Box(Modifier.width(18.dp).height(10.dp).clip(RoundedCornerShape(3.dp)).background(p.accent))
            }
        }
    }
}
