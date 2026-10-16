package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivitySignupBinding
import kotlinx.coroutines.launch

class Signup : AppCompatActivity() {

    private lateinit var binding: ActivitySignupBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val repo = AuthRepository()
    private var role: String = "student"   // "student" = user, "admin" = penganjur event

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        role = intent.getStringExtra("role") ?: "student"
        binding.tvSignupTitle.text =
            if (role == "admin") "Create Admin Account" else "Create User Account"

        // Daftar dengan emel + password
        binding.btnSignup.setOnClickListener {
            val name = binding.etName.text?.toString()?.trim() ?: ""
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString()?.trim() ?: ""

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.length < 8 || !password.any { it.isLetter() } || !password.any { it.isDigit() }) {
                Toast.makeText(
                    this, "Password: minimum 8 characters with letters and numbers",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            setBusy(true)
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val user = result.user
                    if (user == null) {
                        setBusy(false)
                        return@addOnSuccessListener
                    }
                    saveProfile(user, name, sendVerification = true)
                }
                .addOnFailureListener {
                    setBusy(false)
                    Toast.makeText(this, "Signup failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Daftar dengan Google
        binding.btnGoogleSignUp.setOnClickListener {
            setBusy(true)
            lifecycleScope.launch {
                // FIX: pass the selected role so NEW Google accounts are created as admin/student correctly
                repo.signInWithGoogle(this@Signup, role)
                    .onSuccess { user -> handleGoogleUser(user) }
                    .onFailure {
                        setBusy(false)
                        Toast.makeText(
                            this@Signup, it.message ?: "Google sign-in failed", Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun newCustomId(): String {
        val prefix = if (role == "admin") "ADM-" else "USR-"
        return prefix + System.currentTimeMillis().toString().takeLast(5)
    }

    private fun saveProfile(user: FirebaseUser, name: String, sendVerification: Boolean) {
        val customId = newCustomId()
        val userMap = hashMapOf<String, Any>(
            "uid" to user.uid,
            "userId" to customId,
            "name" to name,
            "email" to (user.email ?: ""),
            "role" to role,
            "createdAt" to System.currentTimeMillis()
        )

        db.collection("users").document(user.uid).set(userMap)
            .addOnSuccessListener {
                if (sendVerification) user.sendEmailVerification()
                rtdb.child("users").child(user.uid).setValue(userMap)
                Toast.makeText(this, "Account created! ID: $customId", Toast.LENGTH_LONG).show()
                goToDashboard(role)
            }
            .addOnFailureListener {
                setBusy(false)
                Toast.makeText(this, "Failed to save profile: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun handleGoogleUser(user: FirebaseUser) {
        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Akaun sedia ada: guna role sedia ada dan hormati 2FA
                    checkMfaThenGo(user.uid, doc.getString("role") ?: "student")
                } else {
                    saveProfile(user, user.displayName ?: "Google User", sendVerification = false)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                setBusy(false)
                Toast.makeText(this, "Couldn't load user: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkMfaThenGo(uid: String, userRole: String) {
        db.collection("mfa").document(uid).get()
            .addOnSuccessListener { m ->
                if (m.getBoolean("enabled") == true) {
                    startActivity(Intent(this, TotpVerifyActivity::class.java))
                    finish()
                } else {
                    goToDashboard(userRole)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                setBusy(false)
                Toast.makeText(this, "Couldn't verify 2FA status. Try again.", Toast.LENGTH_LONG).show()
            }
    }

    private fun setBusy(busy: Boolean) {
        binding.btnSignup.isEnabled = !busy
        binding.btnGoogleSignUp.isEnabled = !busy
    }

    private fun goToDashboard(role: String) {
        val intent = if (role == "admin") {
            Intent(this, AdminDash::class.java)
        } else {
            Intent(this, UserDash::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}