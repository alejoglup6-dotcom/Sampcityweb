@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Reply
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.foundation.gestures.detectTransformGestures
import coil.compose.AsyncImage
import com.sampcity.social.SampApp
import com.sampcity.social.data.MediaRef
import com.sampcity.social.data.Me
import com.sampcity.social.data.Post
import com.sampcity.social.data.PreviewData
import com.sampcity.social.data.Reactor
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.Navigator
import com.sampcity.social.ui.openWeb
import com.sampcity.social.util.Clock
import com.sampcity.social.util.count
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object Reactions {
    val emoji = listOf("", "👍", "❤️", "😂", "😮", "😢", "😡")
    val names = listOf("", "Me gusta", "Me encanta", "Me divierte", "Me asombra", "Me entristece", "Me enoja")
}

/** Texto con enlaces, @menciones y #etiquetas que se pueden tocar. */
@Composable
fun RichText(text: String, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyMedium, color: Color = MaterialTheme.colorScheme.onSurface, maxLines: Int = Int.MAX_VALUE) {
    val nav = LocalNavigator.current
    val ctx = LocalContext.current
    val accent = MaterialTheme.colorScheme.secondary
    val annotated = remember(text, accent) {
        buildAnnotatedString {
            val rx = Regex("(https?://[^\\s<]+)|(?<![\\w@])@(\\w{1,24})|(?<![\\w#])#([\\p{L}\\d_]{2,30})")
            var last = 0
            val link = TextLinkStyles(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold))
            for (m in rx.findAll(text)) {
                append(text.substring(last, m.range.first)); last = m.range.last + 1
                val full = m.value
                when {
                    m.groups[1] != null -> withLink(LinkAnnotation.Clickable("url", TextLinkStyles(SpanStyle(color = accent, textDecoration = TextDecoration.Underline))) { openWeb(ctx, full) }) { append(full) }
                    m.groups[2] != null -> withLink(LinkAnnotation.Clickable("m", link) { nav.profile(m.groupValues[2]) }) { append(full) }
                    else -> withLink(LinkAnnotation.Clickable("h", link) { nav.search("#" + m.groupValues[3]) }) { append(full) }
                }
            }
            append(text.substring(last))
        }
    }
    Text(annotated, modifier, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

// ---- Fotos de una publicación ----
@Composable
fun MediaGrid(media: List<MediaRef>, modifier: Modifier = Modifier, onOpen: (Int) -> Unit) {
    val api = SampApp.instance.api
    val shown = media.take(4)
    @Composable fun cell(i: Int, m: Modifier) {
        Box(m.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onOpen(i) }) {
            AsyncImage(api.mediaUrl(shown[i].previewId), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (shown[i].isVideo) Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(44.dp).background(Color(0x99000000), CircleShape))
            if (i == 3 && media.size > 4) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) { Text("+${media.size - 4}", color = Color.White, style = MaterialTheme.typography.titleLarge) }
        }
    }
    val gap = 3.dp
    when (shown.size) {
        0 -> {}
        1 -> cell(0, modifier.fillMaxWidth().aspectRatio(4f / 3f))
        2 -> Row(modifier.fillMaxWidth().height(200.dp), horizontalArrangement = Arrangement.spacedBy(gap)) { cell(0, Modifier.weight(1f).fillMaxSize()); cell(1, Modifier.weight(1f).fillMaxSize()) }
        3 -> Row(modifier.fillMaxWidth().height(240.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            cell(0, Modifier.weight(1f).fillMaxSize())
            Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) { cell(1, Modifier.weight(1f).fillMaxWidth()); cell(2, Modifier.weight(1f).fillMaxWidth()) }
        }
        else -> Column(modifier.fillMaxWidth().height(260.dp), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) { cell(0, Modifier.weight(1f).fillMaxSize()); cell(1, Modifier.weight(1f).fillMaxSize()) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) { cell(2, Modifier.weight(1f).fillMaxSize()); cell(3, Modifier.weight(1f).fillMaxSize()) }
        }
    }
}

