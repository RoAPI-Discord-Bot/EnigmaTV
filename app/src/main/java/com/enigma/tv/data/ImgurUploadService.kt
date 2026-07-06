package com.enigma.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Uploads profile photos to Imgur and returns a stable HTTPS link for Firebase sync.
 */
object ImgurUploadService {
    /** Imgur public API client id (anonymous image upload). */
    private const val CLIENT_ID = "546c25a59c58ad7"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun uploadBase64Jpeg(base64: String): String? = withContext(Dispatchers.IO) {
        if (base64.isBlank()) return@withContext null
        try {
            val body = JSONObject().put("image", base64).toString()
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://api.imgur.com/3/image")
                .addHeader("Authorization", "Client-ID $CLIENT_ID")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string().orEmpty())
                json.optJSONObject("data")?.optString("link")?.takeIf { it.startsWith("http") }
            }
        } catch (_: Exception) {
            null
        }
    }
}
