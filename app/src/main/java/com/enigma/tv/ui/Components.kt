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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.animation.core.animateFloatAsState
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
        Text(
            text = cleanRowTitle(title),
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp,
            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp)
        )
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
    cardWidthDp: Int = 150,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    onLongClickPlay: (() -> Unit)? = null
) {
    val cardW = cardWidthDp.dp
    val cardH = (cardWidthDp * 1.5f).dp
    val isTv = cardWidthDp > 170

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.08f else 1f, label = "card_scale")

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
                .clip(RoundedCornerShape(12.dp))
                .background(CardBg.copy(alpha = 0.4f))
                .then(
                    if (focused) {
                        Modifier
                            .border(3.dp, if (isTv) Color.White else accent, RoundedCornerShape(12.dp))
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
                    tint = Color(0xFF333333),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                )
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
                        tint = if (isFavorite) EnigmaPink else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (onLongClickPlay != null && subtitle == null) {
                Text(
                    text = "Hold to play",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 9.sp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = TextPrimary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(accent, RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = accent,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = title,
            color = if (focused && isTv) Color.White else TextSecondary,
            fontSize = if (isTv) 14.sp else 12.sp,
            fontWeight = if (focused && isTv) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        content = { content() }
    )
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFF666666))
            Text(
                text = message,
                color = Color(0xFF666666),
                fontSize = 11.sp,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
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
