@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sampcity.social.ui.components

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sampcity.social.util.newVideoPlayer

/** Video sin controles (historias y reels): se reproduce si `play`, se repite si `loop`, y avisa al terminar y su progreso (0 a 1). */
@Composable
fun VideoPlayer(url: String, play: Boolean, modifier: Modifier = Modifier, loop: Boolean = false, fill: Boolean = false, muted: Boolean = false, onEnded: () -> Unit = {}, onProgress: (Float) -> Unit = {}, onReady: (Long) -> Unit = {}) {
    val ctx = LocalContext.current
    val player = remember(url) { newVideoPlayer(ctx).apply { setMediaItem(MediaItem.fromUri(url)); repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF; prepare() } }
    var ready by remember(url) { mutableStateOf(false) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlaybackStateChanged(st: Int) { if (st == Player.STATE_ENDED) onEnded(); if (st == Player.STATE_READY) { ready = true; onReady(player.duration) } }
        }
        player.addListener(l); onDispose { player.removeListener(l); player.release() }
    }
    LaunchedEffect(play, ready) { player.volume = if (muted) 0f else 1f; player.playWhenReady = play }
    LaunchedEffect(play) { while (play) { if (player.duration > 0) onProgress((player.currentPosition.toFloat() / player.duration).coerceIn(0f, 1f)); kotlinx.coroutines.delay(60) } }
    AndroidView(modifier = modifier, factory = { c -> PlayerView(c).apply { useController = false; resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } }, update = { it.player = player })
}
