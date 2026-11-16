// Prueba de integración: recorre la API contra un servidor en marcha con una base de datos de prueba VACÍA.
//   node test/smoke.js            (usa http://localhost:3000; cambia con BASE=...)
// Necesita MYSQL_* en el entorno para ascender a un jugador a staff. No la corras contra la base real.
require("dotenv").config();
const assert = require("node:assert"), mysql = require("mysql2/promise");
const { totp } = require("../lib/util");
const BASE = process.env.BASE || "http://localhost:3000";
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==", "base64");
class User {
  constructor(name) { this.name = name; this.cookie = ""; }
  async raw(method, path, body, headers = {}) {
    const r = await fetch(BASE + path, { method, redirect: "manual", headers: { ...(this.cookie ? { Cookie: this.cookie } : {}), ...(body !== undefined && !Buffer.isBuffer(body) ? { "Content-Type": "application/json" } : {}), ...headers }, body: body === undefined ? undefined : Buffer.isBuffer(body) ? body : JSON.stringify(body) });
    for (const c of r.headers.getSetCookie()) if (c.startsWith("sc=")) this.cookie = c.split(";")[0] === "sc=" ? "" : c.split(";")[0];
    return r;
  }
  async api(method, path, body) { const r = await this.raw(method, "/api" + path, body ?? (method === "GET" ? undefined : {})); return { st: r.status, d: await r.json().catch(() => null) }; }
  get(p) { return this.api("GET", p); } post(p, b) { return this.api("POST", p, b); } put(p, b) { return this.api("PUT", p, b); } del(p, b) { return this.api("DELETE", p, b); }
  async upload(kind = "img") { const r = await this.raw("POST", "/api/media?kind=" + kind, PNG, { "Content-Type": "image/png", "X-SC": "1" }); return (await r.json()).id; }
}
let n = 0; const ok = (c, m) => { assert.ok(c, m); n++; }, eq = (a, b, m) => { assert.deepStrictEqual(a, b, m); n++; };
(async () => {
  const db = await mysql.createConnection({ host: process.env.MYSQL_HOST, port: Number(process.env.MYSQL_PORT) || 3306, user: process.env.MYSQL_USER, password: process.env.MYSQL_PASSWORD, database: process.env.MYSQL_DATABASE });
  const tag = Date.now().toString(36).replace(/\d/g, "").slice(-4) || "x", nm = (s) => `${s}_T${tag}`.replace(/_T(.)/, (m, c) => "_T" + c.toLowerCase());
  const A = new User(nm("Ana")), B = new User(nm("Beto")), C = new User(nm("Caro")), anon = new User("");
  for (const u of [A, B, C]) eq((await u.post("/register", { name: u.name, email: `${u.name.toLowerCase()}@ej.com`.slice(0, 31), password: "secreta1" })).st, 200, "registro " + u.name);
  await db.query("UPDATE player SET admin_level = 5 WHERE name = ?", [C.name]); // Caro es staff
  ok((await A.get("/me")).d.user.name === A.name, "sesión de Ana");

  // --- amistad A-B ---
  eq((await A.post("/friends/request", { name: B.name })).st, 200); eq((await B.post("/friends/accept", { name: A.name })).st, 200);

  // --- privacidad de fotos: publicación «solo amigos» ---
  const m1 = await A.upload(); ok(m1 > 0, "sube foto");
  eq((await C.raw("GET", "/media/" + m1)).status, 404, "foto sin publicar: solo su dueña");
  const p1 = (await A.post("/wall", { body: "Solo amigos #prueba", media: [m1], vis: 1 })).d.id; ok(p1 > 0);
  eq((await A.raw("GET", "/media/" + m1)).status, 200, "la dueña la ve");
  eq((await B.raw("GET", "/media/" + m1)).status, 200, "su amigo la ve");
  eq((await anon.raw("GET", "/media/" + m1)).status, 404, "sin sesión no se ve");
  const C2 = new User(nm("Dani")); eq((await C2.post("/register", { name: C2.name, email: `${C2.name.toLowerCase()}@ej.com`.slice(0, 31), password: "secreta1" })).st, 200);
  eq((await C2.raw("GET", "/media/" + m1)).status, 404, "un extraño no la ve");
  eq((await C2.get(`/user/${A.name}/photos`)).d.length, 0, "ni en la pestaña Fotos");
  eq((await B.get(`/user/${A.name}/photos`)).d.length, 1, "el amigo sí");
  const m2 = await A.upload(), p2 = (await A.post("/wall", { body: "Pública #prueba #hola", media: [m2] })).d.id;
  const pub = await anon.raw("GET", "/media/" + m2); eq(pub.status, 200, "foto pública"); ok(/public/.test(pub.headers.get("cache-control")));

  // --- feed: cursor, «Para ti» y tendencias ---
  ok((await B.get("/posts?type=feed&before=" + (p2 + 1))).d.some((p) => p.id === p2), "feed con cursor");
  ok(!(await B.get("/posts?type=feed&before=" + p2)).d.some((p) => p.id === p2), "el cursor excluye lo ya visto");
  ok((await C2.get("/posts?type=foryou&page=0")).d.some((p) => p.id === p2), "Para ti muestra lo público");
  ok(!(await C2.get("/posts?type=foryou&page=0")).d.some((p) => p.id === p1), "Para ti respeta la privacidad");
  ok((await anon.get("/trends")).d.some((t) => t.tag === "hola"), "tendencias");

  // --- chat privado: foto, respuesta, reacción, búsqueda, borrado ---
  const data = "data:image/png;base64," + PNG.toString("base64");
  const c1 = (await A.post("/chat/" + B.name, { body: "hola beto", data })).d; ok(c1.id > 0 && c1.m.img, "mensaje con foto");
  eq((await B.raw("GET", c1.m.img)).status, 200, "el destinatario ve la foto"); eq((await C2.raw("GET", c1.m.img)).status, 404, "un tercero no"); eq((await anon.raw("GET", c1.m.img)).status, 404);
  const c2 = (await B.post("/chat/" + A.name, { body: "respuesta", reply: c1.id })).d; eq(c2.m.reply.id, c1.id, "responder a un mensaje");
  eq((await C2.post(`/chat/msg/${c1.id}/react`, { kind: 2 })).st, 404, "no se reacciona a chats ajenos");
  eq((await B.post(`/chat/msg/${c1.id}/react`, { kind: 2 })).d.rx, { 2: 1 }, "reacción");
  const th = (await A.get("/chat/" + B.name)).d; eq(th.msgs.find((m) => m.id === c1.id).rx, { 2: 1 }); eq(th.msgs.length, 2);
  eq((await A.get("/chat/search?q=respuesta")).d.length, 1, "búsqueda en mensajes"); eq((await C2.get("/chat/search?q=respuesta")).d.length, 0);
  const aud = "data:audio/webm;codecs=opus;base64," + Buffer.concat([Buffer.from([0x1a, 0x45, 0xdf, 0xa3]), Buffer.alloc(40)]).toString("base64");
  const c3 = (await A.post("/chat/" + B.name, { data: aud })).d; ok(c3.m.aud, "audio");
  const rng = await B.raw("GET", c3.m.aud, undefined, { Range: "bytes=0-3" }); eq(rng.status, 206, "audio por rangos"); eq(rng.headers.get("content-type"), "audio/webm");
  eq((await A.del("/chat/msg/" + c1.id)).d.ok, true); eq((await B.raw("GET", c1.m.img)).status, 404, "al borrar el mensaje se borra su foto");

  // --- chat de grupo ---
  const room = (await A.post("/rooms", { name: "Los de prueba", members: [B.name, C.name] })).d.id; ok(room > 0, "crea grupo");
  const r1 = (await B.post("/rooms/" + room, { body: "hola grupo", data })).d; ok(r1.m.from === B.name && r1.m.img);
  eq((await C.raw("GET", r1.m.img)).status, 200, "miembro ve la foto del grupo"); eq((await C2.raw("GET", r1.m.img)).status, 404, "quien no es miembro no");
  eq((await C2.get("/rooms/" + room)).st, 404); eq((await C2.post("/rooms/" + room, { body: "x" })).st, 404);
  eq((await A.get("/rooms")).d[0].unread, 1, "no leídos del grupo"); eq((await A.get("/notifications/count")).d.m >= 1, true);
  eq((await A.get("/rooms/" + room)).d.msgs.length, 1); eq((await A.get("/rooms")).d[0].unread, 0);
  eq((await B.del(`/rooms/${room}/members/${C.name}`)).st, 403, "solo el creador saca gente"); eq((await A.del(`/rooms/${room}/members/${C.name}`)).st, 200);
  eq((await C.get("/rooms/" + room)).st, 404, "expulsado ya no entra");

  // --- sesiones, contraseña y dos pasos ---
  const B2 = new User(B.name); eq((await B2.post("/login", { name: B.name, password: "secreta1" })).st, 200, "segundo dispositivo");
  const ses = (await B.get("/settings/sessions")).d; eq(ses.length, 2, "dos sesiones abiertas"); ok(ses.some((s) => s.current));
  eq((await B.del("/settings/sessions/" + ses.find((s) => !s.current).id)).st, 200); ok(!(await B2.get("/me")).d.user, "sesión cerrada a distancia");
  eq((await B2.post("/login", { name: B.name, password: "secreta1" })).st, 200);
  eq((await B.post("/settings/password", { current: "secreta1", next: "x".repeat(19) })).st, 400, "máximo 18");
  eq((await B.post("/settings/password", { current: "secreta1", next: "nueva123" })).st, 200);
  ok(!(await B2.get("/me")).d.user, "cambiar la contraseña cierra las otras sesiones"); ok((await B.get("/me")).d.user, "esta sigue abierta");
  const tf = (await B.post("/settings/2fa/setup")).d; ok(tf.secret.length >= 16);
  eq((await B.post("/settings/2fa/enable", { code: "000000" })).st, 400); eq((await B.post("/settings/2fa/enable", { code: totp(tf.secret) })).st, 200);
  const l1 = await B2.post("/login", { name: B.name, password: "nueva123" }); eq(l1.st, 401); eq(l1.d.totp, true, "pide el código");
  eq((await B2.post("/login", { name: B.name, password: "nueva123", code: totp(tf.secret) })).st, 200, "entra con el código");
  eq((await B.post("/settings/2fa/disable", { password: "nueva123" })).st, 200);
  await B2.post("/logout"); ok(!(await B2.get("/me")).d.user);

  // --- moderación ---
  eq((await A.get("/admin/stats")).st, 403, "panel solo para staff"); ok((await C.get("/admin/stats")).d.totals.jugadores >= 4);
  eq((await C.post("/admin/words", { words: "palabrota" })).st, 200);
  eq((await A.post("/wall", { body: "esto es una PALABROTA" })).st, 400, "filtro de palabras en publicaciones"); eq((await A.post("/chat/" + B.name, { body: "palabrota" })).st, 400, "y en el chat");
  eq((await C.del("/admin/words", { word: "palabrota" })).st, 200);
  eq((await C.post("/admin/bans", { player: A.name, hours: 2, reason: "prueba" })).st, 200);
  const bn = await A.post("/wall", { body: "no debería salir" }); eq(bn.st, 403); eq(bn.d.banned, true, "suspendido no publica"); ok((await A.get("/me")).d.user.banned > 0);
  eq((await C.del("/admin/bans/" + A.name)).st, 200); eq((await A.post("/wall", { body: "ya puedo" })).st, 200);
  eq((await C.post("/admin/verified/" + A.name)).st, 200); ok((await anon.get("/info")).d.verified.includes(A.name), "verificado");
  eq((await C.del("/posts/" + p2)).st, 200); ok((await C.get("/admin/log")).d.some((l) => l.action === "borrar publicación"), "registro del staff");
  eq((await anon.raw("GET", "/media/" + m2)).status, 404, "medio borrado con su publicación");

  // --- datos y eliminación ---
  const ex = await A.raw("GET", "/api/settings/export"); eq(ex.status, 200); ok((await ex.json()).publicaciones.length >= 1, "descarga de datos");
  eq((await A.post("/settings/delete", { password: "mal" })).st, 403); eq((await A.post("/settings/delete", { password: "secreta1" })).st, 200);
  ok(!(await A.get("/me")).d.user, "sale de la sesión"); eq((await B.get("/posts?author=" + A.name)).d.length, 0, "sin publicaciones"); eq((await B.get("/friends")).d.length, 0, "sin amistades");
  eq((await A.post("/login", { name: A.name, password: "secreta1" })).st, 200, "la cuenta del juego sigue existiendo");

  // --- varios ---
  eq((await anon.get("/stream")).st, 401); eq((await anon.get("/preview?u=" + encodeURIComponent("https://127.0.0.1/"))).d, {}, "vista previa no consulta direcciones internas");
  eq((await anon.post("/recover", { name: A.name })).st, 503, "sin SMTP la recuperación avisa");
  const pg = await anon.raw("GET", "/"); ok(/script-src-elem/.test(pg.headers.get("content-security-policy")));
  await db.end();
  console.log(`OK: ${n} comprobaciones`);
})().catch((e) => { console.error("FALLÓ:", e.message, "\n", e.stack.split("\n").slice(1, 4).join("\n")); process.exit(1); });
