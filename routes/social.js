// Funciones sociales: foto de perfil / banner / biografía, y chat privado entre jugadores.
module.exports = (app, { sec, friendIds, express, str, pid, SKIN_COL, SKIN_URL, q, sessionId, hit, me }) => {
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

// ---- Chat privado. web_msgs: a = quien envía, b = quien recibe ----
const unreadMsgs = async (id) => Number((await q("SELECT COUNT(*) AS n FROM web_msgs WHERE b = ? AND seen = 0", [id]))[0].n);
app.get("/api/chat", async (req, res) => { // mis conversaciones: último mensaje y no leídos de cada una
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const cv = await q("SELECT IF(a = ?, b, a) AS peer, MAX(id) AS mid, SUM(b = ? AND seen = 0) AS unread FROM web_msgs WHERE (a = ? OR b = ?) AND id > COALESCE((SELECT upto FROM web_chat_clear c WHERE c.player_id = ? AND c.peer = IF(a = ?, b, a)), 0) GROUP BY peer ORDER BY mid DESC LIMIT 60", [id, id, id, id, id, id]);
  if (!cv.length) return res.json([]);
  const [ms, ps] = await Promise.all([q("SELECT id, a, body, img, created_at FROM web_msgs WHERE id IN (?)", [cv.map((c) => c.mid)]), q(`SELECT id, name, ${SKIN_COL} AS skin, connected FROM player WHERE id IN (?)`, [cv.map((c) => c.peer)])]);
  const mm = new Map(ms.map((m) => [Number(m.id), m])), pm = new Map(ps.map((p) => [Number(p.id), p]));
  res.json(cv.filter((c) => pm.has(Number(c.peer))).map((c) => { const m = mm.get(Number(c.mid)) || {}, p = pm.get(Number(c.peer)); return { name: p.name, skin: p.skin, connected: p.connected, body: String(m.body || (m.img ? "📷 Foto" : "")).slice(0, 80), mine: Number(m.a) === id, at: m.created_at, unread: Number(c.unread) || 0 }; }));
});
app.get("/api/chat/count", async (req, res) => { const id = sessionId(req); res.json({ n: id ? await unreadMsgs(id).catch(() => 0) : 0 }); });
app.get("/api/chat/:name", async (req, res) => { // mensajes con un jugador (?after=id trae solo los nuevos); marca como leídos los recibidos
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  const upto = Number(((await q("SELECT upto FROM web_chat_clear WHERE player_id = ? AND peer = ?", [id, p.id]))[0] || {}).upto) || 0; // lo que borré de mi lado
  const after = Math.max(pid(req.query.after), upto);
  const rows = req.query.after !== undefined ? await q("SELECT id, a, body, img, seen, created_at FROM web_msgs WHERE ((a = ? AND b = ?) OR (a = ? AND b = ?)) AND id > ? ORDER BY id LIMIT 100", [id, p.id, p.id, id, after])
    : (await q("SELECT id, a, body, img, seen, created_at FROM web_msgs WHERE ((a = ? AND b = ?) OR (a = ? AND b = ?)) AND id > ? ORDER BY id DESC LIMIT 60", [id, p.id, p.id, id, upto])).reverse();
  await q("UPDATE web_msgs SET seen = 1 WHERE a = ? AND b = ? AND seen = 0", [p.id, id]);
  const seenTo = Number((await q("SELECT COALESCE(MAX(id), 0) AS n FROM web_msgs WHERE a = ? AND b = ? AND seen = 1", [id, p.id]))[0].n); // hasta dónde leyó el otro
  res.json({ peer: { name: p.name, skin: p.skin, connected: p.connected }, seenTo, typing: (TYPING.get(p.id + ">" + id) || 0) > Date.now() - 5000, blocked: await sec.isBlocked(id, p.id), msgs: rows.map((m) => ({ id: m.id, mine: Number(m.a) === id, body: m.body, img: m.img ? `/img/${m.img}` : "", at: m.created_at })) });
});
// «Escribiendo…»: en memoria, dura 5 s
const TYPING = new Map();
setInterval(() => { const lim = Date.now() - 10000; for (const [k, v] of TYPING) if (v < lim) TYPING.delete(k); }, 30000).unref();
app.post("/api/chat/:name/typing", async (req, res) => {
  const id = sessionId(req), p = id ? await player(str(req.params.name)) : null;
  if (p) TYPING.set(id + ">" + p.id, Date.now());
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
app.post("/api/chat/:name", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const p = await player(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  if (Number(p.id) === Number(u.id)) return res.status(400).json({ error: "No puedes escribirte a ti mismo" });
  const body = str(req.body.body).trim().slice(0, 1000);
  let img = null; // foto opcional (llega ya reducida desde el navegador)
  if (req.body.data) {
    const m = /^data:image\/(jpeg|png|webp);base64,([A-Za-z0-9+/=]+)$/.exec(str(req.body.data));
    const buf = m && m[2].length <= 1250000 ? Buffer.from(m[2], "base64") : null;
    const magic = buf && ((m[1] === "jpeg" && buf[0] === 0xff && buf[1] === 0xd8) || (m[1] === "png" && buf[0] === 0x89 && buf[1] === 0x50) || (m[1] === "webp" && buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP"));
    if (!magic || buf.length > 900 * 1024) return res.status(400).json({ error: "Imagen no válida o muy pesada (máx. 900 KB)" });
    img = { mime: "image/" + m[1], buf };
  }
  if (!body && !img) return res.status(400).json({ error: "Escribe un mensaje" });
  if (await sec.isBlocked(u.id, p.id)) return res.status(403).json({ error: "No puedes escribirle a este jugador" });
  const pv = Number((await sec.getSettings(p.id)).msg); // quién puede escribirle: 0 todos, 1 solo amigos, 2 nadie
  if (pv === 2 || (pv === 1 && !(await friendIds(p.id)).map(Number).includes(Number(u.id)))) return res.status(403).json({ error: pv === 2 ? "Este jugador no recibe mensajes" : "Este jugador solo recibe mensajes de sus amigos" });
  if (!hit("ch|" + u.id, 30, 60000)) return res.status(429).json({ error: "Vas muy rápido. Espera un momento." });
  const iid = img ? (await q("INSERT INTO web_images (mime, data) VALUES (?, ?)", [img.mime, img.buf])).insertId : null;
  const r = await q("INSERT INTO web_msgs (a, b, body, img) VALUES (?, ?, ?, ?)", [u.id, p.id, body, iid]);
  TYPING.delete(u.id + ">" + p.id);
  res.json({ ok: true, id: r.insertId, img: iid ? `/img/${iid}` : "" });
});
app.delete("/api/chat/msg/:id(\\d+)", async (req, res) => { // borrar un mensaje propio
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const r = await q("DELETE FROM web_msgs WHERE id = ? AND a = ?", [pid(req.params.id), id]);
  res.json({ ok: r.affectedRows > 0 });
});
return { loadAv, unreadMsgs };
};
