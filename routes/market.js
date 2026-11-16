// Marketplace: anuncios de compra/venta entre jugadores (vehículos, propiedades, negocios, objetos).
// La web solo publica el anuncio: el trato y la entrega se hacen dentro del juego.
module.exports = (app, { imgs, express, str, pid, MIN_POST, SKIN_COL, q, sessionId, hit, me }) => {
const CATS = ["vehiculos", "propiedades", "negocios", "objetos"], MAX_ACTIVE = 20, PER = 12;
const like = (s) => "%" + s.replace(/[\\%_]/g, "\\$&") + "%"; // el texto buscado no puede meter comodines

app.get("/api/market", async (req, res) => {
  const uid = sessionId(req) || 0, cat = CATS.includes(req.query.cat) ? req.query.cat : "", s = str(req.query.q).trim().slice(0, 40);
  const pg = Math.min(1000, Math.max(0, Number(req.query.page) || 0)), where = [], wp = [];
  if (req.query.mine === "1") { if (!uid) return res.status(401).json({ error: "Inicia sesión" }); where.push("m.seller = ?"); wp.push(uid); } else where.push("m.status = 1");
  if (cat) { where.push("m.cat = ?"); wp.push(cat); }
  if (s) { where.push("(m.title LIKE ? OR m.body LIKE ?)"); wp.push(like(s), like(s)); }
  const rows = await q(`SELECT m.id, m.cat, m.title, m.price, m.body, m.img, m.status, m.created_at, p.name AS seller, p.${SKIN_COL} AS skin, p.connected, (m.seller = ?) AS mine FROM web_market m JOIN player p ON p.id = m.seller WHERE ${where.join(" AND ")} ORDER BY ${req.query.sort === "barato" ? "m.price ASC, m.id DESC" : req.query.sort === "caro" ? "m.price DESC, m.id DESC" : "m.id DESC"} LIMIT ? OFFSET ?`, [uid, ...wp, PER + 1, pg * PER]); // +1 para saber si hay más
  res.json(rows.map((r) => ({ ...r, price: Number(r.price), mine: Number(r.mine) === 1, img: r.img ? `/img/${r.img}` : "" })));
});

// Publicar: la foto (opcional) llega ya reducida desde el navegador, igual que en /api/photos
app.post("/api/market", express.json({ limit: "1500kb" }), async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  if (!hit("mk|" + u.id, 10, 3600000)) return res.status(429).json({ error: "Publicaste muchos anuncios. Espera un rato." });
  const cat = str(req.body.cat), title = str(req.body.title).trim().slice(0, 80), body = str(req.body.body).trim().slice(0, 600), price = Math.floor(Number(req.body.price));
  if (!CATS.includes(cat)) return res.status(400).json({ error: "Elige una categoría" });
  if (title.length < 3) return res.status(400).json({ error: "Escribe un título (mínimo 3 letras)" });
  if (!Number.isSafeInteger(price) || price < 0 || price > 2000000000) return res.status(400).json({ error: "Escribe un precio válido" });
  if (Number((await q("SELECT COUNT(*) AS n FROM web_market WHERE seller = ? AND status = 1", [u.id]))[0].n) >= MAX_ACTIVE) return res.status(409).json({ error: `Ya tienes ${MAX_ACTIVE} anuncios activos. Elimina o marca alguno como vendido.` });
  let img = null;
  if (req.body.data) {
    const m = /^data:image\/(jpeg|png|webp);base64,([A-Za-z0-9+/=]+)$/.exec(str(req.body.data));
    if (!m || m[2].length > 1250000) return res.status(400).json({ error: "Imagen no válida" });
    const buf = Buffer.from(m[2], "base64");
    const magic = (m[1] === "jpeg" && buf[0] === 0xff && buf[1] === 0xd8) || (m[1] === "png" && buf[0] === 0x89 && buf[1] === 0x50) || (m[1] === "webp" && buf.toString("ascii", 0, 4) === "RIFF" && buf.toString("ascii", 8, 12) === "WEBP");
    if (!magic || buf.length > 900 * 1024) return res.status(400).json({ error: "Imagen no válida o muy pesada (máx. 900 KB)" });
    img = await imgs.put("image/" + m[1], buf);
  }
  const r = await q("INSERT INTO web_market (seller, cat, title, price, body, img) VALUES (?, ?, ?, ?, ?, ?)", [u.id, cat, title, price, body, img]);
  res.json({ ok: true, id: r.insertId });
});

// Editar un anuncio propio (texto, precio y categoría)
app.put("/api/market/:id(\\d+)", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const cat = str(req.body.cat), title = str(req.body.title).trim().slice(0, 80), body = str(req.body.body).trim().slice(0, 600), price = Math.floor(Number(req.body.price));
  if (!CATS.includes(cat) || title.length < 3 || !Number.isSafeInteger(price) || price < 0 || price > 2000000000) return res.status(400).json({ error: "Revisa la categoría, el título y el precio" });
  const r = await q("UPDATE web_market SET cat = ?, title = ?, price = ?, body = ? WHERE id = ? AND seller = ?", [cat, title, price, body, pid(req.params.id), u.id]);
  if (!r.affectedRows && !(await q("SELECT 1 FROM web_market WHERE id = ? AND seller = ?", [pid(req.params.id), u.id])).length) return res.status(403).json({ error: "No es tu anuncio" });
  res.json({ ok: true });
});

// Marcar como vendido / volver a activar (solo quien lo publicó)
app.post("/api/market/:id(\\d+)/sold", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), row = (await q("SELECT seller, status FROM web_market WHERE id = ?", [id]))[0];
  if (!row) return res.status(404).json({ error: "Ese anuncio ya no existe" });
  if (Number(row.seller) !== u.id) return res.status(403).json({ error: "No es tu anuncio" });
  const status = Number(row.status) === 1 ? 2 : 1;
  await q("UPDATE web_market SET status = ? WHERE id = ?", [status, id]);
  res.json({ ok: true, status });
});

// Eliminar: quien lo publicó o el staff
app.delete("/api/market/:id(\\d+)", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión" });
  const id = pid(req.params.id), row = (await q("SELECT seller, img FROM web_market WHERE id = ?", [id]))[0];
  if (!row) return res.status(404).json({ error: "Ese anuncio ya no existe" });
  if (Number(row.seller) !== u.id && !(Number(u.admin_level) >= MIN_POST)) return res.status(403).json({ error: "No tienes permiso" });
  await q("DELETE FROM web_market WHERE id = ?", [id]);
  if (row.img) await imgs.del(row.img);
  res.json({ ok: true });
});
};
