package com.kptm.SmartQRA

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.kptm.SmartQRA.databinding.ActivityTotpSetupBinding

class TotpSetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTotpSetupBinding
    private val db = FirebaseFirestore.getInstance()
    private var secret = ""
    private var alreadyEnabled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Secret tak boleh di-screenshot
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityTotpSetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) { finish(); return }
        val uid = user.uid

        db.collection("mfa").document(uid).get().addOnSuccessListener { doc ->
            alreadyEnabled = doc.getBoolean("enabled") == true
            if (alreadyEnabled) {
                secret = doc.getString("secret").orEmpty()
                binding.tvSetupInfo.text = "2FA is ON. Enter your current code to turn it off."
                binding.ivTotpQr.visibility = View.GONE
                binding.tvSecret.visibility = View.GONE
                binding.btnConfirm.text = "Disable 2FA"
            } else {
                secret = TotpUtil.generateSecret()
                val uri = TotpUtil.otpauthUri(user.email ?: "user", secret)
                binding.ivTotpQr.setImageBitmap(
                    BarcodeEncoder().encodeBitmap(uri, BarcodeFormat.QR_CODE, 600, 600)
                )
                binding.tvSecret.text = "Manual key: $secret"
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
            finish()
        }

        binding.btnConfirm.setOnClickListener {
            val code = binding.etCode.text?.toString().orEmpty().trim()
            if (secret.isEmpty() || !TotpUtil.verify(secret, code)) {
                Toast.makeText(this, "Invalid code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val data = hashMapOf("secret" to secret, "enabled" to !alreadyEnabled)
            db.collection("mfa").document(uid).set(data)
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        if (alreadyEnabled) "2FA disabled" else "2FA enabled",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}