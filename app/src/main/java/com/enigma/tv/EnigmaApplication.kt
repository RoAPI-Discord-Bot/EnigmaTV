package com.enigma.tv

import android.app.Application
import com.google.firebase.FirebaseApp

class EnigmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
    }
}
