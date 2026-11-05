package com.zenith.iptv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenith.iptv.R

/** "Z-Vision" word mark, plus the edition badge (e.g. MAX) in the custom edition. */
@Composable
fun BrandName(size: TextUnit) {
    val suffix = stringResource(R.string.brand_suffix)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Z-", color = Z.accent, fontWeight = FontWeight.Bold, fontSize = size)
        Text("Vision", color = Z.text, fontWeight = FontWeight.Bold, fontSize = size)
        if (suffix.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                suffix.uppercase(), color = Color(0xFF1A0F06), fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.48f).sp, letterSpacing = 2.sp,
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(Color(0xFFFFD15C), Color(0xFFFF8A1F), Color(0xFFFF4D6D))), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}
