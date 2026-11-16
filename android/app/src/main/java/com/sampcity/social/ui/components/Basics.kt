package com.sampcity.social.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.ApiError

val LocalSnack = staticCompositionLocalOf { SnackbarHostState() }
val Online = Color(0xFF3DDC84)
val VerifiedBlue = Color(0xFF1D9BF0)

/** Foto de perfil del jugador (la suya, o su skin del juego); si no carga, la inicial del nombre. */
@Composable
fun Avatar(name: String, skin: String?, size: Dp = 40.dp, modifier: Modifier = Modifier, online: Boolean = false, onClick: (() -> Unit)? = null) {
    val api = SampApp.instance.api
    Box(modifier.size(size).let { if (onClick != null) it.clickable(onClick = onClick) else it }) {
        val initial: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Text(name.firstOrNull()?.uppercase() ?: "?", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = (size.value * 0.42f).sp)
            }
        }
        SubcomposeAsyncImage(
            model = api.avatarUrl(name, skin), contentDescription = name.replace('_', ' '), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize().clip(CircleShape), loading = { initial() }, error = { initial() },
        )
        if (online) Box(Modifier.align(Alignment.BottomEnd).size(size * 0.28f).clip(CircleShape).background(Online).border(2.dp, MaterialTheme.colorScheme.surface, CircleShape))
    }
}

fun String.pretty() = replace('_', ' ')

/** Nombre del jugador con la insignia de verificado si la tiene. */
@Composable
fun PlayerName(name: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall, color: Color = Color.Unspecified) {
    val verified = SampApp.instance.session.info.collectAsStateValue().verified.contains(name)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(name.pretty(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (verified) Box(Modifier.padding(start = 4.dp).size(15.dp).clip(CircleShape).background(VerifiedBlue), contentAlignment = Alignment.Center) { Text("✔", color = Color.White, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) } // igual que .vf de la web
    }
}

@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T = this.collectAsStateWithLifecycle().value

@Composable fun LoadingBox(modifier: Modifier = Modifier.fillMaxSize()) { Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() } }

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier.fillMaxSize()) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(44.dp))
        Spacer(Modifier.size(10.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) { Spacer(Modifier.size(12.dp)); OutlinedButton(onClick = onRetry) { Text("Reintentar") } }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier.fillMaxWidth(), action: String? = null, onAction: (() -> Unit)? = null) {
    Column(modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null && onAction != null) { Spacer(Modifier.size(10.dp)); Button(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary, modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp))

@Composable
fun ConfirmDialog(title: String, text: String = "", confirm: String = "Aceptar", danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = if (text.isNotBlank()) ({ Text(text) }) else null,
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun PromptDialog(title: String, initial: String = "", label: String = "", multiline: Boolean = false, confirm: String = "Guardar", maxLen: Int = 300, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { if (it.length <= maxLen) v = it }, label = if (label.isNotBlank()) ({ Text(label) }) else null, singleLine = !multiline, minLines = if (multiline) 3 else 1, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(v.trim()); onDismiss() }, enabled = v.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Texto de un error de la API para mostrar al usuario. */
fun Throwable.userMessage(): String = (this as? ApiError)?.message ?: message ?: "Algo salió mal"
