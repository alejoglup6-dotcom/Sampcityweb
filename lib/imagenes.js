// Almacén de imágenes sueltas y adjuntos del chat (tabla web_images): fotos de perfil, banners, fotos del staff, anuncios, fotos y audios de mensajes.
// Con MEDIA_DIR en el .env el archivo va a esa carpeta (subcarpeta img/) y en la base solo queda su ficha; sin ella, va entero a la base.
const fs = require("fs/promises"), path = require("path");
module.exports = (q, E) => {
  const DIR = E.MEDIA_DIR ? path.join(path.resolve(E.MEDIA_DIR), "img") : "";
  const file = (id) => path.join(DIR || ".", String(Math.floor(Number(id) / 1000)), String(Number(id)));
  async function put(mime, buf) {
    if (!DIR) return (await q("INSERT INTO web_images (mime, data) VALUES (?, ?)", [mime, buf])).insertId;
    const id = (await q("INSERT INTO web_images (mime, data, disk) VALUES (?, '', 1)", [mime])).insertId;
    try { await fs.mkdir(path.dirname(file(id)), { recursive: true }); await fs.writeFile(file(id), buf); }
    catch (e) { await q("DELETE FROM web_images WHERE id = ?", [id]); throw e; }
    return id;
  }
  async function get(id) { // { mime, data } o null
    const r = (await q("SELECT mime, data, disk FROM web_images WHERE id = ?", [id]))[0];
    if (!r) return null;
    if (!Number(r.disk)) return { mime: r.mime, data: r.data };
    try { return { mime: r.mime, data: await fs.readFile(file(id)) }; } catch { return null; }
  }
  async function del(ids) {
    ids = [...new Set((Array.isArray(ids) ? ids : [ids]).map(Number).filter(Boolean))]; if (!ids.length) return;
    for (const r of await q("SELECT id FROM web_images WHERE id IN (?) AND disk = 1", [ids])) await fs.unlink(file(r.id)).catch(() => {});
    await q("DELETE FROM web_images WHERE id IN (?)", [ids]);
  }
  return { put, get, del, file, DIR };
};
