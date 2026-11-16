// Vista previa de enlaces: título, descripción e imagen (Open Graph) del primer enlace de una publicación.
// El servidor solo consulta páginas https públicas: nunca direcciones internas (se comprueba la IP real a la que se conecta).
const https = require("https"), dns = require("dns");
const { privateIp, ogParse } = require("../lib/util");
module.exports = (app, { crypto, str, q, sessionId, hit }) => {
const lookup = (host, opts, cb) => dns.lookup(host, { all: true }, (err, list) => { // se rechaza si alguna IP del dominio es privada
  if (err) return cb(err);
  const bad = !list.length || list.some((a) => privateIp(a.address));
  if (bad) return cb(new Error("dirección no permitida"));
  return opts && opts.all ? cb(null, list) : cb(null, list[0].address, list[0].family);
});
function fetchHtml(url, hops = 0) {
  return new Promise((ok, no) => {
    let u; try { u = new URL(url); } catch { return no(new Error("url")); }
    if (u.protocol !== "https:" || u.port || u.username || /^[\d.:\[\]]+$/.test(u.hostname)) return no(new Error("url"));
    const rq = https.get(u, { lookup, timeout: 6000, headers: { "User-Agent": "Mozilla/5.0 (compatible; LinkPreview/1.0)", Accept: "text/html" } }, (rs) => {
      if (rs.statusCode >= 300 && rs.statusCode < 400 && rs.headers.location && hops < 3) { rs.resume(); let nx; try { nx = new URL(rs.headers.location, u).href; } catch { return no(new Error("url")); } return fetchHtml(nx, hops + 1).then(ok, no); }
      if (rs.statusCode !== 200 || !/text\/html|xhtml/i.test(rs.headers["content-type"] || "")) { rs.resume(); return no(new Error("sin vista previa")); }
      const parts = []; let n = 0;
      rs.on("data", (c) => { parts.push(c); n += c.length; if (n > 300000) rs.destroy(); }); // solo hace falta el <head>
      const done = () => ok({ html: Buffer.concat(parts).toString("utf8"), url: u.href });
      rs.on("end", done); rs.on("close", done); rs.on("error", done);
    });
    rq.on("timeout", () => rq.destroy(new Error("tiempo agotado"))); rq.on("error", no);
  });
}
app.get("/api/preview", async (req, res) => {
  const uid = sessionId(req), url = str(req.query.u).trim();
  if (!/^https:\/\/[^\s"'<>]{4,490}$/.test(url)) return res.json({});
  const h = crypto.createHash("sha256").update(url).digest("hex");
  const c = (await q("SELECT title, descr, img, at FROM web_previews WHERE h = ?", [h]))[0];
  if (c && Date.now() - Number(c.at) < 7 * 864e5) return res.set("Cache-Control", "private, max-age=3600").json(c.title ? { title: c.title, descr: c.descr, img: c.img, host: new URL(url).hostname } : {});
  if (!uid || !hit("pv|" + uid, 30, 60000)) return res.json({}); // solo quien tiene sesión hace que el servidor consulte una página nueva
  let o = { title: "", descr: "", img: "" };
  try { const r = await fetchHtml(url); o = ogParse(r.html, r.url); } catch {}
  await q("INSERT INTO web_previews (h, url, title, descr, img, at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE title = VALUES(title), descr = VALUES(descr), img = VALUES(img), at = VALUES(at)", [h, url, o.title, o.descr, o.img, Date.now()]).catch(() => {});
  res.json(o.title ? { ...o, host: new URL(url).hostname } : {});
});
};
