// Configuración de la cuenta, privacidad, bloqueos y reportes. Devuelve ayudas que usan los demás módulos.
module.exports = (app, { crypto, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, checkPassword, setSession, LOGOUT, mod }) => {
const NAME = /^\w{1,24}$/;
const DEF = { msg: 0, prof: 0, tag: 0, nmask: 0 }; // msg/tag: 0 todos, 1 amigos, 2 nadie · prof: 0 público, 1 amigos · nmask: avisos silenciados (bits)
const NBIT = { like: 1, comment: 2, reply: 2, mention: 4, mention_c: 4, tag: 4, friend_req: 8, friend_acc: 8, news: 16, update: 16 };
const getSettings = async (uid) => ({ ...DEF, ...((await q("SELECT msg, prof, tag, nmask FROM web_settings WHERE player_id = ?", [uid]))[0] || {}) });
// ids con bloqueo entre ambos (en cualquiera de los dos sentidos)
const blockedIds = async (uid) => (uid ? (await q("SELECT IF(a = ?, b, a) AS id FROM web_blocks WHERE a = ? OR b = ?", [uid, uid, uid])).map((r) => Number(r.id)) : []);
const isBlocked = async (a, b) => !!a && !!b && (await q("SELECT 1 FROM web_blocks WHERE (a = ? AND b = ?) OR (a = ? AND b = ?) LIMIT 1", [a, b, b, a])).length > 0;
// ¿este aviso no debe crearse? (bloqueo entre ambos o tipo silenciado por quien lo recibe)
const muted = async (to, type, actorId) => { try { if (await isBlocked(to, actorId)) return true; const bit = NBIT[type]; return !!bit && (Number((await getSettings(to)).nmask) & bit) !== 0; } catch { return false; } };
// cierre de sesión en todos los dispositivos: se guarda el momento y las cookies anteriores dejan de valer
const loadLogout = async () => { try { for (const r of await q("SELECT player_id, logout_at FROM web_settings WHERE logout_at > 0")) LOGOUT.set(Number(r.player_id), Number(r.logout_at)); } catch {} };
setInterval(loadLogout, 60000).unref();

// Cierra todas las sesiones del jugador y abre una nueva solo en este dispositivo
async function closeOthers(req, res, uid) {
  const at = Date.now() - 1;
  await q("INSERT INTO web_settings (player_id, logout_at) VALUES (?, ?) ON DUPLICATE KEY UPDATE logout_at = VALUES(logout_at)", [uid, at]);
  LOGOUT.set(Number(uid), at);
  await q("DELETE FROM web_sessions WHERE player_id = ?", [uid]).catch(() => {});
  setSession(res, uid, "", req);
}
app.get("/api/settings", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const [s, bl] = await Promise.all([getSettings(u.id), q(`SELECT p.name, p.${SKIN_COL} AS skin FROM web_blocks b JOIN player p ON p.id = b.b WHERE b.a = ? ORDER BY b.created_at DESC LIMIT 200`, [u.id])]);
  res.json({ ...s, blocked: bl });
});
app.post("/api/settings", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("st|" + u.id, 40, 3600000)) return res.status(429).json({ error: "Demasiados cambios. Espera un rato." });
  const n = (v, max) => Math.min(max, Math.max(0, Math.floor(Number(v)) || 0)), cur = await getSettings(u.id), b = req.body;
  const s = { msg: b.msg === undefined ? cur.msg : n(b.msg, 2), prof: b.prof === undefined ? cur.prof : n(b.prof, 1), tag: b.tag === undefined ? cur.tag : n(b.tag, 2), nmask: b.nmask === undefined ? cur.nmask : n(b.nmask, 31) };
  await q("INSERT INTO web_settings (player_id, msg, prof, tag, nmask) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE msg = VALUES(msg), prof = VALUES(prof), tag = VALUES(tag), nmask = VALUES(nmask)", [u.id, s.msg, s.prof, s.tag, s.nmask]);
  res.json({ ok: true, ...s });
});
// Cambiar contraseña: mismo formato que usa el juego al registrar (SHA256(clave + sal) en mayúsculas)
app.post("/api/settings/password", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("pw|" + u.id, 5, 900000)) return res.status(429).json({ error: "Demasiados intentos. Espera 15 minutos." });
  const cur = str(req.body.current).slice(0, 72).replace(/%/g, "#"), nu = str(req.body.next).replace(/%/g, "#"); // el juego cambia % por #
  if (nu.length < 6 || nu.length > 18) return res.status(400).json({ error: "La contraseña nueva debe tener entre 6 y 18 caracteres" }); // el mismo límite que el registro y el juego
  const r = (await q("SELECT salt, pass FROM player WHERE id = ?", [u.id]))[0];
  if (!r || !r.pass || !(await checkPassword(cur, r.salt, r.pass))) return res.status(403).json({ error: "La contraseña actual no es correcta" });
  const salt = crypto.randomBytes(12).toString("base64").replace(/[^A-Za-z0-9]/g, "").slice(0, 15).padEnd(15, "x");
  await q("UPDATE player SET salt = ?, pass = ? WHERE id = ?", [salt, crypto.createHash("sha256").update(nu + salt).digest("hex").toUpperCase(), u.id]);
  await closeOthers(req, res, u.id); // quien tuviera la contraseña vieja queda fuera; este dispositivo sigue dentro
  res.json({ ok: true });
});
app.post("/api/settings/logout-all", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  await closeOthers(req, res, u.id);
  res.json({ ok: true });
});

