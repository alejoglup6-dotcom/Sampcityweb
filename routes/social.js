// Funciones sociales: foto de perfil / banner / biografía, y chat privado entre jugadores.
module.exports = (app, { vivo, sec, friendIds, express, str, pid, SKIN_COL, SKIN_URL, q, sessionId, hit, me }) => {
const NAME = /^\w{1,24}$/;
const player = async (name) => (NAME.test(name) ? (await q(`SELECT id, name, ${SKIN_COL} AS skin, connected FROM player WHERE name = ?`, [name]))[0] : null);

// ---- Perfil: foto, banner y biografía ----
// Fotos de perfil en memoria (nombre → id de imagen); se refresca cada minuto por si hay varios procesos
let AV = new Map();
const loadAv = async () => { try { AV = new Map((await q("SELECT p.name, w.avatar FROM web_profile w JOIN player p ON p.id = w.player_id WHERE w.avatar IS NOT NULL")).map((r) => [r.name.toLowerCase(), r.avatar])); } catch {} };
setInterval(loadAv, 60000).unref();
// /av/Nombre?s=skin → la foto personalizada si existe; si no, la skin del juego
app.get("/av/:name", (req, res) => {
  const n = str(req.params.name), s = str(req.query.s), id = NAME.test(n) ? AV.get(n.toLowerCase()) : null;
  res.set("Cache-Control", "public, max-age=120");
  if (id) return res.redirect(302, `/img/${id}`);
  if (/^\w{1,6}$/.test(s)) return res.redirect(302, SKIN_URL.replace("{id}", encodeURIComponent(s)));
  res.status(404).end();
});
app.get("/api/profile/:name", async (req, res) => {
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "No existe" });
  const w = (await q("SELECT avatar, cover, bio FROM web_profile WHERE player_id = ?", [p.id]))[0] || {};
  res.json({ avatar: w.avatar ? `/img/${w.avatar}` : "", cover: w.cover ? `/img/${w.cover}` : "", bio: w.bio || "" });
});
// La imagen llega ya reducida desde el navegador (igual que /api/photos)
app.post("/api/profile/photo", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("pf|" + u.id, 12, 3600000)) return res.status(429).json({ error: "Cambiaste la foto muchas veces. Espera un rato." });
  const col = req.body.kind === "cover" ? "cover" : req.body.kind === "avatar" ? "avatar" : "";
  const m = /^data:image\/(jpeg|png|webp);base64,([A-Za-z0-9+/=]+)$/.exec(str(req.body.data));
  if (!col || !m || m[2].length > 1250000) return res.status(400).json({ error: "Elige una imagen" });
  const buf = Buffer.from(m[2], "base64");
  const magic = (m[1] === "jpeg" && buf[0] === 0xff && buf[1] === 0xd8) || (m[1] === "png" && buf[0] === 0x89 && buf[1] === 0x50) || (m[1] === "webp" && buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP");
  if (!magic || buf.length > 900 * 1024) return res.status(400).json({ error: "Imagen no válida o muy pesada (máx. 900 KB)" });
  const old = (await q(`SELECT ${col} AS i FROM web_profile WHERE player_id = ?`, [u.id]))[0];
  const id = (await q("INSERT INTO web_images (mime, data) VALUES (?, ?)", ["image/" + m[1], buf])).insertId;
  await q(`INSERT INTO web_profile (player_id, ${col}) VALUES (?, ?) ON DUPLICATE KEY UPDATE ${col} = VALUES(${col})`, [u.id, id]);
  if (old && old.i) await q("DELETE FROM web_images WHERE id = ?", [old.i]);
  if (col === "avatar") AV.set(u.name.toLowerCase(), id);
  res.json({ ok: true, url: `/img/${id}` });
});
app.delete("/api/profile/photo", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const col = req.query.kind === "cover" ? "cover" : req.query.kind === "avatar" ? "avatar" : "";
  if (!col) return res.status(400).json({ error: "Faltan datos" });
  const old = (await q(`SELECT ${col} AS i FROM web_profile WHERE player_id = ?`, [u.id]))[0];
  if (old && old.i) { await q(`UPDATE web_profile SET ${col} = NULL WHERE player_id = ?`, [u.id]); await q("DELETE FROM web_images WHERE id = ?", [old.i]); }
  if (col === "avatar") AV.delete(u.name.toLowerCase());
  res.json({ ok: true });
});
app.post("/api/profile/bio", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("pb|" + u.id, 20, 3600000)) return res.status(429).json({ error: "Demasiados cambios. Espera un rato." });
  const bio = str(req.body.bio).replace(/\s+/g, " ").trim().slice(0, 160);
  await q("INSERT INTO web_profile (player_id, bio) VALUES (?, ?) ON DUPLICATE KEY UPDATE bio = VALUES(bio)", [u.id, bio]);
  res.json({ ok: true, bio });
});

