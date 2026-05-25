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
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackPositionMs: ((Long) -> Unit)? = null,
    startPositionMs: Long = 0L,
    tvControls: TvPlayerControls? = null,
    resolveToken: Int = 0,
    tmdbId: Int? = null,
    playingType: ContentType? = null,
    season: Int = 1,
    episode: Int = 1,
    useExternalChrome: Boolean = false,
    contentModifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    BackHandler { onClose() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var resolvedStream by remember(embedUrl, resolveToken) { mutableStateOf<ResolvedStream?>(null) }
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(MediaPlayMode.Embed) }
    var resolvingNative by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken, activity, tmdbId, playingType) {
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
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = if (useExternalChrome) null else tvControls,
                useExternalChrome = useExternalChrome,
                onPlaybackEnded = onPlaybackEnded,
                onPlaybackPositionMs = onPlaybackPositionMs,
                startPositionMs = startPositionMs,
                modifier = Modifier.fillMaxSize()
            )
            MediaPlayMode.Embed -> {
                val embedColumn: @Composable () -> Unit = {
                    if (!useExternalChrome) {
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
                            showEpisodesButton = tvControls != null,
                            onShowEpisodes = null
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
                    }
                    WebViewPlayer(
                        visible = true,
                        title = title,
                        url = embedUrl,
                        accent = accent,
                        sourceLabel = sourceLabel,
                        posterUrl = posterUrl,
                        streamLoading = streamLoading && useExternalChrome,
                        onClose = onClose,
                        onNextSource = onNextSource,
                        onLoadingChange = onLoadingChange,
                        onPlaybackReady = { onLoadingChange(false) },
                        onStreamFailed = { onLoadingChange(false) },
                        tvControls = null,
                        liveTv = false,
                        useExternalChrome = true,
                        onStreamCaptured = { captured ->
                            resolvedStream = ResolvedStream.fromEmbed(embedUrl, captured, "embed-capture")
                            mode = MediaPlayMode.Native
                            resolvingNative = false
                        },
                        onPlaybackEnded = if (playingType == ContentType.TV) onPlaybackEnded else null,
                        onPlaybackProgress = onPlaybackPositionMs?.let { cb ->
                            { ms: Long -> cb(ms) }
                        },
                        startPositionMs = startPositionMs,
                        modifier = Modifier.fillMaxSize()
                    )
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
