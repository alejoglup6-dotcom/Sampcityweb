// Página: Marketplace (/marketplace) · anuncios de compra y venta entre jugadores.
// La web publica el anuncio; el trato y la entrega se hacen dentro del juego.
Object.assign(EN,{"Vender":"Sell","Para ti":"For you","Mis anuncios":"My listings","Todo":"All","Vehículos":"Vehicles","Propiedades":"Properties","Negocios":"Businesses","Objetos":"Items","Buscar en Marketplace":"Search Marketplace","Vendido":"Sold","A convenir":"Negotiable","Aún no hay anuncios aquí.":"No listings here yet.","No encontramos anuncios con esa búsqueda.":"No listings match that search.","Publica el primero: vehículos, casas, negocios u objetos del servidor.":"Post the first one: vehicles, houses, businesses or server items.","Publicar anuncio":"Post listing","Nuevo anuncio":"New listing","Categoría":"Category","Título":"Title","Precio":"Price","Descripción (opcional)":"Description (optional)","Foto (opcional)":"Photo (optional)","Elige una categoría":"Choose a category","Escribe un título (mínimo 3 letras)":"Enter a title (at least 3 letters)","Escribe un precio válido":"Enter a valid price","Anuncio publicado":"Listing posted","Contactar al vendedor":"Contact seller","Marcar como vendido":"Mark as sold","Volver a publicar":"Relist","¿Eliminar este anuncio?":"Delete this listing?","Anuncio eliminado":"Listing deleted","El trato y la entrega se hacen dentro del juego.":"The deal and delivery happen in-game.","Publicando…":"Posting…","Quitar búsqueda":"Clear search"});
const MKC=[["","Todo","marketplace"],["vehiculos","Vehículos","jugar"],["propiedades","Propiedades","inicio"],["negocios","Negocios","marketplace"],["objetos","Objetos","galeria"]];
const MK={cat:"",q:"",mine:0,find:0},MKI={};
const mkCat=c=>MKC.find(x=>x[0]===c)||MKC[0],mkPrice=p=>p>0?money(p):t("A convenir");
const mkUrl=()=>"/market?cat="+MK.cat+"&q="+encodeURIComponent(MK.q)+(MK.mine?"&mine=1":"");
const mkCard=p=>{MKI[p.id]=p;const sold=Number(p.status)===2;
 return `<button type="button" class="mkc${sold?" sold":""}" onclick="mkVer(${p.id})">${p.img?`<img loading="lazy" decoding="async" src="${esc(p.img)}" alt="">`:`<span class="mkph">${ic(mkCat(p.cat)[2])}</span>`}${sold?`<i>${t("Vendido")}</i>`:""}<b>${mkPrice(p.price)}</b><span>${esc(p.title)}</span><small>${esc(p.seller.replace(/_/g," "))}</small></button>`};
async function pg_market(){
 const r=await api(mkUrl()+"&page=0");if(!r.ok)return fail(r.d.error,view);
 const more=r.d.length>12,l=r.d.slice(0,12);
 return `<div class="fbw2 mkw"><div class="fbhd"><h2>${t("Marketplace")}</h2><button class="fbib" type="button" onclick="mkBuscar()" aria-label="${t(MK.find||MK.q?"Quitar búsqueda":"Buscar")}" aria-pressed="${MK.find||MK.q?"true":"false"}">${ic("buscar")}</button></div>
 ${MK.find||MK.q?`<form class="mkf" onsubmit="return mkQ()"><input id="mkq" type="search" maxlength="40" value="${esc(MK.q)}" placeholder="${t("Buscar en Marketplace")}" aria-label="${t("Buscar en Marketplace")}"><button class="btn p s">${t("Buscar")}</button></form>`:""}
 <div class="fpl"><button type="button" onclick="mkVender()">${ic("mas")} ${t("Vender")}</button><button type="button" class="${MK.mine?"":"on"}" onclick="mkSet({mine:0})">${t("Para ti")}</button>${U?`<button type="button" class="${MK.mine?"on":""}" onclick="mkSet({mine:1})">${t("Mis anuncios")}</button>`:""}</div>
 <div class="mkcat" role="group" aria-label="${t("Categoría")}">${MKC.map(c=>`<button type="button" class="${MK.cat===c[0]?"on":""}" onclick="mkSet({cat:'${c[0]}'})">${ic(c[2])} ${t(c[1])}</button>`).join("")}</div>
 ${l.length?`<div class="mkl">${l.map(mkCard).join("")}${infList(mkUrl(),l,more,n=>n.map(mkCard).join(""))}</div>`
 :`<section class="mks">${ic("marketplace")}<h3>${t(MK.q?"No encontramos anuncios con esa búsqueda.":"Aún no hay anuncios aquí.")}</h3><p class="m">${t("Publica el primero: vehículos, casas, negocios u objetos del servidor.")}</p><button class="btn p" type="button" onclick="mkVender()">${ic("mas")} ${t("Publicar anuncio")}</button></section>`}
 <p class="m mkn">${t("El trato y la entrega se hacen dentro del juego.")}</p></div>`}
