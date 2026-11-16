package com.sampcity.social.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Modelos de las respuestas de la API (/api/…). Todo con valores por defecto: si el servidor añade o quita un campo, la app no se rompe.

@Serializable data class Info(
    val now: String = "", @Serializable(FI::class) val total: Int = 0, @Serializable(FI::class) val online: Int = 0, val ip: String = "",
    val skinUrl: String = "", val videoMax: Int = 25, val reelMax: Int = 60, val reelSecs: Int = 90,
    val verified: List<String> = emptyList(), val mail: Boolean = false, val push: Boolean = false, val turnstile: String = "",
)

@Serializable data class DiscordLink(@SerialName("discord_id") val discordId: String = "", @SerialName("linked_at") val linkedAt: String = "")

@Serializable data class Me(
    val name: String = "", @Serializable(FS::class) val skin: String? = null, @SerialName("reg_date") val regDate: String = "",
    @SerialName("last_connection") val lastConnection: String? = null,
    @SerialName("time_playing") @Serializable(FI::class) val timePlaying: Int = 0,
    @Serializable(FI::class) val level: Int = 0, @Serializable(FI::class) val rep: Int = 0, @Serializable(FI::class) val connected: Int = 0,
    @SerialName("admin_level") @Serializable(FI::class) val adminLevel: Int = 0, @Serializable(FI::class) val vip: Int = 0,
    @Serializable(FL::class) val cash: Long = 0, @SerialName("bank_money") @Serializable(FL::class) val bankMoney: Long = 0,
    @SerialName("wanted_level") @Serializable(FI::class) val wantedLevel: Int = 0, @SerialName("arrests_count") @Serializable(FI::class) val arrests: Int = 0,
    @SerialName("kills_count") @Serializable(FI::class) val kills: Int = 0, val crew: String? = null,
    val rango: String = "Ciudadano", val canPost: Boolean = false, val discord: DiscordLink? = null,
    @Serializable(FL::class) val banned: Long = 0, @Serializable(FI::class) val unread: Int = 0,
    @Serializable(FI::class) val friends: Int = 0, @Serializable(FI::class) val freq: Int = 0,
)
@Serializable data class MeBox(val user: Me? = null)

@Serializable data class MediaRef(@Serializable(FI::class) val id: Int = 0, val kind: String = "img", val thumb: Int? = null) {
    val isVideo get() = kind == "vid" || kind == "rel"
    /** id del archivo que se muestra como imagen: la foto, o la miniatura del video */
    val previewId get() = if (isVideo) (thumb ?: id) else id
}

@Serializable data class PollOpt(val t: String = "", @Serializable(FI::class) val n: Int = 0)
@Serializable data class Poll(val opts: List<PollOpt> = emptyList(), @Serializable(FI::class) val mine: Int = -1)

@Serializable data class SharedPost(
    val id: Int = 0, val type: String = "post", val title: String = "", val body: String = "", val author: String = "",
    @Serializable(FS::class) val skin: String? = null, @SerialName("created_at") val createdAt: String = "",
    val media: List<MediaRef> = emptyList(), val tags: List<String> = emptyList(), val gone: Boolean = false,
)

@Serializable data class Post(
    val id: Int = 0, val type: String = "post", val title: String = "", val body: String = "", val author: String = "",
    @Serializable(FS::class) val skin: String? = null, @SerialName("created_at") val createdAt: String = "",
    @Serializable(FI::class) val vis: Int = 0, @SerialName("share_id") val shareId: Int? = null,
    @SerialName("edited_at") val editedAt: String? = null, @SerialName("group_id") val groupId: Int? = null, val gname: String? = null,
    @Serializable(FI::class) val likes: Int = 0, @Serializable(FI::class) val comments: Int = 0,
    /** mi reacción: 0 ninguna, 1-6 */
    @Serializable(FI::class) val mine: Int = 0, val rk: String? = null,
    val media: List<MediaRef> = emptyList(), val tags: List<String> = emptyList(), val poll: Poll? = null, val shared: SharedPost? = null,
    // solo en los reels
    @Serializable(FI::class) val saves: Int = 0, @Serializable(FI::class) val saved: Int = 0, val fstate: String? = null,
) {
    val reactionKinds: List<Int> get() = rk?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
}
@Serializable data class ReelPage(val items: List<Post> = emptyList(), val more: Boolean = false)

