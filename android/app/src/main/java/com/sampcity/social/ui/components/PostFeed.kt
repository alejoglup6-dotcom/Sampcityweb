@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sampcity.social.data.Post

/** Lista de publicaciones con «tirar para actualizar», carga infinita y estados de carga, error y vacío. */
@Composable
fun PostFeed(
    paged: Paged<Post>, host: PostHost, modifier: Modifier = Modifier, state: LazyListState = rememberLazyListState(),
    empty: String = "Aún no hay publicaciones.", contentPadding: PaddingValues = PaddingValues(0.dp), header: (LazyListScope.() -> Unit)? = null,
) {
    PullToRefreshBox(isRefreshing = paged.refreshing, onRefresh = { paged.refresh() }, modifier = modifier) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            header?.invoke(this)
            when {
                paged.loading && paged.items.isEmpty() -> item { LoadingBox(Modifier.fillMaxWidth().height(220.dp)) }
                paged.error != null && paged.items.isEmpty() -> item { ErrorBox(paged.error!!, { paged.refresh(true) }, Modifier.fillMaxWidth().height(260.dp)) }
                paged.loaded && paged.items.isEmpty() -> item { EmptyBox(empty) }
            }
            items(paged.items, key = { it.id }) { p -> PostCard(p, host) }
            if (paged.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.height(28.dp)) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
        OnEndReached(state) { paged.more() }
    }
}
