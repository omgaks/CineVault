package com.sole.cinevault.network

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sole.cinevault.ui.theme.AmberCore
import com.sole.cinevault.ui.theme.TextFaint
import com.sole.cinevault.ui.theme.TextMuted
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun CineVaultInviteQr(payload: String, modifier: Modifier = Modifier, expiresAtEpochMs: Long? = null) {
    val bitmap = remember(payload) { encodeQr(payload, 720) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAtEpochMs) {
        while (expiresAtEpochMs != null) { delay(1000); now = System.currentTimeMillis() }
    }
    val secondsLeft = expiresAtEpochMs?.let { ((it - now) / 1000).coerceAtLeast(0) }
    val expired = secondsLeft == 0L
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("CINEVAULT INVITE", color = AmberCore, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.background(AmberCore.copy(alpha = 0.14f), RoundedCornerShape(22.dp)).border(1.5.dp, AmberCore.copy(alpha = 0.6f), RoundedCornerShape(22.dp)).padding(10.dp)) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "CineVault Nearby pairing QR code",
                modifier = Modifier.size(210.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).alpha(if (expired) 0.25f else 1f),
            )
        }
        if (secondsLeft != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                if (expired) "Invite expired. Stop and start sharing for a new code." else "Expires in ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                color = if (expired) TextMuted else TextFaint, fontSize = 12.sp,
            )
        }
    }
}

internal fun encodeQr(payload: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}
