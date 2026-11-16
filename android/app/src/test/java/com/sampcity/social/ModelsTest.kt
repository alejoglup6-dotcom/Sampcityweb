package com.sampcity.social

import com.sampcity.social.data.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Los modelos tienen que leer las respuestas reales de la API (capturadas del servidor de pruebas en src/test/resources/api). */
class ModelsTest {
    private fun fx(name: String): JsonElement = J.parseToJsonElement(javaClass.getResourceAsStream("/api/$name.json")!!.bufferedReader().readText()).jsonObject["body"]!!
    private inline fun <reified T> load(name: String): T = J.decodeFromJsonElement<T>(fx(name))

    @Test fun conteosComoTexto() {
        val feed = load<List<Post>>("A_foryou")
        val p = feed.first { it.id == 1 }
        assertEquals(2, p.likes); assertEquals(3, p.comments)            // el servidor los manda como "2" y "3"
        assertEquals(listOf(1, 2), p.reactionKinds)
        assertEquals(2, p.media.size); assertEquals(listOf("Beto_Ruiz"), p.tags)
        assertEquals("7", p.skin)                                         // la skin llega como número
    }

    @Test fun encuestaYCompartida() {
        val poll = load<Post>("A_post_poll").poll!!
        assertEquals(3, poll.opts.size); assertEquals(1, poll.opts[0].n); assertEquals(-1, poll.mine)
        val sh = load<Post>("A_post_share")
        assertEquals(1, sh.shareId); assertNotNull(sh.shared); assertTrue(sh.shared!!.body.startsWith("Primer día"))
    }

    @Test fun yo() {
        val me = J.decodeFromJsonElement<MeBox>(fx("A_me")).user!!
        assertEquals("Ana_Lopez", me.name); assertEquals("Ciudadano", me.rango); assertFalse(me.canPost); assertEquals(1, me.friends)
    }

    @Test fun chat() {
        val t = load<ChatThread>("A_chat_thread")
        assertEquals("Beto_Ruiz", t.peer.name); assertEquals(2, t.msgs.size)
        assertEquals(1, t.msgs[0].rx["2"]); assertEquals(1, t.msgs[1].reply?.id)
        val l = load<List<ChatConv>>("A_chat_list"); assertEquals(1, l[0].unread)
        val room = load<RoomThread>("A_room"); assertEquals(3, room.room.members.size); assertEquals("Beto_Ruiz", room.msgs[0].from)
        assertEquals("Los taxistas", load<List<RoomConv>>("A_rooms")[0].name)
    }

    @Test fun perfilAmigosYGrupos() {
        val u = load<UserProfile>("A_user"); assertEquals("self", u.fstate); assertEquals(2, u.posts); assertEquals("Taxista", u.job)
        assertEquals(1, load<List<Friend>>("A_friends").size); assertEquals(2, load<List<FriendReq>>("A_friend_requests").size)
        assertEquals("friends", load<List<FriendHit>>("A_friend_search")[0].state)
        val g = load<GroupDetail>("A_group"); assertTrue(g.isOwner); assertEquals(2, g.ms.size)
        assertEquals(1, load<GroupList>("A_groups").mine.size)
        assertEquals(2, load<List<PhotoRef>>("A_user_photos").size)
    }

    @Test fun avisosYBusqueda() {
        val n = load<List<Notif>>("A_notifications"); assertEquals(9, n.size); assertEquals("news", n[0].type); assertEquals(6, n[0].postId)
        assertEquals(9, load<NotifCount>("A_notif_count").n)
        assertEquals(1, load<SearchResult>("A_search").people.size); assertEquals(1, load<SearchResult>("A_search_posts").posts.size)
        assertEquals("taxi", load<List<Trend>>("A_trends")[0].tag)
        assertEquals(3, load<List<Comment>>("A_comments").size)
    }

    @Test fun cuentaYPanel() {
        assertEquals(0, load<Settings>("A_settings").nmask); assertTrue(load<List<SessionInfo>>("A_sessions")[0].current); assertFalse(load<TwoFa>("A_twofa").on)
        val st = load<AdminStats>("C_admin_stats"); assertEquals(4L, st.totals["jugadores"]); assertEquals(1, st.series["registros"]!!.size)
        assertEquals("spam", load<List<Report>>("C_reports")[0].reason)
        assertTrue(load<Info>("A_info").push); assertTrue(load<AppInfo>("A_app").available)
        assertEquals(29, load<List<StaffMember>>("A_staff")[0].let { it.skin!!.toInt() })
        assertEquals(4, load<List<TopEntry>>("A_top").size)
    }

    @Test fun flexTolerante() {
        val p = J.decodeFromString<Post>("""{"id":9,"likes":"12","comments":5,"mine":null,"vis":"1","skin":null,"unknown":{"x":1}}""")
        assertEquals(12, p.likes); assertEquals(5, p.comments); assertEquals(0, p.mine); assertEquals(1, p.vis); assertEquals(null, p.skin)
    }
}
