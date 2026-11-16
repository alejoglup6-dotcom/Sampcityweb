@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.sampcity.social.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.ChatMsg
import com.sampcity.social.data.MediaRef
import com.sampcity.social.data.RoomMember
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.MediaViewer
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.PlayerPickerDialog
import com.sampcity.social.ui.components.PromptDialog
import com.sampcity.social.ui.components.Reactions
import com.sampcity.social.ui.components.RichText
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import com.sampcity.social.util.Media
import com.sampcity.social.util.PlayerHub
import com.sampcity.social.util.Recorder
import com.sampcity.social.util.mmss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull

sealed interface ChatTarget {
    data class Player(val name: String) : ChatTarget
    data class Room(val id: Int) : ChatTarget
}

class ChatVM(val target: ChatTarget) : ViewModel() {
    private val app = SampApp.instance
    private val repo = app.repo
    val msgs = mutableStateListOf<ChatMsg>()
    var title by mutableStateOf(if (target is ChatTarget.Player) target.name.pretty() else "")
    var skin by mutableStateOf<String?>(null)
    var online by mutableStateOf(false)
    var members by mutableStateOf<List<RoomMember>>(emptyList())
    var isOwner by mutableStateOf(false)
    var seenTo by mutableStateOf(0)
    var typing by mutableStateOf(false)
    var blocked by mutableStateOf(false)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
    private var typingJob: Job? = null
    private val lastId get() = msgs.maxOfOrNull { it.id } ?: 0

    init {
        load()
        viewModelScope.launch { app.live.events.collect { handle(it.type, it.data) } }
    }

    private fun handle(type: String, d: kotlinx.serialization.json.JsonObject) {
        val t = target
        when (type) {
            "msg" -> if ((t is ChatTarget.Player && d["from"]?.jsonPrimitive?.contentOrNull == t.name) || (t is ChatTarget.Room && d["room"]?.jsonPrimitive?.intOrNull == t.id)) poll()
            "typing" -> if (t is ChatTarget.Player && d["from"]?.jsonPrimitive?.contentOrNull == t.name) {
                typing = true; typingJob?.cancel(); typingJob = viewModelScope.launch { delay(4500); typing = false }
            }
            "seen" -> if (t is ChatTarget.Player && d["by"]?.jsonPrimitive?.contentOrNull == t.name) poll()
            "rx" -> d["id"]?.jsonPrimitive?.intOrNull?.let { id ->
                val i = msgs.indexOfFirst { it.id == id }
                if (i >= 0) msgs[i] = msgs[i].copy(rx = d["rx"]?.jsonObject?.mapNotNull { (k, v) -> v.jsonPrimitive.intOrNull?.let { k to it } }?.toMap().orEmpty())
            }
            "del" -> d["id"]?.jsonPrimitive?.intOrNull?.let { id -> msgs.removeAll { it.id == id } }
        }
    }

    fun load() {
        viewModelScope.launch {
            loading = msgs.isEmpty(); error = null
            try { apply(fetch(null), replace = true) } catch (e: Exception) { error = e.userMessage() }
            loading = false
        }
    }

    fun poll() { viewModelScope.launch { runCatching { fetch(lastId) }.onSuccess { apply(it, false) } } }

    private suspend fun fetch(after: Int?): Pair<List<ChatMsg>, Unit> {
        when (val t = target) {
            is ChatTarget.Player -> repo.thread(t.name, after).let { r -> title = r.peer.name.pretty(); skin = r.peer.skin; online = r.peer.connected == 1; seenTo = r.seenTo; blocked = r.blocked; if (after != null) typing = r.typing; return r.msgs to Unit }
            is ChatTarget.Room -> repo.roomThread(t.id, after).let { r -> title = r.room.name; members = r.room.members; isOwner = r.room.owner; return r.msgs to Unit }
        }
    }
    private fun apply(r: Pair<List<ChatMsg>, Unit>, replace: Boolean) {
        if (replace) { msgs.clear(); msgs.addAll(r.first) }
        else { val have = msgs.map { it.id }.toHashSet(); r.first.filter { have.add(it.id) }.forEach { msgs.add(it) } }
    }

