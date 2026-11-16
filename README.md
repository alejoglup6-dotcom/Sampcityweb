# SampCity Web

1. `cp .env.example .env` y rellena los datos (base de datos, SESSION_SECRET, Discord).
2. `npm install` y `npm start`.
3. En el Discord Developer Portal (OAuth2) agrega el redirect: `https://TU-DOMINIO/auth/discord/callback`.

- Login: usa el mismo nombre y contraseña del juego (bcrypt o SHA256+sal, como snrp.pwn).
- La web solo crea la tabla `web_posts`; las demás (`player`, `discord_links`, `crews`) ya existen y solo se leen,
  salvo `discord_links`, donde se inserta/borra la vinculación.
- Vacantes (/solicitar-staff): quien tenga `admin_level` >= VACANCY_MIN_LEVEL (por defecto igual que POST_MIN_LEVEL) abre, edita, cierra vacantes de staff o de facciones y acepta/rechaza solicitudes; los jugadores se postulan con su cuenta. Las tablas `web_vacancies` y `web_applications` se crean solas.
- Noticias/actualizaciones/FAQ: las publica quien tenga `admin_level` >= POST_MIN_LEVEL (por defecto 5 = Administrador, en la escala de staff 0-9 del 04-oct-2026; si en Render está en 4, cámbialo a 5).

- Fotos: el staff las publica por enlace https:// (tipo `photo`), sin subir archivos.
- `SERVER_IP` (opcional) en el `.env` muestra el botón "Copiar IP"; `/api/info` solo da cifras públicas.
- En TV: se navega con las flechas del control remoto (se activa solo, o con `?tv=1`).

## Novedades
- Me gusta y comentarios en noticias/actualizaciones/fotos (hay que iniciar sesión; el staff puede borrar comentarios).
- Fotos: el staff las sube desde el celular o PC (se reducen solas, máx. 900 KB y se guardan en la base de datos) o por enlace https.
- Sección Reglas y botón Editar para el staff.
- Los intentos de login se guardan en la base de datos (tabla `web_tries`).
- Tablas nuevas que crea sola la web: `web_likes`, `web_comments`, `web_images`, `web_tries`.

## Seguridad
- Secretos solo en `.env` / variables de Render (`.gitignore` los excluye). Si alguna vez subiste un `.env` a Git, cambia esas claves: borrarlo del repo no basta.
- Rotar `SESSION_SECRET` cierra todas las sesiones. El servidor no arranca con el valor de ejemplo.
- Crea un usuario MySQL solo para la web: SELECT en `player` y `crews`; SELECT/INSERT/DELETE en `discord_links`; todos los permisos solo en las tablas `web_*`.
- `MYSQL_SSL=1` cifra la conexión con la base de datos.
- Turnstile (opcional): define `TURNSTILE_SITEKEY` y `TURNSTILE_SECRET` para pedir verificación anti-bots al iniciar sesión.
- Login limitado por cuenta+IP (6) y por IP (30) cada 15 min; la API limita peticiones por IP.
- Cabeceras: CSP, HSTS, X-Frame-Options, Permissions-Policy. En HTTPS se fuerza la redirección desde HTTP.

## Interfaz
- Tema oscuro/claro y español/inglés (botones en el pie de página; se recuerdan en el navegador). Las publicaciones no se traducen.
- Nuevas secciones: Testimonios (los publica el staff, con su nombre y el texto real del jugador) y Contacto.
- Contacto: guarda los mensajes en la tabla `web_contact` (se crea sola). Solo el staff los ve y los borra. Máx. 3 mensajes por hora por IP; Turnstile se aplica aquí también si está activo.
- Página 404 propia: `public/404.html`.

## Avatares, staff por rangos y clasificación
- `SKIN_COLUMN` (por defecto `skin`): columna de la tabla `player` con el id de skin. `SKIN_URL` (opcional): imagen de cada skin, con `{id}` (ej. `/assets/skins/{id}.png` si las alojas tú). Si la imagen falla, se muestra la inicial.
- Staff agrupado por rango con avatar y "última vez"; nueva sección Clasificación (`/api/top`); noticias y fotos paginadas (12 por página).

