package com.enigma.tv.data

/** Curated live streams — opens in the player WebView (availability varies by region). */
data class LiveChannel(
    val id: String,
    val name: String,
    val category: String,
    val logoEmoji: String,
    val streamUrl: String
)

object LiveChannels {
    val channels = listOf(
        LiveChannel(
            "espn",
            "ESPN Live",
            "Sports",
            "🏈",
            "https://dlhd.dad/stream/stream-500.php"
        ),
        LiveChannel(
            "sky_sports",
            "Sky Sports",
            "Sports",
            "⚽",
            "https://dlhd.dad/stream/stream-130.php"
        ),
        LiveChannel(
            "bein",
            "beIN Sports",
            "Sports",
            "🌍",
            "https://dlhd.dad/stream/stream-519.php"
        ),
        LiveChannel(
            "cnn",
            "CNN International",
            "News",
            "📰",
            "https://dlhd.dad/stream/stream-100.php"
        ),
        LiveChannel(
            "bbc",
            "BBC News",
            "News",
            "🇬🇧",
            "https://dlhd.dad/stream/stream-877.php"
        ),
        LiveChannel(
            "mtv",
            "MTV",
            "Entertainment",
            "🎵",
            "https://dlhd.dad/stream/stream-211.php"
        )
    )
}
