package com.kptm.SmartQRA

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions

class ScanQrActivity : AppCompatActivity() {

    // Register the scanner launcher activity result
    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            finish() // Close scanner screen if cancelled
        } else {
            val scannedData = result.contents
            Toast.makeText(this, "Scanned: $scannedData", Toast.LENGTH_LONG).show()

            // TODO: Pass 'scannedData' to your backend / Firebase or process it here

            finish() // Close scanner screen after successful scan
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch ZXing Scanner with options
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Align the admin QR code within the frame")
            setCameraId(0) // Use back camera
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }

        barcodeLauncher.launch(options)
    }
}