package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.kptm.SmartQRA.databinding.ActivityLoginBinding // Fixed import path
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class Login : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var role: String = "student"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        role = intent.getStringExtra("role") ?: "student"
        binding.tvLoginTitle.text = if (role == "admin") "Admin Login" else "Student Login"

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: return@addOnSuccessListener
                    fetchRoleAndRedirect(uid)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Login failed: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        binding.tvGoToSignup.setOnClickListener {
            val intent = Intent(this, Signup::class.java)
            intent.putExtra("role", role)
            startActivity(intent)
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
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Couldn't fetch user role: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }
}