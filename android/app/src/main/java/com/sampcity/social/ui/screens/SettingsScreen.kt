@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sampcity.social.BuildConfig
import com.sampcity.social.SampApp
import com.sampcity.social.data.SessionInfo
import com.sampcity.social.data.Settings
import com.sampcity.social.live.NotifWorker
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.SectionTitle
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.ui.openWeb
import com.sampcity.social.util.Clock
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun SettingsScreen() {
    val nav = LocalNavigator.current; val app = SampApp.instance; val repo = app.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    val me = app.session.me
    var s by remember { mutableStateOf<Settings?>(null) }
    var sessions by remember { mutableStateOf<List<SessionInfo>>(emptyList()) }
    var twoOn by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var theme by remember { mutableStateOf(app.prefs.theme) }
    var bg by remember { mutableStateOf(app.prefs.bgAlerts) }

    fun load() { scope.launch { try { s = repo.settings(); sessions = runCatching { repo.sessions() }.getOrDefault(emptyList()); twoOn = runCatching { repo.twoFa().on }.getOrDefault(false); error = null } catch (e: Exception) { error = e.userMessage() } } }
    LaunchedEffect(Unit) { load() }
    fun toast(t: String) { scope.launch { snack.showSnackbar(t) } }
    fun save(msg: Int = s!!.msg, prof: Int = s!!.prof, tag: Int = s!!.tag, nmask: Int = s!!.nmask) {
        s = s!!.copy(msg = msg, prof = prof, tag = tag, nmask = nmask)
        scope.launch { runCatching { repo.saveSettings(msg, prof, tag, nmask) }.onFailure { toast(it.userMessage()); load() } }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Configuración") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) }) { pad ->
        val st = s
        if (error != null && st == null) ErrorBox(error!!, ::load, Modifier.padding(pad).fillMaxSize()) else if (st == null) LoadingBox(Modifier.padding(pad).fillMaxSize()) else Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { if (me != null) { Avatar(me.name, me.skin, 52.dp); Column(Modifier.padding(start = 12.dp)) { PlayerName(me.name, style = MaterialTheme.typography.titleMedium); Text(me.rango, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            }
            Section("Cuenta y seguridad") {
                Text("Es la misma contraseña con la que entras al juego.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ dialog = "password" }, Modifier.fillMaxWidth()) { Text("Cambiar contraseña") }
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Verificación en dos pasos"); Text(if (twoOn) "Activada" else "Pide un código de tu app al entrar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; OutlinedButton({ dialog = if (twoOn) "2fa-off" else "2fa-on" }) { Text(if (twoOn) "Desactivar" else "Activar") } }
                HorizontalDivider()
                Text("Sesiones abiertas", style = MaterialTheme.typography.titleSmall)
                sessions.forEach { x ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(x.device + if (x.current) "  (este dispositivo)" else "", style = MaterialTheme.typography.bodyMedium); Text("${x.ip} · ${Clock.ago(x.lastSeen)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (!x.current) TextButton({ scope.launch { runCatching { repo.closeSession(x.id) }.onSuccess { load() }.onFailure { toast(it.userMessage()) } } }) { Text("Cerrar") }
                    }
                }
                OutlinedButton({ dialog = "logout-all" }, Modifier.fillMaxWidth()) { Text("Cerrar sesión en todos los dispositivos") }
            }
            Section("Privacidad") {
                Choice("Quién puede ver mi perfil", listOf("Todos", "Solo amigos"), st.prof) { save(prof = it) }
                Choice("Quién puede enviarme mensajes", listOf("Todos", "Solo amigos", "Nadie"), st.msg) { save(msg = it) }
                Choice("Quién puede etiquetarme", listOf("Todos", "Solo amigos", "Nadie"), st.tag) { save(tag = it) }
            }
            Section("Notificaciones") {
                listOf(1 to "Reacciones", 2 to "Comentarios y respuestas", 4 to "Menciones y etiquetas", 8 to "Amistades", 16 to "Noticias y actualizaciones").forEach { (bit, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(st.nmask and bit == 0, { save(nmask = if (it) st.nmask and bit.inv() else st.nmask or bit) }); Text(label) }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Avisos con la app cerrada"); Text("Revisa cada ~15 minutos si hay mensajes o avisos nuevos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(bg, { bg = it; app.prefs.bgAlerts = it; NotifWorker.schedule(ctx, it) })
                }
            }
            Section("Jugadores bloqueados") {
                if (st.blocked.isEmpty()) Text("No has bloqueado a nadie.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                st.blocked.forEach { b -> Row(verticalAlignment = Alignment.CenterVertically) { Avatar(b.name, b.skin, 38.dp); Text(b.name.pretty(), Modifier.padding(start = 10.dp).weight(1f)); TextButton({ scope.launch { runCatching { repo.unblock(b.name) }.onSuccess { load() }.onFailure { toast(it.userMessage()) } } }) { Text("Desbloquear") } } }
            }
            Section("Apariencia") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to "Del sistema", "dark" to "Oscuro", "light" to "Claro").forEach { (k, l) -> FilterChip(theme == k, { theme = k; app.prefs.theme = k; app.themeMode.value = k }, { Text(l) }) } }
            }
            Section("Cuenta de Discord") {
                Text(if (me?.discord != null) "Vinculada ✔" else "Aún no has vinculado tu Discord.")
                OutlinedButton({ openWeb(ctx, "/verificar") }) { Text("Vincular o revisar") }
            }
            Section("Tus datos") {
                OutlinedButton({
                    scope.launch {
                        try {
                            val bytes = repo.exportData(); val f = File(ctx.cacheDir, "sampcity-${me?.name ?: "datos"}.json").apply { writeBytes(bytes) }
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Guardar mis datos").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { toast(e.userMessage()) }
                    }
                }, Modifier.fillMaxWidth()) { Text("Descargar mis datos") }
                Text("Borra tus publicaciones, comentarios, mensajes, amistades y perfil de la red social. Tu cuenta del juego NO se borra.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ dialog = "delete" }, Modifier.fillMaxWidth()) { Text("Eliminar mi contenido", color = MaterialTheme.colorScheme.error) }
            }
            Section("Acerca de") {
                Text("SampCity ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                Text(BuildConfig.BASE_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton({ scope.launch { runCatching { repo.appInfo() }.onSuccess { i -> if (i.available && i.code > BuildConfig.VERSION_CODE) { toast("Hay una versión nueva: ${i.version}"); openWeb(ctx, "/descargar-app") } else toast("Ya tienes la última versión") }.onFailure { toast(it.userMessage()) } } }) { Text("Buscar actualizaciones") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton({ openWeb(ctx, DISCORD_URL) }) { Text("Discord") }; OutlinedButton({ openWeb(ctx, "/reglas") }) { Text("Reglas") } }
                Button({ scope.launch { app.session.logout() } }, Modifier.fillMaxWidth()) { Text("Cerrar sesión") }
            }
        }
    }

    when (dialog) {
        "password" -> PasswordDialog({ dialog = null }) { toast("Contraseña cambiada"); load() }
        "2fa-on" -> TwoFaDialog({ dialog = null }) { twoOn = true; toast("Verificación activada") }
        "2fa-off" -> SecretDialog("Desactivar verificación", "Escribe tu contraseña para desactivarla.", "Desactivar", { dialog = null }) { pw -> repo.twoFaDisable(pw); twoOn = false; toast("Verificación desactivada") }
        "delete" -> SecretDialog("Eliminar mi contenido", "Escribe tu contraseña para confirmar. Esto no se puede deshacer.", "Eliminar", { dialog = null }) { pw -> repo.deleteMyContent(pw); app.session.logout() }
        "logout-all" -> ConfirmDialog("¿Cerrar sesión en todos los dispositivos?", "Este dispositivo seguirá abierto.", "Cerrar", true, { scope.launch { runCatching { repo.logoutAll() }.onSuccess { toast("Sesiones cerradas"); load() }.onFailure { toast(it.userMessage()) } } }) { dialog = null }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary); content() }
    }
}

@Composable
private fun Choice(label: String, options: List<String>, value: Int, onChange: (Int) -> Unit) {
    Column { Text(label, style = MaterialTheme.typography.bodyMedium); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { options.forEachIndexed { i, o -> FilterChip(value == i, { onChange(i) }, { Text(o) }) } } }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = SampApp.instance.repo; val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var cur by remember { mutableStateOf("") }; var n1 by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Cambiar contraseña") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(cur, { cur = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contraseña actual") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(n1, { n1 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contraseña nueva (6 a 18)") }, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(n2, { n2 = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Repite la contraseña nueva") }, visualTransformation = PasswordVisualTransformation())
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = {
        TextButton({
            if (n1.length !in 6..18) { err = "La contraseña nueva debe tener entre 6 y 18 caracteres"; return@TextButton }
            if (n1 != n2) { err = "Las contraseñas nuevas no coinciden"; return@TextButton }
            busy = true; scope.launch { try { repo.changePassword(cur, n1); onDone(); onDismiss() } catch (e: Exception) { err = e.userMessage(); busy = false } }
        }, enabled = cur.isNotEmpty() && !busy) { Text("Cambiar") }
    }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun SecretDialog(title: String, text: String, action: String, onDismiss: () -> Unit, run: suspend (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var pw by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(text); OutlinedTextField(pw, { pw = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation()); err?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, confirmButton = { TextButton({ busy = true; scope.launch { try { run(pw); onDismiss() } catch (e: Exception) { err = e.userMessage(); busy = false } } }, enabled = pw.isNotEmpty() && !busy) { Text(action, color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}

@Composable
private fun TwoFaDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val repo = SampApp.instance.repo; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var secret by remember { mutableStateOf<String?>(null) }; var uri by remember { mutableStateOf("") }; var code by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { try { repo.twoFaSetup().also { secret = it.secret; uri = it.uri } } catch (e: Exception) { err = e.userMessage() } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Verificación en dos pasos") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (secret == null && err == null) LoadingBox(Modifier.fillMaxWidth().padding(20.dp))
            secret?.let { sec ->
                Text("Escribe esta clave en tu app de códigos (Google Authenticator, Authy…) o ábrela directamente, y luego el código de 6 dígitos:")
                Text(sec.chunked(4).joinToString(" "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                OutlinedButton({ runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure { err = "No encontré una app de códigos instalada" } }) { Text("Abrir en mi app") }
                OutlinedTextField(code, { if (it.length <= 7) code = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Código de 6 dígitos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton({ scope.launch { try { repo.twoFaEnable(code); onDone(); onDismiss() } catch (e: Exception) { err = e.userMessage() } } }, enabled = secret != null && code.length >= 6) { Text("Confirmar") } }, dismissButton = { TextButton(onDismiss) { Text("Cancelar") } })
}
