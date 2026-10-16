package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val repo = AuthRepository()
    private var role: String = "student"   // "student" = user, "admin" = penganjur event

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        role = intent.getStringExtra("role") ?: "student"
        binding.tvLoginTitle.text = if (role == "admin") "Admin Login" else "User Login"

        // Email/Password
        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty().trim()
            val password = binding.etPassword.text?.toString().orEmpty().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)
            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid == null) {
                        setLoading(false)
                        return@addOnSuccessListener
                    }
                    checkMfaThenRedirect(uid)
                }
                .addOnFailureListener {
                    setLoading(false)
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // Forgot password
        binding.btnForgot.setOnClickListener {
            val email = binding.etEmail.text?.toString().orEmpty().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.etEmail.error = "Enter your email first"
                return@setOnClickListener
            }
            lifecycleScope.launch {
                repo.sendPasswordReset(email)
                    .onSuccess {
                        Toast.makeText(this@Login, "Reset link sent to $email", Toast.LENGTH_LONG).show()
                    }
                    .onFailure {
                        Toast.makeText(this@Login, it.message ?: "Failed", Toast.LENGTH_LONG).show()
                    }
            }
        }

        // Google (Credential Manager) - role yang dipilih dihantar supaya akaun BARU ikut portal
        binding.btnGoogleSignIn.setOnClickListener {
            lifecycleScope.launch {
                setLoading(true)
                repo.signInWithGoogle(this@Login, role)
                    .onSuccess { user -> handleGoogleUser(user) }
                    .onFailure {
                        setLoading(false)
                        Toast.makeText(this@Login, it.message ?: "Google sign-in failed", Toast.LENGTH_LONG).show()
                    }
            }
        }

        binding.tvGoToSignup.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
        }
    }

    /**
     * Akaun Google sedia ada: guna role sedia ada (role yang dipilih di skrin pertama diabaikan).
     * Akaun Google baharu: dicipta ikut role skrin yang dipilih (student atau admin).
     */
    private fun handleGoogleUser(user: FirebaseUser) {
        val uid = user.uid
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    checkMfaThenRedirect(uid)
                    return@addOnSuccessListener
                }

                val prefix = if (role == "admin") "ADM-" else "USR-"
                val customId = prefix + System.currentTimeMillis().toString().takeLast(5)
                val userMap = hashMapOf<String, Any>(
                    "uid" to uid,
                    "userId" to customId,
                    "name" to (user.displayName ?: "Google User"),
                    "email" to (user.email ?: ""),
                    "role" to role,
                    "createdAt" to System.currentTimeMillis()
                )
                db.collection("users").document(uid).set(userMap)
                    .addOnSuccessListener {
                        rtdb.child("users").child(uid).setValue(userMap)
                        checkMfaThenRedirect(uid)
                    }
                    .addOnFailureListener {
                        auth.signOut()
                        setLoading(false)
                        Toast.makeText(this, "Failed to save profile: ${it.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener {
                auth.signOut()
                setLoading(false)
                Toast.makeText(this, "Couldn't load user: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkMfaThenRedirect(uid: String) {
        db.collection("mfa").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.getBoolean("enabled") == true) {
                    startActivity(Intent(this, TotpVerifyActivity::class.java))
                    finish()
                } else {
                    fetchRoleAndRedirect(uid)
                }
            }
            .addOnFailureListener {
                auth.signOut()
                setLoading(false)
                Toast.makeText(
                    this,
                    "Couldn't verify 2FA status. Check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun fetchRoleAndRedirect(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val actualRole = document.getString("role") ?: "student"
                val intent = if (actualRole == "admin") {
                    Intent(this, AdminDash::class.java)
                } else {
                    Intent(this, UserDash::class.java)
                }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Couldn't fetch user role: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setLoading(loading: Boolean) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.INVISIBLE
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
    }
}