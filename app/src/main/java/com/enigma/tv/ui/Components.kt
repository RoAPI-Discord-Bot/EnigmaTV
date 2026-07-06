package com.enigma.tv.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Tv
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.SearchSuggestion
import com.enigma.tv.data.ViewerProfile
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.enigma.tv.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.BgHeader
import com.enigma.tv.ui.theme.CardBg
import com.enigma.tv.ui.theme.GlassBorderAccent
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.SearchBg
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

const val ENIGMA_TV_BRAND = "ENIGMATV"

@Composable
fun EnigmaHeader(
    sectionLabel: String? = null,
    accent: Color = EnigmaPurple,
    placeholder: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    searchSuggestions: List<SearchSuggestion> = emptyList(),
    onSuggestionClick: (SearchSuggestion) -> Unit = {},
    onDismissSuggestions: () -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    activeProfile: ViewerProfile? = null,
    onProfileClick: (() -> Unit)? = null,
    showSearch: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(cornerRadius = 0.dp, accentBorder = false)
            .background(BgHeader.copy(alpha = 0.55f))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onMenuClick != null) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextPrimary)
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.enigma_mark),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(28.dp)
                )
                Column {
                Text(
                    text = ENIGMA_TV_BRAND,
                    color = accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip
                )
                sectionLabel?.let { label ->
                    Text(
                        text = label,
                        color = EnigmaPink.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
                }
            }
            if (onProfileClick != null) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .clickable(onClick = onProfileClick)
                        .padding(start = 8.dp)
                ) {
                    ProfileNavIcon(activeProfile)
                }
            }
        }
        if (showSearch) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                placeholder = {
                    Text(
                        placeholder,
                        color = TextSecondary.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SearchBg.copy(alpha = 0.85f),
                    unfocusedContainerColor = SearchBg.copy(alpha = 0.65f),
                    focusedBorderColor = GlassBorderAccent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = accent
                ),
                shape = RoundedCornerShape(14.dp)
            )
            IconButton(
                onClick = onSearch,
                modifier = Modifier
                    .glassSurface(cornerRadius = 14.dp, accentBorder = true)
                    .background(accent.copy(alpha = 0.85f), RoundedCornerShape(14.dp))
                    .size(48.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
            }
            }
            if (searchSuggestions.isNotEmpty()) {
                SearchSuggestionsDropdown(
                    suggestions = searchSuggestions,
                    onPick = onSuggestionClick,
                    onDismiss = onDismissSuggestions,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SearchSuggestionsDropdown(
    suggestions: List<SearchSuggestion>,
    onPick: (SearchSuggestion) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(SearchBg.copy(alpha = 0.95f))
            .padding(vertical = 4.dp)
    ) {
        suggestions.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPick(item)
                        onDismiss()
                    }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    if (item.type == ContentType.MOVIE) Icons.Default.Movie else Icons.Default.Tv,
                    contentDescription = null,
                    tint = if (item.type == ContentType.MOVIE) EnigmaPink else EnigmaPurple,
                    modifier = Modifier.size(20.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${if (item.type == ContentType.MOVIE) "Movie" else "TV"} · ${item.year}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ContentSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.padding(bottom = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(EnigmaPurple, EnigmaPink)
                        )
                    )
            )
            Text(
                text = cleanRowTitle(title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    accent: Color,
    badge: String? = null,
    subtitle: String? = null,
    episodeTag: String? = null,
    progress: Float? = null,
    remainingMs: Long? = null,
    cardWidthDp: Int = 150,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClickPlay: (() -> Unit)? = null
) {
    val cardW = cardWidthDp.dp
    val cardH = (cardWidthDp * 1.5f).dp
    val isTv = cardWidthDp >= 140

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.07f else 1f, label = "card_scale")

    val clickModifier = if (onLongClickPlay != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClickPlay)
    } else {
        Modifier.clickable(onClick = onClick)
    }

    val baseModifier = Modifier
        .width(cardW)
        .scale(scale)
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
        .then(clickModifier)

    Column(modifier = baseModifier) {
        Box(
            modifier = Modifier
                .size(width = cardW, height = cardH)
                .clip(RoundedCornerShape(14.dp))
                .background(CardBg.copy(alpha = 0.4f))
                .then(
                    if (focused) {
                        Modifier.border(
                            3.dp,
                            if (isTv) Color.White else accent,
                            RoundedCornerShape(14.dp)
                        )
                    } else Modifier
                )
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (badge == "TV") Icons.Default.Tv else Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFF444444),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
            }

            // Bottom gradient scrim with title baked in — Netflix style
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((cardWidthDp * 0.65f).dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f),
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxWidth()
                ) {
                    // Episode tag (e.g. "S2E5") for continue watching
                    if (episodeTag != null) {
                        Text(
                            text = episodeTag,
                            color = accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (isTv) 13.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    // Progress bar + time remaining
                    if (progress != null) {
                        val timeLabel = remainingMs?.let { ms ->
                            val totalMin = (ms / 60000L).toInt()
                            if (totalMin >= 60) {
                                val h = totalMin / 60
                                val m = totalMin % 60
                                if (m == 0) "${h}h left" else "${h}h ${m}m left"
                            } else {
                                "${totalMin}m left"
                            }
                        }
                        if (timeLabel != null) {
                            Text(
                                text = timeLabel,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.padding(top = 3.dp, bottom = 2.dp)
                            )
                        }
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = accent,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }
                }
            }

            if (onFavoriteClick != null) {
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) EnigmaPink else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            if (badge != null) {
                Text(
                    text = badge,
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(
                            accent.copy(alpha = 0.92f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun TvPosterRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // On TV use a simple horizontal scroll Row — LazyRow interferes with
    // the vertical LazyColumn scroll nesting on Leanback. Each card is
    // individually focusable so D-Pad navigation works naturally.
    // clipToBounds prevents cards bleeding over the sidebar edge during
    // the drawer open/close width animation.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            content = { content() }
        )
    }
}

@Composable
fun PosterRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun LoadingState(message: String = "FETCHING...") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(4) {
            ShimmerCard(widthDp = 140)
        }
    }
}

@Composable
fun ShimmerCard(widthDp: Int = 140) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "shimmerX"
    )
    val shimmerBrush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(
            Color(0xFF1E1E2E),
            Color(0xFF2A2A3E),
            Color(0xFF353554),
            Color(0xFF2A2A3E),
            Color(0xFF1E1E2E),
        ),
        start = Offset(shimmerTranslate - 400f, 0f),
        end = Offset(shimmerTranslate, 0f)
    )
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .height((widthDp * 1.5f).dp)
            .clip(RoundedCornerShape(14.dp))
            .background(shimmerBrush)
    )
}

@Composable
fun ScrollableContent(padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(padding)
    ) {
        content()
    }
}
