package com.enigma.tv

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp

class EnigmaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Capture uncaught exceptions and save them to SharedPreferences
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val trace = android.util.Log.getStackTraceString(exception)
            getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("last_crash", trace)
                .commit() // sync save
            defaultHandler?.uncaughtException(thread, exception)
        }

        Thread {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        }.start()
    }
}
