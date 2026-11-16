// Grupos, eventos, encuestas y vistas de historias.
module.exports = (app, { sec, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, notify }) => {
const NAME = /^\w{1,24}$/, like = (s) => "%" + s.replace(/[\\%_]/g, "\\$&") + "%";
const txt = (v, n) => str(v).replace(/\s+/g, " ").trim().slice(0, n);
const isStaff = (u) => Number(u.admin_level) >= MIN_POST;

// ---- Grupos. Rol en web_group_members: 0 solicitud pendiente, 1 miembro, 2 administrador ----
const roleOf = async (gid, uid) => (uid ? Number(((await q("SELECT role FROM web_group_members WHERE group_id = ? AND player_id = ?", [gid, uid]))[0] || { role: -1 }).role) : -1);
const GSEL = `SELECT g.id, g.name, g.about, g.priv, g.owner, g.created_at, (SELECT COUNT(*) FROM web_group_members m WHERE m.group_id = g.id AND m.role > 0) AS members, (SELECT COUNT(*) FROM web_posts p WHERE p.group_id = g.id) AS posts FROM web_groups g`;
const gOut = (g, role) => ({ id: g.id, name: g.name, about: g.about, priv: Number(g.priv), members: Number(g.members), posts: Number(g.posts), created_at: g.created_at, role });
app.get("/api/groups", async (req, res) => { // mis grupos y grupos para descubrir (?q= busca por nombre)
  const uid = sessionId(req) || 0, s = txt(req.query.q, 40);
  const mine = uid ? await q(`${GSEL} JOIN web_group_members x ON x.group_id = g.id AND x.player_id = ? ORDER BY g.name LIMIT 100`, [uid]) : [];
  const roles = uid ? new Map((await q("SELECT group_id, role FROM web_group_members WHERE player_id = ?", [uid])).map((r) => [Number(r.group_id), Number(r.role)])) : new Map();
  const more = await q(`${GSEL} WHERE ${s ? "g.name LIKE ?" : "1 = 1"} ORDER BY members DESC, g.id DESC LIMIT 40`, s ? [like(s)] : []);
  res.json({ mine: mine.map((g) => gOut(g, roles.get(Number(g.id)) ?? -1)), more: more.filter((g) => !roles.has(Number(g.id))).map((g) => gOut(g, -1)) });
});
app.post("/api/groups", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("gc|" + u.id, 3, 86400000)) return res.status(429).json({ error: "Solo puedes crear 3 grupos por día" });
  const name = txt(req.body.name, 60), about = txt(req.body.about, 300), priv = req.body.priv ? 1 : 0;
  if (name.length < 3) return res.status(400).json({ error: "Escribe un nombre (mínimo 3 letras)" });
  if ((await q("SELECT 1 FROM web_groups WHERE name = ? LIMIT 1", [name])).length) return res.status(409).json({ error: "Ya existe un grupo con ese nombre" });
  const r = await q("INSERT INTO web_groups (name, about, priv, owner) VALUES (?, ?, ?, ?)", [name, about, priv, u.id]);
  await q("INSERT INTO web_group_members (group_id, player_id, role) VALUES (?, ?, 2)", [r.insertId, u.id]);
  res.json({ ok: true, id: r.insertId });
});
app.get("/api/groups/:id(\\d+)", async (req, res) => {
  const uid = sessionId(req) || 0, id = pid(req.params.id), g = (await q(`${GSEL} WHERE g.id = ?`, [id]))[0];
  if (!g) return res.status(404).json({ error: "Ese grupo no existe" });
  const role = await roleOf(id, uid), open = !Number(g.priv) || role > 0;
  const ms = open ? await q(`SELECT p.name, p.${SKIN_COL} AS skin, p.connected, m.role FROM web_group_members m JOIN player p ON p.id = m.player_id WHERE m.group_id = ? AND m.role > 0 ORDER BY m.role DESC, p.name LIMIT 200`, [id]) : [];
  const pend = role === 2 ? await q(`SELECT p.name, p.${SKIN_COL} AS skin FROM web_group_members m JOIN player p ON p.id = m.player_id WHERE m.group_id = ? AND m.role = 0 ORDER BY p.name LIMIT 100`, [id]) : [];
  res.json({ ...gOut(g, role), isOwner: Number(g.owner) === uid, open, ms, pend });
});
app.put("/api/groups/:id(\\d+)", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id); if ((await roleOf(id, u.id)) !== 2 && !isStaff(u)) return res.status(403).json({ error: "Solo los administradores del grupo" });
  const name = txt(req.body.name, 60), about = txt(req.body.about, 300);
  if (name.length < 3) return res.status(400).json({ error: "Escribe un nombre (mínimo 3 letras)" });
  if ((await q("SELECT 1 FROM web_groups WHERE name = ? AND id <> ? LIMIT 1", [name, id])).length) return res.status(409).json({ error: "Ya existe un grupo con ese nombre" });
  await q("UPDATE web_groups SET name = ?, about = ?, priv = ? WHERE id = ?", [name, about, req.body.priv ? 1 : 0, id]);
  res.json({ ok: true });
});
app.delete("/api/groups/:id(\\d+)", async (req, res) => { // el dueño (o el staff) elimina el grupo con sus publicaciones
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), g = (await q("SELECT owner FROM web_groups WHERE id = ?", [id]))[0];
  if (!g) return res.status(404).json({ error: "Ese grupo no existe" });
  if (Number(g.owner) !== Number(u.id) && !isStaff(u)) return res.status(403).json({ error: "Solo el creador del grupo" });
  await q("DELETE FROM web_posts WHERE group_id = ?", [id]); await q("DELETE FROM web_group_members WHERE group_id = ?", [id]); await q("DELETE FROM web_groups WHERE id = ?", [id]);
  res.json({ ok: true });
});
app.post("/api/groups/:id(\\d+)/join", async (req, res) => { // unirse (público) o pedir unirse (privado)
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), g = (await q("SELECT priv, owner, name FROM web_groups WHERE id = ?", [id]))[0];
  if (!g) return res.status(404).json({ error: "Ese grupo no existe" });
  if ((await roleOf(id, u.id)) >= 0) return res.json({ ok: true });
  if (!hit("gj|" + u.id, 20, 3600000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  const role = Number(g.priv) ? 0 : 1;
  await q("INSERT IGNORE INTO web_group_members (group_id, player_id, role) VALUES (?, ?, ?)", [id, u.id, role]);
  if (!role) await notify(g.owner, u, "grp_req", null, g.name);
  res.json({ ok: true, role });
});
app.delete("/api/groups/:id(\\d+)/join", async (req, res) => { // salir del grupo o cancelar la solicitud
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), g = (await q("SELECT owner FROM web_groups WHERE id = ?", [id]))[0];
  if (g && Number(g.owner) === Number(u.id)) return res.status(400).json({ error: "Eres el creador: elimina el grupo o deja a otro administrador antes de salir" });
  await q("DELETE FROM web_group_members WHERE group_id = ? AND player_id = ?", [id, u.id]);
  res.json({ ok: true });
});
app.post("/api/groups/:id(\\d+)/members/:name", async (req, res) => { // administradores: aceptar, expulsar, hacer administrador
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), n = str(req.params.name), act = str(req.body.action);
  if ((await roleOf(id, u.id)) !== 2 && !isStaff(u)) return res.status(403).json({ error: "Solo los administradores del grupo" });
  const p = NAME.test(n) ? (await q("SELECT id FROM player WHERE name = ?", [n]))[0] : null, g = (await q("SELECT owner, name FROM web_groups WHERE id = ?", [id]))[0];
  if (!p || !g) return res.status(404).json({ error: "No existe" });
  if (Number(p.id) === Number(g.owner)) return res.status(403).json({ error: "No se puede cambiar al creador del grupo" });
  if (act === "accept") { const r = await q("UPDATE web_group_members SET role = 1 WHERE group_id = ? AND player_id = ? AND role = 0", [id, p.id]); if (r.affectedRows) await notify(p.id, u, "grp_ok", null, g.name); }
  else if (act === "remove") await q("DELETE FROM web_group_members WHERE group_id = ? AND player_id = ?", [id, p.id]);
  else if (act === "admin") await q("UPDATE web_group_members SET role = IF(role = 2, 1, 2) WHERE group_id = ? AND player_id = ? AND role > 0", [id, p.id]);
  else return res.status(400).json({ error: "Faltan datos" });
  res.json({ ok: true });
});

