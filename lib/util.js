// Funciones puras (sin base de datos ni red): se prueban con `npm test`.
const crypto = require("crypto");

// ---- Sesión: cookie firmada (HMAC). Formato nuevo: id.vence.sid.firma · formato viejo: id.vence.firma ----
const sign = (secret, v) => crypto.createHmac("sha256", secret).update(v).digest("base64url");
function packSession(secret, id, exp, sid) {
  const v = `${id}.${exp}.${sid}`;
  return `${v}.${sign(secret, v)}`;
}
function parseSession(secret, raw, now = Date.now()) {
  const parts = String(raw || "").split(".");
  if (parts.length !== 3 && parts.length !== 4) return null;
  const sig = parts.pop(), v = parts.join("."), good = sign(secret, v);
  if (sig.length !== good.length || !crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(good))) return null;
  const [id, exp, sid = ""] = parts;
  if (!/^\d+$/.test(id) || !(Number(exp) > now) || (sid && !/^[a-f0-9]{16,32}$/.test(sid))) return null;
  return { id: Number(id), exp: Number(exp), sid };
}

// ---- Verificación en dos pasos (TOTP, RFC 6238: 6 dígitos cada 30 s, SHA-1) ----
const B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
function b32encode(buf) {
  let bits = 0, val = 0, out = "";
  for (const b of buf) { val = (val << 8) | b; bits += 8; while (bits >= 5) { out += B32[(val >>> (bits - 5)) & 31]; bits -= 5; } }
  if (bits) out += B32[(val << (5 - bits)) & 31];
  return out;
}
function b32decode(s) {
  let bits = 0, val = 0; const out = [];
  for (const c of String(s).toUpperCase().replace(/[^A-Z2-7]/g, "")) { val = (val << 5) | B32.indexOf(c); bits += 5; if (bits >= 8) { out.push((val >>> (bits - 8)) & 255); bits -= 8; } }
  return Buffer.from(out);
}
function totp(secret, t = Date.now(), step = 0) {
  const c = Buffer.alloc(8); c.writeBigUInt64BE(BigInt(Math.floor(t / 30000) + step));
  const h = crypto.createHmac("sha1", b32decode(secret)).update(c).digest(), o = h[19] & 15;
  return String(((h.readUInt32BE(o) & 0x7fffffff) % 1e6)).padStart(6, "0");
}
// acepta el código actual y los de ±30 s (relojes algo desfasados)
function totpOk(secret, code, t = Date.now()) {
  code = String(code || "").replace(/\s+/g, "");
  if (!/^\d{6}$/.test(code) || !secret) return false;
  return [-1, 0, 1].some((s) => crypto.timingSafeEqual(Buffer.from(totp(secret, t, s)), Buffer.from(code)));
}

// ---- Filtro de palabras: sin tildes ni mayúsculas, por palabra completa ----
const plain = (s) => String(s).toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "");
function badWord(text, words) {
  if (!words || !words.length) return "";
  const toks = new Set(plain(text).split(/[^a-z0-9ñ]+/).filter(Boolean)), flat = " " + plain(text).replace(/[^a-z0-9ñ]+/g, " ").trim() + " ";
  for (const w of words) { const p = plain(w).trim(); if (!p) continue; if (p.includes(" ") ? flat.includes(" " + p + " ") : toks.has(p)) return w; }
  return "";
}

// ---- #etiquetas de un texto (para tendencias) ----
const hashtags = (text) => [...new Set([...String(text).matchAll(/(?:^|\s)#([\p{L}\d_]{2,30})/gu)].map((m) => m[1].toLowerCase()))];

// ---- Vista previa de enlaces: direcciones que NO se pueden consultar (red interna, localhost…) ----
function privateIp(ip) {
  ip = String(ip).toLowerCase();
  const m4 = /^(?:::ffff:)?(\d+)\.(\d+)\.(\d+)\.(\d+)$/.exec(ip);
  if (m4) {
    const [a, b] = [Number(m4[1]), Number(m4[2])];
    return a === 0 || a === 10 || a === 127 || (a === 100 && b >= 64 && b <= 127) || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168) || (a === 192 && b === 0) || (a === 198 && (b === 18 || b === 19)) || a >= 224;
  }
  return ip === "::" || ip === "::1" || /^f[cd]/.test(ip) || /^fe[89ab]/.test(ip) || ip.startsWith("::ffff:") || ip.startsWith("64:ff9b:") || !ip.includes(":");
}
// saca título, descripción e imagen de un HTML (Open Graph, con <title> de respaldo)
function ogParse(html, base) {
  const dec = (s) => String(s || "").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#0?39;|&apos;/g, "'").replace(/&#(\d+);/g, (m, n) => String.fromCodePoint(Number(n) || 32)).replace(/\s+/g, " ").trim();
  const meta = (k) => { for (const tag of html.match(/<meta\b[^>]*>/gi) || []) { if (new RegExp(`(?:property|name)\\s*=\\s*["']${k}["']`, "i").test(tag)) { const c = /content\s*=\s*(?:"([^"]*)"|'([^']*)')/i.exec(tag); if (c) return dec(c[1] ?? c[2]); } } return ""; };
  const title = meta("og:title") || dec((/<title[^>]*>([^<]{1,300})/i.exec(html) || [])[1]), descr = meta("og:description") || meta("description");
  let img = meta("og:image") || meta("twitter:image");
  try { img = img ? new URL(img, base).href : ""; } catch { img = ""; }
  return { title: title.slice(0, 140), descr: descr.slice(0, 240), img: /^https:\/\//.test(img) && img.length <= 500 ? img : "" };
}

module.exports = { sign, packSession, parseSession, b32encode, b32decode, totp, totpOk, plain, badWord, hashtags, privateIp, ogParse };