    fun refreshRoom() { if (target is ChatTarget.Room) viewModelScope.launch { runCatching { repo.roomThread(target.id) }.onSuccess { members = it.room.members; title = it.room.name; isOwner = it.room.owner } } }

    suspend fun send(body: String, data: String?, dur: Int?, reply: Int?) {
        sending = true
        try {
            val r = when (val t = target) { is ChatTarget.Player -> repo.sendMsg(t.name, body, data, dur, reply); is ChatTarget.Room -> repo.sendRoomMsg(t.id, body, data, dur, reply) }
            r.m?.let { m -> if (msgs.none { it.id == m.id }) msgs.add(m) }
        } finally { sending = false }
    }

    fun typingPing() { (target as? ChatTarget.Player)?.let { viewModelScope.launch { repo.typing(it.name) } } }
    fun react(m: ChatMsg, kind: Int) { viewModelScope.launch { runCatching { repo.reactMsg(m.id, kind) }.onSuccess { r -> val i = msgs.indexOfFirst { it.id == m.id }; if (i >= 0) msgs[i] = msgs[i].copy(rx = r.rx, myrx = r.myrx) } } }
    fun delete(m: ChatMsg) { viewModelScope.launch { runCatching { repo.deleteMsg(m.id) }.onSuccess { msgs.removeAll { it.id == m.id } } } }
}

class ChatVMFactory(private val t: ChatTarget) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatVM(t) as T
}

private sealed interface Row_ { data class M(val m: ChatMsg) : Row_; data class Day(val label: String, val key: String) : Row_ }

