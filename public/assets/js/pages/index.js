// Página: SampCity RolePlay (/) · portada con disposición tipo Facebook (inicio con sesión y login)
Object.assign(EN,{"Crear cuenta nueva":"Create new account","Nombre de usuario":"Username","Crear publicación":"Create post","Opciones":"Options","Únete al Discord para soporte y eventos":"Join the Discord for support and events","Jugar ahora":"Play now"});
const fbBody=m=>{document.body.classList.toggle("fb-home",m==="home");document.body.classList.toggle("fb-login",m==="login");$("#fbi").innerHTML=""};
const fbPost=p=>{(window.PS=window.PS||{})[p.id]=p;const mine=U&&(U.canPost||((p.type==="post"||p.type==="story")&&p.author===U.name));
return `<article class="fbp"><div class="fbh">${skin(p.skin,p.author)}<div><a href="/u/${encodeURIComponent(p.author)}"><b>${esc(p.author.replace(/_/g," "))}</b></a>${tagsHtml(p)}${p.type==="post"?"":`<span class="tag">${t(TIT[p.type]||"")}</span>`}<br><a class="dt" href="/p/${p.id}"><small>${ago(p.created_at)}</small></a>${pMeta(p)}</div>${U?`<button class="fbo" onclick="opc(${p.id})" aria-label="${t("Opciones")}">•••</button>`:""}</div>${p.title?`<h3>${esc(p.title)}</h3>`:""}${p.body?`<p>${linkM(p.body)}</p>`:""}${pollHtml(p)}${mediaHtml(p)}${shareHtml(p)}<div class="fba"><button class="${p.mine>0?"p":""}" id="l${p.id}" data-k="${Number(p.mine)||0}" onclick="like(${p.id})" aria-label="${t("Me gusta")}">${rxBtn(p)}</button><button id="cb${p.id}" onclick="coms(${p.id})" aria-label="Comentarios">${ic("discord_comentarios")} <span>${p.comments}</span></button><button onclick="compartir(${p.id})" aria-label="${t("Compartir")}">${ic("compartir_vincular")}</button></div><div id="c${p.id}"></div></article>`};
async function pg_home(){fbBody("home");await info();
const wide=matchMedia("(min-width:1180px)").matches; // la columna derecha solo existe en pantallas anchas: en celular no se piden esos datos
const[a,r,fr,sg,bd]=await Promise.all([api("/posts?type=story&scope=friends"),api("/posts?type=feed&page="+PG),wide?api("/friends"):0,wide?api("/friends/suggestions"):0,wide?api("/birthdays"):0]);
if(!r.ok)return fail(r.d.error,view);
const more=r.d.length>12,l=r.d.slice(0,12),ip=SV_HOST+":"+SV_PORT;
$("#fbi").innerHTML=`<a class="fbc" href="samp://${ip}" aria-label="${t("Jugar ahora")}">${ic("jugar")}</a><button class="fbc" type="button" aria-label="${t("Copiar IP")}" onclick="navigator.clipboard.writeText('${ip}').then(()=>toast(t('Copiada')))">${ic("copiar_ip")}</button><a class="fbc" href="${DISCORD}" target="_blank" rel="noopener" aria-label="Discord">${ic("discord_comentarios")}</a>`;
fbNav();
const ct=(p,x)=>`<a class="hsb-ct" href="/u/${encodeURIComponent(p.name)}">${skin(p.skin,p.name)}<span>${esc(p.name.replace(/_/g," "))}</span>${x}</a>`,fl=fr&&fr.ok?fr.d.slice(0,12):[],sl=sg&&sg.ok?(Array.isArray(sg.d)?sg.d:sg.d.list||[]).slice(0,4):[];
const side=wide?`<aside class="hsb" aria-label="${t("Servidor")}"><section><h4>${t("Servidor")}</h4><p class="hsb-n"><b>${Number(INFO.online)||0}</b> ${t("en línea")} · <b>${Number(INFO.total)||0}</b> ${t("registrados")}</p><div class="hsb-b"><a class="btn p s" href="samp://${ip}">${ic("jugar")} ${t("Jugar ahora")}</a><button class="btn s" type="button" onclick="navigator.clipboard.writeText('${ip}').then(()=>toast(t('Copiada')))">${ic("copiar_ip")} ${t("Copiar IP")}</button></div></section>
${bd&&bd.ok&&bd.d.length?`<section><h4>🎂 ${t("Cumpleaños de hoy")}</h4>${bd.d.map(p=>ct(p,"")).join("")}</section>`:""}
<section><h4>${t("Contactos")}${fl.length?`<a href="/amigos">${t("Ver todos")}</a>`:""}</h4>${fl.length?fl.map(p=>ct(p,p.connected?`<i class="hsb-dot" title="${t("en línea")}"></i>`:"")).join(""):`<p class="m">${t("Aún no tienes amigos.")}</p><a class="btn s" href="/amigos">${t("Buscar amigos")}</a>`}</section>
${sl.length?`<section><h4>${t("Personas que quizás conozcas")}</h4>${sl.map(p=>ct(p,p.connected?`<i class="hsb-dot" title="${t("en línea")}"></i>`:"")).join("")}</section>`:""}</aside>`:"";
return `<div class="fbw"><div class="fbc2">${skin(U.skin,U.name)}<button class="fbpill" onclick="nuevo()">${t("¿Qué estás pensando?")}</button><button class="gal" type="button" onclick="nuevo(1)" aria-label="${t("Galería")}">${ic("galeria")}</button></div>
${storyStrip(a.ok?a.d:[])}${frBanner()}${U.friends?"":frEmpty(l.length)}`
+(l.length?l.map(fbPost).join("")+infList("/posts?type=feed",l,more,n=>n.map(fbPost).join("")):(U.friends?frNone():""))+`</div>${side}`}
async function pg_landing(){fbBody("login");await info();setTimeout(bindLanding);
return `<div class="fbl"><a class="fbd" href="${DISCORD}" target="_blank" rel="noopener">${ic("discord_comentarios")} ${t("Únete al Discord para soporte y eventos")}</a>
<button class="fbg" type="button" onclick="idioma()">${L==="en"?"English":"Español"}</button>
<img class="fbo2" src="/assets/logo.png" alt="SampCity">
<div class="fbf"><input id="hn" placeholder=" " autocomplete="username" autocapitalize="none" maxlength="24"><label for="hn">${t("Nombre de usuario")}</label></div>
<div class="fbf"><input id="hp" type="password" placeholder=" " autocomplete="current-password" maxlength="72"><label for="hp">${t("Contraseña")}</label></div>
${INFO.turnstile?'<div id="hcft" style="margin:.4rem 0"></div>':""}<div class="msg" id="hm" role="alert"></div>
<button class="btn p fbb" id="hb2">${t("Iniciar sesión")}</button>
<a class="fbq" href="${DISCORD}" target="_blank" rel="noopener">${t("¿Has olvidado tu contraseña?")}</a>
<button class="btn fbn" type="button" onclick="registro()">${t("Crear cuenta nueva")}</button>
<img class="fbz" src="/assets/logo.png" alt="SampCity"></div>`}
function bindLanding(){const n=$("#hn"),p=$("#hp"),b=$("#hb2");if(!n||!b)return;
 const go=async()=>{if(!need(n,n.value.trim().length>=3,t("Escribe tu nombre (mínimo 3 letras)"),"#hm")||!need(p,p.value.length>0,t("Escribe tu contraseña"),"#hm"))return;
  b.disabled=true;const r=await api("/login",{method:"POST",body:{name:n.value,password:p.value,cf:CF}});b.disabled=false;
  if(!r.ok){resetCaptcha("#hcft");$("#hm").className="msg";return $("#hm").textContent=r.d.error||"Error"}
  await init();location.href="/perfil"};
 b.onclick=go;p.onkeydown=e=>{if(e.key==="Enter")go()};if(INFO.turnstile)captcha("#hcft")}
mountPage(()=>U?pg_home():pg_landing());
