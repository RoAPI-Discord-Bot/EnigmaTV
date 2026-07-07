package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.animateFloatAsState
import coil.compose.AsyncImage
import com.enigma.tv.data.IptvChannel
import com.enigma.tv.data.LIVE_CHANNEL_QUICK_PICKS
import com.enigma.tv.data.LiveSportMatch
import com.enigma.tv.data.LiveTvBrowseState
import com.enigma.tv.data.LiveTvTab
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.SearchBg
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

private sealed class ChannelListEntry {
    data class Header(val group: String) : ChannelListEntry()
    data class Channel(val channel: IptvChannel) : ChannelListEntry()
}

@Composable
fun LiveTvScreen(
    live: LiveTvBrowseState,
    layout: ScreenLayout,
    onTab: (LiveTvTab) -> Unit,
    onSearch: (String) -> Unit,
    onReload: () -> Unit,
    onPlayChannel: (IptvChannel) -> Unit,
    onPlayMatch: (LiveSportMatch) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onGroupFilter: (String?) -> Unit,
    onFavoritesOnly: () -> Unit,
    onQuickPick: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf(live.searchQuery) }
    val pad = layout.contentPaddingDp().dp
    val compact = layout == ScreenLayout.PHONE
    val bodySize = if (compact) 14.sp else 15.sp

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = pad)
            .padding(top = if (compact) 4.dp else pad, bottom = 8.dp)
    ) {
        if (!compact) {
            Text("Live TV", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(
                "Live channels and games — scroll the list below.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search teams, games, ESPN…", fontSize = bodySize) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SearchBg,
                unfocusedContainerColor = SearchBg,
                focusedBorderColor = EnigmaPurple,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiveTabChip(LiveTvTab.EVENTS, live.tab == LiveTvTab.EVENTS, onTab, layout)
            LiveTabChip(LiveTvTab.CHANNELS, live.tab == LiveTvTab.CHANNELS, onTab, layout)
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                live.loading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = EnigmaPink,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            "Tuning in…",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                        Text(
                            "Loading live games & channels",
                            color = TextSecondary,
                            fontSize = bodySize,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                live.error != null || (live.channels.isEmpty() && live.events.isEmpty() && !live.loading) -> {
                    Column(
                        Modifier.padding(24.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            live.error ?: "Could not load Live TV content.",
                            color = Color(0xFFFF6B6B),
                            fontSize = bodySize
                        )
                        androidx.compose.material3.Button(
                            onClick = onReload,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Text("Retry Loading", color = Color.White)
                        }
                    }
                }
                live.tab == LiveTvTab.EVENTS -> LiveEventsList(
                    events = live.filteredEvents,
                    layout = layout,
                    onPlay = onPlayMatch
                )
                else -> LiveChannelsBrowser(
                    live = live,
                    layout = layout,
                    onPlayChannel = onPlayChannel,
                    onToggleFavorite = onToggleFavorite,
                    onGroupFilter = onGroupFilter,
                    onFavoritesOnly = onFavoritesOnly,
                    onQuickPick = { pick ->
                        query = pick
                        onQuickPick(pick)
                    }
                )
            }
        }
    }
}