## Estructura por páginas
Cada sección es una página independiente (archivo + URL propia), ya no van todas dentro de `index.html`:

| URL | HTML | JS de la página |
|---|---|---|
| `/` | `public/index.html` | `public/assets/js/pages/index.js` |
| `/noticias` | `public/noticias.html` | `public/assets/js/pages/noticias.js` |
| `/actualizaciones` | `public/actualizaciones.html` | `public/assets/js/pages/actualizaciones.js` |
| `/faq` | `public/faq.html` | `public/assets/js/pages/faq.js` |
| `/fotos` | `public/fotos.html` | `public/assets/js/pages/fotos.js` |
| `/staff` | `public/staff.html` | `public/assets/js/pages/staff.js` |
| `/solicitar-staff` | `public/solicitar-staff.html` | `public/assets/js/pages/solicitar-staff.js` |
| `/clasificacion` | `public/clasificacion.html` | `public/assets/js/pages/clasificacion.js` |
| `/reglas` | `public/reglas.html` | `public/assets/js/pages/reglas.js` |
| `/testimonios` | `public/testimonios.html` | `public/assets/js/pages/testimonios.js` |
| `/contacto` | `public/contacto.html` | `public/assets/js/pages/contacto.js` |
| `/comunidad` | `public/comunidad.html` | `public/assets/js/pages/comunidad.js` |
| `/perfil` | `public/perfil.html` | `public/assets/js/pages/perfil.js` |

- Lo compartido está en `public/assets/css/style.css`, `public/assets/js/core.js` (API, login, tema/idioma, menú) y `public/assets/js/sprite.js` (íconos). El perfil (`perfilFB` y sus ventanas) vive en `public/assets/js/perfil-ui.js`, que solo cargan `/perfil` y `/u/Nombre`.
- Para editar una página toca solo su `.html` y su `.js`. Si cambias CSS/JS, sube el `?v=1` de los `<link>`/`<script>` para evitar caché.
- Para crear una página nueva: copia un `.html`, cambia `data-v`, título y script; agrégala a `NAV` y `R` en `core.js` y a `ROUTES` en `server.js`.
- Los enlaces viejos tipo `/#fotos` redirigen solos a `/fotos`. Discord vuelve a `/perfil?discord=...`.

## Publicaciones con fotos, etiquetas e historias
- Quien tenga sesión publica desde "¿Qué estás pensando?" (hoja a pantalla completa): texto, hasta 4 fotos y etiquetas de jugadores (`/api/users/search`). Las fotos se reducen en el navegador.
- Historias: botón "Crear historia" en la franja del inicio. Una foto o un video MP4/MOV/WebM de **máx. 15 s** (el servidor lee la duración del archivo) y de `VIDEO_MAX_MB` MB (por defecto 25). Duran 24 h: al vencer se borran solas (cada hora) junto con sus medios, me gusta y comentarios. Se ven en el visor (toque derecho/izquierdo, mantener para pausar), con me gusta y comentarios, y aparecen en el feed con miniatura mientras estén activas.
- Medios nuevos (tablas `web_media`, `web_media_chunks`, `web_post_media`, `web_tags`; se crean solas): se guardan en la base de datos en trozos de 256 KB y se sirven en `/media/:id` con soporte de Range.
- Barra inferior del celular (`fbNav()` en `core.js`), como en Facebook: **Feed · Reels · Solicitudes de amistad · Marketplace · Notificaciones · Perfil**. El ícono de solicitudes muestra cuántas tienes pendientes.

