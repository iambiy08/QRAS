package com.kptm.SmartQRA

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScanQrActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val barcodeLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents == null) {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            handleScannedPayload(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_qr)

        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Align the admin QR code within the frame")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(false)
        }

        barcodeLauncher.launch(options)
    }

    /**
     * QR payload format from QRDisplay.kt: "SMARTQRA:$eventId:$timestamp:$remainingMillis"
     */
    private fun handleScannedPayload(rawPayload: String) {
        val parts = rawPayload.split(":")

        if (parts.size < 2 || parts[0] != "SMARTQRA") {
            Toast.makeText(this, "Invalid QR Code", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val eventId = parts[1]
        recordAttendance(eventId)
    }

    private fun recordAttendance(eventId: String) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to check in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val uid = currentUser.uid

        val eventRef = db.collection("events").document(eventId)

        eventRef.get()
            .addOnSuccessListener { eventDoc ->
                if (!eventDoc.exists()) {
                    Toast.makeText(this, "Invalid QR Code / Event not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                val status = eventDoc.getString("status") ?: "Ended"
                if (!status.equals("Active", ignoreCase = true)) {
                    Toast.makeText(this, "This session has ended.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                val currentAttended = eventDoc.getLong("attendedCount")?.toInt() ?: 0
                val maxPeople = eventDoc.getLong("maxPeople")?.toInt() ?: -1

                if (maxPeople != -1 && currentAttended >= maxPeople) {
                    Toast.makeText(this, "Event is full! Maximum capacity reached.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                // Check for duplicate scan by this student
                val attendeeRef = eventRef.collection("attendees").document(uid)
                attendeeRef.get()
                    .addOnSuccessListener { attendeeDoc ->
                        if (attendeeDoc.exists()) {
                            Toast.makeText(this, "You've already checked in for this session.", Toast.LENGTH_LONG).show()
                            finish()
                            return@addOnSuccessListener
                        }

                        fetchStudentNameAndSave(uid, eventRef, attendeeRef)
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error checking attendance: ${it.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error reading event: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun fetchStudentNameAndSave(
        uid: String,
        eventRef: com.google.firebase.firestore.DocumentReference,
        attendeeRef: com.google.firebase.firestore.DocumentReference
    ) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val studentName = userDoc.getString("name") ?: auth.currentUser?.email ?: "Unknown Student"
                val now = Date()
                val timeDisplay = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)

                val attendeeMap = hashMapOf(
                    "studentName" to studentName,
                    "studentUid" to uid,
                    "timeDisplay" to timeDisplay,
                    "scanTime" to now.time,
                    "status" to "Present"
                )

                attendeeRef.set(attendeeMap)
                    .addOnSuccessListener {
                        // Increment the counter atomically (safe even with simultaneous scans)
                        eventRef.update("attendedCount", FieldValue.increment(1))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Attendance recorded successfully!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                            .addOnFailureListener {
                                // Attendee record saved even if counter update fails
                                Toast.makeText(this, "Attendance saved!", Toast.LENGTH_SHORT).show()
                                finish()
                            }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error saving attendance: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching profile: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}