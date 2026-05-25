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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.enigma.tv.util.findActivity
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPink
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary

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
    streamFailed: Boolean = false,
    streamLoading: Boolean = false,
    streamPlaying: Boolean = false,
    liveWaitingMessage: String? = null,
    tvControls: TvPlayerControls? = null,
    onPrevEpisode: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    hasPrevEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
    content: @Composable () -> Unit
) {
    var chromeVisible by remember { mutableStateOf(true) }
    var episodePanelOpen by remember { mutableStateOf(false) }
    val hasTv = tvControls != null
    val isLivePlayer = subtitle.contains("Live", ignoreCase = true)

    val syncChrome: (Boolean) -> Unit = { visible ->
        chromeVisible = visible
        if (!visible) episodePanelOpen = false
    }

    LaunchedEffect(streamFailed) {
        if (streamFailed) chromeVisible = true
    }

    LaunchedEffect(chromeVisible) {
        if (!chromeVisible) episodePanelOpen = false
    }

    // Live WebView: keep fullscreen layout stable when toggling chrome (avoids resize/black screen on tap)
    ImmersiveSystemBars(enabled = !isLivePlayer && !chromeVisible && !episodePanelOpen)

    BackHandler {
        when {
            episodePanelOpen -> episodePanelOpen = false
            chromeVisible && !streamFailed -> chromeVisible = false
            else -> onClose()
        }
    }

    CompositionLocalProvider(LocalPlayerChromeSync provides syncChrome) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BgDark)
        ) {
            Box(Modifier.fillMaxSize()) {
                content()
            }

            if (!chromeVisible && !streamLoading && !streamFailed) {
                IconButton(
                    onClick = { chromeVisible = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Show controls",
                        tint = TextPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            val showLiveMessage = !streamPlaying &&
                !liveWaitingMessage.isNullOrBlank() &&
                !streamFailed &&
                !streamLoading

            if (streamLoading && !showLiveMessage) {
                EnigmaLoadingRing(
                    modifier = Modifier.fillMaxSize(),
                    message = if (subtitle.contains("Live", ignoreCase = true)) {
                        "CONNECTING LIVE"
                    } else {
                        "LOADING STREAM"
                    },
                    logoSize = 72.dp,
                    ringSize = 110.dp,
                    fullscreen = true
                )
            }

            if (showLiveMessage) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.82f))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        liveWaitingMessage!!,
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (showNextSource && onNextSource != null) {
                        Button(
                            onClick = onNextSource,
                            colors = ButtonDefaults.buttonColors(containerColor = EnigmaPink),
                            modifier = Modifier.padding(top = 20.dp)
                        ) {
                            Text("Try another source")
                        }
                    }
                }
            }

            if (streamFailed && showNextSource && onNextSource != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (subtitle.contains("Live", ignoreCase = true)) {
                            "Feed not available yet"
                        } else {
                            "Stream didn't start"
                        },
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (subtitle.contains("Live", ignoreCase = true)) {
                            "This game may not be on air yet, or this mirror is down. Try Next Server or check back closer to game time."
                        } else {
                            "Try the next server — some feeds only work on certain mirrors."
                        },
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
                    )
                    Button(
                        onClick = onNextSource,
                        colors = ButtonDefaults.buttonColors(containerColor = EnigmaPink),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, tint = TextPrimary)
                        Text(
                            "Next Server",
                            modifier = Modifier.padding(start = 8.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible && !streamLoading,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* keep chrome open while interacting */ }
            ) {
                Column(Modifier.fillMaxWidth()) {
                    PlayerChrome(
                        title = title,
                        subtitle = subtitle,
                        posterUrl = posterUrl,
                        accent = accent,
                        onClose = onClose,
                        showBack = false,
                        showNextSource = showNextSource && !streamFailed,
                        onNextSource = onNextSource,
                        showEpisodesButton = hasTv,
                        onShowEpisodes = if (hasTv) {
                            { episodePanelOpen = !episodePanelOpen }
                        } else null,
                        isTvLayout = layout == ScreenLayout.TV,
                        isLive = subtitle.contains("Live", ignoreCase = true)
                    )
                }
            }

            if (hasTv) {
                TvEpisodePickerPanel(
                    visible = episodePanelOpen && chromeVisible,
                    controls = tvControls!!,
                    accent = accent,
                    onDismiss = { episodePanelOpen = false },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
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
