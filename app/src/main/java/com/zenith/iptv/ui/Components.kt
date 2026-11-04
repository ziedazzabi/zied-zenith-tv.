package com.zenith.iptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.zenith.iptv.R
import com.zenith.iptv.data.EpgProgram
import com.zenith.iptv.data.Media
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Focus-aware clickable surface: works with touch and with a TV remote (OK = click, MENU or long-press = secondary).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    focusScale: Float = 1.04f,
    background: Color = Z.surface,
    focusedBackground: Color = Z.surface3,
    onFocus: (() -> Unit)? = null,
    content: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(if (focused) focusedBackground else if (selected) Z.surface2 else background)
            .border(
                width = if (focused) 2.dp else if (selected) 1.dp else 0.dp,
                color = if (focused) Z.accent else if (selected) Z.accent.copy(alpha = .5f) else Color.Transparent,
                shape = shape,
            )
            .onFocusChanged { val f = it.isFocused; if (f && !focused) onFocus?.invoke(); focused = f }
            .onPreviewKeyEvent { ev ->
                if (onLongClick != null && ev.type == KeyEventType.KeyUp && ev.key == Key.Menu) { onLongClick(); true } else false
            }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) { content(focused) }
}

@Composable
fun Logo(url: String, name: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    SubcomposeAsyncImage(
        model = url.ifBlank { null },
        contentDescription = name,
        contentScale = contentScale,
        modifier = modifier,
        error = { LetterTile(name) },
        loading = { LetterTile(name) },
    )
}

@Composable
fun LetterTile(name: String) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    val hue = (name.hashCode() and 0xFFFFFF)
    val c1 = Color(0xFF000000L or (hue.toLong() and 0x7F7F7FL) or 0x303040L)
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, Z.surface3))),
        contentAlignment = Alignment.Center
    ) { Text(letter, color = Z.text, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { Icon(icon, null, tint = Z.accent, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Z.text)
    }
}

/** Poster card for movies/series. */
@Composable
fun PosterCard(
    item: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Adaptive.poster(),
    favorite: Boolean = false,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
) {
    Focusable(onClick = onClick, onLongClick = onLongClick, modifier = modifier.width(width), focusScale = 1.07f) { focused ->
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Z.surface2)) {
                Logo(item.logo, item.name, Modifier.fillMaxSize(), ContentScale.Crop)
                if (item.rating.isNotBlank() && item.rating != "0") {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = .65f)).padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Z.amber, modifier = Modifier.size(11.dp))
                            Text(item.rating.take(3), color = Z.text, fontSize = 11.sp)
                        }
                    }
                }
                if (favorite) Icon(Icons.Default.Favorite, null, tint = Z.pink, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(16.dp))
                if (progress > 0f) LinearProgressIndicator(
                    progress = { progress }, color = Z.accent, trackColor = Color.Black.copy(alpha = .5f),
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
                )
            }
            Text(
                item.name, maxLines = if (focused) 2 else 1, overflow = TextOverflow.Ellipsis,
                color = if (focused) Z.text else Z.muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}

/** Channel row used in live lists: number, logo, name, now playing + progress. */
@Composable
fun ChannelRow(
    item: Media,
    now: EpgProgram?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    favorite: Boolean = false,
    locked: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
) {
    Focusable(
        onClick = onClick, onLongClick = onLongClick, onFocus = onFocus, selected = selected,
        modifier = modifier.fillMaxWidth(), focusScale = 1.01f, shape = RoundedCornerShape(10.dp),
    ) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.number.toString(), color = Z.muted, fontSize = 13.sp, textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
            Spacer(Modifier.width(10.dp))
            Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)).background(Z.surface2)) {
                Logo(item.logo, item.name, Modifier.fillMaxSize().padding(3.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name, color = Z.text, fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.catchupDays > 0) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.History, stringResource(R.string.catchup), tint = Z.accent, modifier = Modifier.size(14.dp))
                    }
                    if (favorite) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Favorite, null, tint = Z.pink, modifier = Modifier.size(14.dp)) }
                    if (locked) { Spacer(Modifier.width(4.dp)); Icon(Icons.Default.Lock, null, tint = Z.amber, modifier = Modifier.size(14.dp)) }
                }
                if (now != null) {
                    Text(now.title, color = Z.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { now.progress() }, color = Z.accent, trackColor = Z.line,
                        modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Focusable(
        onClick = onClick, selected = selected, modifier = modifier, shape = RoundedCornerShape(50),
        background = if (selected) Z.accent.copy(alpha = .18f) else Z.surface,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = if (selected) Z.accent else Z.muted, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, color = if (selected) (if (Z.palette.light) Z.text else Z.accent) else Z.text, fontSize = 13.sp, maxLines = 1, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
fun PinDialog(title: String = stringResource(R.string.enter_pin), onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    val fr = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Z.surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin, onValueChange = { v -> pin = v.filter { it.isDigit() }.take(8) },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                keyboardActions = KeyboardActions(onDone = { onPin(pin) }),
                modifier = Modifier.fillMaxWidth().focusRequester(fr),
            )
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
        },
        confirmButton = { TextButton(onClick = { onPin(pin) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = Z.muted, textAlign = TextAlign.Center)
    }
}

private val hhmm24 = SimpleDateFormat("HH:mm", Locale.getDefault())
private val hhmm12 = SimpleDateFormat("h:mm a", Locale.getDefault())
fun timeText(ms: Long, use24h: Boolean): String = synchronized(hhmm24) { (if (use24h) hhmm24 else hhmm12).format(Date(ms)) }
fun dayText(ms: Long): String = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(ms))

fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
