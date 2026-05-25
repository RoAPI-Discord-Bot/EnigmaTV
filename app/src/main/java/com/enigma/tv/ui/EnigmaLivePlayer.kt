package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.LinearProgressIndicator
import com.enigma.tv.data.LiveEmbedResolver
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamPlaybackResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.util.findActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class LivePlayMode { Embed, Native }

@Composable
fun EnigmaLivePlayer(
    visible: Boolean,
    title: String,
    embedUrl: String,
    posterUrl: String?,
    sourceLabel: String,
    streamLoading: Boolean,
    onClose: () -> Unit,
    onNextSource: () -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    resolveToken: Int = 0
) {
    if (!visible) return

    BackHandler { onClose() }

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var resolvedStream by remember(embedUrl, resolveToken) { mutableStateOf<ResolvedStream?>(null) }
    var playUrl by remember(embedUrl, resolveToken) { mutableStateOf(embedUrl) }
    var mode by remember(embedUrl, resolveToken) { mutableStateOf(LivePlayMode.Embed) }
    var resolvingNative by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken, activity) {
        resolvingNative = true
        onLoadingChange(false)
        resolvedStream = null
        mode = LivePlayMode.Embed
        try {
            val resolved = withContext(Dispatchers.IO) {
                LiveEmbedResolver.resolvePlayableUrl(embedUrl)
            }
            playUrl = resolved
            resolvedStream = withContext(Dispatchers.IO) {
                StreamPlaybackResolver.resolve(
                    context = context,
                    embedUrl = resolved,
                    activity = activity,
                    tmdbId = null,
                    type = null
                ) ?: StreamPlaybackResolver.resolve(
                    context = context,
                    embedUrl = embedUrl,
                    activity = activity,
                    tmdbId = null,
                    type = null
                )
            }
            if (resolvedStream != null) {
                mode = LivePlayMode.Native
            }
        } catch (_: Exception) {
            resolvedStream = null
        } finally {
            resolvingNative = false
        }
    }

    val loading = streamLoading && mode == LivePlayMode.Native

    Box(Modifier.fillMaxSize().background(BgDark)) {
        when (mode) {
            LivePlayMode.Native -> ExoLivePlayer(
                visible = true,
                title = title,
                stream = resolvedStream,
                sourceLabel = "$sourceLabel · Direct",
                logoUrl = posterUrl,
                accent = EnigmaPurple,
                streamLoading = loading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource
            )
            LivePlayMode.Embed -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = if (resolvingNative) "$sourceLabel · loading…" else sourceLabel,
                    posterUrl = posterUrl,
                    accent = EnigmaPurple,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    showNextSource = true,
                    onNextSource = onNextSource
                )
                if (resolvingNative) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = EnigmaPurple,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                }
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = playUrl,
                    accent = EnigmaPurple,
                    sourceLabel = sourceLabel,
                    posterUrl = posterUrl,
                    streamLoading = false,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = { },
                    liveTv = true,
                    useExternalChrome = true,
                    onStreamCaptured = { captured ->
                        resolvedStream = ResolvedStream.fromEmbed(playUrl, captured, "live-capture")
                        mode = LivePlayMode.Native
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
