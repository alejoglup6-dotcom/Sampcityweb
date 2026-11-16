// App Android (APK): descarga, datos de la versión y la verificación de dominio (Digital Asset Links) que usa la app para abrirse sin barra de direcciones.
// El APK está en public/downloads/SampCity-App.apk (lo genera android/build.sh junto con data/app.json).
// Ojo: /descargar y /SampCity.apk son del launcher del juego; esta es otra app.
const fs = require("fs"), path = require("path");
module.exports = (app, { E, ROOT }) => {
const APK = path.join(ROOT, "public", "downloads", "SampCity-App.apk"), read = (f) => { try { return JSON.parse(fs.readFileSync(path.join(ROOT, "data", f), "utf8")); } catch { return {}; } };
// /.well-known/assetlinks.json: huella de la llave con la que se firma el APK (data/android.json; ANDROID_SHA256 añade más, p. ej. la de Google Play)
app.get("/.well-known/assetlinks.json", (req, res) => {
  const a = read("android.json"), fp = [...new Set([...(a.sha256 || []), ...String(E.ANDROID_SHA256 || "").split(",").map((s) => s.trim()).filter(Boolean)].map((s) => s.toUpperCase()))];
  res.set("Cache-Control", "public, max-age=300").type("application/json").send(JSON.stringify(fp.length ? [{ relation: ["delegate_permission/common.handle_all_urls"], target: { namespace: "android_app", package_name: a.package || "com.sampcity.social", sha256_cert_fingerprints: fp } }] : []));
});
app.get("/api/app", (req, res) => {
  let st = null; try { st = fs.statSync(APK); } catch {}
  const v = read("app.json");
  res.json(st ? { available: true, version: v.versionName || "", code: v.versionCode || 0, size: st.size, sha256: v.sha256 || "", updated: st.mtime.toISOString(), minAndroid: "7.0" } : { available: false });
});
app.get(["/SampCity-App.apk", "/descargar-app"], (req, res) => {
  if (!fs.existsSync(APK)) return res.status(404).sendFile(path.join(ROOT, "public", "404.html"));
  res.set({ "Content-Type": "application/vnd.android.package-archive", "Content-Disposition": 'attachment; filename="SampCity.apk"', "Cache-Control": "no-cache", "X-Content-Type-Options": "nosniff" });
  res.sendFile(APK, { dotfiles: "deny", headers: {} });
});
};
