package com.kptm.SmartQRA

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kptm.SmartQRA.databinding.LoginandsignBinding // Fixed import path

class LoginAndSign : AppCompatActivity() {

    private lateinit var binding: LoginandsignBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LoginandsignBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Continue as User -> go to Login, tagged as "student"
        binding.btnUser.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            intent.putExtra("role", "student")
            startActivity(intent)
        }

        // Continue as Admin -> go to Login, tagged as "admin"
        binding.btnAdmin.setOnClickListener {
            val intent = Intent(this, Login::class.java)
            intent.putExtra("role", "admin")
            startActivity(intent)
        }
    }
}