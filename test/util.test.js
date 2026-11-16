// Pruebas de las funciones puras (npm test). No necesitan base de datos.
const test = require("node:test"), assert = require("node:assert");
const u = require("../lib/util");

test("sesión: se firma, se lee y no acepta manipulación", () => {
  const S = "secreto-de-prueba-0123456789", exp = Date.now() + 1000, c = u.packSession(S, 42, exp, "0123456789abcdef");
  assert.deepStrictEqual(u.parseSession(S, c), { id: 42, exp, sid: "0123456789abcdef" });
  assert.strictEqual(u.parseSession(S, c.replace(/^42/, "43")), null, "cambiar el id invalida la firma");
  assert.strictEqual(u.parseSession("otro-secreto", c), null);
  assert.strictEqual(u.parseSession(S, u.packSession(S, 42, Date.now() - 1, "0123456789abcdef")), null, "caducada");
  const old = `7.${exp}`; // formato anterior (sin sid) sigue valiendo
  assert.deepStrictEqual(u.parseSession(S, `${old}.${u.sign(S, old)}`), { id: 7, exp, sid: "" });
  assert.strictEqual(u.parseSession(S, ""), null);
});

test("TOTP: vector del RFC 6238 y tolerancia de ±30 s", () => {
  const secret = u.b32encode(Buffer.from("12345678901234567890"));
  assert.strictEqual(secret, "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
  assert.strictEqual(u.totp(secret, 59000), "287082"); // RFC 6238, T = 59 s (8 dígitos: 94287082)
  assert.ok(u.totpOk(secret, "287082", 59000));
  assert.ok(u.totpOk(secret, "287 082", 59000 + 30000), "código del paso anterior");
  assert.ok(!u.totpOk(secret, "287082", 59000 + 120000));
  assert.ok(!u.totpOk(secret, "abc", 59000) && !u.totpOk("", "287082", 59000));
  assert.deepStrictEqual(u.b32decode(secret), Buffer.from("12345678901234567890"));
});

test("filtro de palabras: palabra completa, sin tildes ni mayúsculas", () => {
  const w = ["tonto", "mala frase"];
  assert.strictEqual(u.badWord("Eres un TÓNTO!", w), "tonto");
  assert.strictEqual(u.badWord("una mala   frase aquí", w), "mala frase");
  assert.strictEqual(u.badWord("tontolaba no cuenta", w), "", "no corta palabras");
  assert.strictEqual(u.badWord("todo bien", w), "");
  assert.strictEqual(u.badWord("tonto", []), "");
});

test("hashtags", () => {
  assert.deepStrictEqual(u.hashtags("Hola #Evento y #evento, #ñandú_1 pero no a#b ni #x"), ["evento", "ñandú_1"]);
});

test("vista previa: direcciones internas bloqueadas", () => {
  for (const ip of ["127.0.0.1", "10.1.2.3", "192.168.0.1", "172.16.5.5", "169.254.169.254", "100.64.0.1", "0.0.0.0", "::1", "fd00::1", "fe80::1", "::ffff:10.0.0.1", "::ffff:7f00:1", "basura"]) assert.ok(u.privateIp(ip), ip);
  for (const ip of ["8.8.8.8", "172.32.0.1", "2606:4700::1111", "100.63.0.1"]) assert.ok(!u.privateIp(ip), ip);
});

test("vista previa: lee Open Graph y descarta imágenes que no son https", () => {
  const o = u.ogParse(`<html><head><title>Respaldo</title><meta property="og:title" content="Hola &amp; adiós"><meta name="description" content='Una "descripción"'><meta content="/img/a.png" property="og:image"></head>`, "https://ejemplo.com/pagina");
  assert.deepStrictEqual(o, { title: "Hola & adiós", descr: 'Una "descripción"', img: "https://ejemplo.com/img/a.png" });
  assert.strictEqual(u.ogParse(`<title>Solo título</title><meta property="og:image" content="http://x.com/a.png">`, "https://x.com/").img, "");
  assert.strictEqual(u.ogParse(`<title>Solo título</title>`, "https://x.com/").title, "Solo título");
});
