package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.kptm.SmartQRA.databinding.LoginandsignBinding

class LoginAndSign : AppCompatActivity() {

    private lateinit var binding: LoginandsignBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Dah login: jangan tunjuk role picker, terus route ikut role
            routeLoggedInUser(user.uid)
        } else {
            showRolePicker()
        }
    }

    private fun routeLoggedInUser(uid: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // Akaun tiada profil, sign out dan mula semula
                    FirebaseAuth.getInstance().signOut()
                    showRolePicker()
                    return@addOnSuccessListener
                }
                val role = doc.getString("role") ?: "student"
                val target = if (role == "admin") AdminDash::class.java else UserDash::class.java
                startActivity(Intent(this, target))
                finish()
            }
            .addOnFailureListener {
                // Contoh: tiada internet. Tunjuk role picker supaya app tak tersangkut
                showRolePicker()
            }
    }

    private fun showRolePicker() {
        binding = LoginandsignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Continue as User -> Login, role "student"
        binding.btnUser.setOnClickListener {
            startActivity(Intent(this, Login::class.java).putExtra("role", "student"))
        }

        // Continue as Admin -> Login, role "admin"
        binding.btnAdmin.setOnClickListener {
            startActivity(Intent(this, Login::class.java).putExtra("role", "admin"))
        }
    }
}