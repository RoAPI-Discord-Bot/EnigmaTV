package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enigma.tv.data.ContentType
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamPlaybackResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(MediaPlayMode.Embed) }
    var resolvingNative by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken, activity, tmdbId, playingType, season, episode) {
        resolvingNative = true
        onLoadingChange(false)
        resolvedStream = null
        mode = MediaPlayMode.Embed
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
            mode = MediaPlayMode.Native
        }
    }

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
                streamLoading = streamLoading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = tvControls
            )
            MediaPlayMode.Embed -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = if (resolvingNative) "$sourceLabel · loading player…" else sourceLabel,
                    posterUrl = posterUrl,
                    accent = accent,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    showNextSource = true,
                    onNextSource = onNextSource,
                    tvControls = tvControls
                )
                if (resolvingNative) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = embedUrl,
                    accent = accent,
                    sourceLabel = sourceLabel,
                    posterUrl = posterUrl,
                    streamLoading = false,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = { /* embed uses its own page load state */ },
                    tvControls = tvControls,
                    liveTv = false,
                    useExternalChrome = true,
                    onStreamCaptured = { captured ->
                        resolvedStream = ResolvedStream.fromEmbed(embedUrl, captured, "embed-capture")
                        mode = MediaPlayMode.Native
                        resolvingNative = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                )
            }
        }
    }
}
