// Cuenta: sesiones abiertas, verificación en dos pasos, recuperar contraseña por correo, descargar mis datos y eliminar mi contenido.
const { b32encode, totpOk } = require("../lib/util");
module.exports = (app, { crypto, E, BASE, str, q, sessionOf, hit, me, checkPassword, captchaOk, setSession, clearSession, LOGOUT, REVOKED, getRemovePosts }) => {
const sha = (s) => crypto.createHash("sha256").update(s).digest("hex");
const gamePass = (pass) => { const salt = crypto.randomBytes(12).toString("base64").replace(/[^A-Za-z0-9]/g, "").slice(0, 15).padEnd(15, "x"); return { salt, hash: crypto.createHash("sha256").update(pass + salt).digest("hex").toUpperCase() }; }; // mismo formato que el juego
async function logoutAll(uid) { // cierra todas las sesiones del jugador (las cookies anteriores dejan de valer)
  const at = Date.now();
  await q("INSERT INTO web_settings (player_id, logout_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE logout_at = VALUES(logout_at)", [uid, at]);
  LOGOUT.set(Number(uid), at);
  await q("DELETE FROM web_sessions WHERE player_id = ?", [uid]);
}
async function init() {
  await q("CREATE TABLE IF NOT EXISTS web_sessions (sid CHAR(16) NOT NULL PRIMARY KEY, player_id INT NOT NULL, ua VARCHAR(200) NOT NULL DEFAULT '', ip VARCHAR(45) NOT NULL DEFAULT '', exp BIGINT NOT NULL, revoked TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY p (player_id), KEY ls (last_seen)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_resets (h CHAR(64) NOT NULL PRIMARY KEY, player_id INT NOT NULL, exp BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await loadRevoked(); setInterval(loadRevoked, 60000).unref();
}
async function loadRevoked() {
  try {
    await q("DELETE FROM web_sessions WHERE exp < ?", [Date.now()]); await q("DELETE FROM web_resets WHERE exp < ?", [Date.now()]);
    const rows = await q("SELECT sid FROM web_sessions WHERE revoked = 1"); REVOKED.clear(); for (const r of rows) REVOKED.add(r.sid);
  } catch (e) { console.log("[sesión]", e.message); }
}
const revoke = async (sid) => { if (!sid) return; REVOKED.add(sid); await q("UPDATE web_sessions SET revoked = 1 WHERE sid = ?", [sid]).catch(() => {}); };

// ---- Sesiones abiertas ----
const device = (ua) => { // «Chrome · Android» a partir del User-Agent
  const b = /Edg\//.test(ua) ? "Edge" : /OPR\/|Opera/.test(ua) ? "Opera" : /SamsungBrowser/.test(ua) ? "Samsung Internet" : /Firefox\//.test(ua) ? "Firefox" : /Chrome\//.test(ua) ? "Chrome" : /Safari\//.test(ua) ? "Safari" : "Navegador";
  const o = /Android/.test(ua) ? "Android" : /iPhone|iPad|iPod/.test(ua) ? "iPhone / iPad" : /Windows/.test(ua) ? "Windows" : /Mac OS X/.test(ua) ? "Mac" : /Linux/.test(ua) ? "Linux" : /SmartTV|Tizen|WebOS/i.test(ua) ? "TV" : "";
  return o ? `${b} · ${o}` : b;
};
app.get("/api/settings/sessions", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const cur = (sessionOf(req) || {}).sid || "";
  const rows = await q("SELECT sid, ua, ip, created_at, last_seen FROM web_sessions WHERE player_id = ? AND revoked = 0 AND exp > ? ORDER BY last_seen DESC LIMIT 30", [u.id, Date.now()]);
  res.json(rows.map((r) => ({ id: r.sid, device: device(r.ua), ip: r.ip, created_at: r.created_at, last_seen: r.last_seen, current: r.sid === cur })));
});
app.delete("/api/settings/sessions/:sid", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const sid = str(req.params.sid);
  if (!/^[a-f0-9]{16}$/.test(sid) || !(await q("SELECT 1 FROM web_sessions WHERE sid = ? AND player_id = ?", [sid, u.id])).length) return res.status(404).json({ error: "Esa sesión no existe" });
  await revoke(sid);
  res.json({ ok: true });
});

