package com.enigma.tv.ui

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
import com.enigma.tv.data.StreamResolver
import com.enigma.tv.ui.theme.BgDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified movie/TV player: tries native ExoPlayer first (direct stream), then embed WebView
 * with the same Enigma chrome as Live TV.
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
    resolveToken: Int = 0
) {
    if (!visible) return

    var directUrl by remember(embedUrl, resolveToken) { mutableStateOf<String?>(null) }
    var resolving by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken) {
        resolving = true
        onLoadingChange(true)
        directUrl = withContext(Dispatchers.IO) {
            StreamResolver.resolveDirectUrl(embedUrl)
        }
        resolving = false
        if (directUrl == null) onLoadingChange(true)
    }

    val useNative = !directUrl.isNullOrBlank()
    val loading = streamLoading || resolving

    Box(Modifier.fillMaxSize().background(BgDark)) {
        if (useNative) {
            ExoLivePlayer(
                visible = true,
                title = title,
                streamUrl = directUrl!!,
                sourceLabel = "$sourceLabel · Native",
                logoUrl = posterUrl,
                accent = accent,
                streamLoading = loading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = tvControls
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = sourceLabel,
                    posterUrl = posterUrl,
                    accent = accent,
                    onClose = onClose,
                    showNextSource = true,
                    onNextSource = onNextSource,
                    tvControls = tvControls
                )
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = embedUrl,
                    accent = accent,
                    sourceLabel = sourceLabel,
                    posterUrl = posterUrl,
                    streamLoading = loading,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = onLoadingChange,
                    tvControls = tvControls,
                    liveTv = false,
                    useExternalChrome = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
