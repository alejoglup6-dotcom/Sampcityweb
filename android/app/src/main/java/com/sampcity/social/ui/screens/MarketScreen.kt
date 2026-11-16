@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sampcity.social.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.MarketItem
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.EmptyBox
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.OnEndReached
import com.sampcity.social.ui.components.Paged
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import com.sampcity.social.util.Media
import com.sampcity.social.util.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val MARKET_CATS = listOf("" to "Todos", "vehiculos" to "Vehículos", "propiedades" to "Propiedades", "negocios" to "Negocios", "objetos" to "Objetos")
private val SORTS = listOf("" to "Recientes", "barato" to "Más baratos", "caro" to "Más caros")

class MarketVM : ViewModel() {
    private val repo = SampApp.instance.repo
    var cat by mutableStateOf(""); var sort by mutableStateOf(""); var mine by mutableStateOf(false); var q by mutableStateOf("")
    val list = Paged<MarketItem>(viewModelScope, { it.id }, { it.size >= 12 }) { _, page -> repo.market(cat, sort, q, mine, page) }
    init { list.refresh(true) }
    fun reload() = list.refresh(true)
}

@Composable
fun MarketScreen(vm: MarketVM = viewModel()) {
    val nav = LocalNavigator.current
    val state = rememberLazyListState()
    var open by remember { mutableStateOf<MarketItem?>(null) }
    var create by remember { mutableStateOf<MarketItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Marketplace") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) },
        floatingActionButton = { FloatingActionButton({ creating = true }) { Icon(Icons.Filled.Add, "Nuevo anuncio") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!vm.mine, { vm.mine = false; vm.reload() }, { Text("Anuncios") }); FilterChip(vm.mine, { vm.mine = true; vm.reload() }, { Text("Mis anuncios") })
            }
            OutlinedTextField(vm.q, { vm.q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), singleLine = true, placeholder = { Text("Buscar anuncios…") }, keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { vm.reload() }))
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MARKET_CATS.forEach { (k, l) -> FilterChip(vm.cat == k, { vm.cat = k; vm.reload() }, { Text(l) }) }
                SORTS.forEach { (k, l) -> AssistChip({ vm.sort = k; vm.reload() }, { Text(l) }, colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = if (vm.sort == k) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)) }
            }
            PullToRefreshBox(vm.list.refreshing, { vm.list.refresh() }, Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        vm.list.loading && vm.list.items.isEmpty() -> item { LoadingBox(Modifier.fillMaxWidth().height(200.dp)) }
                        vm.list.error != null && vm.list.items.isEmpty() -> item { ErrorBox(vm.list.error!!, { vm.reload() }, Modifier.fillMaxWidth().height(240.dp)) }
                        vm.list.loaded && vm.list.items.isEmpty() -> item { EmptyBox(if (vm.mine) "Aún no has publicado anuncios." else "No hay anuncios con esos filtros.") }
                    }
                    items(vm.list.items, key = { it.id }) { m -> MarketCard(m) { open = m } }
                }
                OnEndReached(state) { vm.list.more() }
            }
        }
    }
    open?.let { m -> ListingSheet(m, onDismiss = { open = null }, onEdit = { create = m; open = null }, onChanged = { vm.reload() }) }
    if (creating || create != null) ListingDialog(create, onDismiss = { creating = false; create = null }, onDone = { vm.reload() })
}

@Composable
fun MarketCard(m: MarketItem, onClick: () -> Unit) {
    val api = SampApp.instance.api
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row {
            Box(Modifier.size(110.dp).background(MaterialTheme.colorScheme.surfaceVariant)) { if (m.img.isNotBlank()) AsyncImage(api.imgUrl(m.img), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            Column(Modifier.padding(12.dp).weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(money(m.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                Row(verticalAlignment = Alignment.CenterVertically) { Text(MARKET_CATS.firstOrNull { it.first == m.cat }?.second ?: m.cat, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); if (m.status == 2) Text("  · VENDIDO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
                Text("${m.seller.pretty()} · ${Clock.ago(m.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ListingSheet(m: MarketItem, onDismiss: () -> Unit, onEdit: () -> Unit, onChanged: () -> Unit) {
    val nav = LocalNavigator.current; val api = SampApp.instance.api; val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    val me = SampApp.instance.session.me
    var del by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (m.img.isNotBlank()) AsyncImage(api.imgUrl(m.img), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)))
            Text(m.title, style = MaterialTheme.typography.titleLarge)
            Text(money(m.price) + if (m.status == 2) "  ·  VENDIDO" else "", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            if (m.body.isNotBlank()) Text(m.body)
            Row(Modifier.clickable { onDismiss(); nav.profile(m.seller) }, verticalAlignment = Alignment.CenterVertically) { Avatar(m.seller, m.skin, 40.dp, online = m.connected == 1); Column(Modifier.padding(start = 10.dp)) { PlayerName(m.seller); Text("Publicado ${Clock.ago(m.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            Text("El trato y la entrega se hacen dentro del juego.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (m.mine) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onEdit) { Text("Editar") }
                OutlinedButton({ scope.launch { runCatching { repo.toggleSold(m.id) }.onSuccess { onChanged(); onDismiss() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { Text(if (m.status == 1) "Marcar vendido" else "Reactivar") }
                OutlinedButton({ del = true }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            } else Button({ onDismiss(); nav.chat(m.seller) }, Modifier.fillMaxWidth()) { Text("Escribir al vendedor") }
        }
    }
    if (del) ConfirmDialog("¿Eliminar el anuncio?", "", "Eliminar", true, { scope.launch { runCatching { repo.deleteListing(m.id) }.onSuccess { onChanged(); onDismiss() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { del = false }
}

@Composable
private fun ListingDialog(edit: MarketItem?, onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current; val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope(); val snack = LocalSnack.current
    var cat by remember { mutableStateOf(edit?.cat?.ifBlank { "objetos" } ?: "objetos") }
    var title by remember { mutableStateOf(edit?.title ?: "") }
    var price by remember { mutableStateOf(edit?.price?.toString() ?: "") }
    var body by remember { mutableStateOf(edit?.body ?: "") }
    var photo by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { photo = it ?: photo }
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (edit != null) "Editar anuncio" else "Nuevo anuncio") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MARKET_CATS.drop(1).forEach { (k, l) -> FilterChip(cat == k, { cat = k }, { Text(l) }) } }
            OutlinedTextField(title, { if (it.length <= 80) title = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Título") })
            OutlinedTextField(price, { if (it.length <= 10 && it.all(Char::isDigit)) price = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Precio ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(body, { if (it.length <= 600) body = it }, Modifier.fillMaxWidth(), minLines = 3, label = { Text("Descripción") })
            if (edit == null) { OutlinedButton({ pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.AddPhotoAlternate, null); Text(if (photo == null) "  Agregar foto" else "  Foto elegida ✔") } }
        }
    }, confirmButton = {
        TextButton({
            busy = true
            scope.launch {
                try {
                    val p = price.toLongOrNull() ?: 0
                    if (edit != null) repo.editListing(edit.id, cat, title.trim(), p, body.trim())
                    else repo.createListing(cat, title.trim(), p, body.trim(), photo?.let { u -> Media.dataUrl(withContext(Dispatchers.IO) { Media.compress(ctx, u, 1400, 850 * 1024) }) })
                    onDone(); onDismiss()
                } catch (e: Exception) { snack.showSnackbar(e.userMessage()); busy = false }
            }
        }, enabled = title.trim().length >= 3 && price.isNotEmpty() && !busy) { Text("Publicar") }
    }, dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancelar") } })
}
