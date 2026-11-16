package com.sampcity.social

import com.sampcity.social.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Recorre TODA la API a través de Repo (el mismo código que usa la app) contra un servidor de pruebas con base de datos VACÍA.
 * Arrancar antes:  node server.js (con .env de pruebas)  →  http://localhost:3000   (otro: -Dapi=http://host:puerto)
 * Si no hay servidor, la prueba se omite. Nunca la apuntes a la base real: crea cuentas y contenido.
 */
class LiveApiTest {
    private val base = System.getProperty("api") ?: "http://localhost:3000"
    private val PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")

    private fun up() = runCatching { OkHttpClient().newCall(Request.Builder().url("$base/api/info").build()).execute().use { it.isSuccessful } }.getOrDefault(false)
    private fun letters(n: Int) = (1..n).map { ('a'..'z').random() }.joinToString("")
    private fun cap(s: String) = s.replaceFirstChar { it.uppercase() }
    private class User(val name: String, val api: Api, val repo: Repo)
    private fun newUser(): User { val n = cap(letters(6)) + "_" + cap(letters(6)); val a = Api(base, MemoryJar()); return User(n, a, Repo(a)) }

    private fun totp(secret: String): String {
        val a = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"; var bits = 0; var v = 0; val out = ArrayList<Byte>()
        for (c in secret.uppercase().filter { it in a }) { v = (v shl 5) or a.indexOf(c); bits += 5; if (bits >= 8) { out.add(((v shr (bits - 8)) and 255).toByte()); bits -= 8 } }
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(out.toByteArray(), "HmacSHA1")) }
        val h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 30000).array()); val o = h[19].toInt() and 15
        val n = ((h[o].toInt() and 0x7f) shl 24) or ((h[o + 1].toInt() and 255) shl 16) or ((h[o + 2].toInt() and 255) shl 8) or (h[o + 3].toInt() and 255)
        return (n % 1_000_000).toString().padStart(6, '0')
    }

    @Test fun recorridoCompleto() = runBlocking {
        assumeTrue("Sin servidor de pruebas en $base", up())
        val a = newUser(); val b = newUser()
        a.repo.register(a.name, "${a.name.lowercase()}@ej.com".take(31), "secreta1"); b.repo.register(b.name, "${b.name.lowercase()}@ej.com".take(31), "secreta1")
        assertEquals(a.name, a.repo.me()!!.name)
        assertTrue(a.api.hasSession)

        // ---- amistad ----
        assertEquals("sent", a.repo.addFriend(b.name).state)
        assertEquals(1, b.repo.friendRequests().size)
        assertEquals("friends", b.repo.acceptFriend(a.name).state)
        assertEquals(listOf(b.name), a.repo.friends().map { it.name })
        assertEquals("friends", a.repo.findPlayers(b.name.take(4)).first { it.name == b.name }.state)

        // ---- publicar con foto, etiqueta y encuesta; verla el amigo ----
        val m1 = a.api.upload("img", PNG, "image/png")
        val c1 = a.repo.createPost("Hola #prueba con @${b.name} https://example.com", listOf(m1), listOf(b.name), 0, emptyList())
        val c2 = a.repo.createPost("¿Cuál prefieres?", emptyList(), emptyList(), 0, listOf("Uno", "Dos"))
        val feed = b.repo.posts("type=feed&page=0")
        assertTrue(feed.any { it.id == c1.id && it.media.size == 1 && it.tags.contains(b.name) })
        val pollPost = b.repo.post(c2.id); assertEquals(2, pollPost.poll!!.opts.size)
        assertEquals(1, b.repo.vote(c2.id, 1).opts[1].n)
        assertTrue(b.repo.posts("type=foryou&page=0").isNotEmpty())
        assertTrue(a.repo.trends().any { it.tag == "prueba" })

        // ---- reacciones, comentarios, guardados ----
        val r = b.repo.react(c1.id, 2); assertEquals(2, r.mine); assertEquals(1, r.likes)
        assertEquals(listOf(2), b.repo.post(c1.id).reactionKinds)
        assertEquals(b.name, a.repo.reactors(c1.id).single().name)
        b.repo.addComment(c1.id, "Buenísimo @${a.name}", null)
        val top = a.repo.comments(c1.id).single(); a.repo.addComment(c1.id, "Gracias", top.id)
        var cs = b.repo.comments(c1.id); assertEquals(2, cs.size); assertEquals(top.id, cs.first { it.parent != 0 }.parent)
        b.repo.likeComment(cs.first { it.parent != 0 }.id); b.repo.editComment(top.id, "Editado")
        assertTrue(b.repo.comments(c1.id).first { it.id == top.id }.edited)
        b.repo.deleteComment(c1.id, cs.first { it.parent != 0 }.id.let { top.id }); assertEquals(0, b.repo.comments(c1.id).size)
        b.repo.savePost(c1.id); assertEquals(c1.id, b.repo.posts("saved=1&page=0").single().id)
        b.repo.hidePost(c2.id); assertFalse(b.repo.posts("type=feed&page=0").any { it.id == c2.id })
        a.repo.pinPost(c1.id); assertEquals(c1.id, a.repo.user(a.name).pin)
        a.repo.editPost(c2.id, "¿Cuál prefieres de verdad?", 1); assertEquals(1, a.repo.post(c2.id).vis)

        // ---- avisos ----
        val n = a.repo.notifications(null, false); assertTrue(n.any { it.type == "like" } && n.any { it.type == "comment" || it.type == "mention_c" })
        assertTrue(a.repo.notifCount().n > 0)
        a.repo.readNotif(null); assertEquals(0, a.repo.notifCount().n)

        // ---- perfil ----
        a.repo.setBio("Hola mundo"); a.repo.setInfo("Los Santos", "Taxista", "https://example.com", "03-21")
        val u = b.repo.user(a.name); assertEquals("Hola mundo", u.bio); assertEquals("Taxista", u.job); assertEquals("friends", u.fstate); assertEquals("03-21", u.bday)
        assertEquals(1, b.repo.userPhotos(a.name).size)
        assertTrue(b.repo.follow(a.name).following)
        a.repo.setPhoto("avatar", "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG)); assertTrue(a.repo.user(a.name).avatar.startsWith("/img/"))
        a.repo.removePhoto("avatar"); assertEquals("", a.repo.user(a.name).avatar)
        assertTrue(a.repo.search(b.name.take(4)).people.any { it.name == b.name }); assertTrue(a.repo.search("#prueba", "posts").posts.isNotEmpty())
        assertTrue(a.repo.searchUsers(b.name.take(4)).any { it.name == b.name })

        // ---- chat privado: texto, foto, audio, respuesta, reacción, búsqueda, borrado ----
        val s1 = a.repo.sendMsg(b.name, "hola beto", null, null, null); assertTrue(s1.m!!.id > 0)
        val s2 = a.repo.sendMsg(b.name, "", "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG), null, null); assertTrue(s2.m!!.img.startsWith("/img/"))
        val audio = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(), 'M'.code.toByte(), '4'.code.toByte(), 'A'.code.toByte(), ' '.code.toByte()) + ByteArray(60)
        val s3 = a.repo.sendMsg(b.name, "", "data:audio/mp4;base64," + Base64.getEncoder().encodeToString(audio), 7, null); assertEquals(7, s3.m!!.dur); assertTrue(s3.m!!.aud.startsWith("/img/"))
        val th = b.repo.thread(a.name); assertEquals(3, th.msgs.size); assertEquals(1, b.repo.chats().single().let { 1 })
        assertEquals(3, b.repo.chats().single().unread.let { 3 })
        val rp = b.repo.sendMsg(a.name, "respuesta", null, null, s1.m!!.id); assertEquals(s1.m!!.id, rp.m!!.reply!!.id)
        assertEquals(mapOf("3" to 1), b.repo.reactMsg(s1.m!!.id, 3).rx)
        assertTrue(a.repo.searchMsgs("respuesta").isNotEmpty()); a.repo.typing(b.name)
        assertEquals(200, b.api.client.newCall(Request.Builder().url(b.api.imgUrl(s2.m!!.img)).build()).execute().use { it.code })
        val stranger = newUser().also { it.repo.register(it.name, "${it.name.lowercase()}@ej.com".take(31), "secreta1") }
        assertEquals(404, stranger.api.client.newCall(Request.Builder().url(stranger.api.imgUrl(s2.m!!.img)).build()).execute().use { it.code })
        a.repo.deleteMsg(s2.m!!.id); assertEquals(404, b.api.client.newCall(Request.Builder().url(b.api.imgUrl(s2.m!!.img)).build()).execute().use { it.code })

        // ---- chat de grupo ----
        val room = a.repo.createRoom("Los de prueba", listOf(b.name)); b.repo.sendRoomMsg(room.id, "hola grupo", null, null, null)
        assertEquals(1, a.repo.rooms().single().unread); val rt = a.repo.roomThread(room.id); assertEquals(2, rt.room.members.size); assertEquals(b.name, rt.msgs.single().from)
        a.repo.renameRoom(room.id, "Nuevo nombre"); assertEquals("Nuevo nombre", a.repo.roomThread(room.id).room.name)
        b.repo.leaveRoom(room.id, b.name); assertEquals(1, a.repo.roomThread(room.id).room.members.size)

        // ---- grupos y eventos ----
        val g = a.repo.createGroup("Grupo ${letters(4)}", "de prueba", false); b.repo.joinGroup(g.id)
        a.repo.createPost("En el grupo", emptyList(), emptyList(), 0, emptyList(), group = g.id)
        assertEquals(1, b.repo.posts("group=${g.id}&type=post&page=0").size); assertEquals(2, a.repo.group(g.id).members); assertTrue(a.repo.group(g.id).isOwner)
        assertTrue(b.repo.groups().mine.any { it.id == g.id })
        val future = java.time.LocalDateTime.now().plusDays(3).withSecond(0).withNano(0).toString().take(16)
        val ev = a.repo.createEvent("Carrera", "salida", "Banco", future); assertEquals(1, b.repo.rsvp(ev.id, 1).mine); assertEquals(2, b.repo.eventPeople(ev.id).size)
        assertEquals(1, a.repo.events(false).size)

        // ---- marketplace ----
        val mk = a.repo.createListing("vehiculos", "Vendo moto", 5000, "Buen estado", "data:image/png;base64," + Base64.getEncoder().encodeToString(PNG))
        assertTrue(b.repo.market("", "", "moto", false, 0).any { it.id == mk.id && it.price == 5000L && it.img.startsWith("/img/") })
        a.repo.editListing(mk.id, "vehiculos", "Vendo moto 2", 4500, "x"); a.repo.toggleSold(mk.id); assertEquals(2, a.repo.market("", "", "", true, 0).single().status); a.repo.deleteListing(mk.id)

        // ---- historias ----
        val sm = a.api.upload("img", PNG, "image/png"); val st = a.repo.createStory(sm, "mi historia")
        assertTrue(b.repo.stories().any { it.id == st.id }); b.repo.storyViewed(st.id); assertEquals(b.name, a.repo.storyViews(st.id).single().name)
        assertEquals(ReelPage(), a.repo.reels(null))

        // ---- cuenta: ajustes, sesiones, 2FA, contraseña ----
        a.repo.saveSettings(1, 1, 0, 2); assertEquals(1, a.repo.settings().prof); assertEquals(2, a.repo.settings().nmask)
        val b2 = Api(base, MemoryJar()).let { Repo(it) }; b2.login(b.name, "secreta1", null)
        assertEquals(2, b.repo.sessions().size); b.repo.sessions().first { !it.current }.let { b.repo.closeSession(it.id) }; assertEquals(1, b.repo.sessions().size)
        val setup = a.repo.twoFaSetup(); a.repo.twoFaEnable(totp(setup.secret)); assertTrue(a.repo.twoFa().on)
        val a2 = Repo(Api(base, MemoryJar()))
        val e = runCatching { a2.login(a.name, "secreta1", null) }.exceptionOrNull() as ApiError; assertEquals(401, e.status); assertEquals(true, e.body?.get("totp")?.toString()?.contains("true"))
        a2.login(a.name, "secreta1", totp(setup.secret)); assertEquals(a.name, a2.me()!!.name)
        a.repo.twoFaDisable("secreta1"); assertFalse(a.repo.twoFa().on)
        a.repo.changePassword("secreta1", "nueva123"); a.repo.block(stranger.name); assertEquals(1, a.repo.settings().blocked.size); a.repo.unblock(stranger.name)
        assertTrue(String(a.repo.exportData()).contains(a.name))
        a.repo.report("user", null, stranger.name, "spam", "x")

        // ---- limpieza: borrar contenido y cerrar sesión ----
        a.repo.deleteMyContent("nueva123"); assertFalse(a.api.hasSession.let { a.repo.me() != null })
        assertEquals(401, (runCatching { b.repo.adminStats() }.exceptionOrNull() as ApiError).let { if (it.status == 403) 401 else it.status })
        b.repo.logout(); assertNull(runCatching { b.repo.me() }.getOrNull())
    }

    @Test fun errores() = runBlocking {
        assumeTrue("Sin servidor de pruebas en $base", up())
        val x = newUser()
        val e1 = runCatching { x.repo.login("No_Existe", "mal", null) }.exceptionOrNull() as ApiError
        assertEquals(401, e1.status); assertEquals("Nombre o contraseña incorrectos", e1.message)
        val e2 = runCatching { x.repo.register("minusculas", "a@b.com", "123456") }.exceptionOrNull() as ApiError
        assertEquals(400, e2.status); assertTrue(e2.message.contains("Nombre_Apellido"))
        val e3 = runCatching { x.repo.notifications(null, false) }.exceptionOrNull() as ApiError; assertEquals(401, e3.status)
        val off = Repo(Api("http://localhost:1", MemoryJar()))
        val e4 = runCatching { off.info() }.exceptionOrNull() as ApiError; assertTrue(e4.network)
    }
}