@Serializable data class Comment(
    val id: Int = 0, val author: String = "", val body: String = "", @SerialName("created_at") val createdAt: String = "",
    @Serializable(FI::class) val parent: Int = 0, val edited: Boolean = false, @Serializable(FI::class) val likes: Int = 0,
    val mine: Boolean = false, val own: Boolean = false, val del: Boolean = false,
)

@Serializable data class Reactor(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val kind: Int = 1)

// ---- Chat ----
@Serializable data class Peer(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0)
@Serializable data class ChatConv(
    val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0,
    val body: String = "", val mine: Boolean = false, val at: String? = null, @Serializable(FI::class) val mid: Int = 0, @Serializable(FI::class) val unread: Int = 0,
)
@Serializable data class RoomConv(
    val id: Int = 0, val name: String = "", @Serializable(FI::class) val members: Int = 0, val body: String = "", val from: String = "",
    val mine: Boolean = false, val at: String? = null, @Serializable(FI::class) val mid: Int = 0, @Serializable(FI::class) val unread: Int = 0,
)
@Serializable data class ReplyRef(val id: Int = 0, val from: String = "", val body: String = "")
@Serializable data class ChatMsg(
    val id: Int = 0, val mine: Boolean = false, val body: String = "", val img: String = "", val aud: String = "", @Serializable(FI::class) val dur: Int = 0,
    val at: String = "", val rx: Map<String, Int> = emptyMap(), @Serializable(FI::class) val myrx: Int = 0, val reply: ReplyRef? = null,
    val from: String? = null, @Serializable(FS::class) val skin: String? = null,
)
@Serializable data class ChatThread(
    val peer: Peer = Peer(), @Serializable(FI::class) val seenTo: Int = 0, val typing: Boolean = false, val blocked: Boolean = false, val msgs: List<ChatMsg> = emptyList(),
)
@Serializable data class RoomMember(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, val owner: Boolean = false)
@Serializable data class RoomInfo(val id: Int = 0, val name: String = "", val owner: Boolean = false, val members: List<RoomMember> = emptyList())
@Serializable data class RoomThread(val room: RoomInfo = RoomInfo(), val msgs: List<ChatMsg> = emptyList())
@Serializable data class RoomPut(val room: RoomInfo = RoomInfo())
@Serializable data class SentMsg(val ok: Boolean = false, val id: Int = 0, val m: ChatMsg? = null)
@Serializable data class ReactResult(val rx: Map<String, Int> = emptyMap(), val myrx: Int = 0)
@Serializable data class MsgHit(
    val id: Int = 0, val room: Int = 0, val rname: String = "", val with: String = "", val from: String = "", val mine: Boolean = false, val body: String = "", val at: String = "",
)

// ---- Avisos, amigos y perfiles ----
@Serializable data class Notif(
    val id: Int = 0, val type: String = "", @SerialName("post_id") val postId: Int? = null, val body: String = "", @Serializable(FI::class) val seen: Int = 0,
    @SerialName("created_at") val createdAt: String = "", val actor: String? = null, @Serializable(FS::class) val skin: String? = null, val ptype: String? = null,
)
@Serializable data class NotifCount(@Serializable(FI::class) val n: Int = 0, @Serializable(FI::class) val m: Int = 0)