// ---- Chat. web_msgs: a = quien envía, b = quien recibe (privado) · room = chat de grupo (entonces b = 0) ----
const like = (v) => "%" + v.replace(/[\\%_]/g, "\\$&") + "%";
const NAMES = new Map(); // id → nombre (para los avisos en vivo)
const nameOf = async (id) => { let n = NAMES.get(Number(id)); if (!n) { n = ((await q("SELECT name FROM player WHERE id = ?", [id]))[0] || {}).name || ""; if (NAMES.size > 5000) NAMES.clear(); if (n) NAMES.set(Number(id), n); } return n; };
const unreadMsgs = async (id) => Number((await q("SELECT COUNT(*) AS n FROM web_msgs WHERE b = ? AND seen = 0", [id]))[0].n)
  + Number((await q("SELECT COUNT(*) AS n FROM web_room_members rm JOIN web_msgs m ON m.room = rm.room_id AND m.id > rm.last_read AND m.a <> rm.player_id WHERE rm.player_id = ?", [id]))[0].n);
const MSEL = "SELECT m.id, m.a, m.body, m.img, m.seen, m.created_at, m.reply_to FROM web_msgs m";
// Foto o audio adjunto: llega como data: URL (la foto ya reducida en el navegador; el audio grabado con el micrófono)
function attachment(data) {
  const m = /^data:(image\/(?:jpeg|png|webp)|audio\/(?:webm|mp4|ogg))(?:;[\w=.\- ]+)*;base64,([A-Za-z0-9+/=]+)$/.exec(str(data));
  const buf = m && m[2].length <= 1250000 ? Buffer.from(m[2], "base64") : null, t = m ? m[1] : "";
  const ok = buf && buf.length <= 900 * 1024 && ((t === "image/jpeg" && buf[0] === 0xff && buf[1] === 0xd8) || (t === "image/png" && buf[0] === 0x89 && buf[1] === 0x50) || (t === "image/webp" && buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP")
    || (t === "audio/webm" && buf.readUInt32BE(0) === 0x1a45dfa3) || (t === "audio/ogg" && buf.toString("ascii", 0, 4) === "OggS") || (t === "audio/mp4" && buf.toString("ascii", 4, 8) === "ftyp"));
  return ok ? { mime: t, buf } : null;
}
// Da forma a los mensajes: adjunto, mensaje al que responde, reacciones y (en grupos) quién lo envió
async function outMsgs(rows, uid, withFrom = false) {
  if (!rows.length) return [];
  const ids = rows.map((r) => r.id), rids = [...new Set(rows.map((r) => Number(r.reply_to)).filter(Boolean))], iids = rows.map((r) => r.img).filter(Boolean);
  const [rx, rp, im, ps] = await Promise.all([
    q("SELECT msg_id, kind, COUNT(*) AS n, SUM(player_id = ?) AS mine FROM web_msg_rx WHERE msg_id IN (?) GROUP BY msg_id, kind", [uid, ids]),
    rids.length ? q("SELECT m.id, m.body, m.img, p.name FROM web_msgs m LEFT JOIN player p ON p.id = m.a WHERE m.id IN (?)", [rids]) : [],
    iids.length ? q("SELECT id, mime FROM web_images WHERE id IN (?)", [iids]) : [],
    withFrom ? q(`SELECT id, name, ${SKIN_COL} AS skin FROM player WHERE id IN (?)`, [[...new Set(rows.map((r) => r.a))]]) : [],
  ]);
  const mime = new Map(im.map((x) => [Number(x.id), x.mime])), pm = new Map(ps.map((x) => [Number(x.id), x]));
  return rows.map((m) => {
    const r = rp.find((x) => Number(x.id) === Number(m.reply_to)), au = String(mime.get(Number(m.img)) || "").startsWith("audio/"), f = pm.get(Number(m.a));
    const o = { id: m.id, mine: Number(m.a) === Number(uid), body: m.body, img: m.img && !au ? `/img/${m.img}` : "", aud: m.img && au ? `/img/${m.img}` : "", at: m.created_at, rx: {}, myrx: 0 };
    for (const x of rx) if (Number(x.msg_id) === Number(m.id)) { o.rx[x.kind] = Number(x.n); if (Number(x.mine)) o.myrx = Number(x.kind); }
    if (m.reply_to) o.reply = r ? { id: r.id, from: r.name || "", body: String(r.body || (r.img ? "📎" : "")).slice(0, 90) } : { id: 0, from: "", body: "Mensaje eliminado" };
    if (withFrom) { o.from = f ? f.name : ""; o.skin = f ? f.skin : null; }
    return o;
  });
}
const preview = (body, att) => String(body || (att ? (att.mime.startsWith("audio/") ? "🎤 Audio" : "📷 Foto") : "")).slice(0, 90);
const roomMember = async (rid, uid) => (await q("SELECT 1 FROM web_room_members WHERE room_id = ? AND player_id = ?", [rid, uid])).length > 0;
// ¿puede `u` escribirle a `p`? (bloqueos y «quién puede enviarme mensajes»)
async function canWrite(u, p) {
  if (await sec.isBlocked(u.id, p.id)) return "No puedes escribirle a este jugador";
  const pv = Number((await sec.getSettings(p.id)).msg);
  if (pv === 2) return "Este jugador no recibe mensajes";
  if (pv === 1 && !(await friendIds(p.id)).map(Number).includes(Number(u.id))) return "Este jugador solo recibe mensajes de sus amigos";
  return "";
}

app.get("/api/chat", async (req, res) => { // mis conversaciones privadas: último mensaje y no leídos de cada una
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const cv = await q("SELECT IF(a = ?, b, a) AS peer, MAX(id) AS mid, SUM(b = ? AND seen = 0) AS unread FROM web_msgs WHERE room IS NULL AND (a = ? OR b = ?) AND id > COALESCE((SELECT upto FROM web_chat_clear c WHERE c.player_id = ? AND c.peer = IF(a = ?, b, a)), 0) GROUP BY peer ORDER BY mid DESC LIMIT 60", [id, id, id, id, id, id]);
  if (!cv.length) return res.json([]);
  const [ms, ps] = await Promise.all([q("SELECT m.id, m.a, m.body, m.img, m.created_at, (SELECT mime FROM web_images i WHERE i.id = m.img) AS mime FROM web_msgs m WHERE m.id IN (?)", [cv.map((c) => c.mid)]), q(`SELECT id, name, ${SKIN_COL} AS skin, connected FROM player WHERE id IN (?)`, [cv.map((c) => c.peer)])]);
  const mm = new Map(ms.map((m) => [Number(m.id), m])), pm = new Map(ps.map((p) => [Number(p.id), p]));
  res.json(cv.filter((c) => pm.has(Number(c.peer))).map((c) => { const m = mm.get(Number(c.mid)) || {}, p = pm.get(Number(c.peer)); return { name: p.name, skin: p.skin, connected: p.connected, body: preview(m.body, m.img ? { mime: m.mime || "" } : null), mine: Number(m.a) === id, at: m.created_at, mid: Number(c.mid), unread: Number(c.unread) || 0 }; }));
});
app.get("/api/chat/count", async (req, res) => { const id = sessionId(req); res.json({ n: id ? await unreadMsgs(id).catch(() => 0) : 0 }); });
// Buscar en mis mensajes (privados y de grupo)
app.get("/api/chat/search", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const s = str(req.query.q).trim().slice(0, 40); if (s.length < 2) return res.json([]);
  if (!hit("cs|" + id, 30, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  const rows = await q(`SELECT m.id, m.a, m.b, m.room, m.body, m.created_at, pa.name AS fa, pb.name AS fb, r.name AS rname FROM web_msgs m LEFT JOIN player pa ON pa.id = m.a LEFT JOIN player pb ON pb.id = m.b LEFT JOIN web_rooms r ON r.id = m.room
    WHERE m.body LIKE ? AND ((m.room IS NULL AND (m.a = ? OR m.b = ?) AND m.id > COALESCE((SELECT upto FROM web_chat_clear c WHERE c.player_id = ? AND c.peer = IF(m.a = ?, m.b, m.a)), 0)) OR m.room IN (SELECT room_id FROM web_room_members WHERE player_id = ?)) ORDER BY m.id DESC LIMIT 40`, [like(s), id, id, id, id, id]);
  res.json(rows.map((m) => ({ id: m.id, room: m.room ? Number(m.room) : 0, rname: m.rname || "", with: m.room ? "" : Number(m.a) === id ? m.fb : m.fa, from: m.fa || "", mine: Number(m.a) === id, body: m.body.slice(0, 160), at: m.created_at })));
});
app.get("/api/chat/:name", async (req, res) => { // mensajes con un jugador (?after=id trae solo los nuevos); marca como leídos los recibidos
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  const upto = Number(((await q("SELECT upto FROM web_chat_clear WHERE player_id = ? AND peer = ?", [id, p.id]))[0] || {}).upto) || 0; // lo que borré de mi lado
  const after = Math.max(pid(req.query.after), upto);
  const rows = req.query.after !== undefined ? await q(MSEL + " WHERE ((m.a = ? AND m.b = ?) OR (m.a = ? AND m.b = ?)) AND m.id > ? ORDER BY m.id LIMIT 100", [id, p.id, p.id, id, after])
    : (await q(MSEL + " WHERE ((m.a = ? AND m.b = ?) OR (m.a = ? AND m.b = ?)) AND m.id > ? ORDER BY m.id DESC LIMIT 60", [id, p.id, p.id, id, upto])).reverse();
  const rd = await q("UPDATE web_msgs SET seen = 1 WHERE a = ? AND b = ? AND seen = 0", [p.id, id]);
  if (rd.affectedRows) vivo.send(p.id, "seen", { by: await nameOf(id) }); // «visto» al instante
  const seenTo = Number((await q("SELECT COALESCE(MAX(id), 0) AS n FROM web_msgs WHERE a = ? AND b = ? AND seen = 1", [id, p.id]))[0].n); // hasta dónde leyó el otro
  res.json({ peer: { name: p.name, skin: p.skin, connected: p.connected }, seenTo, typing: (TYPING.get(p.id + ">" + id) || 0) > Date.now() - 5000, blocked: await sec.isBlocked(id, p.id), msgs: await outMsgs(rows, id) });
});
// «Escribiendo…»: en memoria, dura 5 s
const TYPING = new Map();
setInterval(() => { const lim = Date.now() - 10000; for (const [k, v] of TYPING) if (v < lim) TYPING.delete(k); }, 30000).unref();
app.post("/api/chat/:name/typing", async (req, res) => {
  const id = sessionId(req), p = id ? await player(str(req.params.name)) : null;
  if (p && !(await sec.isBlocked(id, p.id))) { TYPING.set(id + ">" + p.id, Date.now()); vivo.send(p.id, "typing", { from: await nameOf(id) }); }
  res.json({ ok: true });
});
// Borrar la conversación solo de mi lado
app.delete("/api/chat/:name", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  const top = Number((await q("SELECT COALESCE(MAX(id), 0) AS n FROM web_msgs WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", [id, p.id, p.id, id]))[0].n);
  await q("INSERT INTO web_chat_clear (player_id, peer, upto) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE upto = VALUES(upto)", [id, p.id, top]);
  await q("UPDATE web_msgs SET seen = 1 WHERE a = ? AND b = ? AND seen = 0", [p.id, id]);
  res.json({ ok: true });
});
// Guarda un mensaje (privado o de grupo) con su adjunto y la respuesta a otro mensaje
async function saveMsg(u, { peer = 0, room = null }, body, res) {
  const text = str(body.body).trim().slice(0, 1000), att = body.data ? attachment(body.data) : null;
  if (body.data && !att) { res.status(400).json({ error: "Archivo no válido o muy pesado (máx. 900 KB)" }); return null; }
  if (!text && !att) { res.status(400).json({ error: "Escribe un mensaje" }); return null; }
  if (!hit("ch|" + u.id, 30, 60000)) { res.status(429).json({ error: "Vas muy rápido. Espera un momento." }); return null; }
  let reply = pid(body.reply); // solo se puede responder a un mensaje de esta misma conversación
  if (reply && !(await q(room ? "SELECT 1 FROM web_msgs WHERE id = ? AND room = ?" : "SELECT 1 FROM web_msgs WHERE id = ? AND room IS NULL AND ((a = ? AND b = ?) OR (a = ? AND b = ?))", room ? [reply, room] : [reply, u.id, peer, peer, u.id])).length) reply = 0;
  const iid = att ? (await q("INSERT INTO web_images (mime, data) VALUES (?, ?)", [att.mime, att.buf])).insertId : null;
  const r = await q("INSERT INTO web_msgs (a, b, body, img, room, reply_to, seen) VALUES (?, ?, ?, ?, ?, ?, ?)", [u.id, peer, text, iid, room, reply || null, room ? 1 : 0]);
  const m = (await outMsgs(await q(MSEL + " WHERE m.id = ?", [r.insertId]), u.id, !!room))[0];
  return { id: r.insertId, text: preview(text, att), m };
}
app.post("/api/chat/:name", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  if (Number(p.id) === Number(u.id)) return res.status(400).json({ error: "No puedes escribirte a ti mismo" });
  const no = await canWrite(u, p); if (no) return res.status(403).json({ error: no });
  const s = await saveMsg(u, { peer: p.id }, req.body, res); if (!s) return;
  TYPING.delete(u.id + ">" + p.id);
  vivo.send(p.id, "msg", { from: u.name, id: s.id });
  vivo.push(p.id, { title: u.name.replace(/_/g, " "), body: s.text, url: "/mensajes?u=" + encodeURIComponent(u.name), tag: "chat-" + u.name });
  res.json({ ok: true, id: s.id, img: s.m.img, m: s.m });
});
async function dropMsgs(rows) { // borra mensajes con su adjunto y sus reacciones
  const ids = rows.map((r) => r.id), im = rows.map((r) => r.img).filter(Boolean); if (!ids.length) return;
  await q("DELETE FROM web_msg_rx WHERE msg_id IN (?)", [ids]); await q("DELETE FROM web_msgs WHERE id IN (?)", [ids]);
  if (im.length) await q("DELETE FROM web_images WHERE id IN (?)", [im]);
}
app.delete("/api/chat/msg/:id(\\d+)", async (req, res) => { // borrar un mensaje propio (también su foto o audio)
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const m = (await q("SELECT id, img, b, room FROM web_msgs WHERE id = ? AND a = ?", [pid(req.params.id), id]))[0];
  if (!m) return res.json({ ok: false });
  await dropMsgs([m]);
  const ev = { id: Number(m.id) };
  if (m.room) for (const x of await q("SELECT player_id FROM web_room_members WHERE room_id = ? AND player_id <> ?", [m.room, id])) vivo.send(x.player_id, "del", ev); else vivo.send(m.b, "del", ev);
  res.json({ ok: true });
});
// Reaccionar a un mensaje (kind 1-6; repetir la misma la quita)
app.post("/api/chat/msg/:id(\\d+)/react", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const mid = pid(req.params.id), m = (await q("SELECT id, a, b, room FROM web_msgs WHERE id = ?", [mid]))[0];
  if (!m || !(m.room ? await roomMember(m.room, id) : Number(m.a) === id || Number(m.b) === id)) return res.status(404).json({ error: "No existe" });
  if (!hit("cr|" + id, 60, 60000)) return res.status(429).json({ error: "Vas muy rápido. Espera un momento." });
  const kind = Math.min(6, Math.max(1, Math.floor(Number(req.body.kind)) || 1)), cur = (await q("SELECT kind FROM web_msg_rx WHERE msg_id = ? AND player_id = ?", [mid, id]))[0];
  let mine = kind;
  if (cur && Number(cur.kind) === kind) { mine = 0; await q("DELETE FROM web_msg_rx WHERE msg_id = ? AND player_id = ?", [mid, id]); }
  else await q("INSERT INTO web_msg_rx (msg_id, player_id, kind) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE kind = VALUES(kind)", [mid, id, kind]);
  const rx = {}; for (const x of await q("SELECT kind, COUNT(*) AS n FROM web_msg_rx WHERE msg_id = ? GROUP BY kind", [mid])) rx[x.kind] = Number(x.n);
  const ev = { id: mid, rx };
  if (m.room) for (const x of await q("SELECT player_id FROM web_room_members WHERE room_id = ? AND player_id <> ?", [m.room, id])) vivo.send(x.player_id, "rx", ev); else vivo.send(Number(m.a) === id ? m.b : m.a, "rx", ev);
  res.json({ ok: true, rx, myrx: mine });
});

