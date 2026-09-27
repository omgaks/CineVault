package com.sole.cinevault.network

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@Composable
fun CineVaultInviteQr(payload: String, modifier: Modifier = Modifier) {
    val bitmap = remember(payload) { encodeQr(payload, 720) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "CineVault Nearby pairing QR code",
        modifier = modifier.size(210.dp).background(Color.White),
    )
}

internal fun encodeQr(payload: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until size) for (x in 0 until size) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}
