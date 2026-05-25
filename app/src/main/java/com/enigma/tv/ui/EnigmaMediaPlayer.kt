package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamPlaybackResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class MediaPlayMode { Resolving, Native, Embed }

/**
 * Movie/TV: VidLink API → all embed mirrors → WebView intercept → embed player fallback.
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
    tvControls: TvPlayerControls? = null,
    resolveToken: Int = 0,
    tmdbId: Int? = null,
    playingType: ContentType? = null,
    season: Int = 1,
    episode: Int = 1
) {
    if (!visible) return

    BackHandler { onClose() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var resolvedStream by remember(embedUrl, resolveToken) { mutableStateOf<ResolvedStream?>(null) }
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(MediaPlayMode.Resolving) }

    LaunchedEffect(embedUrl, resolveToken, activity, tmdbId, playingType, season, episode) {
        mode = MediaPlayMode.Resolving
        onLoadingChange(true)
        resolvedStream = null
        try {
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
            mode = if (resolvedStream != null) MediaPlayMode.Native else MediaPlayMode.Embed
        } catch (_: Exception) {
            resolvedStream = null
            mode = MediaPlayMode.Embed
        } finally {
            onLoadingChange(false)
        }
    }

    val loading = streamLoading || mode == MediaPlayMode.Resolving
    val nativeLabel = resolvedStream?.let { "$sourceLabel · ${it.provider}" } ?: sourceLabel

    Box(Modifier.fillMaxSize().background(BgDark)) {
        when (mode) {
            MediaPlayMode.Native -> ExoLivePlayer(
                visible = true,
                title = title,
                stream = resolvedStream,
                sourceLabel = nativeLabel,
                logoUrl = posterUrl,
                accent = accent,
                streamLoading = loading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = tvControls
            )
            MediaPlayMode.Resolving -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = "Finding stream…",
                    posterUrl = posterUrl,
                    accent = accent,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    tvControls = tvControls
                )
                EnigmaLoadingRing(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    message = "LOADING STREAM"
                )
            }
            MediaPlayMode.Embed -> Column(Modifier.fillMaxSize()) {
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = embedUrl,
                    accent = accent,
                    sourceLabel = "$sourceLabel · Player",
                    posterUrl = posterUrl,
                    streamLoading = loading,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = onLoadingChange,
                    tvControls = tvControls,
                    liveTv = false,
                    useExternalChrome = true,
                    onStreamCaptured = { captured ->
                        resolvedStream = ResolvedStream.fromEmbed(embedUrl, captured, "embed-capture")
                        mode = MediaPlayMode.Native
                    },
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}
