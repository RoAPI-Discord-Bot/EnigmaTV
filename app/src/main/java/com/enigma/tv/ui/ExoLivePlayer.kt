package com.enigma.tv.ui

import android.view.View
import android.view.ViewGroup
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
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
    onShowEpisodes: (() -> Unit)? = null,
    tvControls: TvPlayerControls? = null,
    useExternalChrome: Boolean = false,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackPositionMs: ((Long) -> Unit)? = null,
    startPositionMs: Long = 0L,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return


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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playToken by remember { mutableIntStateOf(0) }
    var stripHeaders by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasTextTracks by remember { mutableStateOf(false) }
    var hasReachedReady by remember(playUrl, playToken) { mutableStateOf(false) }
    var didSeekToResume by remember(playUrl, playToken, startPositionMs) { mutableStateOf(false) }

    val sidecarSubtitle = remember(playUrl, playToken, resolved.subtitleUrl) {
        resolved.subtitleUrl?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
    }

    DisposableEffect(useExternalChrome) {
        if (useExternalChrome) syncChrome(true)
        onDispose { }
    }

    val player = remember(playUrl, playToken) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 32_000,
                /* maxBufferMs */ 50_000,
                /* bufferForPlaybackMs */ 2_500,
                /* bufferForPlaybackAfterRebufferMs */ 5_000
            )
            .build()
        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .build().apply {
                playWhenReady = true
                volume = 1f
            }
    }

    val effectiveHeaders = if (stripHeaders) emptyMap() else playbackHeaders

    LaunchedEffect(startPositionMs, hasReachedReady, playUrl, playToken) {
        if (!isLiveBroadcast && hasReachedReady && !didSeekToResume && startPositionMs >= 3_000L) {
            player.seekTo(startPositionMs)
            didSeekToResume = true
        }
    }

    DisposableEffect(playUrl, playToken, effectiveHeaders, sidecarSubtitle) {
        errorMessage = null
        hasReachedReady = false
        didSeekToResume = false
        onLoadingChange(true)
        var prepared = false
        val loadTimeoutJob = scope.launch {
            delay(35_000)
            if (player.playbackState != Player.STATE_READY) {
                onLoadingChange(false)
                errorMessage = "Stream timed out — try next server"
            }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(resolved.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(5_000)
            .setReadTimeoutMs(8_000)
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
                sidecarSubtitle?.let { sub ->
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
                // embed-capture streams from VidLink/VidSrc are ALWAYS HLS (.m3u8)
                // even when the URL doesn't have .m3u8 in the path (CDN token URLs).
                // Forcing ProgressiveMediaSource on HLS causes 502s and 3-5s stutters.
                val isHls = resolved.provider == "embed-capture" ||
                    playUrl.contains(".m3u8", ignoreCase = true) ||
                    playUrl.contains("playlist", ignoreCase = true)
                val mediaSource: MediaSource = if (isHls) {
                    HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                } else if (playUrl.contains(".mp4", ignoreCase = true)) {
                    androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                } else {
                    // Let ExoPlayer auto-detect: it sniffs the content-type header
                    DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
                }
                val startMs = if (isLiveBroadcast) C.TIME_UNSET else startPositionMs.coerceAtLeast(0L)
                player.setMediaSource(mediaSource, startMs)
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
                        hasReachedReady = true
                        onLoadingChange(false)
                        errorMessage = null
                        if (!isLiveBroadcast && !didSeekToResume && startPositionMs > 0L) {
                            player.seekTo(startPositionMs)
                            didSeekToResume = true
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        if (!hasReachedReady) onLoadingChange(true)
                    }
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

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && hasReachedReady && !isLiveBroadcast) {
                    onPlaybackPositionMs?.invoke(player.currentPosition.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                onLoadingChange(false)
                errorMessage = "Stream blocked — try next server"
            }
        }
        player.addListener(listener)
        val progressJob = if (!isLiveBroadcast && onPlaybackPositionMs != null) {
            scope.launch {
                while (isActive) {
                    delay(5_000)
                    if (player.isPlaying || player.currentPosition > 0L) {
                        onPlaybackPositionMs(player.currentPosition.coerceAtLeast(0L))
                    }
                }
            }
        } else null
        onDispose {
            if (!isLiveBroadcast) {
                onPlaybackPositionMs?.invoke(player.currentPosition.coerceAtLeast(0L))
            }
            loadTimeoutJob.cancel()
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
                        val view = android.view.LayoutInflater.from(ctx).inflate(com.enigma.tv.R.layout.enigma_player_view, null) as PlayerView
                        view.apply {
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
                            
                            findViewById<android.widget.TextView>(com.enigma.tv.R.id.tv_enigma_title)?.text = title
                            findViewById<android.widget.TextView>(com.enigma.tv.R.id.tv_enigma_subtitle)?.text = sourceLabel
                            
                            findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_close)?.setOnClickListener { onClose() }
                            
                            val nextBtn = findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_next)
                            if (showNextSource) {
                                nextBtn?.visibility = View.VISIBLE
                                nextBtn?.setOnClickListener { onNextSource?.invoke() }
                            } else {
                                nextBtn?.visibility = View.GONE
                            }
                            
                            val epBtn = findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_episodes)
                            if (tvControls != null) {
                                epBtn?.visibility = View.VISIBLE
                                epBtn?.setOnClickListener { onShowEpisodes?.invoke() }
                            } else {
                                epBtn?.visibility = View.GONE
                            }
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
