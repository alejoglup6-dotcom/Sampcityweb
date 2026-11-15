// Rutas de vacantes. Extraído de server.js sin cambiar su comportamiento.
module.exports = (app, { E, str, pid, MIN_POST, SKIN_COL, ADMIN_LEVELS, q, hit, me, BCAST, notify }) => {
// ---- Vacantes: puestos de staff y de facciones (/solicitar-staff) ----
// El staff (nivel VACANCY_MIN_LEVEL o más; por defecto el mismo de las noticias) abre, edita, cierra y borra vacantes y
// revisa las solicitudes; cualquier jugador con sesión iniciada puede postularse una vez por vacante.
const VAC_MIN = Number(E.VACANCY_MIN_LEVEL) || MIN_POST;
const vacStaff = async (req, res) => { const u = await me(req); if (!u || u.admin_level < VAC_MIN) { res.status(403).json({ error: "No tienes permiso" }); return null; } return u; };
const clip = (v, n) => str(v).replace(/\r/g, "").trim().slice(0, n);
const jparse = (s) => { try { const v = JSON.parse(s); return Array.isArray(v) ? v : []; } catch { return []; } };
function vacData(b) {
  const kind = b.kind === "faction" ? "faction" : "staff";
  const d = { kind, title: clip(b.title, 80), faction: kind === "faction" ? clip(b.faction, 60) : "", description: clip(b.description, 1500), requirements: clip(b.requirements, 1000),
    questions: (Array.isArray(b.questions) ? b.questions : []).map((x) => clip(x, 200).replace(/\s+/g, " ")).filter(Boolean).slice(0, 10),
    slots: Math.min(Math.max(parseInt(b.slots, 10) || 0, 0), 999), minLevel: Math.min(Math.max(parseInt(b.min_level, 10) || 0, 0), 999), needDiscord: b.need_discord ? 1 : 0 };
  if (d.title.length < 3) d.error = "Ponle un título a la vacante (mínimo 3 letras)";
  else if (kind === "faction" && d.faction.length < 2) d.error = "Escribe el nombre de la facción";
  return d;
}
app.get("/api/vacancies", async (req, res) => {
  const u = await me(req), staff = !!u && u.admin_level >= VAC_MIN;
  const rows = await q(`SELECT v.id, v.kind, v.title, v.faction, v.description, v.requirements, v.questions, v.slots, v.min_level, v.need_discord, v.open, v.created_at, v.created_by,
    (SELECT COUNT(*) FROM web_applications a WHERE a.vacancy_id = v.id) AS apps, (SELECT COUNT(*) FROM web_applications a WHERE a.vacancy_id = v.id AND a.status = 0) AS pending,
    (SELECT COUNT(*) FROM web_applications a WHERE a.vacancy_id = v.id AND a.status = 1) AS accepted
    FROM web_vacancies v ${staff && req.query.all === "1" ? "" : "WHERE v.open = 1"} ORDER BY v.open DESC, v.id DESC LIMIT 100`);
  const mine = u ? await q("SELECT vacancy_id, status, note FROM web_applications WHERE player_id = ?", [u.id]) : [];
  res.json({ staff, vacancies: rows.map((r) => ({ id: r.id, kind: r.kind, title: r.title, faction: r.faction, description: r.description, requirements: r.requirements, questions: jparse(r.questions), slots: r.slots, min_level: r.min_level, need_discord: r.need_discord, open: r.open, created_at: r.created_at, ...(staff ? { apps: Number(r.apps), pending: Number(r.pending), accepted: Number(r.accepted) } : { accepted: Number(r.accepted) }) })), mine });
});
app.post("/api/vacancies", async (req, res) => {
  const u = await vacStaff(req, res); if (!u) return;
  const d = vacData(req.body); if (d.error) return res.status(400).json({ error: d.error });
  if (!hit("v|" + u.id, 20, 3600000)) return res.status(429).json({ error: "Has abierto muchas vacantes seguidas. Espera un poco." });
  const ins = await q("INSERT INTO web_vacancies (kind, title, faction, description, requirements, questions, slots, min_level, need_discord, open, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)", [d.kind, d.title, d.faction, d.description, d.requirements, JSON.stringify(d.questions), d.slots, d.minLevel, d.needDiscord, u.name]);
  try { await q("INSERT INTO web_notifs (player_id, actor_id, type, post_id, body) SELECT id, ?, 'vac_new', NULL, ? FROM player WHERE id <> ? ORDER BY id DESC LIMIT ?", [u.id, d.title.slice(0, 120), u.id, BCAST]); } catch (e) { console.log("[notif]", e.message); }
  res.json({ ok: true, id: ins.insertId });
});
app.put("/api/vacancies/:id(\\d+)", async (req, res) => {
  const u = await vacStaff(req, res); if (!u) return;
  const id = pid(req.params.id), v = (await q("SELECT id, open FROM web_vacancies WHERE id = ?", [id]))[0];
  if (!v) return res.status(404).json({ error: "Esa vacante ya no existe" });
  if (Object.keys(req.body).length === 1 && "open" in req.body) { // solo abrir / cerrar
    await q("UPDATE web_vacancies SET open = ? WHERE id = ?", [req.body.open ? 1 : 0, id]);
    return res.json({ ok: true });
  }
  const d = vacData(req.body); if (d.error) return res.status(400).json({ error: d.error });
  await q("UPDATE web_vacancies SET kind = ?, title = ?, faction = ?, description = ?, requirements = ?, questions = ?, slots = ?, min_level = ?, need_discord = ? WHERE id = ?", [d.kind, d.title, d.faction, d.description, d.requirements, JSON.stringify(d.questions), d.slots, d.minLevel, d.needDiscord, id]);
  res.json({ ok: true });
});
app.delete("/api/vacancies/:id(\\d+)", async (req, res) => {
  const u = await vacStaff(req, res); if (!u) return;
  const id = pid(req.params.id);
  await q("DELETE FROM web_applications WHERE vacancy_id = ?", [id]);
  await q("DELETE FROM web_vacancies WHERE id = ?", [id]);
  res.json({ ok: true });
});
app.post("/api/vacancies/:id(\\d+)/apply", async (req, res) => {
  const u = await me(req); if (!u) return res.status(401).json({ error: "Inicia sesión para postularte" });
  const id = pid(req.params.id), v = (await q("SELECT id, title, questions, slots, min_level, need_discord, open FROM web_vacancies WHERE id = ?", [id]))[0];
  if (!v || !v.open) return res.status(404).json({ error: "Esa vacante ya no está abierta" });
  if ((await q("SELECT 1 FROM web_applications WHERE vacancy_id = ? AND player_id = ? LIMIT 1", [id, u.id])).length) return res.status(409).json({ error: "Ya te postulaste a esta vacante" });
  if (v.slots > 0 && Number((await q("SELECT COUNT(*) AS n FROM web_applications WHERE vacancy_id = ? AND status = 1", [id]))[0].n) >= v.slots) return res.status(409).json({ error: "Esta vacante ya está completa" });
  if (v.min_level > 0) { const lv = Number((await q("SELECT level FROM player WHERE id = ?", [u.id]))[0].level) || 0; if (lv < v.min_level) return res.status(403).json({ error: `Necesitas nivel ${v.min_level} o más para esta vacante (tienes ${lv})` }); }
  if (v.need_discord && !(await q("SELECT 1 FROM discord_links WHERE player_id = ? LIMIT 1", [u.id])).length) return res.status(403).json({ error: "Esta vacante pide tener Discord vinculado. Vincúlalo desde tu perfil." });
  if (!hit("ap|" + u.id, 10, 3600000)) return res.status(429).json({ error: "Demasiadas solicitudes seguidas. Inténtalo más tarde." });
  const qs = jparse(v.questions), given = Array.isArray(req.body.answers) ? req.body.answers : [];
  const answers = qs.map((qq, i) => ({ q: qq, a: clip(given[i], 800) }));
  if (answers.some((x) => x.a.length < 3)) return res.status(400).json({ error: "Responde todas las preguntas (mínimo 3 letras cada una)" });
  const about = clip(req.body.about, 800);
  if (!qs.length && about.length < 20) return res.status(400).json({ error: "Cuéntanos por qué quieres el puesto (mínimo 20 letras)" });
  await q("INSERT INTO web_applications (vacancy_id, player_id, name, answers, about) VALUES (?, ?, ?, ?, ?)", [id, u.id, u.name, JSON.stringify(answers), about]);
  for (const s of await q("SELECT id FROM player WHERE admin_level >= ? LIMIT 100", [VAC_MIN]).catch(() => [])) await notify(s.id, u, "vac_app", null, v.title);
  res.json({ ok: true });
});
app.get("/api/vacancies/:id(\\d+)/applications", async (req, res) => {
  if (!(await vacStaff(req, res))) return;
  const rows = await q("SELECT a.id, a.player_id, a.name, a.answers, a.about, a.status, a.note, a.reviewed_by, a.created_at, p.level, p.admin_level, p." + SKIN_COL + " AS skin FROM web_applications a LEFT JOIN player p ON p.id = a.player_id WHERE a.vacancy_id = ? ORDER BY a.status ASC, a.id DESC LIMIT 200", [pid(req.params.id)]);
  res.json(rows.map((r) => ({ ...r, answers: jparse(r.answers), rango: ADMIN_LEVELS[r.admin_level] || "Ciudadano" })));
});
app.post("/api/applications/:id(\\d+)/review", async (req, res) => { // status: 1 aceptar · 2 rechazar · 0 volver a pendiente
  const u = await vacStaff(req, res); if (!u) return;
  const id = pid(req.params.id), st = Number(req.body.status), note = clip(req.body.note, 300);
  if (![0, 1, 2].includes(st)) return res.status(400).json({ error: "Estado no válido" });
  const a = (await q("SELECT a.id, a.player_id, a.vacancy_id, v.title, v.slots FROM web_applications a JOIN web_vacancies v ON v.id = a.vacancy_id WHERE a.id = ?", [id]))[0];
  if (!a) return res.status(404).json({ error: "Esa solicitud ya no existe" });
  await q("UPDATE web_applications SET status = ?, note = ?, reviewed_by = ?, reviewed_at = NOW() WHERE id = ?", [st, note, u.name, id]);
  if (st) await notify(a.player_id, u, st === 1 ? "vac_ok" : "vac_no", null, a.title + (note ? " — " + note : ""));
  let closed = false;
  if (st === 1 && a.slots > 0 && Number((await q("SELECT COUNT(*) AS n FROM web_applications WHERE vacancy_id = ? AND status = 1", [a.vacancy_id]))[0].n) >= a.slots) { await q("UPDATE web_vacancies SET open = 0 WHERE id = ?", [a.vacancy_id]); closed = true; } // cupos llenos: se cierra sola
  res.json({ ok: true, closed });
});
};
