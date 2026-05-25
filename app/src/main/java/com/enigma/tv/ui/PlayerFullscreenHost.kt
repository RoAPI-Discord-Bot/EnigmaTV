package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.enigma.tv.util.findActivity
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Full-screen player shell: video fills the display; chrome auto-hides.
 * Tap video to show/hide controls. TV shows prev/next episode in the mini bar.
 */
@Composable
fun PlayerFullscreenHost(
    title: String,
    subtitle: String,
    posterUrl: String? = null,
    accent: Color,
    layout: ScreenLayout,
    onClose: () -> Unit,
    onNextSource: (() -> Unit)? = null,
    showNextSource: Boolean = false,
    tvControls: TvPlayerControls? = null,
    onPrevEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    hasPrevEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    content: @Composable () -> Unit
) {
    val startWithChrome = layout == ScreenLayout.TV
    var chromeVisible by rememberSaveable { mutableStateOf(startWithChrome) }
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(chromeVisible) {
        if (chromeVisible) {
            delay(if (layout == ScreenLayout.TV) 8_000 else 5_000)
            chromeVisible = false
        }
    }

    ImmersiveSystemBars(enabled = !chromeVisible)

    BackHandler {
        if (chromeVisible) chromeVisible = false else onClose()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgDark)
            .clickable(interactionSource = interaction, indication = null) {
                chromeVisible = !chromeVisible
            }
    ) {
        Box(Modifier.fillMaxSize()) {
            content()
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
        ) {
            Column(Modifier.fillMaxWidth()) {
                PlayerChrome(
                    title = title,
                    subtitle = subtitle,
                    posterUrl = posterUrl,
                    accent = accent,
                    onClose = onClose,
                    showBack = false,
                    showNextSource = showNextSource,
                    onNextSource = onNextSource,
                    tvControls = tvControls,
                    isTvLayout = layout == ScreenLayout.TV,
                    isLive = subtitle.contains("Live", ignoreCase = true)
                )
            }
        }

        AnimatedVisibility(
            visible = !chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            PlayerMiniBar(
                onShowChrome = { chromeVisible = true },
                onClose = onClose,
                accent = accent
            )
        }

        if (tvControls != null && !chromeVisible) {
            PlayerTvMiniRail(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
                season = tvControls.selectedSeason,
                episode = tvControls.selectedEpisode,
                hasPrev = hasPrevEpisode,
                hasNext = hasNextEpisode,
                onPrev = onPrevEpisode,
                onNext = onNextEpisode,
                onShowChrome = { chromeVisible = true },
                accent = accent
            )
        }

        if (!chromeVisible) {
            Text(
                "Tap screen for controls",
                color = TextSecondary.copy(alpha = 0.55f),
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (tvControls != null) 56.dp else 12.dp)
            )
        }
    }
}

@Composable
private fun PlayerMiniBar(
    onShowChrome: () -> Unit,
    onClose: () -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier
            .glassSurface(cornerRadius = 24.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onShowChrome) {
            Icon(Icons.Default.Fullscreen, contentDescription = "Show controls", tint = accent)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
        }
    }
}

@Composable
private fun PlayerTvMiniRail(
    modifier: Modifier = Modifier,
    season: Int,
    episode: Int,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onShowChrome: () -> Unit,
    accent: Color
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(cornerRadius = 14.dp)
            .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(
            onClick = { onPrev?.invoke() },
            enabled = hasPrev && onPrev != null
        ) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous episode", tint = TextPrimary)
        }
        Text(
            "S${season}E$episode",
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.clickable(onClick = onShowChrome)
        )
        IconButton(
            onClick = { onNext?.invoke() },
            enabled = hasNext && onNext != null
        ) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next episode", tint = TextPrimary)
        }
        IconButton(onClick = onShowChrome) {
            Icon(Icons.Default.Settings, contentDescription = "Season & episode", tint = accent)
        }
    }
}

@Composable
private fun ImmersiveSystemBars(enabled: Boolean) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()

    DisposableEffect(enabled, view, activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowInsetsControllerCompat(window, view)
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        if (enabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
    }
}
