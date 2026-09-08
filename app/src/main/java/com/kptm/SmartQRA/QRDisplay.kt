package com.kptm.SmartQRA

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.kptm.SmartQRA.databinding.ActivityQrdisplayBinding

class QRDisplay : AppCompatActivity() {

    private lateinit var binding: ActivityQrdisplayBinding
    private var countDownTimer: CountDownTimer? = null

    private var timeLeftInMillis: Long = 600000L
    private var eventId: String = ""
    private var currentQrBitmap: Bitmap? = null

    private var eventName: String = ""
    private var location: String = ""
    private var manager: String = ""
    private var eventDate: String = ""
    private var maxPeople: Int = -1

    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityQrdisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve intent extras
        eventId = intent.getStringExtra("EVENT_ID") ?: ""
        eventName = intent.getStringExtra("EVENT_NAME") ?: "Session QR"
        location = intent.getStringExtra("LOCATION") ?: "N/A"
        manager = intent.getStringExtra("MANAGED_BY") ?: "N/A"
        eventDate = intent.getStringExtra("DATE") ?: ""
        val durationMin = intent.getLongExtra("DURATION_MIN", 10L)
        maxPeople = intent.getIntExtra("MAX_PEOPLE", -1)

        binding.tvEventTitle.text = eventName

        if (eventId.isEmpty()) {
            Toast.makeText(this, "Invalid Event ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Header Back Arrow: Navigates back without ending session
        binding.btnBack.setOnClickListener {
            leaveScreenWithoutEnding()
        }

        // System Back Navigation: Keeps session active
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveScreenWithoutEnding()
            }
        })

        // Add 5 Minutes (+300,000 ms)
        binding.btnAddTime.setOnClickListener {
            timeLeftInMillis += 300000L

            // Push updated target end time to Firestore
            val updatedTargetEndTime = System.currentTimeMillis() + timeLeftInMillis
            firestore.collection("events").document(eventId)
                .update("targetEndTime", updatedTargetEndTime)
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to extend time: ${it.message}", Toast.LENGTH_SHORT).show()
                }

            startLiveQrTimer()
        }

        // Stop QR: Ends session permanently
        binding.btnStopQr.setOnClickListener {
            endQrSessionAndReturn("Session stopped by Admin.")
        }

        initializeOrSyncEvent(durationMin)
    }

    private fun initializeOrSyncEvent(durationMin: Long) {
        val eventRef = firestore.collection("events").document(eventId)

        eventRef.get()
            .addOnSuccessListener { snapshot ->
                val currentTime = System.currentTimeMillis()

                if (snapshot.exists()) {
                    // Extract existing target end time
                    val targetEndTime = snapshot.getLong("targetEndTime")
                        ?: (currentTime + (durationMin * 60 * 1000L))

                    val remaining = targetEndTime - currentTime

                    if (remaining > 0) {
                        timeLeftInMillis = remaining
                        startLiveQrTimer()
                    } else {
                        endQrSessionAndReturn("Session Expired!")
                    }
                } else {
                    // Create new document if missing
                    timeLeftInMillis = durationMin * 60 * 1000L
                    saveNewEventToDatabase(currentTime + timeLeftInMillis)
                    startLiveQrTimer()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Sync Failed: ${error.message}", Toast.LENGTH_SHORT).show()
                // Fall back to a fresh local timer so the QR still displays
                timeLeftInMillis = durationMin * 60 * 1000L
                startLiveQrTimer()
            }
    }

    private fun saveNewEventToDatabase(targetEndTime: Long) {
        val eventMap = hashMapOf(
            "eventId" to eventId,
            "eventName" to eventName,
            "location" to location,
            "managedBy" to manager,
            "date" to eventDate,
            "attendedCount" to 0,
            "maxPeople" to maxPeople,
            "targetEndTime" to targetEndTime,
            "status" to "Active",
            "timestamp" to System.currentTimeMillis()
        )

        firestore.collection("events").document(eventId).set(eventMap)
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save event: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startLiveQrTimer() {
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished

                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.tvTimer.text = String.format("Time Remaining: %02d:%02d", minutes, seconds)

                generateLiveQrPayload(millisUntilFinished)
            }

            override fun onFinish() {
                endQrSessionAndReturn("Session Expired!")
            }
        }.start()
    }

    private fun generateLiveQrPayload(remainingMillis: Long) {
        val currentTimestamp = System.currentTimeMillis()
        val qrPayload = "SMARTQRA:$eventId:$currentTimestamp:$remainingMillis"

        try {
            val barcodeEncoder = BarcodeEncoder()
            currentQrBitmap = barcodeEncoder.encodeBitmap(qrPayload, BarcodeFormat.QR_CODE, 600, 600)
            binding.ivQrCode.setImageBitmap(currentQrBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun leaveScreenWithoutEnding() {
        countDownTimer?.cancel()
        val intent = Intent(this, AdminDash::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun endQrSessionAndReturn(message: String) {
        countDownTimer?.cancel()

        firestore.collection("events").document(eventId)
            .update("status", "Ended")
            .addOnFailureListener {
                // Non-fatal: event may not exist yet if it just expired instantly
            }

        val reportData = hashMapOf(
            "eventId" to eventId,
            "eventName" to eventName,
            "location" to location,
            "managedBy" to manager,
            "date" to eventDate,
            "status" to "COMPLETED",
            "endedAt" to System.currentTimeMillis()
        )
        firestore.collection("reports").document(eventId).set(reportData)

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

        val intent = Intent(this, AdminDash::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
}