// ---- Verificación en dos pasos (app de códigos: Google Authenticator, Authy, etc.) ----
const totpRow = async (uid) => (await q("SELECT totp, totp_on FROM web_settings WHERE player_id = ?", [uid]))[0] || {};
app.get("/api/settings/2fa", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  res.json({ on: Number((await totpRow(u.id)).totp_on) === 1 });
});
app.post("/api/settings/2fa/setup", async (req, res) => { // genera la clave; no se activa hasta confirmar un código
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("2s|" + u.id, 10, 3600000)) return res.status(429).json({ error: "Demasiados intentos. Espera un rato." });
  if (Number((await totpRow(u.id)).totp_on) === 1) return res.status(409).json({ error: "Ya la tienes activada" });
  const secret = b32encode(crypto.randomBytes(20));
  await q("INSERT INTO web_settings (player_id, totp, totp_on) VALUES (?, ?, 0) ON DUPLICATE KEY UPDATE totp = VALUES(totp), totp_on = 0", [u.id, secret]);
  res.json({ secret, uri: `otpauth://totp/SampCity:${encodeURIComponent(u.name)}?secret=${secret}&issuer=SampCity` });
});
app.post("/api/settings/2fa/enable", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("2e|" + u.id, 10, 900000)) return res.status(429).json({ error: "Demasiados intentos. Espera 15 minutos." });
  const r = await totpRow(u.id);
  if (!r.totp || !totpOk(r.totp, str(req.body.code))) return res.status(400).json({ error: "Código incorrecto. Revisa la hora de tu celular e inténtalo de nuevo." });
  await q("UPDATE web_settings SET totp_on = 1 WHERE player_id = ?", [u.id]);
  res.json({ ok: true });
});
app.post("/api/settings/2fa/disable", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("2d|" + u.id, 5, 900000)) return res.status(429).json({ error: "Demasiados intentos. Espera 15 minutos." });
  const r = (await q("SELECT salt, pass FROM player WHERE id = ?", [u.id]))[0];
  if (!r || !r.pass || !(await checkPassword(str(req.body.password).slice(0, 72).replace(/%/g, "#"), r.salt, r.pass))) return res.status(403).json({ error: "La contraseña no es correcta" });
  await q("UPDATE web_settings SET totp = '', totp_on = 0 WHERE player_id = ?", [u.id]);
  res.json({ ok: true });
});

// ---- Recuperar contraseña por correo (necesita SMTP_HOST en el .env y el paquete nodemailer) ----
let mailer = null;
if (E.SMTP_HOST) { try { mailer = require("nodemailer").createTransport({ host: E.SMTP_HOST, port: Number(E.SMTP_PORT) || 587, secure: Number(E.SMTP_PORT) === 465, auth: E.SMTP_USER ? { user: E.SMTP_USER, pass: E.SMTP_PASS || "" } : undefined }); } catch (e) { console.log("[correo] falta el paquete nodemailer (npm install):", e.message); } }
const mailOn = () => !!mailer;
app.post("/api/recover", async (req, res) => {
  if (!mailer) return res.status(503).json({ error: "La recuperación por correo no está activa. Escribe al staff por Discord." });
  if (!hit("rc|" + req.ip, 5, 3600000)) return res.status(429).json({ error: "Demasiados intentos. Prueba más tarde." });
  if (!(await captchaOk(req))) return res.status(400).json({ error: "Completa la verificación anti-robots", captcha: true });
  const v = str(req.body.name).trim().slice(0, 40);
  const p = v ? (await q("SELECT id, name, email FROM player WHERE name = ? OR email = ? LIMIT 1", [v, v]))[0] : null;
  if (p && p.email && hit("rcu|" + p.id, 3, 3600000)) {
    const token = crypto.randomBytes(32).toString("base64url");
    await q("DELETE FROM web_resets WHERE player_id = ?", [p.id]);
    await q("INSERT INTO web_resets (h, player_id, exp) VALUES (?, ?, ?)", [sha(token), p.id, Date.now() + 3600000]);
    const link = `${BASE}/recuperar?t=${token}`;
    mailer.sendMail({ from: E.MAIL_FROM || E.SMTP_USER, to: p.email, subject: "Recupera tu contraseña de SampCity", text: `Hola ${p.name.replace(/_/g, " ")}:\n\nPara poner una contraseña nueva abre este enlace (vale por 1 hora):\n${link}\n\nSi no lo pediste tú, ignora este correo: tu contraseña no cambia.\n\nSampCity RolePlay` }).catch((e) => console.log("[correo]", e.message));
  }
  res.json({ ok: true }); // la misma respuesta exista o no la cuenta (no se revela quién está registrado)
});
app.post("/api/recover/reset", async (req, res) => {
  if (!hit("rr|" + req.ip, 10, 3600000)) return res.status(429).json({ error: "Demasiados intentos. Prueba más tarde." });
  const token = str(req.body.token).slice(0, 100), nu = str(req.body.password).replace(/%/g, "#");
  if (nu.length < 6 || nu.length > 18) return res.status(400).json({ error: "La contraseña tiene que tener de 6 a 18 caracteres." });
  const r = token ? (await q("SELECT player_id FROM web_resets WHERE h = ? AND exp > ?", [sha(token), Date.now()]))[0] : null;
  if (!r) return res.status(400).json({ error: "El enlace no es válido o ya caducó. Pide uno nuevo." });
  const g = gamePass(nu);
  await q("UPDATE player SET salt = ?, pass = ? WHERE id = ?", [g.salt, g.hash, r.player_id]);
  await q("DELETE FROM web_resets WHERE player_id = ?", [r.player_id]);
  await logoutAll(r.player_id);
  res.json({ ok: true });
});

