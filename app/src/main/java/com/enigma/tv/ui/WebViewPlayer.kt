package com.enigma.tv.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.enigma.tv.ui.theme.BgDark

private const val TAG_STREAM_URL = 0xE71A001

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebViewPlayer(
    visible: Boolean,
    title: String,
    url: String,
    accent: Color,
    sourceLabel: String,
    streamLoading: Boolean,
    onClose: () -> Unit,
    onNextSource: () -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    tvControls: TvPlayerControls? = null,
    liveTv: Boolean = false,
    posterUrl: String? = null,
    useExternalChrome: Boolean = false,
    onStreamCaptured: ((String) -> Unit)? = null,
    onStreamFailed: (() -> Unit)? = null,
    onPlaybackReady: (() -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackProgress: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    var pageLoading by remember(url) { mutableStateOf(true) }

    val guard = remember(liveTv) { WebViewNavigationGuard("") }

    LaunchedEffect(
        guard,
        onStreamCaptured,
        onStreamFailed,
        onPlaybackReady,
        onPlaybackEnded,
        onPlaybackProgress,
        onLoadingChange
    ) {
        guard.onStreamUrl = onStreamCaptured
        guard.onBlocked = { /* silent */ }
        guard.onPageLoading = { loading ->
            if (!(loading && liveTv && !pageLoading)) {
                pageLoading = loading
                onLoadingChange(loading)
            }
        }
        guard.onPlaybackProbe = { ok ->
            if (ok) {
                pageLoading = false
                onPlaybackReady?.invoke() ?: onLoadingChange(false)
            } else if (liveTv) {
                pageLoading = false
                onLoadingChange(false)
            } else {
                onStreamFailed?.invoke()
            }
        }
        guard.onPlaybackEnded = onPlaybackEnded
        guard.onPlaybackProgress = onPlaybackProgress
    }

    LaunchedEffect(url) {
        pageLoading = true
        onLoadingChange(true)
    }

    LaunchedEffect(url, liveTv) {
        kotlinx.coroutines.delay(if (liveTv) 7_000 else 14_000)
        if (pageLoading) {
            pageLoading = false
            onPlaybackReady?.invoke() ?: onLoadingChange(false)
        }
    }

    val content: @Composable ColumnScope.() -> Unit = {
        if (!useExternalChrome) {
            PlayerChrome(
                title = title,
                subtitle = sourceLabel,
                posterUrl = posterUrl,
                accent = accent,
                onClose = onClose,
                showBack = liveTv,
                onBack = onClose,
                showNextSource = true,
                onNextSource = onNextSource,
                showEpisodesButton = tvControls != null
            )
        }
        WebViewStreamBody(
            url = url,
            liveTv = liveTv,
            guard = guard,
            showOverlaySpinner = !useExternalChrome && (pageLoading || streamLoading)
        )
    }

    if (useExternalChrome) {
        Column(modifier = modifier.background(BgDark), content = content)
    } else {
        Box(modifier = modifier.background(BgDark)) {
            Column(modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}

@Composable
private fun ColumnScope.WebViewStreamBody(
    url: String,
    liveTv: Boolean,
    guard: WebViewNavigationGuard,
    showOverlaySpinner: Boolean
) {
    DisposableEffect(url) {
        onDispose { EmbedPlayerShield.stopPeriodic() }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = true)
    ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    guard.configureWebView(this, liveTv = liveTv)
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    settings.mediaPlaybackRequiresUserGesture = false
                    setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv)
                    if (liveTv) LiveWebContent.load(this, url) else loadUrl(url)
                }
            },
            update = { view ->
                val last = view.getTag(TAG_STREAM_URL) as? String
                if (last != url) {
                    EmbedPlayerShield.stopPeriodic()
                    view.setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv)
                    if (liveTv) LiveWebContent.load(view, url) else view.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (showOverlaySpinner) {
            EnigmaLoadingRing(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark.copy(alpha = 0.88f)),
                message = if (liveTv) "CONNECTING LIVE" else "LOADING STREAM",
                logoSize = 72.dp,
                ringSize = 110.dp
            )
        }
    }
}
