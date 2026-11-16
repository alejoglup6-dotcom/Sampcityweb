// Rutas de contenido. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { sec, path, express, E, str, pid, MIN_POST, SKIN_COL, SKIN_URL, CH, IMG_MAX, VID_MAX, VID_SECS, REEL_MAX, REEL_SECS, ADMIN_LEVELS, q, sessionId, hit, captchaOk, me, BCAST, notify, mentioned, postOwner, friendIds, friendStates }) => {
// ---- Estado público (solo cifras, sin datos de cuentas) ----
app.get("/api/info", async (req, res) => {
  const r = (await q("SELECT COUNT(*) AS total, COALESCE(SUM(connected), 0) AS online FROM player"))[0];
  res.json({ total: Number(r.total), online: Number(r.online), ip: E.SERVER_IP || "", skinUrl: SKIN_URL, videoMax: Math.round(VID_MAX / 1048576), reelMax: Math.round(REEL_MAX / 1048576), reelSecs: REEL_SECS, turnstile: E.TURNSTILE_SECRET ? E.TURNSTILE_SITEKEY || "" : "" });
});

// ---- Staff (nombre y rango, nada más) ----
app.get("/api/staff", async (req, res) => res.json(await q(`SELECT name, admin_level AS level, connected, last_connection, ${SKIN_COL} AS skin FROM player WHERE admin_level > 0 ORDER BY admin_level DESC, name LIMIT 100`)));
// ---- Clasificación (solo nombre, skin y cifras públicas) ----
app.get("/api/top", async (req, res) => {
  const by = { level: "level", time: "time_playing", rep: "rep" }[req.query.by] || "level";
  res.json(await q(`SELECT name, level, rep, time_playing, ${SKIN_COL} AS skin FROM player ORDER BY ${by} DESC, level DESC, time_playing DESC, name LIMIT 20`));
});

