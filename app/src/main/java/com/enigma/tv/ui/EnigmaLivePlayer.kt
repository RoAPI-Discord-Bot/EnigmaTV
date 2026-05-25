package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.ui.theme.TextSecondary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.enigma.tv.data.LiveEmbedResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Live sports: unwrap API/iframe shells, prefer native HLS when found, else WebView embed.
 */
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
    onNativeStream: (String) -> Unit,
    resolveToken: Int = 0,
    useExternalChrome: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    BackHandler { onClose() }

    var playUrl by remember(embedUrl, resolveToken) { mutableStateOf<String?>(null) }
    var resolving by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken) {
        resolving = true
        onLoadingChange(true)
        playUrl = null
        try {
            val resolved = withContext(Dispatchers.IO) {
                LiveEmbedResolver.resolvePlayableUrl(embedUrl)
            }
            if (resolved.contains(".m3u8", ignoreCase = true)) {
                onNativeStream(resolved)
                return@LaunchedEffect
            }
            playUrl = if (LiveEmbedResolver.isUnplayableContent(resolved)) null else resolved
        } catch (_: Exception) {
            playUrl = null
        } finally {
            resolving = false
        }
    }

    Box(modifier.background(BgDark)) {
        when {
            resolving -> {
                EnigmaLoadingRing(
                    modifier = Modifier.fillMaxSize(),
                    message = "CONNECTING LIVE",
                    fullscreen = true
                )
            }
            playUrl.isNullOrBlank() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Text(
                        "Could not open this live stream. Try Next Server or another game.",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                    TextButton(onClick = onClose) { Text("Close") }
                }
            }
            useExternalChrome -> {
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = playUrl!!,
                    accent = EnigmaPurple,
                    sourceLabel = sourceLabel,
                    posterUrl = posterUrl,
                    streamLoading = streamLoading,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = onLoadingChange,
                    liveTv = true,
                    useExternalChrome = true,
                    onStreamCaptured = onNativeStream,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = sourceLabel,
                    posterUrl = posterUrl,
                    accent = EnigmaPurple,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    showNextSource = true,
                    onNextSource = onNextSource,
                    isLive = true
                )
                WebViewPlayer(
                    visible = true,
                    title = title,
                    url = playUrl!!,
                    accent = EnigmaPurple,
                    sourceLabel = sourceLabel,
                    posterUrl = posterUrl,
                    streamLoading = streamLoading,
                    onClose = onClose,
                    onNextSource = onNextSource,
                    onLoadingChange = onLoadingChange,
                    liveTv = true,
                    useExternalChrome = true,
                    onStreamCaptured = onNativeStream,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}
