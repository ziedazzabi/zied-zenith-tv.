package com.zenith.iptv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.ui.Z
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the details of a crash so the next launch can show them and send them to the developer. */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    const val EMAIL = "zied.azzabi@gmail.com"

    fun install(app: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val rt = Runtime.getRuntime()
                val text = buildString {
                    appendLine("Z-Vision ${BuildConfigInfo.version(app)} — ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
                    appendLine("Memory: max ${rt.maxMemory() / 1048576} MB, used ${(rt.totalMemory() - rt.freeMemory()) / 1048576} MB")
                    appendLine("Thread: ${thread.name}")
                    appendLine()
                    append(sw.toString().take(12_000))
                }
                File(app.filesDir, FILE).writeText(text)
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun pending(ctx: Context): String? = File(ctx.filesDir, FILE).takeIf { it.exists() }?.readText()
    fun clear(ctx: Context) { File(ctx.filesDir, FILE).delete() }

    fun send(ctx: Context, report: String) {
        val uri = Uri.parse("mailto:$EMAIL?subject=" + Uri.encode("Z-Vision crash report") + "&body=" + Uri.encode(report))
        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure {
                runCatching {
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL)).putExtra(Intent.EXTRA_SUBJECT, "Z-Vision crash report")
                        .putExtra(Intent.EXTRA_TEXT, report), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
    }
}

/** Shown once after a crash: what happened + "send to developer". */
@Composable
fun CrashReportDialog() {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf(CrashReporter.pending(ctx)) }
    val r = report ?: return
    val dismiss = { CrashReporter.clear(ctx); report = null }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Z.surface,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column {
                Text(stringResource(R.string.crash_text, CrashReporter.EMAIL), color = Z.text)
                Text(
                    r, color = Z.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 10.dp).heightIn(max = 260.dp)
                        .background(Z.surface2, RoundedCornerShape(8.dp)).padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = { CrashReporter.send(ctx, r); dismiss() }) { Text(stringResource(R.string.send_report)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } },
    )
}
