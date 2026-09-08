package com.kptm.SmartQRA

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityProfileBinding

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnBack.setOnClickListener {
            finish()
        }

        loadUserProfile()

        binding.btnSaveProfile.setOnClickListener {
            saveUserProfile()
        }

        // Action listeners for security buttons
        binding.btnResetPassword.setOnClickListener {
            val email = auth.currentUser?.email
            if (!email.isNullOrEmpty()) {
                sendPasswordReset(email)
            } else {
                Toast.makeText(this, "No valid email found.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnChangeEmail.setOnClickListener {
            showChangeEmailDialog()
        }
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.etEmail.setText(currentUser.email)

        firestore.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val fullName = document.getString("fullName") ?: ""
                    val role = document.getString("role") ?: "STUDENT"

                    binding.etFullName.setText(fullName)
                    binding.etRole.setText(role.uppercase())
                    binding.tvDisplayRole.text = role.uppercase()
                }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(this, "Failed to load profile: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val currentUser = auth.currentUser ?: return
        val newName = binding.etFullName.text.toString().trim()

        if (newName.isEmpty()) {
            binding.etFullName.error = "Name cannot be empty"
            return
        }

        val updateData = mapOf(
            "fullName" to newName
        )

        firestore.collection("users").document(currentUser.uid)
            .update(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Reset failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChangeEmailDialog() {
        val input = EditText(this).apply {
            hint = "Enter new email address"
            setPadding(40, 30, 40, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("Change Email Address")
            .setView(input)
            .setPositiveButton("Send Verification") { _, _ ->
                val newEmail = input.text.toString().trim()
                if (newEmail.isNotEmpty()) {
                    updateUserEmail(newEmail)
                } else {
                    Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUserEmail(newEmail: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {
                Toast.makeText(this, "Verification sent to $newEmail.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}