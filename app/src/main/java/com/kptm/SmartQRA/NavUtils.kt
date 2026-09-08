package com.kptm.SmartQRA

import android.app.Activity
import android.content.Intent
import com.google.android.material.bottomnavigation.BottomNavigationView

object NavUtils {
    fun setupBottomNavigation(
        activity: Activity,
        bottomNav: BottomNavigationView,
        userRole: String
    ) {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                // Admin Dashboard or User Events tab
                R.id.nav_dashboard, R.id.nav_events -> {
                    val intent = if (userRole.equals("admin", ignoreCase = true)) {
                        Intent(activity, AdminDash::class.java)
                    } else {
                        Intent(activity, UserDash::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    activity.startActivity(intent)
                    activity.finish()
                    true
                }

                // QR Scanner or Show QR tab
                R.id.nav_show_qr, R.id.nav_scan_qr -> {
                    val intent = if (userRole.equals("admin", ignoreCase = true)) {
                        Intent(activity, QRDisplay::class.java)
                    } else {
                        Intent(activity, ScanQrActivity::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    activity.startActivity(intent)
                    activity.finish()
                    true
                }

                else -> false
            }
        }
    }
}