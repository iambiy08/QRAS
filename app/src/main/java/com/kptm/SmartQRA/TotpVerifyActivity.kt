package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.ActivityTotpVerifyBinding

class TotpVerifyActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTotpVerifyBinding
    private val db = FirebaseFirestore.getInstance()
    private var attempts = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTotpVerifyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) { goToStart(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancelAndSignOut()
        })
        binding.btnCancel.setOnClickListener { cancelAndSignOut() }

        binding.btnVerify.setOnClickListener {
            val code = binding.etCode.text?.toString().orEmpty().trim()
            binding.btnVerify.isEnabled = false
            db.collection("mfa").document(uid).get()
                .addOnSuccessListener { doc ->
                    val secret = doc.getString("secret").orEmpty()
                    if (secret.isNotEmpty() && TotpUtil.verify(secret, code)) {
                        redirectByRole(uid)
                    } else {
                        attempts++
                        binding.btnVerify.isEnabled = true
                        if (attempts >= 5) {
                            Toast.makeText(this, "Too many attempts", Toast.LENGTH_LONG).show()
                            cancelAndSignOut()
                        } else {
                            Toast.makeText(this, "Invalid code", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .addOnFailureListener {
                    binding.btnVerify.isEnabled = true
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun redirectByRole(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val target = if (doc.getString("role") == "admin") AdminDash::class.java else UserDash::class.java
            startActivity(Intent(this, target).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }.addOnFailureListener {
            binding.btnVerify.isEnabled = true
            Toast.makeText(this, "Couldn't fetch role: ${it.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cancelAndSignOut() {
        FirebaseAuth.getInstance().signOut()
        goToStart()
    }

    private fun goToStart() {
        startActivity(Intent(this, LoginAndSign::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}