package com.sampcity.social.data

import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

/** Todas las llamadas a la API en un solo lugar (mismos endpoints que usa la web). */
class Repo(val api: Api) {
    // ---- Sesión ----
    suspend fun info(): Info = api.get("/info")
    suspend fun me(): Me? = api.get<MeBox>("/me").user
    suspend fun login(name: String, password: String, code: String?) { api.request("POST", "/login", obj("name" to name, "password" to password, "code" to code?.takeIf { it.isNotBlank() }), notifyAuth = false) }
    suspend fun register(name: String, email: String, password: String) { api.request("POST", "/register", obj("name" to name, "email" to email, "password" to password), notifyAuth = false) }
    suspend fun logout() { runCatching { api.request("POST", "/logout", notifyAuth = false) } }
    suspend fun appInfo(): AppInfo = api.get("/app")

    // ---- Publicaciones ----
    suspend fun posts(query: String): List<Post> = api.get("/posts?$query")
    suspend fun post(id: Int): Post = api.get("/posts/$id")
    suspend fun react(id: Int, kind: Int): LikeResult = api.post("/posts/$id/like", obj("kind" to kind))
    suspend fun reactors(id: Int): List<Reactor> = api.get("/posts/$id/likes")
    suspend fun savePost(id: Int): SaveResult = api.post("/posts/$id/save")
    suspend fun hidePost(id: Int) { api.send("POST", "/posts/$id/hide") }
    suspend fun pinPost(id: Int) { api.send("POST", "/posts/$id/pin") }
    suspend fun editPost(id: Int, body: String, vis: Int? = null) { api.send("PUT", "/posts/$id", obj("body" to body, "vis" to vis)) }
    suspend fun deletePost(id: Int) { api.send("DELETE", "/posts/$id") }
    suspend fun vote(id: Int, opt: Int): Poll = api.post("/posts/$id/vote", obj("opt" to opt))
    suspend fun createPost(body: String, media: List<Int>, tags: List<String>, vis: Int, poll: List<String>, share: Int? = null, group: Int? = null): Created =
        api.post("/wall", obj("body" to body, "media" to media, "tags" to tags, "vis" to vis, "poll" to poll.takeIf { it.isNotEmpty() }, "share" to share, "group" to group))
    suspend fun trends(): List<Trend> = api.get("/trends")
    suspend fun birthdays(): List<Birthday> = api.get("/birthdays")
    suspend fun preview(url: String): PreviewData = api.get("/preview?u=${enc(url)}")

    // ---- Comentarios ----
    suspend fun comments(id: Int): List<Comment> = api.get("/posts/$id/comments")
    suspend fun addComment(id: Int, body: String, parent: Int?) { api.send("POST", "/posts/$id/comments", obj("body" to body, "parent" to parent)) }
    suspend fun deleteComment(postId: Int, id: Int) { api.send("DELETE", "/posts/$postId/comments/$id") }
    suspend fun editComment(id: Int, body: String) { api.send("PUT", "/comments/$id", obj("body" to body)) }
    suspend fun likeComment(id: Int): JsonElement = api.request("POST", "/comments/$id/like")

    // ---- Historias y reels ----
    suspend fun stories(): List<Post> = api.get("/posts?type=story&scope=friends")
    suspend fun storyViewed(id: Int) { runCatching { api.send("POST", "/stories/$id/view") } }
    suspend fun storyViews(id: Int): List<StoryViewer> = api.get("/stories/$id/views")
    suspend fun createStory(media: Int, caption: String): Created = api.post("/stories", obj("media" to media, "body" to caption))
    suspend fun reels(before: Int?, author: String? = null, saved: Boolean = false, start: Int? = null): ReelPage =
        api.get("/reels?n=8" + (before?.let { "&before=$it" } ?: "") + (author?.let { "&author=${enc(it)}" } ?: "") + (if (saved) "&saved=1" else "") + (start?.let { "&start=$it" } ?: ""))
    suspend fun createReel(media: Int, caption: String): Created = api.post("/reels", obj("media" to media, "body" to caption))

