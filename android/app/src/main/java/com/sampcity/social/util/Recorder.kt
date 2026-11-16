package com.sampcity.social.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Graba un audio (AAC en MP4, mono, ~32 kb/s: 60 s pesan unos 240 KB). Solo un audio a la vez. */
class Recorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var t0 = 0L
    val active get() = rec != null
    val seconds get() = if (rec == null) 0.0 else (System.currentTimeMillis() - t0) / 1000.0

    fun start(): Boolean {
        stop(true)
        val f = File(ctx.cacheDir, "audio-${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION") val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC); r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4); r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1); r.setAudioSamplingRate(22050); r.setAudioEncodingBitRate(32000); r.setMaxDuration(60_000); r.setOutputFile(f.absolutePath)
            r.prepare(); r.start(); rec = r; file = f; t0 = System.currentTimeMillis(); true
        } catch (e: Exception) { runCatching { r.release() }; f.delete(); false }
    }

    /** volumen actual, de 0 a 1 */
    fun level(): Float = (rec?.maxAmplitude ?: 0).let { (it / 12000f).coerceIn(0f, 1f) }

    /** Termina: devuelve el archivo y su duración en segundos, o null si se canceló o fue muy corto. */
    fun stop(cancel: Boolean): Pair<File, Int>? {
        val r = rec ?: return null; val f = file
        val dur = seconds
        rec = null; file = null
        runCatching { r.stop() }; runCatching { r.release() }
        if (cancel || f == null || dur < 0.8) { f?.delete(); return null }
        return f to Math.round(dur).toInt().coerceIn(1, 60)
    }
}
