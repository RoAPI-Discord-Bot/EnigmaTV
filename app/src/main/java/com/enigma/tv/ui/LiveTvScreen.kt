package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.enigma.tv.data.LiveSportMatch
import com.enigma.tv.data.LiveTvBrowseState
import com.enigma.tv.data.LiveTvTab
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.SearchBg
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

@Composable
fun LiveTvScreen(
    live: LiveTvBrowseState,
    layout: ScreenLayout,
    onTab: (LiveTvTab) -> Unit,
    onSearch: (String) -> Unit,
    onReload: () -> Unit,
    onPlayChannel: (IptvChannel) -> Unit,
    onPlayMatch: (LiveSportMatch) -> Unit
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
            "Search games (e.g. phillies yankees) or channels (ESPN, FOX Sports, MLB Network).",
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search teams, games, ESPN…") },
                singleLine = true,
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
        }

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
            else -> LiveChannelsList(live.filteredChannels, onPlayChannel)
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
private fun LiveChannelsList(channels: List<IptvChannel>, onPlay: (IptvChannel) -> Unit) {
    if (channels.isEmpty()) {
        Text("No channels match your search. Try ESPN, FOX, MLB, NFL, beIN, Sky Sports…", color = TextSecondary, modifier = Modifier.padding(8.dp))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(channels.take(500), key = { it.id }) { ch -> LiveChannelRow(ch, onPlay) }
    }
}

@Composable
private fun LiveChannelRow(channel: IptvChannel, onPlay: (IptvChannel) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onPlay(channel) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📡", fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
            Text(channel.group, color = TextSecondary, fontSize = 11.sp)
        }
        Text("LIVE", color = EnigmaPink, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