    // ---- Chat ----
    suspend fun chats(): List<ChatConv> = api.get("/chat")
    suspend fun rooms(): List<RoomConv> = api.get("/rooms")
    suspend fun thread(name: String, after: Int? = null): ChatThread = api.get("/chat/${enc(name)}" + (after?.let { "?after=$it" } ?: ""))
    suspend fun roomThread(id: Int, after: Int? = null): RoomThread = api.get("/rooms/$id" + (after?.let { "?after=$it" } ?: ""))
    suspend fun sendMsg(name: String, body: String, data: String?, dur: Int?, reply: Int?): SentMsg = api.post("/chat/${enc(name)}", obj("body" to body, "data" to data, "dur" to dur, "reply" to reply))
    suspend fun sendRoomMsg(id: Int, body: String, data: String?, dur: Int?, reply: Int?): SentMsg = api.post("/rooms/$id", obj("body" to body, "data" to data, "dur" to dur, "reply" to reply))
    suspend fun typing(name: String) { runCatching { api.send("POST", "/chat/${enc(name)}/typing") } }
    suspend fun deleteMsg(id: Int) { api.send("DELETE", "/chat/msg/$id") }
    suspend fun reactMsg(id: Int, kind: Int): ReactResult = api.post("/chat/msg/$id/react", obj("kind" to kind))
    suspend fun clearChat(name: String) { api.send("DELETE", "/chat/${enc(name)}") }
    suspend fun searchMsgs(q: String): List<MsgHit> = api.get("/chat/search?q=${enc(q)}")
    suspend fun createRoom(name: String, members: List<String>): Created = api.post("/rooms", obj("name" to name, "members" to members))
    suspend fun renameRoom(id: Int, name: String): RoomPut = api.put("/rooms/$id", obj("name" to name))
    suspend fun addToRoom(id: Int, names: List<String>): RoomPut = api.put("/rooms/$id", obj("add" to names))
    suspend fun leaveRoom(id: Int, name: String) { api.send("DELETE", "/rooms/$id/members/${enc(name)}") }

    // ---- Avisos ----
    suspend fun notifications(before: Int?, unread: Boolean): List<Notif> = api.get("/notifications?" + (before?.let { "before=$it&" } ?: "") + (if (unread) "unread=1" else ""))
    suspend fun notifCount(): NotifCount = api.get("/notifications/count")
    suspend fun readNotif(id: Int?) { api.send("POST", "/notifications/read", obj("id" to id)) }
    suspend fun deleteNotif(id: Int) { api.send("DELETE", "/notifications/$id") }

    // ---- Amigos y perfiles ----
    suspend fun friends(): List<Friend> = api.get("/friends")
    suspend fun friendRequests(): List<FriendReq> = api.get("/friends/requests")
    suspend fun suggestions(): List<Suggestion> = api.get("/friends/suggestions")
    suspend fun findPlayers(q: String): List<FriendHit> = api.get("/friends/search?q=${enc(q)}")
    suspend fun addFriend(name: String): FriendState = api.post("/friends/request", obj("name" to name))
    suspend fun acceptFriend(name: String): FriendState = api.post("/friends/accept", obj("name" to name))
    suspend fun removeFriend(name: String): FriendState = api.delete("/friends/${enc(name)}")
    suspend fun user(name: String): UserProfile = api.get("/user/${enc(name)}")
    suspend fun userPhotos(name: String): List<PhotoRef> = api.get("/user/${enc(name)}/photos")
    suspend fun follow(name: String): FollowResult = api.post("/follow/${enc(name)}")
    suspend fun setBio(bio: String) { api.send("POST", "/profile/bio", obj("bio" to bio)) }
    suspend fun setInfo(city: String, job: String, link: String, bday: String) { api.send("POST", "/profile/info", obj("city" to city, "job" to job, "link" to link, "bday" to bday)) }
    suspend fun setPhoto(kind: String, dataUrl: String) { api.send("POST", "/profile/photo", obj("kind" to kind, "data" to dataUrl)) }
    suspend fun removePhoto(kind: String) { api.send("DELETE", "/profile/photo?kind=$kind") }
    suspend fun searchUsers(q: String): List<UserHit> = api.get("/users/search?q=${enc(q)}")
    suspend fun search(q: String, t: String = ""): SearchResult = api.get("/search?q=${enc(q)}&t=$t")

    // ---- Grupos y eventos ----
    suspend fun groups(q: String = ""): GroupList = api.get("/groups" + if (q.isNotBlank()) "?q=${enc(q)}" else "")
    suspend fun group(id: Int): GroupDetail = api.get("/groups/$id")
    suspend fun createGroup(name: String, about: String, priv: Boolean): Created = api.post("/groups", obj("name" to name, "about" to about, "priv" to if (priv) 1 else 0))
    suspend fun updateGroup(id: Int, name: String, about: String, priv: Boolean) { api.send("PUT", "/groups/$id", obj("name" to name, "about" to about, "priv" to if (priv) 1 else 0)) }
    suspend fun deleteGroup(id: Int) { api.send("DELETE", "/groups/$id") }
    suspend fun joinGroup(id: Int) { api.send("POST", "/groups/$id/join") }
    suspend fun leaveGroup(id: Int) { api.send("DELETE", "/groups/$id/join") }
    suspend fun groupMember(id: Int, name: String, action: String) { api.send("POST", "/groups/$id/members/${enc(name)}", obj("action" to action)) }
    suspend fun events(past: Boolean): List<EventItem> = api.get("/events" + if (past) "?when=past" else "")
    suspend fun createEvent(title: String, about: String, place: String, starts: String): Created = api.post("/events", obj("title" to title, "about" to about, "place" to place, "starts" to starts))
    suspend fun rsvp(id: Int, st: Int): EventItem = api.post("/events/$id/rsvp", obj("st" to st))
    suspend fun eventPeople(id: Int): List<EventPerson> = api.get("/events/$id/people")
    suspend fun deleteEvent(id: Int) { api.send("DELETE", "/events/$id") }

