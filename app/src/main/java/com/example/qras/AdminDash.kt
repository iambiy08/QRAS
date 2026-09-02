package com.example.qras

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.qras.databinding.ActivityAdminDashBinding

class AdminDash : AppCompatActivity() {

    private lateinit var binding: ActivityAdminDashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminDashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCreateEvent.setOnClickListener {
            val intent = Intent(this, CreateEventActivity::class.java)
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}