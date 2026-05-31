package com.enigma.tv.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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

    // ── Keep screen on while the player is visible ────────────────────────────
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val scope = rememberCoroutineScope()
    var playToken by remember { mutableIntStateOf(0) }
    var retryCount by remember(playUrl) { mutableIntStateOf(0) }
    var stripHeaders by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasTextTracks by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(true) } // ON by default when tracks exist
    var hasReachedReady by remember(playUrl, playToken) { mutableStateOf(false) }

    val sidecarSubtitle = remember(playUrl, playToken, resolved.subtitleUrl) {
        resolved.subtitleUrl?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
    }

    DisposableEffect(useExternalChrome) {
        if (useExternalChrome) syncChrome(true)
        onDispose { }
    }

    val player = remember(playUrl, playToken) {
        // Adaptive bandwidth meter — picks video quality the connection can actually handle
        val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                     */ 5_000,   // 5s minimum — resume fast after stalls
                /* maxBufferMs                     */ 30_000,  // 30s max look-ahead
                /* bufferForPlaybackMs             */ 1_500,   // start after 1.5s
                /* bufferForPlaybackAfterRebufferMs*/ 1_000    // resume after just 1s after any stall
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                // Adaptive quality: allow dropping to lower bitrates when bandwidth is low
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setForceHighestSupportedBitrate(false)
        )

        ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build().apply {
                playWhenReady = true
                volume = 1f
            }
    }

    val effectiveHeaders = if (stripHeaders) emptyMap() else playbackHeaders

    DisposableEffect(playUrl, playToken, effectiveHeaders, sidecarSubtitle) {
        errorMessage = null
        hasReachedReady = false
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
            .setConnectTimeoutMs(8_000)   // fail fast on slow CDN connect
            .setReadTimeoutMs(8_000)      // don't wait 30s for a stalled segment — fail and retry
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
                // Provider tag is set by EnigmaMediaPlayer based on actual URL content.
                // embed-hls = captured HLS/m3u8, embed-mp4 = captured direct mp4
                val mediaSource: MediaSource = when {
                    resolved.provider == "embed-hls" ||
                    playUrl.contains(".m3u8", ignoreCase = true) ||
                    playUrl.contains("playlist", ignoreCase = true) -> {
                        android.util.Log.d("EnigmaPlayer", "Using HlsMediaSource for: $playUrl")
                        HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                    }
                    resolved.provider == "embed-mp4" ||
                    playUrl.contains(".mp4", ignoreCase = true) -> {
                        android.util.Log.d("EnigmaPlayer", "Using ProgressiveMediaSource for: $playUrl")
                        androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(mediaItem)
                    }
                    else -> {
                        // Auto-detect from Content-Type header
                        android.util.Log.d("EnigmaPlayer", "Using DefaultMediaSourceFactory for: $playUrl")
                        DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
                    }
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
                    }
                    Player.STATE_BUFFERING -> {
                        // Never show loading spinner during mid-play rebuffer — just
                        // buffer silently. Only show spinner on initial load.
                        if (!hasReachedReady) onLoadingChange(true)
                    }
                    Player.STATE_ENDED -> {
                        onLoadingChange(false)
                        if (!isLiveBroadcast) onPlaybackEnded?.invoke()
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                val hasText = tracks.groups.any { group ->
                    group.type == C.TRACK_TYPE_TEXT && group.length > 0
                }
                hasTextTracks = hasText
                // Auto-enable the first available text track so captions work immediately
                if (hasText && captionsEnabled) {
                    val selector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
                    selector?.setParameters(
                        selector.buildUponParameters()
                            .setPreferredTextLanguage("en")
                            .setSelectUndeterminedTextLanguage(true)
                            .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, false)
                    )
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && hasReachedReady && !isLiveBroadcast) {
                    onPlaybackPositionMs?.invoke(player.currentPosition.coerceAtLeast(0L))
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Classify the error: HTTP 403/401 = truly blocked, anything else = network issue
                val cause = error.cause
                val isHttpError = cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                val httpCode = if (isHttpError)
                    (cause as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException).responseCode
                else -1

                val isRealBlock = isHttpError && (httpCode == 403 || httpCode == 401)

                if (!isRealBlock && retryCount < 1) {
                    // Network/connection error on Fire TV — retry once immediately
                    retryCount++
                    playToken++
                    return
                }

                onLoadingChange(false)
                errorMessage = when {
                    isHttpError && (httpCode == 403 || httpCode == 401) ->
                        "Stream blocked (HTTP $httpCode) — try next server"
                    isHttpError && httpCode in 400..499 ->
                        "Stream unavailable (HTTP $httpCode) — try next server"
                    isHttpError && httpCode in 500..599 ->
                        "Server error — try next server"
                    else ->
                        "Connection failed — try next server"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            if (!isLiveBroadcast) {
                // Save final position for continue-watching on close
                onPlaybackPositionMs?.invoke(player.currentPosition.coerceAtLeast(0L))
            }
            loadTimeoutJob.cancel()
            player.removeListener(listener)
            player.release()
        }
    }

    // Show CC button when the stream has text tracks (including live) or a sidecar subtitle
    val showCcButton = hasTextTracks || sidecarSubtitle != null

    // Sync captionsEnabled → text renderer whenever it changes
    LaunchedEffect(captionsEnabled, hasTextTracks) {
        if (hasTextTracks) {
            val selector = player.trackSelector as? androidx.media3.exoplayer.trackselection.DefaultTrackSelector
            selector?.setParameters(
                selector.buildUponParameters()
                    .setRendererDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
                    .setPreferredTextLanguage(if (captionsEnabled) "en" else "")
                    .setSelectUndeterminedTextLanguage(captionsEnabled)
            )
        }
    }

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

                            // ── Hold-to-accelerate on rewind / fast-forward ───────────────
                            // Tap = 5 s, hold = jumps 30 s every 500 ms while held
                            fun attachHoldSeek(btnId: Int, direction: Int) {
                                val btn = findViewById<android.view.View>(btnId) ?: return
                                var holdJob: kotlinx.coroutines.Job? = null
                                btn.setOnClickListener {
                                    val pos = player.currentPosition
                                    val target = (pos + direction * 5_000L).coerceIn(0L, player.duration.coerceAtLeast(0L))
                                    player.seekTo(target)
                                }
                                btn.setOnLongClickListener {
                                    holdJob = scope.launch {
                                        while (isActive) {
                                            val pos = player.currentPosition
                                            val target = (pos + direction * 30_000L).coerceIn(0L, player.duration.coerceAtLeast(0L))
                                            player.seekTo(target)
                                            delay(500)
                                        }
                                    }
                                    true
                                }
                                btn.setOnTouchListener { _, event ->
                                    if (event.action == android.view.MotionEvent.ACTION_UP ||
                                        event.action == android.view.MotionEvent.ACTION_CANCEL) {
                                        holdJob?.cancel()
                                        holdJob = null
                                    }
                                    false
                                }
                            }
                            // Use Media3's resource IDs for rew/ffwd so the layout IDs match
                            attachHoldSeek(androidx.media3.ui.R.id.exo_rew, -1)
                            attachHoldSeek(androidx.media3.ui.R.id.exo_ffwd, +1)

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
                        // Don't use setShowSubtitleButton — it opens a dialog showing "Unknown"
                        // Instead we wire our own XML CC button manually
                        view.setShowSubtitleButton(false)
                        val ccBtn = view.findViewById<android.view.View>(androidx.media3.ui.R.id.exo_subtitle)
                        if (showCcButton) {
                            ccBtn?.visibility = View.VISIBLE
                            ccBtn?.setOnClickListener {
                                captionsEnabled = !captionsEnabled
                            }
                        } else {
                            ccBtn?.visibility = View.GONE
                        }
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
