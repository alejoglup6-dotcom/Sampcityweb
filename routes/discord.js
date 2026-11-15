// Rutas de discord. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { crypto, store, E, BASE, q, sessionId, me, notify }) => {
// ---- Vincular Discord (OAuth2: solo se lee el id del usuario) ----
// El "state" se guarda aquí (por cuenta, 10 min) y no en una cookie: algunos navegadores no la devolvían al volver de
// Discord y el enlace fallaba sin decir nada. Siempre se vuelve a la página de donde se vino (lo dice el propio state).
const DSTATE = new Map(); // state -> { playerId, next, until }
// El código lo canjea el bot desde QoreHost (tabla discord_oauth_queue): Cloudflare bloquea a veces la IP de salida de
// Render (429 del 22-oct-2026). La página /verificar espera el resultado con /api/discord/estado.
// DISCORD_OAUTH_MODO=web vuelve a canjearlo aquí.
const OAUTH_VIA_BOT = String(E.DISCORD_OAUTH_MODO || "bot").toLowerCase() !== "web";
app.get("/auth/discord", async (req, res) => {
  const u = await me(req);
  const next = req.query.next === "verificar" ? "verificar" : "perfil"; // a dónde se vuelve después
  if (!u) return res.redirect(next === "verificar" ? "/verificar" : "/?login=1");
  const now = Date.now();
  for (const [k, v] of DSTATE) if (v.until < now) DSTATE.delete(k);
  const state = crypto.randomBytes(16).toString("hex") + "." + next;
  DSTATE.set(state, { playerId: Number(u.id), next, until: now + 600000 });
  const p = new URLSearchParams({ client_id: E.DISCORD_CLIENT_ID, redirect_uri: `${BASE}/auth/discord/callback`, response_type: "code", scope: "identify", state });
  res.redirect(`https://discord.com/oauth2/authorize?${p}`);
});
app.get("/auth/discord/callback", async (req, res) => {
  const qs = typeof req.query.state === "string" ? req.query.state : "";
  const back = qs.endsWith(".verificar") ? "/verificar" : "/perfil";
  const fail = (motivo, info = "") => {
    console.log("[discord]", motivo, info);
    return res.redirect(`${back}?discord=error&motivo=${motivo}`);
  };
  // Discord a veces contesta con una página HTML (Cloudflare) en vez de JSON: se apunta qué devolvió y en qué paso
  const dj = async (paso, r) => {
    const txt = await r.text();
    try { return JSON.parse(txt); } catch {
      const h = r.headers;
      throw Object.assign(new Error("respuesta no JSON"), { paso, info: `${paso} HTTP ${r.status} type=${h.get("content-type") || "-"} cf-ray=${h.get("cf-ray") || "-"} retry-after=${h.get("retry-after") || "-"} x-ratelimit-global=${h.get("x-ratelimit-global") || "-"} x-ratelimit-scope=${h.get("x-ratelimit-scope") || "-"} server=${h.get("server") || "-"} cuerpo=${txt.slice(0, 300).replace(/\s+/g, " ")}` });
    }
  };
  try {
    if (req.query.error) return fail("cancelado", String(req.query.error)); // pulsó Cancelar en Discord
    const saved = DSTATE.get(qs);
    DSTATE.delete(qs); // el estado se usa una sola vez
    if (!saved || saved.until < Date.now() || typeof req.query.code !== "string" || !req.query.code) return fail("estado");
    const u = (await q("SELECT id, name, admin_level FROM player WHERE id = ?", [saved.playerId]))[0];
    if (!u) return fail("sesion");
    if (OAUTH_VIA_BOT) {
      const token = crypto.randomBytes(16).toString("hex");
      await q("INSERT INTO discord_oauth_queue (token, player_id, back, code) VALUES (?, ?, ?, ?)", [token, u.id, back === "/verificar" ? "verificar" : "perfil", String(req.query.code)]);
      return res.redirect(`/verificar?discord=pendiente&req=${token}`);
    }
    const t = await dj("token", await fetch("https://discord.com/api/oauth2/token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: new URLSearchParams({ client_id: E.DISCORD_CLIENT_ID, client_secret: E.DISCORD_CLIENT_SECRET, grant_type: "authorization_code", code: String(req.query.code), redirect_uri: `${BASE}/auth/discord/callback` }), signal: AbortSignal.timeout(10000) }));
    if (!t.access_token) return fail("token", JSON.stringify(t).slice(0, 200)); // casi siempre DISCORD_CLIENT_SECRET mal puesto
    const d = await dj("usuario", await fetch("https://discord.com/api/users/@me", { headers: { Authorization: `Bearer ${t.access_token}` }, signal: AbortSignal.timeout(10000) }));
    if (!/^\d+$/.test(d.id || "")) return fail("usuario", JSON.stringify(d).slice(0, 200));
    const rows = await q("SELECT dl.player_id, dl.discord_id, p.name FROM discord_links dl LEFT JOIN player p ON p.id = dl.player_id WHERE dl.discord_id = ? OR dl.player_id = ?", [d.id, u.id]);
    const same = rows.find((r) => Number(r.player_id) === Number(u.id) && String(r.discord_id) === d.id);
    if (!same && rows.length) {
      // ese Discord ya está con otra cuenta del juego, o esta cuenta ya tiene otro Discord
      const other = rows.find((r) => String(r.discord_id) === d.id);
      return res.redirect(other ? `${back}?discord=duplicado&tipo=discord&cuenta=${encodeURIComponent(other.name || "")}` : `${back}?discord=duplicado&tipo=cuenta`);
    }
    if (!same) {
      await q("INSERT INTO discord_links (player_id, discord_id) VALUES (?, ?)", [u.id, d.id]);
      await notify(u.id, { id: 0 }, "discord");
    }
    res.redirect(back + "?discord=ok"); // el bot ve el enlace nuevo y pone el rol de verificado y el apodo
  } catch (e) { return e.paso ? fail("limite", e.info) : fail("servidor", e.message); }
});
// Resultado del canje que hace el bot. Solo lo ve la cuenta que lo pidió; el aviso "discord" se crea una vez.
app.get("/api/discord/estado", async (req, res) => {
  res.set("Cache-Control", "no-store");
  const id = sessionId(req);
  if (!id) return res.status(401).json({ error: "Inicia sesión" });
  const tk = typeof req.query.req === "string" && /^[0-9a-f]{32}$/.test(req.query.req) ? req.query.req : "";
  const r = tk ? (await q("SELECT id, status, motivo, cuenta, back, notified FROM discord_oauth_queue WHERE token = ? AND player_id = ?", [tk, id]))[0] : null;
  if (!r) return res.status(404).json({ error: "No encontrado" });
  if (r.status === "hecho" && !Number(r.notified) && (await q("UPDATE discord_oauth_queue SET notified = 1 WHERE id = ? AND notified = 0", [r.id])).affectedRows === 1) await notify(id, { id: 0 }, "discord");
  res.json({ estado: r.status, motivo: r.motivo, cuenta: r.cuenta, back: r.back });
});
app.delete("/api/discord", async (req, res) => {
  const u = await me(req);
  if (!u) return res.status(401).json({ error: "Inicia sesión" });
  await q("DELETE FROM discord_links WHERE player_id = ?", [u.id]);
  res.json({ ok: true });
});

};
