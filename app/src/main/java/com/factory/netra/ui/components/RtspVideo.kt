package com.factory.netra.ui.components

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factory.netra.container
import com.factory.netra.data.prefs.VideoEngine
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.delay

enum class StreamState { Connecting, Playing, Reconnecting }

@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context, url: String, useTcp: Boolean): ExoPlayer {
    // Low-latency buffers: this is live CCTV, not a movie.
    val load = DefaultLoadControl.Builder()
        .setBufferDurationsMs(500, 2_000, 250, 500)
        .build()
    val player = ExoPlayer.Builder(context).setLoadControl(load).build()
    val source = RtspMediaSource.Factory()
        .setForceUseRtpTcp(useTcp) // TCP first; every other retry tries UDP
        .setTimeoutMs(8_000)
        .createMediaSource(MediaItem.fromUri(url))
    player.setMediaSource(source)
    player.volume = 0f
    player.playWhenReady = true
    return player
}

/** URLs where the Android player failed and VLC took over (kept for this app session). */
private val vlcFallback = mutableStateMapOf<String, Boolean>()

/**
 * Live Dahua RTSP stream.
 * Engine "Auto": Android's Media3 player first (light, hardware decoding); if it fails
 * twice on a camera, that camera switches to libVLC, which plays the same old/OEM
 * Dahua firmwares that desktop VLC plays.
 */
@Composable
fun RtspVideo(
    url: String,
    modifier: Modifier = Modifier,
    useTexture: Boolean = false,
    onTexture: (TextureView?) -> Unit = {},
    onState: (StreamState) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val settings by LocalContext.current.container.settings.flow.collectAsStateWithLifecycle()
    val engine = settings.videoEngine
    val useVlc = engine == VideoEngine.VLC || (engine == VideoEngine.AUTO && vlcFallback[url] == true)
    if (useVlc) {
        VlcVideo(url, modifier, useTexture, onTexture, onState, overlay)
    } else {
        ExoVideo(
            url, modifier, useTexture, onTexture, onState, overlay,
            onGiveUp = if (engine == VideoEngine.AUTO) ({ vlcFallback[url] = true }) else null,
        )
    }
}

/** Media3 / ExoPlayer engine. Reconnects every 6 s, alternating RTP over TCP and UDP. */
@Composable
private fun ExoVideo(
    url: String,
    modifier: Modifier,
    useTexture: Boolean,
    onTexture: (TextureView?) -> Unit,
    onState: (StreamState) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
    onGiveUp: (() -> Unit)?,
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url) { mutableStateOf(StreamState.Connecting) }
    var lastError by remember(url) { mutableStateOf<String?>(null) }
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    val player = remember(url, attempt) { buildPlayer(context, url, useTcp = attempt % 2 == 0) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    state = StreamState.Playing
                    lastError = null
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Visible in Logcat with the filter tag:NetraVideo
                Log.e("NetraVideo", "${safeUrl(url)} [${if (attempt % 2 == 0) "TCP" else "UDP"}] -> ${error.errorCodeName}: ${error.cause?.message ?: error.message}", error)
                lastError = explain(error)
                state = StreamState.Reconnecting
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        player.addListener(listener)
        player.prepare()
        onDispose {
            player.removeListener(listener)
            player.release()
            onTexture(null)
        }
    }

    LaunchedEffect(state, attempt) {
        onState(state)
        if (state == StreamState.Reconnecting) {
            if (attempt >= 1 && onGiveUp != null) {
                onGiveUp() // tried TCP and UDP: hand over to VLC
                return@LaunchedEffect
            }
            delay(6_000)
            state = StreamState.Connecting
            attempt++
        }
    }

    BoxWithConstraints(modifier.background(NetraColors.Video), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 140.dp
        Box(Modifier.aspectRatio(aspect)) {
            key(player) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        if (useTexture) TextureView(ctx).also { player.setVideoTextureView(it); onTexture(it) }
                        else SurfaceView(ctx).also { player.setVideoSurfaceView(it) }
                    },
                )
            }
            overlay()
        }
        StreamStatus(state, lastError, compact)
    }
}

/** Hides the password before anything is logged. */
internal fun safeUrl(url: String) = url.replace(Regex("//[^@/]*@"), "//***@")

/** Turns ExoPlayer errors into something a security admin can act on. */
private fun explain(e: PlaybackException): String {
    val msg = (e.cause?.message ?: e.message ?: "").lowercase()
    return when {
        "401" in msg || "unauthorized" in msg -> "Wrong username/password for RTSP"
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            "refused" in msg || "unreachable" in msg -> "Cannot reach RTSP port 554"
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            "timeout" in msg || "timed out" in msg -> "RTSP timed out"
        e.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            e.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED -> "Video codec not supported here (set H.264)"
        else -> {
            val reason = (e.cause?.message ?: e.message).orEmpty().take(90)
            if (reason.isBlank()) e.errorCodeName else "${e.errorCodeName}\n$reason"
        }
    }
}

@Composable
internal fun BoxScope.StreamStatus(state: StreamState, lastError: String?, compact: Boolean) {
    Box(Modifier.align(if (compact) Alignment.TopEnd else Alignment.Center).padding(8.dp)) {
        when (state) {
            StreamState.Connecting -> CircularProgressIndicator(
                color = NetraColors.Chalk, strokeWidth = 2.dp,
                modifier = Modifier.size(if (compact) 16.dp else 28.dp),
            )
            StreamState.Reconnecting -> if (compact) {
                Icon(Icons.Outlined.VideocamOff, lastError, tint = NetraColors.Alarm, modifier = Modifier.size(18.dp))
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Icon(Icons.Outlined.VideocamOff, null, tint = NetraColors.TextDim)
                    Text("No video, retrying", style = MaterialTheme.typography.bodySmall, color = NetraColors.TextDim)
                    lastError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = NetraColors.Amber, textAlign = TextAlign.Center)
                    }
                }
            }
            StreamState.Playing -> Unit
        }
    }
}
