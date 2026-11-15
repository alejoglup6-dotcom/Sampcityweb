// Rutas de amigos. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { str, pid, SKIN_COL, pool, q, sessionId, hit, me, NJ, notify, unreadCount }) => {
// ---- Notificaciones (API) ----
app.get("/api/notifications/count", async (req, res) => {
  const id = sessionId(req);
  res.json({ n: id ? await unreadCount(id).catch(() => 0) : 0 });
});
app.get("/api/notifications", async (req, res) => {
  const id = sessionId(req);
  if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const before = pid(req.query.before), unread = req.query.unread === "1";
  res.json(await q(`SELECT n.id, n.type, n.post_id, n.body, n.seen, n.created_at, a.name AS actor, a.${SKIN_COL} AS skin, p.type AS ptype ${NJ} ${before ? "AND n.id < ?" : ""} ${unread ? "AND n.seen = 0" : ""} ORDER BY n.id DESC LIMIT 31`, before ? [id, before] : [id]));
});
app.post("/api/notifications/read", async (req, res) => { // con id marca una; sin id, todas
  const id = sessionId(req);
  if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const n = pid(req.body.id);
  await q(`UPDATE web_notifs SET seen = 1 WHERE player_id = ? ${n ? "AND id = ?" : ""}`, n ? [id, n] : [id]);
  res.json({ ok: true });
});
app.delete("/api/notifications/:id(\\d+)", async (req, res) => {
  const id = sessionId(req);
  if (!id) return res.status(401).json({ error: "Inicia sesión" });
  await q("DELETE FROM web_notifs WHERE id = ? AND player_id = ?", [pid(req.params.id), id]);
  res.json({ ok: true });
});

