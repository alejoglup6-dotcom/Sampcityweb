require("dotenv").config();
const crypto = require("crypto");
const path = require("path");
const express = require("express");
const compression = require("compression");
const mysql = require("mysql2/promise");
const bcrypt = require("bcryptjs");
const store = require("./store");
const { packSession, parseSession, totpOk } = require("./lib/util");

const E = process.env;
const BASE = (E.BASE_URL || "http://localhost:3000").replace(/\/$/, "");
const SECRET = E.SESSION_SECRET || "";
if (SECRET.length < 24 || /^cambia-esto/i.test(SECRET)) throw new Error("Pon un SESSION_SECRET largo y aleatorio en el .env (no uses el de ejemplo)");
const HTTPS = BASE.startsWith("https"), ORIGIN = new URL(BASE).origin;
const str = (v) => (typeof v === "string" ? v : ""); // solo texto: objetos/arreglos en el JSON se ignoran
const pid = (v) => { const n = Number(v); return Number.isSafeInteger(n) && n > 0 ? n : 0; }; // ids válidos (enteros positivos)
const MIN_POST = Number(E.POST_MIN_LEVEL) || 5; // Administrador o más (escala 0-9)
const SKIN_COL = /^\w{1,40}$/.test(E.SKIN_COLUMN || "") ? E.SKIN_COLUMN : "skin"; // columna de `player` con el id de skin
const SKIN_URL = E.SKIN_URL || "https://assets.open.mp/assets/images/skins/{id}.png"; // {id} se reemplaza por la skin
const CH = 256 * 1024, IMG_MAX = 1200 * 1024, VID_MAX = (Number(E.VIDEO_MAX_MB) || 25) * 1048576, VID_SECS = 15.5; // medios en trozos de 256 KB (evita el límite de paquete de MySQL)
const REEL_MAX = (Number(E.REEL_MAX_MB) || 60) * 1048576, REEL_SECS = Number(E.REEL_MAX_SECS) || 90; // reels: videos más largos (kind = "rel")
const ADMIN_LEVELS = ["Ciudadano", "Soporte", "Ayudante", "Moderador", "Moderador Global", "Administrador", "Encargado de Staff", "Desarrollador", "Co-Fundador", "Fundador"]; // player.admin_level 0-9 (escala del 04-oct-2026)

const pool = mysql.createPool({ host: E.MYSQL_HOST, port: Number(E.MYSQL_PORT) || 3306, user: E.MYSQL_USER, password: E.MYSQL_PASSWORD, database: E.MYSQL_DATABASE, charset: "utf8mb4", connectionLimit: Math.max(2, Number(E.MYSQL_POOL) || 10), ssl: E.MYSQL_SSL === "1" ? { minVersion: "TLSv1.2", rejectUnauthorized: E.MYSQL_SSL_STRICT !== "0" } : undefined, dateStrings: true, supportBigNumbers: true, bigNumberStrings: true });
const q = async (sql, p) => (await pool.query(sql, p))[0];

// ---- Sesión: cookie firmada (HMAC), HttpOnly, 7 días. Cada sesión lleva su id (sid): se listan y se cierran una a una ----
const LOGOUT = new Map(); // jugador → momento del último «cerrar sesión en todos los dispositivos»
const REVOKED = new Set(); // sesiones cerradas a distancia (se recarga de web_sessions cada minuto)
function setSession(res, id, extra = "", req = null) {
  const sid = crypto.randomBytes(8).toString("hex"), exp = Date.now() + 7 * 864e5;
  res.append("Set-Cookie", `sc=${packSession(SECRET, id, exp, sid)}; Path=/; HttpOnly; SameSite=Lax; Max-Age=604800${HTTPS ? "; Secure" : ""}${extra}`);
  q("INSERT INTO web_sessions (sid, player_id, ua, ip, exp) VALUES (?, ?, ?, ?, ?)", [sid, id, String((req && req.headers["user-agent"]) || "").slice(0, 200), String((req && req.ip) || "").replace(/^::ffff:/, "").slice(0, 45), exp]).catch((e) => console.log("[sesión]", e.message));
  return sid;
}
function sessionOf(req) { // { id, exp, sid } de la cookie, o null
  if (req._ses !== undefined) return req._ses;
  const m = (req.headers.cookie || "").match(/(?:^|; )sc=([^;]+)/), s = m ? parseSession(SECRET, m[1]) : null;
  return (req._ses = s && s.exp - 7 * 864e5 >= (LOGOUT.get(s.id) || 0) && !(s.sid && REVOKED.has(s.sid)) ? s : null); // la cookie debe ser posterior al último cierre global
}
const sessionId = (req) => { const s = sessionOf(req); return s ? s.id : null; };
const clearSession = (res) => res.append("Set-Cookie", `sc=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0${HTTPS ? "; Secure" : ""}`);

// ---- Contraseñas: igual que snrp.pwn (bcrypt $2a/$2b/$2y, o SHA256(clave + sal) en mayúsculas) ----
async function checkPassword(input, salt, stored) {
  if (/^\$2[aby]\$/.test(stored)) return bcrypt.compare(input, stored); // async: no bloquea el servidor mientras calcula
  const h = crypto.createHash("sha256").update(input + (salt || "")).digest("hex").toUpperCase();
  return h.length === stored.length && crypto.timingSafeEqual(Buffer.from(h), Buffer.from(stored.toUpperCase()));
}

