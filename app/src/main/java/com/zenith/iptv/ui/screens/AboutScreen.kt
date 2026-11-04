package com.zenith.iptv.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.BuildConfigInfo
import com.zenith.iptv.R
import com.zenith.iptv.ui.Adaptive
import com.zenith.iptv.ui.Focusable
import com.zenith.iptv.ui.LocalIsTv
import com.zenith.iptv.ui.Nav
import com.zenith.iptv.ui.ScreenScaffold
import com.zenith.iptv.ui.SectionTitle
import com.zenith.iptv.ui.Z

const val DEVELOPER = "Zied Azzabi"
const val CONTACT_EMAIL = "zied.azzabi@gmail.com"
const val DONATE_EMAIL = "zied.azzabi@yahoo.fr"
const val PAYPAL_URL = "https://www.paypal.com/donate/?business=zied.azzabi%40yahoo.fr&no_recurring=0&currency_code=USD"

/** About Z-Vision: who made it, how to reach him, and how to support it (PayPal / Neteller). */
@Composable
fun AboutScreen(nav: Nav) {
    val ctx = LocalContext.current
    val isTv = LocalIsTv.current
    val wide = Adaptive.wide()
    ScreenScaffold(stringResource(R.string.about_app), nav) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 900.dp).fillMaxWidth()) {
                // Header: logo, name, version, credits
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                    Image(painterResource(R.drawable.logo_mark), null, Modifier.size(if (isTv) 96.dp else 72.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Row {
                            Text("Z-", color = Z.accent, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                            Text("Vision", color = Z.text, fontWeight = FontWeight.Bold, fontSize = 28.sp)
                        }
                        Text(stringResource(R.string.version_n, BuildConfigInfo.version(ctx)), color = Z.muted, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.idea_dev) + ": " + DEVELOPER, color = Z.text, fontWeight = FontWeight.SemiBold)
                    }
                }

                SectionTitle(stringResource(R.string.contact), icon = Icons.Default.Email)
                Text(stringResource(R.string.contact_hint), color = Z.muted, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                PayCard(
                    title = DEVELOPER, subtitle = CONTACT_EMAIL + "\n" + stringResource(R.string.scan_hint), qr = R.drawable.qr_contact, wide = wide,
                    actions = listOf(
                        Triple(Icons.Default.Email, stringResource(R.string.send_email)) { sendEmail(ctx) },
                        Triple(Icons.Default.ContentCopy, stringResource(R.string.copy_email)) { copy(ctx, CONTACT_EMAIL) },
                    ),
                )

                Spacer(Modifier.height(12.dp))
                SectionTitle(stringResource(R.string.support_app), icon = Icons.Default.Favorite)
                Text(stringResource(R.string.donate_text), color = Z.text, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                PayCard(
                    title = "PayPal", subtitle = stringResource(R.string.paypal_hint) + "\n" + DONATE_EMAIL, qr = R.drawable.qr_paypal, wide = wide,
                    badge = Color(0xFF003087),
                    actions = listOf(
                        Triple(Icons.Default.OpenInBrowser, stringResource(R.string.open_paypal)) { open(ctx, PAYPAL_URL) },
                        Triple(Icons.Default.ContentCopy, stringResource(R.string.copy_email)) { copy(ctx, DONATE_EMAIL) },
                    ),
                )
                Spacer(Modifier.height(12.dp))
                PayCard(
                    title = "Neteller", subtitle = stringResource(R.string.neteller_hint) + "\n" + DONATE_EMAIL, qr = R.drawable.qr_neteller, wide = wide,
                    badge = Color(0xFF8DC63F),
                    actions = listOf(Triple(Icons.Default.ContentCopy, stringResource(R.string.copy_email)) { copy(ctx, DONATE_EMAIL) }),
                )
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.thanks_support), color = Z.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun PayCard(
    title: String,
    subtitle: String,
    @DrawableRes qr: Int,
    wide: Boolean,
    actions: List<Triple<ImageVector, String, () -> Unit>>,
    badge: Color? = null,
) {
    val content: @Composable () -> Unit = {
        Image(painterResource(qr), null, Modifier.size(if (wide) 150.dp else 130.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Z.surface).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            content()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) {
                        Spacer(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(badge))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(title, color = Z.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Z.muted, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { (icon, label, action) ->
                        Focusable(onClick = action, shape = RoundedCornerShape(10.dp), background = Z.surface2) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = Z.accent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(label, color = Z.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun open(ctx: Context, url: String) {
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (e: Exception) { Toast.makeText(ctx, R.string.scan_hint, Toast.LENGTH_LONG).show() }
}

private fun sendEmail(ctx: Context) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$CONTACT_EMAIL?subject=Z-Vision")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) { copy(ctx, CONTACT_EMAIL) }
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("email", text))
    Toast.makeText(ctx, R.string.email_copied, Toast.LENGTH_SHORT).show()
}
