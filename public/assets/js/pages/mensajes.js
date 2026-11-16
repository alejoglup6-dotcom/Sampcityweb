// Página: Mensajes (/mensajes) · chat privado entre jugadores. Se actualiza solo cada pocos segundos.
Object.assign(EN,{"Inicia sesión para ver tus mensajes.":"Log in to see your messages.","Nuevo mensaje":"New message","Aún no tienes conversaciones.":"You have no conversations yet.","Escribe a un amigo o a cualquier jugador.":"Write to a friend or any player.","Elige una conversación":"Pick a conversation","o empieza una nueva.":"or start a new one.","Escribe un mensaje…":"Write a message…","Enviar":"Send","Volver":"Back","Tú":"You","Buscar jugador…":"Search player…","Tus amigos":"Your friends","Sin resultados":"No results","Di hola 👋":"Say hi 👋","¿Borrar este mensaje?":"Delete this message?","Visto":"Seen","Enviado":"Sent","Borrar":"Delete","escribiendo…":"typing…","Enviar foto":"Send photo","Ver perfil":"View profile","Borrar conversación":"Delete conversation","¿Borrar esta conversación? Solo se borra para ti.":"Delete this conversation? It is only deleted for you.","No puedes escribirle a este jugador.":"You can't message this player.","📷 Foto":"📷 Photo"});
let CHU=new URLSearchParams(location.search).get("u")||"",CHL=0,CHT=0,CHD="",CHN=0,CHS=0;
const chNm=n=>esc(String(n).replace(/_/g," ")),chDt=s=>new Date(String(s).replace(" ","T"));
const chHr=s=>chDt(s).toLocaleTimeString(L==="en"?"en-US":"es-CO",{hour:"2-digit",minute:"2-digit"});
const chTxt=b=>esc(b).replace(/(https?:\/\/[^\s<]+)/g,'<a href="$1" target="_blank" rel="noopener nofollow">$1</a>').replace(/\n/g,"<br>");
const chTick=m=>m.mine?`<i class="chk" title="${t(m.id<=CHS?"Visto":"Enviado")}">${m.id<=CHS?"✓✓":"✓"}</i>`:"";
// burbujas con separador de día
function chMsgs(l){return l.map(m=>{const d=chDt(m.at).toDateString(),h=d!==CHD?`<div class="chd">${fecha(m.at)}</div>`:"";CHD=d;CHL=Math.max(CHL,Number(m.id));
 return h+`<div class="chb${m.mine?" me":""}" id="m${m.id}" data-id="${m.id}"${m.mine?` oncontextmenu="chDel(${m.id});return false" title="${t("Borrar")}: clic derecho / mantener pulsado"`:""}>${m.img?`<a href="${esc(m.img)}" target="_blank" rel="noopener"><img class="chim" loading="lazy" src="${esc(m.img)}" alt=""></a>`:""}${m.body?`<span>${chTxt(m.body)}</span>`:""}<small>${chHr(m.at)} ${chTick(m)}</small></div>`}).join("")}
