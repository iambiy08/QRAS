package com.example.qras

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.qras.databinding.LoginandsignBinding

class LoginAndSign : AppCompatActivity() {

    private lateinit var binding: LoginandsignBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginandsignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Continue as User
        binding.btnUser.setOnClickListener {
            val intent = Intent(this, UserDash::class.java)
            startActivity(intent)
        }

        // Continue as Admin
        binding.btnAdmin.setOnClickListener {
            val intent = Intent(this, AdminDash::class.java)
            startActivity(intent)
        }
    }
}