package com.enigma.tv.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun parseReleaseDate(raw: String?): LocalDate? {
    if (raw.isNullOrBlank()) return null
    return try {
        LocalDate.parse(raw)
    } catch (_: Exception) {
        null
    }
}

fun MovieItem.isReleased(): Boolean {
    val date = parseReleaseDate(releaseDate) ?: return true
    return !date.isAfter(LocalDate.now())
}

fun TvItem.isReleased(): Boolean {
    val date = parseReleaseDate(firstAirDate) ?: return true
    return !date.isAfter(LocalDate.now())
}

fun MovieItem.comingSoonLabel(): String? {
    val date = parseReleaseDate(releaseDate) ?: return null
    if (!date.isAfter(LocalDate.now())) return null
    val formatted = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    return "Coming soon · $formatted"
}

fun TvItem.comingSoonLabel(): String? {
    val date = parseReleaseDate(firstAirDate) ?: return null
    if (!date.isAfter(LocalDate.now())) return null
    val formatted = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    return "Coming soon · $formatted"
}

/** True when the title can be streamed (not a future release). */
fun MovieItem.canStream(): Boolean = comingSoonLabel() == null

fun TvItem.canStream(): Boolean = comingSoonLabel() == null

fun formatRuntime(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
