package com.sampcity.social.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

/** Reduce fotos antes de subirlas: máx. `maxSide` px y `maxBytes` (el servidor rechaza fotos pesadas). */
object Media {
    fun compress(ctx: Context, uri: Uri, maxSide: Int = 1600, maxBytes: Int = 880 * 1024): ByteArray {
        val cr = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: error("No pude leer la imagen")
        val rot = runCatching { cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } }.getOrNull() ?: 1
        val deg = when (rot) { ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f }
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (scale < 1f || deg != 0f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(deg) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        var q = 88
        while (true) {
            val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            if (out.size() <= maxBytes || q <= 40) return out.toByteArray()
            q -= 8
            if (q < 60 && bmp.width > 900) bmp = Bitmap.createScaledBitmap(bmp, bmp.width * 3 / 4, bmp.height * 3 / 4, true)
        }
    }

    fun dataUrl(bytes: ByteArray, mime: String = "image/jpeg") = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun videoDurationSec(ctx: Context, uri: Uri): Double = MediaMetadataRetriever().run {
        try { setDataSource(ctx, uri); (extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0 } finally { release() }
    }

    /** Un cuadro del video (miniatura obligatoria al subir videos) */
    fun videoThumb(ctx: Context, uri: Uri): ByteArray = MediaMetadataRetriever().run {
        try {
            setDataSource(ctx, uri)
            val b = getFrameAtTime(500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: getFrameAtTime(0) ?: error("No pude leer el video")
            val s = minOf(1f, 960f / maxOf(b.width, b.height)); val t = if (s < 1f) Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true) else b
            ByteArrayOutputStream().also { t.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
        } finally { release() }
    }

    fun readBytes(ctx: Context, uri: Uri, max: Long): ByteArray {
        ctx.contentResolver.openInputStream(uri)!!.use { ins ->
            val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024); var total = 0L
            while (true) { val n = ins.read(buf); if (n < 0) break; total += n; if (total > max) error("El archivo pesa demasiado"); out.write(buf, 0, n) }
            return out.toByteArray()
        }
    }

    fun mimeOf(ctx: Context, uri: Uri): String = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
}