// ---- Descargar mis datos ----
app.get("/api/settings/export", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("ex|" + u.id, 3, 3600000)) return res.status(429).json({ error: "Ya descargaste tus datos hace poco. Prueba más tarde." });
  const names = async (ids) => (ids.length ? (await q("SELECT name FROM player WHERE id IN (?)", [ids])).map((r) => r.name) : []);
  const data = {
    generado: new Date().toISOString(), cuenta: (await q("SELECT name, email, reg_date, last_connection, level FROM player WHERE id = ?", [u.id]))[0],
    perfil: (await q("SELECT bio, city, job, link, bday FROM web_profile WHERE player_id = ?", [u.id]))[0] || {},
    configuracion: (await q("SELECT msg, prof, tag, nmask FROM web_settings WHERE player_id = ?", [u.id]))[0] || {},
    publicaciones: await q("SELECT id, type, title, body, vis, created_at FROM web_posts WHERE author = ? ORDER BY id", [u.name]),
    comentarios: await q("SELECT post_id, body, created_at FROM web_comments WHERE player_id = ? ORDER BY id", [u.id]),
    mensajes: await q("SELECT m.id, IF(m.a = ?, 'enviado', 'recibido') AS sentido, p.name AS con, m.body, m.created_at FROM web_msgs m LEFT JOIN player p ON p.id = IF(m.a = ?, m.b, m.a) WHERE m.a = ? OR m.b = ? ORDER BY m.id LIMIT 20000", [u.id, u.id, u.id, u.id]),
    amigos: await names((await q("SELECT IF(a = ?, b, a) AS id FROM web_friends WHERE status = 1 AND (a = ? OR b = ?)", [u.id, u.id, u.id])).map((r) => r.id)),
    siguiendo: await names((await q("SELECT b FROM web_follows WHERE a = ?", [u.id])).map((r) => r.b)),
    bloqueados: await names((await q("SELECT b FROM web_blocks WHERE a = ?", [u.id])).map((r) => r.b)),
    anuncios: await q("SELECT title, price, body, status, created_at FROM web_market WHERE seller = ?", [u.id]).catch(() => []),
    sesiones: await q("SELECT ua, ip, created_at, last_seen FROM web_sessions WHERE player_id = ?", [u.id]),
  };
  res.set({ "Content-Type": "application/json; charset=utf-8", "Content-Disposition": `attachment; filename="sampcity-${u.name}.json"` }).send(JSON.stringify(data, null, 1));
});

