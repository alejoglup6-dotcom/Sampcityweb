package com.sampcity.social.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Crear una historia (foto, o video de hasta 15 s): se sube el archivo y luego se publica. */
@Composable
fun StoryCreateDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val app = SampApp.instance
    var uri by remember { mutableStateOf<Uri?>(null) }
    var isVideo by remember { mutableStateOf(false) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) { uri = u; isVideo = Media.mimeOf(ctx, u).startsWith("video/") }
    }

    fun publish() {
        val u = uri ?: return
        busy = true
        scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    if (isVideo) {
                        if (Media.videoDurationSec(ctx, u) > 15.4) error("El video dura más de 15 segundos")
                        val max = app.session.info.value.videoMax * 1048576L
                        val thumb = app.api.upload("img", Media.videoThumb(ctx, u), "image/jpeg")
                        app.api.upload("vid", Media.readBytes(ctx, u, max), Media.mimeOf(ctx, u).let { if (it == "video/quicktime" || it == "video/webm") it else "video/mp4" }, thumb)
                    } else app.api.upload("img", Media.compress(ctx, u), "image/jpeg")
                }
                app.repo.createStory(id, caption.trim())
                onDone(); onDismiss()
            } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text("Crear historia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uri == null) OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, Modifier.fillMaxWidth().height(96.dp)) {
                    Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(28.dp)); Text("  Elegir foto o video (máx. 15 s)")
                } else {
                    if (isVideo) Text("Video seleccionado ✔", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                    else AsyncImage(uri, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(220.dp))
                    OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }, enabled = !busy) { Text("Cambiar") }
                }
                OutlinedTextField(caption, { if (it.length <= 200) caption = it }, label = { Text("Añade un texto…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                if (busy) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Subiendo…") }
            }
        },
        confirmButton = { TextButton(::publish, enabled = uri != null && !busy) { Text("Publicar") } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } },
    )
}