@Serializable data class Friend(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0)
@Serializable data class FriendReq(val name: String = "", @Serializable(FS::class) val skin: String? = null, @SerialName("created_at") val createdAt: String = "", @Serializable(FI::class) val mutual: Int = 0)
@Serializable data class Suggestion(
    val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, @Serializable(FI::class) val level: Int = 0,
    @Serializable(FI::class) val mutual: Int = 0, val state: String = "none",
)
@Serializable data class FriendHit(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, val state: String = "none")
@Serializable data class FriendState(val state: String = "none")

@Serializable data class UserProfile(
    val name: String = "", @Serializable(FS::class) val skin: String? = null, val rango: String = "Ciudadano",
    @Serializable(FI::class) val level: Int = 0, @Serializable(FI::class) val rep: Int = 0, @SerialName("time_playing") @Serializable(FI::class) val timePlaying: Int = 0,
    @SerialName("reg_date") val regDate: String = "", @SerialName("last_connection") val lastConnection: String? = null, @Serializable(FI::class) val connected: Int = 0,
    @Serializable(FI::class) val posts: Int = 0, @Serializable(FI::class) val reels: Int = 0, @Serializable(FI::class) val likes: Int = 0, @Serializable(FI::class) val friends: Int = 0,
    val fstate: String = "none", val fl: List<Friend> = emptyList(), val avatar: String = "", val cover: String = "", val bio: String = "",
    val city: String = "", val job: String = "", val link: String = "", val bday: String = "", @Serializable(FI::class) val pin: Int = 0,
    @Serializable(FI::class) val followers: Int = 0, val following: Boolean = false, @Serializable(FI::class) val mutual: Int = 0, val locked: Boolean = false,
)
@Serializable data class PhotoRef(@SerialName("post_id") val postId: Int = 0, @Serializable(FI::class) val id: Int = 0)
@Serializable data class FollowResult(val following: Boolean = false)

// ---- Grupos, eventos, marketplace, búsqueda ----
@Serializable data class GroupItem(
    val id: Int = 0, val name: String = "", val about: String = "", @Serializable(FI::class) val priv: Int = 0, @Serializable(FI::class) val members: Int = 0,
    @Serializable(FI::class) val posts: Int = 0, @SerialName("created_at") val createdAt: String = "", @Serializable(FI::class) val role: Int = -1,
)
@Serializable data class GroupList(val mine: List<GroupItem> = emptyList(), val more: List<GroupItem> = emptyList())
@Serializable data class GroupMember(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, @Serializable(FI::class) val role: Int = 1)
@Serializable data class GroupDetail(
    val id: Int = 0, val name: String = "", val about: String = "", @Serializable(FI::class) val priv: Int = 0, @Serializable(FI::class) val members: Int = 0,
    @Serializable(FI::class) val posts: Int = 0, @Serializable(FI::class) val role: Int = -1, val isOwner: Boolean = false, val open: Boolean = false,
    val ms: List<GroupMember> = emptyList(), val pend: List<GroupMember> = emptyList(),
)
@Serializable data class EventItem(
    val id: Int = 0, val title: String = "", val about: String = "", val place: String = "", val starts: String = "", val owner: String = "",
    @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val going: Int = 0, @Serializable(FI::class) val maybe: Int = 0,
    @Serializable(FI::class) val mine: Int = 0, val own: Boolean = false,
)
@Serializable data class EventPerson(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val st: Int = 1)
@Serializable data class MarketItem(
    val id: Int = 0, val cat: String = "", val title: String = "", @Serializable(FL::class) val price: Long = 0, val body: String = "", val img: String = "",
    @Serializable(FI::class) val status: Int = 1, @SerialName("created_at") val createdAt: String = "", val seller: String = "",
    @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, val mine: Boolean = false,
)
@Serializable data class PersonHit(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FI::class) val connected: Int = 0, @Serializable(FI::class) val level: Int = 0)
@Serializable data class SearchResult(val people: List<PersonHit> = emptyList(), val posts: List<Post> = emptyList(), val market: List<MarketItem> = emptyList())
@Serializable data class UserHit(val name: String = "", @Serializable(FS::class) val skin: String? = null)
@Serializable data class Trend(val tag: String = "", @Serializable(FI::class) val n: Int = 0)
@Serializable data class Birthday(val name: String = "", @Serializable(FS::class) val skin: String? = null)

