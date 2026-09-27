package com.sole.cinevault.network

import android.content.Context
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Permission-free QR scanner backed by Google Code Scanner.
 * CineVault receives only the decoded text; camera UI and permission handling
 * stay outside the app process.
 */
class CineVaultQrScanner(context: Context) {
    private val scanner = GmsBarcodeScanning.getClient(
        context.applicationContext,
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build(),
    )

    fun scan(
        onResult: (String) -> Unit,
        onCancelled: () -> Unit = {},
        onError: (String) -> Unit,
    ) {
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val value = barcode.rawValue
                if (value.isNullOrBlank()) onError("The QR code did not contain a connection invite.")
                else onResult(value)
            }
            .addOnCanceledListener { onCancelled() }
            .addOnFailureListener {
                onError("QR scanner could not start. You can still use Find Devices on the same network.")
            }
    }
}