// ---- Eliminar mi contenido de la red social (la cuenta del juego NO se toca) ----
app.post("/api/settings/delete", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("del|" + u.id, 3, 3600000)) return res.status(429).json({ error: "Demasiados intentos. Prueba más tarde." });
  const r = (await q("SELECT salt, pass FROM player WHERE id = ?", [u.id]))[0];
  if (!r || !r.pass || !(await checkPassword(str(req.body.password).slice(0, 72).replace(/%/g, "#"), r.salt, r.pass))) return res.status(403).json({ error: "La contraseña no es correcta" });
  const id = u.id, imgs = [], del = (sql, p) => q(sql, p).catch((e) => console.log("[eliminar]", e.message));
  await getRemovePosts()((await q("SELECT id FROM web_posts WHERE author = ?", [u.name])).map((x) => x.id));
  const cs = (await q("SELECT id FROM web_comments WHERE player_id = ? OR parent IN (SELECT id FROM (SELECT id FROM web_comments WHERE player_id = ?) x)", [id, id])).map((x) => x.id);
  if (cs.length) { await del("DELETE FROM web_clikes WHERE comment_id IN (?)", [cs]); await del("DELETE FROM web_comments WHERE id IN (?)", [cs]); }
  imgs.push(...(await q("SELECT img FROM web_msgs WHERE (a = ? OR b = ?) AND img IS NOT NULL AND room IS NULL", [id, id])).map((x) => x.img), ...(await q("SELECT img FROM web_msgs WHERE a = ? AND img IS NOT NULL", [id])).map((x) => x.img));
  await del("DELETE FROM web_msg_rx WHERE player_id = ? OR msg_id IN (SELECT id FROM web_msgs WHERE a = ? OR (b = ? AND room IS NULL))", [id, id, id]);
  await del("DELETE FROM web_msgs WHERE a = ? OR (b = ? AND room IS NULL)", [id, id]);
  const pf = (await q("SELECT avatar, cover FROM web_profile WHERE player_id = ?", [id]))[0]; if (pf) imgs.push(pf.avatar, pf.cover);
  imgs.push(...(await q("SELECT img FROM web_market WHERE seller = ? AND img IS NOT NULL", [id]).catch(() => [])).map((x) => x.img));
  for (const [t, c] of [["web_likes", "player_id"], ["web_clikes", "player_id"], ["web_saved", "player_id"], ["web_hidden", "player_id"], ["web_tags", "player_id"], ["web_poll_votes", "player_id"], ["web_story_views", "player_id"], ["web_event_rsvp", "player_id"], ["web_notifs", "player_id"], ["web_notifs", "actor_id"], ["web_profile", "player_id"], ["web_chat_clear", "player_id"], ["web_chat_clear", "peer"], ["web_market", "seller"], ["web_push", "player_id"], ["web_room_members", "player_id"], ["web_group_members", "player_id"], ["web_applications", "player_id"], ["web_verified", "player_id"]]) await del(`DELETE FROM ${t} WHERE ${c} = ?`, [id]);
  for (const t of ["web_friends", "web_follows", "web_blocks"]) await del(`DELETE FROM ${t} WHERE a = ? OR b = ?`, [id, id]);
  const evs = (await q("SELECT id FROM web_events WHERE owner = ?", [id]).catch(() => [])).map((x) => x.id);
  if (evs.length) { await del("DELETE FROM web_event_rsvp WHERE event_id IN (?)", [evs]); await del("DELETE FROM web_events WHERE id IN (?)", [evs]); }
  for (const g of await q("SELECT id FROM web_groups WHERE owner = ?", [id]).catch(() => [])) { // mis grupos pasan a otro miembro; si no queda nadie, se borran
    const nx = (await q("SELECT player_id FROM web_group_members WHERE group_id = ? AND role > 0 ORDER BY role DESC LIMIT 1", [g.id]))[0];
    if (nx) { await del("UPDATE web_groups SET owner = ? WHERE id = ?", [nx.player_id, g.id]); await del("UPDATE web_group_members SET role = 2 WHERE group_id = ? AND player_id = ?", [g.id, nx.player_id]); }
    else { await getRemovePosts()((await q("SELECT id FROM web_posts WHERE group_id = ?", [g.id])).map((x) => x.id)); await del("DELETE FROM web_group_members WHERE group_id = ?", [g.id]); await del("DELETE FROM web_groups WHERE id = ?", [g.id]); }
  }
  for (const rm of await q("SELECT id FROM web_rooms WHERE owner = ?", [id]).catch(() => [])) { // lo mismo con mis chats de grupo
    const nx = (await q("SELECT player_id FROM web_room_members WHERE room_id = ? LIMIT 1", [rm.id]))[0];
    if (nx) await del("UPDATE web_rooms SET owner = ? WHERE id = ?", [nx.player_id, rm.id]); else { await del("DELETE FROM web_msgs WHERE room = ?", [rm.id]); await del("DELETE FROM web_rooms WHERE id = ?", [rm.id]); }
  }
  const im = [...new Set(imgs.filter(Boolean))]; if (im.length) await del("DELETE FROM web_images WHERE id IN (?)", [im]);
  await del("UPDATE web_settings SET msg = 0, prof = 0, tag = 0, nmask = 0, totp = '', totp_on = 0 WHERE player_id = ?", [id]);
  await logoutAll(id); clearSession(res);
  res.json({ ok: true });
});
return { init, logoutAll, revoke, mailOn, gamePass };
};
