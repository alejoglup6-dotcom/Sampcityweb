// Pasa a la carpeta MEDIA_DIR los fotos y videos que hoy están en la base de datos (tabla web_media_chunks).
// Uso: pon MEDIA_DIR en el .env, y ejecuta una vez:  node scripts/medios-a-disco.js
// Se puede repetir: solo mueve los que faltan. Cada archivo se comprueba antes de borrar sus trozos de la base.
require("dotenv").config();
const fs = require("fs/promises"), path = require("path"), mysql = require("mysql2/promise");
const E = process.env;
(async () => {
  if (!E.MEDIA_DIR) throw new Error("Falta MEDIA_DIR en el .env");
  const dir = path.resolve(E.MEDIA_DIR), db = await mysql.createConnection({ host: E.MYSQL_HOST, port: Number(E.MYSQL_PORT) || 3306, user: E.MYSQL_USER, password: E.MYSQL_PASSWORD, database: E.MYSQL_DATABASE });
  const [rows] = await db.query("SELECT id, size FROM web_media WHERE disk = 0 ORDER BY id");
  let ok = 0, bad = 0;
  for (const m of rows) {
    const [ch] = await db.query("SELECT data FROM web_media_chunks WHERE media_id = ? ORDER BY n", [m.id]), buf = Buffer.concat(ch.map((c) => c.data));
    if (buf.length !== Number(m.size)) { console.log(`medio ${m.id}: tamaño distinto (${buf.length} de ${m.size}), se deja en la base`); bad++; continue; }
    const file = path.join(dir, String(Math.floor(m.id / 1000)), String(m.id));
    await fs.mkdir(path.dirname(file), { recursive: true }); await fs.writeFile(file, buf);
    if ((await fs.stat(file)).size !== buf.length) { console.log(`medio ${m.id}: no se escribió bien`); bad++; continue; }
    await db.query("UPDATE web_media SET disk = 1 WHERE id = ?", [m.id]); await db.query("DELETE FROM web_media_chunks WHERE media_id = ?", [m.id]); ok++;
  }
  console.log(`Listo: ${ok} movidos a ${dir}, ${bad} sin mover.`);
  await db.end();
})().catch((e) => { console.error(e.message); process.exit(1); });
