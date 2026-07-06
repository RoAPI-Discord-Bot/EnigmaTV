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
    companion object {
        private var downloadManager: androidx.media3.exoplayer.offline.DownloadManager? = null

        @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
        @Synchronized
        fun getDownloadManager(context: Context): androidx.media3.exoplayer.offline.DownloadManager {
            if (downloadManager == null) {
                val databaseProvider = androidx.media3.database.StandaloneDatabaseProvider(context)
                val cache = androidx.media3.datasource.cache.SimpleCache(
                    java.io.File(context.filesDir, "downloads"),
                    androidx.media3.datasource.cache.NoOpCacheEvictor(),
                    databaseProvider
                )
                val factory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                downloadManager = androidx.media3.exoplayer.offline.DownloadManager(
                    context,
                    databaseProvider,
                    cache,
                    factory,
                    java.util.concurrent.Executors.newFixedThreadPool(6)
                )
            }
            return downloadManager!!
        }
    }
}
