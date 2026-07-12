package com.enigma.tv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ThumbnailEntry(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int
)

object VttThumbnailParser {

    private const val TAG = "VttThumbnailParser"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun parse(vttUrl: String): List<ThumbnailEntry> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(vttUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            
            val vttContent = response.body?.string() ?: return@withContext emptyList()
            val baseUrl = vttUrl.substringBeforeLast("/") + "/"
            
            return@withContext parseVttContent(vttContent, baseUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse thumbnails VTT", e)
            emptyList()
        }
    }

    private fun parseVttContent(content: String, baseUrl: String): List<ThumbnailEntry> {
        val entries = mutableListOf<ThumbnailEntry>()
        val lines = content.lines().map { it.trim() }
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.contains("-->")) {
                val times = line.split("-->")
                if (times.size == 2) {
                    val startMs = parseVttTime(times[0].trim())
                    val endMs = parseVttTime(times[1].trim())
                    
                    if (i + 1 < lines.size) {
                        val imgLine = lines[i + 1]
                        if (imgLine.isNotBlank() && imgLine.contains("#xywh=")) {
                            val parts = imgLine.split("#xywh=")
                            if (parts.size == 2) {
                                val imgPath = parts[0]
                                val coords = parts[1].split(",")
                                if (coords.size == 4) {
                                    val x = coords[0].toIntOrNull() ?: 0
                                    val y = coords[1].toIntOrNull() ?: 0
                                    val w = coords[2].toIntOrNull() ?: 0
                                    val h = coords[3].toIntOrNull() ?: 0
                                    
                                    val fullUrl = if (imgPath.startsWith("http")) imgPath else baseUrl + imgPath
                                    
                                    entries.add(
                                        ThumbnailEntry(
                                            startMs = startMs,
                                            endMs = endMs,
                                            imageUrl = fullUrl,
                                            x = x,
                                            y = y,
                                            w = w,
                                            h = h
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            i++
        }
        return entries
    }

    private fun parseVttTime(timeString: String): Long {
        try {
            val parts = timeString.split(":")
            var hours = 0L
            var minutes = 0L
            var seconds = 0L
            var ms = 0L
            
            if (parts.size == 3) {
                hours = parts[0].toLong()
                minutes = parts[1].toLong()
                val secParts = parts[2].split(".")
                seconds = secParts[0].toLong()
                if (secParts.size == 2) ms = secParts[1].padEnd(3, '0').substring(0, 3).toLong()
            } else if (parts.size == 2) {
                minutes = parts[0].toLong()
                val secParts = parts[1].split(".")
                seconds = secParts[0].toLong()
                if (secParts.size == 2) ms = secParts[1].padEnd(3, '0').substring(0, 3).toLong()
            }
            
            return (hours * 3600000) + (minutes * 60000) + (seconds * 1000) + ms
        } catch (e: Exception) {
            return 0L
        }
    }
}