// ---- Cuenta ----
@Serializable data class Blocked(val name: String = "", @Serializable(FS::class) val skin: String? = null)
@Serializable data class Settings(
    @Serializable(FI::class) val msg: Int = 0, @Serializable(FI::class) val prof: Int = 0, @Serializable(FI::class) val tag: Int = 0,
    @Serializable(FI::class) val nmask: Int = 0, val blocked: List<Blocked> = emptyList(),
)
@Serializable data class SessionInfo(
    val id: String = "", val device: String = "", val ip: String = "", @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_seen") val lastSeen: String = "", val current: Boolean = false,
)
@Serializable data class TwoFa(val on: Boolean = false)
@Serializable data class TwoFaSetup(val secret: String = "", val uri: String = "")
@Serializable data class StaffMember(
    val name: String = "", @Serializable(FI::class) val level: Int = 0, @Serializable(FI::class) val connected: Int = 0,
    @SerialName("last_connection") val lastConnection: String? = null, @Serializable(FS::class) val skin: String? = null,
)
@Serializable data class TopEntry(
    val name: String = "", @Serializable(FI::class) val level: Int = 0, @Serializable(FI::class) val rep: Int = 0,
    @SerialName("time_playing") @Serializable(FI::class) val timePlaying: Int = 0, @Serializable(FS::class) val skin: String? = null,
)
@Serializable data class AppInfo(
    val available: Boolean = false, val version: String = "", @Serializable(FI::class) val code: Int = 0, @Serializable(FL::class) val size: Long = 0,
    val sha256: String = "", val updated: String = "",
)

// ---- Panel del staff ----
@Serializable data class DayCount(val d: String = "", @Serializable(FI::class) val n: Int = 0)
@Serializable data class TopAuthor(val name: String = "", @Serializable(FI::class) val n: Int = 0)
@Serializable data class AdminStats(
    val totals: Map<String, Long> = emptyMap(), val series: Map<String, List<DayCount>> = emptyMap(), val top: List<TopAuthor> = emptyList(),
)
@Serializable data class Ban(val name: String = "", @Serializable(FS::class) val skin: String? = null, @Serializable(FL::class) val until: Long = 0, val reason: String = "", val staff: String? = null)
@Serializable data class Report(
    val id: Int = 0, val kind: String = "", @Serializable(FI::class) val target: Int = 0, val reason: String = "", val note: String = "",
    @SerialName("created_at") val createdAt: String = "", val reporter: String? = null, val info: String = "", val href: String = "",
)
@Serializable data class ModLog(val id: Int = 0, val staff: String = "", val action: String = "", val target: String = "", val detail: String = "", @SerialName("created_at") val createdAt: String = "")
@Serializable data class VerifiedUser(val name: String = "", @Serializable(FS::class) val skin: String? = null)
@Serializable data class Words(val words: List<String> = emptyList())
@Serializable data class StoryViewer(val name: String = "", @Serializable(FS::class) val skin: String? = null)
@Serializable data class Created(val ok: Boolean = false, val id: Int = 0)
@Serializable data class UploadedMedia(val id: Int = 0, val kind: String = "")
@Serializable data class MyFollow(val ok: Boolean = false, val following: Boolean = false)
@Serializable data class LikeResult(val liked: Boolean = false, @Serializable(FI::class) val mine: Int = 0, @Serializable(FI::class) val likes: Int = 0, val rk: String = "")
@Serializable data class SaveResult(val saved: Boolean = false, @Serializable(FI::class) val saves: Int = 0)
@Serializable data class PreviewData(val title: String = "", val descr: String = "", val img: String = "", val host: String = "")

typealias Json_ = JsonElement
