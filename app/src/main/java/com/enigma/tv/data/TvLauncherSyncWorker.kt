package com.enigma.tv.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class TvLauncherSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // This feature only works on Android TV / Google TV devices.
        // On regular Android phones, the TV content provider doesn't exist
        // and every query throws IllegalArgumentException — crashing in a tight retry loop.
        // Skip entirely if the device is not a TV.
        if (!isTvDevice(applicationContext)) {
            return Result.success()
        }

        return try {
            val store = ContinueWatchingStore(applicationContext)
            val entries = store.readOnce(ProfileScopedPrefs.DEFAULT_PROFILE_ID)

            val helper = PreviewChannelHelper(applicationContext)
            val resolver = applicationContext.contentResolver

            // Clear old watch next programs for our package
            val cursor = resolver.query(
                TvContractCompat.WatchNextPrograms.CONTENT_URI,
                WatchNextProgram.PROJECTION,
                null, null, null
            )

            cursor?.use {
                while (it.moveToNext()) {
                    val prog = WatchNextProgram.fromCursor(it)
                    if (prog.intentUri?.toString()?.startsWith("enigma://") == true) {
                        resolver.delete(TvContractCompat.buildWatchNextProgramUri(prog.id), null, null)
                    }
                }
            }

            // Insert new
            for (entry in entries) {
                val builder = WatchNextProgram.Builder()
                    .setTitle(entry.name)
                    .setDescription("Resume watching " + entry.name)
                    .setType(if (entry.type == ContentType.MOVIE) TvContractCompat.PreviewPrograms.TYPE_MOVIE else TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                    .setPosterArtUri(Uri.parse(entry.poster))
                    .setIntentUri(Uri.parse("enigma://details/${entry.id}/${entry.type.name}"))

                if (entry.type == ContentType.TV) {
                    builder.setEpisodeTitle("S${entry.season}E${entry.episode}")
                }

                helper.publishWatchNextProgram(builder.build())
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("TvLauncherSyncWorker", "TV launcher sync failed (non-TV device?): ${e.message}")
            // Don't retry — this will always fail on non-TV devices
            Result.success()
        }
    }

    private fun isTvDevice(context: Context): Boolean {
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
        return uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
}
