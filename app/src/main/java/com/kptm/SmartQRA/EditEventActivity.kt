package com.kptm.SmartQRA

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityEditEventBinding
import java.util.Calendar

class EditEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditEventBinding
    private val db = FirebaseFirestore.getInstance()
    private var eventId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        eventId = intent.getStringExtra("EVENT_ID") ?: ""
        if (eventId.isEmpty()) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.etDate.setOnClickListener { showDatePicker() }
        binding.tilDate.setEndIconOnClickListener { showDatePicker() }
        binding.btnCancelEdit.setOnClickListener { finish() }
        binding.btnSaveEvent.setOnClickListener { saveChanges() }
        binding.btnSaveEvent.isEnabled = false // dihidupkan lepas data siap dimuat

        loadEvent()
    }

    private fun loadEvent() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        db.collection("events").document(eventId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                if (doc.getString("createdBy") != uid) {
                    Toast.makeText(this, "You can only edit your own events", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                if (!"Active".equals(doc.getString("status"), ignoreCase = true)) {
                    Toast.makeText(this, "Ended events are read-only", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                binding.etEventName.setText(doc.getString("eventName") ?: "")
                binding.etLocation.setText(doc.getString("location") ?: "")
                binding.etManagedBy.setText(doc.getString("managedBy") ?: "")
                binding.etDate.setText(doc.getString("date") ?: "")

                val max = doc.getLong("maxPeople")?.toInt() ?: -1
                binding.etMaxPeople.setText(if (max > 0) max.toString() else "")

                val shortId = doc.getString("shortId") ?: "N/A"
                val attended = doc.getLong("attendedCount")?.toInt() ?: 0
                binding.tvEditInfo.text = "ID: $shortId  •  $attended checked in"

                binding.btnSaveEvent.isEnabled = true
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load event: ${it.message}", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        var day = cal.get(Calendar.DAY_OF_MONTH)
        var month = cal.get(Calendar.MONTH)
        var year = cal.get(Calendar.YEAR)

        // Prefill dari tarikh sedia ada (format dd/MM/yyyy)
        val parts = binding.etDate.text?.toString()?.split("/")
        if (parts != null && parts.size == 3) {
            val d = parts[0].toIntOrNull()
            val m = parts[1].toIntOrNull()
            val y = parts[2].toIntOrNull()
            if (d != null && m != null && y != null) {
                day = d
                month = m - 1
                year = y
            }
        }

        DatePickerDialog(
            this,
            { _, selYear, selMonth, selDay ->
                // Format sama dengan CreateEvent
                binding.etDate.setText(String.format("%02d/%02d/%d", selDay, selMonth + 1, selYear))
            },
            year, month, day
        ).show()
    }

    private fun saveChanges() {
        val name = binding.etEventName.text?.toString()?.trim() ?: ""
        val location = binding.etLocation.text?.toString()?.trim() ?: ""
        val manager = binding.etManagedBy.text?.toString()?.trim() ?: ""
        val date = binding.etDate.text?.toString()?.trim() ?: ""
        val maxText = binding.etMaxPeople.text?.toString()?.trim() ?: ""

        if (name.isEmpty()) {
            binding.etEventName.error = "Event name is required"
            return
        }

        val newMax = if (maxText.isEmpty()) -1 else (maxText.toIntOrNull() ?: 0)
        if (maxText.isNotEmpty() && newMax < 1) {
            binding.etMaxPeople.error = "Enter 1 or more, or leave empty for no limit"
            return
        }

        binding.btnSaveEvent.isEnabled = false
        val eventRef = db.collection("events").document(eventId)

        // Baca semula dulu supaya attendedCount dan status sentiasa terkini
        eventRef.get()
            .addOnSuccessListener { doc ->
                if (!"Active".equals(doc.getString("status"), ignoreCase = true)) {
                    Toast.makeText(this, "This event has already ended.", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }

                val attended = doc.getLong("attendedCount")?.toInt() ?: 0
                if (newMax != -1 && newMax < attended) {
                    binding.etMaxPeople.error = "Cannot be lower than current check-ins ($attended)"
                    binding.btnSaveEvent.isEnabled = true
                    return@addOnSuccessListener
                }

                // Hantar medan yang berubah sahaja
                val updates = mutableMapOf<String, Any>()
                if (name != (doc.getString("eventName") ?: "")) updates["eventName"] = name
                if (location != (doc.getString("location") ?: "")) updates["location"] = location
                if (manager != (doc.getString("managedBy") ?: "")) updates["managedBy"] = manager
                if (date != (doc.getString("date") ?: "")) updates["date"] = date
                if (newMax != (doc.getLong("maxPeople")?.toInt() ?: -1)) updates["maxPeople"] = newMax

                if (updates.isEmpty()) {
                    Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                eventRef.update(updates)
                    .addOnSuccessListener { syncReport(updates) }
                    .addOnFailureListener {
                        Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_LONG).show()
                        binding.btnSaveEvent.isEnabled = true
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to read event: ${it.message}", Toast.LENGTH_LONG).show()
                binding.btnSaveEvent.isEnabled = true
            }
    }

    // reports/{eventId} cuma wujud selepas sesi tamat, jadi biasanya langkah ni dilangkau.
    // Kalau gagal pun tak apa, event sendiri dah berjaya dikemas kini.
    private fun syncReport(updates: Map<String, Any>) {
        val reportFields = updates.filterKeys {
            it in setOf("eventName", "location", "managedBy", "date")
        }
        if (reportFields.isEmpty()) {
            finishSaved()
            return
        }

        val reportRef = db.collection("reports").document(eventId)
        reportRef.get()
            .addOnSuccessListener { rep ->
                if (rep.exists()) {
                    reportRef.update(reportFields).addOnCompleteListener { finishSaved() }
                } else {
                    finishSaved()
                }
            }
            .addOnFailureListener { finishSaved() }
    }

    private fun finishSaved() {
        Toast.makeText(this, "Event updated", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }
}