## Notificaciones
- Nueva página `/notificaciones` (`public/notificaciones.html` + `public/assets/js/pages/notificaciones.js`) y campana con contador en la **barra inferior** del celular, en la cabecera (PC) y en el menú lateral/hamburguesa. El contador se actualiza solo cada 45 s.
- Avisan de: **me gusta** y **comentarios** en tus publicaciones/historias, **también comentó** (alguien comentó donde tú ya habías comentado), **menciones** `@Nombre_Apellido` en publicaciones y comentarios (al escribir `@` + 2 letras sale una lista de jugadores), **etiquetas** en publicaciones, **noticias y actualizaciones nuevas** del staff, **Discord vinculado** y, solo para el staff, **mensajes nuevos en Contacto**.
- En la lista: filtros Todas / No leídas, "Marcar todo como leído", menú ••• por aviso (marcar como leída / eliminar) y "Ver anteriores". Al tocar un aviso se marca como leído y te lleva a la publicación (si es un comentario se abre con los comentarios desplegados).
- Tabla nueva (se crea sola): `web_notifs`. Los avisos se borran con su publicación, al quitar el me gusta y a los 45 días. `NOTIF_BROADCAST_MAX` (opcional, por defecto 3000) limita a cuántos jugadores se avisa de una noticia/actualización.
- **Solicitudes de amistad:** ya implementadas (ver «Amigos»); usan los tipos `friend_req` y `friend_acc`. Cuando crees el sistema de amigos solo llama, en `server.js`:
  - al enviar la solicitud: `await notify(idDestino, u, "friend_req");`
  - al aceptarla: `await notify(idQuienLaEnvió, u, "friend_acc");`
  
  (`u` es el jugador que actúa, el que devuelve `me(req)`). Al tocar el aviso se abre el perfil de esa persona; si luego quieres botones Confirmar/Eliminar dentro del aviso, se añaden en `ntRow()` de `notificaciones.js`.
- Para otro tipo de aviso: llama `notify(destino, actor, "tipo", idPublicación, "texto corto")`, y agrega el texto y el ícono del tipo en `NTX` y `NIC` de `notificaciones.js` (y el color en `.ty[data-ty=...]` del CSS).

## Amigos
- Tabla nueva (se crea sola): `web_friends` (`a` envía, `b` recibe; `status` 0 = pendiente, 1 = amigos).
- Página `/amigos` (`public/amigos.html` + `public/assets/js/pages/amigos.js`): buscador, solicitudes recibidas (Confirmar/Eliminar), «Personas que quizás conozcas» (primero con amigos en común, luego los más activos) y mis amigos.
- Perfil `/u/Nombre`: botón Agregar a amigos / Cancelar solicitud / Confirmar-Eliminar / Amigos, contador y vista de amigos. Las solicitudes también se responden desde `/notificaciones`.
- El feed (`/feed` e inicio) y la franja de historias solo muestran publicaciones de tus amigos y las tuyas (`/api/posts?type=feed`, `scope=friends`). Sin amigos aparece un aviso con botón hacia `/amigos`. Noticias y actualizaciones siguen en sus páginas.
- API: `POST /api/friends/request`, `POST /api/friends/accept`, `DELETE /api/friends/:name`, `GET /api/friends`, `/api/friends/requests`, `/api/friends/suggestions`, `/api/friends/search?q=`.
- Permisos MySQL: la web necesita SELECT/INSERT/UPDATE/DELETE en `web_friends`.

## Reels, Marketplace y diseño tipo Facebook
- **Reels** (`/reels`: `public/reels.html` + `public/assets/js/pages/reels.js`): videos que sube la comunidad, a pantalla completa. Se desliza hacia arriba para ver el siguiente (se cargan solos de 8 en 8). A la derecha: me gusta, comentarios (hoja desde abajo), compartir, guardar y ••• (guardar, copiar enlace, ver perfil, eliminar). Un toque pausa (el primero activa el sonido), dos toques dan me gusta.
  - Se suben desde el botón ＋ de Reels o «Crear reel» del perfil: MP4/MOV/WebM de hasta `REEL_MAX_SECS` segundos (por defecto 90) y `REEL_MAX_MB` MB (por defecto 60), con barra de progreso. El servidor comprueba la duración leyendo el archivo.
  - Enlaces: `/reels?r=ID` abre ese reel primero (también `/p/ID`), `?u=Nombre` solo los de ese jugador, `?saved=1` tus guardados, `&c=1` abre los comentarios.
  - Los videos de reels se guardan como `kind = 'rel'` en `web_media` (no se pueden usar como historia de 15 s).
  - API: `GET /api/reels`, `POST /api/reels`, `POST /api/posts/:id/save`. Tabla nueva (se crea sola): `web_saved`. Permisos MySQL: SELECT/INSERT/DELETE en `web_saved`.
