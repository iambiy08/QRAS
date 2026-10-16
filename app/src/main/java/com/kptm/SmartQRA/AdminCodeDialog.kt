package com.kptm.SmartQRA

import android.content.Context
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun promptAdminCode(context: Context): String? =
    suspendCancellableCoroutine { cont ->
        val input = EditText(context).apply {
            hint = "Admin code"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(50, 30, 50, 30)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Admin code required")
            .setMessage("Enter the admin code to register this Google account as an admin.")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                if (cont.isActive) cont.resume(input.text.toString().trim())
            }
            .setNegativeButton("Cancel") { _, _ ->
                if (cont.isActive) cont.resume(null)
            }
            .setOnCancelListener { if (cont.isActive) cont.resume(null) }
            .create()
        cont.invokeOnCancellation { dialog.dismiss() }
        dialog.show()
    }