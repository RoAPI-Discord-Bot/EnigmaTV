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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enigma.tv.data.LiveEmbedResolver
import com.enigma.tv.data.StreamExtractor
import com.enigma.tv.data.StreamResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextSecondary
import com.enigma.tv.util.findActivity
import com.enigma.tv.util.isTelevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val skipWebView = remember(context) { context.isTelevision() }
    var directUrl by remember(embedUrl, resolveToken) { mutableStateOf<String?>(null) }
    var resolving by remember(embedUrl, resolveToken) { mutableStateOf(true) }

    LaunchedEffect(embedUrl, resolveToken, activity, skipWebView) {
        resolving = true
        onLoadingChange(true)
        directUrl = null
        try {
            val resolved = withContext(Dispatchers.IO) {
                LiveEmbedResolver.resolvePlayableUrl(embedUrl)
            }
            directUrl = withContext(Dispatchers.IO) {
                StreamResolver.resolveDirectUrl(resolved)
            }
            if (directUrl.isNullOrBlank() && !skipWebView && activity != null) {
                directUrl = StreamExtractor(context).extractStreamUrl(
                    embedUrl = resolved,
                    referer = embedUrl,
                    activity = activity
                )
            }
            if (directUrl.isNullOrBlank() && !skipWebView && activity != null) {
                directUrl = StreamExtractor(context).extractStreamUrl(
                    embedUrl = embedUrl,
                    activity = activity
                )
            }
        } catch (_: Exception) {
            directUrl = null
        } finally {
            resolving = false
            onLoadingChange(false)
        }
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
                accent = EnigmaPurple,
                streamLoading = loading,
                onClose = onClose,
                onLoadingChange = onLoadingChange,
                showNextSource = true,
                onNextSource = onNextSource
            )
            resolving -> Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = "Extracting stream…",
                    posterUrl = posterUrl,
                    accent = EnigmaPurple,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose
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
                    accent = EnigmaPurple,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    showNextSource = true,
                    onNextSource = onNextSource
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (skipWebView) {
                                "No direct stream on TV for this source — try next source."
                            } else {
                                "Couldn't load this live stream directly. Try another source."
                            },
                            color = TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        TextButton(onClick = onNextSource) {
                            Text("Try next source", color = EnigmaPurple, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}
