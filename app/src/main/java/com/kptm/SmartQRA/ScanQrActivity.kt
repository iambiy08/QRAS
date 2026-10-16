package com.kptm.SmartQRA

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.kptm.SmartQRA.databinding.ActivityScanQrBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ScanQrActivity : AppCompatActivity() {

    companion object {
        private const val MAX_QR_AGE_MS = 10_000L
        private const val LATE_AFTER_MS = 5 * 60_000L // 5 minit
    }

    private lateinit var binding: ActivityScanQrBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var torchOn = false
    private var handled = false

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startScanning()
            else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scanner.setStatusText("")
        binding.btnBack.setOnClickListener { finish() }

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            binding.btnTorch.setOnClickListener {
                torchOn = !torchOn
                if (torchOn) binding.scanner.setTorchOn() else binding.scanner.setTorchOff()
            }
        } else {
            binding.btnTorch.visibility = View.GONE
        }

        if (hasCameraPermission()) startScanning()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun startScanning() {
        binding.scanner.decodeSingle(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                val text = result.text ?: return
                if (handled) return
                handled = true
                binding.processingOverlay.visibility = View.VISIBLE
                handleScannedPayload(text)
            }
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) {}
        })
        binding.scanner.resume()
    }

    private fun handleScannedPayload(rawPayload: String) {
        val parts = rawPayload.split(":")
        if (parts.size < 3 || parts[0] != "SMARTQRA") {
            toastAndFinish("Invalid QR Code")
            return
        }
        val qrTime = parts[2].toLongOrNull() ?: run {
            toastAndFinish("Invalid QR Code")
            return
        }
        if (abs(System.currentTimeMillis() - qrTime) > MAX_QR_AGE_MS) {
            toastAndFinish("QR code expired. Scan the latest code.")
            return
        }
        recordAttendance(parts[1])
    }

    private fun recordAttendance(eventId: String) {
        val currentUser = auth.currentUser ?: run {
            toastAndFinish("You must be logged in to check in")
            return
        }
        val uid = currentUser.uid
        val eventRef = db.collection("events").document(eventId)

        eventRef.get().addOnSuccessListener { eventDoc ->
            if (!eventDoc.exists()) {
                toastAndFinish("Invalid QR Code / Event not found")
                return@addOnSuccessListener
            }

            val status = eventDoc.getString("status") ?: "Ended"
            if (!status.equals("Active", ignoreCase = true)) {
                toastAndFinish("This session has ended.")
                return@addOnSuccessListener
            }

            val eventStart = eventDoc.getLong("timestamp") ?: System.currentTimeMillis()
            val attendeeStatus = if (System.currentTimeMillis() - eventStart > LATE_AFTER_MS) "Late" else "Present"

            val attendeeRef = eventRef.collection("attendees").document(uid)
            attendeeRef.get().addOnSuccessListener { attendeeDoc ->
                if (attendeeDoc.exists()) {
                    toastAndFinish("You've already checked in for this session.")
                } else {
                    fetchUserAndSave(uid, eventRef, attendeeRef, attendeeStatus)
                }
            }.addOnFailureListener {
                toastAndFinish("Error checking attendance: ${it.message}")
            }
        }.addOnFailureListener {
            toastAndFinish("Error reading event: ${it.message}")
        }
    }

    private fun fetchUserAndSave(
        uid: String,
        eventRef: DocumentReference,
        attendeeRef: DocumentReference,
        attendeeStatus: String
    ) {
        // Membaca dokumen daripada koleksi users berdasarkan UID
        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
            val userName = userDoc.getString("name") ?: "Unknown User"
            val userEmail = userDoc.getString("email")?.trim()?.lowercase() ?: (auth.currentUser?.email?.lowercase() ?: "N/A")
            val studentId = userDoc.getString("userId")?.trim() ?: "N/A"
            val timeDisplay = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())

            val record = hashMapOf(
                "userId" to studentId,
                "userUid" to uid,          // needed by Attendance History (collectionGroup query + rules)
                "userName" to userName,
                "email" to userEmail,
                "status" to attendeeStatus,
                "scanTime" to System.currentTimeMillis(),
                "timeDisplay" to timeDisplay
            )

            attendeeRef.set(record).addOnSuccessListener {
                eventRef.update("attendedCount", FieldValue.increment(1))
                toastAndFinish("Attendance Recorded: $attendeeStatus")
            }.addOnFailureListener {
                toastAndFinish("Failed to record attendance: ${it.message}")
            }
        }.addOnFailureListener {
            toastAndFinish("Failed to fetch user profile: ${it.message}")
        }
    }

    private fun toastAndFinish(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        finish()
    }
}