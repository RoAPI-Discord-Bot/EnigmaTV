package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.input.ImeAction
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(layout.contentPaddingDp().dp)
    ) {
        Text("Live TV", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Watch live channels or pick a game — search teams or networks.",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearch(it)
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search teams, games, ESPN…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SearchBg,
                unfocusedContainerColor = SearchBg,
                focusedBorderColor = EnigmaPurple,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LiveTabChip(LiveTvTab.EVENTS, live.tab == LiveTvTab.EVENTS, onTab)
            LiveTabChip(LiveTvTab.CHANNELS, live.tab == LiveTvTab.CHANNELS, onTab)
        }

        Spacer(Modifier.height(12.dp))

        when {
            live.loading -> {
                Column(
                    Modifier.fillMaxWidth().padding(40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = EnigmaPurple)
                    Text("Loading live games & channels…", color = TextSecondary, modifier = Modifier.padding(top = 12.dp))
                }
            }
            live.error != null -> {
                Text(live.error, color = Color(0xFFFF6B6B), modifier = Modifier.padding(8.dp))
                Text("Tap Live TV in menu to retry.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.clickable { onReload() })
            }
            live.tab == LiveTvTab.EVENTS -> LiveEventsList(live.filteredEvents, onPlayMatch)
            else -> LiveChannelsBrowser(
                live = live,
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

@Composable
private fun LiveTabChip(tab: LiveTvTab, selected: Boolean, onTab: (LiveTvTab) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onTab(tab) },
        label = { Text(tab.label) },
        leadingIcon = {
            Icon(
                if (tab == LiveTvTab.EVENTS) Icons.Default.LiveTv else Icons.Default.Tv,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
private fun LiveChannelsBrowser(
    live: LiveTvBrowseState,
    onPlayChannel: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit,
    onGroupFilter: (String?) -> Unit,
    onFavoritesOnly: () -> Unit,
    onQuickPick: (String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LIVE_CHANNEL_QUICK_PICKS.forEach { (label, q) ->
                FilterChip(
                    selected = live.searchQuery.equals(q, ignoreCase = true),
                    onClick = { onQuickPick(q) },
                    label = { Text(label, fontSize = 12.sp) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
            live.channelGroups.forEach { group ->
                FilterChip(
                    selected = live.channelGroupFilter == group,
                    onClick = { onGroupFilter(group) },
                    label = { Text(group, maxLines = 1) }
                )
            }
        }

        if (live.recentChannels.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Recent", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            live.recentChannels.take(8).forEach { ch ->
                LiveChannelRow(ch, live.favoriteChannelIds.contains(ch.id), onPlayChannel, onToggleFavorite)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            if (live.favoritesOnly) "Favorite channels" else "Channels (${live.filteredChannels.size})",
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(6.dp))

        if (live.filteredChannels.isEmpty()) {
            Text("No channels match. Try ESPN, FOX, MLB, NFL…", color = TextSecondary, modifier = Modifier.padding(8.dp))
        } else if (live.searchQuery.isBlank() && live.channelGroupFilter == null && !live.favoritesOnly) {
            val rows = buildList<ChannelListEntry> {
                live.filteredChannels.groupBy { it.group }.toSortedMap().forEach { (group, channels) ->
                    add(ChannelListEntry.Header(group))
                    channels.forEach { add(ChannelListEntry.Channel(it)) }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        is ChannelListEntry.Channel -> LiveChannelRow(
                            row.channel,
                            live.favoriteChannelIds.contains(row.channel.id),
                            onPlayChannel,
                            onToggleFavorite
                        )
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(live.filteredChannels.take(500), key = { it.id }) { ch ->
                    LiveChannelRow(
                        ch,
                        live.favoriteChannelIds.contains(ch.id),
                        onPlayChannel,
                        onToggleFavorite
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveEventsList(events: List<LiveSportMatch>, onPlay: (LiveSportMatch) -> Unit) {
    if (events.isEmpty()) {
        Text("No live games match your search. Try team names or a sport (baseball, nfl).", color = TextSecondary, modifier = Modifier.padding(8.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(events, key = { it.id }) { match -> LiveEventCard(match, onPlay) }
    }
}

@Composable
private fun LiveEventCard(match: LiveSportMatch, onPlay: (LiveSportMatch) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .clickable { onPlay(match) }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (match.posterUrl != null) {
            AsyncImage(
                model = match.posterUrl,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop
            )
        }
        Column(Modifier.weight(1f)) {
            Text(match.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(match.category.replaceFirstChar { it.uppercase() }, color = EnigmaPink, fontSize = 11.sp)
        }
        Text(
            if (match.sources.isNotEmpty()) "WATCH" else "N/A",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(if (match.sources.isNotEmpty()) EnigmaPurple else Color.Gray, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun LiveChannelRow(
    channel: IptvChannel,
    isFavorite: Boolean,
    onPlay: (IptvChannel) -> Unit,
    onToggleFavorite: (IptvChannel) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onPlay(channel) }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("📺", fontSize = 22.sp, modifier = Modifier.width(44.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(channel.group, color = TextSecondary, fontSize = 11.sp)
        }
        IconButton(onClick = { onToggleFavorite(channel) }, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) EnigmaPink else TextSecondary
            )
        }
        Text(
            "LIVE",
            color = EnigmaPink,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}
