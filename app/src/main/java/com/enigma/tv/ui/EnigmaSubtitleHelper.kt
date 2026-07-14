package com.enigma.tv.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object EnigmaSubtitleHelper {
    private const val TAG = "EnigmaSubtitleHelper"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getLocalSubtitleUri(context: Context, url: String, referer: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "temp_subtitle.vtt")
            if (file.exists()) {
                file.delete()
            }
            
            val requestBuilder = Request.Builder().url(url)
            if (referer.isNotBlank()) {
                requestBuilder.addHeader("Referer", referer)
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to download subtitle: HTTP ${response.code}")
                return@withContext null
            }
            
            val body = response.body?.string() ?: return@withContext null
            file.writeText(body)
            
            return@withContext Uri.fromFile(file).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subtitle", e)
            null
        }
    }
}