// ---- Noticias / actualizaciones / FAQ / reglas / fotos ----
const TYPES = ["news", "update", "faq", "photo", "rules", "review", "post", "story", "reel"]; // post = publicaciones de los jugadores (muro) // review = testimonios (los publica el staff)
const staffOnly = async (req, res) => { const u = await me(req); if (!u || u.admin_level < MIN_POST) { res.status(403).json({ error: "No tienes permiso" }); return null; } return u; };
app.get("/api/posts", async (req, res) => {
  const type = TYPES.includes(req.query.type) ? req.query.type : null, uid = sessionId(req) || 0;
  const paged = req.query.page !== undefined, per = paged ? 12 : req.query.type === "story" ? 150 : 50, pg = Math.min(1000, Math.max(0, Number(req.query.page) || 0)); // con ?page= devuelve 12 (+1 para saber si hay siguiente)
  const where = [], wp = [], au = str(req.query.author);
  const bl = uid ? await sec.blockedIds(uid) : []; // no se muestran publicaciones de jugadores con bloqueo entre ambos
  if (bl.length) { where.push("p.author NOT IN (SELECT name FROM player WHERE id IN (?))"); wp.push(bl); }
  // privacidad: 0 pública, 1 amigos, 2 solo yo. Sin sesión solo se ven las públicas.
  const fids = uid ? await friendIds(uid) : [], vn = uid ? (await q("SELECT name FROM player WHERE id IN (?)", [[...fids, uid]])).map((r) => r.name) : [], myName = uid ? ((await q("SELECT name FROM player WHERE id = ?", [uid]))[0] || {}).name || "" : "";
  if (!uid) where.push("p.vis = 0"); else { where.push("(p.vis = 0 OR p.author = ? OR (p.vis = 1 AND p.author IN (?)))"); wp.push(myName, vn); where.push("p.id NOT IN (SELECT post_id FROM web_hidden WHERE player_id = ?)"); wp.push(uid); }
  if (req.query.type === "feed" || (req.query.scope === "friends" && type === "story")) { // solo publicaciones de tus amigos (y las tuyas)
    if (!uid) return res.json([]);
    const fol = (await q("SELECT b FROM web_follows WHERE a = ?", [uid])).map((r) => r.b); // también lo público de quienes sigues
    const names = (await q("SELECT name FROM player WHERE id IN (?)", [[...(await friendIds(uid)), ...fol, uid]])).map((r) => r.name);
    where.push("p.author IN (?)"); wp.push(names);
  }
  if (req.query.type === "feed") where.push("(p.type = 'post' OR (p.type = 'story' AND p.created_at > NOW() - INTERVAL 24 HOUR))"); else if (type) { where.push("p.type = ?"); wp.push(type); if (type === "story") where.push("p.created_at > NOW() - INTERVAL 24 HOUR"); } // las historias duran 24 h
  if (/^\w{1,24}$/.test(au)) { where.push("p.author = ?"); wp.push(au); }
  res.json(await attach(await q(`SELECT p.id, p.type, p.title, p.body, p.author, (SELECT ${SKIN_COL} FROM player WHERE name = p.author LIMIT 1) AS skin, p.created_at, p.vis, p.share_id, p.edited_at, (SELECT COUNT(*) FROM web_likes l WHERE l.post_id = p.id) AS likes, (SELECT COUNT(*) FROM web_comments c WHERE c.post_id = p.id) AS comments, (SELECT COALESCE(MAX(l.kind), 0) FROM web_likes l WHERE l.post_id = p.id AND l.player_id = ?) AS mine, (SELECT GROUP_CONCAT(DISTINCT l.kind ORDER BY l.kind) FROM web_likes l WHERE l.post_id = p.id) AS rk FROM web_posts p ${where.length ? "WHERE " + where.join(" AND ") : ""} ORDER BY p.id DESC LIMIT ? OFFSET ?`, [uid, ...wp, paged ? per + 1 : per, pg * per])));
});
const ONE = (extra = "") => `SELECT p.id, p.type, p.title, p.body, p.author, (SELECT ${SKIN_COL} FROM player WHERE name = p.author LIMIT 1) AS skin, p.created_at, p.vis, p.share_id, p.edited_at, (SELECT COUNT(*) FROM web_likes l WHERE l.post_id = p.id) AS likes, (SELECT COUNT(*) FROM web_comments c WHERE c.post_id = p.id) AS comments, (SELECT COALESCE(MAX(l.kind), 0) FROM web_likes l WHERE l.post_id = p.id AND l.player_id = ?) AS mine, (SELECT GROUP_CONCAT(DISTINCT l.kind ORDER BY l.kind) FROM web_likes l WHERE l.post_id = p.id) AS rk FROM web_posts p WHERE p.id = ? ${extra}`;
const STORY_LIVE = "AND (p.type <> 'story' OR p.created_at > NOW() - INTERVAL 24 HOUR)";
app.get("/api/posts/:id(\\d+)", async (req, res) => {
  const id = pid(req.params.id), uid = sessionId(req) || 0;
  const row = (await q(ONE(), [uid, id]))[0];
  if (!row) return res.status(404).json({ error: "No existe" });
  if (Number(row.vis) > 0) { const v = uid ? (await q("SELECT name, admin_level FROM player WHERE id = ?", [uid]))[0] : null; // amigos / solo yo
    if (!v || (v.name !== row.author && !(Number(v.admin_level) >= MIN_POST) && (Number(row.vis) === 2 || !(await friendIds(uid)).map(Number).includes(Number(((await q("SELECT id FROM player WHERE name = ?", [row.author]))[0] || {}).id))))) return res.status(404).json({ error: "No existe" }); }
  if (row.type === "story" && !(await q("SELECT 1 FROM web_posts p WHERE p.id = ? " + STORY_LIVE, [id])).length) return res.status(404).json({ error: "Esta historia ya caducó" });
  res.json((await attach([row]))[0]);
});
app.post("/api/posts", async (req, res) => {
  const u = await staffOnly(req, res); if (!u) return;
  const type = str(req.body.type), title = str(req.body.title).trim(), body = str(req.body.body).trim();
  if (!TYPES.includes(type) || type === "story" || !title || !body) return res.status(400).json({ error: "Faltan datos" });
  if (type === "reel") return res.status(400).json({ error: "Los reels se suben desde /reels" });
  if (type === "photo" && !/^https:\/\/[^\s"'<>]{4,500}$/.test(body)) return res.status(400).json({ error: "El enlace de la foto debe empezar con https://" });
  const ins = await q("INSERT INTO web_posts (type, title, body, author) VALUES (?, ?, ?, ?)", [type, title.slice(0, 120), body.slice(0, 4000), u.name]);
  if (type === "news" || type === "update") try { await q("INSERT INTO web_notifs (player_id, actor_id, type, post_id, body) SELECT id, ?, ?, ?, ? FROM player WHERE id <> ? ORDER BY id DESC LIMIT ?", [u.id, type, ins.insertId, title.slice(0, 120), u.id, BCAST]); } catch (e) { console.log("[notif]", e.message); }
  res.json({ ok: true });
});
app.put("/api/posts/:id", async (req, res) => {
  const own = pid(req.params.id) ? (await q("SELECT author, type FROM web_posts WHERE id = ?", [pid(req.params.id)]))[0] : null;
  if (own && own.type === "post") { // publicaciones del muro: las edita su autor (o el staff); solo el texto
    const me_ = await me(req); if (!me_) return res.status(401).json({ error: "Inicia sesión" });
    if (own.author !== me_.name && !(Number(me_.admin_level) >= MIN_POST)) return res.status(403).json({ error: "No tienes permiso" });
    const text = str(req.body.body).trim().slice(0, 4000);
    if (!text && !(await q("SELECT 1 FROM web_posts p WHERE p.id = ? AND (p.share_id IS NOT NULL OR EXISTS (SELECT 1 FROM web_post_media m WHERE m.post_id = p.id))", [pid(req.params.id)])).length) return res.status(400).json({ error: "Escribe algo" });
    const nv = req.body.vis === undefined ? null : Math.min(2, Math.max(0, Math.floor(Number(req.body.vis)) || 0));
    await q("UPDATE web_posts SET body = ?, edited_at = NOW(), vis = COALESCE(?, vis) WHERE id = ?", [text, nv, pid(req.params.id)]);
    return res.json({ ok: true });
  }
  const u = await staffOnly(req, res); if (!u) return;
  const title = str(req.body.title).trim().slice(0, 120), body = str(req.body.body).slice(0, 4000);
  if (!title || !body || !pid(req.params.id)) return res.status(400).json({ error: "Faltan datos" });
  await q("UPDATE web_posts SET title = ?, body = IF(type = 'photo', body, ?) WHERE id = ?", [title, body, pid(req.params.id)]);
  res.json({ ok: true });
});
app.delete("/api/posts/:id", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), p = (await q("SELECT body, type, author FROM web_posts WHERE id = ?", [id]))[0];
  if (!p) return res.status(404).json({ error: "No existe" });
  if (u.admin_level < MIN_POST && !((p.type === "post" || p.type === "story" || p.type === "reel") && p.author === u.name)) return res.status(403).json({ error: "No tienes permiso" }); // el staff borra cualquiera; cada jugador, solo lo suyo
  const im = /^\/img\/(\d+)$/.exec(p?.body || "");
  if (im) await q("DELETE FROM web_images WHERE id = ?", [im[1]]);
  await removePosts([id]);
  res.json({ ok: true });
});
// ---- Muro: cualquier jugador con sesión publica; el perfil público de cada jugador ----
app.post("/api/wall", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const body = str(req.body.body).trim().slice(0, 1000);
  const mids = [...new Set((Array.isArray(req.body.media) ? req.body.media : []).slice(0, 4).map(pid).filter(Boolean))];
  const tags = [...new Set((Array.isArray(req.body.tags) ? req.body.tags : []).map(str).filter((n) => /^\w{1,24}$/.test(n)))].slice(0, 10);
  const vis = Math.min(2, Math.max(0, Math.floor(Number(req.body.vis)) || 0)), share = pid(req.body.share);
  const orig = share ? (await q("SELECT id, author FROM web_posts WHERE id = ? AND vis = 0 AND type IN ('post', 'news', 'update', 'photo', 'reel')", [share]))[0] : null;
  if (share && !orig) return res.status(404).json({ error: "Esa publicación ya no se puede compartir" });
  if (!body && !mids.length && !orig) return res.status(400).json({ error: "Escribe algo o agrega una foto" });
  if (!hit("p|" + u.id, 5, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  if (mids.length && (await q("SELECT m.id FROM web_media m WHERE m.id IN (?) AND m.owner = ? AND m.kind = 'img' AND NOT EXISTS (SELECT 1 FROM web_post_media x WHERE x.media_id = m.id)", [mids, u.id])).length !== mids.length) return res.status(400).json({ error: "Alguna foto no es válida, vuelve a subirla" });
  let tg = tags.length ? await q("SELECT id, name FROM player WHERE name IN (?)", [tags]) : [];
  if (tg.length) { // respeta quién puede etiquetar a cada jugador (Configuración → Privacidad) y los bloqueos
    const mine = new Set((await friendIds(u.id)).map(Number)), bl = new Set(await sec.blockedIds(u.id)), ok = [];
    for (const t of tg) { const pv = Number((await sec.getSettings(t.id)).tag); if (!bl.has(Number(t.id)) && (Number(t.id) === Number(u.id) || pv === 0 || (pv === 1 && mine.has(Number(t.id))))) ok.push(t); }
    tg = ok;
  }
  const r = await q("INSERT INTO web_posts (type, title, body, author, vis, share_id) VALUES ('post', '', ?, ?, ?, ?)", [body, u.name, vis, orig ? orig.id : null]);
  if (orig) { const oa = (await q("SELECT id FROM player WHERE name = ?", [orig.author]))[0]; if (oa) await notify(oa.id, u, "share", r.insertId, body); }
  for (const [i, m] of mids.entries()) await q("INSERT INTO web_post_media (post_id, media_id, pos) VALUES (?, ?, ?)", [r.insertId, m, i]);
  for (const t of tg) await q("INSERT IGNORE INTO web_tags (post_id, player_id, name) VALUES (?, ?, ?)", [r.insertId, t.id, t.name]);
  const done = new Set(tg.map((t) => String(t.id)));
  for (const t of tg) await notify(t.id, u, "tag", r.insertId, body);
  for (const m of await mentioned(body, u.id)) if (!done.has(String(m.id))) await notify(m.id, u, "mention", r.insertId, body);
  res.json({ ok: true, id: r.insertId });
});
app.get("/api/user/:name", async (req, res) => {
  const n = str(req.params.name);
  if (!/^\w{1,24}$/.test(n)) return res.status(404).json({ error: "No existe" });
  const r = (await q(`SELECT id, name, ${SKIN_COL} AS skin, admin_level, level, rep, time_playing, reg_date, last_connection, connected FROM player WHERE name = ?`, [n]))[0];
  if (!r) return res.status(404).json({ error: "No existe" });
  const me_ = sessionId(req), fids = await friendIds(r.id);
  const fstate = !me_ ? "none" : String(me_) === String(r.id) ? "self" : (await friendStates(me_, [r.id])).get(Number(r.id)) || "none";
  if (me_ && fstate !== "self" && (await sec.isBlocked(me_, r.id))) return res.status(404).json({ error: "No existe" }); // bloqueados: el perfil no existe para el otro
  if (fstate !== "self" && fstate !== "friends" && Number((await sec.getSettings(r.id)).prof) === 1 && !(me_ && Number(((await q("SELECT admin_level FROM player WHERE id = ?", [me_]))[0] || {}).admin_level) >= MIN_POST)) // perfil solo para amigos
    return res.json({ name: r.name, skin: r.skin, rango: ADMIN_LEVELS[r.admin_level] || "Ciudadano", connected: 0, locked: true, fstate, friends: fids.length, posts: 0, reels: 0, likes: 0, fl: [], avatar: "", cover: "", bio: "" });
  const fl = fids.length ? await q(`SELECT name, ${SKIN_COL} AS skin, connected FROM player WHERE id IN (?) ORDER BY connected DESC, name LIMIT 30`, [fids]) : [];
  const posts = (await q("SELECT COUNT(*) AS n FROM web_posts WHERE author = ? AND type = 'post'", [r.name]))[0].n;
  const reels = (await q("SELECT COUNT(*) AS n FROM web_posts WHERE author = ? AND type = 'reel'", [r.name]))[0].n;
  const likes = (await q("SELECT COUNT(*) AS n FROM web_likes l JOIN web_posts p ON p.id = l.post_id WHERE p.author = ? AND p.type = 'post'", [r.name]))[0].n;
  const pf = (await q("SELECT avatar, cover, bio, city, job, link, bday, pin FROM web_profile WHERE player_id = ?", [r.id]))[0] || {}; // foto, banner, biografía e información
  res.json({ name: r.name, skin: r.skin, rango: ADMIN_LEVELS[r.admin_level] || "Ciudadano", level: r.level, rep: r.rep, time_playing: r.time_playing, reg_date: r.reg_date, last_connection: r.last_connection, connected: r.connected, posts: Number(posts), reels: Number(reels), likes: Number(likes), friends: fids.length, fstate, fl, avatar: pf.avatar ? `/img/${pf.avatar}` : "", cover: pf.cover ? `/img/${pf.cover}` : "", bio: pf.bio || "",
    city: pf.city || "", job: pf.job || "", link: pf.link || "", bday: pf.bday || "", pin: Number(pf.pin) || 0,
    followers: Number((await q("SELECT COUNT(*) AS n FROM web_follows WHERE b = ?", [r.id]))[0].n), following: !!me_ && (await q("SELECT 1 FROM web_follows WHERE a = ? AND b = ?", [me_, r.id])).length > 0,
    mutual: me_ && fstate !== "self" ? (await friendIds(me_)).map(Number).filter((x) => fids.map(Number).includes(x)).length : 0 }); // sin dinero, teléfono ni datos privados
});
// ---- Contacto: lo envía cualquiera, solo el staff lo lee ----
app.post("/api/contact", async (req, res) => {
  if (str(req.body.website)) return res.json({ ok: true }); // campo trampa para bots: un humano no lo ve ni lo llena
  const name = str(req.body.name).trim().slice(0, 80), contact = str(req.body.contact).trim().slice(0, 80), body = str(req.body.message).trim().slice(0, 1000);
  if (!name || body.length < 10) return res.status(400).json({ error: "Escribe tu nombre y un mensaje de al menos 10 letras" });
  if (!hit("c|" + req.ip, 3, 3600000)) return res.status(429).json({ error: "Ya enviaste varios mensajes. Inténtalo más tarde." });
  if (!(await captchaOk(req))) return res.status(400).json({ error: "Completa la verificación anti-robots", captcha: true });
  await q("INSERT INTO web_contact (name, contact, body) VALUES (?, ?, ?)", [name, contact, body]);
  for (const s of await q("SELECT id FROM player WHERE admin_level >= ? LIMIT 100", [MIN_POST]).catch(() => [])) await notify(s.id, { id: 0 }, "contact");
  res.json({ ok: true });
});
app.get("/api/contact", async (req, res) => {
  if (!(await staffOnly(req, res))) return;
  res.json(await q("SELECT id, name, contact, body, created_at FROM web_contact ORDER BY id DESC LIMIT 100"));
});
app.delete("/api/contact/:id", async (req, res) => {
  if (!(await staffOnly(req, res))) return;
  await q("DELETE FROM web_contact WHERE id = ?", [pid(req.params.id)]);
  res.json({ ok: true });
});
// Subida de fotos (solo staff): la imagen llega ya reducida desde el navegador, se guarda en la base de datos
app.post("/api/photos", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await staffOnly(req, res); if (!u) return;
  const m = /^data:image\/(jpeg|png|webp);base64,([A-Za-z0-9+/=]+)$/.exec(str(req.body.data)), title = str(req.body.title).trim().slice(0, 120);
  if (!m || !title || m[2].length > 1250000) return res.status(400).json({ error: "Escribe un pie de foto y elige una imagen" });
  const buf = Buffer.from(m[2], "base64");
  const magic = (m[1] === "jpeg" && buf[0] === 0xff && buf[1] === 0xd8) || (m[1] === "png" && buf[0] === 0x89 && buf[1] === 0x50) || (m[1] === "webp" && buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP");
  if (!magic || buf.length > 900 * 1024) return res.status(400).json({ error: "Imagen no válida o muy pesada (máx. 900 KB)" });
  const r = await q("INSERT INTO web_images (mime, data) VALUES (?, ?)", ["image/" + m[1], buf]);
  await q("INSERT INTO web_posts (type, title, body, author) VALUES ('photo', ?, ?, ?)", [title, `/img/${r.insertId}`, u.name]);
  res.json({ ok: true });
});
app.get("/img/:id(\\d+)", async (req, res) => {
  const r = (await q("SELECT mime, data FROM web_images WHERE id = ?", [req.params.id]))[0];
  if (!r) return res.status(404).end();
  res.set({ "Content-Type": r.mime, "Cache-Control": "public, max-age=604800, immutable", "Content-Security-Policy": "default-src 'none'; sandbox", "Content-Disposition": "inline" }).send(r.data);
});
// Me gusta y comentarios (hay que haber iniciado sesión)
app.post("/api/posts/:id/like", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id);
  const own = await postOwner(id);
  if (!own) return res.status(404).json({ error: "No existe" });
  // Reacciones: kind 1-6. Repetir la misma la quita; elegir otra la cambia.
  const kind = Math.min(6, Math.max(1, Math.floor(Number(req.body.kind)) || 1)), cur = (await q("SELECT kind FROM web_likes WHERE post_id = ? AND player_id = ?", [id, u.id]))[0];
  let mine = kind;
  if (!cur) { await q("INSERT IGNORE INTO web_likes (post_id, player_id, kind) VALUES (?, ?, ?)", [id, u.id, kind]); await notify(own.aid, u, "like", id); }
  else if (Number(cur.kind) === kind) { mine = 0; await q("DELETE FROM web_likes WHERE post_id = ? AND player_id = ?", [id, u.id]); await q("DELETE FROM web_notifs WHERE actor_id = ? AND post_id = ? AND type = 'like'", [u.id, id]); } // quitar la reacción quita el aviso
  else await q("UPDATE web_likes SET kind = ? WHERE post_id = ? AND player_id = ?", [kind, id, u.id]);
  const st = (await q("SELECT COUNT(*) AS n, GROUP_CONCAT(DISTINCT kind ORDER BY kind) AS rk FROM web_likes WHERE post_id = ?", [id]))[0];
  res.json({ liked: mine > 0, mine, likes: Number(st.n), rk: st.rk || "" });
});
app.get("/api/posts/:id/comments", async (req, res) => {
  const u = await me(req), rows = await q("SELECT c.id, c.player_id, c.author, c.body, c.created_at, c.parent, c.edited_at, (SELECT COUNT(*) FROM web_clikes l WHERE l.comment_id = c.id) AS likes, (SELECT COUNT(*) FROM web_clikes l WHERE l.comment_id = c.id AND l.player_id = ?) AS mine FROM web_comments c WHERE c.post_id = ? ORDER BY c.id LIMIT 300", [u ? u.id : 0, pid(req.params.id)]);
  res.json(rows.map((c) => ({ id: c.id, author: c.author, body: c.body, created_at: c.created_at, parent: Number(c.parent) || 0, edited: !!c.edited_at, likes: Number(c.likes), mine: Number(c.mine) > 0, own: !!u && String(u.id) === String(c.player_id), del: !!u && (String(u.id) === String(c.player_id) || u.admin_level >= MIN_POST) })));
});
app.post("/api/posts/:id/comments", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), body = str(req.body.body).trim().slice(0, 300);
  if (!body) return res.status(400).json({ error: "Escribe algo" });
  const own = await postOwner(id);
  if (!own) return res.status(404).json({ error: "No existe" });
  if (Number((await q("SELECT COUNT(*) AS n FROM web_comments WHERE player_id = ? AND created_at > NOW() - INTERVAL 1 MINUTE", [u.id]))[0].n) >= 5) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  let parent = pid(req.body.parent); // responder a un comentario (un solo nivel: responder a una respuesta cuelga del mismo hilo)
  if (parent) { const pc = (await q("SELECT id, parent, player_id FROM web_comments WHERE id = ? AND post_id = ?", [parent, id]))[0]; if (!pc) parent = 0; else { if (Number(pc.parent)) parent = Number(pc.parent); if (String(pc.player_id) !== String(u.id)) await notify(pc.player_id, u, "reply", id, body); } }
  await q("INSERT INTO web_comments (post_id, player_id, author, body, parent) VALUES (?, ?, ?, ?, ?)", [id, u.id, u.name, body, parent || null]);
  // avisos: citados (@) → dueño de la publicación → quienes ya habían comentado
  const sent = new Set();
  for (const m of await mentioned(body, u.id)) { sent.add(String(m.id)); await notify(m.id, u, "mention_c", id, body); }
  if (own.aid && !sent.has(String(own.aid))) await notify(own.aid, u, "comment", id, body);
  if (own.aid) sent.add(String(own.aid));
  for (const c of await q("SELECT DISTINCT player_id FROM web_comments WHERE post_id = ? AND player_id <> ? LIMIT 20", [id, u.id]).catch(() => [])) if (!sent.has(String(c.player_id))) await notify(c.player_id, u, "reply", id, body);
  res.json({ ok: true });
});
app.delete("/api/posts/:id/comments/:cid", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  await q("DELETE FROM web_comments WHERE id = ? AND post_id = ? AND (player_id = ? OR ? >= ?)", [pid(req.params.cid), pid(req.params.id), u.id, u.admin_level, MIN_POST]);
  res.json({ ok: true });
});