function mkSet(o){Object.assign(MK,o);show()}
function mkBuscar(){if(MK.find||MK.q){MK.find=0;MK.q="";show()}else{MK.find=1;show().then(()=>{const i=$("#mkq");i&&i.focus()})}}
function mkQ(){MK.q=$("#mkq").value.trim();show();return false}
// Detalle de un anuncio
function mkVer(id){const p=MKI[id];if(!p)return;const sold=Number(p.status)===2,own=p.mine,staff=U&&U.canPost;
 modal(`<div class="mkd">${p.img?`<img src="${esc(p.img)}" alt="${esc(p.title)}">`:""}<p class="mkp">${mkPrice(p.price)}${sold?` <span class="pill">${t("Vendido")}</span>`:""}</p><h3>${esc(p.title)}</h3><p class="m">${ic(mkCat(p.cat)[2])} ${t(mkCat(p.cat)[1])} · ${ago(p.created_at)}</p>${p.body?`<p class="mkb">${esc(p.body).replace(/\n/g,"<br>")}</p>`:""}
 <a class="mks2" href="/u/${encodeURIComponent(p.seller)}">${skin(p.skin,p.seller)}<b>${esc(p.seller.replace(/_/g," "))}</b>${Number(p.connected)?`<small>${t("en línea")}</small>`:""}</a><p class="m mkn">${t("El trato y la entrega se hacen dentro del juego.")}</p>
 <div class="mka">${own?`<button class="btn p" type="button" onclick="mkSold(${id})">${t(sold?"Volver a publicar":"Marcar como vendido")}</button>`:`<a class="btn p" href="${U?"/mensajes?u="+encodeURIComponent(p.seller):"/u/"+encodeURIComponent(p.seller)}">${ic("mensajes")} ${t("Contactar al vendedor")}</a>`}${own||staff?`<button class="btn" type="button" onclick="mkDel(${id})">${t("Eliminar")}</button>`:""}<button class="btn" type="button" onclick="cerrar()">${t("Cerrar")}</button></div></div>`,true)}
async function mkSold(id){const r=await api("/market/"+id+"/sold",{method:"POST",body:{}});if(!r.ok)return toast(r.d.error||t("No se pudo cargar"));cerrar();show()}
async function mkDel(id){if(!confirm(t("¿Eliminar este anuncio?")))return;const r=await api("/market/"+id,{method:"DELETE"});if(!r.ok)return toast(r.d.error||t("No se pudo cargar"));cerrar();toast(t("Anuncio eliminado"));show()}
// Publicar un anuncio
function mkVender(){if(!U)return login();
 modal(`<h3>${t("Nuevo anuncio")}</h3><div class="mkform"><label for="mkc">${t("Categoría")}</label><select id="mkc">${MKC.slice(1).map(c=>`<option value="${c[0]}"${MK.cat===c[0]?" selected":""}>${t(c[1])}</option>`).join("")}</select>
 <label for="mkt">${t("Título")}</label><input id="mkt" maxlength="80" autocomplete="off"><label for="mkpr">${t("Precio")}</label><input id="mkpr" inputmode="numeric" maxlength="13" placeholder="$" autocomplete="off">
 <label for="mkbd">${t("Descripción (opcional)")}</label><textarea id="mkbd" maxlength="600" rows="3"></textarea><label for="mkfi">${t("Foto (opcional)")}</label><input id="mkfi" type="file" accept="image/*"></div>
 <div class="msg" id="mkm" role="alert"></div><button class="btn p" id="mkgo" type="button">${t("Publicar anuncio")}</button> <button class="btn" type="button" onclick="cerrar()">${t("Cancelar")}</button>`);
 $("#mkpr").oninput=e=>{const d=e.target.value.replace(/\D/g,"").slice(0,10);e.target.value=d?Number(d).toLocaleString(L==="en"?"en-US":"es-CO"):""};
 $("#mkgo").onclick=async()=>{const ti=$("#mkt"),pr=$("#mkpr"),b=$("#mkgo"),price=Number(pr.value.replace(/\D/g,"")),f=$("#mkfi").files[0];
  if(!need(ti,ti.value.trim().length>=3,t("Escribe un título (mínimo 3 letras)"),"#mkm")||!need(pr,pr.value.trim()!==""&&price<=2e9,t("Escribe un precio válido"),"#mkm"))return;
  b.disabled=true;b.textContent=t("Publicando…");let data="";
  if(f){try{data=await pic(f)}catch{b.disabled=false;b.textContent=t("Publicar anuncio");return $("#mkm").textContent=t("No pude leer esa imagen")}}
  const r=await api("/market",{method:"POST",body:{cat:$("#mkc").value,title:ti.value.trim(),price,body:$("#mkbd").value.trim(),data}});
  if(!r.ok){b.disabled=false;b.textContent=t("Publicar anuncio");return $("#mkm").textContent=r.d.error||t("No se pudo cargar")}
  cerrar();toast(t("Anuncio publicado"));MK.q="";MK.find=0;show()}}
mountPage(pg_market);
