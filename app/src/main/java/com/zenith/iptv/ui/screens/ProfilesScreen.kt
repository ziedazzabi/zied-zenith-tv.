package com.zenith.iptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Profile
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.PinDialog
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z

private val COLORS = listOf(0xFF22D3EE, 0xFFF472B6, 0xFFFBBF24, 0xFF34D399, 0xFFA78BFA, 0xFFFB923C)

/** "Who's watching?" — each profile has its own favourites, history and progress; kids profiles hide locked content. */
@Composable
fun ProfilesScreen(nav: Nav, pickMode: Boolean) {
    val store = Graph.store
    val profiles by store.profiles.collectAsState()
    val settings by store.settings.collectAsState()
    var editing by remember { mutableStateOf<Profile?>(null) }
    var pinFor by remember { mutableStateOf<Profile?>(null) }

    fun choose(p: Profile) {
        // leaving a kids profile requires the parental PIN
        val current = store.activeProfile()
        if (current.kids && !p.kids && current.id != p.id && !pickMode) { pinFor = p; return }
        store.switchProfile(p.id)
        nav.home()
    }

    ScreenScaffold(stringResource(R.string.whos_watching), nav, showBack = !pickMode) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                items(profiles, key = { it.id }) { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Focusable(
                            onClick = { choose(p) }, onLongClick = { editing = p }, shape = RoundedCornerShape(24.dp),
                            background = Color(p.color), focusedBackground = Color(p.color), focusScale = 1.1f,
                            selected = p.id == store.activeProfileId(),
                        ) {
                            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                                Text(p.name.take(1).uppercase(), color = Color.Black, fontSize = 48.sp, fontWeight = FontWeight.Black)
                                if (p.kids) Icon(Icons.Default.ChildCare, null, tint = Color.Black, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(p.name, color = Z.text, fontSize = 16.sp)
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Focusable(onClick = { editing = Profile(store.newId(), "", COLORS[profiles.size % COLORS.size]) }, shape = RoundedCornerShape(24.dp), focusScale = 1.1f) {
                            Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Z.muted, modifier = Modifier.size(48.dp)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.add_profile), color = Z.muted, fontSize = 16.sp)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.profile_hint), color = Z.muted, fontSize = 13.sp)
        }
    }

    editing?.let { p -> ProfileEditor(p, canDelete = profiles.size > 1 && profiles.any { it.id == p.id }, onDismiss = { editing = null }) }
    pinFor?.let { p ->
        PinDialog(onDismiss = { pinFor = null }) { pin ->
            if (pin == settings.parentalPin) { pinFor = null; store.switchProfile(p.id); nav.home() }
        }
    }
}

@Composable
private fun ProfileEditor(p: Profile, canDelete: Boolean, onDismiss: () -> Unit) {
    val store = Graph.store
    var name by remember { mutableStateOf(p.name) }
    var kids by remember { mutableStateOf(p.kids) }
    var color by remember { mutableStateOf(p.color) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Z.surface,
        title = { Text(stringResource(R.string.profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(20) }, singleLine = true, label = { Text(stringResource(R.string.name)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    COLORS.forEach { c ->
                        Focusable(onClick = { color = c }, shape = CircleShape, background = Color(c), focusedBackground = Color(c), selected = color == c) {
                            Box(Modifier.size(32.dp).clip(CircleShape)) { if (color == c) Box(Modifier.size(10.dp).align(Alignment.Center).clip(CircleShape).background(Color.Black)) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(kids, { kids = it })
                    Text(stringResource(R.string.kids_profile), color = Z.text)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isNotBlank()) store.saveProfile(p.copy(name = name.trim(), kids = kids, color = color))
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (canDelete) TextButton(onClick = { store.deleteProfile(p.id); onDismiss() }) { Text(stringResource(R.string.delete), color = Z.red) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
