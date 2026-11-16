package com.sampcity.social.live

import com.sampcity.social.AuthState
import com.sampcity.social.Session
import com.sampcity.social.data.Api
import com.sampcity.social.data.J
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

data class LiveEvent(val type: String, val data: JsonObject)

/**
 * Conexión en vivo con el servidor (Server-Sent Events, /api/stream): mensajes, «escribiendo», «visto», reacciones y avisos llegan al instante.
 * Solo está abierta con la app a la vista; en segundo plano lo cubre NotifWorker.
 */
class Live(private val api: Api, private val session: Session) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<LiveEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<LiveEvent> = _events.asSharedFlow()
    private var source: EventSource? = null
    private var job: Job? = null
    /** la app está a la vista */
    @Volatile var foreground = false; private set
    /** el servicio de avisos la mantiene abierta aunque la app esté cerrada */
    @Volatile private var keep = false

    private val client by lazy { api.client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).retryOnConnectionFailure(true).build() }

    fun start() { foreground = true; ensure() }
    fun stop() { foreground = false; ensure() }
    fun keepAlive(on: Boolean) { keep = on; ensure() }

    private fun ensure() {
        if (!foreground && !keep) { job?.cancel(); job = null; source?.cancel(); source = null; return }
        if (job?.isActive == true) return
        job = scope.launch {
            var wait = 2000L
            while (isActive) {
                if (session.state.value is AuthState.In) {
                    val ok = runOnce()
                    wait = if (ok) 2000L else (wait * 2).coerceAtMost(60000L)
                }
                delay(wait)
            }
        }
    }

    /** Abre la conexión y espera a que se cierre. true si llegó a conectar. */
    private suspend fun runOnce(): Boolean {
        val done = kotlinx.coroutines.CompletableDeferred<Boolean>()
        var opened = false
        val req = Request.Builder().url(api.url("/api/stream")).header("Accept", "text/event-stream").build()
        source = EventSources.createFactory(client).newEventSource(req, object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) { opened = true }
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val obj = runCatching { J.parseToJsonElement(data).jsonObject }.getOrDefault(JsonObject(emptyMap()))
                _events.tryEmit(LiveEvent(type ?: "message", obj))
                if (type == "notif" || type == "msg") scope.launch { session.refreshCounts() }
            }
            override fun onClosed(eventSource: EventSource) { done.complete(opened) }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                if (response?.code == 401) session.expired()
                done.complete(opened)
            }
        })
        return try { done.await() } finally { source?.cancel(); source = null }
    }
}
