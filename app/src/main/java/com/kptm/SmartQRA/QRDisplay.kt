package com.kptm.SmartQRA

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.kptm.SmartQRA.databinding.ActivityQrdisplayBinding
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID

class QRDisplay : AppCompatActivity() {

    private lateinit var binding: ActivityQrdisplayBinding
    private var countDownTimer: CountDownTimer? = null
    private var timeLeftInMillis: Long = 0
    private var isSessionActive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrdisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val eventName = intent.getStringExtra("EVENT_NAME") ?: "Event"
        val location = intent.getStringExtra("LOCATION") ?: ""
        val manager = intent.getStringExtra("MANAGED_BY") ?: ""
        val date = intent.getStringExtra("DATE") ?: ""
        val durationMin = intent.getLongExtra("DURATION_MIN", 10)

        binding.tvEventTitle.text = eventName
        timeLeftInMillis = durationMin * 60 * 1000

        // Create Unique Session Payload for QR Code
        val sessionId = UUID.randomUUID().toString()
        val qrPayload = "QRAS_SESSION::$sessionId::$eventName::$location::$date"

        // Generate QR Code Image
        val qrBitmap = generateQrCode(qrPayload)
        binding.ivQrCode.setImageBitmap(qrBitmap)

        startTimer()

        // Button: Add 5 minutes
        binding.btnAddTime.setOnClickListener {
            if (isSessionActive) {
                timeLeftInMillis += 5 * 60 * 1000
                countDownTimer?.cancel()
                startTimer()
            } else {
                // Reactivate session
                isSessionActive = true
                timeLeftInMillis = 5 * 60 * 1000
                binding.tvStatus.text = "Status: Active"
                binding.tvStatus.setTextColor(Color.parseColor("#16A34A"))
                binding.ivQrCode.clearColorFilter()
                binding.ivQrCode.setImageBitmap(qrBitmap)
                startTimer()
            }
        }

        // Button: Stop Session Immediately
        binding.btnStopQr.setOnClickListener {
            stopSession()
        }
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                updateTimerText()
            }

            override fun onFinish() {
                stopSession()
            }
        }.start()
    }

    private fun updateTimerText() {
        val minutes = (timeLeftInMillis / 1000) / 60
        val seconds = (timeLeftInMillis / 1000) % 60
        binding.tvTimer.text = String.format("Time Remaining: %02d:%02d", minutes, seconds)
    }

    private fun stopSession() {
        countDownTimer?.cancel()
        isSessionActive = false
        binding.tvTimer.text = "Time Remaining: 00:00"
        binding.tvStatus.text = "Status: Expired / Stopped"
        binding.tvStatus.setTextColor(Color.parseColor("#DC2626"))

        // Grey out QR code when stopped
        binding.ivQrCode.setColorFilter(Color.GRAY)
    }

    private fun generateQrCode(text: String): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 512, 512)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}