// ---- Medios (fotos y videos): se guardan en la base de datos en trozos, con soporte de Range (necesario para videos en iPhone) ----
const imgOk = (m, b) => (m === "image/jpeg" && b[0] === 0xff && b[1] === 0xd8) || (m === "image/png" && b[0] === 0x89 && b[1] === 0x50) || (m === "image/webp" && b.toString("ascii", 0, 4) === "RIFF" && b.toString("ascii", 8, 12) === "WEBP");
function mp4Dur(b) { // duración en segundos leyendo moov > mvhd (MP4/MOV)
  try {
    const find = (st, en, name) => { let o = st; while (o + 8 <= en) { let sz = b.readUInt32BE(o), hd = 8; const ty = b.toString("ascii", o + 4, o + 8); if (sz === 1) { sz = Number(b.readBigUInt64BE(o + 8)); hd = 16; } else if (sz === 0) sz = en - o; if (sz < hd) return null; if (ty === name) return [o + hd, Math.min(o + sz, en)]; o += sz; } return null; };
    const mo = find(0, b.length, "moov"), mv = mo && find(mo[0], mo[1], "mvhd"); if (!mv) return null;
    const v = b[mv[0]], p = mv[0] + 4, ts = v === 1 ? b.readUInt32BE(p + 16) : b.readUInt32BE(p + 8), du = v === 1 ? Number(b.readBigUInt64BE(p + 20)) : b.readUInt32BE(p + 12);
    return ts ? du / ts : null;
  } catch { return null; }
}
// «faststart»: pasa el índice (moov) del MP4 antes de los datos (mdat). Los celulares suelen grabarlo al final y entonces
// el navegador tiene que bajar el final del archivo antes de empezar a reproducir. Solo mueve cajas y corrige posiciones (stco/co64).
function faststart(b) {
  try {
    const top = []; let o = 0;
    while (o + 8 <= b.length) { let sz = b.readUInt32BE(o), hd = 8; if (sz === 1) { sz = Number(b.readBigUInt64BE(o + 8)); hd = 16; } else if (sz === 0) sz = b.length - o; if (sz < hd || o + sz > b.length) return b; top.push({ t: b.toString("ascii", o + 4, o + 8), o, sz }); o += sz; }
    const mi = top.findIndex((x) => x.t === "moov"), di = top.findIndex((x) => x.t === "mdat");
    if (mi < 0 || di < 0 || mi < di) return b; // ya está bien (o no es un MP4 normal)
    const moov = Buffer.from(b.subarray(top[mi].o, top[mi].o + top[mi].sz)), shift = moov.length;
    const fix = (st, en) => { // recorre moov > trak > mdia > minf > stbl y suma `shift` a cada posición de los datos
      for (let p = st; p + 8 <= en;) {
        const sz = moov.readUInt32BE(p), ty = moov.toString("ascii", p + 4, p + 8); if (sz < 8 || p + sz > en) return false;
        if (["trak", "mdia", "minf", "stbl"].includes(ty)) { if (!fix(p + 8, p + sz)) return false; }
        else if (ty === "stco") { const n = moov.readUInt32BE(p + 12); for (let i = 0; i < n; i++) { const v = moov.readUInt32BE(p + 16 + i * 4) + shift; if (v > 0xffffffff) return false; moov.writeUInt32BE(v, p + 16 + i * 4); } }
        else if (ty === "co64") { const n = moov.readUInt32BE(p + 12); for (let i = 0; i < n; i++) moov.writeBigUInt64BE(moov.readBigUInt64BE(p + 16 + i * 8) + BigInt(shift), p + 16 + i * 8); }
        p += sz;
      }
      return true;
    };
    if (!fix(8, moov.length)) return b;
    const before = top.slice(0, di).map((x) => b.subarray(x.o, x.o + x.sz)), rest = top.slice(di).filter((x) => x.t !== "moov").map((x) => b.subarray(x.o, x.o + x.sz));
    return Buffer.concat([...before, moov, ...rest]);
  } catch { return b; }
}
// Conversión opcional a MP4 H.264 liviano (máx. 1280 px, ~2 Mbps) con ffmpeg: si existe (paquete ffmpeg-static, FFMPEG_PATH o ffmpeg del sistema).
// Se hace en segundo plano, de a un video a la vez: el reel se publica al instante con el original y luego se cambia por el liviano.
const { spawn, spawnSync } = require("child_process"), os = require("os");
const FFMPEG = (() => { if (E.FFMPEG_PATH) return E.FFMPEG_PATH; try { const p = require("ffmpeg-static"); if (p) return p; } catch {} try { if (spawnSync("ffmpeg", ["-version"], { timeout: 5000 }).status === 0) return "ffmpeg"; } catch {} return null; })();
const REEL_KBPS = Number(E.REEL_MAX_KBPS) || 2500; // por encima de esto (o si no es MP4) se convierte
const tq = []; let tBusy = false;
function transcodeLater(postId, mediaId) { if (FFMPEG && E.REEL_TRANSCODE !== "0") { tq.push([postId, mediaId]); tNext(); } }
async function tNext() {
  if (tBusy || !tq.length) return; tBusy = true;
  const [postId, mediaId] = tq.shift(), dir = await require("fs/promises").mkdtemp(path.join(os.tmpdir(), "reel-"));
  try {
    const m = (await q("SELECT owner, mime, size, thumb FROM web_media WHERE id = ?", [mediaId]))[0]; if (!m) return;
    const rows = await q("SELECT data FROM web_media_chunks WHERE media_id = ? ORDER BY n", [mediaId]), src = Buffer.concat(rows.map((r) => r.data));
    const d = (m.mime === "video/webm" ? webmDur(src) : mp4Dur(src)) || 1, kbps = (src.length * 8) / d / 1000;
    if (m.mime === "video/mp4" && kbps <= REEL_KBPS) return; // ya es liviano
    const fin = path.join(dir, "in"), fout = path.join(dir, "out.mp4"); await require("fs/promises").writeFile(fin, src);
    const ok = await new Promise((done) => {
      const p = spawn(FFMPEG, ["-hide_banner", "-loglevel", "error", "-y", "-i", fin, "-map", "0:v:0", "-map", "0:a:0?", "-vf", "scale='if(gt(iw,ih),min(1280,iw),-2)':'if(gt(iw,ih),-2,min(1280,ih))'", "-c:v", "libx264", "-preset", "veryfast", "-crf", "26", "-maxrate", "2000k", "-bufsize", "4000k", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-ac", "2", "-movflags", "+faststart", fout], { stdio: ["ignore", "ignore", "pipe"] });
      let err = ""; p.stderr.on("data", (x) => { err += x; }); const to = setTimeout(() => p.kill("SIGKILL"), 10 * 60000);
      p.on("close", (c) => { clearTimeout(to); if (c !== 0) console.log("[reel] ffmpeg falló:", err.slice(-300)); done(c === 0); }); p.on("error", (e) => { clearTimeout(to); console.log("[reel] ffmpeg:", e.message); done(false); });
    });
    if (!ok) return;
    const out = await require("fs/promises").readFile(fout);
    if (!out.length || out.length >= src.length * 0.95 && m.mime === "video/mp4") return; // no ganó nada
    if (!(await q("SELECT 1 FROM web_post_media WHERE post_id = ? AND media_id = ?", [postId, mediaId])).length) return; // lo borraron mientras tanto
    const r = await q("INSERT INTO web_media (owner, kind, mime, size, thumb) VALUES (?, 'rel', 'video/mp4', ?, ?)", [m.owner, out.length, m.thumb]);
    try { for (let n = 0, o = 0; o < out.length; n++, o += CH) await q("INSERT INTO web_media_chunks (media_id, n, data) VALUES (?, ?, ?)", [r.insertId, n, out.subarray(o, o + CH)]); }
    catch (e) { await delMedia([r.insertId]); throw e; }
    const up = await q("UPDATE web_post_media SET media_id = ? WHERE post_id = ? AND media_id = ?", [r.insertId, postId, mediaId]);
    if (up.affectedRows) setTimeout(() => delMedia([mediaId]).catch(() => {}), 30 * 60000).unref(); // el original se borra en 30 min: quien lo esté viendo no se corta
    else await delMedia([r.insertId]);
    console.log(`[reel] ${postId}: ${Math.round(src.length / 1024)} KB → ${Math.round(out.length / 1024)} KB`);
  } catch (e) { console.log("[reel] no se pudo convertir:", e.message); }
  finally { await require("fs/promises").rm(dir, { recursive: true, force: true }).catch(() => {}); tBusy = false; tNext(); }
}
function webmDur(b) { // duración en segundos leyendo Duration y TimecodeScale (WebM)
  try {
    const h = b.subarray(0, 8192); if (h.readUInt32BE(0) !== 0x1a45dfa3) return null;
    let sc = 1e6; const i = h.indexOf(Buffer.from([0x2a, 0xd7, 0xb1])); if (i >= 0) { const n = h[i + 3] - 0x80; if (n >= 1 && n <= 6) sc = h.readUIntBE(i + 4, n); }
    const j = h.indexOf(Buffer.from([0x44, 0x89])); if (j < 0) return null;
    const d = h[j + 2] === 0x84 ? h.readFloatBE(j + 3) : h[j + 2] === 0x88 ? h.readDoubleBE(j + 3) : null;
    return d == null ? null : (d * sc) / 1e9;
  } catch { return null; }
}
const delMedia = async (ids) => { ids = ids.filter(Boolean); if (!ids.length) return; mcDrop(ids); ids.forEach((i) => mediaMeta.delete(Number(i))); await q("DELETE FROM web_media_chunks WHERE media_id IN (?)", [ids]); await q("DELETE FROM web_media WHERE id IN (?)", [ids]); };
async function removePosts(ids) { // borra publicaciones con sus me gusta, comentarios, etiquetas y medios
  if (!ids.length) return;
  const ms = await q("SELECT m.id, m.thumb FROM web_post_media pm JOIN web_media m ON m.id = pm.media_id WHERE pm.post_id IN (?)", [ids]);
  for (const t of ["web_post_media", "web_tags", "web_likes", "web_comments", "web_notifs", "web_saved"]) await q(`DELETE FROM ${t} WHERE post_id IN (?)`, [ids]);
  await q("DELETE FROM web_posts WHERE id IN (?)", [ids]);
  await delMedia(ms.flatMap((m) => [m.id, m.thumb]));
}
async function attach(rows) { // añade fotos/videos y etiquetas a cada publicación
  if (!rows.length) return rows;
  const ids = rows.map((r) => r.id), ms = await q("SELECT pm.post_id, m.id, m.kind, m.thumb FROM web_post_media pm JOIN web_media m ON m.id = pm.media_id WHERE pm.post_id IN (?) ORDER BY pm.pos", [ids]), ts = await q("SELECT post_id, name FROM web_tags WHERE post_id IN (?) ORDER BY player_id", [ids]);
  for (const r of rows) { r.media = ms.filter((m) => m.post_id === r.id).map((m) => ({ id: m.id, kind: m.kind, thumb: m.thumb })); r.tags = ts.filter((t) => t.post_id === r.id).map((t) => t.name); }
  const sh = [...new Set(rows.map((r) => Number(r.share_id)).filter(Boolean))];
  if (sh.length) {
    const os = await q(`SELECT p.id, p.type, p.title, p.body, p.author, (SELECT ${SKIN_COL} FROM player WHERE name = p.author LIMIT 1) AS skin, p.created_at FROM web_posts p WHERE p.id IN (?) AND p.vis = 0 AND p.type <> 'story'`, [sh]);
    await attach(os); // sus fotos y etiquetas (no llevan share_id: no hay recursión)
    for (const r of rows) if (r.share_id) r.shared = os.find((o) => Number(o.id) === Number(r.share_id)) || { gone: true };
  }
  return rows;
}
async function cleanup() { // historias vencidas (24 h) y medios huérfanos
  try {
    await q("DELETE FROM web_notifs WHERE created_at < NOW() - INTERVAL 45 DAY");
    await removePosts((await q("SELECT id FROM web_posts WHERE type = 'story' AND created_at < NOW() - INTERVAL 24 HOUR")).map((r) => r.id));
    await delMedia((await q("SELECT m.id FROM web_media m LEFT JOIN web_post_media pm ON pm.media_id = m.id LEFT JOIN web_media v ON v.thumb = m.id WHERE m.created_at < NOW() - INTERVAL 2 HOUR AND pm.media_id IS NULL AND v.id IS NULL")).map((r) => r.id));
  } catch (e) { console.log("[cleanup]", e.message); }
}
app.post("/api/media", express.raw({ type: () => true, limit: Math.max(VID_MAX, REEL_MAX) }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (req.headers["x-sc"] !== "1") return res.status(403).json({ error: "Petición no válida" });
  if (!hit("m|" + u.id, 20, 600000)) return res.status(429).json({ error: "Demasiadas subidas, espera unos minutos" });
  const kind = req.query.kind === "vid" ? "vid" : req.query.kind === "rel" ? "rel" : "img", mime = String(req.headers["content-type"] || "").split(";")[0].trim().toLowerCase();
  let buf = req.body;
  if (!Buffer.isBuffer(buf) || !buf.length) return res.status(400).json({ error: "Archivo vacío" });
  let thumb = null;
  if (kind === "img") { if (!imgOk(mime, buf) || buf.length > IMG_MAX) return res.status(400).json({ error: "Imagen no válida o muy pesada" }); }
  else {
    const mp = mime === "video/mp4" || mime === "video/quicktime", wb = mime === "video/webm", rel = kind === "rel", mx = rel ? REEL_MAX : VID_MAX, secs = rel ? REEL_SECS : VID_SECS;
    if ((!mp && !wb) || buf.length > mx || (mp && buf.toString("ascii", 4, 8) !== "ftyp")) return res.status(400).json({ error: "Video no válido. Usa MP4 de hasta " + Math.round(mx / 1048576) + " MB" });
    const d = mp ? mp4Dur(buf) : webmDur(buf);
    if (d == null || d > secs + 0.5) return res.status(400).json({ error: d == null ? "No pude leer la duración del video. Usa un MP4." : `El video debe durar máximo ${rel ? REEL_SECS : 15} segundos` });
    if (mp) buf = faststart(buf);
    thumb = pid(req.query.thumb);
    if (!thumb || !(await q("SELECT id FROM web_media WHERE id = ? AND owner = ? AND kind = 'img'", [thumb, u.id])).length) return res.status(400).json({ error: "Falta la miniatura del video" });
  }
  const r = await q("INSERT INTO web_media (owner, kind, mime, size, thumb) VALUES (?, ?, ?, ?, ?)", [u.id, kind, mime, buf.length, thumb]);
  try { for (let n = 0, o = 0; o < buf.length; n++, o += CH) await q("INSERT INTO web_media_chunks (media_id, n, data) VALUES (?, ?, ?)", [r.insertId, n, buf.subarray(o, o + CH)]); }
  catch (e) { await delMedia([r.insertId]); throw e; }
  res.json({ id: r.insertId, kind });
});
// Caché en memoria de los trozos más vistos (los reels se ven muchas veces seguidas): evita leer MySQL en cada petición de video
const MCACHE_MAX = (Number(E.MEDIA_CACHE_MB) || 96) * 1048576, mcache = new Map(); let mcacheSize = 0;
const mcGet = (k) => { const v = mcache.get(k); if (v) { mcache.delete(k); mcache.set(k, v); } return v; }; // al leerlo pasa a ser el más reciente
const mcPut = (k, v) => { if (v.length > MCACHE_MAX / 4 || mcache.has(k)) return; mcache.set(k, v); mcacheSize += v.length; for (const [x, y] of mcache) { if (mcacheSize <= MCACHE_MAX) break; mcache.delete(x); mcacheSize -= y.length; } };
const mcDrop = (ids) => { const set = new Set(ids.map(String)); for (const [k, v] of mcache) if (set.has(k.split(":")[0])) { mcache.delete(k); mcacheSize -= v.length; } };
const mediaMeta = new Map(); // id → { mime, size } (los medios no cambian nunca)
async function chunks(id, a, b) { // trozos a..b de un medio, desde la caché o de MySQL (solo los que faltan)
  const out = [], miss = [];
  for (let n = a; n <= b; n++) { const c = mcGet(id + ":" + n); out.push(c); if (!c) miss.push(n); }
  if (miss.length) for (const r of await q("SELECT n, data FROM web_media_chunks WHERE media_id = ? AND n BETWEEN ? AND ? ORDER BY n", [id, miss[0], miss.at(-1)])) { const n = Number(r.n); if (out[n - a]) continue; out[n - a] = r.data; mcPut(id + ":" + n, r.data); }
  return out.map((c) => c || Buffer.alloc(0));
}
app.get("/media/:id(\\d+)", async (req, res) => {
  const id = Number(req.params.id);
  let m = mediaMeta.get(id);
  if (!m) { m = (await q("SELECT mime, size FROM web_media WHERE id = ?", [id]))[0]; if (!m) return res.status(404).end(); if (mediaMeta.size > 5000) mediaMeta.clear(); mediaMeta.set(id, m); }
  const size = Number(m.size), etag = `"m${id}-${size}"`; let s = 0, e = size - 1, part = false;
  const head = { "Content-Type": m.mime, "Accept-Ranges": "bytes", "Cache-Control": "public, max-age=31536000, immutable", ETag: etag }; // un id nunca cambia de contenido
  if (req.headers["if-none-match"] === etag) return res.status(304).set(head).end();
  const rg = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || "");
  if (rg && (rg[1] || rg[2])) {
    part = true;
    if (rg[1] === "") s = Math.max(0, size - Number(rg[2])); else { s = Number(rg[1]); if (rg[2]) e = Math.min(e, Number(rg[2])); }
    if (s > e || s >= size) return res.status(416).set("Content-Range", `bytes */${size}`).end();
    e = Math.min(e, s + 2 * 1048576 - 1); // máx. 2 MB por respuesta: el navegador pide el resto según lo necesite
  }
  res.status(part ? 206 : 200).set({ ...head, "Content-Length": e - s + 1, ...(part ? { "Content-Range": `bytes ${s}-${e}/${size}` } : {}) });
  if (req.method === "HEAD") return res.end();
  // se envía por tandas de 1 MB: un archivo grande sin Range no se carga entero en memoria
  try {
    for (let a = Math.floor(s / CH), last = Math.floor(e / CH); a <= last && !res.destroyed; a += 4) {
      const b = Math.min(last, a + 3), buf = Buffer.concat(await chunks(id, a, b)), off = a * CH;
      const piece = buf.subarray(Math.max(0, s - off), Math.min(buf.length, e - off + 1));
      if (!res.write(piece)) await new Promise((ok) => { res.once("drain", ok); res.once("close", ok); });
    }
    res.end();
  } catch (err) { console.log("[media]", err.message); res.destroy(); } // ya se enviaron las cabeceras: se corta la conexión y el navegador reintenta
});
// ---- Historias: una foto o video (máx. 15 s) por historia; duran 24 h; usan los mismos me gusta y comentarios que las publicaciones ----
app.post("/api/stories", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const mid = pid(req.body.media), cap = str(req.body.body).trim().slice(0, 200);
  if (!hit("s|" + u.id, 10, 60000)) return res.status(429).json({ error: "Vas muy rápido, espera un momento" });
  if (!mid || !(await q("SELECT m.id FROM web_media m WHERE m.id = ? AND m.owner = ? AND m.kind IN ('img', 'vid') AND NOT EXISTS (SELECT 1 FROM web_post_media x WHERE x.media_id = m.id)", [mid, u.id])).length) return res.status(400).json({ error: "Sube primero la foto o el video" });
  const r = await q("INSERT INTO web_posts (type, title, body, author) VALUES ('story', '', ?, ?)", [cap, u.name]);
  await q("INSERT INTO web_post_media (post_id, media_id, pos) VALUES (?, ?, 0)", [r.insertId, mid]);
  res.json({ ok: true, id: r.insertId });
});
// ---- Reels: videos de la comunidad (kind = "rel", hasta REEL_MAX_SECS s), con me gusta, comentarios y guardados ----
const REEL_Q = (where) => `SELECT p.id, p.body, p.author, p.created_at, (SELECT ${SKIN_COL} FROM player WHERE name = p.author LIMIT 1) AS skin, (SELECT id FROM player WHERE name = p.author LIMIT 1) AS aid, (SELECT COUNT(*) FROM web_likes l WHERE l.post_id = p.id) AS likes, (SELECT COUNT(*) FROM web_comments c WHERE c.post_id = p.id) AS comments, (SELECT COUNT(*) FROM web_saved s WHERE s.post_id = p.id) AS saves, (SELECT COALESCE(MAX(l.kind), 0) FROM web_likes l WHERE l.post_id = p.id AND l.player_id = ?) AS mine, (SELECT GROUP_CONCAT(DISTINCT l.kind ORDER BY l.kind) FROM web_likes l WHERE l.post_id = p.id) AS rk, (SELECT COUNT(*) FROM web_saved s WHERE s.post_id = p.id AND s.player_id = ?) AS saved FROM web_posts p WHERE p.type = 'reel' ${where}`;
app.get("/api/reels", async (req, res) => { // ?before=id (siguiente página) · ?author=Nombre · ?saved=1 (mis guardados) · ?start=id (abre ese primero)
  const uid = sessionId(req) || 0, before = pid(req.query.before), start = pid(req.query.start), au = str(req.query.author), per = Math.min(24, Math.max(1, Number(req.query.n) || 8));
  const where = [], wp = [uid, uid];
  if (req.query.saved === "1") { if (!uid) return res.status(401).json({ error: "Inicia sesión" }); where.push("AND p.id IN (SELECT post_id FROM web_saved WHERE player_id = ?)"); wp.push(uid); }
  if (/^\w{1,24}$/.test(au)) { where.push("AND p.author = ?"); wp.push(au); }
  if (before) { where.push("AND p.id < ?"); wp.push(before); }
  if (start) { where.push("AND p.id <> ?"); wp.push(start); }
  let rows = await q(REEL_Q(where.join(" ")) + " ORDER BY p.id DESC LIMIT ?", [...wp, per + 1]);
  const more = rows.length > per; rows = rows.slice(0, per);
  if (start && !before) rows = [...(await q(REEL_Q("AND p.id = ?"), [uid, uid, start])), ...rows];
  const st = uid ? await friendStates(uid, [...new Set(rows.map((r) => Number(r.aid)).filter(Boolean))]) : new Map();
  const out = (await attach(rows)).map(({ aid, ...r }) => ({ ...r, fstate: !uid ? "none" : String(aid) === String(uid) ? "self" : st.get(Number(aid)) || "none" }));
  res.json({ items: out, more });
});
app.post("/api/reels", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const mid = pid(req.body.media), cap = str(req.body.body).trim().slice(0, 500);
  if (!hit("r|" + u.id, 5, 600000)) return res.status(429).json({ error: "Vas muy rápido, espera unos minutos" });
  if (!mid || !(await q("SELECT m.id FROM web_media m WHERE m.id = ? AND m.owner = ? AND m.kind = 'rel' AND NOT EXISTS (SELECT 1 FROM web_post_media x WHERE x.media_id = m.id)", [mid, u.id])).length) return res.status(400).json({ error: "Sube primero el video" });
  const r = await q("INSERT INTO web_posts (type, title, body, author) VALUES ('reel', '', ?, ?)", [cap, u.name]);
  await q("INSERT INTO web_post_media (post_id, media_id, pos) VALUES (?, ?, 0)", [r.insertId, mid]);
  transcodeLater(r.insertId, mid);
  for (const m of await mentioned(cap, u.id)) await notify(m.id, u, "mention", r.insertId, cap);
  res.json({ ok: true, id: r.insertId });
});
app.post("/api/posts/:id/save", async (req, res) => { // guardar / quitar de guardados
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id);
  if (!(await q("SELECT 1 FROM web_posts WHERE id = ?", [id])).length) return res.status(404).json({ error: "No existe" });
  const del = await q("DELETE FROM web_saved WHERE post_id = ? AND player_id = ?", [id, u.id]);
  if (!del.affectedRows) await q("INSERT IGNORE INTO web_saved (post_id, player_id) VALUES (?, ?)", [id, u.id]);
  res.json({ saved: !del.affectedRows, saves: Number((await q("SELECT COUNT(*) AS n FROM web_saved WHERE post_id = ?", [id]))[0].n) });
});
app.get("/api/user/:name/photos", async (req, res) => { // fotos de las publicaciones de un jugador (pestaña Fotos del perfil)
  const n = str(req.params.name);
  if (!/^\w{1,24}$/.test(n)) return res.json([]);
  res.json(await q("SELECT pm.post_id, m.id FROM web_posts p JOIN web_post_media pm ON pm.post_id = p.id JOIN web_media m ON m.id = pm.media_id WHERE p.author = ? AND p.type = 'post' AND m.kind = 'img' ORDER BY p.id DESC, pm.pos LIMIT 60", [n]));
});
// ---- Buscar jugadores para etiquetar (solo nombre y skin) ----
app.get("/api/users/search", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const v = str(req.query.q).trim().replace(/\s+/g, "_").slice(0, 24).replace(/[\\%_]/g, "\\$&");
  if (v.length < 2) return res.json([]);
  res.json(await q(`SELECT name, ${SKIN_COL} AS skin FROM player WHERE name LIKE ? ORDER BY connected DESC, name LIMIT 8`, [`%${v}%`]));
});

return { STORY_LIVE, FFMPEG, attach, cleanup };
};
