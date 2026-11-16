package com.sampcity.social

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.sampcity.social.data.Api
import com.sampcity.social.data.PersistentCookieJar
import com.sampcity.social.data.Repo
import com.sampcity.social.live.Live
import com.sampcity.social.live.NotifWorker
import com.sampcity.social.util.Prefs

class SampApp : Application(), ImageLoaderFactory {
    lateinit var api: Api; private set
    lateinit var repo: Repo; private set
    lateinit var session: Session; private set
    lateinit var live: Live; private set
    lateinit var prefs: Prefs; private set
    val themeMode = kotlinx.coroutines.flow.MutableStateFlow("system")
    /** avisos internos entre pantallas: "post-created", "profile-changed"… */
    val bus = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        themeMode.value = prefs.theme
        api = Api(System.getProperty("sampcity.api") ?: BuildConfig.BASE_URL, PersistentCookieJar(getSharedPreferences("session", Context.MODE_PRIVATE)))
        repo = Repo(api)
        session = Session(this, repo, api)
        live = Live(api, session)
        api.onUnauthorized = { session.expired() }
        createChannels()
        runCatching { NotifWorker.schedule(this, prefs.bgAlerts) }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_SOCIAL, "Avisos de la red social", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Me gusta, comentarios, menciones y amistades" })
        nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "Conexión en vivo", NotificationManager.IMPORTANCE_MIN).apply { description = "Notificación fija que mantiene los avisos al instante. Puedes ocultarla sin perder los avisos." })
        nm.createNotificationChannel(NotificationChannel(CH_CHAT, "Mensajes", NotificationManager.IMPORTANCE_HIGH).apply { description = "Mensajes privados y de grupo" })
    }

    // Coil comparte el cliente HTTP de la API: las fotos privadas (chats, «solo amigos») se piden con la sesión
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(api.client)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .crossfade(true).build()

    companion object {
        const val CH_SOCIAL = "social"; const val CH_CHAT = "chat"; const val CH_SERVICE = "service"
        lateinit var instance: SampApp; private set
    }
}