// ---- Chats de grupo: web_rooms + web_room_members (last_read = último mensaje leído por cada miembro) ----
const ROOM_MAX = 30;
const roomOut = async (rid, uid) => {
  const r = (await q("SELECT id, name, owner FROM web_rooms WHERE id = ?", [rid]))[0]; if (!r) return null;
  const ms = await q(`SELECT p.id, p.name, p.${SKIN_COL} AS skin, p.connected FROM web_room_members rm JOIN player p ON p.id = rm.player_id WHERE rm.room_id = ? ORDER BY p.name`, [rid]);
  return { id: r.id, name: r.name, owner: Number(r.owner) === Number(uid), members: ms.map((m) => ({ name: m.name, skin: m.skin, connected: m.connected, owner: Number(m.id) === Number(r.owner) })) };
};
async function invitable(u, names) { // jugadores que `u` puede meter a un chat: existen, sin bloqueo y aceptan sus mensajes
  const list = [...new Set((Array.isArray(names) ? names : []).map(str).filter((n) => NAME.test(n)))].slice(0, ROOM_MAX), out = [];
  for (const n of list) { const p = await player(n); if (p && Number(p.id) !== Number(u.id) && !(await canWrite(u, p))) out.push(p); }
  return out;
}
app.get("/api/rooms", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const rows = await q(`SELECT r.id, r.name, rm.last_read, (SELECT COUNT(*) FROM web_room_members x WHERE x.room_id = r.id) AS members, (SELECT MAX(m.id) FROM web_msgs m WHERE m.room = r.id) AS mid,
    (SELECT COUNT(*) FROM web_msgs m WHERE m.room = r.id AND m.id > rm.last_read AND m.a <> ?) AS unread FROM web_room_members rm JOIN web_rooms r ON r.id = rm.room_id WHERE rm.player_id = ? ORDER BY COALESCE(mid, 0) DESC, r.id DESC LIMIT 60`, [id, id]);
  const mids = rows.map((r) => r.mid).filter(Boolean), ms = mids.length ? await q("SELECT m.id, m.a, m.body, m.img, m.created_at, p.name, (SELECT mime FROM web_images i WHERE i.id = m.img) AS mime FROM web_msgs m LEFT JOIN player p ON p.id = m.a WHERE m.id IN (?)", [mids]) : [];
  res.json(rows.map((r) => { const m = ms.find((x) => Number(x.id) === Number(r.mid)) || {}; return { id: r.id, name: r.name, members: Number(r.members), body: preview(m.body, m.img ? { mime: m.mime || "" } : null), from: m.name || "", mine: Number(m.a) === id, at: m.created_at || null, mid: Number(r.mid) || 0, unread: Number(r.unread) || 0 }; }));
});
app.post("/api/rooms", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const name = str(req.body.name).replace(/\s+/g, " ").trim().slice(0, 40);
  if (name.length < 2) return res.status(400).json({ error: "Ponle un nombre al grupo (mínimo 2 letras)" });
  if (!hit("rm|" + u.id, 5, 3600000)) return res.status(429).json({ error: "Creaste muchos grupos. Espera un rato." });
  const ps = (await invitable(u, req.body.members)).slice(0, ROOM_MAX - 1);
  if (!ps.length) return res.status(400).json({ error: "Agrega al menos a un jugador que pueda recibir tus mensajes" });
  const r = await q("INSERT INTO web_rooms (name, owner) VALUES (?, ?)", [name, u.id]);
  for (const x of [u, ...ps]) await q("INSERT IGNORE INTO web_room_members (room_id, player_id) VALUES (?, ?)", [r.insertId, x.id]);
  for (const x of ps) { vivo.send(x.id, "msg", { room: r.insertId }); vivo.push(x.id, { title: name, body: `${u.name.replace(/_/g, " ")} te agregó al grupo`, url: "/mensajes?g=" + r.insertId, tag: "room-" + r.insertId }); }
  res.json({ ok: true, id: r.insertId });
});
app.get("/api/rooms/:id(\\d+)", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const rid = pid(req.params.id); if (!(await roomMember(rid, id))) return res.status(404).json({ error: "Ese grupo no existe o ya no estás en él" });
  const rows = req.query.after !== undefined ? await q(MSEL + " WHERE m.room = ? AND m.id > ? ORDER BY m.id LIMIT 100", [rid, pid(req.query.after)]) : (await q(MSEL + " WHERE m.room = ? ORDER BY m.id DESC LIMIT 60", [rid])).reverse();
  if (rows.length) await q("UPDATE web_room_members SET last_read = GREATEST(last_read, ?) WHERE room_id = ? AND player_id = ?", [rows.at(-1).id, rid, id]);
  res.json({ room: await roomOut(rid, id), msgs: await outMsgs(rows, id, true) });
});
app.post("/api/rooms/:id(\\d+)", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const rid = pid(req.params.id), room = (await q("SELECT name FROM web_rooms WHERE id = ?", [rid]))[0];
  if (!room || !(await roomMember(rid, u.id))) return res.status(404).json({ error: "Ese grupo no existe o ya no estás en él" });
  const s = await saveMsg(u, { room: rid }, req.body, res); if (!s) return;
  await q("UPDATE web_room_members SET last_read = ? WHERE room_id = ? AND player_id = ?", [s.id, rid, u.id]);
  const bl = new Set(await sec.blockedIds(u.id));
  for (const x of await q("SELECT player_id FROM web_room_members WHERE room_id = ? AND player_id <> ?", [rid, u.id])) {
    vivo.send(x.player_id, "msg", { room: rid, id: s.id });
    if (!bl.has(Number(x.player_id))) vivo.push(x.player_id, { title: room.name, body: `${u.name.replace(/_/g, " ")}: ${s.text}`, url: "/mensajes?g=" + rid, tag: "room-" + rid });
  }
  res.json({ ok: true, id: s.id, m: s.m });
});
app.put("/api/rooms/:id(\\d+)", async (req, res) => { // el creador cambia el nombre o agrega jugadores
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const rid = pid(req.params.id), r = (await q("SELECT owner FROM web_rooms WHERE id = ?", [rid]))[0];
  if (!r || !(await roomMember(rid, u.id))) return res.status(404).json({ error: "Ese grupo no existe" });
  if (Number(r.owner) !== Number(u.id)) return res.status(403).json({ error: "Solo el creador del grupo" });
  const name = str(req.body.name).replace(/\s+/g, " ").trim().slice(0, 40);
  if (name.length >= 2) await q("UPDATE web_rooms SET name = ? WHERE id = ?", [name, rid]);
  if (Array.isArray(req.body.add)) {
    const n = Number((await q("SELECT COUNT(*) AS n FROM web_room_members WHERE room_id = ?", [rid]))[0].n), top = Number((await q("SELECT COALESCE(MAX(id), 0) AS n FROM web_msgs WHERE room = ?", [rid]))[0].n);
    for (const x of (await invitable(u, req.body.add)).slice(0, Math.max(0, ROOM_MAX - n))) { await q("INSERT IGNORE INTO web_room_members (room_id, player_id, last_read) VALUES (?, ?, ?)", [rid, x.id, top]); vivo.send(x.id, "msg", { room: rid }); }
  }
  res.json({ ok: true, room: await roomOut(rid, u.id) });
});
app.delete("/api/rooms/:id(\\d+)/members/:name", async (req, res) => { // salir del grupo, o (el creador) sacar a alguien
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const rid = pid(req.params.id), r = (await q("SELECT owner FROM web_rooms WHERE id = ?", [rid]))[0], p = await player(str(req.params.name));
  if (!r || !p || !(await roomMember(rid, u.id))) return res.status(404).json({ error: "No existe" });
  const self = Number(p.id) === Number(u.id);
  if (!self && Number(r.owner) !== Number(u.id)) return res.status(403).json({ error: "Solo el creador del grupo" });
  await q("DELETE FROM web_room_members WHERE room_id = ? AND player_id = ?", [rid, p.id]);
  const left = await q("SELECT player_id FROM web_room_members WHERE room_id = ? ORDER BY joined_at LIMIT 1", [rid]);
  if (!left.length) { await dropMsgs(await q("SELECT id, img FROM web_msgs WHERE room = ?", [rid])); await q("DELETE FROM web_rooms WHERE id = ?", [rid]); } // sin miembros: se borra con sus mensajes
  else if (Number(p.id) === Number(r.owner)) await q("UPDATE web_rooms SET owner = ? WHERE id = ?", [left[0].player_id, rid]); // se fue el creador: pasa al más antiguo
  vivo.send(p.id, "msg", { room: rid });
  res.json({ ok: true });
});
return { loadAv, unreadMsgs };
};
