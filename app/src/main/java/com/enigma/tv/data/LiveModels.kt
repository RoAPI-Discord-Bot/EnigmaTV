package com.enigma.tv.data

enum class LivePlaybackType { HLS, EMBED }

/** 24/7 broadcast channel from IPTV playlist */
data class IptvChannel(
    val id: String,
    val name: String,
    val group: String,
    val logoUrl: String?,
    val streamUrl: String
)

/** Live sports event from Streamed API */
data class LiveSportMatch(
    val id: String,
    val title: String,
    val category: String,
    val dateMs: Long,
    val posterPath: String?,
    val sources: List<MatchStreamSource>,
    val popular: Boolean = false
) {
    val posterUrl: String?
        get() = posterPath?.let { if (it.startsWith("http")) it else "https://streamed.pk$it" }
}

data class MatchStreamSource(
    val source: String,
    val id: String
)

data class LiveStreamLink(
    val embedUrl: String,
    val label: String,
    val hd: Boolean,
    val source: String
)

data class LiveTvBrowseState(
    val loading: Boolean = false,
    val error: String? = null,
    val tab: LiveTvTab = LiveTvTab.EVENTS,
    val events: List<LiveSportMatch> = emptyList(),
    val channels: List<IptvChannel> = emptyList(),
    val filteredEvents: List<LiveSportMatch> = emptyList(),
    val filteredChannels: List<IptvChannel> = emptyList(),
    val channelGroups: List<String> = emptyList(),
    val channelGroupFilter: String? = null,
    val favoritesOnly: Boolean = false,
    val favoriteChannelIds: Set<String> = emptySet(),
    val recentChannels: List<IptvChannel> = emptyList(),
    val searchQuery: String = ""
)

/** Quick-search shortcuts for the channel browser */
val LIVE_CHANNEL_QUICK_PICKS = listOf(
    "ESPN" to "ESPN",
    "FOX Sports" to "FOX",
    "MLB" to "MLB",
    "NFL" to "NFL",
    "NBA" to "NBA",
    "CNN" to "CNN",
    "BBC" to "BBC",
    "beIN" to "beIN"
)

enum class LiveTvTab(val label: String) {
    EVENTS("Live Games"),
    CHANNELS("TV Channels")
}
