package com.enigma.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.data.StreamExtractor
import com.enigma.tv.data.StreamResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Movie/TV player: resolves direct HLS/MP4 via HTTP + hidden WebView extraction, then ExoPlayer only.
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

    BackHandler { onClose() }

    val appContext = LocalContext.current.applicationContext
    var directUrl by remember(embedUrl, resolveToken) { mutableStateOf<String?>(null) }
    var resolving by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken) {
        resolving = true
        onLoadingChange(true)
        directUrl = withContext(Dispatchers.IO) {
            StreamResolver.resolveDirectUrl(embedUrl)
        }
        if (directUrl.isNullOrBlank()) {
            directUrl = StreamExtractor(appContext).extractStreamUrl(embedUrl)
        }
        resolving = false
        onLoadingChange(false)
    }

    val useNative = !directUrl.isNullOrBlank()
    val loading = streamLoading || resolving

    Box(Modifier.fillMaxSize().background(BgDark)) {
        when {
            useNative -> ExoLivePlayer(
                visible = true,
                title = title,
                streamUrl = directUrl!!,
                sourceLabel = "$sourceLabel · Direct",
                logoUrl = posterUrl,
                accent = accent,
                streamLoading = loading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = tvControls
            )
            resolving -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = "Extracting stream…",
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
            else -> Column(Modifier.fillMaxSize()) {
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
                    tvControls = tvControls
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Couldn't extract a direct stream for this source.",
                            color = TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        TextButton(onClick = onNextSource) {
                            Text("Try next server", color = accent, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
