package com.sampcity.social.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sampcity.social.R
import com.sampcity.social.SampApp
import com.sampcity.social.data.ApiError
import com.sampcity.social.ui.openWeb
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

private val NAME_RX = Regex("^[A-Z][A-Za-z]*_[A-Z][A-Za-z]*$")
private val MAIL_RX = Regex("^[A-Za-z0-9_.]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$")
const val DISCORD_URL = "https://discord.gg/QU7YWerPfV"

/** Iniciar sesión o crear cuenta: es la misma cuenta del juego. */
@Composable
fun AuthScreen() {
    val app = SampApp.instance
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val info by app.session.info.collectAsStateWithLifecycle()
    val notice by app.session.notice.collectAsStateWithLifecycle()
    var register by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var needCode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        error = null
        if (register) {
            when {
                !NAME_RX.matches(name.trim()) || name.trim().length > 24 -> { error = "El nombre tiene que ser Nombre_Apellido (ej.: Carlos_Ramirez)"; return }
                !MAIL_RX.matches(email.trim()) || email.trim().length > 31 -> { error = "Escribe un correo válido"; return }
                pass.length !in 6..18 -> { error = "La contraseña tiene que tener de 6 a 18 caracteres"; return }
                pass != pass2 -> { error = "Las contraseñas no coinciden"; return }
            }
        } else if (name.trim().length < 3 || pass.isEmpty()) { error = "Escribe tu nombre y tu contraseña"; return }
        busy = true
        scope.launch {
            try { if (register) app.session.register(name, email, pass) else app.session.login(name, pass, code) }
            catch (e: ApiError) {
                if (e.body?.get("totp")?.jsonPrimitive?.booleanOrNull == true) needCode = true
                error = e.message
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Image(painterResource(R.drawable.logo), "SampCity", Modifier.width(240.dp).padding(vertical = 12.dp))
        Text(if (register) "Crea tu cuenta. Es la misma con la que entras al juego." else "Entra con el nombre y la contraseña de tu cuenta del servidor.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }

        OutlinedTextField(name, { name = it }, label = { Text("Nombre_Apellido") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false))
        if (register) OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (register) ImeAction.Next else ImeAction.Done))
        if (register) OutlinedTextField(pass2, { pass2 = it }, label = { Text("Repite la contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
        if (needCode && !register) OutlinedTextField(code, { code = it }, label = { Text("Código de 6 dígitos") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center) }

        Button(::submit, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(52.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary) else Text(if (register) "Crear cuenta" else "Iniciar sesión")
        }
        if (!register) TextButton({ if (info.mail) openWeb(ctx, "/recuperar") else openWeb(ctx, DISCORD_URL) }) { Text("¿Has olvidado tu contraseña?") }
        Spacer(Modifier.height(6.dp))
        OutlinedButton({ register = !register; error = null }, Modifier.fillMaxWidth()) { Text(if (register) "Ya tengo cuenta" else "Crear cuenta nueva") }
        if (register) Text("La primera vez que entres al servidor crearás tu personaje.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}
