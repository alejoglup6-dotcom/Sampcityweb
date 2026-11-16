@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sampcity.social.util

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.sampcity.social.SampApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Un reproductor de audio para toda la app (los audios del chat): solo suena uno a la vez. Usa la sesión para los archivos privados. */
object PlayerHub {
    data class State(val url: String? = null, val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val speed: Float = 1f)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var ticker: Job? = null
    private var speed = 1f

    private fun get(ctx: Context): ExoPlayer = player ?: ExoPlayer.Builder(ctx.applicationContext)
        .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(SampApp.instance.api.client)))
        .build().also { p ->
            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { _state.value = _state.value.copy(playing = isPlaying); if (isPlaying) tick() }
                override fun onPlaybackStateChanged(st: Int) {
                    if (st == Player.STATE_ENDED) { p.pause(); p.seekTo(0); _state.value = _state.value.copy(playing = false, positionMs = 0) }
                    if (st == Player.STATE_READY && p.duration > 0) _state.value = _state.value.copy(durationMs = p.duration)
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) { _state.value = State() }
            })
            player = p
        }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch { while (isActive && player?.isPlaying == true) { _state.value = _state.value.copy(positionMs = player?.currentPosition ?: 0); delay(80) } }
    }

    fun toggle(ctx: Context, url: String) {
        val p = get(ctx)
        if (_state.value.url == url) { if (p.isPlaying) p.pause() else p.play(); return }
        p.setMediaItem(MediaItem.fromUri(url)); p.playbackParameters = PlaybackParameters(speed); p.prepare(); p.play()
        _state.value = State(url = url, playing = true, speed = speed)
    }

    fun seekFraction(ctx: Context, url: String, f: Float, knownMs: Long) {
        val p = get(ctx)
        if (_state.value.url != url) { toggle(ctx, url) }
        val d = (if (p.duration > 0) p.duration else knownMs).coerceAtLeast(1)
        p.seekTo((d * f.coerceIn(0f, 1f)).toLong()); _state.value = _state.value.copy(positionMs = (d * f).toLong())
    }

    fun cycleSpeed() {
        speed = when (speed) { 1f -> 1.5f; 1.5f -> 2f; else -> 1f }
        player?.playbackParameters = PlaybackParameters(speed); _state.value = _state.value.copy(speed = speed)
    }

    fun stop() { player?.pause(); player?.stop(); _state.value = State() }
}

/** Reproductor de video (historias y reels): usa la sesión, así los archivos protegidos también cargan. */
fun newVideoPlayer(ctx: Context): ExoPlayer = ExoPlayer.Builder(ctx.applicationContext)
    .setMediaSourceFactory(DefaultMediaSourceFactory(OkHttpDataSource.Factory(SampApp.instance.api.client))).build()