@Composable
fun ChatScreen(target: ChatTarget) {
    val vm: ChatVM = viewModel(key = target.toString(), factory = ChatVMFactory(target))
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val clipboard = LocalClipboardManager.current
    val app = SampApp.instance
    var replyTo by remember { mutableStateOf<ChatMsg?>(null) }
    var actions by remember { mutableStateOf<ChatMsg?>(null) }
    var viewer by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    val isPlayer = target is ChatTarget.Player
    val owner = LocalLifecycleOwner.current

    // respaldo: si la conexión en vivo falla, se consulta cada pocos segundos mientras la pantalla está a la vista
    LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { while (true) { delay(5000); vm.poll() } } }
    // al entrar se marcan como leídos (el servidor lo hace al pedir el hilo); se actualizan los contadores
    LaunchedEffect(vm.msgs.size) { app.session.refreshCounts() }

    val rows = remember(vm.msgs.toList()) {
        val out = ArrayList<Row_>(); var last = ""
        for (m in vm.msgs) { val k = Clock.dayKey(m.at); if (k != last) { out.add(Row_.Day(Clock.day(m.at), k)); last = k }; out.add(Row_.M(m)) }
        out.reversed()
    }
    val listState = rememberLazyListState()
    LaunchedEffect(vm.msgs.size) { if (vm.msgs.isNotEmpty()) listState.animateScrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (isPlayer) nav.profile((target as ChatTarget.Player).name) else info = true }) {
                        if (isPlayer) Avatar((target as ChatTarget.Player).name, vm.skin, 38.dp, online = vm.online)
                        else Box(Modifier.size(38.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        Column(Modifier.padding(start = 10.dp)) {
                            if (isPlayer) PlayerName(( target as ChatTarget.Player).name, style = MaterialTheme.typography.titleSmall) else Text(vm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (vm.typing) "escribiendo…" else if (isPlayer) (if (vm.online) "en línea" else "") else "${vm.members.size} miembros", style = MaterialTheme.typography.labelSmall, color = if (vm.typing || vm.online) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    Box {
                        IconButton({ if (isPlayer) menu = true else info = true }) { Icon(Icons.Filled.MoreVert, "Más") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Ver perfil") }, { menu = false; nav.profile((target as ChatTarget.Player).name) })
                            DropdownMenuItem({ Text("Borrar conversación") }, { menu = false; confirm = "clear" })
                            DropdownMenuItem({ Text("Reportar") }, { menu = false; confirm = "report" })
                            DropdownMenuItem({ Text("Bloquear") }, { menu = false; confirm = "block" })
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (vm.blocked) Surface(tonalElevation = 3.dp) { Text("No puedes escribirle a este jugador.", Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else ChatInput(vm, replyTo, { replyTo = null }, isPlayer)
        },
    ) { pad ->
        when {
            vm.loading -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            vm.error != null && vm.msgs.isEmpty() -> ErrorBox(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), state = listState, reverseLayout = true, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (vm.msgs.isEmpty()) item { Text("Di hola 👋", Modifier.fillMaxWidth().padding(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                items(rows, key = { when (it) { is Row_.M -> "m${it.m.id}"; is Row_.Day -> "d${it.key}" } }) { r ->
                    when (r) {
                        is Row_.Day -> Text(r.label, Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        is Row_.M -> Bubble(r.m, showSender = !isPlayer && !r.m.mine, seen = isPlayer && r.m.mine && r.m.id <= vm.seenTo, tickable = isPlayer, onActions = { actions = r.m }, onImage = { viewer = it }, onJump = { id -> scope.launch { val i = rows.indexOfFirst { it is Row_.M && it.m.id == id }; if (i >= 0) listState.animateScrollToItem(i) } })
                    }
                }
            }
        }
    }

    viewer?.let { MediaViewerUrl(it) { viewer = null } }
    actions?.let { m ->
        ModalBottomSheet({ actions = null }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (k in 1..6) Box(Modifier.size(46.dp).clip(CircleShape).background(if (m.myrx == k) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent).clickable { actions = null; vm.react(m, k) }, contentAlignment = Alignment.Center) { Text(Reactions.emoji[k], fontSize = 26.sp) }
                }
                SheetRow(Icons.Outlined.Reply, "Responder") { actions = null; replyTo = m }
                if (m.body.isNotBlank()) SheetRow(Icons.Outlined.ContentCopy, "Copiar") { actions = null; clipboard.setText(AnnotatedString(m.body)) }
                if (m.mine) SheetRow(Icons.Outlined.Delete, "Borrar", true) { actions = null; confirm = "del:${m.id}" } else SheetRow(Icons.Outlined.Flag, "Reportar", true) { actions = null; confirm = "msg:${m.id}" }
            }
        }
    }
    if (info && target is ChatTarget.Room) RoomInfoSheet(vm, target.id, onDismiss = { info = false }, onLeft = { nav.back() })
    val c = confirm
    when {
        c == "clear" -> ConfirmDialog("¿Borrar esta conversación?", "Solo se borra para ti.", "Borrar", true, { scope.launch { runCatching { app.repo.clearChat((target as ChatTarget.Player).name) }.onSuccess { vm.msgs.clear(); nav.back() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { confirm = null }
        c == "block" -> ConfirmDialog("¿Bloquear a ${(target as ChatTarget.Player).name.pretty()}?", "", "Bloquear", true, { scope.launch { runCatching { app.repo.block((target as ChatTarget.Player).name) }.onSuccess { nav.back() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { confirm = null }
        c == "report" -> ReportUserDialog((target as ChatTarget.Player).name) { confirm = null }
        c?.startsWith("del:") == true -> ConfirmDialog("¿Borrar este mensaje?", "", "Borrar", true, { vm.msgs.firstOrNull { it.id == c.removePrefix("del:").toInt() }?.let(vm::delete) }) { confirm = null }
        c?.startsWith("msg:") == true -> PromptDialog("Reportar mensaje", label = "¿Qué pasa? (opcional)", confirm = "Enviar", onConfirm = { note -> scope.launch { runCatching { app.repo.report("msg", c.removePrefix("msg:").toInt(), null, "otro", note) }.onSuccess { snack.showSnackbar("Gracias, el staff lo revisará") } } }, onDismiss = { confirm = null })
    }
}

@Composable private fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) =
    ListItem({ Text(text, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) }, leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = onClick))

@Composable
private fun MediaViewerUrl(url: String, onClose: () -> Unit) {
    // el visor de fotos usa ids de /media; las fotos del chat son /img/{id}: se muestra en un diálogo simple
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) { AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

@Composable
private fun Bubble(m: ChatMsg, showSender: Boolean, seen: Boolean, tickable: Boolean, onActions: () -> Unit, onImage: (String) -> Unit, onJump: (Int) -> Unit) {
    val api = SampApp.instance.api
    val c = MaterialTheme.colorScheme
    val mine = m.mine
    val bg = if (mine) c.primary else c.surfaceVariant
    val fg = if (mine) c.onPrimary else c.onSurface
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (showSender) Avatar(m.from ?: "?", m.skin, 28.dp, Modifier.padding(end = 6.dp))
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (mine) 18.dp else 4.dp, bottomEnd = if (mine) 4.dp else 18.dp)).background(bg).combinedClickable(onClick = onActions, onLongClick = onActions).padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (showSender && m.from != null) Text(m.from.pretty(), style = MaterialTheme.typography.labelMedium, color = c.secondary)
                m.reply?.let { r ->
                    Column(Modifier.padding(bottom = 4.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x33000000)).clickable(enabled = r.id != 0) { onJump(r.id) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(if (r.id == 0) "Mensaje eliminado" else r.from.pretty(), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .8f), fontWeight = FontWeight.Bold)
                        if (r.id != 0) Text(r.body, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = .85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (m.img.isNotBlank()) AsyncImage(api.imgUrl(m.img), null, contentScale = ContentScale.Crop, modifier = Modifier.padding(bottom = 4.dp).size(width = 220.dp, height = 220.dp).clip(RoundedCornerShape(10.dp)).clickable { onImage(api.imgUrl(m.img)) })
                if (m.aud.isNotBlank()) AudioBubble(m, mine)
                if (m.body.isNotBlank()) RichText(m.body, color = fg)
                Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    Text(Clock.clock(m.at), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = .7f))
                    if (mine && tickable) Text(if (seen) " ✓✓" else " ✓", style = MaterialTheme.typography.labelSmall, color = if (seen) Color(0xFFB9F6CA) else fg.copy(alpha = .7f))
                }
            }
            val kinds = m.rx.filter { it.value > 0 }
            if (kinds.isNotEmpty()) Text(kinds.keys.mapNotNull { it.toIntOrNull() }.sorted().joinToString("") { Reactions.emoji.getOrElse(it) { "" } } + (kinds.values.sum().let { if (it > 1) " $it" else "" }),
                Modifier.padding(top = 2.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 13.sp)
        }
    }
}

/** Reproductor de un audio: ▶/⏸, ondas con progreso (tocar para saltar), tiempo y velocidad. */
@Composable
private fun AudioBubble(m: ChatMsg, mine: Boolean) {
    val ctx = LocalContext.current
    val url = SampApp.instance.api.imgUrl(m.aud)
    val st by PlayerHub.state.collectAsStateWithLifecycle()
    val active = st.url == url
    val playing = active && st.playing
    val total = if (active && st.durationMs > 0) st.durationMs else m.dur * 1000L
    val frac = if (active && total > 0) (st.positionMs.toFloat() / total).coerceIn(0f, 1f) else 0f
    val bars = remember(m.id) { val r = java.util.Random(m.id.toLong()); var e = .5f; List(34) { e = (e + (r.nextFloat() - .5f) * .7f).coerceIn(.15f, 1f); e } }
    val fg = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val acc = if (mine) Color.White else MaterialTheme.colorScheme.secondary
    Row(Modifier.width(230.dp).padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (mine) Color.White else MaterialTheme.colorScheme.primary).clickable { PlayerHub.toggle(ctx, url) }, contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Pausa" else "Reproducir", tint = if (mine) MaterialTheme.colorScheme.primary else Color.White)
        }
        var w by remember { mutableStateOf(1) }
        Row(Modifier.padding(horizontal = 8.dp).weight(1f).height(34.dp).onSizeChanged { w = it.width }.pointerInput(url) { detectTapGestures { o -> PlayerHub.seekFraction(ctx, url, o.x / w.coerceAtLeast(1), m.dur * 1000L) } }, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEachIndexed { i, h -> Box(Modifier.weight(1f).fillMaxHeight(h).clip(CircleShape).background(if (i < frac * bars.size) acc else fg.copy(alpha = .4f))) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(mmss(if (active && st.positionMs > 0) (st.positionMs / 1000).toInt() else m.dur), style = MaterialTheme.typography.labelSmall, color = fg)
            if (active) Text("${if (st.speed == st.speed.toInt().toFloat()) st.speed.toInt().toString() else st.speed.toString()}×", style = MaterialTheme.typography.labelSmall, color = fg, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { PlayerHub.cycleSpeed() }.padding(horizontal = 4.dp))
        }
    }
}

@Composable
private fun ChatInput(vm: ChatVM, replyTo: ChatMsg?, clearReply: () -> Unit, isPlayer: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var text by remember { mutableStateOf("") }
    var lastTyping by remember { mutableStateOf(0L) }
    val recorder = remember { Recorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var secs by remember { mutableStateOf(0) }
    val levels = remember { mutableStateListOf<Float>().apply { repeat(30) { add(0f) } } }

    DisposableEffect(Unit) { onDispose { recorder.stop(true) } }
    LaunchedEffect(recording) {
        while (recording) {
            secs = recorder.seconds.toInt(); levels.add(recorder.level().coerceAtLeast(.08f)); levels.removeAt(0)
            if (!recorder.active) { recording = false; sendAudio(vm, recorder, replyTo, scope, snack, clearReply); break } // llegó al máximo de 60 s
            delay(70)
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) { if (recorder.start()) { recording = true; levels.replaceAll { 0f } } else scope.launch { snack.showSnackbar("No pude usar el micrófono") } } else scope.launch { snack.showSnackbar("Permite el micrófono para enviar audios") } }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            try { val b = withContext(Dispatchers.IO) { Media.compress(ctx, uri) }; vm.send(text.trim(), Media.dataUrl(b), null, replyTo?.id); text = ""; clearReply() }
            catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
        }
    }
    fun sendText() { val b = text.trim(); if (b.isEmpty() || vm.sending) return; scope.launch { try { vm.send(b, null, null, replyTo?.id); text = ""; clearReply() } catch (e: Exception) { snack.showSnackbar(e.userMessage()) } } }

    Surface(tonalElevation = 3.dp) {
        Column(Modifier.navigationBarsPadding().imePadding()) {
            if (replyTo != null && !recording) Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Respondiendo a ${if (replyTo.mine) "ti" else (replyTo.from ?: "").pretty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary); Text(replyTo.body.ifBlank { if (replyTo.aud.isNotBlank()) "🎤 Audio" else "📷 Foto" }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
                IconButton(clearReply, Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Cancelar", Modifier.size(18.dp)) }
            }
            if (recording) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ recorder.stop(true); recording = false }) { Icon(Icons.Filled.Delete, "Cancelar grabación", tint = MaterialTheme.colorScheme.error) }
                Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Text(" " + mmss(secs), Modifier.width(52.dp), color = if (secs >= 50) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                Row(Modifier.weight(1f).height(32.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    levels.forEach { l -> Box(Modifier.weight(1f).fillMaxHeight(l.coerceIn(.1f, 1f)).clip(CircleShape).background(MaterialTheme.colorScheme.secondary)) }
                }
                IconButton({ recording = false; sendAudio(vm, recorder, replyTo, scope, snack, clearReply) }, Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar audio", tint = MaterialTheme.colorScheme.onPrimary) }
            } else Row(Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                IconButton({ pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Outlined.AddPhotoAlternate, "Enviar foto") }
                OutlinedTextField(text, { if (it.length <= 1000) { text = it; val n = System.currentTimeMillis(); if (isPlayer && n - lastTyping > 3000) { lastTyping = n; vm.typingPing() } } }, Modifier.weight(1f), placeholder = { Text("Escribe un mensaje…") }, maxLines = 5, shape = RoundedCornerShape(24.dp))
                if (text.isBlank()) IconButton({ if (androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) { if (recorder.start()) { recording = true; levels.replaceAll { 0f } } else scope.launch { snack.showSnackbar("No pude usar el micrófono") } } else askMic.launch(Manifest.permission.RECORD_AUDIO) }) { Icon(Icons.Filled.Mic, "Grabar audio") }
                else IconButton(::sendText, enabled = !vm.sending) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

private fun sendAudio(vm: ChatVM, recorder: Recorder, replyTo: ChatMsg?, scope: kotlinx.coroutines.CoroutineScope, snack: androidx.compose.material3.SnackbarHostState, clearReply: () -> Unit) {
    val r = recorder.stop(false)
    if (r == null) { scope.launch { snack.showSnackbar("Mantén la grabación un poco más") }; return }
    val (file, dur) = r
    scope.launch {
        try {
            val bytes = withContext(Dispatchers.IO) { file.readBytes().also { file.delete() } }
            if (bytes.size > 880 * 1024) { snack.showSnackbar("Audio muy largo"); return@launch }
            vm.send("", Media.dataUrl(bytes, "audio/mp4"), dur, replyTo?.id); clearReply()
        } catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
    }
}

@Composable
private fun RoomInfoSheet(vm: ChatVM, id: Int, onDismiss: () -> Unit, onLeft: () -> Unit) {
    val repo = SampApp.instance.repo
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val me = SampApp.instance.session.me
    var rename by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    var leave by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(vm.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            Text("${vm.members.size} miembros", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            if (vm.isOwner) { SheetRow(Icons.Outlined.Reply, "Cambiar nombre") { rename = true }; SheetRow(Icons.Outlined.AddPhotoAlternate, "Agregar jugadores") { add = true } }
            vm.members.forEach { m ->
                ListItem({ Text(m.name.pretty()) }, supportingContent = { if (m.owner) Text("Creador") }, leadingContent = { Avatar(m.name, m.skin, 38.dp, online = m.connected == 1) },
                    trailingContent = { if (vm.isOwner && m.name != me?.name) TextButton({ scope.launch { runCatching { repo.leaveRoom(id, m.name) }.onSuccess { vm.refreshRoom() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { Text("Sacar") } },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
            SheetRow(Icons.Outlined.Delete, "Salir del grupo", true) { leave = true }
        }
    }
    if (rename) PromptDialog("Cambiar nombre", vm.title, "Nombre del grupo", maxLen = 40, onConfirm = { n -> scope.launch { runCatching { repo.renameRoom(id, n) }.onSuccess { vm.refreshRoom() }.onFailure { snack.showSnackbar(it.userMessage()) } } }, onDismiss = { rename = false })
    if (add) PlayerPickerDialog("Agregar al grupo", exclude = vm.members.map { it.name }.toSet(), onPick = { n -> scope.launch { runCatching { repo.addToRoom(id, listOf(n)) }.onSuccess { vm.refreshRoom(); snack.showSnackbar("${n.pretty()} agregado") }.onFailure { snack.showSnackbar(it.userMessage()) } } }, onDismiss = { add = false })
    if (leave) ConfirmDialog("¿Salir de este grupo?", "", "Salir", true, { scope.launch { runCatching { repo.leaveRoom(id, me?.name ?: "") }.onSuccess { onDismiss(); onLeft() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { leave = false }
}
