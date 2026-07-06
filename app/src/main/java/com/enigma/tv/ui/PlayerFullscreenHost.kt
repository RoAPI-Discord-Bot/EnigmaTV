package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
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
    isNativePlayerActive: Boolean = false,
    content: @Composable (PlayerActionDispatcher) -> Unit
) {
    // Chrome starts HIDDEN — only show on user interaction
    var chromeVisible by remember { mutableStateOf(false) }
    var episodePanelOpen by remember { mutableStateOf(false) }
    val hasTv = tvControls != null
    val isLivePlayer = subtitle.contains("Live", ignoreCase = true)
    val actionDispatcher = remember { PlayerActionDispatcher() }
    
    androidx.compose.runtime.SideEffect {
        actionDispatcher.onShowEpisodesRequest = {
            if (hasTv) {
                chromeVisible = true
                episodePanelOpen = true
            }
        }
    }

    val syncChrome: (Boolean) -> Unit = { visible ->
        chromeVisible = visible
    }

    // Auto-hide chrome after 4 seconds when playing
    LaunchedEffect(chromeVisible, streamFailed, episodePanelOpen) {
        if (chromeVisible && !streamFailed && streamPlaying && !episodePanelOpen) {
            kotlinx.coroutines.delay(4000)
            chromeVisible = false
        }
    }

    LaunchedEffect(streamFailed) {
        if (streamFailed) chromeVisible = true
    }

    // Always show chrome while loading or failed so the X button is always reachable
    LaunchedEffect(streamLoading, streamFailed) {
        if (streamLoading || streamFailed) chromeVisible = true
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

    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(chromeVisible, isNativePlayerActive) {
        if (!chromeVisible && !isNativePlayerActive) {
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // ── Register Activity-level key handler so remote keys are ALWAYS caught ──
    // SideEffect re-registers after every recompose so the lambda always closes
    // over fresh chromeVisible / isNativePlayerActive state.
    androidx.compose.runtime.SideEffect {
        RemoteKeyRouter.handler = { keyCode ->
            when (keyCode) {
                // Media keys ALWAYS work directly — no chrome needed
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                android.view.KeyEvent.KEYCODE_MEDIA_PLAY,
                android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    actionDispatcher.togglePlay(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    actionDispatcher.seekForward(); true
                }
                android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    actionDispatcher.seekBackward(); true
                }
                // DPAD: if chrome hidden → show it and consume.
                // If chrome visible → return false so Compose focus moves between buttons.
                android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                android.view.KeyEvent.KEYCODE_DPAD_UP,
                android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (!chromeVisible) {
                        chromeVisible = true
                        true  // consumed — just open chrome
                    } else {
                        false // let Compose focus system handle it
                    }
                }
                else -> false
            }
        }
    }
    DisposableEffect(Unit) { onDispose { RemoteKeyRouter.handler = null } }

    CompositionLocalProvider(LocalPlayerChromeSync provides syncChrome) {
        Box(
            Modifier
                .fillMaxSize()
                .background(BgDark)
                .focusRequester(rootFocusRequester)
                .then(if (!isNativePlayerActive && !chromeVisible) Modifier.focusable() else Modifier)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // Tap anywhere to toggle chrome
                    chromeVisible = !chromeVisible
                }
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key.nativeKeyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                actionDispatcher.togglePlay()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (isNativePlayerActive) {
                                    actionDispatcher.seekForward()
                                    true
                                } else if (!chromeVisible) {
                                    chromeVisible = true
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND,
                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (isNativePlayerActive) {
                                    actionDispatcher.seekBackward()
                                    true
                                } else if (!chromeVisible) {
                                    chromeVisible = true
                                    true
                                } else false
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER -> {
                                if (isNativePlayerActive) {
                                    actionDispatcher.togglePlay()
                                } else if (!chromeVisible) {
                                    chromeVisible = true
                                } else {
                                    actionDispatcher.togglePlay()
                                }
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (!chromeVisible) {
                                    chromeVisible = true
                                    true
                                } else false
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
            Box(Modifier.fillMaxSize()) {
                content(actionDispatcher)
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
                // Show close button during loading so user can always exit
                Box(Modifier.fillMaxSize()) {
                    EnigmaLoadingRing(
                        modifier = Modifier.fillMaxSize(),
                        message = if (subtitle.contains("Live", ignoreCase = true)) {
                            "Connecting..."
                        } else {
                            "Loading..."
                        },
                        logoSize = 60.dp,
                        ringSize = 90.dp,
                        fullscreen = true
                    )
                    // Always-visible close button so user isn't stuck
                    androidx.compose.material3.IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
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
                visible = chromeVisible && !streamLoading && !isNativePlayerActive,
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
                    visible = episodePanelOpen,
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

@Composable
fun PlaybackControlsRow(
    actionDispatcher: PlayerActionDispatcher,
    accent: Color,
    isTvLayout: Boolean
) {
    var isPlaying by remember { mutableStateOf(true) }
    var restartFocused by remember { mutableStateOf(false) }
    var rewindFocused by remember { mutableStateOf(false) }
    var playFocused by remember { mutableStateOf(false) }
    var forwardFocused by remember { mutableStateOf(false) }

    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (isTvLayout) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sideSize = if (isTvLayout) 52.dp else 40.dp
        val sideIconSize = if (isTvLayout) 28.dp else 22.dp

        // Restart
        IconButton(
            onClick = { actionDispatcher.restart() },
            modifier = Modifier
                .size(sideSize)
                .focusable()
                .onFocusChanged { restartFocused = it.isFocused }
                .background(
                    if (restartFocused) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(percent = 50)
                )
                .then(
                    if (restartFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(percent = 50))
                    else Modifier
                )
        ) {
            Icon(Icons.Default.Replay, contentDescription = "Restart", tint = TextPrimary, modifier = Modifier.size(sideIconSize))
        }

        Spacer(modifier = Modifier.width(if (isTvLayout) 32.dp else 20.dp))

        // Rewind
        IconButton(
            onClick = { actionDispatcher.seekBackward() },
            modifier = Modifier
                .size(sideSize)
                .focusable()
                .onFocusChanged { rewindFocused = it.isFocused }
                .background(
                    if (rewindFocused) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(percent = 50)
                )
                .then(
                    if (rewindFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(percent = 50))
                    else Modifier
                )
        ) {
            Icon(Icons.Default.FastRewind, contentDescription = "Rewind", tint = TextPrimary, modifier = Modifier.size(sideIconSize))
        }

        Spacer(modifier = Modifier.width(if (isTvLayout) 32.dp else 20.dp))

        // Play / Pause — centre button, biggest
        val playSize = if (isTvLayout) 64.dp else 52.dp
        IconButton(
            onClick = {
                isPlaying = !isPlaying
                actionDispatcher.togglePlay()
            },
            modifier = Modifier
                .size(playSize)
                .focusRequester(playFocusRequester)
                .onFocusChanged { playFocused = it.isFocused }
                .background(
                    if (playFocused) Color.White else accent,
                    RoundedCornerShape(percent = 50)
                )
                .then(
                    if (playFocused) Modifier.border(3.dp, Color.White, RoundedCornerShape(percent = 50))
                    else Modifier
                )
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                tint = if (playFocused) accent else Color.White,
                modifier = Modifier.size(if (isTvLayout) 34.dp else 28.dp)
            )
        }

        Spacer(modifier = Modifier.width(if (isTvLayout) 32.dp else 20.dp))

        // Fast Forward
        IconButton(
            onClick = { actionDispatcher.seekForward() },
            modifier = Modifier
                .size(sideSize)
                .focusable()
                .onFocusChanged { forwardFocused = it.isFocused }
                .background(
                    if (forwardFocused) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.10f),
                    RoundedCornerShape(percent = 50)
                )
                .then(
                    if (forwardFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(percent = 50))
                    else Modifier
                )
        ) {
            Icon(Icons.Default.FastForward, contentDescription = "Fast Forward", tint = TextPrimary, modifier = Modifier.size(sideIconSize))
        }
    }
}