const chRow=c=>`<a class="chr${c.name===CHU?" on":""}${c.unread?" un":""}" href="/mensajes?u=${encodeURIComponent(c.name)}" onclick="return chOpen('${esc(c.name)}')">${skin(c.skin,c.name)}${Number(c.connected)?'<i class="chon"></i>':""}<span><b>${chNm(c.name)}</b><small>${c.mine?t("Tú")+": ":""}${esc(c.body)}</small></span><em>${ago(c.at)}</em>${c.unread?`<i class="chu">${c.unread>99?"99+":c.unread}</i>`:""}</a>`;
const chList=l=>l.length?l.map(chRow).join(""):`<div class="che"><p class="m">${t("Aún no tienes conversaciones.")}</p><button class="btn p s" type="button" onclick="chNuevo()">${ic("mas")} ${t("Nuevo mensaje")}</button></div>`;
async function pg_chat(){clearTimeout(CHT);
 if(!U)return `<div class="card"><h3>${ic("mensajes")} ${t("Mensajes")}</h3><p class="m">${t("Inicia sesión para ver tus mensajes.")}</p><button class="btn p" style="margin-top:.7rem" onclick="login()">${t("Iniciar sesión")}</button></div>`;
 const[l,th]=await Promise.all([api("/chat"),CHU?api("/chat/"+encodeURIComponent(CHU)):0]);
 if(!l.ok)return fail(l.d.error,view);
 let pane;CHD="";CHL=0;
 if(CHU&&th.ok){const p=th.d.peer;CHS=Number(th.d.seenTo)||0;CHU=p.name;
  pane=`<header class="chh"><button class="fbib chbk" type="button" onclick="chBack()" aria-label="${t("Volver")}">‹</button><a href="/u/${encodeURIComponent(p.name)}">${skin(p.skin,p.name)}<span><b>${chNm(p.name)}</b><small id="chst">${Number(p.connected)?t("en línea"):""}</small></span></a><button class="fbib chmn" type="button" onclick="chMenu()" aria-label="${t("Opciones")}">•••</button></header>
  <div class="chm" id="chm" aria-live="polite">${th.d.msgs.length?chMsgs(th.d.msgs):`<p class="m chz">${t("Di hola 👋")}</p>`}</div>
  ${th.d.blocked?`<p class="m chbl">${t("No puedes escribirle a este jugador.")}</p>`:`<form class="chf" onsubmit="return chSend()"><label class="fbib chph" aria-label="${t("Enviar foto")}">${ic("galeria")}<input type="file" accept="image/*" hidden onchange="chFoto(this)"></label><textarea id="chi" rows="1" maxlength="1000" placeholder="${t("Escribe un mensaje…")}" aria-label="${t("Escribe un mensaje…")}"></textarea><button class="btn p" id="chs" aria-label="${t("Enviar")}">➤</button></form>`}`;window.CHP=p}
 else if(CHU)pane=`<div class="che"><p class="m">${esc(th.d.error||t("No se pudo cargar"))}</p><button class="btn s" type="button" onclick="chBack()">${t("Volver")}</button></div>`;
 else pane=`<div class="che"><span class="chi0">${ic("mensajes")}</span><h3>${t("Elige una conversación")}</h3><p class="m">${t("o empieza una nueva.")}</p><button class="btn p s" type="button" onclick="chNuevo()">${ic("mas")} ${t("Nuevo mensaje")}</button></div>`;
 setTimeout(chReady);
 return `<div class="chw${CHU?" open":""}"><aside class="chl"><div class="fbhd"><h2>${t("Mensajes")}</h2><button class="fbib" type="button" onclick="chNuevo()" aria-label="${t("Nuevo mensaje")}">${ic("mas")}</button></div><div id="chls">${chList(l.d)}</div></aside><section class="cht">${pane}</section></div>`}
function chReady(){const m=$("#chm"),i=$("#chi");if(m)m.scrollTop=m.scrollHeight;
 if(i){i.onkeydown=e=>{if(e.key==="Enter"&&!e.shiftKey){e.preventDefault();chSend()}};i.oninput=()=>{i.style.height="auto";i.style.height=Math.min(i.scrollHeight,120)+"px";const n=Date.now();if(n-(window.CHTY||0)>3000){window.CHTY=n;api("/chat/"+encodeURIComponent(CHU)+"/typing",{method:"POST",body:{}})}};if(matchMedia("(min-width:720px)").matches)i.focus()}
 CHN=0;clearTimeout(CHT);CHT=setTimeout(chPoll,4000);if(CHU)pollN()}
// sondeo: mensajes nuevos de la conversación abierta cada 4 s; la lista de conversaciones cada ~12 s
async function chPoll(){clearTimeout(CHT);if(view!=="chat"||!U)return;
 if(!document.hidden){CHN++;
  if(CHU&&$("#chm")){const r=await api("/chat/"+encodeURIComponent(CHU)+"?after="+CHL);
   if(r.ok){const st=$("#chst");if(st){st.textContent=r.d.typing?t("escribiendo…"):Number(r.d.peer.connected)?t("en línea"):"";st.classList.toggle("ty",!!r.d.typing)}const m=$("#chm"),near=m.scrollHeight-m.scrollTop-m.clientHeight<120,nu=r.d.msgs.filter(x=>!$("#m"+x.id));
    if(Number(r.d.seenTo)>CHS){CHS=Number(r.d.seenTo);document.querySelectorAll(".chb.me").forEach(b=>{const k=b.querySelector(".chk");if(k&&Number(b.dataset.id)<=CHS){k.textContent="✓✓";k.title=t("Visto")}})}
    if(nu.length){$(".chz")?.remove();m.insertAdjacentHTML("beforeend",chMsgs(nu));if(near)m.scrollTop=m.scrollHeight}}}
  if(CHN%3===0||!CHU){const l=await api("/chat");if(l.ok&&$("#chls"))$("#chls").innerHTML=chList(l.d)}}
 CHT=setTimeout(chPoll,4000)}
