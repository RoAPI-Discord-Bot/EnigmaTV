package com.enigma.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamPlaybackResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.TextPrimary
import com.enigma.tv.ui.theme.TextSecondary
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enigma.tv.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close

private enum class MediaPlayMode { Embed, Native }

/**
 * Flux-style hybrid: embed WebView plays immediately; native Exo upgrades when a direct URL is found.
 */
@Composable
fun EnigmaMediaPlayer(
    visible: Boolean,
    title: String,
    embedUrl: String,
    posterUrl: String?,
    accent: Color,
    sourceLabel: String,
    streamLoading: Boolean,
    onClose: () -> Unit,
    onNextSource: () -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackPositionMs: ((Long) -> Unit)? = null,
    onPlaybackDurationMs: ((Long) -> Unit)? = null,
    onShowEpisodes: (() -> Unit)? = null,
    onNativePlayerActive: ((Boolean) -> Unit)? = null,
    startPositionMs: Long = 0L,
    tvControls: TvPlayerControls? = null,
    resolveToken: Int = 0,
    tmdbId: Int? = null,
    playingType: ContentType? = null,
    season: Int = 1,
    episode: Int = 1,
    useExternalChrome: Boolean = false,
    actionDispatcher: PlayerActionDispatcher? = null,
    contentModifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = remember(context) { context.findActivity() }
    var resolvedStream by remember(embedUrl, resolveToken) { mutableStateOf<ResolvedStream?>(null) }
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(MediaPlayMode.Embed) }
    var resolvingNative by remember(embedUrl, resolveToken) { mutableStateOf(true) }
    var streamFailed by remember(embedUrl, resolveToken) { mutableStateOf(false) }

    // Start in Embed mode immediately — WebView loads while we probe for a native stream
    LaunchedEffect(embedUrl, resolveToken, activity, tmdbId, playingType) {
        resolvingNative = true
        streamFailed = false
        resolvedStream = null
        mode = MediaPlayMode.Embed
        onNativePlayerActive?.invoke(false)
        // Drop any loading overlay immediately — WebView renders transparently (like a browser).
        // The spinner only makes sense for ExoPlayer buffering, not WebView page loads.
        onLoadingChange(false)
        // Probe for a native stream in the background while WebView is already visible
        resolvedStream = withContext(Dispatchers.IO) {
            StreamPlaybackResolver.resolve(
                context = context,
                embedUrl = embedUrl,
                activity = activity,
                tmdbId = tmdbId,
                type = playingType,
                season = season,
                episode = episode
            )
        }
        resolvingNative = false
        if (resolvedStream != null) {
            // Seamless upgrade: signal loading so ExoPlayer buffering spinner shows
            onLoadingChange(true)
            mode = MediaPlayMode.Native
            onNativePlayerActive?.invoke(true)
        } else {
            // All API and fallback sources failed.
            // Do not auto-skip; instead show an error so the user knows this title has no stream.
            streamFailed = true
        }
    }

    val nativeLabel = resolvedStream?.let { "$sourceLabel · ${it.provider}" } ?: sourceLabel

    Box(contentModifier.background(BgDark)) {
        when (mode) {
            MediaPlayMode.Native -> ExoLivePlayer(
                visible = true,
                title = title,
                stream = resolvedStream,
                sourceLabel = nativeLabel,
                logoUrl = posterUrl,
                accent = accent,
                streamLoading = streamLoading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = false,
                onNextSource = onNextSource,
                onShowEpisodes = { actionDispatcher?.requestShowEpisodes() },
                tvControls = tvControls,
                useExternalChrome = useExternalChrome,
                onPlaybackEnded = onPlaybackEnded,
                onPlaybackPositionMs = onPlaybackPositionMs,
                onPlaybackDurationMs = onPlaybackDurationMs,
                startPositionMs = startPositionMs,
                actionDispatcher = actionDispatcher,
                modifier = Modifier.fillMaxSize()
            )
            MediaPlayMode.Embed -> {
                val embedColumn: @Composable () -> Unit = {
                    if (!useExternalChrome) {
                        if (!resolvingNative) {
                            // Show top bar only after native resolution is done
                            PlayerChrome(
                                title = title,
                                subtitle = sourceLabel,
                                posterUrl = posterUrl,
                                accent = accent,
                                onClose = onClose,
                                showBack = true,
                                onBack = onClose,
                                showNextSource = true,
                                onNextSource = onNextSource,
                                showEpisodesButton = tvControls != null,
                                onShowEpisodes = onShowEpisodes
                            )
                        } else {
                            // During native resolve: just close button, no top bar clutter
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                androidx.compose.material3.IconButton(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(12.dp)
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = TextPrimary
                                    )
                                }
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .align(Alignment.BottomCenter),
                                    color = accent,
                                    trackColor = Color.White.copy(alpha = 0.12f)
                                )
                            }
                        }
                    }
                    if (streamFailed) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Stream unavailable",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "We couldn't find a playable stream for this title.",
                                    color = TextSecondary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    } else {
                        WebViewPlayer(
                            visible = true,
                            title = title,
                            url = embedUrl,
                            accent = accent,
                            sourceLabel = sourceLabel,
                            posterUrl = posterUrl,
                            streamLoading = false, // never block WebView with a spinner
                            onClose = onClose,
                            onNextSource = onNextSource,
                            // Suppress WebView's loading callbacks — it renders transparently.
                            // We already cleared loading above; don't let it re-raise the overlay.
                            onLoadingChange = { /* WebView renders like a browser — no spinner */ },
                            onPlaybackReady = { /* no-op: WebView is already visible */ },
                            onStreamFailed = { onLoadingChange(false) },
                            tvControls = null,
                            liveTv = false,
                            useExternalChrome = true,
                            onStreamCaptured = { captured, capturedSubtitleUrl ->
                                if (mode != MediaPlayMode.Native) {
                                    android.util.Log.d("EnigmaCapture", "Captured stream URL: $captured | subtitle: $capturedSubtitleUrl")
                                    scope.launch {
                                        val mgr = android.webkit.CookieManager.getInstance()
                                        val c1 = mgr.getCookie(embedUrl) ?: ""
                                        val c2 = mgr.getCookie(captured) ?: ""
                                        val mergedCookies = (c1.split(";") + c2.split(";"))
                                            .map { it.trim() }
                                            .filter { it.isNotEmpty() }
                                            .distinct()
                                            .joinToString("; ")
                                        
                                        val lowerUrl = captured.lowercase()
                                        val provider = when {
                                            lowerUrl.contains(".m3u8") || lowerUrl.contains("playlist") -> "embed-hls"
                                            lowerUrl.contains(".mp4") -> "embed-mp4"
                                            else -> "embed-hls"
                                        }
                                        android.util.Log.d("EnigmaCapture", "Detected provider: $provider")
                                        val webViewUserAgent = android.webkit.WebSettings.getDefaultUserAgent(context)
                                        
                                        // Use subtitle captured by the visible WebView directly (most reliable path)
                                        // Fall back to HLS manifest parsing if the WebView didn't intercept a .vtt
                                        val subtitleUrl = capturedSubtitleUrl
                                            ?: kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.enigma.tv.data.StreamResolver.resolveSubtitleFromHls(captured)
                                            }
                                        android.util.Log.d("EnigmaCapture", "Final subtitle URL: $subtitleUrl")
                                        
                                        resolvedStream = com.enigma.tv.data.ResolvedStream.fromEmbed(embedUrl, captured, provider, mergedCookies, webViewUserAgent).copy(subtitleUrl = subtitleUrl)
                                        mode = MediaPlayMode.Native
                                        resolvingNative = false
                                        onNativePlayerActive?.invoke(true)
                                    }
                                }
                            },
                            onPlaybackEnded = if (playingType == ContentType.TV) onPlaybackEnded else null,
                            onPlaybackProgress = onPlaybackPositionMs?.let { cb ->
                                { ms: Long -> cb(ms) }
                            },
                            startPositionMs = startPositionMs,
                            actionDispatcher = actionDispatcher,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                if (useExternalChrome) {
                    Box(Modifier.fillMaxSize()) { embedColumn() }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        embedColumn()
                    }
                }
            }
        }
    }
}
