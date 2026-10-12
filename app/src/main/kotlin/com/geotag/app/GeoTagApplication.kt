package com.geotag.app

import android.app.Application
import com.google.firebase.FirebaseApp

class GeoTagApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
