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
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple

/**
 * Live sports WebView — URL is pre-resolved in [com.enigma.tv.ui.EnigmaViewModel.playLiveEmbed].
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
    onNativeStream: ((String) -> Unit)? = null,
    resolveToken: Int = 0,
    useExternalChrome: Boolean = true,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible || embedUrl.isBlank()) return

    BackHandler { onClose() }

    var playUrl by remember(embedUrl, resolveToken) { mutableStateOf(embedUrl) }

    LaunchedEffect(embedUrl, resolveToken) {
        playUrl = embedUrl
    }

    Box(modifier.background(BgDark)) {
        WebViewPlayer(
            visible = true,
            title = title,
            url = playUrl,
            accent = EnigmaPurple,
            sourceLabel = sourceLabel,
            posterUrl = posterUrl,
            streamLoading = streamLoading && !streamFailed,
            onClose = onClose,
            onNextSource = onNextSource,
            onLoadingChange = onLoadingChange,
            onStreamFailed = onStreamFailed,
            onPlaybackReady = onPlaybackReady,
            liveTv = true,
            useExternalChrome = useExternalChrome,
            onStreamCaptured = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}
