package com.enigma.tv.data

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class PlaybackTestResult(
    val success: Boolean,
    val hasCaptions: Boolean,
    val error: String? = null
)

object StreamPlaybackTester {

    @OptIn(UnstableApi::class)
    suspend fun testPlayback(context: Context, resolved: ResolvedStream): PlaybackTestResult = withContext(Dispatchers.Main) {
        var player: ExoPlayer? = null
        try {
            // Build player with same legacy decoding fix as ExoLivePlayer
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildTextRenderers(
                    context: Context,
                    extensionRendererMode: Int,
                    messageHandler: android.os.Handler,
                    textOutput: androidx.media3.exoplayer.text.TextOutput,
                    out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
                ) {
                    super.buildTextRenderers(context, extensionRendererMode, messageHandler, textOutput, out)
                    for (renderer in out) {
                        if (renderer.trackType == C.TRACK_TYPE_TEXT) {
                            try {
                                val method = renderer.javaClass.getMethod("experimentalSetLegacyDecodingEnabled", Boolean::class.javaPrimitiveType)
                                method.invoke(renderer, true)
                            } catch (e: Exception) {
                                // Ignore if not available
                            }
                        }
                    }
                }
            }

            player = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory)
                .build()

            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(resolved.userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(8_000)
                .apply {
                    val headers = resolved.headers.toMutableMap()
                    if (resolved.referer.isNotBlank()) {
                        headers["Referer"] = resolved.referer
                    }
                    if (headers.isNotEmpty()) {
                        setDefaultRequestProperties(headers)
                    }
                }

            val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(context, dataSourceFactory)
            
            val uri = android.net.Uri.parse(resolved.url)
            val itemBuilder = MediaItem.Builder().setUri(uri)
            
            val mainMimeType = when {
                resolved.provider == "embed-hls" ||
                resolved.url.contains(".m3u8", ignoreCase = true) ||
                resolved.url.contains("playlist", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
                
                resolved.provider == "embed-mp4" ||
                resolved.url.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
                
                else -> null
            }
            if (mainMimeType != null) {
                itemBuilder.setMimeType(mainMimeType)
            }

            val primaryMediaItem = itemBuilder.build()
            var mediaSource: MediaSource = DefaultMediaSourceFactory(defaultDataSourceFactory).createMediaSource(primaryMediaItem)

            val subUrl = resolved.subtitleUrl?.takeIf { StreamResolver.isValidSubtitleUrl(it) }
            if (subUrl != null) {
                val subUri = android.net.Uri.parse(subUrl)
                val mime = when {
                    subUrl.substringBefore("?").endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
                    subUrl.substringBefore("?").endsWith(".srt", ignoreCase = true) -> MimeTypes.APPLICATION_SUBRIP
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
                    .setTreatLoadErrorsAsEndOfStream(true)
                    .createMediaSource(subtitleConfig, C.TIME_UNSET)
                    
                mediaSource = MergingMediaSource(mediaSource, subSource)
            }

            player.setMediaSource(mediaSource)
            player.prepare()
            // We don't need to actually play the audio/video, just prepare it to STATE_READY
            player.playWhenReady = false

            // Wait up to 15 seconds for STATE_READY
            val result = withTimeoutOrNull(15_000) {
                suspendCancellableCoroutine { continuation ->
                    val listener = object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                if (continuation.isActive) continuation.resume(true)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            if (continuation.isActive) continuation.resume(false)
                        }
                    }
                    player?.addListener(listener)
                    
                    if (player?.playbackState == Player.STATE_READY) {
                        if (continuation.isActive) continuation.resume(true)
                    } else if (player?.playerError != null) {
                        if (continuation.isActive) continuation.resume(false)
                    }
                    
                    continuation.invokeOnCancellation {
                        player?.removeListener(listener)
                    }
                }
            } ?: false

            if (!result) {
                val errorMsg = player.playerError?.message ?: "Stream timed out while loading"
                return@withContext PlaybackTestResult(false, false, errorMsg)
            }

            // Verify captions
            var hasCaptions = false
            val currentTracks = player.currentTracks
            for (group in currentTracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    hasCaptions = true
                    break
                }
            }

            return@withContext PlaybackTestResult(true, hasCaptions)
            
        } catch (e: Exception) {
            return@withContext PlaybackTestResult(false, false, e.message ?: "Unknown error")
        } finally {
            player?.release()
        }
    }
}