// ---- Bloqueos ----
app.post("/api/blocks/:name", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const n = str(req.params.name), p = NAME.test(n) ? (await q("SELECT id, admin_level FROM player WHERE name = ?", [n]))[0] : null;
  if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  if (Number(p.id) === Number(u.id)) return res.status(400).json({ error: "No puedes bloquearte a ti mismo" });
  if (Number(p.admin_level) >= MIN_POST) return res.status(403).json({ error: "No se puede bloquear al staff" });
  await q("INSERT IGNORE INTO web_blocks (a, b) VALUES (?, ?)", [u.id, p.id]);
  await q("DELETE FROM web_friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", [u.id, p.id, p.id, u.id]); // bloquear también quita la amistad
  res.json({ ok: true });
});
app.delete("/api/blocks/:name", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const n = str(req.params.name), p = NAME.test(n) ? (await q("SELECT id FROM player WHERE name = ?", [n]))[0] : null;
  if (p) await q("DELETE FROM web_blocks WHERE a = ? AND b = ?", [u.id, p.id]);
  res.json({ ok: true });
});

// ---- Reportes: cualquiera reporta, el staff revisa ----
const KINDS = ["post", "comment", "user", "market", "msg"], REASONS = ["spam", "acoso", "contenido", "estafa", "suplantacion", "otro"];
app.post("/api/reports", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("rp|" + u.id, 10, 3600000)) return res.status(429).json({ error: "Enviaste muchos reportes. Espera un rato." });
  const kind = str(req.body.kind), reason = str(req.body.reason), note = str(req.body.note).trim().slice(0, 300);
  let target = pid(req.body.target);
  if (kind === "user") { const n = str(req.body.name), p = NAME.test(n) ? (await q("SELECT id FROM player WHERE name = ?", [n]))[0] : null; target = p ? Number(p.id) : 0; }
  if (!KINDS.includes(kind) || !REASONS.includes(reason) || !target) return res.status(400).json({ error: "Faltan datos" });
  if ((await q("SELECT 1 FROM web_reports WHERE reporter = ? AND kind = ? AND target = ? AND status = 0 LIMIT 1", [u.id, kind, target])).length) return res.json({ ok: true }); // ya lo había reportado
  await q("INSERT INTO web_reports (reporter, kind, target, reason, note) VALUES (?, ?, ?, ?, ?)", [u.id, kind, target, reason, note]);
  res.json({ ok: true });
});
const staff = async (req, res) => { const u = await me(req); if (!u || !(Number(u.admin_level) >= MIN_POST)) { res.status(403).json({ error: "Solo staff" }); return null; } return u; };
app.get("/api/reports", async (req, res) => {
  if (!(await staff(req, res))) return;
  const rows = await q("SELECT r.id, r.kind, r.target, r.reason, r.note, r.created_at, p.name AS reporter FROM web_reports r LEFT JOIN player p ON p.id = r.reporter WHERE r.status = 0 ORDER BY r.id DESC LIMIT 100");
  for (const r of rows) { // a qué apunta cada reporte: texto corto y enlace
    try {
      if (r.kind === "post") { const x = (await q("SELECT author, body, title FROM web_posts WHERE id = ?", [r.target]))[0]; r.info = x ? `${x.author}: ${(x.title || x.body || "").slice(0, 120)}` : "(eliminada)"; r.href = x ? `/p/${r.target}` : ""; }
      else if (r.kind === "comment") { const x = (await q("SELECT author, body, post_id FROM web_comments WHERE id = ?", [r.target]))[0]; r.info = x ? `${x.author}: ${x.body.slice(0, 120)}` : "(eliminado)"; r.href = x ? `/p/${x.post_id}` : ""; }
      else if (r.kind === "user") { const x = (await q("SELECT name FROM player WHERE id = ?", [r.target]))[0]; r.info = x ? x.name : "(no existe)"; r.href = x ? `/u/${x.name}` : ""; }
      else if (r.kind === "market") { const x = (await q("SELECT title FROM web_market WHERE id = ?", [r.target]))[0]; r.info = x ? x.title : "(eliminado)"; r.href = x ? "/marketplace" : ""; }
      else if (r.kind === "msg") { const x = (await q("SELECT m.body, p.name FROM web_msgs m JOIN player p ON p.id = m.a WHERE m.id = ?", [r.target]))[0]; r.info = x ? `${x.name}: ${x.body.slice(0, 120)}` : "(eliminado)"; r.href = x ? `/u/${x.name}` : ""; }
    } catch { r.info = ""; r.href = ""; }
  }
  res.json(rows);
});
app.post("/api/reports/:id(\\d+)/close", async (req, res) => {
  const st = await staff(req, res); if (!st) return;
  const rp = (await q("SELECT kind, target, reason FROM web_reports WHERE id = ?", [pid(req.params.id)]))[0];
  await q("UPDATE web_reports SET status = 1 WHERE id = ?", [pid(req.params.id)]);
  if (rp && mod) await mod.log(st, "resolver reporte", `${rp.kind} ${rp.target}`, rp.reason);
  res.json({ ok: true });
});
return { getSettings, blockedIds, isBlocked, muted, loadLogout };
};
