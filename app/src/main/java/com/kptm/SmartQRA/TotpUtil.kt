package com.kptm.SmartQRA

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpUtil {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        return sb.toString() // 20 bytes = 32 aksara tepat
    }

    private fun decode(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in s.uppercase()) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                out.write((buffer shr (bits - 8)) and 0xFF)
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    private fun codeAt(secret: String, timeMillis: Long): String {
        val counter = timeMillis / 30000
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(decode(secret), "HmacSHA1"))
        val h = mac.doFinal(msg)
        val o = h[h.size - 1].toInt() and 0x0F
        val bin = ((h[o].toInt() and 0x7F) shl 24) or
                ((h[o + 1].toInt() and 0xFF) shl 16) or
                ((h[o + 2].toInt() and 0xFF) shl 8) or
                (h[o + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toString().padStart(6, '0')
    }

    /** Terima kod semasa serta 30 saat sebelum/selepas (toleransi jam). */
    fun verify(secret: String, code: String): Boolean {
        val now = System.currentTimeMillis()
        return (-1..1).any { codeAt(secret, now + it * 30000L) == code }
    }

    fun otpauthUri(email: String, secret: String) =
        "otpauth://totp/SmartQRA:${Uri.encode(email)}?secret=$secret&issuer=SmartQRA"
}