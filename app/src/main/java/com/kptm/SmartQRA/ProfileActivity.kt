package com.kptm.SmartQRA

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) uploadProfilePhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSaveProfile.setOnClickListener { saveUserProfile() }
        binding.btnChangeEmail.setOnClickListener { showChangeEmailDialog() }

        binding.btnResetPassword.setOnClickListener {
            val email = auth.currentUser?.email
            if (!email.isNullOrEmpty()) sendPasswordReset(email)
            else Toast.makeText(this, "No valid email found.", Toast.LENGTH_SHORT).show()
        }

        // 2FA: buka skrin setup (aktif / matikan)
        binding.btn2fa.setOnClickListener {
            startActivity(Intent(this, TotpSetupActivity::class.java))
        }

        binding.btnDeleteAccount.setOnClickListener { showDeleteAccountDialog() }

        binding.imgProfileAvatar.setOnClickListener {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        refresh2faStatus()   // dikemas kini bila balik dari skrin setup
    }

    // ---------- 2FA status ----------

    private fun refresh2faStatus() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("mfa").document(uid).get()
            .addOnSuccessListener { doc ->
                val on = doc.getBoolean("enabled") == true
                binding.btn2fa.text =
                    if (on) "Two-Factor Authentication: ON" else "Two-Factor Authentication: OFF"
            }
    }

    // ---------- Profile ----------

    /** Papar gambar profil: buang tint biru dan padding ikon lama, potong ikut bentuk bulat. */
    private fun showAvatar(bitmap: Bitmap) {
        binding.imgProfileAvatar.apply {
            imageTintList = null
            setPadding(0, 0, 0, 0)
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            setImageBitmap(bitmap)
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

        firestore.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val role = document.getString("role") ?: "student"
                    binding.etFullName.setText(document.getString("name") ?: "")
                    binding.etRole.setText(role.uppercase())
                    binding.tvDisplayRole.text = role.uppercase()
                    binding.tvUserId.text = "ID: ${document.getString("userId") ?: "N/A"}"

                    val photo = document.getString("photoBase64")
                    if (!photo.isNullOrEmpty()) {
                        ImageUtil.base64ToBitmap(photo)?.let { showAvatar(it) }
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load profile: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveUserProfile() {
        val currentUser = auth.currentUser ?: return
        val newName = binding.etFullName.text?.toString().orEmpty().trim()

        if (newName.isEmpty()) {
            binding.etFullName.error = "Name cannot be empty"
            return
        }

        firestore.collection("users").document(currentUser.uid)
            .update("name", newName)
            .addOnSuccessListener {
                Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfilePhoto(uri: Uri) {
        val currentUser = auth.currentUser ?: return
        val base64 = ImageUtil.uriToBase64(this, uri)

        if (base64 == null) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            return
        }

        firestore.collection("users").document(currentUser.uid)
            .update("photoBase64", base64)
            .addOnSuccessListener {
                ImageUtil.base64ToBitmap(base64)?.let { showAvatar(it) }
                Toast.makeText(this, "Profile photo updated!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to upload photo: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, "Password reset link sent to $email", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Reset failed: ${it.message}", Toast.LENGTH_SHORT).show()
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
                if (newEmail.isNotEmpty()) updateUserEmail(newEmail)
                else Toast.makeText(this, "Email cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateUserEmail(newEmail: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        user.verifyBeforeUpdateEmail(newEmail)
            .addOnSuccessListener {
                Toast.makeText(this, "Verification sent to $newEmail.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- Delete Account ----------

    private fun showDeleteAccountDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        val warningText = TextView(this).apply {
            text = "This permanently deletes your account, profile and 2FA settings. " +
                    "This cannot be undone. Type DELETE to confirm:"
            setPadding(0, 0, 0, 20)
        }
        val confirmInput = EditText(this).apply { hint = "Type DELETE" }
        container.addView(warningText)
        container.addView(confirmInput)

        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setView(container)
            .setPositiveButton("Delete") { _, _ ->
                if (confirmInput.text.toString().trim() == "DELETE") {
                    reauthenticateThenDelete()
                } else {
                    Toast.makeText(this, "Confirmation text did not match", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reauthenticateThenDelete() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_SHORT).show()
            return
        }

        val isEmailUser = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }

        if (isEmailUser) {
            val passwordInput = EditText(this).apply {
                hint = "Enter your password"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            AlertDialog.Builder(this)
                .setTitle("Confirm your password")
                .setView(passwordInput)
                .setPositiveButton("Confirm") { _, _ ->
                    val email = user.email
                    val password = passwordInput.text.toString()
                    if (email.isNullOrEmpty() || password.isEmpty()) {
                        Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    user.reauthenticate(EmailAuthProvider.getCredential(email, password))
                        .addOnSuccessListener { deleteUserData(user.uid) }
                        .addOnFailureListener {
                            Toast.makeText(this, "Re-authentication failed: ${it.message}", Toast.LENGTH_LONG).show()
                        }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Akaun Google: sign in semula untuk refresh sesi
            lifecycleScope.launch {
                AuthRepository().signInWithGoogle(this@ProfileActivity)
                    .onSuccess { refreshedUser ->
                        if (refreshedUser.uid == user.uid) {
                            deleteUserData(user.uid)
                        } else {
                            Toast.makeText(
                                this@ProfileActivity,
                                "Please choose the same Google account you are logged in with.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .onFailure {
                        Toast.makeText(
                            this@ProfileActivity,
                            "Google re-authentication failed: ${it.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun deleteUserData(uid: String) {
        val batch = firestore.batch()
        batch.delete(firestore.collection("users").document(uid))
        batch.delete(firestore.collection("mfa").document(uid))

        batch.commit()
            .addOnSuccessListener { deleteAuthAccount() }
            .addOnFailureListener {
                // PERMISSION_DENIED di sini = masalah Firestore Rules
                Toast.makeText(this, "Could not delete profile data: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun deleteAuthAccount() {
        val user = auth.currentUser ?: return
        user.delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Account deleted.", Toast.LENGTH_LONG).show()
                val intent = Intent(this, LoginAndSign::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to delete account: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}