- **Marketplace** (`/marketplace`): por ahora muestra «Próximamente»; se conectará con el servidor de SA-MP cuando la función esté lista en el gamemode.
- **Solicitudes** (`/amigos`): filas como Facebook (foto grande, amigos en común con sus caras, «hace 16 sem», Confirmar/Eliminar), «Ver todas», pestañas Sugerencias / Tus amigos y buscador (lupa).
- **Notificaciones**: secciones Nuevas (sin leer) / Hoy / Anteriores; «Marcar todo como leído» y «Ver solo no leídas» están en el botón •••. Los avisos de reels abren el reel.
- **Perfil** (`/perfil` y `/u/Nombre`, función `perfilFB()` en `core.js`): portada, foto redonda, rango, amigos · publicaciones · reels, botones (Agregar a historia / Crear reel, o Agregar a amigos) y pestañas Todo · Reels · Fotos · Más (Amigos, Guardados). La sección **Resumen** muestra los datos de la cuenta: en tu perfil nivel, reputación, tiempo jugado, efectivo, banco, facción, teléfono, arrestos, registro, última conexión y Discord; en el de otros solo los datos públicos.

## Reels: rendimiento (videos que se traban o no cargan)
- En el celular solo hay 2-3 videos cargados a la vez (el que ves y sus vecinos); los demás se liberan. El siguiente empieza a cargarse cuando el actual ya está sonando. Al deslizar rápido no se cargan los que solo pasan por la pantalla.
- Si un video se queda quieto 5 s o falla la conexión, se vuelve a pedir desde donde iba (hasta 3 veces); después aparece «Toca para reintentar». Mientras carga se ve un círculo girando.
- Servidor: los MP4 se guardan con el índice al principio («faststart»), así empiezan a reproducirse sin bajar el final del archivo. Los trozos de video más vistos quedan en memoria (`MEDIA_CACHE_MB`, por defecto 96) y el navegador guarda los videos en su caché. La conexión a MySQL usa hasta `MYSQL_POOL` conexiones (por defecto 10).
- **Conversión con ffmpeg** (dependencia opcional `ffmpeg-static`, se instala con `npm install`; o `FFMPEG_PATH`, o el `ffmpeg` del sistema): los reels que pesan más de `REEL_MAX_KBPS` (por defecto 2500 kbps) o que no son MP4 se convierten en segundo plano a MP4 H.264 de máx. 1280 px (~2 Mbps). El reel sale al instante con el original y luego se cambia por el liviano; el original se borra a los 30 min. Se apaga con `REEL_TRANSCODE=0`. Al arrancar, el registro dice si ffmpeg está disponible.

## Guía del servidor (`/guia`)
- Buscador de comandos, trabajos, facciones, lugares, negocios, estacionamientos y guías, con "quizás quisiste decir", sinónimos y búsquedas relacionadas.
- Se actualiza sola: el gamemode (`gamemodes/src/guia.pwn` del repo Backup) vuelca cada vez que arranca sus comandos, trabajos, facciones y lugares en `guide_entries`; la web lo junta con `businesses` y `parkings` en vivo (caché de 2 min). Las guías escritas a mano están en `data/guia.json`.
- API: `GET /api/guia`, `POST /api/guia/busqueda` (registro de búsquedas, máx. 20 por minuto por IP), `GET /api/guia/sin-resultados` (staff: lo que la gente busca y no encuentra).
- Tabla nueva (se crea sola): `web_guide_searches`. Permisos MySQL: SELECT en `guide_entries`, `businesses`, `parkings` y `parking_spots`.

