package com.sampcity.social

import android.content.Context
import com.sampcity.social.data.Api
import com.sampcity.social.data.ApiError
import com.sampcity.social.data.Info
import com.sampcity.social.data.Me
import com.sampcity.social.data.NotifCount
import com.sampcity.social.data.Repo
import com.sampcity.social.live.NotifWorker
import com.sampcity.social.util.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data object Offline : AuthState
    data class In(val me: Me) : AuthState
}

/** Estado de la sesión y datos globales (quién soy, avisos sin leer, jugadores verificados). */
class Session(private val ctx: Context, private val repo: Repo, private val api: Api) {
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()
    private val _counts = MutableStateFlow(NotifCount())
    val counts: StateFlow<NotifCount> = _counts.asStateFlow()
    /** Mensaje para mostrar una vez (p. ej. «tu sesión caducó») */
    val notice = MutableStateFlow<String?>(null)

    val me: Me? get() = (state.value as? AuthState.In)?.me
    val isStaff get() = me?.canPost == true
    fun isVerified(name: String) = name in _info.value.verified

    /** Al abrir la app: datos públicos y, si hay cookie de sesión, quién soy. */
    suspend fun bootstrap() {
        runCatching { repo.info() }.onSuccess { _info.value = it; Clock.sync(it.now) }
        if (!api.hasSession) { _state.value = AuthState.LoggedOut; return }
        try {
            val m = repo.me()
            _state.value = if (m != null) AuthState.In(m) else AuthState.LoggedOut
            if (m != null) { refreshCounts(); com.sampcity.social.live.LiveService.sync(ctx) }
        } catch (e: ApiError) {
            // sin internet no se cierra la sesión: se muestra «sin conexión» y se puede reintentar
            _state.value = if (e.network) AuthState.Offline else AuthState.LoggedOut
        }
    }

    suspend fun login(name: String, password: String, code: String?) {
        repo.login(name.trim(), password, code)
        bootstrap()
    }

    suspend fun register(name: String, email: String, password: String) {
        repo.register(name.trim(), email.trim(), password)
        bootstrap()
    }

    suspend fun refreshMe() { runCatching { repo.me() }.getOrNull()?.let { _state.value = AuthState.In(it) } }
    suspend fun refreshCounts() { runCatching { repo.notifCount() }.onSuccess { _counts.value = it } }
    fun setCounts(c: NotifCount) { _counts.value = c }

    suspend fun logout() {
        repo.logout(); api.clearSession(); _counts.value = NotifCount(); _state.value = AuthState.LoggedOut
        NotifWorker.cancelAll(ctx); com.sampcity.social.live.LiveService.sync(ctx)
    }

    fun expired() {
        if (_state.value is AuthState.In) { api.clearSession(); _state.value = AuthState.LoggedOut; notice.value = "Tu sesión se cerró. Inicia sesión de nuevo." }
    }
}
