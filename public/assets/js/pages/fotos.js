// Página: Fotos (/fotos)
const phBtn=(p,i)=>`<button data-on-click="foto(${i})"><img class="bg" aria-hidden="true" alt="" decoding="async" loading="lazy" src="${esc(p.body)}"><img loading="lazy" decoding="async" src="${esc(p.body)}" alt="${esc(p.title)}"><small>${esc(p.author.replace(/_/g," "))}</small></button>`;
const phOk=p=>/^(https:\/\/|\/img\/\d+$)/.test(p.body);
async function pg_fotos(){const r=await api("/posts?type=photo&page="+PG);if(!r.ok)return fail(r.d.error,"fotos");const more=r.d.length>12,all=r.d.slice(0,12),ok=all.filter(phOk);window.PH=ok;const seen=new Set(all.map(p=>p.id)),sen=ok.length?inf(async pg=>{const x=await api("/posts?type=photo&page="+pg);if(!x.ok)return null;const n=x.d.slice(0,12).filter(p=>!seen.has(p.id));n.forEach(p=>seen.add(p.id));const g=n.filter(phOk),b=window.PH.length;window.PH.push(...g);return{h:g.map((p,i)=>phBtn(p,b+i)).join(""),more:x.d.length>12}},more):"";
 return (U?.canPost?form("photo"):"")+`<h2 class="ttl">${t("Fotos de la comunidad")}</h2>${ok.length?`<div class="gal two">${ok.map(phBtn).join("")}${sen}</div>`:`<p class="m">${t("Aún no hay fotos. El staff las publica desde aquí.")}</p>`}`+cta()}

mountPage(()=>pg_fotos());
