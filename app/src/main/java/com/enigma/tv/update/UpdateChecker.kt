package com.enigma.tv.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean,
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

object UpdateChecker {
    private const val GITHUB_REPO = "RoAPI-Discord-Bot/EnigmaTV"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val TAG = "UpdateChecker"

    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()

                val jsonResponse = JSONObject(response.toString())
                val tagName = jsonResponse.getString("tag_name")
                
                // Parse version string, handling potential "v" prefix (e.g. "v2.18.1" or "2.18.1")
                val latestVersionStr = tagName.removePrefix("v")
                val currentVersionStr = currentVersion.removePrefix("v")

                val releaseNotes = jsonResponse.optString("body", "No release notes provided.")
                var downloadUrl = ""

                val assets = jsonResponse.optJSONArray("assets")
                if (assets != null && assets.length() > 0) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            downloadUrl = asset.getString("browser_download_url")
                            break
                        }
                    }
                }

                // If we found an APK and the versions differ
                val hasUpdate = latestVersionStr != currentVersionStr && downloadUrl.isNotEmpty()

                return@withContext UpdateInfo(
                    hasUpdate = hasUpdate,
                    latestVersion = latestVersionStr,
                    downloadUrl = downloadUrl,
                    releaseNotes = releaseNotes
                )
            } else {
                Log.e(TAG, "Failed to check for updates. Response Code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during update check", e)
        }
        return@withContext null
    }
}
