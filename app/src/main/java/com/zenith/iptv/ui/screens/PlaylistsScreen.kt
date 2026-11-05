package com.zenith.iptv.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Playlist
import com.zenith.iptv.data.Preset
import com.zenith.iptv.data.Presets
import com.zenith.iptv.data.SourceType
import com.zenith.iptv.ui.Chip
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PrefillHolder
import com.zenith.iptv.ui.Routes
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z
import kotlin.random.Random

@Composable
fun PlaylistsScreen(nav: Nav) {
    val store = Graph.store
    val repo = Graph.repo
    val playlists by store.playlists.collectAsState()
    val settings by store.settings.collectAsState()
    var confirmDelete by remember { mutableStateOf<Playlist?>(null) }

    ScreenScaffold(stringResource(R.string.playlists), nav, actions = {
        ActionButton(stringResource(R.string.add_playlist), Icons.Default.Add, primary = true) { nav.go(Routes.addPlaylist()) }
    }) {
        if (playlists.isEmpty()) { EmptyState(stringResource(R.string.no_playlist)); return@ScreenScaffold }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(playlists, key = { it.id }) { p ->
                val active = p.id == (settings.activePlaylistId.ifBlank { playlists.first().id })
                Focusable(
                    onClick = { repo.open(p); nav.home() },
                    onLongClick = { nav.go(Routes.addPlaylist(p.id)) },
                    selected = active, modifier = Modifier.fillMaxWidth(), focusScale = 1.01f,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = if (active) Z.accent else Z.line, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, color = Z.text, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${typeLabel(p.type)} · ${p.url}", color = Z.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Focusable(onClick = { repo.open(p, forceRefresh = true); nav.home() }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Default.Refresh, stringResource(R.string.refresh), tint = Z.muted, modifier = Modifier.padding(8.dp))
                        }
                        Focusable(onClick = { nav.go(Routes.addPlaylist(p.id)) }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Default.Edit, stringResource(R.string.edit), tint = Z.muted, modifier = Modifier.padding(8.dp))
                        }
                        Focusable(onClick = { confirmDelete = p }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Z.muted, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null }, containerColor = Z.surface,
            title = { Text(stringResource(R.string.delete_playlist_q)) }, text = { Text(p.name) },
            confirmButton = {
                TextButton(onClick = {
                    store.deletePlaylist(p.id); confirmDelete = null
                    repo.activePlaylist()?.let { repo.open(it) }
                }) { Text(stringResource(R.string.delete), color = Z.red) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
fun typeLabel(t: SourceType) = stringResource(
    when (t) { SourceType.XTREAM -> R.string.xtream; SourceType.M3U -> R.string.m3u; SourceType.STALKER -> R.string.mac_portal }
)

@Composable
fun AddPlaylistScreen(nav: Nav, editId: String) {
    val store = Graph.store
    val repo = Graph.repo
    val ctx = LocalContext.current
    val existing = remember(editId) { store.playlists.value.firstOrNull { it.id == editId } }
    val prefill = remember { PrefillHolder.m3u.also { PrefillHolder.m3u = null } }
    val preset = remember { Presets.get(ctx) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    if (preset != null && !advanced && (existing == null || Presets.serverIndexOf(preset, existing) >= 0)) {
        PresetLogin(nav, preset, existing, onAdvanced = if (preset.allowOtherSources) ({ advanced = true }) else null)
        return
    }

    var type by rememberSaveable { mutableStateOf(existing?.type ?: if (prefill != null) SourceType.M3U else SourceType.XTREAM) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(existing?.url ?: prefill ?: "") }
    var user by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var mac by rememberSaveable { mutableStateOf(existing?.mac ?: randomMac()) }
    var epg by rememberSaveable { mutableStateOf(existing?.epgUrl ?: "") }
    var ua by rememberSaveable { mutableStateOf(existing?.userAgent ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            url = uri.toString()
            if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: ""
        }
    }

    // Users often paste a full get.php?username=…&password=… link — split it into Xtream fields.
    fun maybeSplitXtream(v: String) {
        val t = v.trim()
        if (!t.startsWith("http://", true) && !t.startsWith("https://", true)) return
        val u = runCatching { Uri.parse(t) }.getOrNull() ?: return
        if (!u.isHierarchical) return
        val un = runCatching { u.getQueryParameter("username") }.getOrNull()
        val pw = runCatching { u.getQueryParameter("password") }.getOrNull()
        if (!un.isNullOrBlank() && !pw.isNullOrBlank() && ("get.php" in v || "player_api.php" in v)) {
            type = SourceType.XTREAM
            url = "${u.scheme}://${u.authority}"
            user = un; pass = pw
        }
    }

    fun save() {
        error = null
        if (url.isBlank()) { error = ctx.getString(R.string.err_url); return }
        // "server.com:8080" → "http://server.com:8080"
        val raw = url.trim()
        if (!raw.contains("://") && !raw.startsWith("/")) url = "http://$raw"
        if (type == SourceType.XTREAM && (user.isBlank() || pass.isBlank())) { error = ctx.getString(R.string.err_credentials); return }
        if (type == SourceType.STALKER && !Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())) { error = ctx.getString(R.string.err_mac); return }
        val p = Playlist(
            id = existing?.id ?: store.newId(),
            name = name.ifBlank { runCatching { Uri.parse(url).host }.getOrNull() ?: ctx.getString(R.string.my_playlist) },
            type = type, url = url.trim(), username = user.trim(), password = pass.trim(),
            mac = mac.trim().uppercase(), epgUrl = epg.trim(), userAgent = ua.trim(),
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        )
        store.savePlaylist(p)
        repo.open(p, forceRefresh = true)
        nav.home()
    }

    ScreenScaffold(stringResource(if (existing == null) R.string.add_playlist else R.string.edit_playlist), nav) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceType.entries.forEach { t -> Chip(typeLabel(t), type == t, onClick = { type = t }) }
                }
                Field(stringResource(R.string.playlist_name), name, { name = it })
                when (type) {
                    SourceType.XTREAM -> {
                        Field(stringResource(R.string.server_url), url, { url = it; maybeSplitXtream(it) }, hint = "http://example.com:8080", keyboard = KeyboardType.Uri)
                        Field(stringResource(R.string.username), user, { user = it })
                        Field(stringResource(R.string.password), pass, { pass = it }, password = true)
                    }
                    SourceType.M3U -> {
                        Field(stringResource(R.string.m3u_url), url, { url = it; maybeSplitXtream(it) }, hint = "http://…/playlist.m3u", keyboard = KeyboardType.Uri)
                        ActionButton(stringResource(R.string.pick_file), Icons.Default.FolderOpen) {
                            runCatching { picker.launch(arrayOf("*/*")) }
                        }
                    }
                    SourceType.STALKER -> {
                        Field(stringResource(R.string.portal_url), url, { url = it }, hint = "http://portal.example.com/c/", keyboard = KeyboardType.Uri)
                        Field(stringResource(R.string.mac_address), mac, { mac = it }, hint = "00:1A:79:XX:XX:XX")
                    }
                }
                Text(stringResource(R.string.optional), color = Z.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                Field(stringResource(R.string.epg_url), epg, { epg = it }, hint = stringResource(R.string.epg_url_hint), keyboard = KeyboardType.Uri)
                Field(stringResource(R.string.user_agent), ua, { ua = it })
                error?.let { Text(it, color = Z.red, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
                ActionButton(stringResource(R.string.save_and_load), Icons.Default.CheckCircle, primary = true) { save() }
            }
        }
    }
}

/** Custom edition: the servers are built in, the user only signs in. */
@Composable
private fun PresetLogin(nav: Nav, preset: Preset, existing: Playlist?, onAdvanced: (() -> Unit)?) {
    val store = Graph.store
    val repo = Graph.repo
    val ctx = LocalContext.current
    var server by rememberSaveable { mutableStateOf(Presets.serverIndexOf(preset, existing).coerceAtLeast(0)) }
    var user by rememberSaveable { mutableStateOf(existing?.username ?: "") }
    var pass by rememberSaveable { mutableStateOf(existing?.password ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun signIn() {
        error = null
        if (user.isBlank() || pass.isBlank()) { error = ctx.getString(R.string.err_credentials); return }
        val s = preset.servers.getOrNull(server) ?: preset.servers.first()
        val p = Presets.playlist(
            existing?.id ?: store.newId(), preset, s, user.trim(), pass.trim(),
            existing?.createdAt ?: System.currentTimeMillis(),
        ).copy(userAgent = existing?.userAgent ?: "")
        store.savePlaylist(p)
        repo.open(p, forceRefresh = true)
        nav.home()
    }

    ScreenScaffold(stringResource(R.string.login_title), nav) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preset.providerName.isNotBlank()) Text(preset.providerName, color = Z.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.login_hint), color = Z.muted, fontSize = 14.sp)
                if (preset.servers.size > 1) {
                    Text(stringResource(R.string.choose_server), color = Z.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        preset.servers.forEachIndexed { i, s ->
                            Chip(s.name.ifBlank { "${stringResource(R.string.choose_server)} ${i + 1}" }, server == i, onClick = { server = i })
                        }
                    }
                }
                Field(stringResource(R.string.username), user, { user = it })
                Field(stringResource(R.string.password), pass, { pass = it }, password = true)
                error?.let { Text(it, color = Z.red, fontSize = 13.sp) }
                Spacer(Modifier.height(4.dp))
                ActionButton(stringResource(R.string.sign_in), Icons.Default.CheckCircle, primary = true) { signIn() }
                onAdvanced?.let { ActionButton(stringResource(R.string.add_playlist), Icons.Default.Add) { it() } }
            }
        }
    }
}

@Composable
private fun Field(
    label: String, value: String, onChange: (String) -> Unit, hint: String = "",
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        placeholder = { if (hint.isNotEmpty()) Text(hint, color = Z.muted) },
        trailingIcon = if (password) ({
            IconButton(onClick = { shown = !shown }) {
                Icon(
                    if (shown) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    stringResource(if (shown) R.string.hide_password else R.string.show_password),
                    tint = if (shown) Z.accent else Z.muted,
                )
            }
        }) else null,
        visualTransformation = if (password && !shown) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password && !shown) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Z.accent, focusedLabelColor = Z.accent),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun randomMac(): String =
    "00:1A:79:" + (1..3).joinToString(":") { "%02X".format(Random.nextInt(256)) }