@Composable
private fun LiveTabChip(tab: LiveTvTab, selected: Boolean, onTab: (LiveTvTab) -> Unit, layout: ScreenLayout) {
    FilterChip(
        selected = selected,
        onClick = { onTab(tab) },
        label = { Text(tab.label, fontSize = if (layout == ScreenLayout.PHONE) 14.sp else 15.sp) },
        leadingIcon = {
            Icon(
                if (tab == LiveTvTab.EVENTS) Icons.Default.LiveTv else Icons.Default.Tv,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

@Composable
private fun LiveChannelsBrowser(
    live: LiveTvBrowseState,
    layout: ScreenLayout,
    onPlayChannel: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onGroupFilter: (String?) -> Unit,
    onFavoritesOnly: () -> Unit,
    onQuickPick: (String) -> Unit
) {
    val chipScroll = rememberScrollState()
    val listBottomPad = if (layout.usePermanentDrawer()) 24.dp else 88.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = listBottomPad),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chipScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LIVE_CHANNEL_QUICK_PICKS.forEach { (label, q) ->
                    FilterChip(
                        selected = live.searchQuery.equals(q, ignoreCase = true),
                        onClick = { onQuickPick(q) },
                        label = { Text(label, fontSize = 13.sp) }
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = live.channelGroupFilter == null && !live.favoritesOnly,
                    onClick = {
                        onGroupFilter(null)
                        if (live.favoritesOnly) onFavoritesOnly()
                    },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = live.favoritesOnly,
                    onClick = onFavoritesOnly,
                    label = { Text("♥ Favorites") }
                )
                live.channelGroups.take(24).forEach { group ->
                    FilterChip(
                        selected = live.channelGroupFilter == group,
                        onClick = { onGroupFilter(group) },
                        label = { Text(group, maxLines = 1) }
                    )
                }
            }
        }

        if (live.recentChannels.isNotEmpty()) {
            item {
                Text(
                    "Recent",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            items(live.recentChannels.take(8), key = { "recent-${it.id}" }) { ch ->
                LiveChannelRow(
                    ch,
                    live.favoriteChannelIds.contains(ch.id),
                    layout,
                    onPlayChannel,
                    onToggleFavorite
                )
            }
        }

        item {
            Text(
                if (live.favoritesOnly) "Favorite channels" else "Channels (${live.filteredChannels.size})",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        if (live.filteredChannels.isEmpty()) {
            item {
                Text(
                    "No channels match. Try ESPN, FOX, MLB, NFL…",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        } else if (live.searchQuery.isBlank() && live.channelGroupFilter == null && !live.favoritesOnly) {
            val rows = buildList<ChannelListEntry> {
                live.filteredChannels.groupBy { it.group }.toSortedMap().forEach { (group, channels) ->
                    add(ChannelListEntry.Header(group))
                    channels.forEach { add(ChannelListEntry.Channel(it)) }
                }
            }
            items(rows.size, key = { i ->
                when (val row = rows[i]) {
                    is ChannelListEntry.Header -> "h-${row.group}"
                    is ChannelListEntry.Channel -> row.channel.id
                }
            }) { i ->
                when (val row = rows[i]) {
                    is ChannelListEntry.Header -> Text(
                        row.group,
                        color = EnigmaPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                    is ChannelListEntry.Channel -> LiveChannelRow(
                        row.channel,
                        live.favoriteChannelIds.contains(row.channel.id),
                        layout,
                        onPlayChannel,
                        onToggleFavorite
                    )
                }
            }
        } else {
            items(live.filteredChannels.take(500), key = { it.id }) { ch ->
                LiveChannelRow(
                    ch,
                    live.favoriteChannelIds.contains(ch.id),
                    layout,
                    onPlayChannel,
                    onToggleFavorite
                )
            }
        }
    }
}

@Composable
private fun LiveEventsList(
    events: List<LiveSportMatch>,
    layout: ScreenLayout,
    onPlay: (LiveSportMatch) -> Unit
) {
    val listBottomPad = if (layout.usePermanentDrawer()) 24.dp else 88.dp
    if (events.isEmpty()) {
        Text(
            "No live games match your search. Try team names or a sport (baseball, nfl).",
            color = TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(12.dp)
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = listBottomPad),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(events, key = { it.id }) { match ->
            LiveEventCard(match, layout, onPlay)
        }
    }
}

@Composable
private fun LiveEventCard(match: LiveSportMatch, layout: ScreenLayout, onPlay: (LiveSportMatch) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val isTv = layout == ScreenLayout.TV
    val padV = if (layout == ScreenLayout.PHONE) 14.dp else 12.dp
    val scale by animateFloatAsState(targetValue = if (focused && isTv) 1.08f else 1f)

    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .glassSurface(cornerRadius = 12.dp, accentBorder = focused)
            .background(
                if (focused) EnigmaPurple.copy(alpha = 0.85f) else Color.Transparent
            )
            .clickable { onPlay(match) }
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 14.dp, vertical = padV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (match.posterUrl != null) {
            AsyncImage(
                model = match.posterUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                match.title,
                color = if (focused && isTv) Color.White else TextPrimary,
                fontWeight = if (focused && isTv) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = if (isTv) 17.sp else 15.sp,
                lineHeight = 22.sp
            )
            Text(
                match.category.replaceFirstChar { it.uppercase() },
                color = EnigmaPink,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            val schedule = com.enigma.tv.data.StreamedRepository.formatEventSchedule(match.dateMs)
            if (schedule.isNotBlank()) {
                Text(
                    schedule,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Text(
            if (match.sources.isNotEmpty()) "WATCH" else "N/A",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    if (match.sources.isNotEmpty()) EnigmaPurple else Color.Gray,
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LiveChannelRow(
    channel: IptvChannel,
    isFavorite: Boolean,
    layout: ScreenLayout,
    onPlay: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = layout == ScreenLayout.TV
    val rowH = if (layout == ScreenLayout.PHONE) 72.dp else 64.dp
    val scale by animateFloatAsState(targetValue = if (focused && isTv) 1.08f else 1f)

    Row(
        Modifier
            .fillMaxWidth()
            .height(rowH)
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .glassSurface(cornerRadius = 10.dp, accentBorder = focused)
            .background(if (focused) EnigmaPurple.copy(alpha = 0.85f) else Color.Transparent)
            .clickable { onPlay(channel) }
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("📺", fontSize = 24.sp, modifier = Modifier.width(48.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                channel.name,
                color = if (focused && isTv) Color.White else TextPrimary,
                fontWeight = if (focused && isTv) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isTv) 17.sp else 15.sp,
                maxLines = 2,
                lineHeight = 18.sp
            )
            Text(
                channel.group,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        IconButton(
            onClick = { onToggleFavorite(channel) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) EnigmaPink else TextSecondary
            )
        }
        Text(
            "LIVE",
            color = EnigmaPink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}