// ---- Eventos. RSVP: 1 asistiré, 2 me interesa ----
const ESEL = `SELECT e.id, e.title, e.about, e.place, e.starts, e.created_at, p.name AS owner, p.${SKIN_COL} AS skin, (SELECT COUNT(*) FROM web_event_rsvp r WHERE r.event_id = e.id AND r.st = 1) AS going, (SELECT COUNT(*) FROM web_event_rsvp r WHERE r.event_id = e.id AND r.st = 2) AS maybe, (SELECT COALESCE(MAX(r.st), 0) FROM web_event_rsvp r WHERE r.event_id = e.id AND r.player_id = ?) AS mine, (e.owner = ?) AS own FROM web_events e JOIN player p ON p.id = e.owner`;
const eOut = (e) => ({ ...e, going: Number(e.going), maybe: Number(e.maybe), mine: Number(e.mine), own: Number(e.own) === 1 });
app.get("/api/events", async (req, res) => {
  const uid = sessionId(req) || 0, past = req.query.when === "past";
  res.json((await q(`${ESEL} WHERE e.starts ${past ? "<" : ">="} NOW() - INTERVAL 3 HOUR ORDER BY e.starts ${past ? "DESC" : "ASC"} LIMIT 60`, [uid, uid])).map(eOut));
});
app.post("/api/events", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("ev|" + u.id, 3, 86400000)) return res.status(429).json({ error: "Solo puedes crear 3 eventos por día" });
  const title = txt(req.body.title, 80), about = str(req.body.about).trim().slice(0, 600), place = txt(req.body.place, 80), m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(str(req.body.starts));
  if (title.length < 3) return res.status(400).json({ error: "Escribe un título (mínimo 3 letras)" });
  const d = m ? new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5]) : null;
  if (!d || isNaN(d) || d.getTime() < Date.now() - 600000 || d.getTime() > Date.now() + 366 * 864e5) return res.status(400).json({ error: "Elige una fecha y hora futuras (máximo un año)" });
  const r = await q("INSERT INTO web_events (title, about, place, starts, owner) VALUES (?, ?, ?, ?, ?)", [title, about, place, `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}:00`, u.id]);
  await q("INSERT INTO web_event_rsvp (event_id, player_id, st) VALUES (?, ?, 1)", [r.insertId, u.id]);
  res.json({ ok: true, id: r.insertId });
});
app.post("/api/events/:id(\\d+)/rsvp", async (req, res) => { // repetir la misma respuesta la quita
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), st = Number(req.body.st) === 2 ? 2 : 1;
  if (!(await q("SELECT 1 FROM web_events WHERE id = ?", [id])).length) return res.status(404).json({ error: "Ese evento no existe" });
  const cur = (await q("SELECT st FROM web_event_rsvp WHERE event_id = ? AND player_id = ?", [id, u.id]))[0];
  if (cur && Number(cur.st) === st) await q("DELETE FROM web_event_rsvp WHERE event_id = ? AND player_id = ?", [id, u.id]);
  else await q("INSERT INTO web_event_rsvp (event_id, player_id, st) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE st = VALUES(st)", [id, u.id, st]);
  res.json(eOut((await q(`${ESEL} WHERE e.id = ?`, [u.id, u.id, id]))[0]));
});
app.get("/api/events/:id(\\d+)/people", async (req, res) => {
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin, r.st FROM web_event_rsvp r JOIN player p ON p.id = r.player_id WHERE r.event_id = ? ORDER BY r.st, p.name LIMIT 300`, [pid(req.params.id)]));
});
app.delete("/api/events/:id(\\d+)", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), e = (await q("SELECT owner FROM web_events WHERE id = ?", [id]))[0];
  if (!e) return res.status(404).json({ error: "Ese evento no existe" });
  if (Number(e.owner) !== Number(u.id) && !isStaff(u)) return res.status(403).json({ error: "No tienes permiso" });
  await q("DELETE FROM web_event_rsvp WHERE event_id = ?", [id]); await q("DELETE FROM web_events WHERE id = ?", [id]);
  res.json({ ok: true });
});

// ---- Encuestas: un voto por jugador; votar otra opción lo cambia ----
app.post("/api/posts/:id(\\d+)/vote", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), pl = (await q("SELECT opts FROM web_polls WHERE post_id = ?", [id]))[0];
  let o = []; try { o = JSON.parse(pl.opts); } catch {}
  const opt = Math.floor(Number(req.body.opt));
  if (!pl || !(opt >= 0 && opt < o.length)) return res.status(400).json({ error: "Opción no válida" });
  await q("INSERT INTO web_poll_votes (post_id, player_id, opt) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE opt = VALUES(opt)", [id, u.id, opt]);
  const vs = await q("SELECT opt, COUNT(*) AS n FROM web_poll_votes WHERE post_id = ? GROUP BY opt", [id]);
  res.json({ opts: o.map((t, i) => ({ t, n: Number((vs.find((v) => Number(v.opt) === i) || {}).n) || 0 })), mine: opt });
});

// ---- Historias: quién la vio (solo lo ve su autor) ----
app.post("/api/stories/:id(\\d+)/view", async (req, res) => {
  const u = await me(req); if (!u) return res.json({ ok: true });
  const id = pid(req.params.id), s = (await q("SELECT author FROM web_posts WHERE id = ? AND type = 'story'", [id]))[0];
  if (s && s.author !== u.name) await q("INSERT IGNORE INTO web_story_views (post_id, player_id) VALUES (?, ?)", [id, u.id]);
  res.json({ ok: true });
});
app.get("/api/stories/:id(\\d+)/views", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), s = (await q("SELECT author FROM web_posts WHERE id = ? AND type = 'story'", [id]))[0];
  if (!s || (s.author !== u.name && !isStaff(u))) return res.status(403).json({ error: "No tienes permiso" });
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin FROM web_story_views v JOIN player p ON p.id = v.player_id WHERE v.post_id = ? ORDER BY v.at DESC LIMIT 300`, [id]));
});
void sec;
};
