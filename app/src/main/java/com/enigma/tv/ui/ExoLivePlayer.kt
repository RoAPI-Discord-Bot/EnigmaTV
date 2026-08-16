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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import com.enigma.tv.ui.theme.EnigmaPink
import androidx.compose.material3.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.onSizeChanged
import android.content.ContentResolver
import android.provider.Settings
import android.media.AudioManager
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.R as MediaUiR
import com.enigma.tv.data.ResolvedStream
import com.enigma.tv.data.StreamResolver
import com.enigma.tv.ui.theme.BgDark
import com.enigma.tv.ui.theme.EnigmaPurple
import com.enigma.tv.ui.theme.TextPrimary
import okio.buffer

enum class CaptionStatus {
    SEARCHING,
    UNAVAILABLE,
    AVAILABLE
}

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
    onLoadingMessageChange: ((String) -> Unit)? = null,
    isLiveBroadcast: Boolean = false,
    showNextSource: Boolean = false,
    onNextSource: (() -> Unit)? = null,
    onShowEpisodes: (() -> Unit)? = null,
    tvControls: TvPlayerControls? = null,
    useExternalChrome: Boolean = false,
    onPlaybackEnded: (() -> Unit)? = null,
    onPlaybackReady: (() -> Unit)? = null,
    onPlaybackPositionMs: ((Long) -> Unit)? = null,
    onPlaybackDurationMs: ((Long) -> Unit)? = null,
    bingeNextLabel: String? = null,
    onBingeNext: (() -> Unit)? = null,
    startPositionMs: Long = 0L,
    actionDispatcher: PlayerActionDispatcher? = null,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    if (!visible) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var captionsEnabled by remember { mutableStateOf(true) }
    var hasTextTracks by remember { mutableStateOf(false) }
    // Real-time fetch progress (updated from network interceptor)
    var fetchedBytes by remember { mutableLongStateOf(0L) }
    var speedBps by remember { mutableLongStateOf(0L) }
    var elapsedSecs by remember { mutableLongStateOf(0L) }
    
    // Thumbnail scrubbing state
    var thumbnailEntries by remember { mutableStateOf<List<com.enigma.tv.data.ThumbnailEntry>>(emptyList()) }
    var scrubPositionMs by remember { mutableStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(stream?.thumbnailUrl) {
        val vttUrl = stream?.thumbnailUrl
        if (!vttUrl.isNullOrBlank()) {
            val entries = com.enigma.tv.data.VttThumbnailParser.parse(vttUrl)
            thumbnailEntries = entries
        } else {
            thumbnailEntries = emptyList()
        }
    }

    val resolved = stream ?: streamUrl.takeIf { it.isNotBlank() }?.let {
        ResolvedStream(url = it, referer = "", provider = "direct")
    }

    if (resolved == null || resolved.url.isBlank()) {
        Box(Modifier.fillMaxSize().background(BgDark)) {
            EnigmaLoadingRing(
                modifier = Modifier.fillMaxSize(),
                message = "FINDING STREAM",
                fullscreen = true
            )
        }
        return
    }

    val playUrl = resolved.url
    val playbackHeaders = resolved.playbackHeaders()
    val syncChrome = LocalPlayerChromeSync.current

    var fetchLabelText by remember { mutableStateOf("LOADING STREAM") }

    // ── Keep screen on and hide system bars while the player is visible ──────
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            val activity = context as? Activity
            val window = activity?.window
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    val scope = rememberCoroutineScope()
    var playToken by remember { mutableIntStateOf(0) }
    var retryCount by remember(playUrl) { mutableIntStateOf(0) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var stripHeaders by remember(playUrl) { mutableStateOf(true) }
    var hasReachedReady by remember(playUrl, playToken) { mutableStateOf(false) }
    var bingeCountdown by remember(playUrl, playToken) { mutableStateOf<Int?>(null) }
    
    // Gesture state
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gestureIcon by remember { mutableStateOf("") }
    var playerWidthPx by remember { mutableIntStateOf(1) }
    var playerHeightPx by remember { mutableIntStateOf(1) }
    val audioManager = remember { context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }

    // Watch Party integration
    val partyVm: WatchPartyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val partyState by partyVm.state.collectAsState()

    var sidecarSubtitle by remember { mutableStateOf<String?>(null) }
    var subtitleResolved by remember { mutableStateOf(false) }
    var captionStatus by remember(playUrl) {
        mutableStateOf(
            if (!resolved.subtitleUrl.isNullOrBlank() && StreamResolver.isValidSubtitleUrl(resolved.subtitleUrl))
                CaptionStatus.AVAILABLE
            else
                CaptionStatus.SEARCHING
        )
    }

    LaunchedEffect(playUrl, playToken, resolved.subtitleUrl) {
        sidecarSubtitle = null
        val subUrl = resolved.subtitleUrl?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
        if (subUrl != null) {
            sidecarSubtitle = subUrl
            captionStatus = CaptionStatus.AVAILABLE
            subtitleResolved = true
            return@LaunchedEffect
        }
        
        // Start playback immediately — do not wait for captions
        captionStatus = CaptionStatus.SEARCHING
        subtitleResolved = true

        val foundSub = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            StreamResolver.resolveSubtitlesForStream(playUrl, resolved.referer.ifBlank { playUrl })
        }
        if (foundSub != null && StreamResolver.isValidSubtitleUrl(foundSub)) {
            sidecarSubtitle = foundSub
            captionStatus = CaptionStatus.AVAILABLE
            android.util.Log.d("EnigmaCapture", "Background subtitle found and set: $foundSub")
        } else {
            captionStatus = CaptionStatus.UNAVAILABLE
        }
    }

    val filterStore = remember { com.enigma.tv.data.FilterPreferencesStore(context) }
    val filterSettings by filterStore.settingsFlow.collectAsState(initial = com.enigma.tv.data.FilterSettings())

    var bleepIntervals by remember { mutableStateOf<List<com.enigma.tv.data.BleepInterval>>(emptyList()) }
    var sceneIntervals by remember { mutableStateOf<List<com.enigma.tv.data.ModerationSceneInterval>>(emptyList()) }
    var isSceneBlurred by remember { mutableStateOf(false) }

    var showCaptionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(playUrl, resolved.referer) {
        val tmdbId = resolved.referer.substringAfter("tmdb=", "").toIntOrNull()
        if (tmdbId != null) {
            val scenes = com.enigma.tv.data.SceneModerationRepository.getSceneIntervals(tmdbId, com.enigma.tv.data.ContentType.MOVIE)
            sceneIntervals = scenes
        }
    }

    LaunchedEffect(sidecarSubtitle, filterSettings.captionOffsetMs) {
        val sub = sidecarSubtitle ?: return@LaunchedEffect
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val req = okhttp3.Request.Builder().url(sub).build()
                val resp = okhttp3.OkHttpClient().newCall(req).execute()
                val text = resp.body?.string() ?: ""
                bleepIntervals = com.enigma.tv.data.ProfanityBleepEngine.parseSubtitleToBleepIntervals(text, filterSettings.captionOffsetMs)
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(useExternalChrome) {
        if (useExternalChrome) syncChrome(true)
        onDispose { }
    }

    val player = remember(playUrl, playToken) {
        // V3: Adaptive bandwidth meter + aggressive buffer for best quality
        val bandwidthMeter = androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context).build()
        val loadControl = DefaultLoadControl.Builder()
            // Reduced min buffer (15sâ†’ready) so playback starts much faster.
            // Max is still 120s so it keeps buffering in the background.
            .setBufferDurationsMs(15_000, 120_000, 1_500, 3_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true)
                .setPreferredTextRoleFlags(C.ROLE_FLAG_CAPTION or C.ROLE_FLAG_SUBTITLE)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setForceHighestSupportedBitrate(true)
        )

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildTextRenderers(
                context: android.content.Context,
                output: androidx.media3.exoplayer.text.TextOutput,
                outputLooper: android.os.Looper,
                extensionRendererMode: Int,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                val renderer = androidx.media3.exoplayer.text.TextRenderer(output, outputLooper)
                renderer.experimentalSetLegacyDecodingEnabled(true)
                out.add(renderer)
            }
        }

        ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5_000)
            .setSeekForwardIncrementMs(5_000)
            .build().apply {
                playWhenReady = true
                volume = 1f
            }
    }

      // Real-time fetch progress counter (AtomicLong updated from OkHttp network interceptor)
      val bytesRef = remember(playUrl) { java.util.concurrent.atomic.AtomicLong(0L) }
      
      // Poll the bytes counter while the player is still loading so the UI can show progress
      LaunchedEffect(streamLoading, playUrl, playToken) {
          if (!streamLoading) return@LaunchedEffect
          val startTime = System.currentTimeMillis()
          while (true) {
              fetchedBytes = bytesRef.get()
              val elapsedMs = System.currentTimeMillis() - startTime
              elapsedSecs = (elapsedMs / 1000L).coerceAtLeast(1L)
              speedBps = fetchedBytes / elapsedSecs
              
              val speedLabel = when {
                  speedBps >= 1_048_576L -> "%.1f MB/s".format(speedBps / 1_048_576.0)
                  speedBps >= 1024L      -> "${speedBps / 1024} KB/s"
                  else                   -> "$speedBps B/s"
              }
              
              val bufferedSecs = player.bufferedPosition / 1000L
              fetchLabelText = when {
                  !isLiveBroadcast && bufferedSecs > 0L -> "FETCHED ${bufferedSecs} OF 15s ($speedLabel)"
                  fetchedBytes >= 1_048_576L -> "FETCHING STREAM  %.1f MB ($speedLabel) • ${elapsedSecs}s".format(fetchedBytes / 1_048_576.0)
                  fetchedBytes >= 1024L      -> "FETCHING STREAM  ${fetchedBytes / 1024} KB ($speedLabel) • ${elapsedSecs}s"
                  fetchedBytes > 0L          -> "FETCHING STREAM  $fetchedBytes B ($speedLabel) • ${elapsedSecs}s"
                  else                       -> "LOADING STREAM"
              }
              onLoadingMessageChange?.invoke(fetchLabelText)
              
              kotlinx.coroutines.delay(250)
          }
      }

    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.pause()
                Lifecycle.Event.ON_RESUME -> player.play()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingSeekMs by remember { mutableStateOf<Long?>(null) }
    var seekJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var wasPlayingBeforeSeek by remember { mutableStateOf(false) }

    val debouncedPlayer = remember(player) {
        object : androidx.media3.common.ForwardingPlayer(player) {
            override fun seekTo(positionMs: Long) {
                pendingSeekMs = positionMs
                if (seekJob == null) {
                    wasPlayingBeforeSeek = playWhenReady
                    playWhenReady = false
                }
                seekJob?.cancel()
                seekJob = scope.launch {
                    kotlinx.coroutines.delay(600)
                    super.seekTo(pendingSeekMs!!)
                    playWhenReady = wasPlayingBeforeSeek
                    pendingSeekMs = null
                    seekJob = null
                }
            }

            override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
                pendingSeekMs = positionMs
                if (seekJob == null) {
                    wasPlayingBeforeSeek = playWhenReady
                    playWhenReady = false
                }
                seekJob?.cancel()
                seekJob = scope.launch {
                    kotlinx.coroutines.delay(600)
                    super.seekTo(mediaItemIndex, pendingSeekMs!!)
                    playWhenReady = wasPlayingBeforeSeek
                    pendingSeekMs = null
                    seekJob = null
                }
            }

            override fun getCurrentPosition(): Long {
                return pendingSeekMs ?: super.getCurrentPosition()
            }
        }
    }

    DisposableEffect(actionDispatcher, debouncedPlayer) {
        if (actionDispatcher != null) {
            actionDispatcher.onTogglePlay = {
                if (debouncedPlayer.isPlaying) debouncedPlayer.pause() else debouncedPlayer.play()
            }
            actionDispatcher.onSeekForward = {
                debouncedPlayer.seekTo(debouncedPlayer.currentPosition + 10_000)
            }
            actionDispatcher.onSeekBackward = {
                debouncedPlayer.seekTo(debouncedPlayer.currentPosition - 10_000)
            }
            actionDispatcher.onRestart = {
                debouncedPlayer.seekTo(0)
                debouncedPlayer.play()
            }
        }
        onDispose {
            actionDispatcher?.onTogglePlay = null
            actionDispatcher?.onSeekForward = null
            actionDispatcher?.onSeekBackward = null
            actionDispatcher?.onRestart = null
        }
    }

    val effectiveHeaders = if (stripHeaders) emptyMap() else playbackHeaders

    DisposableEffect(playUrl, playToken, effectiveHeaders, subtitleResolved, sidecarSubtitle) {
        if (!subtitleResolved) return@DisposableEffect onDispose { }
        errorMessage = null
        hasReachedReady = false
        onLoadingChange(true)
        var prepared = false
        val loadTimeoutJob = scope.launch {
            delay(35_000)
            if (player.playbackState != Player.STATE_READY) {
                onLoadingChange(false)
                errorMessage = "Stream timed out â€” try next server"
            }
        }
        // Use OkHttpDataSource instead of DefaultHttpDataSource. DefaultHttpDataSource drops
        // headers upon cross-origin redirects (which HLS segments often do, going from CDN origin
        // to node IP). OkHttpDataSource handles cookies and redirects much better.
        //
        // We add a CountingInterceptor so we can show real-time fetch progress ("Fetching... X MB")
        // to the user, letting them distinguish a slow CDN from an app problem.
        // Reset progress counter for fresh load (bytesRef is declared at composable scope)
        bytesRef.set(0L)
        val progressInterceptor = okhttp3.Interceptor { chain ->
            val response = chain.proceed(chain.request())
            val body = response.body
            if (body != null) {
                val countingBody = object : okhttp3.ResponseBody() {
                    override fun contentType() = body.contentType()
                    override fun contentLength() = body.contentLength()
                    override fun source(): okio.BufferedSource {
                        val rawSource = body.source()
                        return object : okio.ForwardingSource(rawSource) {
                            override fun read(sink: okio.Buffer, byteCount: Long): Long {
                                val bytesRead = super.read(sink, byteCount)
                                if (bytesRead > 0) bytesRef.addAndGet(bytesRead)
                                return bytesRead
                            }
                        }.buffer()
                    }
                }
                response.newBuilder().body(countingBody).build()
            } else response
        }
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .addNetworkInterceptor(progressInterceptor)
            .build()
        
        // (progress reset already done above, before interceptor definition)
        
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(resolved.userAgent)
            .apply {
                if (effectiveHeaders.isNotEmpty()) {
                    setDefaultRequestProperties(effectiveHeaders)
                }
            }
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, dataSourceFactory)
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
                val mainMimeType = when {
                    resolved.provider == "embed-hls" ||
                    playUrl.contains(".m3u8", ignoreCase = true) ||
                    playUrl.contains("playlist", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                    
                    resolved.provider == "embed-mp4" ||
                    playUrl.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
                    
                    else -> null
                }
                
                if (mainMimeType != null) {
                    itemBuilder.setMimeType(mainMimeType)
                }
                
                val primaryMediaItem = itemBuilder.build()
                
                android.util.Log.d("EnigmaPlayer", "Using DefaultMediaSourceFactory for: $playUrl")
                var mediaSource: MediaSource = DefaultMediaSourceFactory(defaultDataSourceFactory).createMediaSource(primaryMediaItem)
                
                val subUri = sidecarSubtitle?.let { android.net.Uri.parse(it) }
                if (subUri != null) {
                    val mime = when {
                        sidecarSubtitle?.substringBefore("?")?.endsWith(".vtt", ignoreCase = true) == true -> MimeTypes.TEXT_VTT
                        sidecarSubtitle?.substringBefore("?")?.endsWith(".srt", ignoreCase = true) == true -> MimeTypes.APPLICATION_SUBRIP
                        resolved.subtitleUrl?.contains(".vtt", ignoreCase = true) == true -> MimeTypes.TEXT_VTT
                        resolved.subtitleUrl?.contains(".srt", ignoreCase = true) == true -> MimeTypes.APPLICATION_SUBRIP
                        else -> MimeTypes.TEXT_VTT
                    }
                    val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                        .setMimeType(mime)
                        .setLanguage("en")
                        .setLabel("English")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                        .build()
                        
                    val subSource = SingleSampleMediaSource.Factory(defaultDataSourceFactory)
                        .setTreatLoadErrorsAsEndOfStream(true) // Ignore 403 errors to prevent crashing the video
                        .createMediaSource(subtitleConfig, C.TIME_UNSET)
                        
                    mediaSource = MergingMediaSource(mediaSource, subSource)
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
            errorMessage = "Stream unavailable â€” try another source"
            return@DisposableEffect onDispose { }
        }
        var initialQualityForced = false

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        hasReachedReady = true
                        onLoadingChange(false)
                        errorMessage = null

                        val durationMs = player.duration
                        if (!isLiveBroadcast && durationMs in 100L..180_000L && showNextSource && onNextSource != null) {
                            android.util.Log.w("ExoLivePlayer", "Stream duration ($durationMs ms) too short for movie/show. Auto-switching server...")
                            player.pause()
                            onNextSource()
                            return
                        }

                        onPlaybackReady?.invoke()
                        
                        if (!initialQualityForced) {
                            initialQualityForced = true
                            // Force the highest quality video track available automatically
                            val videoGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                            if (videoGroups.isNotEmpty()) {
                                var bestGroup: androidx.media3.common.Tracks.Group? = null
                                var bestTrackIndex = -1
                                var maxPixels = -1
                                for (group in videoGroups) {
                                    for (i in 0 until group.length) {
                                        if (group.isTrackSupported(i)) {
                                            val format = group.getTrackFormat(i)
                                            val pixels = format.width * format.height
                                            if (pixels > maxPixels) {
                                                maxPixels = pixels
                                                bestGroup = group
                                                bestTrackIndex = i
                                            }
                                        }
                                    }
                                }
                                if (bestGroup != null && bestTrackIndex >= 0) {
                                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                        .setOverrideForType(
                                            androidx.media3.common.TrackSelectionOverride(bestGroup.mediaTrackGroup, listOf(bestTrackIndex))
                                        ).build()
                                }
                            }
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        // Never show loading spinner during mid-play rebuffer â€” just
                        // buffer silently. Only show spinner on initial load.
                        if (!hasReachedReady) onLoadingChange(true)
                    }
                    Player.STATE_ENDED -> {
                        onLoadingChange(false)
                        if (!isLiveBroadcast) {
                            if (tvControls != null && onPlaybackEnded != null) {
                                bingeCountdown = 10
                            } else {
                                onPlaybackEnded?.invoke()
                            }
                        }
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
                // Report duration once we know it
                val dur = player.duration
                if (dur > 0L) onPlaybackDurationMs?.invoke(dur)
            }

            override fun onPlayerError(error: PlaybackException) {
                // Classify the error: HTTP 403/401 = truly blocked, anything else = network issue
                val cause = error.cause
                val isHttpError = cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                val httpCode = if (isHttpError)
                    (cause as androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException).responseCode
                else -1

                val isRealBlock = isHttpError && (httpCode == 403 || httpCode == 401 || httpCode == 428)

                if (isRealBlock && stripHeaders) {
                    // 403/401 and headers were stripped? Try WITH headers and retry.
                    stripHeaders = false
                    playToken++
                    return
                }

                if (!isRealBlock && retryCount < 1) {
                    // Network/connection error on Fire TV â€” retry once immediately
                    retryCount++
                    playToken++
                    return
                }

                if (showNextSource && onNextSource != null) {
                    android.util.Log.w("ExoLivePlayer", "Player error (HTTP $httpCode). Auto-switching to next server...")
                    onNextSource()
                    return
                }

                onLoadingChange(false)
                errorMessage = when {
                    isHttpError && (httpCode == 403 || httpCode == 401 || httpCode == 428) ->
                        "Stream blocked (HTTP $httpCode) â€” try next server"
                    isHttpError && httpCode in 400..499 ->
                        "Stream unavailable (HTTP $httpCode) â€” try next server"
                    isHttpError && httpCode in 500..599 ->
                        "Server error â€” try next server"
                    else ->
                        "Connection failed â€” try next server"
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

    // Sync captionsEnabled â†’ text renderer whenever it changes
    LaunchedEffect(captionsEnabled, hasTextTracks) {
        if (hasTextTracks) {
            val exo = (player as? ExoPlayer) ?: return@LaunchedEffect
            exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !captionsEnabled)
                .setPreferredTextLanguage(if (captionsEnabled) "en" else "")
                .build()
        }
    }

    LaunchedEffect(bingeCountdown) {
        if (bingeCountdown != null && bingeCountdown!! > 0) {
            delay(1000L)
            bingeCountdown = bingeCountdown!! - 1
        } else if (bingeCountdown == 0) {
            bingeCountdown = null
            onPlaybackEnded?.invoke()
        }
    }

    // Watch Party Sync loop
    val partyActive = partyState.isActive
    val partyIsHost = partyState.isHost
    LaunchedEffect(partyActive, partyIsHost) {
        if (!partyActive) return@LaunchedEffect
        while (true) {
            if (hasReachedReady) {
                if (partyIsHost) {
                    // Host broadcasts state every 2 seconds
                    partyVm.broadcastState(player.currentPosition, player.isPlaying)
                } else {
                    // Guest checks for drift
                    val hostPos = partyState.syncPositionMs
                    val myPos = player.currentPosition
                    if (hostPos > 0L && kotlin.math.abs(hostPos - myPos) > 5000L) {
                        player.seekTo(hostPos)
                    }
                    if (partyState.syncIsPlaying && !player.isPlaying) player.play()
                    if (!partyState.syncIsPlaying && player.isPlaying) player.pause()
                }
            }
            delay(2000L)
        }
    }

    // Binge Mode loop
    LaunchedEffect(player, bingeNextLabel) {
        if (bingeNextLabel == null) return@LaunchedEffect
        while (true) {
            if (hasReachedReady && !isLiveBroadcast) {
                val dur = player.duration
                val pos = player.currentPosition
                if (dur > 0 && pos > 0 && dur - pos <= 15000L) {
                    val remaining = ((dur - pos) / 1000).toInt().coerceAtLeast(0)
                    if (bingeCountdown != remaining) {
                        bingeCountdown = remaining
                    }
                    if (remaining == 0) {
                        onBingeNext?.invoke()
                        break // Wait for recomposition to change the stream
                    }
                } else if (bingeCountdown != null) {
                    bingeCountdown = null
                }
            }
            delay(500)
        }
    }

    // Profanity bleep & scene moderation monitor loop
    LaunchedEffect(player, bleepIntervals, sceneIntervals, filterSettings) {
        var isCurrentlyBleeping = false
        while (true) {
            if (hasReachedReady) {
                val pos = player.currentPosition

                // Profanity Bleep Check
                if (filterSettings.profanityMode == "BLEEP" && bleepIntervals.isNotEmpty()) {
                    val matchingInterval = bleepIntervals.firstOrNull { 
                        pos >= it.startMs && pos <= it.endMs && com.enigma.tv.data.ProfanityBleepEngine.shouldBleep(it, filterSettings.profanitySensitivity) 
                    }
                    if (matchingInterval != null) {
                        if (!isCurrentlyBleeping) {
                            isCurrentlyBleeping = true
                            player.volume = 0f
                            val duration = (matchingInterval.endMs - pos).coerceAtLeast(100L)
                            com.enigma.tv.data.ProfanityBleepEngine.playBleep(duration)
                        }
                    } else if (isCurrentlyBleeping) {
                        isCurrentlyBleeping = false
                        player.volume = 1f
                    }
                }

                // Scene Moderation Check
                if (filterSettings.sceneMode != "DISABLED" && sceneIntervals.isNotEmpty()) {
                    val scene = sceneIntervals.firstOrNull { pos >= it.startMs && pos <= it.endMs }
                    if (scene != null) {
                        if (filterSettings.sceneMode == "SKIP") {
                            player.seekTo(scene.endMs + 200L)
                            android.widget.Toast.makeText(context, "Skipped sensitive scene", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (filterSettings.sceneMode == "BLUR") {
                            isSceneBlurred = true
                        }
                    } else {
                        isSceneBlurred = false
                    }
                }
            }
            delay(120)
        }
    }

    val videoContent: @Composable () -> Unit = {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { playerWidthPx = it.width; playerHeightPx = it.height }
                    .pointerInput(tvControls) {
                        if (tvControls != null) return@pointerInput // TV uses D-pad, not gestures
                        var totalDx = 0f
                        var totalDy = 0f
                        var edgeSide = 0 // -1 left, 1 right, 0 horizontal seek
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                totalDx = 0f
                                val leftEdge = playerWidthPx * 0.2f
                                val rightEdge = playerWidthPx * 0.8f
                                edgeSide = when {
                                    offset.x < leftEdge -> -1
                                    offset.x > rightEdge -> 1
                                    else -> 0
                                }
                            },
                            onDragEnd = { gestureLabel = null },
                            onDragCancel = { gestureLabel = null },
                            onHorizontalDrag = { _: PointerInputChange, dragAmount: Float ->
                                totalDx += dragAmount
                                val seekSeconds = (totalDx / playerWidthPx * 90).toInt()
                                if (edgeSide == 0 && hasReachedReady) {
                                    val icon = if (seekSeconds > 0) "â©" else "âª"
                                    gestureLabel = "$icon ${kotlin.math.abs(seekSeconds)}s"
                                    gestureIcon = icon
                                } else if (edgeSide == -1) {
                                    // Left edge: brightness
                                    try {
                                        val cr = context.contentResolver
                                        val cur = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128)
                                        val newBrightness = (cur + (dragAmount * -0.5f)).toInt().coerceIn(10, 255)
                                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, newBrightness)
                                        val pct = (newBrightness / 255f * 100).toInt()
                                        gestureLabel = "â˜€ï¸ $pct%"
                                    } catch (_: Exception) {}
                                } else if (edgeSide == 1) {
                                    // Right edge: volume
                                    if (dragAmount < -3f) audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0
                                    ) else if (dragAmount > 3f) audioManager.adjustStreamVolume(
                                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0
                                    )
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    gestureLabel = "ðŸ”Š ${(curVol * 100 / maxVol)}%"
                                }
                            }
                        )
                    }
            ) {
                AndroidView(
                    factory = { ctx ->
                        val view = android.view.LayoutInflater.from(ctx).inflate(com.enigma.tv.R.layout.enigma_player_view, null) as PlayerView
                        view.apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            this.player = player
                            useController = true   // Use our custom XML controller layout
                            controllerShowTimeoutMs = 4000
                            controllerHideOnTouch = true
                            // Disable focus on PlayerView itself so Compose handles all D-pad input
                            isFocusable = false
                            isFocusableInTouchMode = false
                        }
                    },
                    update = { view ->
                        view.player = debouncedPlayer
                        view.controllerShowTimeoutMs = 4000
                        // CC button
                        view.setShowSubtitleButton(false)
                        val ccBtn = view.findViewById<android.widget.ImageButton>(com.enigma.tv.R.id.btn_enigma_cc)
                        ccBtn?.visibility = android.view.View.VISIBLE
                        ccBtn?.isFocusable = true
                        ccBtn?.isFocusableInTouchMode = true
                        ccBtn?.alpha = if (captionsEnabled) 1.0f else 0.7f
                        ccBtn?.setColorFilter(
                            if (captionsEnabled) android.graphics.Color.parseColor("#9C27B0")
                            else android.graphics.Color.WHITE
                        )
                        ccBtn?.setOnClickListener {
                            showCaptionDialog = true
                        }
                        // Close button
                        view.findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_close)
                            ?.setOnClickListener { onClose() }
                        // Title + subtitle
                        view.findViewById<android.widget.TextView>(com.enigma.tv.R.id.tv_enigma_title)
                            ?.text = title
                        view.findViewById<android.widget.TextView>(com.enigma.tv.R.id.tv_enigma_subtitle)
                            ?.text = sourceLabel
                        // Next-server button
                        val nextBtn = view.findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_next)
                        
                        // Thumbnail scrubber
                        val timeBar = view.findViewById<androidx.media3.ui.DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress)
                        timeBar?.addListener(object : androidx.media3.ui.TimeBar.OnScrubListener {
                            override fun onScrubStart(tb: androidx.media3.ui.TimeBar, position: Long) {
                                scrubPositionMs = position
                                isScrubbing = true
                            }
                            override fun onScrubMove(tb: androidx.media3.ui.TimeBar, position: Long) {
                                scrubPositionMs = position
                            }
                            override fun onScrubStop(tb: androidx.media3.ui.TimeBar, position: Long, canceled: Boolean) {
                                isScrubbing = false
                            }
                        })
                        if (showNextSource && onNextSource != null) {
                            nextBtn?.visibility = View.VISIBLE
                            nextBtn?.setOnClickListener { onNextSource() }
                        } else {
                            nextBtn?.visibility = View.GONE
                        }
                        // Speed button
                        view.findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_speed)?.setOnClickListener {
                            val currentSpeed = player.playbackParameters.speed
                            val nextSpeed = when {
                                currentSpeed < 1.0f -> 1.0f
                                currentSpeed < 1.25f -> 1.25f
                                currentSpeed < 1.5f -> 1.5f
                                currentSpeed < 2.0f -> 2.0f
                                else -> 0.75f
                            }
                            player.playbackParameters = androidx.media3.common.PlaybackParameters(nextSpeed)
                            // Show a quick toast to confirm speed change
                            android.widget.Toast.makeText(context, "${nextSpeed}x Speed", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        // Episodes button
                        val epsBtn = view.findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_episodes)
                        if (tvControls != null && onShowEpisodes != null) {
                            epsBtn?.visibility = View.VISIBLE
                            epsBtn?.setOnClickListener { onShowEpisodes() }
                        } else {
                            epsBtn?.visibility = View.GONE
                        }
                        
                        // Quality button
                        view.findViewById<android.view.View>(com.enigma.tv.R.id.btn_enigma_quality)?.setOnClickListener {
                            showQualityPicker = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isSceneBlurred) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.95f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Scene Hidden (Content Moderation Active)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                
                // Thumbnail Overlay
                if (isScrubbing && thumbnailEntries.isNotEmpty()) {
                    val thumb = thumbnailEntries.firstOrNull { scrubPositionMs >= it.startMs && scrubPositionMs < it.endMs }
                        ?: thumbnailEntries.lastOrNull { scrubPositionMs >= it.startMs }
                    if (thumb != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 120.dp), // hover above scrubber
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val density = androidx.compose.ui.platform.LocalDensity.current.density
                            coil.compose.SubcomposeAsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(thumb.imageUrl)
                                    .addHeader("Referer", resolved?.referer ?: "")
                                    .allowHardware(false)
                                    .build(),
                                contentDescription = "Preview",
                                loading = { /* empty */ },
                                success = { state ->
                                    val bitmap = (state.result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                                    if (bitmap != null) {
                                        androidx.compose.foundation.Canvas(
                                            modifier = Modifier
                                                .width((thumb.w / density).dp)
                                                .height((thumb.h / density).dp)
                                                .border(2.dp, Color.White, RoundedCornerShape(4.dp))
                                                .clip(RoundedCornerShape(4.dp))
                                        ) {
                                            drawImage(
                                                image = bitmap.asImageBitmap(),
                                                srcOffset = androidx.compose.ui.unit.IntOffset(thumb.x, thumb.y),
                                                srcSize = androidx.compose.ui.unit.IntSize(thumb.w, thumb.h),
                                                dstOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Binge Countdown Overlay
                if (bingeCountdown != null && bingeNextLabel != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 120.dp, end = 60.dp),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        var bingeFocused by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (bingeFocused) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.8f))
                                .border(2.dp, if (bingeFocused) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable {
                                    bingeCountdown = null
                                    onBingeNext?.invoke() 
                                }
                                .onFocusChanged { bingeFocused = it.isFocused }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column {
                                Text("Up Next", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(bingeNextLabel!!, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(40.dp)) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    progress = { bingeCountdown!!.toFloat() / 15f },
                                    color = EnigmaPink,
                                    trackColor = Color.White.copy(alpha = 0.2f),
                                    strokeWidth = 3.dp
                                )
                                Text(bingeCountdown.toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                if (showQualityPicker) {
                    QualityPickerDialog(
                        player = player as ExoPlayer,
                        visible = true,
                        onDismiss = { showQualityPicker = false }
                    )
                }

                if (showCaptionDialog) {
                    CaptionPickerDialog(
                        visible = true,
                        captionStatus = captionStatus,
                        hasFileCaptions = hasTextTracks || captionStatus == CaptionStatus.AVAILABLE,
                        captionsEnabled = captionsEnabled,
                        onToggleCaptions = { captionsEnabled = !captionsEnabled },
                        onDismiss = { showCaptionDialog = false }
                    )
                }

                
                if (streamLoading && !useExternalChrome) {
                    EnigmaLoadingRing(
                        modifier = Modifier.fillMaxSize().background(BgDark.copy(alpha = 0.85f)),
                        message = fetchLabelText,
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
                
                // Gesture feedback overlay
                gestureLabel?.let { label ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.Black.copy(alpha = 0.72f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(horizontal = 28.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                bingeCountdown?.let { count ->
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 64.dp, end = 32.dp)
                            .background(Color.Black.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .padding(24.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Next episode starting in $count...",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { 
                                    bingeCountdown = null
                                    onPlaybackEnded?.invoke()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
                            ) { Text("Play Now", color = Color.White) }
                            Button(
                                onClick = { 
                                    bingeCountdown = null
                                    onClose()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                            ) { Text("Cancel", color = Color.White) }
                        }
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

@OptIn(UnstableApi::class)
@Composable
fun QualityPickerDialog(
    player: ExoPlayer,
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.95f), androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Text("Select Quality", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))
            val videoGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            
            // "Auto" option
            var autoFocused by remember { mutableStateOf(false) }
            Button(
                onClick = {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO).build()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { autoFocused = it.isFocused }
                    .then(if (autoFocused) Modifier.border(2.dp, EnigmaPurple, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier),
                colors = ButtonDefaults.buttonColors(containerColor = if(autoFocused) Color.White else Color.White.copy(alpha=0.1f))
            ) {
                Text("Auto", color = if (autoFocused) Color.Black else Color.White)
            }
            
            videoGroups.forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height <= 0) continue
                    val resolution = "${format.height}p"
                    val isSupported = group.isTrackSupported(i)
                    if (!isSupported) continue
                    
                    var itemFocused by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                                .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(i)))
                                .build()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { itemFocused = it.isFocused }
                            .then(if (itemFocused) Modifier.border(2.dp, EnigmaPurple, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) else Modifier),
                        colors = ButtonDefaults.buttonColors(containerColor = if (itemFocused) Color.White else Color.White.copy(alpha = 0.1f))
                    ) {
                        Text(resolution, color = if (itemFocused) Color.Black else Color.White)
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun CaptionPickerDialog(
    visible: Boolean,
    captionStatus: CaptionStatus,
    hasFileCaptions: Boolean,
    captionsEnabled: Boolean,
    onToggleCaptions: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .background(Color.Black.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .padding(24.dp)
        ) {
            Text("Captions & Subtitles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(16.dp))

            if (hasFileCaptions) {
                var btnFocused by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        onToggleCaptions()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { btnFocused = it.isFocused }
                        .then(if (btnFocused) Modifier.border(2.dp, EnigmaPurple, RoundedCornerShape(8.dp)) else Modifier),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (captionsEnabled) EnigmaPink else if (btnFocused) Color.White else Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Text(
                        text = if (captionsEnabled) "? Subtitles Enabled" else "Enable Subtitles",
                        color = if (captionsEnabled) Color.White else if (btnFocused) Color.Black else Color.White
                    )
                }
            } else {
                Text(
                    text = if (captionStatus == CaptionStatus.SEARCHING) "Searching for subtitles..." else "No subtitles available for this title",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))
            var closeFocused by remember { mutableStateOf(false) }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).onFocusChanged { closeFocused = it.isFocused }
                    .then(if (closeFocused) Modifier.border(2.dp, EnigmaPurple, RoundedCornerShape(8.dp)) else Modifier),
                colors = ButtonDefaults.buttonColors(containerColor = if (closeFocused) Color.White else Color.White.copy(alpha = 0.1f))
            ) {
                Text("Close", color = if (closeFocused) Color.Black else Color.White)
            }
        }
    }
}