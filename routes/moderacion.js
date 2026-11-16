// Moderación: suspensiones, filtro de palabras, registro de acciones del staff, insignia de verificado y estadísticas.
const { badWord } = require("../lib/util");
module.exports = (app, { str, pid, MIN_POST, SKIN_COL, q, sessionId, me }) => {
const NAME = /^\w{1,24}$/;
let BANS = new Map(), WORDS = [], VERIFIED = []; // en memoria; se recargan cada minuto y al cambiarlas
async function load() {
  try {
    BANS = new Map((await q("SELECT player_id, until, reason FROM web_bans WHERE until > ?", [Date.now()])).map((r) => [Number(r.player_id), { until: Number(r.until), reason: r.reason }]));
    WORDS = (await q("SELECT w FROM web_badwords ORDER BY w LIMIT 2000")).map((r) => r.w);
    VERIFIED = (await q("SELECT name FROM player WHERE admin_level > 0 UNION SELECT p.name FROM web_verified v JOIN player p ON p.id = v.player_id LIMIT 500")).map((r) => r.name);
  } catch (e) { console.log("[mod]", e.message); }
}
async function init() {
  await q("CREATE TABLE IF NOT EXISTS web_bans (player_id INT NOT NULL PRIMARY KEY, until BIGINT NOT NULL, reason VARCHAR(200) NOT NULL DEFAULT '', by_id INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_badwords (w VARCHAR(40) NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_modlog (id INT AUTO_INCREMENT PRIMARY KEY, staff_id INT NOT NULL, staff VARCHAR(24) NOT NULL, action VARCHAR(24) NOT NULL, target VARCHAR(60) NOT NULL DEFAULT '', detail VARCHAR(300) NOT NULL DEFAULT '', created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY c (created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_verified (player_id INT NOT NULL PRIMARY KEY, by_id INT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await load(); setInterval(load, 60000).unref();
}
const log = async (staff, action, target = "", detail = "") => { try { await q("INSERT INTO web_modlog (staff_id, staff, action, target, detail) VALUES (?, ?, ?, ?, ?)", [staff.id, staff.name, action, String(target).slice(0, 60), String(detail).replace(/\s+/g, " ").trim().slice(0, 300)]); } catch (e) { console.log("[mod] registro:", e.message); } };
const banOf = (uid) => { const b = BANS.get(Number(uid)); return b && b.until > Date.now() ? b : null; };

// ---- Filtro general: un suspendido no puede publicar, comentar ni escribir; los textos no pueden llevar palabras vetadas ----
const FREE = /^\/api\/(logout|login|register|recover|settings|notifications|push|blocks|admin|reports|tebex|store|discord)(\/|$)/; // lo que sí puede hacer un suspendido (y lo que no pasa por el filtro de palabras)
const FIELDS = ["body", "title", "about", "bio", "note", "place", "city", "job", "message"];
app.use("/api", (req, res, next) => {
  if (req.method === "GET" || req.method === "HEAD" || FREE.test(req.originalUrl.split("?")[0])) return next();
  const uid = sessionId(req), b = uid ? banOf(uid) : null;
  if (b) return res.status(403).json({ error: `Tu cuenta está suspendida en la red social hasta el ${new Date(b.until).toLocaleString("es-CO", { dateStyle: "medium", timeStyle: "short" })}${b.reason ? ". Motivo: " + b.reason : ""}`, banned: true });
  if (WORDS.length && req.body && typeof req.body === "object" && !Buffer.isBuffer(req.body)) {
    const texts = FIELDS.map((f) => req.body[f]).filter((v) => typeof v === "string");
    if (/^\/api\/(groups|rooms)(\/|$)/.test(req.originalUrl) && typeof req.body.name === "string") texts.push(req.body.name); // nombres de grupos y chats (no los de jugadores)
    if (Array.isArray(req.body.poll)) texts.push(...req.body.poll.filter((v) => typeof v === "string"));
    const w = texts.length ? badWord(texts.join(" \n "), WORDS) : "";
    if (w) return res.status(400).json({ error: "Tu texto contiene una palabra no permitida. Cámbiala e inténtalo de nuevo." });
  }
  next();
});

// ---- Panel del staff ----
const staff = async (req, res) => { const u = await me(req); if (!u || !(Number(u.admin_level) >= MIN_POST)) { res.status(403).json({ error: "Solo staff" }); return null; } return u; };
const byName = async (n) => (NAME.test(n) ? (await q("SELECT id, name, admin_level FROM player WHERE name = ?", [n]))[0] : null);
app.get("/api/admin/bans", async (req, res) => {
  if (!(await staff(req, res))) return;
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin, b.until, b.reason, s.name AS staff FROM web_bans b JOIN player p ON p.id = b.player_id LEFT JOIN player s ON s.id = b.by_id WHERE b.until > ? ORDER BY b.created_at DESC LIMIT 200`, [Date.now()]));
});
app.post("/api/admin/bans", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const p = await byName(str(req.body.player)), hours = Math.min(24 * 365, Math.max(1, Math.floor(Number(req.body.hours)) || 24)), reason = str(req.body.reason).replace(/\s+/g, " ").trim().slice(0, 200);
  if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  if (Number(p.admin_level) >= MIN_POST) return res.status(403).json({ error: "No se puede suspender al staff" });
  const until = Date.now() + hours * 3600000;
  await q("INSERT INTO web_bans (player_id, until, reason, by_id) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE until = VALUES(until), reason = VALUES(reason), by_id = VALUES(by_id), created_at = NOW()", [p.id, until, reason, u.id]);
  BANS.set(Number(p.id), { until, reason }); await log(u, "suspender", p.name, `${hours} h. ${reason}`);
  res.json({ ok: true });
});
app.delete("/api/admin/bans/:name", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const p = await byName(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  await q("DELETE FROM web_bans WHERE player_id = ?", [p.id]); BANS.delete(Number(p.id)); await log(u, "quitar suspensión", p.name);
  res.json({ ok: true });
});
app.get("/api/admin/words", async (req, res) => { if (!(await staff(req, res))) return; res.json(WORDS); });
app.post("/api/admin/words", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const ws = [...new Set(str(req.body.words).toLowerCase().split(/[,\n]+/).map((w) => w.replace(/\s+/g, " ").trim()).filter((w) => w.length >= 2 && w.length <= 40))].slice(0, 100);
  if (!ws.length) return res.status(400).json({ error: "Escribe al menos una palabra (mínimo 2 letras)" });
  for (const w of ws) await q("INSERT IGNORE INTO web_badwords (w) VALUES (?)", [w]);
  await load(); await log(u, "vetar palabra", "", ws.join(", "));
  res.json({ ok: true, words: WORDS });
});
app.delete("/api/admin/words", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const w = str(req.body.word).slice(0, 40);
  await q("DELETE FROM web_badwords WHERE w = ?", [w]); await load(); await log(u, "permitir palabra", "", w);
  res.json({ ok: true, words: WORDS });
});
app.post("/api/admin/verified/:name", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const p = await byName(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  await q("INSERT IGNORE INTO web_verified (player_id, by_id) VALUES (?, ?)", [p.id, u.id]); await load(); await log(u, "verificar", p.name);
  res.json({ ok: true });
});
app.delete("/api/admin/verified/:name", async (req, res) => {
  const u = await staff(req, res); if (!u) return;
  const p = await byName(str(req.params.name)); if (!p) return res.status(404).json({ error: "Ese jugador no existe" });
  await q("DELETE FROM web_verified WHERE player_id = ?", [p.id]); await load(); await log(u, "quitar verificado", p.name);
  res.json({ ok: true });
});
app.get("/api/admin/verified", async (req, res) => {
  if (!(await staff(req, res))) return;
  res.json(await q(`SELECT p.name, p.${SKIN_COL} AS skin FROM web_verified v JOIN player p ON p.id = v.player_id ORDER BY v.created_at DESC LIMIT 300`));
});
app.get("/api/admin/log", async (req, res) => {
  if (!(await staff(req, res))) return;
  const before = pid(req.query.before);
  res.json(await q(`SELECT id, staff, action, target, detail, created_at FROM web_modlog ${before ? "WHERE id < ?" : ""} ORDER BY id DESC LIMIT 60`, before ? [before] : []));
});
// Estadísticas: totales y actividad de los últimos 14 días
let statsCache = { at: 0, d: null };
app.get("/api/admin/stats", async (req, res) => {
  if (!(await staff(req, res))) return;
  if (statsCache.d && Date.now() - statsCache.at < 60000) return res.json(statsCache.d);
  const one = async (sql, p) => Number(((await q(sql, p).catch(() => [{}]))[0] || {}).n) || 0;
  const days = async (sql) => (await q(sql).catch(() => [])).map((r) => ({ d: r.d, n: Number(r.n) }));
  const perDay = (tb, col = "created_at", where = "") => days(`SELECT DATE_FORMAT(${col}, '%Y-%m-%d') AS d, COUNT(*) AS n FROM ${tb} WHERE ${col} > NOW() - INTERVAL 14 DAY ${where} GROUP BY d ORDER BY d`);
  const d = {
    totals: {
      jugadores: await one("SELECT COUNT(*) AS n FROM player"), conectados: await one("SELECT COALESCE(SUM(connected), 0) AS n FROM player"),
      publicaciones: await one("SELECT COUNT(*) AS n FROM web_posts WHERE type = 'post'"), reels: await one("SELECT COUNT(*) AS n FROM web_posts WHERE type = 'reel'"),
      comentarios: await one("SELECT COUNT(*) AS n FROM web_comments"), mensajes: await one("SELECT COUNT(*) AS n FROM web_msgs"),
      grupos: await one("SELECT COUNT(*) AS n FROM web_groups"), anuncios: await one("SELECT COUNT(*) AS n FROM web_market WHERE status = 1"),
      reportes: await one("SELECT COUNT(*) AS n FROM web_reports WHERE status = 0"), suspendidos: BANS.size,
      activos7: await one("SELECT COUNT(DISTINCT player_id) AS n FROM web_sessions WHERE last_seen > NOW() - INTERVAL 7 DAY"),
      medios_mb: Math.round((await one("SELECT COALESCE(SUM(size), 0) AS n FROM web_media")) / 1048576),
    },
    series: { registros: await perDay("player", "reg_date"), publicaciones: await perDay("web_posts", "created_at", "AND type IN ('post', 'reel', 'story')"), comentarios: await perDay("web_comments"), mensajes: await perDay("web_msgs") },
    top: await q("SELECT author AS name, COUNT(*) AS n FROM web_posts WHERE type IN ('post', 'reel') AND created_at > NOW() - INTERVAL 30 DAY GROUP BY author ORDER BY n DESC LIMIT 8").catch(() => []),
  };
  statsCache = { at: Date.now(), d };
  res.json(d);
});
return { init, log, banOf, verified: () => VERIFIED, reload: load };
};