    // ---- Marketplace ----
    suspend fun market(cat: String, sort: String, q: String, mine: Boolean, page: Int): List<MarketItem> =
        api.get("/market?page=$page" + (if (cat.isNotEmpty()) "&cat=$cat" else "") + (if (sort.isNotEmpty()) "&sort=$sort" else "") + (if (q.isNotBlank()) "&q=${enc(q)}" else "") + (if (mine) "&mine=1" else ""))
    suspend fun createListing(cat: String, title: String, price: Long, body: String, dataUrl: String?): Created = api.post("/market", obj("cat" to cat, "title" to title, "price" to price, "body" to body, "data" to dataUrl))
    suspend fun editListing(id: Int, cat: String, title: String, price: Long, body: String) { api.send("PUT", "/market/$id", obj("cat" to cat, "title" to title, "price" to price, "body" to body)) }
    suspend fun toggleSold(id: Int) { api.send("POST", "/market/$id/sold") }
    suspend fun deleteListing(id: Int) { api.send("DELETE", "/market/$id") }

    // ---- Contenido del staff, ranking ----
    suspend fun createStaffPost(type: String, title: String, body: String) { api.send("POST", "/posts", obj("type" to type, "title" to title, "body" to body)) }
    suspend fun editStaffPost(id: Int, title: String, body: String) { api.send("PUT", "/posts/$id", obj("title" to title, "body" to body)) }
    suspend fun staff(): List<StaffMember> = api.get("/staff")
    suspend fun top(by: String): List<TopEntry> = api.get("/top?by=$by")

    // ---- Cuenta ----
    suspend fun settings(): Settings = api.get("/settings")
    suspend fun saveSettings(msg: Int, prof: Int, tag: Int, nmask: Int) { api.send("POST", "/settings", obj("msg" to msg, "prof" to prof, "tag" to tag, "nmask" to nmask)) }
    suspend fun changePassword(current: String, next: String) { api.send("POST", "/settings/password", obj("current" to current, "next" to next)) }
    suspend fun logoutAll() { api.send("POST", "/settings/logout-all") }
    suspend fun sessions(): List<SessionInfo> = api.get("/settings/sessions")
    suspend fun closeSession(id: String) { api.send("DELETE", "/settings/sessions/$id") }
    suspend fun twoFa(): TwoFa = api.get("/settings/2fa")
    suspend fun twoFaSetup(): TwoFaSetup = api.post("/settings/2fa/setup")
    suspend fun twoFaEnable(code: String) { api.send("POST", "/settings/2fa/enable", obj("code" to code)) }
    suspend fun twoFaDisable(password: String) { api.send("POST", "/settings/2fa/disable", obj("password" to password)) }
    suspend fun exportData(): ByteArray = api.download("/api/settings/export")
    suspend fun deleteMyContent(password: String) { api.send("POST", "/settings/delete", obj("password" to password)) }
    suspend fun block(name: String) { api.send("POST", "/blocks/${enc(name)}") }
    suspend fun unblock(name: String) { api.send("DELETE", "/blocks/${enc(name)}") }
    suspend fun report(kind: String, target: Int?, name: String?, reason: String, note: String) { api.send("POST", "/reports", obj("kind" to kind, "target" to target, "name" to name, "reason" to reason, "note" to note)) }

    // ---- Panel del staff ----
    suspend fun adminStats(): AdminStats = api.get("/admin/stats")
    suspend fun bans(): List<Ban> = api.get("/admin/bans")
    suspend fun ban(player: String, hours: Int, reason: String) { api.send("POST", "/admin/bans", obj("player" to player, "hours" to hours, "reason" to reason)) }
    suspend fun unban(name: String) { api.send("DELETE", "/admin/bans/${enc(name)}") }
    suspend fun words(): List<String> = api.get("/admin/words")
    suspend fun addWords(words: String) { api.send("POST", "/admin/words", obj("words" to words)) }
    suspend fun removeWord(word: String) { api.send("DELETE", "/admin/words", obj("word" to word)) }
    suspend fun verified(): List<VerifiedUser> = api.get("/admin/verified")
    suspend fun verify(name: String) { api.send("POST", "/admin/verified/${enc(name)}") }
    suspend fun unverify(name: String) { api.send("DELETE", "/admin/verified/${enc(name)}") }
    suspend fun modLog(): List<ModLog> = api.get("/admin/log")
    suspend fun reports(): List<Report> = api.get("/reports")
    suspend fun closeReport(id: Int) { api.send("POST", "/reports/$id/close") }
}
