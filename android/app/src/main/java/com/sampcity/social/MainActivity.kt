package com.sampcity.social

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sampcity.social.ui.AppRoot
import com.sampcity.social.ui.routeFromUrl
import com.sampcity.social.ui.theme.SampTheme

class MainActivity : ComponentActivity() {
    private var route by mutableStateOf<String?>(null)

    private val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        route = routeOf(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED && SampApp.instance.prefs.bgAlerts) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            SampTheme(SampApp.instance.themeMode.collectAsStateValue()) { AppRoot(route) }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); route = routeOf(intent) }

    override fun onStart() { super.onStart(); SampApp.instance.live.start() }
    override fun onStop() { super.onStop(); SampApp.instance.live.stop() }

    private fun routeOf(i: Intent?): String? = i?.getStringExtra("route") ?: i?.data?.let { routeFromUrl(it) }
}

@androidx.compose.runtime.Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): T = this.collectAsStateWithLifecycle().value
