package com.kptm.SmartQRA

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityCreateEventBinding
import java.util.Calendar

class CreateEvent : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }

        binding.btnGenerateQr.setOnClickListener {
            createAndLaunchEvent()
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                val dateString = String.format("%02d/%02d/%d", selectedDay, selectedMonth + 1, selectedYear)
                binding.etDate.setText(dateString)
            },
            year,
            month,
            day
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

        // Create a new document with an auto-generated ID (Firestore equivalent of .push())
        val eventRef = db.collection("events").document()
        val eventId = eventRef.id
        val targetEndTime = System.currentTimeMillis() + (durationMin * 60 * 1000L)

        val eventMap = hashMapOf(
            "eventId" to eventId,
            "eventName" to eventName,
            "location" to location,
            "managedBy" to manager,
            "date" to date,
            "attendedCount" to 0,
            "maxPeople" to maxPeople,
            "targetEndTime" to targetEndTime,
            "status" to "Active",
            "timestamp" to System.currentTimeMillis()
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
}