## Launcher de Android (prueba)
- La app SampCity (launcher de SA-MP para Android) consulta al abrirse `GET /api/launcher/distribution.json` (servidor y archivos del juego), `GET /api/launcher/news` (noticias y actualizaciones de la web) y `GET /api/launcher/donate`.
- El servidor sale de `LAUNCHER_SERVER` (por defecto `sv.sampcity.app:7781`; la web lo pasa a IP, que es lo que necesita el cliente).
- `data/launcher-cache.json` es la lista de archivos del juego (carpeta, nombre y tamaño). Solo se manda si `LAUNCHER_CDN` apunta a donde están subidos (p. ej. un bucket de Cloudflare R2); sin eso la app no descarga nada y usa los que ya haya en el teléfono.
- Descarga de la app: `sampcity.app/descargar` (o `/SampCity.apk`) lleva al `.apk` de `LAUNCHER_CDN/SampCity.apk`.
- Opcionales: `LAUNCHER_VERSION` (si es mayor que la de la app, ofrece actualizarla) y `LAUNCHER_APK_URL` (dónde está el `.apk` nuevo).

## Red social, bloque 4: seguridad, tiempo real y moderación
- **Privacidad de medios:** `/img/:id` y `/media/:id` comprueban quién mira. Fotos y audios de chats: solo los participantes. Fotos y videos de publicaciones «amigos», «solo yo» o de grupos privados: solo quien puede ver la publicación.
- **Sesiones:** cada inicio de sesión queda en `web_sessions`; en Configuración se listan y se cierran una a una. Cambiar la contraseña cierra las demás. Contraseña: 6 a 18 caracteres en todas partes.
- **Verificación en dos pasos** (app de códigos, TOTP), solo para la web. **Recuperar contraseña** por correo en `/recuperar` (necesita `SMTP_HOST`, `SMTP_USER`, `SMTP_PASS`, `MAIL_FROM`; sin eso el enlace lleva a Discord).
- **Tus datos:** descargar todo en JSON y eliminar el contenido de la red social (la cuenta del juego no se toca).
- **Tiempo real:** `/api/stream` (Server-Sent Events) entrega al instante mensajes, «escribiendo», «visto», reacciones y la campana. Si el hosting corta la conexión, la web sigue sondeando como antes. Requiere un solo proceso de Node.
- **Push:** `public/sw.js` + paquete `web-push`. Se activa por dispositivo en Configuración → Notificaciones. Las claves se generan solas (tabla `web_kv`) o se ponen en `VAPID_PUBLIC` / `VAPID_PRIVATE`. Necesita HTTPS.
- **Chat:** chats de grupo (hasta 30), responder a un mensaje, reacciones, audios de hasta 60 s y búsqueda en mensajes.
- **Feed:** pestañas Amigos / Para ti (por interacción y recencia), tendencias de #etiquetas, enlaces clicables con vista previa y paginación por cursor.
- **Moderación** (`/panel`, staff): estadísticas, suspensiones, palabras no permitidas, verificados y registro de acciones del staff.
- **Medios en disco:** con `MEDIA_DIR` van a esa carpeta las fotos y videos nuevos, y también avatares, banners, fotos de anuncios y adjuntos del chat (subcarpeta `img/`). `node scripts/medios-a-disco.js` mueve lo que ya está en la base. Haz copia de seguridad de esa carpeta junto con la base de datos.
- **Varios procesos de Node:** los límites de peticiones se guardan en `web_limits` (valen entre procesos y reinicios). Con más de un proceso pon `LIVE_SHARED=1` para que el tiempo real pase por `web_live` (retraso de ~1 s).
- **CSP estricta:** no hay `onclick="…"` ni scripts en línea sin registrar. Los eventos se escriben `data-on-click="funcion(args)"` y los despacha `core.js` (admite llamadas a funciones globales con argumentos simples, varias con `;`, `return fn()`, `if(event.target===this)…`). Para una página nueva con un `<script>` en línea, reinicia el servidor: su hash se calcula al arrancar. Un manejador que no cumpla el formato se escribe como función con nombre.
- **Tablas nuevas** (se crean solas): `web_sessions`, `web_resets`, `web_kv`, `web_push`, `web_bans`, `web_badwords`, `web_modlog`, `web_verified`, `web_rooms`, `web_room_members`, `web_msg_rx`, `web_previews`. Columnas nuevas en `web_msgs`, `web_media` y `web_settings`, e índices en `web_posts`, `web_comments`, `web_likes` y `web_msgs`.
- **Pruebas:** `npm test` (funciones puras) y `node test/smoke.js` (API completa contra una base de pruebas vacía; nunca contra la real). GitHub Actions corre ambas en cada push.

