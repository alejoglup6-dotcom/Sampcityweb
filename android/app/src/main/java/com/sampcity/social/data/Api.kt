package com.sampcity.social.data

import android.content.SharedPreferences
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

val J = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false; encodeDefaults = true }

/** Error de la API: `message` es el texto que manda el servidor ({"error": "…"}) o uno genérico si no hubo respuesta. */
class ApiError(val status: Int, override val message: String, val body: JsonObject? = null) : Exception(message) {
    val unauthorized get() = status == 401
    val network get() = status == 0
}

/** Cookie de sesión del cliente: en la app se guarda en el teléfono; en las pruebas, en memoria. */
interface SessionJar : CookieJar { val hasSession: Boolean; fun clear() }

/** Solo en memoria (pruebas) */
class MemoryJar : SessionJar {
    private val store = LinkedHashMap<String, Cookie>()
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { for (c in cookies) { if (c.expiresAt < System.currentTimeMillis() || c.value.isEmpty()) store.remove(c.name) else store[c.name] = c } }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = store.values.filter { it.matches(url) }
    @Synchronized override fun clear() = store.clear()
    override val hasSession get() = synchronized(this) { store.values.any { it.name == "sc" && it.expiresAt > System.currentTimeMillis() } }
}

/** Guarda las cookies (la sesión `sc`) para que siga abierta al cerrar la app. */
class PersistentCookieJar(private val prefs: SharedPreferences) : SessionJar {
    private val store = LinkedHashMap<String, Cookie>()
    init {
        prefs.getStringSet("cookies", emptySet())?.forEach { line ->
            val sep = line.indexOf('\n')
            if (sep > 0) Cookie.parse(line.substring(0, sep).toHttpUrl(), line.substring(sep + 1))?.let { store[key(it)] = it }
        }
    }
    private fun key(c: Cookie) = "${c.domain}|${c.path}|${c.name}"
    @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (c in cookies) { if (c.expiresAt < System.currentTimeMillis() || c.value.isEmpty()) store.remove(key(c)) else store[key(c)] = c }
        prefs.edit().putStringSet("cookies", store.values.filter { it.persistent }.map { "${HttpUrl.Builder().scheme(if (it.secure) "https" else "http").host(it.domain).build()}\n$it" }.toSet()).apply()
    }
    @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.entries.removeAll { it.value.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }
    @Synchronized override fun clear() { store.clear(); prefs.edit().remove("cookies").apply() }
    override val hasSession get() = synchronized(this) { store.values.any { it.name == "sc" && it.expiresAt > System.currentTimeMillis() } }
}

class Api(val baseUrl: String, private val jar: SessionJar, extra: (OkHttpClient.Builder) -> Unit = {}) {
    val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { c -> c.proceed(c.request().newBuilder().header("User-Agent", "SampCityApp/${com.sampcity.social.BuildConfig.VERSION_NAME} (Android)").build()) }
        .also(extra).build()

    /** Se llama cuando el servidor dice 401 con una sesión que creíamos abierta (cookie vencida o cerrada desde otro dispositivo). */
    var onUnauthorized: () -> Unit = {}

    val hasSession get() = jar.hasSession
    fun clearSession() = jar.clear()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    fun url(path: String): String = if (path.startsWith("http")) path else baseUrl + path
    fun mediaUrl(id: Int) = "$baseUrl/media/$id"
    fun imgUrl(pathOrId: String) = if (pathOrId.startsWith("/")) baseUrl + pathOrId else pathOrId
    fun avatarUrl(name: String, skin: String?) = "$baseUrl/av/${java.net.URLEncoder.encode(name, "UTF-8")}?s=${skin ?: ""}"

    private suspend fun exec(req: Request): Response = suspendCancellableCoroutine { cont: CancellableContinuation<Response> ->
        val call = client.newCall(req)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (cont.isActive) cont.resumeWith(Result.failure(ApiError(0, if (e is java.net.SocketTimeoutException) "El servidor tardó demasiado en responder" else "Sin conexión con el servidor"))) }
            override fun onResponse(call: Call, response: Response) { cont.resumeWith(Result.success(response)) }
        })
    }

    private suspend fun finish(resp: Response, notifyAuth: Boolean): JsonElement = withContext(Dispatchers.IO) {
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            val el = runCatching { J.parseToJsonElement(text) }.getOrNull()
            if (!r.isSuccessful) {
                val obj = el as? JsonObject
                val msg = (obj?.get("error") as? JsonPrimitive)?.contentOrNull ?: when (r.code) {
                    429 -> "Demasiadas peticiones. Espera un momento."; in 500..599 -> "El servidor tuvo un problema. Inténtalo de nuevo."; else -> "No se pudo completar (${r.code})"
                }
                if (r.code == 401 && notifyAuth && jar.hasSession) onUnauthorized()
                throw ApiError(r.code, msg, obj)
            }
            el ?: throw ApiError(r.code, "Respuesta no válida del servidor")
        }
    }

    suspend fun request(method: String, path: String, body: JsonElement? = null, notifyAuth: Boolean = true): JsonElement {
        val rb: RequestBody? = if (method == "GET" || method == "HEAD") null else (body ?: JsonObject(emptyMap())).toString().toRequestBody(JSON)
        val req = Request.Builder().url(url("/api$path")).header("Accept", "application/json").method(method, rb).build()
        return finish(exec(req), notifyAuth)
    }

    suspend inline fun <reified T> get(path: String): T = J.decodeFromJsonElement<T>(request("GET", path))
    suspend inline fun <reified T> post(path: String, body: JsonElement? = null): T = J.decodeFromJsonElement<T>(request("POST", path, body))
    suspend inline fun <reified T> put(path: String, body: JsonElement? = null): T = J.decodeFromJsonElement<T>(request("PUT", path, body))
    suspend inline fun <reified T> delete(path: String, body: JsonElement? = null): T = J.decodeFromJsonElement<T>(request("DELETE", path, body))
    suspend fun send(method: String, path: String, body: JsonElement? = null) { request(method, path, body) }

    /** Subida de un archivo a /api/media (fotos y videos de publicaciones, historias y reels). */
    suspend fun upload(kind: String, bytes: ByteArray, mime: String, thumbId: Int? = null): Int {
        val q = "kind=$kind" + (thumbId?.let { "&thumb=$it" } ?: "")
        val req = Request.Builder().url(url("/api/media?$q")).header("X-SC", "1").post(bytes.toRequestBody(mime.toMediaType())).build()
        val el = finish(exec(req), true)
        return el.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: throw ApiError(0, "El servidor no devolvió el archivo subido")
    }

    /** Descarga de un archivo cualquiera del servidor con la sesión (para exportar datos). */
    suspend fun download(path: String): ByteArray {
        val resp = exec(Request.Builder().url(url(path)).build())
        return withContext(Dispatchers.IO) { resp.use { if (!it.isSuccessful) throw ApiError(it.code, "No se pudo descargar (${it.code})"); it.body!!.bytes() } }
    }
}

fun obj(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    for ((k, v) in pairs) when (v) {
        null -> {}
        is String -> put(k, JsonPrimitive(v))
        is Boolean -> put(k, JsonPrimitive(v))
        is Int -> put(k, JsonPrimitive(v))
        is Long -> put(k, JsonPrimitive(v))
        is Double -> put(k, JsonPrimitive(v))
        is JsonElement -> put(k, v)
        is List<*> -> put(k, kotlinx.serialization.json.JsonArray(v.map { if (it is Number) JsonPrimitive(it) else if (it is JsonElement) it else JsonPrimitive(it.toString()) }))
        else -> put(k, JsonPrimitive(v.toString()))
    }
}
