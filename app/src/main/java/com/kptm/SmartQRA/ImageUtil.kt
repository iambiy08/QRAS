package com.kptm.SmartQRA

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtil {
    fun uriToBase64(context: Context, uri: Uri, maxSize: Int = 400): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val ratio = minOf(maxSize.toFloat() / original.width, maxSize.toFloat() / original.height)
            val resized = if (ratio >= 1f) original else Bitmap.createScaledBitmap(
                original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
            )

            val out = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 70, out)
            Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun base64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}