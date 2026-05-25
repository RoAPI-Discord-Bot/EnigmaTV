package com.enigma.tv.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as MediaUiR
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary

@OptIn(UnstableApi::class)
@Composable
fun ExoLivePlayer(
    visible: Boolean,
    title: String,
    streamUrl: String = "",
    stream: ResolvedStream? = null,
    sourceLabel: String,
    logoUrl: String? = null,
    accent: Color = EnigmaPurple,
    streamLoading: Boolean,
    onClose: () -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    isLiveBroadcast: Boolean = false,
    showNextSource: Boolean = false,
    onNextSource: (() -> Unit)? = null,
    tvControls: TvPlayerControls? = null,
    useExternalChrome: Boolean = false,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackProgress: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    BackHandler { onClose() }

    val resolved = stream ?: streamUrl.takeIf { it.isNotBlank() }?.let {
        ResolvedStream(url = it, referer = "", provider = "direct")
    }

    if (resolved == null || resolved.url.isBlank()) {
        Box(Modifier.fillMaxSize().background(BgDark)) {
            EnigmaLoadingRing(
                modifier = Modifier.fillMaxSize(),
                message = "LOADING STREAM",
                fullscreen = true
            )
        }
        return
    }

    val playUrl = resolved.url
    val playbackHeaders = resolved.playbackHeaders()
    val syncChrome = LocalPlayerChromeSync.current

    LaunchedEffect(useExternalChrome) {
        if (useExternalChrome) syncChrome(true)
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playToken by remember { mutableIntStateOf(0) }
    var stripHeaders by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var subtitleUrl by remember(playUrl, playToken) { mutableStateOf<String?>(null) }
    var hasTextTracks by remember { mutableStateOf(false) }

    LaunchedEffect(playUrl, playToken, resolved.subtitleUrl) {
        val fromResolved = resolved.subtitleUrl?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
        subtitleUrl = fromResolved ?: withContext(Dispatchers.IO) {
            StreamResolver.resolveSubtitlesForStream(playUrl, resolved.referer.ifBlank { playUrl })
        }?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
    }

    val player = remember(playUrl, playToken) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            volume = 1f
        }
    }

    val effectiveHeaders = if (stripHeaders) emptyMap() else playbackHeaders

    DisposableEffect(playUrl, playToken, effectiveHeaders, subtitleUrl) {
        errorMessage = null
        onLoadingChange(true)
        var prepared = false
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(resolved.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(18_000)
            .setReadTimeoutMs(25_000)
            .apply {
                if (effectiveHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(effectiveHeaders)
                }
            }
        try {
            val uri = android.net.Uri.parse(playUrl)
            if (!uri.scheme.isNullOrBlank()) {
                val itemBuilder = MediaItem.Builder().setUri(uri)
                if (isLiveBroadcast) {
                    itemBuilder.setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(3_000)
                            .build()
                    )
                }
                subtitleUrl?.let { sub ->
                    val subUri = android.net.Uri.parse(sub)
                    val mime = when {
                        sub.contains(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
                        else -> MimeTypes.TEXT_VTT
                    }
                    itemBuilder.setSubtitleConfigurations(
                        listOf(
                            MediaItem.SubtitleConfiguration.Builder(subUri)
                                .setMimeType(mime)
                                .setLanguage("en")
                                .setLabel("English")
                                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                .build()
                        )
                    )
                }
                val mediaItem = itemBuilder.build()
                val mediaSource: MediaSource = if (playUrl.contains(".m3u8", ignoreCase = true)) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }
                player.setMediaSource(mediaSource)
                player.prepare()
                prepared = true
            }
        } catch (_: Exception) {
            prepared = false
        }
        if (!prepared) {
            onLoadingChange(false)
            errorMessage = "Stream unavailable — try another source"
            return@DisposableEffect onDispose { }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        onLoadingChange(false)
                        errorMessage = null
                    }
                    Player.STATE_BUFFERING -> onLoadingChange(true)
                    Player.STATE_ENDED -> {
                        onLoadingChange(false)
                        if (!isLiveBroadcast) onPlaybackEnded?.invoke()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                hasTextTracks = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_TEXT && group.length > 0
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onLoadingChange(false)
                if (!stripHeaders && playbackHeaders.isNotEmpty()) {
                    stripHeaders = true
                    playToken++
                } else {
                    errorMessage = "Stream blocked — try next server"
                }
            }
        }
        player.addListener(listener)
        val progressJob = if (!isLiveBroadcast && onPlaybackProgress != null) {
            scope.launch {
                while (isActive) {
                    delay(12_000)
                    val dur = player.duration
                    if (dur > 0) {
                        val pct = ((player.currentPosition * 100) / dur).toInt().coerceIn(0, 100)
                        onPlaybackProgress(pct)
                    }
                }
            }
        } else null
        onDispose {
            progressJob?.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    val showCcButton = !isLiveBroadcast && hasTextTracks

    val videoContent: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                            useController = true
                            controllerShowTimeoutMs = 5000
                            controllerHideOnTouch = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            if (useExternalChrome) {
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility ->
                                        syncChrome(visibility == View.VISIBLE)
                                    }
                                )
                            }
                            if (isLiveBroadcast) {
                                post { hideVodTimeline(this) }
                            }
                        }
                    },
                    update = { view ->
                        view.player = player
                        view.setShowSubtitleButton(showCcButton)
                        if (useExternalChrome) {
                            view.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    syncChrome(visibility == View.VISIBLE)
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (streamLoading) {
                    EnigmaLoadingRing(
                        modifier = Modifier.fillMaxSize().background(BgDark.copy(alpha = 0.85f)),
                        message = "LOADING STREAM",
                        logoSize = 72.dp,
                        ringSize = 100.dp
                    )
                }
                errorMessage?.let { msg ->
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(BgDark.copy(alpha = 0.9f)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                        Text(msg, color = TextPrimary, fontSize = 14.sp)
                        Button(
                            onClick = { playToken++ },
                            colors = ButtonDefaults.buttonColors(containerColor = EnigmaPurple),
                            modifier = Modifier.padding(top = 16.dp)
                        ) { Text("Retry") }
                    }
                }
            }
    }

    Box(modifier.background(BgDark)) {
        if (useExternalChrome) {
            videoContent()
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerChrome(
                    title = title,
                    subtitle = sourceLabel,
                    posterUrl = logoUrl,
                    accent = accent,
                    onClose = onClose,
                    showBack = true,
                    onBack = onClose,
                    onRetry = { playToken++ },
                    showNextSource = showNextSource,
                    onNextSource = onNextSource,
                    showEpisodesButton = tvControls != null,
                    isLive = isLiveBroadcast
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    videoContent()
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun hideVodTimeline(playerView: PlayerView) {
    listOf(
        MediaUiR.id.exo_progress,
        MediaUiR.id.exo_duration,
        MediaUiR.id.exo_position
    ).forEach { id ->
        playerView.findViewById<View>(id)?.visibility = View.GONE
    }
}
