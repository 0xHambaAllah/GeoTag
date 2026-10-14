package com.geotag.app.util

/**
 * Konstanta aplikasi.
 */
object AppConstants {
    /** Email admin pusat — berhak atas semua action, label "Admin App" */
    const val ADMIN_APP_EMAIL = "riosetyawan54@gmail.com"
    const val ADMIN_APP_LABEL = "Admin App"

    fun isAdminApp(email: String?): Boolean {
        return email != null && email.equals(ADMIN_APP_EMAIL, ignoreCase = true)
    }
}
