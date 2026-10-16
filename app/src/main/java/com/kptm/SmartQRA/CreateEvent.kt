package com.kptm.SmartQRA

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityCreateEventBinding
import java.util.Calendar

class CreateEvent : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private val db = FirebaseFirestore.getInstance()
    private val invitedEmailsList = mutableListOf<String>()

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { parseCsvFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        binding.btnUploadCsv.setOnClickListener {
            pickFileLauncher.launch("*/*")
        }

        binding.btnGenerateQr.setOnClickListener {
            createAndLaunchEvent()
        }
    }

    /**
     * Reads a CSV/TXT file and keeps ONLY:
     *  - valid emails (contain '@')
     *  - student/admin IDs (usr-xxxx / adm-xxxx)
     * Header rows (name,email,userid...) and plain names produce no valid token, so they are skipped.
     * Quotes, BOM and invisible characters are stripped, everything is lowercased and de-duplicated.
     */
    private fun parseCsvFile(uri: Uri) {
        try {
            val found = LinkedHashSet<String>()

            contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        found.addAll(IdentityUtils.extractIdentifiers(line))
                    }
                }
            }

            invitedEmailsList.clear()
            invitedEmailsList.addAll(found)

            binding.tvSelectedFile.text = "Loaded ${invitedEmailsList.size} valid student ID/email(s)"
            if (invitedEmailsList.isEmpty()) {
                Toast.makeText(
                    this,
                    "No valid emails or student IDs (usr-/adm-) found in this file",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(this, "Loaded ${invitedEmailsList.size} valid records", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val dateString = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etDate.setText(dateString)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    private fun createAndLaunchEvent() {
        val eventName = binding.etEventName.text?.toString()?.trim() ?: ""
        val location = binding.etLocation.text?.toString()?.trim() ?: ""
        val manager = binding.etManagedBy.text?.toString()?.trim() ?: ""
        val date = binding.etDate.text?.toString()?.trim() ?: ""
        val durationMin = binding.etDuration.text?.toString()?.toLongOrNull() ?: 10L
        val maxPeople = binding.etMaxPeople.text?.toString()?.toIntOrNull() ?: -1

        if (eventName.isEmpty()) {
            Toast.makeText(this, "Please enter an event name", Toast.LENGTH_SHORT).show()
            return
        }

        val eventRef = db.collection("events").document()
        val eventId = eventRef.id
        val targetEndTime = System.currentTimeMillis() + (durationMin * 60 * 1000L)

        val eventMap = hashMapOf(
            "eventId" to eventId,
            "shortId" to generateShortId(),
            "createdBy" to (FirebaseAuth.getInstance().currentUser?.uid ?: ""),
            "eventName" to eventName,
            "location" to location,
            "managedBy" to manager,
            "date" to date,
            "attendedCount" to 0,
            "maxPeople" to maxPeople,
            "targetEndTime" to targetEndTime,
            "status" to "Active",
            "timestamp" to System.currentTimeMillis(),
            "invitedEmails" to invitedEmailsList.toList()
        )

        eventRef.set(eventMap)
            .addOnSuccessListener {
                val intent = Intent(this, QRDisplay::class.java).apply {
                    putExtra("EVENT_ID", eventId)
                    putExtra("EVENT_NAME", eventName)
                    putExtra("LOCATION", location)
                    putExtra("MANAGED_BY", manager)
                    putExtra("DATE", date)
                    putExtra("DURATION_MIN", durationMin)
                    putExtra("MAX_PEOPLE", maxPeople)
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to create event: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateShortId(length: Int = 6): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..length).map { chars.random() }.joinToString("")
    }
}