## App Android (APK) — nativa
- `android/` es una **app nativa** (Kotlin + Jetpack Compose, Material 3), no una web envuelta. Usa la misma API que la web (`/api/…`) con la misma cuenta del juego y la misma sesión por cookie. Paquete `com.sampcity.social`, Android 7.0+.
- **Qué tiene:** inicio (feed «Amigos» / «Para ti», historias), publicar con fotos, etiquetas y encuestas, reacciones, comentarios con respuestas, perfiles, chat en vivo (SSE) con fotos, audios, respuestas, reacciones y grupos, avisos, amigos, búsqueda y tendencias, grupos, eventos, marketplace, reels, configuración (privacidad, sesiones, dos pasos, datos), panel del staff y modo oscuro/claro. Tienda, vacantes y guía se abren en el navegador.
- **Avisos con la app cerrada:** un servicio en primer plano (`LiveService`, con una notificación fija «Conexión en vivo») mantiene abierta la conexión SSE y muestra mensajes y avisos al instante; una tarea periódica (~15 min) hace de respaldo. Se controlan en Configuración → Notificaciones. Algunas marcas (Xiaomi, Huawei, Oppo) matan los servicios: hay que quitar la restricción de batería a la app. Notificaciones garantizadas aun así harían falta con Firebase Cloud Messaging (proyecto Firebase y `google-services.json`).
- **Código:** `android/app/src/main/java/com/sampcity/social/` → `data/` (API, modelos, `Repo` con cada endpoint), `ui/` (pantallas y componentes), `live/` (tiempo real y avisos), `util/`. Los modelos aceptan los números como texto (el servidor manda los conteos así).
- **Compilar:** `cd android && ./build.sh` (o `./build.sh otro.dominio`). Necesita JDK 17+, el SDK de Android (`ANDROID_HOME`, plataforma 35 y build-tools 35) y `android/keystore.properties` + `android/keystore/sampcity-release.jks` (la llave de firma; **no está en git: guárdala en un lugar seguro**, sin ella no se puede actualizar la app ya instalada). Deja el APK en `public/downloads/SampCity-App.apk` y la ficha en `data/app.json`. Sin SDK local: Actions → «APK de Android».
- **Versiones nuevas:** sube `versionCode` y `versionName` en `android/app/build.gradle`. La app avisa sola (`/api/app`) cuando el servidor tiene un APK con `versionCode` mayor.
- **Pruebas:** `./gradlew testDebugUnitTest` (desde `android/`): modelos contra respuestas reales (`src/test/resources/api`), paginador y `LiveApiTest`, que recorre toda la API desde Kotlin contra un servidor de pruebas con base de datos **vacía** en `http://localhost:3000` (si no hay servidor se omite). Ese servidor se arranca con `RATE_MULT=30` en el `.env` para que los límites de peticiones por IP no frenen la prueba; en producción no se define.
- **Web:** la página `/app` ofrece el APK (`/descargar-app`, descarga como `SampCity.apk`). `/.well-known/assetlinks.json` (huella en `data/android.json`; `ANDROID_SHA256` en el `.env` añade otras) permite que los enlaces de la web (`/p/…`, `/u/…`) abran la app.
- `/descargar` y `/SampCity.apk` siguen siendo del launcher del juego; esta app es otra.
