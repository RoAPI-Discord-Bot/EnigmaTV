package com.enigma.tv.data

import android.media.AudioManager
import android.media.ToneGenerator
import java.util.Locale

enum class ProfanitySeverity {
    MILD,
    MODERATE,
    SEVERE
}

data class BleepInterval(
    val startMs: Long,
    val endMs: Long,
    val word: String,
    val severity: ProfanitySeverity
)

object ProfanityBleepEngine {

    private var toneGenerator: ToneGenerator? = null

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (_: Exception) {
            toneGenerator = null
        }
    }

    private val severeWords = setOf(
        "fuck", "fucking", "fucked", "fucker", "fuckin", "fucks",
        "cunt", "nigger", "nigga", "faggot", "motherfucker", "motherfucking"
    )

    private val moderateWords = setOf(
        "shit", "shitting", "shitted", "shits", "bullshit",
        "bitch", "bitches", "bitching",
        "dick", "dicks", "pussy", "bastard", "cock", "cocksucker",
        "asshole", "assholes", "jackass"
    )

    private val mildWords = setOf(
        "ass", "damn", "damned", "hell", "goddamn", "crap", "piss", "pissed"
    )

    fun playBleep(durationMs: Long) {
        try {
            val gen = toneGenerator ?: ToneGenerator(AudioManager.STREAM_MUSIC, 85).also { toneGenerator = it }
            gen.startTone(ToneGenerator.TONE_PROP_BEEP, durationMs.coerceAtLeast(100L).toInt())
        } catch (_: Exception) {}
    }

    fun parseSubtitleToBleepIntervals(subtitleContent: String, offsetMs: Long = 0L): List<BleepInterval> {
        if (subtitleContent.isBlank()) return emptyList()
        val intervals = mutableListOf<BleepInterval>()

        val blocks = subtitleContent.split("\n\n", "\r\n\r\n")
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLine: String? = null
            val textLines = mutableListOf<String>()

            for (line in lines) {
                if (line.contains("-->")) {
                    timeLine = line
                } else if (timeLine != null && !line.startsWith("WEBVTT") && !line.all { it.isDigit() }) {
                    textLines.add(line)
                }
            }

            if (timeLine == null || textLines.isEmpty()) continue

            val times = timeLine.split("-->").map { it.trim() }
            if (times.size < 2) continue

            val startMs = parseTimestampToMs(times[0]) ?: continue
            val endMs = parseTimestampToMs(times[1]) ?: continue
            val fullText = textLines.joinToString(" ").lowercase(Locale.ROOT)

            val words = fullText.split(Regex("""[^\w]+"""))
            for (word in words) {
                val severity = when {
                    severeWords.contains(word) -> ProfanitySeverity.SEVERE
                    moderateWords.contains(word) -> ProfanitySeverity.MODERATE
                    mildWords.contains(word) -> ProfanitySeverity.MILD
                    else -> null
                }
                if (severity != null) {
                    intervals.add(
                        BleepInterval(
                            startMs = (startMs + offsetMs).coerceAtLeast(0L),
                            endMs = (endMs + offsetMs).coerceAtLeast(0L),
                            word = word,
                            severity = severity
                        )
                    )
                }
            }
        }
        return intervals
    }

    fun shouldBleep(interval: BleepInterval, sensitivity: String): Boolean {
        return when (sensitivity.uppercase(Locale.ROOT)) {
            "LOW" -> interval.severity == ProfanitySeverity.SEVERE
            "HIGH" -> true // MILD, MODERATE, SEVERE
            else -> interval.severity == ProfanitySeverity.SEVERE || interval.severity == ProfanitySeverity.MODERATE // "MEDIUM"
        }
    }

    private fun parseTimestampToMs(timeStr: String): Long? {
        return try {
            val cleaned = timeStr.trim().replace(',', '.')
            val parts = cleaned.split(":")
            when (parts.size) {
                3 -> {
                    val hrs = parts[0].toLong()
                    val mins = parts[1].toLong()
                    val secsAndMs = parts[2].split(".")
                    val secs = secsAndMs[0].toLong()
                    val ms = if (secsAndMs.size > 1) secsAndMs[1].padEnd(3, '0').take(3).toLong() else 0L
                    (hrs * 3600_000L) + (mins * 60_000L) + (secs * 1000L) + ms
                }
                2 -> {
                    val mins = parts[0].toLong()
                    val secsAndMs = parts[1].split(".")
                    val secs = secsAndMs[0].toLong()
                    val ms = if (secsAndMs.size > 1) secsAndMs[1].padEnd(3, '0').take(3).toLong() else 0L
                    (mins * 60_000L) + (secs * 1000L) + ms
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
