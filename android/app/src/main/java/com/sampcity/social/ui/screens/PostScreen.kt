@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sampcity.social.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sampcity.social.SampApp
import com.sampcity.social.data.Comment
import com.sampcity.social.data.Post
import com.sampcity.social.ui.LocalNavigator
import com.sampcity.social.ui.components.Avatar
import com.sampcity.social.ui.components.ConfirmDialog
import com.sampcity.social.ui.components.ErrorBox
import com.sampcity.social.ui.components.LoadingBox
import com.sampcity.social.ui.components.LocalSnack
import com.sampcity.social.ui.components.PlayerName
import com.sampcity.social.ui.components.PostCard
import com.sampcity.social.ui.components.PostHostDialogs
import com.sampcity.social.ui.components.PromptDialog
import com.sampcity.social.ui.components.RichText
import com.sampcity.social.ui.components.pretty
import com.sampcity.social.ui.components.rememberPostHost
import com.sampcity.social.ui.components.userMessage
import com.sampcity.social.util.Clock
import kotlinx.coroutines.launch

class PostVM(private val id: Int) : ViewModel() {
    private val repo = SampApp.instance.repo
    var post by mutableStateOf<Post?>(null)
    var comments by mutableStateOf<List<Comment>>(emptyList())
    var error by mutableStateOf<String?>(null)
    var sending by mutableStateOf(false)
    init { load() }
    fun load() {
        viewModelScope.launch {
            error = null
            try { post = repo.post(id); comments = repo.comments(id) } catch (e: Exception) { error = e.userMessage() }
        }
    }
    fun reloadComments() { viewModelScope.launch { runCatching { repo.comments(id) }.onSuccess { comments = it; post = post?.copy(comments = it.size) } } }
}

class PostVMFactory(private val id: Int) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = PostVM(id) as T
}

@Composable
fun PostScreen(id: Int) {
    val vm: PostVM = viewModel(key = "post$id", factory = PostVMFactory(id))
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val repo = SampApp.instance.repo
    val host = rememberPostHost(onUpdate = { vm.post = it }, onRemove = { nav.back() })
    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }
    var editing by remember { mutableStateOf<Comment?>(null) }
    var deleting by remember { mutableStateOf<Comment?>(null) }

    fun send() {
        val body = text.trim(); if (body.isEmpty() || vm.sending) return
        vm.sending = true
        scope.launch {
            try {
                val e = editing
                if (e != null) repo.editComment(e.id, body) else repo.addComment(id, body, replyTo?.let { if (it.parent != 0) it.parent else it.id })
                text = ""; replyTo = null; editing = null; vm.reloadComments()
            } catch (e: Exception) { snack.showSnackbar(e.userMessage()) }
            vm.sending = false
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Publicación") }, navigationIcon = { IconButton(nav::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } }) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    val ctx = replyTo ?: editing
                    if (ctx != null) Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (editing != null) "Editando tu comentario" else "Respondiendo a ${ctx.author.pretty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                        IconButton({ replyTo = null; editing = null; text = "" }, Modifier.size(28.dp)) { Icon(Icons.Filled.Close, "Cancelar", Modifier.size(18.dp)) }
                    }
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(text, { if (it.length <= 300) text = it }, Modifier.weight(1f), placeholder = { Text("Escribe un comentario…") }, maxLines = 4, shape = RoundedCornerShape(24.dp))
                        IconButton(::send, enabled = text.isNotBlank() && !vm.sending) { Icon(Icons.AutoMirrored.Filled.Send, "Enviar", tint = if (text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
    ) { pad ->
        val post = vm.post
        when {
            vm.error != null && post == null -> ErrorBox(vm.error!!, vm::load, Modifier.padding(pad).fillMaxSize())
            post == null -> LoadingBox(Modifier.padding(pad).fillMaxSize())
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize()) {
                item { PostCard(post, host, detail = true) }
                item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
                val tops = vm.comments.filter { it.parent == 0 }
                if (tops.isEmpty()) item { Text("Aún no hay comentarios. Sé el primero.", Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                tops.forEach { c ->
                    item(key = "c${c.id}") { CommentRow(c, post.id, false, vm, onReply = { replyTo = it; editing = null }, onEdit = { editing = it; replyTo = null; text = it.body }, onDelete = { deleting = it }) }
                    items(vm.comments.filter { it.parent == c.id }, key = { "c${it.id}" }) { r -> CommentRow(r, post.id, true, vm, onReply = { replyTo = it; editing = null }, onEdit = { editing = it; replyTo = null; text = it.body }, onDelete = { deleting = it }) }
                }
            }
        }
    }
    PostHostDialogs(host)
    deleting?.let { c -> ConfirmDialog("¿Eliminar el comentario?", "También se borran sus respuestas.", "Eliminar", true, { scope.launch { runCatching { repo.deleteComment(id, c.id) }.onSuccess { vm.reloadComments() }.onFailure { snack.showSnackbar(it.userMessage()) } } }) { deleting = null } }
}

@Composable
private fun CommentRow(c: Comment, postId: Int, reply: Boolean, vm: PostVM, onReply: (Comment) -> Unit, onEdit: (Comment) -> Unit, onDelete: (Comment) -> Unit) {
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val repo = SampApp.instance.repo
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var liked by remember(c.id, c.mine) { mutableStateOf(c.mine) }
    var likes by remember(c.id, c.likes) { mutableStateOf(c.likes) }
    Row(Modifier.fillMaxWidth().padding(start = if (reply) 52.dp else 14.dp, end = 14.dp, top = 8.dp, bottom = 2.dp)) {
        Avatar(c.author, null, if (reply) 28.dp else 34.dp, onClick = { nav.profile(c.author) })
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Box {
                Column(Modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    PlayerName(c.author, Modifier.clickable { nav.profile(c.author) }, MaterialTheme.typography.labelLarge)
                    RichText(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                Text(Clock.ago(c.createdAt) + if (c.edited) " · editado" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("  Me gusta" + (if (likes > 0) " · $likes" else ""), style = MaterialTheme.typography.labelMedium, color = if (liked) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (liked) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { liked = !liked; likes += if (liked) 1 else -1; scope.launch { runCatching { repo.likeComment(c.id) }.onFailure { liked = !liked; likes += if (liked) 1 else -1 } } }.padding(4.dp))
                Text("Responder", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onReply(c) }.padding(4.dp))
                Box {
                    Icon(Icons.Filled.MoreVert, "Más", Modifier.size(20.dp).clickable { menu = true }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(menu, { menu = false }) {
                        if (c.own) DropdownMenuItem({ Text("Editar") }, { menu = false; onEdit(c) })
                        if (c.del) DropdownMenuItem({ Text("Eliminar") }, { menu = false; onDelete(c) })
                        if (!c.own) DropdownMenuItem({ Text("Reportar") }, { menu = false; report = true })
                    }
                }
            }
        }
    }
    if (report) PromptDialog("Reportar comentario", label = "¿Qué pasa? (opcional)", confirm = "Enviar", onConfirm = { note -> scope.launch { runCatching { repo.report("comment", c.id, null, "otro", note) } } }, onDismiss = { report = false })
}
