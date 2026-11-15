// Rutas de guia. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { path, str, MIN_POST, q, hit, me, ROOT }) => {
// ---- Guía (/guia): se arma sola con lo que vuelca el gamemode al arrancar (guide_entries), los negocios y
// estacionamientos en vivo y los artículos de data/guia.json. Se guarda 2 minutos en memoria. ----
const GUIA_ART = (() => { try { return JSON.parse(require("fs").readFileSync(path.join(ROOT, "data", "guia.json"), "utf8")).articulos || []; } catch (e) { console.log("[guia] data/guia.json:", e.message); return []; } })();
const BIZ_TYPES = ["", "Restaurante", "Bar", "Tienda 24/7", "Taller"];
let GUIA = null, GUIA_AT = 0;
const safe = async (sql, p) => { try { return await q(sql, p); } catch { return []; } }; // la tabla puede no existir todavía
async function guia() {
  if (GUIA && Date.now() - GUIA_AT < 120000) return GUIA;
  const [rows, biz, parks, spots, pop] = await Promise.all([
    safe("SELECT kind, slug, title, summary, body, category, tags, x, y, zone, staff, UNIX_TIMESTAMP(updated_at) AS t FROM guide_entries"),
    safe("SELECT id, name, type, owner_name, price, open, open_hour, close_hour, level, x, y FROM businesses"),
    safe("SELECT id, name, price, x, y FROM parkings"),
    safe("SELECT parking_id, COUNT(*) AS n FROM parking_spots GROUP BY parking_id"),
    safe("SELECT q FROM web_guide_searches WHERE results > 0 AND last_at > DATE_SUB(NOW(), INTERVAL 30 DAY) ORDER BY n DESC LIMIT 12"),
  ]);
  const e = [];
  let updated = 0;
  for (const r of rows) {
    if (r.category === "Oculto") continue;
    updated = Math.max(updated, Number(r.t) || 0);
    e.push({ k: r.kind, id: r.slug, t: r.title, s: r.summary || "", b: r.body || "", c: r.category || "", g: r.tags || "", z: r.zone || "", x: Math.round(r.x || 0), y: Math.round(r.y || 0), st: Number(r.staff) || 0 });
  }
  const zoneOf = (x, y) => { // la zona del lugar de la guía más cercano (los negocios no la guardan)
    let best = "", bd = 1e12;
    for (const r of e) if (r.z && (r.x || r.y)) { const d = (r.x - x) ** 2 + (r.y - y) ** 2; if (d < bd) { bd = d; best = r.z; } }
    return bd < 250 ** 2 ? best : "";
  };
  for (const b of biz) {
    const tipo = BIZ_TYPES[b.type] || "Negocio", dueño = b.owner_name ? b.owner_name.replace(/_/g, " ") : "";
    const horario = Number(b.open_hour) === Number(b.close_hour) ? "todo el día" : `de ${b.open_hour}:00 a ${b.close_hour}:00`;
    e.push({ k: "negocio", id: "negocio-" + b.id, t: b.name, c: tipo, g: "negocio " + tipo.toLowerCase() + (dueño ? "" : " en venta comprar"), z: zoneOf(b.x, b.y), x: Math.round(b.x), y: Math.round(b.y), st: 0,
      s: dueño ? `${tipo} de ${dueño}. ${b.open ? "Abierto " + horario : "Cerrado"}.` : `${tipo} en venta por $${Number(b.price).toLocaleString("es-CO")}.`,
      b: dueño ? `Nivel ${b.level} de 3. Pulsa Y en el mostrador para comprar.` : "Pulsa Y en su mostrador para comprarlo (nivel 3). Se administra con /negocio." });
  }
  const sn = new Map(spots.map((r) => [Number(r.parking_id), Number(r.n)]));
  for (const p of parks) e.push({ k: "lugar", id: "parking-" + p.id, t: "Estacionamiento: " + p.name, c: "Estacionamientos", g: "estacionamiento parking retirar vehiculo", z: zoneOf(p.x, p.y), x: Math.round(p.x), y: Math.round(p.y), st: 0,
    s: (Number(p.price) > 0 ? `Retirar un vehículo cuesta $${p.price}.` : "Gratis.") + ` ${sn.get(Number(p.id)) || 0} huecos.`, b: "Pulsa Y en el punto: MIS VEHÍCULOS y RETIRAR." });
  for (const a of GUIA_ART) e.push({ k: "guia", id: a.slug, t: a.title, s: a.summary, b: a.body, c: a.category, g: a.tags || "", r: a.relacionados || [], st: 0 });
  GUIA = { entries: e, popular: pop.map((r) => r.q), updated: updated ? new Date(updated * 1000).toISOString() : null };
  GUIA_AT = Date.now();
  return GUIA;
}
app.get("/api/guia", async (req, res) => res.set("Cache-Control", "public, max-age=60").json(await guia()));
// Lo que busca la gente: sirve para las búsquedas populares y para ver qué falta en la guía (las que no dan resultado)
app.post("/api/guia/busqueda", async (req, res) => {
  const v = str(req.body.q).toLowerCase().replace(/\s+/g, " ").trim().slice(0, 60), n = Math.max(0, Math.min(999, Number(req.body.n) || 0));
  if (v.length < 3 || !hit("gs|" + req.ip, 20, 60000)) return res.json({ ok: true });
  await safe("INSERT INTO web_guide_searches (q, results, n) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE n = n + 1, results = VALUES(results), last_at = NOW()", [v, n]);
  res.json({ ok: true });
});
app.get("/api/guia/sin-resultados", async (req, res) => { // para el staff: lo que se busca y no está en la guía
  const u = await me(req); if (!u || u.admin_level < MIN_POST) return res.status(403).json({ error: "Solo staff" });
  res.json(await safe("SELECT q, n, last_at FROM web_guide_searches WHERE results = 0 ORDER BY n DESC, last_at DESC LIMIT 50"));
});

};