// ---- Límite de intentos de login (guardado en la base de datos, sobrevive a reinicios) ----
async function limited(key, max) {
  await q("DELETE FROM web_tries WHERE t < ?", [Date.now() - 15 * 60000]);
  return Number((await q("SELECT COUNT(*) AS n FROM web_tries WHERE k = ?", [key]))[0].n) >= max;
}
// ---- Límite de peticiones por IP (en memoria) ----
const buckets = new Map();
const hit = (key, max, ms) => { const now = Date.now(); let b = buckets.get(key); if (!b || b.r < now) { b = { n: 0, r: now + ms }; buckets.set(key, b); } return ++b.n <= max; };
setInterval(() => { const now = Date.now(); for (const [k, b] of buckets) if (b.r < now) buckets.delete(k); }, 60000).unref();
// ---- Protección anti-bots: Cloudflare Turnstile (opcional, se activa con TURNSTILE_SITEKEY y TURNSTILE_SECRET) ----
async function captchaOk(req) {
  if (!E.TURNSTILE_SECRET) return true;
  const t = str(req.body.cf).slice(0, 2048);
  if (!t) return false;
  try {
    const r = await (await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", { method: "POST", body: new URLSearchParams({ secret: E.TURNSTILE_SECRET, response: t, remoteip: req.ip }), signal: AbortSignal.timeout(5000) })).json();
    return !!r.success;
  } catch { return false; }
}
const DUMMY_HASH = bcrypt.hashSync("sampcity-dummy", 10); // para que el tiempo de respuesta no delate si el usuario existe

const app = express();
// Express 4 no captura errores de funciones async: así un fallo de la base de datos no tumba el servidor
["get", "post", "put", "delete"].forEach((m) => { const o = app[m].bind(app); app[m] = (p, ...h) => (h.length ? o(p, ...h.map((f) => (typeof f === "function" && f.length < 4 ? (a, b, c) => Promise.resolve(f(a, b, c)).catch(c) : f))) : o(p)); });
app.set("trust proxy", 1);
app.use(compression({ filter: (req, res) => req.path !== "/api/stream" && compression.filter(req, res) })); // gzip/deflate para HTML, CSS, JS y JSON (el video y las imágenes ya vienen comprimidos y se saltan)
app.disable("x-powered-by");
// 18-19. HTTPS forzado + cabeceras de seguridad
// script-src-elem (se completa al cargar las páginas): solo corren los <script> propios y los en línea conocidos (por su hash);
// un <script> inyectado no se ejecuta. Los navegadores viejos usan script-src.
let CSP = "";
const cspBuild = (hashes = []) => ["default-src 'self'", "script-src 'self' 'unsafe-inline' https://challenges.cloudflare.com", `script-src-elem 'self' https://challenges.cloudflare.com ${hashes.map((h) => `'sha256-${h}'`).join(" ")}`.trim(), "script-src-attr 'unsafe-inline'", "worker-src 'self'", "manifest-src 'self'", "style-src 'self' 'unsafe-inline'", "font-src 'self'", "img-src 'self' data: blob: https:", "media-src 'self' blob:", "connect-src 'self'", "frame-src https://challenges.cloudflare.com", "object-src 'none'", "base-uri 'self'", "form-action 'self'", "frame-ancestors 'none'"].join("; ");
CSP = cspBuild();
app.use((req, res, next) => {
  if (HTTPS && req.headers["x-forwarded-proto"] === "http") return res.redirect(301, BASE + req.originalUrl);
  res.set({ "X-Content-Type-Options": "nosniff", "X-Frame-Options": "DENY", "Referrer-Policy": "same-origin", "Content-Security-Policy": CSP, "Permissions-Policy": "camera=(), microphone=(self), geolocation=(), payment=(), usb=()", "Cross-Origin-Opener-Policy": "same-origin" });
  if (HTTPS) res.set("Strict-Transport-Security", "max-age=31536000");
  next();
});
// ---- Webhook de Tebex: se recibe el cuerpo crudo antes de los middlewares globales ----
app.post("/api/tebex/webhook",
  express.raw({ type: () => true, limit: "256kb" }),
  (req, res, next) => {
    req.rawBody = req.body;
    req.tebexWebhook = true;
    next();
  }
);

// 17. Límite de la API por IP + peticiones que cambian datos: mismo origen y JSON (así otra web no puede lanzarlas)
app.use("/api", (req, res, next) => {
  if (req.tebexWebhook) return next();
  res.set("Cache-Control", "no-store");
  if (!hit("a|" + req.ip, 300, 60000) || (req.method !== "GET" && !hit("w|" + req.ip, 60, 60000))) return res.set("Retry-After", "60").status(429).json({ error: "Demasiadas peticiones. Espera un momento." });
  if (req.method !== "GET") {
    const o = req.headers.origin;
    if (o && o !== ORIGIN && o !== `${req.protocol}://${req.get("host")}`) return res.status(403).json({ error: "Origen no permitido" });
    if (req.path !== "/media" && !/^application\/json/i.test(req.headers["content-type"] || "")) return res.status(415).json({ error: "Formato no válido" });
  }
  next();
});

const BIG_BODY = (p) => p === "/api/photos" || p === "/api/market" || p === "/api/profile/photo" || /^\/api\/chat\/\w{1,24}$/.test(p) || /^\/api\/rooms\/\d+$/.test(p); // rutas que aceptan una foto o un audio
const jsonSmall = express.json({ limit: "20kb" }), jsonBig = express.json({ limit: "1500kb" });
app.use((req, res, next) => (req.tebexWebhook || req.path === "/api/media" ? next() : (BIG_BODY(req.path) ? jsonBig : jsonSmall)(req, res, next)));
const me = async (req) => {
  const id = sessionId(req);
  if (!id) return null;
  const r = await q("SELECT id, name, admin_level FROM player WHERE id = ?", [id]);
  return r[0] || null;
};
const vivo = require("./routes/vivo")(app, { E, BASE, str, q, sessionId, hit }); // tiempo real y push
const mod = require("./routes/moderacion")(app, { str, pid, MIN_POST, SKIN_COL, q, sessionId, me }); // suspensiones y filtro de palabras (va antes de las demás rutas)

// ---- Notificaciones: me gusta, comentarios, menciones, etiquetas, noticias, amistades (por agregar), etc. ----
// notify(destino, actor, tipo, id_publicación, texto): cualquier parte del servidor puede crear una notificación.
// Para solicitudes de amistad usa los tipos "friend_req" (te enviaron una) y "friend_acc" (aceptaron la tuya).
const BCAST = Number(E.NOTIF_BROADCAST_MAX) || 3000; // a cuántos jugadores como máximo se avisa de una noticia/actualización nueva
const ONCE = new Set(["like", "tag", "mention", "friend_req", "follow", "like_c"]); // no se repite si ya existe (mismo actor y publicación)
const UNREAD_ONCE = new Set(["reply", "contact", "vac_app"]); // no se repite mientras haya una sin leer igual
// Solo notificaciones cuya publicación sigue existiendo (y cuyas historias no han caducado)
const NJ = "FROM web_notifs n LEFT JOIN web_posts p ON p.id = n.post_id LEFT JOIN player a ON a.id = n.actor_id WHERE n.player_id = ? AND (n.post_id IS NULL OR p.id IS NOT NULL) AND (p.type IS NULL OR p.type <> 'story' OR p.created_at > NOW() - INTERVAL 24 HOUR)";
async function notify(to, actor, type, postId = null, body = "") {
  try {
    if (!to || String(to) === String(actor.id)) return; // nadie se notifica a sí mismo
    if (await sec.muted(to, type, actor.id)) return; // bloqueo entre ambos, o tipo de aviso silenciado en Configuración
    if (ONCE.has(type) && (await q("SELECT 1 FROM web_notifs WHERE player_id = ? AND actor_id = ? AND type = ? AND post_id <=> ? LIMIT 1", [to, actor.id, type, postId])).length) return;
    if (UNREAD_ONCE.has(type) && (await q("SELECT 1 FROM web_notifs WHERE player_id = ? AND type = ? AND post_id <=> ? AND seen = 0 LIMIT 1", [to, type, postId])).length) return;
    await q("INSERT INTO web_notifs (player_id, actor_id, type, post_id, body) VALUES (?, ?, ?, ?, ?)", [to, actor.id, type, postId, String(body).replace(/\s+/g, " ").trim().slice(0, 140)]);
    vivo.send(to, "notif", { type }); // la campana se actualiza al instante
    vivo.push(to, vivo.notifText(type, actor.name, postId, body));
  } catch (e) { console.log("[notif] error al crear el aviso:", e.message); }
}
const unreadCount = async (uid) => Number((await q(`SELECT COUNT(*) AS n ${NJ} AND n.seen = 0`, [uid]))[0].n);
async function mentioned(text, selfId) { // jugadores citados con @Nombre_Apellido que existen
  const names = [...new Set([...String(text).matchAll(/(?:^|[^\w@])@(\w{1,24})/g)].map((m) => m[1]))].slice(0, 10);
  if (!names.length) return [];
  return (await q("SELECT id, name FROM player WHERE name IN (?)", [names])).filter((r) => String(r.id) !== String(selfId));
}
const postOwner = async (id) => { // dueño de una publicación (sin JOIN entre tablas: evita problemas de collation)
  const p = (await q("SELECT type, author FROM web_posts WHERE id = ?", [id]))[0];
  if (!p) return null;
  const a = (await q("SELECT id FROM player WHERE name = ? LIMIT 1", [p.author]))[0];
  if (!a) console.log("[notif] no encontré al jugador autor de la publicación", id, JSON.stringify(p.author));
  return { type: p.type, aid: a ? a.id : null };
};

// ---- Login ----
// ---- Registro desde la web (04-oct-2026) ----
// Crea la cuenta en la tabla player como el juego (mismas reglas de nombre, contraseña y correo) y la apunta en
// web_signups: al entrar al servidor por primera vez, tras poner la contraseña, el juego abre el creador de personaje
// y completa la cuenta (gamemodes/src/web_signup.pwn del repo Backup). Ya se puede iniciar sesión y verificar Discord.
const RP_NAME = /^[A-Z][A-Za-z]*_[A-Z][A-Za-z]*$/;
const EMAIL = /^[A-Za-z0-9_.]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;
app.post("/api/register", async (req, res) => {
  const name = str(req.body.name).trim(), pass = str(req.body.password).replace(/%/g, "#"), email = str(req.body.email).trim();
  if (!hit("rv|" + req.ip, 30, 600000)) return res.status(429).json({ error: "Demasiados intentos. Espera unos minutos." });
  if (name.length < 3 || name.length > 24 || !RP_NAME.test(name)) return res.status(400).json({ error: "El nombre tiene que ser Nombre_Apellido: dos palabras con mayúscula inicial, solo letras, unidas por un guion bajo." });
  if (pass.length < 6 || pass.length > 18) return res.status(400).json({ error: "La contraseña tiene que tener de 6 a 18 caracteres." });
  if (email.length > 31 || !EMAIL.test(email)) return res.status(400).json({ error: "Escribe un correo válido (máximo 31 caracteres)." });
  if (!(await captchaOk(req))) return res.status(400).json({ error: "Completa la verificación anti-robots", captcha: true });
  if ((await q("SELECT id FROM player WHERE name = ?", [name]))[0]) return res.status(409).json({ error: "Ese nombre ya está en uso. Si es tuyo, inicia sesión." });
  if ((await q("SELECT id FROM player WHERE email = ?", [email]))[0]) return res.status(409).json({ error: "Ese correo ya está en uso." });
  if (!hit("r|" + req.ip, 5, 3600000)) return res.status(429).json({ error: "Demasiadas cuentas nuevas desde tu conexión. Prueba más tarde." }); // solo cuentan las que se crean
  // contraseña como SHA256_PassHash del juego (al entrar al servidor se pasa sola a bcrypt)
  const salt = crypto.randomBytes(12).toString("base64").replace(/[^A-Za-z0-9]/g, "").slice(0, 15).padEnd(15, "x");
  const hash = crypto.createHash("sha256").update(pass + salt).digest("hex").toUpperCase();
  const now = new Date(), d = (n) => String(n).padStart(2, "0");
  const date = `${now.getFullYear()}-${d(now.getMonth() + 1)}-${d(now.getDate())} ${d(now.getHours())}:${d(now.getMinutes())}:${d(now.getSeconds())}`;
  let id;
  try {
    // valores de una cuenta nueva (SetPiDefaultValues de snrp.pwn); el juego los vuelve a poner al crear el personaje
    const r = await q(
      `INSERT INTO player (name, ip, email, salt, pass, reg_date, last_connection, last_connection_timestamp, level, rep, connected, playerid, time_for_rep, skin, cash,
        pos_x, pos_y, pos_z, angle, state, fight_style, health, hungry, thirst, config_sounds, config_audio, config_time, config_hud, config_admin, config_secure_login, phone_visible_number, doubt_channel)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0, 0, 3600000, 170, 0, 396.7158, -1529.8263, 32.2734, 216.87, 0, 4, 100, 100, 100, 1, 1, 1, 1, 1, 0, 1, 1)`,
      [name, String(req.ip || "").replace(/^::ffff:/, "").slice(0, 15), email, salt, hash, date, date, Math.floor(now / 1000)],
    );
    id = r.insertId;
    await q("INSERT INTO web_signups (player_id) VALUES (?)", [id]);
  } catch (e) {
    if (e.code === "ER_DUP_ENTRY") return res.status(409).json({ error: "Ese nombre o correo ya está en uso." });
    console.log("[registro]", e.message);
    return res.status(500).json({ error: "No se pudo crear la cuenta. Inténtalo de nuevo." });
  }
  setSession(res, id, "", req);
  res.json({ ok: true });
});

app.post("/api/login", async (req, res) => {
  const name = str(req.body.name).trim().slice(0, 24), pass = str(req.body.password).slice(0, 72).replace(/%/g, "#"); // el juego cambia % por # en todo lo que se escribe
  const key = `${req.ip}|${name.toLowerCase()}`, ipKey = `${req.ip}|*`;
  if (!name || !pass || /[\u0000-\u001f]/.test(name)) return res.status(400).json({ error: "Escribe tu nombre y tu contraseña" });
  if (!hit("l|" + req.ip, 20, 60000) || (await limited(key, 6)) || (await limited(ipKey, 30))) return res.status(429).json({ error: "Demasiados intentos. Espera 15 minutos." });
  if (!(await captchaOk(req))) return res.status(400).json({ error: "Completa la verificación anti-robots", captcha: true });
  const r = (await q("SELECT id, salt, pass FROM player WHERE name = ?", [name]))[0];
  const good = r && r.pass ? await checkPassword(pass, r.salt, r.pass) : (await bcrypt.compare(pass, DUMMY_HASH), false);
  if (!good) {
    await q("INSERT INTO web_tries (k, t) VALUES (?, ?), (?, ?)", [key, Date.now(), ipKey, Date.now()]);
    return res.status(401).json({ error: "Nombre o contraseña incorrectos" });
  }
  // verificación en dos pasos: si está activa, además de la contraseña hace falta el código de la app
  const tf = (await q("SELECT totp FROM web_settings WHERE player_id = ? AND totp_on = 1", [r.id]).catch(() => []))[0];
  if (tf && tf.totp) {
    const code = str(req.body.code).trim();
    if (!code) return res.status(401).json({ error: "Escribe el código de 6 dígitos de tu app de verificación", totp: true });
    if (!totpOk(tf.totp, code)) { await q("INSERT INTO web_tries (k, t) VALUES (?, ?), (?, ?)", [key, Date.now(), ipKey, Date.now()]); return res.status(401).json({ error: "Código incorrecto", totp: true }); }
  }
  await q("DELETE FROM web_tries WHERE k = ?", [key]);
  setSession(res, r.id, "", req);
  res.json({ ok: true });
});
app.post("/api/logout", async (req, res) => { const s = sessionOf(req); if (s && s.sid) await cuenta.revoke(s.sid); clearSession(res); res.json({ ok: true }); });

// ---- Cuenta (solo datos propios) ----
app.get("/api/me", async (req, res) => {
  const u = await me(req);
  if (!u) return res.json({ user: null });
  const a = (await q(`SELECT p.name, p.${SKIN_COL} AS skin, p.reg_date, p.last_connection, p.time_playing, p.level, p.rep, p.connected, p.admin_level, p.vip, p.vip_expire_date, p.cash, p.bank_money, p.phone_number, p.wanted_level, p.arrests_count, p.kills_count, c.name AS crew FROM player p LEFT JOIN crews c ON c.id = p.crew WHERE p.id = ?`, [u.id]))[0];
  const d = (await q("SELECT discord_id, linked_at FROM discord_links WHERE player_id = ?", [u.id]))[0];
  const unread = await unreadCount(u.id).catch(() => 0), ses = sessionOf(req), ban = mod.banOf(u.id);
  if (ses && ses.sid) q("UPDATE web_sessions SET last_seen = NOW() WHERE sid = ?", [ses.sid]).catch(() => {});
  const fc = (await q("SELECT SUM(status = 1) AS f, SUM(status = 0 AND b = ?) AS r FROM web_friends WHERE a = ? OR b = ?", [u.id, u.id, u.id]).catch(() => [{}]))[0];
  res.json({ user: { ...a, rango: ADMIN_LEVELS[a.admin_level] || "Ciudadano", canPost: a.admin_level >= MIN_POST, discord: d || null, banned: ban ? ban.until : 0, unread, friends: Number(fc.f) || 0, freq: Number(fc.r) || 0 } });
});

require("./routes/discord")(app, { crypto, store, E, BASE, q, sessionId, me, notify });

const sec = require("./routes/seguridad")(app, { crypto, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, checkPassword, setSession, LOGOUT, mod });
const { friendIds, friendStates } = require("./routes/amigos")(app, { sec, str, pid, SKIN_COL, pool, q, sessionId, hit, me, NJ, notify, unreadCount, unreadMsgs: (id) => social.unreadMsgs(id) });

const { STORY_LIVE, FFMPEG, attach, cleanup, canSee, removePosts } = require("./routes/contenido")(app, { vivo, mod, infoExtra: () => ({ verified: mod.verified(), mail: cuenta.mailOn(), push: vivo.pushOn() }), sec, path, express, E, str, pid, MIN_POST, SKIN_COL, SKIN_URL, CH, IMG_MAX, VID_MAX, VID_SECS, REEL_MAX, REEL_SECS, ADMIN_LEVELS, q, sessionId, hit, captchaOk, me, BCAST, notify, mentioned, postOwner, friendIds, friendStates });

const cuenta = require("./routes/cuenta")(app, { crypto, E, BASE, str, q, sessionOf, hit, me, checkPassword, captchaOk, setSession, clearSession, LOGOUT, REVOKED, getRemovePosts: () => removePosts });
require("./routes/guia")(app, { path, str, MIN_POST, q, hit, me, ROOT: __dirname });

require("./routes/launcher")(app, { path, store, E, q, attach, ROOT: __dirname });

// ---- SEO: robots.txt, sitemap.xml e index con la URL real (canonical / Open Graph) ----
const fs = require("fs");
// Cada página es su propio archivo en public/ con su propia URL (ver README)
const ROUTES = { "/": "index", "/reels": "reels", "/marketplace": "marketplace", "/feed": "feed", "/amigos": "amigos", "/mensajes": "mensajes", "/configuracion": "configuracion", "/grupos": "grupos", "/eventos": "eventos", "/buscar": "buscar", "/noticias": "noticias", "/actualizaciones": "actualizaciones", "/faq": "faq", "/fotos": "fotos", "/staff": "staff", "/solicitar-staff": "solicitar-staff", "/clasificacion": "clasificacion", "/reglas": "reglas", "/testimonios": "testimonios", "/contacto": "contacto", "/comunidad": "comunidad", "/perfil": "perfil", "/notificaciones": "notificaciones", "/verificar": "verificar", "/guia": "guia", "/recuperar": "recuperar", "/panel": "panel" };
const PAGES = {}, VER = Date.now().toString(36); // la versión cambia en cada arranque: el navegador siempre baja el CSS/JS nuevo
const USER_PAGE = fs.readFileSync(path.join(__dirname, "public", "usuario.html"), "utf8").replace(/\?v=\d+/g, "?v=" + VER);
app.get("/u/:name", (req, res) => (/^\w{1,24}$/.test(req.params.name) ? res.type("html").set("Cache-Control", "no-cache").send(USER_PAGE) : res.status(404).sendFile(path.join(__dirname, "public", "404.html"))));
for (const [url, file] of Object.entries(ROUTES)) {
  PAGES[url] = fs.readFileSync(path.join(__dirname, "public", file + ".html"), "utf8").replace(/\{\{BASE\}\}/g, BASE).replace(/\?v=\d+/g, "?v=" + VER);
  app.get(url === "/" ? ["/", "/index.html"] : [url, url + ".html"], (req, res) => res.type("html").set("Cache-Control", "public, max-age=300").send(PAGES[url]));
}
{ // hashes de los <script> en línea de todas las páginas (tal como se sirven)
  const hs = new Set(), scan = (html) => { for (const m of html.matchAll(/<script(?![^>]*\bsrc=)[^>]*>([\s\S]*?)<\/script>/gi)) hs.add(crypto.createHash("sha256").update(m[1]).digest("base64")); };
  for (const f of fs.readdirSync(path.join(__dirname, "public")).filter((f) => f.endsWith(".html"))) { const h = fs.readFileSync(path.join(__dirname, "public", f), "utf8"); scan(h); scan(h.replace(/\{\{BASE\}\}/g, BASE).replace(/\?v=\d+/g, "?v=" + VER)); }
  CSP = cspBuild([...hs]);
}
const PUB_PAGE = fs.readFileSync(path.join(__dirname, "public", "publicacion.html"), "utf8").replace(/\?v=\d+/g, "?v=" + VER);
const oe = (v) => String(v).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
app.get("/p/:id(\\d+)", async (req, res) => { // enlace propio de cada publicación/historia, con vista previa al compartir
  const id = pid(req.params.id);
  let t = "Publicación", d = "Mira esta publicación en SampCity RolePlay.", img = BASE + "/assets/logo.png";
  try {
    const p = (await q("SELECT p.id, p.type, p.author, p.body FROM web_posts p WHERE p.id = ? " + STORY_LIVE, [id]))[0];
    if (p) {
      t = p.author.replace(/_/g, " ") + (p.type === "story" ? " · Historia" : p.type === "reel" ? " · Reel" : "");
      if (p.body && p.type !== "photo") d = p.body.replace(/\s+/g, " ").slice(0, 160);
      const m = (await q("SELECT m.id, m.kind, m.thumb FROM web_post_media pm JOIN web_media m ON m.id = pm.media_id WHERE pm.post_id = ? ORDER BY pm.pos LIMIT 1", [id]))[0];
      if (m) img = `${BASE}/media/${m.kind === "img" ? m.id : m.thumb}`; else if (p.type === "photo" && /^\/img\/\d+$/.test(p.body)) img = BASE + p.body;
    }
  } catch (e) { console.log("[p]", e.message); }
  res.type("html").set("Cache-Control", "no-cache").send(PUB_PAGE.replace(/\{\{OGTITLE\}\}/g, oe(t)).replace(/\{\{OGDESC\}\}/g, oe(d)).replace(/\{\{OGIMG\}\}/g, oe(img)).replace(/\{\{OGURL\}\}/g, oe(`${BASE}/p/${id}`)));
});
app.get("/robots.txt", (req, res) => res.type("text/plain").send(`User-agent: *\nAllow: /\nDisallow: /api/\nDisallow: /auth/\nDisallow: /notificaciones\nDisallow: /amigos\n\nSitemap: ${BASE}/sitemap.xml\n`));
app.get("/sitemap.xml", (req, res) => res.type("application/xml").send(`<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${Object.keys(ROUTES).filter((u) => u !== "/perfil" && u !== "/notificaciones" && u !== "/amigos" && u !== "/mensajes" && u !== "/configuracion" && u !== "/buscar" && u !== "/verificar" && u !== "/recuperar" && u !== "/panel").map((u) => `  <url><loc>${BASE}${u}</loc><changefreq>${u === "/" || u === "/noticias" ? "daily" : "weekly"}</changefreq><priority>${u === "/" ? "1.0" : "0.7"}</priority></url>`).join("\n")}\n</urlset>\n`));
app.use(express.static(path.join(__dirname, "public"), { maxAge: "1h", setHeaders: (res, f) => { if (/[\\/]assets[\\/]fonts[\\/]/.test(f)) res.set("Cache-Control", "public, max-age=31536000, immutable"); else if (/[\\/]assets[\\/]/.test(f)) res.set("Cache-Control", /[?&]v=\w+/.test(res.req.url) ? "public, max-age=31536000, immutable" : "public, max-age=86400"); else if (/[\\/]sw\.js$/.test(f)) res.set("Cache-Control", "no-cache"); } }));
const social = require("./routes/social")(app, { vivo, sec, friendIds, express, str, pid, SKIN_COL, SKIN_URL, q, sessionId, hit, me });
require("./routes/extra")(app, { canSee, sec, attach, friendIds, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, notify });
require("./routes/enlaces")(app, { crypto, str, q, sessionId, hit });
require("./routes/grupos")(app, { mod, canSee, sec, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me, notify });
require("./routes/market")(app, { express, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me });

require("./routes/vacantes")(app, { E, str, pid, MIN_POST, SKIN_COL, ADMIN_LEVELS, q, hit, me, BCAST, notify });

// ---- Tienda SampCity ----
store(app, { q, me, hit, str, pid, E, BASE });
// Página 404 propia (la API responde JSON)
app.use((req, res) => (req.path.startsWith("/api/") ? res.status(404).json({ error: "No existe" }) : res.status(404).sendFile(path.join(__dirname, "public", "404.html"))));
app.use((err, req, res, next) => {
  const st = err.status >= 400 && err.status < 500 ? err.status : 500; // JSON roto, cuerpo muy grande, etc. no son errores del servidor
  if (st === 500) console.log("[web]", err.message);
  res.status(st).json({ error: st === 500 ? "Error del servidor" : st === 413 ? "Contenido demasiado grande" : "Petición no válida" });
});

const addKey = async (tb, key, cols) => { try { if (!(await q(`SHOW INDEX FROM ${tb} WHERE Key_name = ?`, [key])).length) await q(`ALTER TABLE ${tb} ADD INDEX ${key} (${cols})`); } catch (e) { console.log("[índice]", tb, key, e.message); } };
const addColEarly = async () => { for (const [c, d] of [["totp", "VARCHAR(40) NOT NULL DEFAULT ''"], ["totp_on", "TINYINT NOT NULL DEFAULT 0"]]) if (!(await q(`SHOW COLUMNS FROM web_settings LIKE '${c}'`)).length) await q(`ALTER TABLE web_settings ADD COLUMN ${c} ${d}`); };
(async () => {
  // Tablas propias de la web (no se toca ninguna del juego)
  await q(`CREATE TABLE IF NOT EXISTS web_posts (id INT AUTO_INCREMENT PRIMARY KEY, type VARCHAR(8) NOT NULL, title VARCHAR(120) NOT NULL, body TEXT NOT NULL, author VARCHAR(24) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY t (type)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await q("CREATE TABLE IF NOT EXISTS web_signups (player_id INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_likes (post_id INT NOT NULL, player_id INT NOT NULL, PRIMARY KEY (post_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_comments (id INT AUTO_INCREMENT PRIMARY KEY, post_id INT NOT NULL, player_id INT NOT NULL, author VARCHAR(24) NOT NULL, body VARCHAR(300) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY p (post_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_settings (player_id INT NOT NULL PRIMARY KEY, msg TINYINT NOT NULL DEFAULT 0, prof TINYINT NOT NULL DEFAULT 0, tag TINYINT NOT NULL DEFAULT 0, nmask INT NOT NULL DEFAULT 0, logout_at BIGINT NOT NULL DEFAULT 0) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_blocks (a INT NOT NULL, b INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (a, b), KEY b (b)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_reports (id INT AUTO_INCREMENT PRIMARY KEY, reporter INT NOT NULL, kind VARCHAR(8) NOT NULL, target INT NOT NULL, reason VARCHAR(20) NOT NULL, note VARCHAR(300) NOT NULL DEFAULT '', status TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY st (status, id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await addColEarly();
  await cuenta.init(); await mod.init(); await vivo.init();
  await sec.loadLogout();
  const addCol = async (tb, col, ddl) => { if (!(await q(`SHOW COLUMNS FROM ${tb} LIKE '${col}'`)).length) await q(`ALTER TABLE ${tb} ADD COLUMN ${col} ${ddl}`); }; // columnas nuevas sobre tablas que ya existen
  await addCol("web_posts", "vis", "TINYINT NOT NULL DEFAULT 0"); await addCol("web_posts", "share_id", "INT NULL"); await addCol("web_posts", "edited_at", "DATETIME NULL");
  await addCol("web_posts", "group_id", "INT NULL");
  await q("CREATE TABLE IF NOT EXISTS web_groups (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(60) NOT NULL, about VARCHAR(300) NOT NULL DEFAULT '', priv TINYINT NOT NULL DEFAULT 0, owner INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY n (name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_group_members (group_id INT NOT NULL, player_id INT NOT NULL, role TINYINT NOT NULL DEFAULT 1, PRIMARY KEY (group_id, player_id), KEY p (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_events (id INT AUTO_INCREMENT PRIMARY KEY, title VARCHAR(80) NOT NULL, about VARCHAR(600) NOT NULL DEFAULT '', place VARCHAR(80) NOT NULL DEFAULT '', starts DATETIME NOT NULL, owner INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY s (starts)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_event_rsvp (event_id INT NOT NULL, player_id INT NOT NULL, st TINYINT NOT NULL DEFAULT 1, PRIMARY KEY (event_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_polls (post_id INT NOT NULL PRIMARY KEY, opts VARCHAR(600) NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_poll_votes (post_id INT NOT NULL, player_id INT NOT NULL, opt TINYINT NOT NULL, PRIMARY KEY (post_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_story_views (post_id INT NOT NULL, player_id INT NOT NULL, at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (post_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await addCol("web_comments", "parent", "INT NULL"); await addCol("web_comments", "edited_at", "DATETIME NULL");
  await q("CREATE TABLE IF NOT EXISTS web_clikes (comment_id INT NOT NULL, player_id INT NOT NULL, PRIMARY KEY (comment_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_hidden (player_id INT NOT NULL, post_id INT NOT NULL, PRIMARY KEY (player_id, post_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_follows (a INT NOT NULL, b INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (a, b), KEY b (b)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_profile (player_id INT NOT NULL PRIMARY KEY, avatar INT NULL, cover INT NULL, bio VARCHAR(160) NOT NULL DEFAULT '') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  for (const [c, d] of [["city", "VARCHAR(40) NOT NULL DEFAULT ''"], ["job", "VARCHAR(60) NOT NULL DEFAULT ''"], ["link", "VARCHAR(120) NOT NULL DEFAULT ''"], ["bday", "CHAR(5) NOT NULL DEFAULT ''"], ["pin", "INT NULL"]]) await addCol("web_profile", c, d);
  await q("CREATE TABLE IF NOT EXISTS web_msgs (id INT AUTO_INCREMENT PRIMARY KEY, a INT NOT NULL, b INT NOT NULL, body VARCHAR(1000) NOT NULL, seen TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY ab (a, b, id), KEY ba (b, a, id), KEY bs (b, seen)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  if (!(await q("SHOW COLUMNS FROM web_likes LIKE 'kind'")).length) await q("ALTER TABLE web_likes ADD COLUMN kind TINYINT NOT NULL DEFAULT 1"); // reacciones: 1 me gusta, 2 me encanta, 3 me divierte, 4 me asombra, 5 me entristece, 6 me enoja
  await social.loadAv();
  await addCol("web_msgs", "img", "INT NULL");
  await q("CREATE TABLE IF NOT EXISTS web_chat_clear (player_id INT NOT NULL, peer INT NOT NULL, upto INT NOT NULL DEFAULT 0, PRIMARY KEY (player_id, peer)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_market (id INT AUTO_INCREMENT PRIMARY KEY, seller INT NOT NULL, cat VARCHAR(12) NOT NULL, title VARCHAR(80) NOT NULL, price BIGINT NOT NULL DEFAULT 0, body VARCHAR(600) NOT NULL DEFAULT '', img INT NULL, status TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY st (status, id), KEY seller (seller)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_images (id INT AUTO_INCREMENT PRIMARY KEY, mime VARCHAR(16) NOT NULL, data MEDIUMBLOB NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_contact (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(80) NOT NULL, contact VARCHAR(80) NOT NULL, body VARCHAR(1000) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_guide_searches (q VARCHAR(60) NOT NULL PRIMARY KEY, results INT NOT NULL DEFAULT 0, n INT NOT NULL DEFAULT 1, last_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_tries (k VARCHAR(80) NOT NULL, t BIGINT NOT NULL, KEY k (k)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_media (id INT AUTO_INCREMENT PRIMARY KEY, owner INT NOT NULL, kind VARCHAR(3) NOT NULL, mime VARCHAR(20) NOT NULL, size INT NOT NULL, thumb INT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY o (owner), KEY th (thumb)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_media_chunks (media_id INT NOT NULL, n INT NOT NULL, data MEDIUMBLOB NOT NULL, PRIMARY KEY (media_id, n)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_post_media (post_id INT NOT NULL, media_id INT NOT NULL, pos TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (post_id, media_id), KEY m (media_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_tags (post_id INT NOT NULL, player_id INT NOT NULL, name VARCHAR(24) NOT NULL, PRIMARY KEY (post_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_notifs (id INT AUTO_INCREMENT PRIMARY KEY, player_id INT NOT NULL, actor_id INT NOT NULL DEFAULT 0, type VARCHAR(12) NOT NULL, post_id INT NULL, body VARCHAR(160) NOT NULL DEFAULT '', seen TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY pl (player_id, id), KEY pu (player_id, seen), KEY po (post_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_saved (post_id INT NOT NULL, player_id INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (post_id, player_id), KEY pl (player_id, created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  // Códigos de Discord que canjea el bot (src/handlers/functions/discordOauth.js del bot). Sin clave foránea a player;
  // el bot borra las filas de más de una hora. Si cambia, cambiarla igual en el bot.
  await q(`CREATE TABLE IF NOT EXISTS discord_oauth_queue (id INT NOT NULL AUTO_INCREMENT, token CHAR(32) NOT NULL, player_id INT NOT NULL, back VARCHAR(10) NOT NULL DEFAULT 'verificar', code VARCHAR(100) NULL, status VARCHAR(20) NOT NULL DEFAULT 'pendiente', motivo VARCHAR(16) NOT NULL DEFAULT '', cuenta VARCHAR(24) NOT NULL DEFAULT '', discord_id VARCHAR(20) NULL, detail VARCHAR(255) NOT NULL DEFAULT '', notified TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (id), UNIQUE KEY token (token), KEY pending (status, id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);
  await q("CREATE TABLE IF NOT EXISTS web_friends (a INT NOT NULL, b INT NOT NULL, status TINYINT NOT NULL DEFAULT 0, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (a, b), KEY b (b, status)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_vacancies (id INT AUTO_INCREMENT PRIMARY KEY, kind VARCHAR(8) NOT NULL, title VARCHAR(80) NOT NULL, faction VARCHAR(60) NOT NULL DEFAULT '', description TEXT NOT NULL, requirements TEXT NOT NULL, questions TEXT NOT NULL, slots INT NOT NULL DEFAULT 0, min_level INT NOT NULL DEFAULT 0, need_discord TINYINT NOT NULL DEFAULT 0, open TINYINT NOT NULL DEFAULT 1, created_by VARCHAR(24) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, KEY o (open, id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_applications (id INT AUTO_INCREMENT PRIMARY KEY, vacancy_id INT NOT NULL, player_id INT NOT NULL, name VARCHAR(24) NOT NULL, answers TEXT NOT NULL, about VARCHAR(800) NOT NULL DEFAULT '', status TINYINT NOT NULL DEFAULT 0, note VARCHAR(300) NOT NULL DEFAULT '', reviewed_by VARCHAR(24) NULL, reviewed_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY vp (vacancy_id, player_id), KEY pl (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

  // ---- Tienda SampCity: productos, pedidos, pagos, entregas y eventos de Tebex ----
  await q(`CREATE TABLE IF NOT EXISTS store_products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    tebex_package_id INT NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(24) NOT NULL,
    amount INT NOT NULL DEFAULT 0,
    active TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  await q(`CREATE TABLE IF NOT EXISTS store_orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    player_id INT NOT NULL,
    product_id INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    tebex_basket_id VARCHAR(64) NULL,
    tebex_transaction_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY player_orders (player_id, id),
    KEY basket (tebex_basket_id),
    KEY transaction (tebex_transaction_id),
    CONSTRAINT store_orders_product FOREIGN KEY (product_id) REFERENCES store_products(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  await q(`CREATE TABLE IF NOT EXISTS store_payments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    provider VARCHAR(16) NOT NULL DEFAULT 'tebex',
    transaction_id VARCHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    currency VARCHAR(8) NOT NULL DEFAULT 'USD',
    raw_event JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY unique_transaction (provider, transaction_id),
    KEY order_payments (order_id),
    CONSTRAINT store_payments_order FOREIGN KEY (order_id) REFERENCES store_orders(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  await q(`CREATE TABLE IF NOT EXISTS store_deliveries (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    player_id INT NOT NULL,
    product_id INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(500) NULL,
    delivered_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY one_delivery_per_order (order_id),
    KEY player_deliveries (player_id, status),
    CONSTRAINT store_deliveries_order FOREIGN KEY (order_id) REFERENCES store_orders(id)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  await q(`CREATE TABLE IF NOT EXISTS store_webhook_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(16) NOT NULL DEFAULT 'tebex',
    event_type VARCHAR(48) NOT NULL,
    transaction_id VARCHAR(64) NULL,
    signature_valid TINYINT NOT NULL DEFAULT 0,
    processed TINYINT NOT NULL DEFAULT 0,
    body JSON NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY transaction_events (provider, transaction_id),
    KEY event_type (event_type, created_at)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4`);

  await q(`INSERT INTO store_products
    (tebex_package_id, name, kind, amount, active)
    VALUES (7726831, '10 Coins', 'coins', 10, 0)
    ON DUPLICATE KEY UPDATE name = VALUES(name), kind = VALUES(kind), amount = VALUES(amount)`);

  // Chat: responder, reacciones, audios y chats de grupo (room = id del chat de grupo; en ese caso b = 0)
  await addCol("web_msgs", "room", "INT NULL"); await addCol("web_msgs", "reply_to", "INT NULL");
  await q("CREATE TABLE IF NOT EXISTS web_msg_rx (msg_id INT NOT NULL, player_id INT NOT NULL, kind TINYINT NOT NULL DEFAULT 1, PRIMARY KEY (msg_id, player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_rooms (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(40) NOT NULL, owner INT NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_room_members (room_id INT NOT NULL, player_id INT NOT NULL, last_read INT NOT NULL DEFAULT 0, joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (room_id, player_id), KEY p (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_previews (h CHAR(64) NOT NULL PRIMARY KEY, url VARCHAR(500) NOT NULL, title VARCHAR(140) NOT NULL DEFAULT '', descr VARCHAR(240) NOT NULL DEFAULT '', img VARCHAR(500) NOT NULL DEFAULT '', at BIGINT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await addCol("web_media", "disk", "TINYINT NOT NULL DEFAULT 0"); // 1 = el archivo está en MEDIA_DIR y no en la base de datos
  // Índices para que el feed, los perfiles y el chat no recorran tablas enteras
  for (const [tb, key, cols] of [["web_posts", "au", "author, id"], ["web_posts", "gr", "group_id, id"], ["web_posts", "ty", "type, id"], ["web_posts", "cr", "created_at"], ["web_likes", "pl", "player_id"], ["web_comments", "pc", "player_id, created_at"], ["web_comments", "pa", "parent"], ["web_msgs", "im", "img"], ["web_msgs", "rm", "room, id"], ["web_msgs", "bs", "b, seen"], ["web_tags", "pl", "player_id"], ["web_market", "ss", "status, id"]]) await addKey(tb, key, cols);
  setInterval(cleanup, 3600000).unref(); cleanup();
  console.log(FFMPEG ? `[reel] ffmpeg disponible: los reels pesados se convierten a MP4 liviano (${FFMPEG})` : "[reel] sin ffmpeg: los reels se guardan tal cual (instala ffmpeg-static para convertirlos)");
  app.listen(Number(E.PORT) || 3000, () => console.log(`[web] SampCity en ${BASE}`));
})().catch((e) => { console.error("No pude conectar con la base de datos:", e.message); process.exit(1); });
