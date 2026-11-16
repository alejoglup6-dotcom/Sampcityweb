package com.sampcity.social.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sampcity.social.AuthState
import com.sampcity.social.R
import com.sampcity.social.SampApp
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.screens.AuthScreen
import kotlinx.coroutines.launch

@Composable
fun AppRoot(initialRoute: String?) {
    val app = SampApp.instance
    val auth by app.session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { app.session.bootstrap() }
    when (auth) {
        AuthState.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.size(140.dp))
                CircularProgressIndicator()
            }
        }
        AuthState.Offline -> ErrorBox("No hay conexión con SampCity. Revisa tu internet.", { scope.launch { app.session.bootstrap() } })
        AuthState.LoggedOut -> AuthScreen()
        is AuthState.In -> MainApp(initialRoute)
    }
}
