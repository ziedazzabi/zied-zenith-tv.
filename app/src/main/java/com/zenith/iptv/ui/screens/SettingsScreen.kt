package com.zenith.iptv.ui.screens

import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
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

    val langs = listOf("" to stringResource(R.string.lang_system), "en" to "English", "ar" to "العربية", "fr" to "Français")
    val off = stringResource(R.string.off)

    ScreenScaffold(stringResource(R.string.settings), nav) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)
        ) {
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
            item { SettingRow(Icons.Default.Info, "Zenith TV", BuildConfigInfo.version(ctx)) {} }
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
