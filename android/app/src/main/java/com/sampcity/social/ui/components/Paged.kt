package com.sampcity.social.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * Lista con carga por páginas y desplazamiento infinito.
 * fetch(último elemento, número de página) devuelve la página; `hasMore` decide si hay otra.
 */
class Paged<T>(
    private val scope: CoroutineScope,
    private val key: (T) -> Any,
    private val hasMore: (List<T>) -> Boolean,
    private val trim: (List<T>) -> List<T> = { it },
    private val fetch: suspend (last: T?, page: Int) -> List<T>,
) {
    val items = mutableStateListOf<T>()
    var loading by mutableStateOf(false); private set
    var refreshing by mutableStateOf(false); private set
    var loadingMore by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var end by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    private var page = 0
    private var job: Job? = null

    fun refresh(initial: Boolean = false) {
        job?.cancel()
        job = scope.launch {
            if (initial || items.isEmpty()) loading = true else refreshing = true
            error = null
            try {
                val r = fetch(null, 0)
                items.clear(); items.addAll(trim(r)); page = 0; end = !hasMore(r); loaded = true
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.userMessage() }
            loading = false; refreshing = false
        }
    }

    fun more() {
        if (end || loading || refreshing || loadingMore || items.isEmpty()) return
        loadingMore = true
        scope.launch {
            try {
                val r = fetch(items.lastOrNull(), page + 1)
                val seen = items.map(key).toHashSet()
                items.addAll(trim(r).filter { seen.add(key(it)) }); page++; end = !hasMore(r)
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; error = e.userMessage(); end = true }
            loadingMore = false
        }
    }

    fun update(old: (T) -> Boolean, new: T) { val i = items.indexOfFirst(old); if (i >= 0) items[i] = new }
    fun remove(pred: (T) -> Boolean) { items.removeAll(pred) }
}

/** Pide más elementos cuando el usuario se acerca al final de la lista. */
@Composable
fun OnEndReached(state: LazyListState, buffer: Int = 4, onEnd: () -> Unit) {
    LaunchedEffect(state) {
        snapshotFlow { val l = state.layoutInfo; (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - buffer && l.totalItemsCount > 0 }
            .distinctUntilChanged().filter { it }.collect { onEnd() }
    }
}
