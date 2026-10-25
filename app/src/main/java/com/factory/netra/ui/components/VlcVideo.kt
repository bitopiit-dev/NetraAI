package com.factory.netra.ui.components

import android.net.Uri
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.factory.netra.container
import com.factory.netra.ui.theme.NetraColors
import kotlinx.coroutines.delay
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * libVLC engine: plays the same RTSP streams desktop VLC plays, including older
 * Dahua / OEM firmwares that Android's built-in player rejects.
 */
@Composable
internal fun VlcVideo(
    url: String,
    modifier: Modifier,
    useTexture: Boolean,
    onTexture: (TextureView?) -> Unit,
    onState: (StreamState) -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val libVlc = context.container.libVlc
    var attempt by remember(url) { mutableIntStateOf(0) }
    var state by remember(url) { mutableStateOf(StreamState.Connecting) }
    var lastError by remember(url) { mutableStateOf<String?>(null) }
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    val player = remember(url, attempt) { MediaPlayer(libVlc) }
    val layout = remember(url, attempt) { VLCVideoLayout(context) }

    DisposableEffect(player) {
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Vout -> if (ev.voutCount > 0) {
                    state = StreamState.Playing
                    lastError = null
                    player.currentVideoTrack?.let { t ->
                        if (t.width > 0 && t.height > 0) {
                            val sar = if (t.sarNum > 0 && t.sarDen > 0) t.sarNum.toFloat() / t.sarDen else 1f
                            aspect = t.width * sar / t.height
                        }
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e("NetraVideo", "VLC error ${safeUrl(url)}")
                    lastError = "VLC could not open the stream"
                    state = StreamState.Reconnecting
                }
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> {
                    if (state == StreamState.Playing) lastError = "Stream ended"
                    state = StreamState.Reconnecting
                }
                else -> Unit
            }
        }
        player.attachViews(layout, null, false, useTexture)
        player.videoScale = MediaPlayer.ScaleType.SURFACE_FILL // box below already has the video's aspect
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=400")
            addOption(":rtsp-tcp")
            addOption(":no-audio")
        }
        player.media = media
        media.release()
        player.play()
        if (useTexture) layout.post { onTexture(findTexture(layout)) }

        onDispose {
            player.setEventListener(null)
            player.stop()
            player.detachViews()
            player.release()
            onTexture(null)
        }
    }

    LaunchedEffect(state, attempt) {
        onState(state)
        when (state) {
            StreamState.Connecting -> {
                delay(15_000) // no picture after 15 s: treat as failed
                lastError = lastError ?: "No picture from camera (timeout)"
                state = StreamState.Reconnecting
            }
            StreamState.Reconnecting -> {
                delay(6_000)
                state = StreamState.Connecting
                attempt++
            }
            StreamState.Playing -> Unit
        }
    }

    BoxWithConstraints(modifier.background(NetraColors.Video), contentAlignment = Alignment.Center) {
        val compact = maxHeight < 140.dp
        Box(Modifier.aspectRatio(aspect)) {
            key(player) {
                AndroidView(modifier = Modifier.fillMaxSize(), factory = { layout })
            }
            overlay()
        }
        StreamStatus(state, lastError, compact)
    }
}

private fun findTexture(v: View): TextureView? {
    if (v is TextureView) return v
    if (v is ViewGroup) for (i in 0 until v.childCount) findTexture(v.getChildAt(i))?.let { return it }
    return null
}