/** Visor de fotos a pantalla completa: se desliza entre ellas y se amplía con dos dedos. */
@Composable
fun MediaViewer(media: List<MediaRef>, start: Int, onClose: () -> Unit) {
    val api = SampApp.instance.api
    val pager = rememberPagerState(start) { media.size }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(pager, Modifier.fillMaxSize()) { i ->
                var scale by remember { mutableStateOf(1f) }
                var off by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f); off = if (scale > 1f) off + pan else androidx.compose.ui.geometry.Offset.Zero } }, contentAlignment = Alignment.Center) {
                    AsyncImage(api.mediaUrl(media[i].previewId), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y })
                }
            }
            IconButton(onClose, Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 8.dp)) { Icon(Icons.Filled.Close, "Cerrar", tint = Color.White) }
            if (media.size > 1) Text("${pager.currentPage + 1} / ${media.size}", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp))
        }
    }
}

// ---- Vista previa del primer enlace ----
private val previewCache = HashMap<String, PreviewData?>()

@Composable
fun LinkPreview(url: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val data by produceState<PreviewData?>(previewCache[url], url) {
        if (!previewCache.containsKey(url)) value = runCatching { SampApp.instance.repo.preview(url) }.getOrNull()?.takeIf { it.title.isNotBlank() }.also { previewCache[url] = it }
    }
    val d = data ?: return
    Card(modifier.fillMaxWidth().clickable { openWeb(ctx, url) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.heightIn(min = 72.dp)) {
            if (d.img.isNotBlank()) AsyncImage(d.img, null, contentScale = ContentScale.Crop, modifier = Modifier.width(92.dp).height(80.dp))
            Column(Modifier.padding(10.dp)) {
                Text(d.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (d.descr.isNotBlank()) Text(d.descr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---- Acciones sobre publicaciones (las comparten el feed, el perfil, los grupos, la búsqueda y el detalle) ----
class PostHost(
    val scope: CoroutineScope, val ctx: Context, val snack: SnackbarHostState, val nav: Navigator,
    var onUpdate: (Post) -> Unit, var onRemove: (Int) -> Unit,
) {
    private val repo get() = SampApp.instance.repo
    val me: Me? get() = SampApp.instance.session.me
    var menu by mutableStateOf<Post?>(null)
    var edit by mutableStateOf<Post?>(null)
    var report by mutableStateOf<Post?>(null)
    var share by mutableStateOf<Post?>(null)
    var reactors by mutableStateOf<Post?>(null)
    var confirmDelete by mutableStateOf<Post?>(null)
    var viewer by mutableStateOf<Pair<List<MediaRef>, Int>?>(null)

    fun toast(msg: String) { scope.launch { snack.showSnackbar(msg) } }
    private fun fail(e: Throwable) = toast(e.userMessage())
    fun link(p: Post) = SampApp.instance.api.baseUrl + "/p/" + p.id

    fun react(p: Post, kind: Int) {
        val nm = if (p.mine == kind) 0 else kind
        val d = when { p.mine == 0 -> 1; nm == 0 -> -1; else -> 0 }
        onUpdate(p.copy(mine = nm, likes = (p.likes + d).coerceAtLeast(0)))
        scope.launch { runCatching { repo.react(p.id, kind) }.onSuccess { onUpdate(p.copy(mine = it.mine, likes = it.likes, rk = it.rk.ifBlank { null })) }.onFailure { onUpdate(p); fail(it) } }
    }
    fun vote(p: Post, opt: Int) { scope.launch { runCatching { repo.vote(p.id, opt) }.onSuccess { onUpdate(p.copy(poll = it)) }.onFailure(::fail) } }
    fun save(p: Post) { scope.launch { runCatching { repo.savePost(p.id) }.onSuccess { toast(if (it.saved) "Guardada" else "Quitada de guardados") }.onFailure(::fail) } }
    fun hide(p: Post) { scope.launch { runCatching { repo.hidePost(p.id) }.onSuccess { onRemove(p.id); toast("Publicación oculta") }.onFailure(::fail) } }
    fun pin(p: Post) { scope.launch { runCatching { repo.pinPost(p.id) }.onSuccess { toast("Listo: se actualizó tu publicación fijada") }.onFailure(::fail) } }
    fun delete(p: Post) { scope.launch { runCatching { repo.deletePost(p.id) }.onSuccess { onRemove(p.id); toast("Publicación eliminada") }.onFailure(::fail) } }
    fun saveEdit(p: Post, body: String, vis: Int) { scope.launch { runCatching { repo.editPost(p.id, body, vis) }.onSuccess { onUpdate(p.copy(body = body, vis = vis, editedAt = "ahora")) }.onFailure(::fail) } }
    fun sendReport(p: Post, reason: String, note: String) { scope.launch { runCatching { repo.report("post", p.id, null, reason, note) }.onSuccess { toast("Gracias, el staff lo revisará") }.onFailure(::fail) } }
    fun block(name: String) { scope.launch { runCatching { repo.block(name) }.onSuccess { toast("Bloqueaste a ${name.pretty()}") }.onFailure(::fail) } }
    fun shareToProfile(p: Post) { scope.launch { runCatching { repo.createPost("", emptyList(), emptyList(), 0, emptyList(), share = p.id) }.onSuccess { toast("Compartida en tu perfil") }.onFailure(::fail) } }
    fun copyLink(p: Post) { (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("enlace", link(p))); toast("Enlace copiado") }
    fun sendLink(p: Post) { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link(p)), "Compartir").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
fun rememberPostHost(onUpdate: (Post) -> Unit, onRemove: (Int) -> Unit = {}): PostHost {
    val scope = rememberCoroutineScope(); val ctx = LocalContext.current; val snack = LocalSnack.current; val nav = LocalNavigator.current
    val h = remember { PostHost(scope, ctx, snack, nav, onUpdate, onRemove) }
    h.onUpdate = onUpdate; h.onRemove = onRemove
    return h
}

private val REASONS = listOf("spam" to "Spam", "acoso" to "Acoso o insultos", "contenido" to "Contenido inapropiado", "estafa" to "Estafa", "suplantacion" to "Suplantación", "otro" to "Otro")

/** Ventanas de las acciones: se coloca una vez por pantalla. */
@Composable
fun PostHostDialogs(h: PostHost) {
    h.viewer?.let { (m, i) -> MediaViewer(m, i) { h.viewer = null } }
    h.menu?.let { p -> PostMenuSheet(h, p) { h.menu = null } }
    h.edit?.let { p ->
        var text by remember(p.id) { mutableStateOf(p.body) }; var vis by remember(p.id) { mutableStateOf(p.vis) }
        androidx.compose.material3.AlertDialog(onDismissRequest = { h.edit = null }, title = { Text("Editar publicación") }, text = {
            Column {
                OutlinedTextField(text, { if (it.length <= 1000) text = it }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Público", "Amigos", "Solo yo").forEachIndexed { i, l -> FilterChip(vis == i, { vis = i }, { Text(l) }) } }
            }
        }, confirmButton = { TextButton({ h.saveEdit(p, text.trim(), vis); h.edit = null }) { Text("Guardar") } }, dismissButton = { TextButton({ h.edit = null }) { Text("Cancelar") } })
    }
    h.report?.let { p ->
        var reason by remember { mutableStateOf("spam") }; var note by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(onDismissRequest = { h.report = null }, title = { Text("Reportar publicación") }, text = {
            Column {
                REASONS.forEach { (k, l) -> Row(Modifier.fillMaxWidth().clickable { reason = k }, verticalAlignment = Alignment.CenterVertically) { RadioButton(reason == k, { reason = k }); Text(l) } }
                OutlinedTextField(note, { if (it.length <= 300) note = it }, label = { Text("Detalle (opcional)") }, modifier = Modifier.fillMaxWidth())
            }
        }, confirmButton = { TextButton({ h.sendReport(p, reason, note); h.report = null }) { Text("Enviar") } }, dismissButton = { TextButton({ h.report = null }) { Text("Cancelar") } })
    }
    h.confirmDelete?.let { p -> ConfirmDialog("¿Eliminar esta publicación?", "No se puede deshacer.", "Eliminar", true, { h.delete(p) }) { h.confirmDelete = null } }
    h.share?.let { p -> ShareSheet(h, p) { h.share = null } }
    h.reactors?.let { p -> ReactorsSheet(h, p) { h.reactors = null } }
}

@Composable
private fun SheetItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, danger: Boolean = false, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text, color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified) },
        leadingContent = { Icon(icon, null, tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun PostMenuSheet(h: PostHost, p: Post, onDismiss: () -> Unit) {
    val own = h.me?.name == p.author; val staff = h.me?.canPost == true
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(bottom = 24.dp)) {
            SheetItem(Icons.Outlined.BookmarkBorder, "Guardar o quitar de guardados") { onDismiss(); h.save(p) }
            if (p.likes > 0) SheetItem(Icons.Outlined.ThumbUp, "Ver quién reaccionó") { onDismiss(); h.reactors = p }
            SheetItem(Icons.Outlined.ContentCopy, "Copiar enlace") { onDismiss(); h.copyLink(p) }
            SheetItem(Icons.Outlined.Share, "Compartir…") { onDismiss(); h.share = p }
            if (own && p.type == "post") { SheetItem(Icons.Outlined.Edit, "Editar") { onDismiss(); h.edit = p }; SheetItem(Icons.Filled.PushPin, "Fijar o desfijar en mi perfil") { onDismiss(); h.pin(p) } }
            if (own || staff) SheetItem(Icons.Outlined.Delete, "Eliminar", true) { onDismiss(); h.confirmDelete = p }
            if (!own) {
                SheetItem(Icons.Outlined.VisibilityOff, "Ocultar publicación") { onDismiss(); h.hide(p) }
                SheetItem(Icons.Outlined.Flag, "Reportar", true) { onDismiss(); h.report = p }
                SheetItem(Icons.Outlined.Block, "Bloquear a ${p.author.pretty()}", true) { onDismiss(); h.block(p.author) }
            }
        }
    }
}

@Composable
private fun ShareSheet(h: PostHost, p: Post, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            if (p.vis == 0 && p.groupId == null) SheetItem(Icons.Outlined.Reply, "Compartir en mi perfil") { onDismiss(); h.shareToProfile(p) }
            SheetItem(Icons.Outlined.Share, "Enviar enlace por otra app") { onDismiss(); h.sendLink(p) }
            SheetItem(Icons.Outlined.ContentCopy, "Copiar enlace") { onDismiss(); h.copyLink(p) }
        }
    }
}

@Composable
private fun ReactorsSheet(h: PostHost, p: Post, onDismiss: () -> Unit) {
    val list by produceState<List<Reactor>?>(null, p.id) { value = runCatching { SampApp.instance.repo.reactors(p.id) }.getOrDefault(emptyList()) }
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Reacciones", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            if (list == null) LoadingBox(Modifier.fillMaxWidth().height(120.dp))
            list.orEmpty().forEach { r ->
                ListItem(headlineContent = { Text(r.name.pretty()) }, leadingContent = { Avatar(r.name, r.skin, 36.dp) }, trailingContent = { Text(Reactions.emoji.getOrElse(r.kind) { "👍" }, fontSize = 20.sp) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent), modifier = Modifier.clickable { onDismiss(); h.nav.profile(r.name) })
            }
        }
    }
}

// ---- Tarjeta de una publicación ----
@Composable
fun PostCard(p: Post, h: PostHost, modifier: Modifier = Modifier, detail: Boolean = false) {
    val nav = LocalNavigator.current
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().background(c.surface).padding(vertical = 12.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.author, p.skin, 42.dp, onClick = { nav.profile(p.author) })
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerName(p.author, Modifier.clickable { nav.profile(p.author) })
                    if (p.tags.isNotEmpty()) Text(" con ${p.tags.first().pretty()}${if (p.tags.size > 1) " y ${p.tags.size - 1} más" else ""}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Clock.ago(p.createdAt) + if (p.editedAt != null) " · editado" else "", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    Icon(when (p.vis) { 1 -> Icons.Outlined.People; 2 -> Icons.Filled.Lock; else -> Icons.Outlined.Public }, null, Modifier.padding(start = 6.dp).size(13.dp), tint = c.onSurfaceVariant)
                    if (p.gname != null) { Icon(Icons.Filled.Group, null, Modifier.padding(start = 6.dp).size(13.dp), tint = c.onSurfaceVariant); Text(" " + p.gname, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            if (p.type != "post") AssistChip({}, label = { Text(typeName(p.type)) }, modifier = Modifier.padding(end = 4.dp))
            IconButton({ h.menu = p }) { Icon(Icons.Filled.MoreHoriz, "Opciones") }
        }
        if (p.title.isNotBlank()) Text(p.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp))
        if (p.body.isNotBlank()) {
            RichText(p.body, Modifier.padding(horizontal = 14.dp, vertical = 8.dp).let { if (detail) it else it.clickable { nav.post(p.id) } }, style = MaterialTheme.typography.bodyLarge, maxLines = if (detail) Int.MAX_VALUE else 14)
            val url = if (p.media.isEmpty() && p.shared == null) Regex("https://[^\\s<>\"']{4,490}").find(p.body)?.value else null
            if (url != null) LinkPreview(url, Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
        }
        p.poll?.let { PollView(p, h, Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
        if (p.media.isNotEmpty()) MediaGrid(p.media, Modifier.padding(top = 6.dp)) { i -> h.viewer = p.media to i }
        p.shared?.let { SharedCard(it, Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) }
        // resumen
        if (p.likes > 0 || p.comments > 0) Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (p.likes > 0) Row(Modifier.clickable { h.reactors = p }, verticalAlignment = Alignment.CenterVertically) {
                Text(p.reactionKinds.ifEmpty { listOf(1) }.take(3).joinToString("") { Reactions.emoji.getOrElse(it) { "👍" } }, fontSize = 14.sp)
                Text(" ${count(p.likes)}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.weight(1f))
            if (p.comments > 0) Text("${count(p.comments)} comentario${if (p.comments == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.clickable { nav.post(p.id) })
        }
        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = c.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
            ReactionButton(p, h, Modifier.weight(1f))
            ActionButton(Icons.Outlined.ChatBubbleOutline, "Comentar", Modifier.weight(1f)) { nav.post(p.id) }
            ActionButton(Icons.Outlined.Share, "Compartir", Modifier.weight(1f)) { h.share = p }
        }
    }
}

private fun typeName(t: String) = when (t) { "news" -> "Noticia"; "update" -> "Actualización"; "photo" -> "Foto"; "faq" -> "FAQ"; "rules" -> "Reglas"; "review" -> "Testimonio"; "reel" -> "Reel"; "story" -> "Historia"; else -> t }

@Composable
private fun ActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)); Text(" $label", style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** Me gusta con un toque; manteniendo pulsado se elige la reacción. */
@Composable
fun ReactionButton(p: Post, h: PostHost, modifier: Modifier = Modifier) {
    var picker by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val c = MaterialTheme.colorScheme
    val on = p.mine > 0
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).combinedClickable(onClick = { h.react(p, if (on) p.mine else 1) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); picker = true }).padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            if (on) Text(Reactions.emoji[p.mine], fontSize = 18.sp) else Icon(Icons.Outlined.ThumbUp, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Text(" " + if (on) Reactions.names[p.mine] else "Me gusta", style = MaterialTheme.typography.labelLarge, color = if (on) c.secondary else c.onSurfaceVariant)
        }
        if (picker) Popup(alignment = Alignment.TopStart, offset = IntOffset(0, -150), onDismissRequest = { picker = false }) {
            Row(Modifier.clip(RoundedCornerShape(30.dp)).background(c.surfaceContainerHighest).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (k in 1..6) Box(Modifier.size(44.dp).clip(CircleShape).clickable { picker = false; h.react(p, k) }, contentAlignment = Alignment.Center) { Text(Reactions.emoji[k], fontSize = 26.sp) }
            }
        }
    }
}

@Composable
private fun PollView(p: Post, h: PostHost, modifier: Modifier) {
    val poll = p.poll ?: return
    val total = poll.opts.sumOf { it.n }.coerceAtLeast(1)
    Column(modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        poll.opts.forEachIndexed { i, o ->
            val mine = poll.mine == i
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { h.vote(p, i) }) {
                LinearProgressIndicator(progress = { o.n.toFloat() / total }, Modifier.matchParentSize(), color = MaterialTheme.colorScheme.primary.copy(alpha = .25f), trackColor = Color.Transparent, drawStopIndicator = {})
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(o.t, Modifier.weight(1f), fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
                    Text("${o.n * 100 / total}%", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text("${poll.opts.sumOf { it.n }} voto${if (poll.opts.sumOf { it.n } == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SharedCard(s: com.sampcity.social.data.SharedPost, modifier: Modifier) {
    val nav = LocalNavigator.current
    Card(modifier.fillMaxWidth().let { if (!s.gone) it.clickable { nav.post(s.id) } else it }, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        if (s.gone) { Text("Esta publicación ya no está disponible.", Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant); return@Card }
        Column {
            if (s.media.isNotEmpty()) AsyncImage(SampApp.instance.api.mediaUrl(s.media.first().previewId), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(180.dp))
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Avatar(s.author, s.skin, 26.dp); Spacer(Modifier.width(8.dp)); PlayerName(s.author); Text("  " + Clock.ago(s.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (s.body.isNotBlank()) Text(s.body, Modifier.padding(top = 6.dp), maxLines = 5, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
