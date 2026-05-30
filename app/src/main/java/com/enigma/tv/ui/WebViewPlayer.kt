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

class PlayerActionDispatcher {
    var onTogglePlay: (() -> Unit)? = null
    var onSeekForward: (() -> Unit)? = null
    var onSeekBackward: (() -> Unit)? = null

    fun togglePlay() = onTogglePlay?.invoke()
    fun seekForward() = onSeekForward?.invoke()
    fun seekBackward() = onSeekBackward?.invoke()
}

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
    actionDispatcher: PlayerActionDispatcher? = null,
    onStreamCaptured: ((String) -> Unit)? = null,
    onStreamFailed: (() -> Unit)? = null,
    onPlaybackReady: (() -> Unit)? = null,
    onLiveWaiting: (() -> Unit)? = null,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackProgress: ((Long) -> Unit)? = null,
    startPositionMs: Long = 0L,
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
        onLiveWaiting,
        onLoadingChange
    ) {
        guard.onStreamUrl = onStreamCaptured
        guard.onBlocked = { /* silent */ }
        guard.onLiveWaiting = onLiveWaiting
        guard.onStreamFailed = onStreamFailed
        guard.onPageLoading = { loading ->
            if (!(liveTv && guard.isStreamPlaying())) {
                if (!(loading && liveTv && !pageLoading)) {
                    pageLoading = loading
                    onLoadingChange(loading)
                }
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

    LaunchedEffect(url, startPositionMs) {
        guard.resetForUrl(url, liveTv = liveTv, resumePositionMs = startPositionMs)
        pageLoading = true
        onLoadingChange(true)
    }

    LaunchedEffect(url, liveTv) {
        // Bail out sooner if page never reaches playing state
        kotlinx.coroutines.delay(if (liveTv) 5_000 else 10_000)
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
            startPositionMs = startPositionMs,
            actionDispatcher = actionDispatcher,
            showOverlaySpinner = !useExternalChrome && (pageLoading || streamLoading),
            onClose = onClose
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
    startPositionMs: Long,
    actionDispatcher: PlayerActionDispatcher?,
    showOverlaySpinner: Boolean,
    onClose: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(actionDispatcher, webViewRef) {
        if (actionDispatcher != null && webViewRef != null) {
            actionDispatcher.onTogglePlay = {
                webViewRef?.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(v => v.paused ? v.play() : v.pause());", null
                )
            }
            actionDispatcher.onSeekForward = {
                webViewRef?.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(v => { if(!isNaN(v.duration)) v.currentTime = Math.min(v.duration, v.currentTime + 10); });", null
                )
            }
            actionDispatcher.onSeekBackward = {
                webViewRef?.evaluateJavascript(
                    "document.querySelectorAll('video').forEach(v => v.currentTime = Math.max(0, v.currentTime - 10));", null
                )
            }
        }
        onDispose {
            actionDispatcher?.onTogglePlay = null
            actionDispatcher?.onSeekForward = null
            actionDispatcher?.onSeekBackward = null
        }
    }

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
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    guard.configureWebView(this, liveTv = liveTv)
                    settings.mediaPlaybackRequiresUserGesture = false
                    setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv)
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_UP && keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                            if (canGoBack()) {
                                goBack()
                            } else {
                                onClose()
                            }
                            true
                        } else false
                    }
                    if (liveTv) LiveWebContent.load(this, url) else loadUrl(url)
                    webViewRef = this
                }
            },
            update = { view ->
                val last = view.getTag(TAG_STREAM_URL) as? String
                if (last != url) {
                    EmbedPlayerShield.stopPeriodic()
                    view.setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv, resumePositionMs = startPositionMs)
                    if (liveTv) LiveWebContent.load(view, url) else view.loadUrl(url)
                }
            },
            onRelease = { view ->
                EmbedPlayerShield.stopPeriodic()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.removeAllViews()
                view.destroy()
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
