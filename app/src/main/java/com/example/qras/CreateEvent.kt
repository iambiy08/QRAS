package com.example.qras

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.qras.databinding.ActivityCreateEventBinding
import java.util.Calendar

class CreateEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateEventBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Date Picker Setup
        binding.etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val dpd = DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val dateStr = "$selectedDay/${selectedMonth + 1}/$selectedYear"
                binding.etDate.setText(dateStr)
            }, year, month, day)
            dpd.show()
        }

        binding.btnGenerateQr.setOnClickListener {
            // Added safe call ?. and toString() handling to prevent nullable Editable errors
            val name = binding.etEventName.text?.toString()?.trim() ?: ""
            val location = binding.etLocation.text?.toString()?.trim() ?: ""
            val manager = binding.etManagedBy.text?.toString()?.trim() ?: ""
            val date = binding.etDate.text?.toString()?.trim() ?: ""
            val durationStr = binding.etDuration.text?.toString()?.trim() ?: "10"

            if (name.isEmpty() || location.isEmpty() || manager.isEmpty() || date.isEmpty() || durationStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val durationLong = durationStr.toLongOrNull() ?: 10L

            // Explicit Intent creation ensures QrDisplayActivity and putExtra resolve correctly
            val intent = Intent(this, QRDisplay::class.java).apply {
                putExtra("EVENT_NAME", name)
                putExtra("LOCATION", location)
                putExtra("MANAGED_BY", manager)
                putExtra("DATE", date)
                putExtra("DURATION_MIN", durationLong)
            }
            startActivity(intent)
        }
    }
}