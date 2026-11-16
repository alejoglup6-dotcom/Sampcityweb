// Tiempo real (Server-Sent Events) y notificaciones push (Web Push).
// send(uid, evento, datos) avisa al instante a las pestañas abiertas de un jugador; push(uid, {...}) llega aunque la web esté cerrada.
module.exports = (app, { E, BASE, str, q, sessionId, hit }) => {
// ---- En vivo: cada pestaña abre /api/stream y se queda escuchando ----
const conns = new Map(); // jugador → Set de respuestas abiertas
const MAX_PER_USER = 6;
const send = (uid, ev, data = {}) => {
  const set = conns.get(Number(uid)); if (!set || !set.size) return false;
  const msg = `event: ${ev}\ndata: ${JSON.stringify(data)}\n\n`;
  for (const r of set) { try { r.write(msg); } catch {} }
  return true;
};
const online = (uid) => (conns.get(Number(uid)) || { size: 0 }).size > 0;
app.get("/api/stream", (req, res) => {
  const uid = sessionId(req); if (!uid) return res.status(401).json({ error: "Inicia sesión" });
  res.status(200).set({ "Content-Type": "text/event-stream; charset=utf-8", "Cache-Control": "no-store, no-transform", Connection: "keep-alive", "X-Accel-Buffering": "no" });
  res.flushHeaders(); res.write("retry: 5000\n\n");
  let set = conns.get(uid); if (!set) conns.set(uid, (set = new Set()));
  if (set.size >= MAX_PER_USER) { const old = set.values().next().value; set.delete(old); try { old.end(); } catch {} } // demasiadas pestañas: se suelta la más vieja
  set.add(res);
  req.on("close", () => { set.delete(res); if (!set.size && conns.get(uid) === set) conns.delete(uid); });
});
setInterval(() => { for (const set of conns.values()) for (const r of set) { try { r.write(": ping\n\n"); } catch {} } }, 25000).unref(); // mantiene viva la conexión tras proxys

// ---- Push: el navegador se suscribe y el servidor le envía avisos cifrados (paquete opcional web-push) ----
let wp = null, KEY = "";
try { wp = require("web-push"); } catch {}
const PUSH_HOSTS = /^(fcm\.googleapis\.com|updates\.push\.services\.mozilla\.com|[a-z0-9-]+\.notify\.windows\.com|[a-z0-9-]+\.push\.apple\.com)$/; // solo servicios de push conocidos: el servidor nunca llama a otra dirección
async function init() {
  await q("CREATE TABLE IF NOT EXISTS web_kv (k VARCHAR(40) NOT NULL PRIMARY KEY, v TEXT NOT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  await q("CREATE TABLE IF NOT EXISTS web_push (id INT AUTO_INCREMENT PRIMARY KEY, player_id INT NOT NULL, h CHAR(64) NOT NULL, endpoint VARCHAR(600) NOT NULL, p256dh VARCHAR(120) NOT NULL, auth VARCHAR(40) NOT NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, UNIQUE KEY h (h), KEY p (player_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  if (!wp) return console.log("[push] sin el paquete web-push: no hay notificaciones push (npm install lo instala)");
  let pub = E.VAPID_PUBLIC || "", priv = E.VAPID_PRIVATE || "";
  if (!pub || !priv) { // sin claves en el .env: se generan una vez y se guardan en la base de datos
    const row = (await q("SELECT v FROM web_kv WHERE k = 'vapid'"))[0];
    let k = null; try { k = row ? JSON.parse(row.v) : null; } catch {}
    if (!k) { k = wp.generateVAPIDKeys(); await q("INSERT INTO web_kv (k, v) VALUES ('vapid', ?) ON DUPLICATE KEY UPDATE v = VALUES(v)", [JSON.stringify(k)]); }
    pub = k.publicKey; priv = k.privateKey;
  }
  try { wp.setVapidDetails(/^https:/.test(BASE) ? BASE : "mailto:admin@localhost.invalid", pub, priv); KEY = pub; console.log("[push] notificaciones push activas"); }
  catch (e) { console.log("[push] claves VAPID no válidas:", e.message); }
}
const hash = (s) => require("crypto").createHash("sha256").update(s).digest("hex");
async function deliver(rows, payload) {
  const body = JSON.stringify(payload);
  await Promise.all(rows.map((r) => wp.sendNotification({ endpoint: r.endpoint, keys: { p256dh: r.p256dh, auth: r.auth } }, body, { TTL: 3600, timeout: 8000 }).catch(async (e) => {
    if (e && (e.statusCode === 404 || e.statusCode === 410)) await q("DELETE FROM web_push WHERE id = ?", [r.id]).catch(() => {}); // suscripción caducada
  })));
}
async function push(uid, payload) { // payload: { title, body, url, tag }
  if (!KEY || !uid) return;
  try { const rows = await q("SELECT id, endpoint, p256dh, auth FROM web_push WHERE player_id = ? ORDER BY id DESC LIMIT 10", [uid]); if (rows.length) await deliver(rows, payload); }
  catch (e) { console.log("[push]", e.message); }
}
async function pushAll(payload, exceptId = 0) { // noticias: a todos los suscritos, por tandas y en segundo plano
  if (!KEY) return;
  try { const rows = await q("SELECT id, endpoint, p256dh, auth FROM web_push WHERE player_id <> ? ORDER BY id DESC LIMIT 5000", [exceptId]); for (let i = 0; i < rows.length; i += 25) await deliver(rows.slice(i, i + 25), payload); }
  catch (e) { console.log("[push]", e.message); }
}
app.get("/api/push/key", (req, res) => res.json({ key: KEY }));
app.post("/api/push/sub", async (req, res) => {
  const uid = sessionId(req); if (!uid) return res.status(401).json({ error: "Inicia sesión" });
  if (!KEY) return res.status(503).json({ error: "Las notificaciones push no están activas en el servidor" });
  if (!hit("ps|" + uid, 20, 3600000)) return res.status(429).json({ error: "Demasiados intentos. Espera un rato." });
  const ep = str(req.body.endpoint), k = req.body.keys || {}, p = str(k.p256dh), a = str(k.auth);
  let host = ""; try { const u = new URL(ep); if (u.protocol === "https:" && !u.port) host = u.hostname; } catch {}
  if (!PUSH_HOSTS.test(host) || ep.length > 600 || !/^[A-Za-z0-9_-]{20,120}$/.test(p) || !/^[A-Za-z0-9_-]{8,40}$/.test(a)) return res.status(400).json({ error: "Suscripción no válida" });
  await q("INSERT INTO web_push (player_id, h, endpoint, p256dh, auth) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_id = VALUES(player_id), p256dh = VALUES(p256dh), auth = VALUES(auth)", [uid, hash(ep), ep, p, a]);
  res.json({ ok: true });
});
app.delete("/api/push/sub", async (req, res) => {
  const uid = sessionId(req); if (!uid) return res.status(401).json({ error: "Inicia sesión" });
  await q("DELETE FROM web_push WHERE h = ? AND player_id = ?", [hash(str(req.body && req.body.endpoint)), uid]);
  res.json({ ok: true });
});
// Texto de cada aviso para el push (la página de notificaciones tiene los suyos)
const nm = (n) => String(n || "Alguien").replace(/_/g, " ");
const TXT = { like: "reaccionó a tu publicación", like_c: "reaccionó a tu comentario", comment: "comentó tu publicación", reply: "respondió en una publicación", mention: "te mencionó", mention_c: "te mencionó en un comentario", tag: "te etiquetó", friend_req: "te envió una solicitud de amistad", friend_acc: "aceptó tu solicitud de amistad", follow: "empezó a seguirte", share: "compartió tu publicación" };
const notifText = (type, actor, postId, body) => ({
  title: "SampCity", tag: type + (postId || ""),
  body: TXT[type] ? `${nm(actor)} ${TXT[type]}${body ? ": " + String(body).slice(0, 80) : ""}` : type === "contact" ? "Nuevo mensaje en Contacto" : type === "news" || type === "update" ? String(body || "Hay una publicación nueva") : "Tienes una notificación nueva",
  url: postId ? `/p/${postId}` : type === "friend_req" || type === "friend_acc" ? "/amigos" : "/notificaciones",
});
return { send, online, push, pushAll, notifText, init, pushOn: () => !!KEY };
};
