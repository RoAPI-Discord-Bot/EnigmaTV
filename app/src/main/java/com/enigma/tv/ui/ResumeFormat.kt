package com.enigma.tv.ui

object ResumeFormat {
    /** Human-readable resume point; null if too early to show. */
    fun label(positionMs: Long): String? {
        if (positionMs < 3_000L) return null
        val totalSec = positionMs / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}
