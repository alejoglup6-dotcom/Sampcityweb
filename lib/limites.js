// Límites de peticiones. Se cuentan en memoria (rápido) y cada 3 s se sincronizan con la tabla web_limits:
// así sobreviven a un reinicio y valen entre varios procesos de Node (con unos segundos de margen).
module.exports = (q) => {
  const B = new Map(); // clave → { n: usos en la ventana, r: cuándo vence, d: usos aún sin guardar }
  const hit = (key, max, ms) => {
    key = String(key).slice(0, 120);
    const now = Date.now(); let b = B.get(key);
    if (!b || b.r < now) { b = { n: 0, r: now + ms, d: 0 }; B.set(key, b); }
    b.n++; b.d++;
    return b.n <= max;
  };
  let busy = false, tick = 0;
  async function sync() {
    if (busy) return; busy = true;
    try {
      const now = Date.now(), out = [];
      for (const [k, b] of B) { if (b.r < now) { B.delete(k); continue; } if (b.d > 0) { out.push([k, b.d, b.r]); b.d = 0; } }
      for (let i = 0; i < out.length; i += 500) await q("INSERT INTO web_limits (k, n, r) VALUES ? ON DUPLICATE KEY UPDATE n = IF(r < ?, VALUES(n), n + VALUES(n)), r = IF(r < ?, VALUES(r), r)", [out.slice(i, i + 500), now, now]);
      for (const x of await q("SELECT k, n, r FROM web_limits WHERE r > ? LIMIT 50000", [now])) { // lo que contaron los demás procesos (o este antes de reiniciar)
        const b = B.get(x.k);
        if (!b) B.set(x.k, { n: Number(x.n), r: Number(x.r), d: 0 }); else { b.n = Math.max(b.n, Number(x.n) + b.d); b.r = Number(x.r); }
      }
      if (++tick % 20 === 0) await q("DELETE FROM web_limits WHERE r < ?", [now]);
    } catch (e) { if (tick++ % 100 === 0) console.log("[límites]", e.message); }
    busy = false;
  }
  async function start() {
    await q("CREATE TABLE IF NOT EXISTS web_limits (k VARCHAR(120) NOT NULL PRIMARY KEY, n INT NOT NULL, r BIGINT NOT NULL, KEY r (r)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    await sync(); setInterval(sync, 3000).unref();
  }
  return { hit, sync, start };
};
