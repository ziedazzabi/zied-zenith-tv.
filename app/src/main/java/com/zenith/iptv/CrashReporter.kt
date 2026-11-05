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
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.ui.Z
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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

    /** Sends the report through FormSubmit (forwards to the developer's e-mail). Returns true when delivered. */
    suspend fun sendOnline(report: String): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            val body = kotlinx.serialization.json.buildJsonObject {
                put("_subject", kotlinx.serialization.json.JsonPrimitive("Z-Vision crash report — ${Build.MANUFACTURER} ${Build.MODEL}"))
                put("_template", kotlinx.serialization.json.JsonPrimitive("box"))
                put("_captcha", kotlinx.serialization.json.JsonPrimitive("false"))
                put("report", kotlinx.serialization.json.JsonPrimitive(report))
            }.toString()
            val req = okhttp3.Request.Builder()
                .url("https://formsubmit.co/ajax/$EMAIL")
                .header("Accept", "application/json")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            com.zenith.iptv.data.Net.client.newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                r.isSuccessful && Regex("\"success\"\\s*:\\s*\"?true").containsMatchIn(text)
            }
        }.getOrDefault(false)
    }

    /** mailto: link small enough for a QR code (scan with a phone to send the report by e-mail). */
    fun mailtoForQr(report: String): String =
        "mailto:$EMAIL?subject=" + Uri.encode("Z-Vision crash report") + "&body=" + Uri.encode(report.take(1200))

    fun qrBitmap(text: String, size: Int = 480): android.graphics.Bitmap? = runCatching {
        val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size,
            mapOf(com.google.zxing.EncodeHintType.MARGIN to 1, com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L))
        val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bmp
    }.getOrNull()

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

/** Shown after a crash: what happened + send it to the developer (online, or QR code for a phone). */
@Composable
fun CrashReportDialog() {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var report by remember { mutableStateOf(CrashReporter.pending(ctx)) }
    var sending by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val r = report ?: return
    val dismiss = { CrashReporter.clear(ctx); report = null }
    AlertDialog(
        onDismissRequest = dismiss,
        containerColor = Z.surface,
        title = { Text(stringResource(R.string.crash_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.crash_text_online), color = Z.text)
                if (failed) {
                    val qr = remember(r) { CrashReporter.qrBitmap(CrashReporter.mailtoForQr(r)) }
                    Text(stringResource(R.string.crash_send_failed, CrashReporter.EMAIL), color = Z.amber, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    if (qr != null) androidx.compose.foundation.Image(
                        qr.asImageBitmap(), null,
                        Modifier.padding(top = 10.dp).size(200.dp).background(androidx.compose.ui.graphics.Color.White).padding(6.dp),
                    )
                }
                Text(
                    r, color = Z.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 10.dp).heightIn(max = 200.dp)
                        .background(Z.surface2, RoundedCornerShape(8.dp)).padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = !sending, onClick = {
                sending = true
                scope.launch {
                    val ok = CrashReporter.sendOnline(r)
                    sending = false
                    if (ok) { android.widget.Toast.makeText(ctx, R.string.report_sent, android.widget.Toast.LENGTH_LONG).show(); dismiss() }
                    else failed = true
                }
            }) { Text(stringResource(if (sending) R.string.sending else R.string.send_report)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.close)) } },
    )
}
