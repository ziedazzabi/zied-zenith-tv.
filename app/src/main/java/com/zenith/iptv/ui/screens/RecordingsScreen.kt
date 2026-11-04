package com.zenith.iptv.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.Graph
import com.zenith.iptv.R
import com.zenith.iptv.data.Kind
import com.zenith.iptv.data.Media
import com.zenith.iptv.data.RecordingState
import com.zenith.iptv.player.PlayRequest
import com.zenith.iptv.recording.Recordings
import com.zenith.iptv.ui.EmptyState
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.Z
import com.zenith.iptv.ui.dayText
import com.zenith.iptv.ui.timeText
import java.io.File

@Composable
fun RecordingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val store = Graph.store
    val recs by store.recordings.collectAsState()
    val settings by store.settings.collectAsState()

    ScreenScaffold(stringResource(R.string.recordings), nav) {
        if (recs.isEmpty()) { EmptyState(stringResource(R.string.no_recordings)); return@ScreenScaffold }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(recs.sortedByDescending { it.start }, key = { it.id }) { r ->
                val (icon, color, label) = when (r.state) {
                    RecordingState.SCHEDULED -> Triple(Icons.Default.Schedule, Z.amber, stringResource(R.string.state_scheduled))
                    RecordingState.RECORDING -> Triple(Icons.Default.FiberManualRecord, Z.red, stringResource(R.string.state_recording))
                    RecordingState.DONE -> Triple(Icons.Default.PlayArrow, Z.green, stringResource(R.string.state_done))
                    RecordingState.FAILED -> Triple(Icons.Default.FiberManualRecord, Z.muted, stringResource(R.string.state_failed))
                    RecordingState.CANCELLED -> Triple(Icons.Default.Stop, Z.muted, stringResource(R.string.state_cancelled))
                }
                val playable = r.filePath.isNotBlank() && File(r.filePath).exists() && r.state != RecordingState.SCHEDULED
                Focusable(
                    onClick = {
                        if (playable) {
                            val m = Media(id = "rec${r.id}", playlistId = "rec", kind = Kind.MOVIE, name = r.title, categoryId = "")
                            nav.play(PlayRequest(listOf(m), urlOverride = Uri.fromFile(File(r.filePath)).toString(), title = "${r.channelName} · ${r.title}"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), focusScale = 1.01f,
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, color = Z.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(
                                "${r.channelName} · ${dayText(r.start)} ${timeText(r.start, settings.use24h)}–${timeText(r.stop, settings.use24h)} · $label" +
                                    (if (r.sizeBytes > 0) " · ${"%.1f".format(r.sizeBytes / 1_048_576.0)} MB" else "") +
                                    (if (r.error.isNotBlank()) " · ${r.error}" else ""),
                                color = Z.muted, fontSize = 12.sp, maxLines = 2,
                            )
                        }
                        if (r.state == RecordingState.RECORDING || r.state == RecordingState.SCHEDULED) {
                            Focusable(onClick = { Recordings.stop(ctx, r.id) }, shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Stop, stringResource(R.string.stop), tint = Z.text, modifier = Modifier.padding(8.dp))
                            }
                        }
                        Focusable(onClick = { Recordings.delete(ctx, r.id) }, shape = RoundedCornerShape(50)) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = Z.muted, modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }
    }
}
