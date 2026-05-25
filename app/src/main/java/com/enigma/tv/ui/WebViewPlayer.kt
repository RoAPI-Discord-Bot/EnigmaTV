package com.enigma.tv.ui

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.MovieAccent
import com.enigma.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay

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
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    var blockedNotice by remember { mutableStateOf<String?>(null) }
    val guard = remember(liveTv) {
        WebViewNavigationGuard("").apply {
            onBlocked = { blocked ->
                blockedNotice = when {
                    blocked == "popup_window" -> "Blocked popup"
                    else -> "Blocked redirect"
                }
            }
            onPageLoading = onLoadingChange
        }
    }

    LaunchedEffect(url) {
        onLoadingChange(true)
    }

    LaunchedEffect(blockedNotice) {
        if (blockedNotice != null) {
            delay(2500)
            blockedNotice = null
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
                showNextSource = true,
                onNextSource = onNextSource,
                tvControls = tvControls
            )
        }
        WebViewStreamBody(
            url = url,
            liveTv = liveTv,
            guard = guard,
            streamLoading = streamLoading,
            blockedNotice = blockedNotice
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
    streamLoading: Boolean,
    blockedNotice: String?
) {
    blockedNotice?.let { notice ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MovieAccent.copy(alpha = 0.2f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = MovieAccent)
            Text(notice, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
        }
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
                    guard.configureWebView(this)
                    if (liveTv) {
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }
                    setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv)
                    loadUrl(url)
                }
            },
            update = { view ->
                val last = view.getTag(TAG_STREAM_URL) as? String
                if (last != url) {
                    view.setTag(TAG_STREAM_URL, url)
                    guard.resetForUrl(url, liveTv = liveTv)
                    view.loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (streamLoading) {
            EnigmaLoadingRing(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgDark.copy(alpha = 0.85f)),
                message = "LOADING STREAM",
                logoSize = 72.dp,
                ringSize = 110.dp
            )
        }
    }
}
