// Más funciones sociales: quién reaccionó, ocultar y fijar publicaciones, seguir, reacciones y edición de
// comentarios, información del perfil y buscador global.
module.exports = (app, { canSee, sec, attach, friendIds, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, notify }) => {
const NAME = /^\w{1,24}$/, like = (s) => "%" + s.replace(/[\\%_]/g, "\\$&") + "%";
const byName = async (n) => (NAME.test(n) ? (await q("SELECT id, name FROM player WHERE name = ?", [n]))[0] : null);

// Quién reaccionó a una publicación
app.get("/api/posts/:id(\\d+)/likes", async (req, res) => {
  if (!(await canSee(pid(req.params.id), sessionId(req) || 0))) return res.status(404).json({ error: "No existe" });
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin, l.kind FROM web_likes l JOIN player p ON p.id = l.player_id WHERE l.post_id = ? ORDER BY l.kind, p.name LIMIT 300`, [pid(req.params.id)]));
});
// Ocultar una publicación de mi feed (se puede deshacer)
app.post("/api/posts/:id(\\d+)/hide", async (req, res) => {
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const del = await q("DELETE FROM web_hidden WHERE player_id = ? AND post_id = ?", [id, pid(req.params.id)]);
  if (!del.affectedRows) await q("INSERT IGNORE INTO web_hidden (player_id, post_id) VALUES (?, ?)", [id, pid(req.params.id)]);
  res.json({ hidden: !del.affectedRows });
});
// Fijar una publicación propia arriba del perfil (una sola)
app.post("/api/posts/:id(\\d+)/pin", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), p = (await q("SELECT author, type FROM web_posts WHERE id = ?", [id]))[0];
  if (!p || p.author !== u.name || p.type !== "post") return res.status(403).json({ error: "Solo puedes fijar tus publicaciones" });
  const cur = Number(((await q("SELECT pin FROM web_profile WHERE player_id = ?", [u.id]))[0] || {}).pin) || 0, pin = cur === id ? null : id;
  await q("INSERT INTO web_profile (player_id, pin) VALUES (?, ?) ON DUPLICATE KEY UPDATE pin = VALUES(pin)", [u.id, pin]);
  res.json({ pinned: !!pin });
});
// Seguir / dejar de seguir (ver sus publicaciones públicas sin ser amigos)
app.post("/api/follow/:name", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const p = await byName(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  if (Number(p.id) === Number(u.id)) return res.status(400).json({ error: "No puedes seguirte a ti mismo" });
  if (await sec.isBlocked(u.id, p.id)) return res.status(403).json({ error: "No puedes seguir a este jugador" });
  if (!hit("fo|" + u.id, 40, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  const del = await q("DELETE FROM web_follows WHERE a = ? AND b = ?", [u.id, p.id]);
  if (!del.affectedRows) { await q("INSERT IGNORE INTO web_follows (a, b) VALUES (?, ?)", [u.id, p.id]); await notify(p.id, u, "follow"); }
  res.json({ following: !del.affectedRows, followers: Number((await q("SELECT COUNT(*) AS n FROM web_follows WHERE b = ?", [p.id]))[0].n) });
});
// Comentarios: me gusta y editar
app.post("/api/comments/:id(\\d+)/like", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), c = (await q("SELECT player_id, post_id, body FROM web_comments WHERE id = ?", [id]))[0];
  if (!c) return res.status(404).json({ error: "No existe" });
  const del = await q("DELETE FROM web_clikes WHERE comment_id = ? AND player_id = ?", [id, u.id]);
  if (!del.affectedRows) { await q("INSERT IGNORE INTO web_clikes (comment_id, player_id) VALUES (?, ?)", [id, u.id]); await notify(c.player_id, u, "like_c", c.post_id, c.body); }
  res.json({ liked: !del.affectedRows, likes: Number((await q("SELECT COUNT(*) AS n FROM web_clikes WHERE comment_id = ?", [id]))[0].n) });
});
app.put("/api/comments/:id(\\d+)", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const body = str(req.body.body).trim().slice(0, 300); if (!body) return res.status(400).json({ error: "Escribe algo" });
  const r = await q("UPDATE web_comments SET body = ?, edited_at = NOW() WHERE id = ? AND player_id = ?", [body, pid(req.params.id), u.id]);
  if (!r.affectedRows) return res.status(403).json({ error: "No tienes permiso" });
  res.json({ ok: true });
});
// Información del perfil: ciudad, ocupación, enlace y cumpleaños (día y mes)
app.post("/api/profile/info", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("pi|" + u.id, 20, 3600000)) return res.status(429).json({ error: "Demasiados cambios. Espera un rato." });
  const t = (v, n) => str(v).replace(/\s+/g, " ").trim().slice(0, n), city = t(req.body.city, 40), job = t(req.body.job, 60);
  let link = t(req.body.link, 120), bday = str(req.body.bday);
  if (link && !/^https:\/\/[^\s"'<>]{4,115}$/.test(link)) return res.status(400).json({ error: "El enlace debe empezar con https://" });
  if (bday && !/^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/.test(bday)) bday = "";
  await q("INSERT INTO web_profile (player_id, city, job, link, bday) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE city = VALUES(city), job = VALUES(job), link = VALUES(link), bday = VALUES(bday)", [u.id, city, job, link, bday]);
  res.json({ ok: true });
});
// Cumpleaños de hoy entre mis amigos
app.get("/api/birthdays", async (req, res) => {
  const id = sessionId(req); if (!id) return res.json([]);
  const ids = await friendIds(id); if (!ids.length) return res.json([]);
  const d = new Date(), md = String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin FROM web_profile w JOIN player p ON p.id = w.player_id WHERE w.bday = ? AND w.player_id IN (?) LIMIT 20`, [md, ids]));
});
// Buscador global: personas, publicaciones públicas (también #etiquetas) y anuncios
app.get("/api/search", async (req, res) => {
  const uid = sessionId(req) || 0, s = str(req.query.q).trim().slice(0, 40), t = str(req.query.t);
  if (s.length < 2) return res.json({ people: [], posts: [], market: [] });
  if (!hit("se|" + req.ip, 40, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  const bl = uid ? await sec.blockedIds(uid) : [], out = { people: [], posts: [], market: [] }, all = !["people", "posts", "market"].includes(t);
  if (all || t === "people") out.people = (await q(`SELECT id, name, ${SKIN_COL} AS skin, connected, level FROM player WHERE name LIKE ? ORDER BY connected DESC, name LIMIT ?`, [like(s.replace(/\s+/g, "_")), all ? 5 : 30])).filter((p) => !bl.includes(Number(p.id))).map((p) => ({ name: p.name, skin: p.skin, connected: p.connected, level: p.level }));
  if (all || t === "posts") out.posts = await attach(await q(`SELECT p.id, p.type, p.title, p.body, p.author, (SELECT ${SKIN_COL} FROM player WHERE name = p.author LIMIT 1) AS skin, p.created_at, p.vis, p.share_id, p.edited_at, (SELECT COUNT(*) FROM web_likes l WHERE l.post_id = p.id) AS likes, (SELECT COUNT(*) FROM web_comments c WHERE c.post_id = p.id) AS comments, (SELECT COALESCE(MAX(l.kind), 0) FROM web_likes l WHERE l.post_id = p.id AND l.player_id = ?) AS mine, (SELECT GROUP_CONCAT(DISTINCT l.kind ORDER BY l.kind) FROM web_likes l WHERE l.post_id = p.id) AS rk FROM web_posts p WHERE p.vis = 0 AND p.group_id IS NULL AND p.type IN ('post', 'news', 'update') AND (p.body LIKE ? OR p.title LIKE ?) ${bl.length ? "AND p.author NOT IN (SELECT name FROM player WHERE id IN (?))" : ""} ORDER BY p.id DESC LIMIT ?`, [uid, like(s), like(s), ...(bl.length ? [bl] : []), all ? 5 : 30]), uid);
  if (all || t === "market") out.market = (await q(`SELECT m.id, m.cat, m.title, m.price, m.body, m.img, m.status, m.created_at, p.name AS seller, p.${SKIN_COL} AS skin, p.connected, (m.seller = ?) AS mine FROM web_market m JOIN player p ON p.id = m.seller WHERE m.status = 1 AND (m.title LIKE ? OR m.body LIKE ?) ORDER BY m.id DESC LIMIT ?`, [uid, like(s), like(s), all ? 4 : 30])).map((r) => ({ ...r, price: Number(r.price), mine: Number(r.mine) === 1, img: r.img ? `/img/${r.img}` : "" }));
  res.json(out);
});
void MIN_POST;
};
