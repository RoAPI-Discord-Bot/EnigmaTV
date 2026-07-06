package com.enigma.tv.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.MediaDetailUi
import com.enigma.tv.data.MediaTrailerUi
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
    isTv: Boolean = false,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit
) {
    val context = LocalContext.current

    // Intercept Back button — always close the detail screen
    BackHandler(enabled = true) { onClose() }

    // Full-screen opaque scrim that owns the entire focus tree.
    // Nothing behind this box can receive focus or click events.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            // focusGroup makes this Box the root of a self-contained focus tree.
            // D-Pad navigation is trapped inside; the home rows behind are invisible to focus.
            .focusGroup()
    ) {
        when {
            loading -> EnigmaLoadingRing(fullscreen = true, message = "LOADING DETAILS")
            detail != null -> {
                if (isTv) {
                    TvDetailContent(
                        detail = detail,
                        onClose = onClose,
                        onPlay = onPlay,
                        onRestart = onRestart,
                        onRemoveFromHistory = onRemoveFromHistory,
                        onToggleFavorite = onToggleFavorite,
                        onSeasonChange = onSeasonChange,
                        onEpisodeSelect = onEpisodeSelect,
                        onPlayTrailer = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                } else {
                    MobileDetailContent(
                        detail = detail,
                        onClose = onClose,
                        onPlay = onPlay,
                        onRestart = onRestart,
                        onRemoveFromHistory = onRemoveFromHistory,
                        onToggleFavorite = onToggleFavorite,
                        onSeasonChange = onSeasonChange,
                        onEpisodeSelect = onEpisodeSelect,
                        onPlayTrailer = { url ->
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TV LAYOUT: Side-by-side — poster on the left, scrollable info on the right
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TvDetailContent(
    detail: MediaDetailUi,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onPlayTrailer: (String) -> Unit
) {
    val accent = if (detail.type == ContentType.MOVIE) MovieAccent else TvAccent
    val playFocusRequester = remember { FocusRequester() }

    // Auto-focus the Play button when the screen opens
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        runCatching { playFocusRequester.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
        // Full-bleed backdrop
        if (detail.backdropUrl != null) {
            AsyncImage(
                model = detail.backdropUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(BgDark, BgDark.copy(alpha = 0.85f), BgDark.copy(alpha = 0.4f))
                        )
                    )
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BgDark.copy(alpha = 0.55f))
            )
        }

        Row(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // ── LEFT: Poster ──────────────────────────────────────────────
            Column(
                Modifier.width(220.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = detail.posterUrl,
                    contentDescription = detail.title,
                    modifier = Modifier
                        .width(220.dp)
                        .height(330.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.DarkGray),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(20.dp))
                // Rating
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    RatingBadge(score = detail.ratingScore, votes = detail.ratingVotes, accent = accent)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        detail.imdbRating?.let { ProviderRatingBadge("IMDB", it, Color(0xFFF5C518)) }
                        detail.rottenTomatoesRating?.let { ProviderRatingBadge("🍅", it, Color(0xFFFA320A)) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Favorite button — focusable, clearly visible
                var favFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (favFocused) EnigmaPurple.copy(alpha = 0.4f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .border(
                            if (favFocused) 2.dp else 0.dp,
                            if (favFocused) Color.White else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onToggleFavorite() }
                        .onFocusChanged { favFocused = it.isFocused }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        if (detail.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (detail.isFavorite) EnigmaPink else TextPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        if (detail.isFavorite) "Saved" else "Add to List",
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }

            // ── RIGHT: Scrollable info panel ──────────────────────────────
            Column(Modifier.weight(1f)) {
                // Back button row
                var backFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (backFocused) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                        .border(if (backFocused) 2.dp else 0.dp, if (backFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { onClose() }
                        .onFocusChanged { backFocused = it.isFocused }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary, modifier = Modifier.size(20.dp))
                    Text("Back", color = TextPrimary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))

                // Title + meta
                Text(detail.title, color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Text(detail.metaLine, color = TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    detail.contentRating?.let { ContentRatingBadge(it) }
                    Text(detail.genresText, color = TextSecondary, fontSize = 13.sp)
                }
                Spacer(Modifier.height(20.dp))

                // Play button — large, prominent, auto-focused
                var playFocused by remember { mutableStateOf(false) }
                if (detail.isPlayable) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onPlay,
                            modifier = Modifier
                                .weight(if (detail.resumePositionMs > 0) 1f else 0.7f, fill = false)
                                .height(56.dp)
                                .focusRequester(playFocusRequester)
                                .onFocusChanged { playFocused = it.isFocused }
                                .then(
                                    if (playFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp))
                                    else Modifier
                                ),
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(30.dp))
                            Text(
                                when {
                                    detail.resumePositionMs > 0 && detail.type == ContentType.TV -> "Resume S${detail.selectedSeason}E${detail.selectedEpisode}"
                                    detail.resumePositionMs > 0 && detail.type == ContentType.MOVIE -> "Resume Movie"
                                    detail.type == ContentType.TV -> "Play S${detail.selectedSeason}E${detail.selectedEpisode}"
                                    else -> "Play Now"
                                },
                                modifier = Modifier.padding(start = 10.dp),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (detail.resumePositionMs > 0) {
                            var restartFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = onRestart,
                                modifier = Modifier
                                    .height(56.dp)
                                    .onFocusChanged { restartFocused = it.isFocused }
                                    .then(
                                        if (restartFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp))
                                        else Modifier
                                    ),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Restart", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            }
                            var removeFocused by remember { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    onRemoveFromHistory()
                                    onClose()
                                },
                                modifier = Modifier
                                    .height(56.dp)
                                    .onFocusChanged { removeFocused = it.isFocused }
                                    .then(
                                        if (removeFocused) Modifier.border(3.dp, EnigmaPink, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from history", tint = EnigmaPink)
                            }
                        }
                    }
                } else {
                    Text(
                        detail.releaseLabel ?: "Coming soon",
                        color = EnigmaPink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Scrollable content below (overview, seasons, episodes, cast)
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    // Overview
                    item {
                        Text(
                            detail.overview.ifBlank { "No description available." },
                            color = TextPrimary.copy(alpha = 0.9f),
                            fontSize = 16.sp,
                            lineHeight = 24.sp
                        )
                    }

                    // TV: Season picker + episodes
                    if (detail.type == ContentType.TV && detail.seasons.isNotEmpty()) {
                        item {
                            Text("Season", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(detail.seasons) { s ->
                                    var chipFocused by remember { mutableStateOf(false) }
                                    FilterChip(
                                        selected = s == detail.selectedSeason,
                                        onClick = { onSeasonChange(s) },
                                        label = { Text("S$s", fontWeight = FontWeight.Bold) },
                                        modifier = Modifier
                                            .onFocusChanged { chipFocused = it.isFocused }
                                            .then(
                                                if (chipFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                                else Modifier
                                            ),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                        item {
                            Text("Episodes", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        }
                        items(detail.episodes) { ep ->
                            TvEpisodeRow(ep, ep.episodeNumber == detail.selectedEpisode, onEpisodeSelect)
                        }
                    }

                    // Cast
                    if (detail.cast.isNotEmpty()) {
                        item {
                            Text("Cast", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(detail.cast.take(12)) { member ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(80.dp)
                                    ) {
                                        AsyncImage(
                                            model = member.photoUrl,
                                            contentDescription = member.name,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.DarkGray),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(member.name, color = TextPrimary, fontSize = 11.sp, maxLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MOBILE LAYOUT: Vertical scroll, same as before but with BackHandler
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MobileDetailContent(
    detail: MediaDetailUi,
    onClose: () -> Unit,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonChange: (Int) -> Unit,
    onEpisodeSelect: (Int) -> Unit,
    onPlayTrailer: (String) -> Unit
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
                    .background(Brush.verticalGradient(listOf(Color.Transparent, BgDark)))
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

            Row(
                Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RatingBadge(score = detail.ratingScore, votes = detail.ratingVotes, accent = accent)
                detail.imdbRating?.let { ProviderRatingBadge("IMDB", it, Color(0xFFF5C518)) }
                detail.rottenTomatoesRating?.let { ProviderRatingBadge("🍅", it, Color(0xFFFA320A)) }
                detail.contentRating?.let { ContentRatingBadge(it) }
                detail.releaseLabel?.let {
                    Text(it, color = EnigmaPink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                }
            }

            Text(detail.genresText, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                detail.overview.ifBlank { "No description available." },
                color = TextPrimary.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            if (detail.trailers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Trailers & Teasers", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    detail.trailers.take(8).forEach { trailer -> TrailerCard(trailer, onPlayTrailer) }
                }
            }

            if (detail.cast.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Cast", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    detail.cast.take(12).forEach { member ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                            AsyncImage(
                                model = member.photoUrl,
                                contentDescription = member.name,
                                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray),
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
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detail.seasons.forEach { s ->
                        FilterChip(selected = s == detail.selectedSeason, onClick = { onSeasonChange(s) }, label = { Text("S$s") })
                    }
                }
                Text("Episodes", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                detail.episodes.forEach { ep ->
                    EpisodeRow(ep, ep.episodeNumber == detail.selectedEpisode, onEpisodeSelect)
                }
            }
            Spacer(Modifier.height(80.dp))
        }

        Box(
            Modifier.fillMaxWidth().background(BgDark.copy(alpha = 0.95f)).padding(16.dp)
        ) {
            if (detail.isPlayable) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text(
                            when {
                                detail.resumePositionMs > 0 && detail.type == ContentType.TV -> "Resume S${detail.selectedSeason}E${detail.selectedEpisode}"
                                detail.resumePositionMs > 0 && detail.type == ContentType.MOVIE -> "Resume Movie"
                                detail.type == ContentType.TV -> "Play S${detail.selectedSeason}E${detail.selectedEpisode}"
                                else -> "Play Now"
                            },
                            modifier = Modifier.padding(start = 10.dp),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (detail.resumePositionMs > 0) {
                        Button(
                            onClick = onRestart,
                            modifier = Modifier.heightIn(min = 52.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Restart", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                Column {
                    Text(detail.releaseLabel ?: "Coming soon — not playable yet", color = EnigmaPink, modifier = Modifier.fillMaxWidth(), fontSize = 14.sp)
                    if (detail.trailers.isNotEmpty()) {
                        Text("Trailers are available above.", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ContentRatingBadge(label: String) {
    Text(
        label,
        color = TextPrimary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.5.dp, TextSecondary.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun RatingBadge(score: String, votes: String?, accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(score, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("/10", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 2.dp, top = 4.dp))
        votes?.let {
            Text(it, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun ProviderRatingBadge(label: String, score: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(score, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun TrailerCard(trailer: MediaTrailerUi, onPlay: (String) -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onPlay(trailer.youtubeUrl) }
    ) {
        Box(Modifier.fillMaxWidth().height(90.dp).background(Color.DarkGray)) {
            if (trailer.thumbnailUrl != null) {
                AsyncImage(
                    model = trailer.thumbnailUrl,
                    contentDescription = trailer.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Play trailer",
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.Center).size(40.dp)
            )
            if (trailer.official) {
                Text(
                    "Official",
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(EnigmaPurple.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(trailer.name, color = TextPrimary, fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    }
}

@Composable
private fun TvEpisodeRow(ep: TvEpisode, selected: Boolean, onSelect: (Int) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> EnigmaPurple.copy(alpha = 0.45f)
                    selected -> EnigmaPurple.copy(alpha = 0.25f)
                    else -> Color.White.copy(alpha = 0.05f)
                }
            )
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable { onSelect(ep.episodeNumber) }
            .onFocusChanged { focused = it.isFocused }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${ep.episodeNumber}.", color = EnigmaPink, fontWeight = FontWeight.Bold, modifier = Modifier.width(32.dp), fontSize = 15.sp)
        Column(Modifier.weight(1f)) {
            Text(ep.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            ep.overview?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextSecondary, fontSize = 12.sp, maxLines = 2)
            }
        }
    }
    Spacer(Modifier.height(6.dp))
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