async function chSend(){const i=$("#chi"),b=$("#chs"),body=i.value.trim();if(!body||b.disabled)return false;b.disabled=true;
 const r=await api("/chat/"+encodeURIComponent(CHU),{method:"POST",body:{body}});b.disabled=false;
 if(!r.ok){toast((r.d&&r.d.error)||t("No se pudo cargar"));return false}
 i.value="";i.style.height="auto";const m=$("#chm");$(".chz")?.remove();if(!$("#m"+r.d.id))m.insertAdjacentHTML("beforeend",chMsgs([{id:r.d.id,mine:true,body,at:new Date().toISOString()}]));m.scrollTop=m.scrollHeight;i.focus();return false}
async function chFoto(inp){const f=inp.files[0];inp.value="";if(!f)return;let data;try{data=await pic(f)}catch{return toast(t("No pude leer esa imagen"))}const r=await api("/chat/"+encodeURIComponent(CHU),{method:"POST",body:{body:$("#chi").value.trim(),data}});if(!r.ok)return toast((r.d&&r.d.error)||t("No se pudo cargar"));$("#chi").value="";const m=$("#chm");$(".chz")?.remove();if(!$("#m"+r.d.id))m.insertAdjacentHTML("beforeend",chMsgs([{id:r.d.id,mine:true,body:"",img:r.d.img,at:new Date().toISOString()}]));m.scrollTop=m.scrollHeight}
function chMenu(){const n=esc(CHU);modal(`<div class="mm"><a class="btn" href="/u/${encodeURIComponent(CHU)}">${t("Ver perfil")}</a><button class="btn" onclick="chClear()">${t("Borrar conversación")}</button><button class="btn" onclick="cerrar();reportar('user',0,'${n}')">${t("Reportar")}</button><button class="btn" onclick="bloquear('${n}')">${t("Bloquear")}</button><button class="btn" onclick="cerrar()">${t("Cerrar")}</button></div>`)}
async function chClear(){if(!confirm(t("¿Borrar esta conversación? Solo se borra para ti.")))return;const r=await api("/chat/"+encodeURIComponent(CHU),{method:"DELETE"});cerrar();if(r.ok)chBack()}
async function chDel(id){if(!confirm(t("¿Borrar este mensaje?")))return;const r=await api("/chat/msg/"+id,{method:"DELETE"});if(r.ok&&r.d.ok)$("#m"+id)?.remove()}
function chGo(n){CHU=n;try{history.pushState(null,"",n?"/mensajes?u="+encodeURIComponent(n):"/mensajes")}catch{}show()}
function chOpen(n){cerrar();chGo(n);return false}
function chBack(){chGo("")}
addEventListener("popstate",()=>{CHU=new URLSearchParams(location.search).get("u")||"";show()});
// Nuevo mensaje: buscar un jugador o elegir un amigo
async function chNuevo(){modal(`<h3>${t("Nuevo mensaje")}</h3><input id="chq" type="search" maxlength="24" placeholder="${t("Buscar jugador…")}" aria-label="${t("Buscar jugador…")}" autocomplete="off"><div id="chrs" class="chrs"></div><button class="btn" type="button" onclick="cerrar()">${t("Cerrar")}</button>`);
 const it=p=>`<a class="chr" href="/mensajes?u=${encodeURIComponent(p.name)}" onclick="return chOpen('${esc(p.name)}')">${skin(p.skin,p.name)}<span><b>${chNm(p.name)}</b></span></a>`,box=$("#chrs");
 const fr=await api("/friends"),fl=fr.ok?fr.d.slice(0,20):[];if(!$("#chq"))return;
 const base=fl.length?`<h5>${t("Tus amigos")}</h5>`+fl.map(it).join(""):"";box.innerHTML=base;let tm=0;
 $("#chq").oninput=e=>{clearTimeout(tm);const v=e.target.value.trim();if(v.length<2){box.innerHTML=base;return}
  tm=setTimeout(async()=>{const r=await api("/users/search?q="+encodeURIComponent(v));if(!$("#chq")||$("#chq").value.trim()!==v)return;const l=(r.ok?r.d:[]).filter(p=>p.name!==U.name);box.innerHTML=l.length?l.map(it).join(""):`<p class="m">${t("Sin resultados")}</p>`},250)}}
mountPage(pg_chat);
