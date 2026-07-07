package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamExtractor
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LivePlayMode { WebView, Native }

/**
 * Live sports player — same hybrid approach as EnigmaMediaPlayer:
 * WebView shows immediately while a hidden extractor sniffs for the HLS/MP4 stream.
 * Once found, seamlessly upgrades to ExoPlayer (no sandbox, no JS injection needed).
 * Falls back to WebView if extraction fails or times out.
 */
@Composable
fun EnigmaLivePlayer(
    visible: Boolean,
    title: String,
    embedUrl: String,
    posterUrl: String?,
    sourceLabel: String,
    streamLoading: Boolean,
    streamFailed: Boolean,
    onClose: () -> Unit,
    onNextSource: () -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onStreamFailed: () -> Unit,
    onPlaybackReady: () -> Unit,
    onLiveWaiting: () -> Unit = {},
    onNativeStream: ((String) -> Unit)? = null,
    onNativePlayerActive: ((Boolean) -> Unit)? = null,
    resolveToken: Int = 0,
    useExternalChrome: Boolean = true,
    actionDispatcher: PlayerActionDispatcher? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible || embedUrl.isBlank()) return

    BackHandler { onClose() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var resolvedStream by remember(embedUrl, resolveToken) { mutableStateOf<ResolvedStream?>(null) }
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(LivePlayMode.WebView) }

    // While WebView plays, probe the embed URL in a hidden extractor for a raw HLS/MP4 stream.
    // If found, upgrade to ExoPlayer — same pattern as EnigmaMediaPlayer.
    // This completely avoids sandbox/JS issues since we never interact with the embed page UI.
    LaunchedEffect(embedUrl, resolveToken) {
        resolvedStream = null
        mode = LivePlayMode.WebView
        onNativePlayerActive?.invoke(false)
        val found = withContext(Dispatchers.IO) {
            try {
                StreamExtractor(context).extractStreamUrl(embedUrl, activity = activity)
            } catch (_: Exception) { null }
        }
        if (found != null) {
            resolvedStream = found
            mode = LivePlayMode.Native
            onNativePlayerActive?.invoke(true)
            onLoadingChange(true)
        }
        // If nothing found, WebView is already playing — no action needed
    }

    Box(modifier.background(BgDark)) {
        when (mode) {
            LivePlayMode.Native -> ExoLivePlayer(
                visible = true,
                title = title,
                stream = resolvedStream,
                sourceLabel = "$sourceLabel · native",
                logoUrl = posterUrl,
                accent = EnigmaPurple,
                streamLoading = streamLoading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                isLiveBroadcast = true,
                showNextSource = true,
                onNextSource = onNextSource,
                onPlaybackReady = onPlaybackReady,
                useExternalChrome = useExternalChrome,
                modifier = Modifier.fillMaxSize()
            )

            LivePlayMode.WebView -> WebViewPlayer(
                visible = true,
                title = title,
                url = embedUrl,
                accent = EnigmaPurple,
                sourceLabel = sourceLabel,
                posterUrl = posterUrl,
                streamLoading = streamLoading && !streamFailed,
                onClose = onClose,
                onNextSource = onNextSource,
                onLoadingChange = onLoadingChange,
                onStreamFailed = onStreamFailed,
                onPlaybackReady = onPlaybackReady,
                onLiveWaiting = onLiveWaiting,
                liveTv = true,
                useExternalChrome = useExternalChrome,
                actionDispatcher = actionDispatcher,
                onStreamCaptured = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
