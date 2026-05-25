package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.MediaDetailUi
import com.enigma.tv.data.TvEpisode
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import com.enigma.tv.ui.theme.TvAccent

@Composable
fun MediaDetailOverlay(
    loading: Boolean,
    detail: MediaDetailUi?,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        when {
            loading -> EnigmaLoadingRing(fullscreen = true, message = "LOADING DETAILS")
            detail != null -> DetailContent(detail, onClose, onPlay, onToggleFavorite, onSeasonChange, onEpisodeSelect)
        }
    }
}

@Composable
private fun DetailContent(
    detail: MediaDetailUi,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit
) {
    val accent = if (detail.type == ContentType.MOVIE) MovieAccent else TvAccent
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            if (detail.backdropUrl != null) {
                AsyncImage(
                    model = detail.backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, BgDark)
                        )
                    )
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (detail.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (detail.isFavorite) EnigmaPink else TextPrimary
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(detail.title, color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(detail.metaLine, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            detail.releaseLabel?.let {
                Text(it, color = EnigmaPink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            }
            Text(detail.genresText, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))

            Spacer(Modifier.height(12.dp))
            Text(detail.overview.ifBlank { "No description available." }, color = TextPrimary.copy(alpha = 0.9f), fontSize = 14.sp, lineHeight = 20.sp)

            if (detail.cast.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Cast", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    detail.cast.take(12).forEach { member ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                            AsyncImage(
                                model = member.photoUrl,
                                contentDescription = member.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop
                            )
                            Text(member.name, color = TextPrimary, fontSize = 10.sp, maxLines = 2)
                        }
                    }
                }
            }

            if (detail.type == ContentType.TV && detail.seasons.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Seasons", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.seasons.forEach { s ->
                        FilterChip(
                            selected = s == detail.selectedSeason,
                            onClick = { onSeasonChange(s) },
                            label = { Text("S$s") }
                        )
                    }
                }
                Text("Episodes", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                detail.episodes.forEach { ep -> EpisodeRow(ep, ep.episodeNumber == detail.selectedEpisode, onEpisodeSelect) }
            }

            Spacer(Modifier.height(80.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(BgDark.copy(alpha = 0.95f))
                .padding(16.dp)
        ) {
            if (detail.isPlayable) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text(
                        if (detail.type == ContentType.TV) "Play S${detail.selectedSeason}E${detail.selectedEpisode}"
                        else "Play Now",
                        modifier = Modifier.padding(start = 10.dp),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    detail.releaseLabel ?: "Coming soon — not playable yet",
                    color = EnigmaPink,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: TvEpisode, selected: Boolean, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) EnigmaPurple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
            .clickable { onSelect(ep.episodeNumber) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${ep.episodeNumber}.", color = EnigmaPink, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(ep.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextSecondary, fontSize = 11.sp, maxLines = 2)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}