// ---- Amigos: solicitudes de amistad. web_friends (a = quien envía, b = quien recibe; status 0 = pendiente, 1 = amigos) ----
const friendIds = async (uid) => (await q("SELECT IF(a = ?, b, a) AS id FROM web_friends WHERE status = 1 AND (a = ? OR b = ?) LIMIT 5000", [uid, uid, uid])).map((r) => Number(r.id));
async function friendStates(uid, ids) { // Map id → "friends" | "sent" (le envié) | "received" (me envió); sin relación no aparece
  const m = new Map();
  if (!ids.length) return m;
  for (const r of await q("SELECT a, b, status FROM web_friends WHERE (a = ? AND b IN (?)) OR (b = ? AND a IN (?))", [uid, ids, uid, ids])) {
    const other = Number(r.a) === Number(uid) ? Number(r.b) : Number(r.a);
    m.set(other, Number(r.status) === 1 ? "friends" : Number(r.a) === Number(uid) ? "sent" : "received");
  }
  return m;
}
async function mutualCounts(mine, ids, samples) { // amigos en común entre yo (mine = ids de mis amigos) y cada jugador de ids
  const out = new Map(ids.map((i) => [Number(i), 0])), set = new Set(mine), smp = new Map();
  if (!ids.length || !set.size) return samples ? { out, smp } : out;
  const add = (x, y) => { if (out.has(x) && set.has(y)) { out.set(x, out.get(x) + 1); const l = smp.get(x) || []; if (l.length < 2) l.push(y); smp.set(x, l); } };
  for (const r of await q("SELECT a, b FROM web_friends WHERE status = 1 AND (a IN (?) OR b IN (?))", [ids, ids])) { add(Number(r.a), Number(r.b)); add(Number(r.b), Number(r.a)); }
  return samples ? { out, smp } : out;
}
async function mutualFaces(smp) { // id → [{ name, skin }] de hasta 2 amigos en común (las caritas de la lista de solicitudes)
  const ids = [...new Set([...smp.values()].flat())], m = new Map();
  if (ids.length) for (const r of await q(`SELECT id, name, ${SKIN_COL} AS skin FROM player WHERE id IN (?)`, [ids])) m.set(Number(r.id), { name: r.name, skin: r.skin });
  return (id) => (smp.get(Number(id)) || []).map((x) => m.get(x)).filter(Boolean);
}
const target = async (req, res, u) => { // jugador indicado en el cuerpo/ruta (no puedes ser tú mismo)
  const n = str(req.body?.name ?? req.params?.name);
  const t = /^\w{1,24}$/.test(n) ? (await q("SELECT id, name FROM player WHERE name = ?", [n]))[0] : null;
  if (!t) { res.status(404).json({ error: "Ese jugador no existe" }); return null; }
  if (String(t.id) === String(u.id)) { res.status(400).json({ error: "No puedes agregarte a ti mismo" }); return null; }
  return t;
};
app.post("/api/friends/request", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const t = await target(req, res, u); if (!t) return;
  if (!hit("fr|" + u.id, 20, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  const st = (await friendStates(u.id, [t.id])).get(Number(t.id));
  if (st === "friends" || st === "sent") return res.json({ state: st });
  if (st === "received") { // ya te había enviado una solicitud: al agregarlo se aceptan las dos
    await q("UPDATE web_friends SET status = 1 WHERE a = ? AND b = ?", [t.id, u.id]);
    await q("DELETE FROM web_notifs WHERE player_id = ? AND actor_id = ? AND type = 'friend_req'", [u.id, t.id]);
    await notify(t.id, u, "friend_acc");
    return res.json({ state: "friends" });
  }
  if (Number((await q("SELECT COUNT(*) AS n FROM web_friends WHERE a = ? AND status = 0", [u.id]))[0].n) >= 100) return res.status(429).json({ error: "Tienes demasiadas solicitudes pendientes. Espera a que respondan o cancela algunas." });
  await q("INSERT IGNORE INTO web_friends (a, b, status) VALUES (?, ?, 0)", [u.id, t.id]);
  await notify(t.id, u, "friend_req");
  res.json({ state: "sent" });
});
app.post("/api/friends/accept", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const t = await target(req, res, u); if (!t) return;
  const r = await q("UPDATE web_friends SET status = 1 WHERE a = ? AND b = ? AND status = 0", [t.id, u.id]);
  if (!r.affectedRows) return res.status(404).json({ error: "Esa solicitud ya no existe" });
  await q("DELETE FROM web_notifs WHERE player_id = ? AND actor_id = ? AND type = 'friend_req'", [u.id, t.id]);
  await notify(t.id, u, "friend_acc");
  res.json({ state: "friends" });
});
app.delete("/api/friends/:name", async (req, res) => { // cancelar la solicitud que enviaste, rechazar la que recibiste o dejar de ser amigos
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const t = await target(req, res, u); if (!t) return;
  await q("DELETE FROM web_friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", [u.id, t.id, t.id, u.id]);
  await q("DELETE FROM web_notifs WHERE type = 'friend_req' AND ((player_id = ? AND actor_id = ?) OR (player_id = ? AND actor_id = ?))", [u.id, t.id, t.id, u.id]);
  res.json({ state: "none" });
});
app.get("/api/friends", async (req, res) => { // mis amigos
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const ids = await friendIds(id);
  res.json(ids.length ? await q(`SELECT name, ${SKIN_COL} AS skin, connected FROM player WHERE id IN (?) ORDER BY connected DESC, name LIMIT 200`, [ids]) : []);
});
app.get("/api/friends/requests", async (req, res) => { // solicitudes que me enviaron
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const rows = await q(`SELECT p.id, p.name, p.${SKIN_COL} AS skin, f.created_at FROM web_friends f JOIN player p ON p.id = f.a WHERE f.b = ? AND f.status = 0 ORDER BY f.created_at DESC LIMIT 50`, [id]);
  const { out: mc, smp } = await mutualCounts(await friendIds(id), rows.map((r) => Number(r.id)), true), faces = await mutualFaces(smp);
  res.json(rows.map((r) => ({ name: r.name, skin: r.skin, created_at: r.created_at, mutual: mc.get(Number(r.id)) || 0, faces: faces(r.id) })));
});
app.get("/api/friends/suggestions", async (req, res) => { // jugadores recomendados: con amigos en común primero, luego los más activos
  const id = sessionId(req); if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const pool = await q(`SELECT p.id, p.name, p.${SKIN_COL} AS skin, p.connected, p.level FROM player p WHERE p.id <> ? AND p.id NOT IN (SELECT IF(a = ?, b, a) FROM web_friends WHERE (a = ? OR b = ?) AND (status = 1 OR b = ?)) ORDER BY p.connected DESC, p.last_connection DESC, p.id DESC LIMIT 100`, [id, id, id, id, id]);
  const ids = pool.map((r) => Number(r.id)), [{ out: mc, smp }, st] = await Promise.all([mutualCounts(await friendIds(id), ids, true), friendStates(id, ids)]);
  const list = pool.map((r) => ({ id: r.id, name: r.name, skin: r.skin, connected: r.connected, level: r.level, mutual: mc.get(Number(r.id)) || 0, state: st.get(Number(r.id)) || "none" })).sort((x, y) => y.mutual - x.mutual || Number(y.connected) - Number(x.connected)).slice(0, 30);
  const faces = await mutualFaces(smp);
  res.json(list.map(({ id: pId, ...r }) => ({ ...r, faces: faces(pId) })));
});
app.get("/api/friends/search", async (req, res) => { // buscar jugadores para agregarlos
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const v = str(req.query.q).trim().replace(/\s+/g, "_").slice(0, 24).replace(/[\\%_]/g, "\\$&");
  if (v.length < 2) return res.json([]);
  const rows = await q(`SELECT id, name, ${SKIN_COL} AS skin, connected FROM player WHERE name LIKE ? AND id <> ? ORDER BY connected DESC, name LIMIT 12`, [`%${v}%`, u.id]);
  const st = await friendStates(u.id, rows.map((r) => Number(r.id)));
  res.json(rows.map((r) => ({ name: r.name, skin: r.skin, connected: r.connected, state: st.get(Number(r.id)) || "none" })));
});

return { friendIds, friendStates };
};
