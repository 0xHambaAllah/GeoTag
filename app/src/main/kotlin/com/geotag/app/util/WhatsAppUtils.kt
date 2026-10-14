package com.geotag.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** Buka chat WhatsApp ke nomor tujuan. */
object WhatsAppUtils {
    /** Normalisasi ke format internasional tanpa '+' (08.. -> 62.., +62.. -> 62..). */
    fun normalize(phone: String): String {
        val digits = phone.filter { it.isDigit() }
        return when {
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("62") -> digits
            else -> digits
        }
    }

    fun openChat(context: Context, phone: String) {
        val number = normalize(phone)
        if (number.isEmpty()) {
            Toast.makeText(context, "Nomor tidak valid", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/$number")
        )
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "WhatsApp tidak tersedia", Toast.LENGTH_SHORT).show()
        }
    }
}
