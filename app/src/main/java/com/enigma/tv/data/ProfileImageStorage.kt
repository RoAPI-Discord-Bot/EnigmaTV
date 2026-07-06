package com.enigma.tv.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File

object ProfileImageStorage {
    private const val MAX_EDGE = 256
    private const val JPEG_QUALITY = 82

    /**
     * Saves a downscaled JPEG locally and returns a base64 payload for Firebase sync.
     */
    fun persistAndEncode(context: Context, profileId: String, uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            context.contentResolver.openInputStream(uri)?.use { input ->
                val original = BitmapFactory.decodeStream(input) ?: return null
                val scaled = scaleDown(original)
                if (scaled != original) original.recycle()

                val dir = File(context.filesDir, "profile_avatars").apply { mkdirs() }
                val file = File(dir, "$profileId.jpg")
                file.outputStream().use { out ->
                    scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                scaled.recycle()

                val bytes = file.readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun localFile(context: Context, profileId: String): File? {
        val file = File(File(context.filesDir, "profile_avatars"), "$profileId.jpg")
        return file.takeIf { it.exists() }
    }

    fun avatarModel(profile: ViewerProfile, context: Context): Any? {
        profile.avatarUri?.takeIf { uri ->
            uri.startsWith("http", ignoreCase = true) &&
                !uri.contains("image.tmdb.org", ignoreCase = true)
        }?.let { return it }
        localFile(context, profile.id)?.let { return it }
        profile.avatarBase64?.takeIf { it.isNotBlank() }?.let {
            return "data:image/jpeg;base64,$it"
        }
        profile.avatarUri?.takeIf { it.isNotBlank() }?.let { return it }
        return null
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= MAX_EDGE) return bitmap
        val scale = MAX_EDGE.toFloat() / maxSide
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
