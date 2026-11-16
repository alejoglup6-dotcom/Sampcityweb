// Service worker de SampCity: notificaciones push y página «sin conexión».
// No guarda el JS/CSS de la web (siempre se piden al servidor): así una versión nueva nunca queda atascada.
const CACHE = "sc-off-1", OFFLINE = "/offline.html", KEEP = [OFFLINE, "/assets/logo.png", "/assets/favicon.png"];
self.addEventListener("install", (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(KEEP)).then(() => self.skipWaiting())); });
self.addEventListener("activate", (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener("fetch", (e) => {
  const r = e.request;
  if (r.method !== "GET") return;
  if (r.mode === "navigate") { e.respondWith(fetch(r).catch(() => caches.match(OFFLINE))); return; } // sin red: página propia en vez del error del navegador
  const u = new URL(r.url);
  if (u.origin === location.origin && KEEP.includes(u.pathname)) e.respondWith(caches.match(r).then((c) => c || fetch(r)));
});
self.addEventListener("push", (e) => {
  let d = {}; try { d = e.data ? e.data.json() : {}; } catch {}
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((cs) => {
    if (cs.some((c) => c.focused)) return; // con la web abierta y a la vista ya llega el aviso en vivo
    return self.registration.showNotification(d.title || "SampCity", { body: d.body || "", tag: d.tag || undefined, icon: "/assets/icon-192.png", badge: "/assets/icon-192.png", data: { url: d.url || "/notificaciones" } });
  }));
});
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  const url = new URL((e.notification.data && e.notification.data.url) || "/", location.origin).href;
  e.waitUntil(self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((cs) => { const c = cs.find((x) => "focus" in x); return c ? c.navigate(url).then((w) => (w || c).focus()).catch(() => self.clients.openWindow(url)) : self.clients.openWindow(url); }));
});
