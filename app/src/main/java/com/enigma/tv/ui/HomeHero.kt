package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.HomeRow
import com.enigma.tv.data.MovieItem
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

fun pickFeaturedMovie(rows: List<HomeRow>): MovieItem? {
    val movieRows = rows.filterIsInstance<HomeRow.Movies>()
    val preferred = listOf("Trending", "Popular", "Top Rated", "Theater", "Recent")
    for (hint in preferred) {
        movieRows.firstOrNull { it.title.contains(hint, ignoreCase = true) }
            ?.items?.firstOrNull { !it.backdropUrl.isNullOrBlank() }
            ?.let { return it }
    }
    return movieRows.firstOrNull()?.items?.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: movieRows.firstOrNull()?.items?.firstOrNull()
}

@Composable
fun HomeHeroBanner(
    movie: MovieItem,
    layout: ScreenLayout,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    val height = if (layout == ScreenLayout.TV) 320.dp else if (layout == ScreenLayout.TABLET) 260.dp else 220.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Brush.linearGradient(listOf(EnigmaPurple.copy(0.5f), Color.White.copy(0.12f))), RoundedCornerShape(18.dp))
            .clickable(onClick = onDetails)
    ) {
        val backdrop = movie.backdropUrl ?: movie.posterUrl
        if (backdrop != null) {
            AsyncImage(
                model = backdrop,
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(0.55f),
                            Color.Black.copy(0.92f)
                        )
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                "FEATURED",
                color = EnigmaPurple.copy(0.95f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                movie.title,
                color = TextPrimary,
                fontSize = if (layout == ScreenLayout.TV) 28.sp else 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${movie.year} · ★ ${"%.1f".format(movie.voteAverage)}",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = MovieAccent),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Play", modifier = Modifier.padding(start = 4.dp), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onDetails,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.35f))
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = TextPrimary)
                    Text("Details", color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
fun HomeQuickNav(
    current: NavSection,
    onSelect: (NavSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickChip("Browse", current == NavSection.HOME) { onSelect(NavSection.HOME) }
        QuickChip("Live", current == NavSection.LIVE) { onSelect(NavSection.LIVE) }
        QuickChip("Continue", current == NavSection.CONTINUE) { onSelect(NavSection.CONTINUE) }
        QuickChip("My List", current == NavSection.FAVORITES) { onSelect(NavSection.FAVORITES) }
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) TextPrimary else TextSecondary,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .glassSurface(cornerRadius = 20.dp, accentBorder = selected)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
