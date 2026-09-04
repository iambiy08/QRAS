package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kptm.SmartQRA.databinding.ActivityCreateEventBinding

class CreateEvent : AppCompatActivity() { // Note: Class name is CreateEvent

    private lateinit var binding: ActivityCreateEventBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGenerateQr.setOnClickListener {
            val name = binding.etEventName.text.toString().trim()
            val location = binding.etLocation.text.toString().trim()
            val manager = binding.etManagedBy.text.toString().trim()
            val date = binding.etDate.text.toString().trim()
            val durationStr = binding.etDuration.text.toString().trim()

            if (name.isEmpty() || location.isEmpty() || manager.isEmpty() || date.isEmpty() || durationStr.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val durationLong = durationStr.toLongOrNull() ?: 10L

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