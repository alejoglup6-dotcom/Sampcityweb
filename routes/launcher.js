// Rutas de launcher. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { path, store, E, q, attach, ROOT }) => {
// ---- Launcher de Android (prueba): lo que consulta la app al abrirse ----
// La lista de archivos del juego (data/launcher-cache.json: nombre, carpeta y tamaño) solo se manda si LAUNCHER_CDN
// apunta a donde están subidos (Cloudflare R2 u otro); sin eso la app no descarga nada y usa los que ya tenga.
const LAUNCHER_CACHE = (() => { try { return JSON.parse(require("fs").readFileSync(path.join(ROOT, "data", "launcher-cache.json"), "utf8")); } catch (e) { return []; } })();
const LAUNCHER_SERVER = E.LAUNCHER_SERVER || "sv.sampcity.app:7781", LAUNCHER_VERSION = E.LAUNCHER_VERSION || "1.0.0";
let launcherIp = { ip: "", at: 0 };
async function launcherAddress() { // el cliente necesita la IP numérica: se resuelve el dominio cada 5 min
  const [host, port] = LAUNCHER_SERVER.split(":");
  if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) return LAUNCHER_SERVER;
  if (!launcherIp.ip || Date.now() - launcherIp.at > 300000) {
    try { launcherIp = { ip: (await require("dns").promises.lookup(host, { family: 4 })).address, at: Date.now() }; } catch (e) { console.log("[launcher] dns:", e.message); }
  }
  return (launcherIp.ip || host) + ":" + (port || "7777");
}
app.get("/api/launcher/distribution.json", async (req, res) => {
  const cdn = (E.LAUNCHER_CDN || "").replace(/\/+$/, "");
  res.set("Cache-Control", "no-store");
  res.json({
    cache: cdn ? LAUNCHER_CACHE : [], cacheMode: [], projectName: "SampCity", packageName: "com.sampcity.launcher", versionHash: LAUNCHER_VERSION,
    rss: "", cdnCache: cdn, cdnLauncher: (E.LAUNCHER_APK_URL || "").replace(/\/[^/]*$/, ""), filesContinue: ["settings.ini", "gta_sa.set", "svconfig.ini"],
    launcher: { appVersion: LAUNCHER_VERSION, name: (E.LAUNCHER_APK_URL || "sampcity.apk").split("/").pop(), hash: "", bytes: 0, size: "" },
    servers: [{ id: 1, show: true, version: "1.0", icon: "https://sampcity.app/assets/icon-192.png", events: [], slot: 300, bonus: false, name: "SampCity", description: "SA-MP Android / PC", address: await launcherAddress(), sampVersion: "0.3.7" }],
  });
});
app.get("/api/launcher/news", async (req, res) => { // noticias y actualizaciones de la web, con su primera foto
  try {
    const rows = await attach(await q("SELECT p.id, p.type, p.title, p.body, p.created_at FROM web_posts p WHERE p.type IN ('news', 'update') ORDER BY p.id DESC LIMIT 10"));
    res.json(rows.map((r) => { const m = (r.media || [])[0]; return { title: r.title || "SampCity", image: m ? String(m.kind === "video" ? m.thumb || "" : m.id) : "", slug: String(r.id), description: String(r.body || "").replace(/\s+/g, " ").slice(0, 160), created_at: r.created_at }; }));
  } catch (e) { res.json([]); }
});
app.get("/api/launcher/donate", (req, res) => res.json([[], []])); // la tienda se abre en la web (sampcity.app/tienda)
// Cliente de 64 bits (SAMP-Mobile 2.10, app com.sampcity.game): otro formato para lo mismo
// Descarga de la app: sampcity.app/descargar (o /SampCity.apk) manda al APK. Por defecto es el del bucket de R2
// (LAUNCHER_CDN/SampCity.apk); LAUNCHER_APK_URL lo cambia por otra direccion.
app.get(["/descargar", "/SampCity.apk"], (req, res) => {
  const cdn = (E.LAUNCHER_CDN || "").replace(/\/+$/, "");
  const apk = E.LAUNCHER_APK_URL || (cdn ? cdn + "/SampCity.apk" : "");
  if (!apk) return res.status(404).sendFile(path.join(ROOT, "public", "404.html"));
  res.set("Cache-Control", "no-store");
  res.redirect(302, apk);
});
app.get("/api/launcher/servers.json", async (req, res) => {
  const [ip, port] = (await launcherAddress()).split(":");
  let online = 0;
  try { online = Number((await q("SELECT COALESCE(SUM(connected), 0) AS n FROM player"))[0].n) || 0; } catch (e) {}
  res.set("Cache-Control", "no-store");
  res.json({ query: [{ number: 1, name: "SampCity", ip, port: Number(port) || 7777, online, maxplayers: 300, password: false }] });
});
app.get("/api/launcher/client_config.json", (req, res) => {
  const cdn = (E.LAUNCHER_CDN || "").replace(/\/+$/, "");
  res.set("Cache-Control", "no-store");
  res.json({ client_config: { version_code: Number(E.LAUNCHER_GAME_VERSION) || 105, url_launcher: E.LAUNCHER_GAME_APK_URL || "", url_cache_files: "https://sampcity.app/api/launcher/files.json", url_files: cdn ? cdn + "/" : "" } });
});
app.get("/api/launcher/files.json", (req, res) => { // sin LAUNCHER_CDN no se manda nada que descargar
  const cdn = (E.LAUNCHER_CDN || "").replace(/\/+$/, "");
  res.set("Cache-Control", "no-store");
  res.json({ files: cdn ? LAUNCHER_CACHE.map((f) => { const p = (f.path ? f.path + "/" : "") + f.name; return { name: f.name, size: f.bytes[0], path: p, url: cdn + "/" + p }; }) : [] });
});

};
