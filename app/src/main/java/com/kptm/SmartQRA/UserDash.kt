package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityUserDashBinding

class UserDash : AppCompatActivity() {

    private lateinit var binding: ActivityUserDashBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserDashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        loadUserProfile()
    }

    private fun setupListeners() {
        binding.btnScanQr.setOnClickListener {
            startActivity(Intent(this, ScanQrActivity::class.java))
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_events -> true
                R.id.nav_scan_qr -> {
                    startActivity(Intent(this, ScanQrActivity::class.java))
                    true
                }
                R.id.nav_history -> {
                    startActivity(Intent(this, AttendanceHistoryActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun loadUserProfile() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val userId = document.getString("userId") ?: "N/A"
                val name = document.getString("name") ?: auth.currentUser?.displayName ?: "User"

                binding.tvUserNameHeader.text = name
                binding.tvUserIdDisplay.text = "ID: $userId"
            }
            .addOnFailureListener {
                binding.tvUserNameHeader.text = "User"
                binding.tvUserIdDisplay.text = "ID: N/A"
            }
    }
}