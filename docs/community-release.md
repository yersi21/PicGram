# Perfiles, privacidad, historias, mensajes y notificaciones

## Estado de entrega

Implementación local sobre Supabase Auth/PostgreSQL/Storage y la arquitectura MVVM
existente. No se ha modificado el proyecto Supabase en producción desde estas
herramientas, creado cuentas ni enviado notificaciones reales. La configuración
pública Android de FCM se añadió a `local.properties` a partir del
`google-services.json` proporcionado para `com.example.picgram`, proyecto
`picgram-fb4c4`. La configuración de entrega en el servidor sigue pendiente de verificar.
No hay un dispositivo/emulador conectado durante esta verificación.
Compilar el APK no despliega las migraciones ni la función de servidor.

## Qué incluye

| Requisito | Implementación |
|---|---|
| RF-05 | Editar nombre, biografía, teléfono, nacimiento, ubicación y foto. Subida real a Storage reutilizando el procesamiento existente. Datos personales en tabla propia con RLS de propietario. El nombre y avatar actuales se consultan al mostrar publicaciones y detalles. |
| RF-27 / RF-09 | Cuenta pública/privada, solicitudes, cancelar solicitud, aceptar/rechazar y contadores de seguidores aprobados. RPC transaccionales serializan cambios de privacidad y seguimiento. Publicar una cuenta acepta sus solicitudes pendientes; la pantalla explica ese comportamiento. |
| RF-19/20/21 | Selector de imagen/video con vista previa, subida real, historias de seguidos encima del feed y visor. Creación y vencimiento asignados por PostgreSQL, exactamente 24 horas. RLS y lectura de bytes rechazan historias vencidas, sin depender de un cron. El visor usa reloj monotónico ajustado con hora del servidor para ocultarlas también sin recargar. |
| RF-22 | Conversaciones, historial paginado (50 por página), mensajes privados, estado enviado/leído y no leídos. Supabase Realtime por WebSocket, renovación de JWT, reconexión y recarga de respaldo cada 30 s. No se presenta el respaldo periódico como conexión en tiempo real. |
| RF-23 | Avisos persistidos por triggers para seguidores, solicitudes, likes, comentarios y mensajes. Bandeja y contador en inicio. Transporte Android FCM opcional y Edge Function para entrega real. Requiere la configuración del servidor descrita abajo; no es una simulación de push con alarmas locales. |

El nombre de usuario y el correo de autenticación no se modifican en el editor:
los datos personales editables son teléfono, fecha de nacimiento y ubicación.
El nombre vuelve a ser editable como parte de RF-05, no como el antiguo botón
aislado de «Editar nombre». Las fotos también conservan sus opciones de ver y eliminar.

## Despliegue seguro, en orden

1. Guardar un respaldo de la base de datos y planificar una actualización coordinada
   de los clientes. Mantener los secretos fuera del repositorio.
2. Confirmar que las migraciones 001–004 ya están aplicadas. No volver a ejecutar
   001 sobre el esquema actual: volvería a instalar sus políticas antiguas.
3. Revisar los nombres/rutas de medios antiguos. El formato admitido es
   `posts/{UUID del propietario}/{archivo}`, `avatars/{UUID}/{archivo}`
   (y `avatars/{UUID}.jpg` antiguo), y `stories/{UUID}/{archivo}`.
   Los archivos con otras rutas deben revisarse/migrarse explícitamente; no se
   deduce su propietario ni se permite su lectura a cualquier cuenta.
4. Ejecutar **una vez**, en este orden, en el SQL Editor de Supabase:
   - `supabase/migrations/005_profiles_privacy.sql`
   - `supabase/migrations/006_stories_messages_notifications.sql`
   - `supabase/migrations/007_private_media.sql`
   - `supabase/migrations/008_push_delivery.sql`
5. Durante 005–007 hay un bloqueo de lectura de medios intencionado: evita exponer
   contenido privado entre pasos del despliegue. Completar 007 retira ese bloqueo
   e instala las reglas definitivas. No dejar el despliegue a medias.
6. Ejecutar `supabase/verify_community.sql` y revisar sus resultados.
7. En Realtime, confirmar que `messages` y `notifications` pertenecen a la
   publicación `supabase_realtime`. 006 lo hace si la publicación existe.
8. Instalar el APK actualizado y realizar la matriz de aceptación siguiente.

No se eliminan cuentas, posts ni archivos existentes. El bucket `picgram-posts`
pasa a privado. Las versiones antiguas que descargan URLs públicas directamente
dejan de ser compatibles: no hay una opción segura que mantenga esos enlaces
públicos y a la vez proteja publicaciones privadas. Los archivos que alguien ya
descargó cuando eran públicos no pueden retirarse de sus dispositivos.

## Push reales: configuración necesaria

Supabase sigue siendo el backend. **FCM se usa exclusivamente como transporte de
Android**, no para Auth, Firestore o almacenamiento. Los cuatro valores públicos
de Android ya están configurados localmente para `picgram-fb4c4`. Es necesario
instalar una compilación nueva; esto no confirma todavía registro de tokens ni
recepción push real. No se ha instalado ninguna clave privada en Android.

### Cliente Android

En `local.properties`, añadir los valores públicos de la aplicación Android
registrada con package `com.example.picgram` en el proyecto Firebase de transporte:

```properties
FIREBASE_APPLICATION_ID=valor_mobilesdk_app_id
FIREBASE_API_KEY=valor_current_key
FIREBASE_PROJECT_ID=valor_project_id
FIREBASE_SENDER_ID=valor_project_number
```

Corresponden a los campos públicos de `google-services.json`. Se inicializa
Firebase con `FirebaseOptions`; no se necesita el plugin Google Services ni se
sustituye la configuración Supabase. No introducir aquí ninguna clave privada ni
credencial `service_role`. Recompilar tras completar esos valores.
Android 13+ solicita permiso solo cuando el transporte está configurado. También
se pueden abrir los permisos desde la pantalla de notificaciones. Se necesita un
dispositivo compatible con Google Play Services y conexión a internet.

### Servidor

1. Habilitar Firebase Cloud Messaging HTTP v1 en el proyecto de transporte.
2. Guardar **solo como secretos de Supabase Edge Functions**:
   - `FCM_SERVICE_ACCOUNT`: JSON de una cuenta de servicio con permiso de envío FCM
     en ese proyecto; contiene su clave privada. Nunca compartirlo en el chat/APK.
   - `PICGRAM_PUSH_HOOK_SECRET`: secreto aleatorio largo exclusivo del webhook.
   - `SUPABASE_URL` y `SUPABASE_SERVICE_ROLE_KEY`: secretos del entorno servidor
     proporcionados por Supabase, no claves de la aplicación Android.
3. Desplegar `supabase/functions/deliver-push` con el CLI de Supabase. El archivo
   `supabase/config.toml` desactiva JWT en **esa función solamente**: cada petición
   debe pasar la comparación del secreto de webhook antes de acceder a datos.
4. Crear un Database Webhook **INSERT** sobre `public.notifications`, método POST,
   URL `https://TU_PROYECTO.supabase.co/functions/v1/deliver-push`, con el header
   `x-picgram-hook-secret` igual al secreto anterior y `Content-Type: application/json`.
   Usar el payload estándar de Database Webhooks. No enviar un service_role desde Android.
5. Crear también un trabajo Supabase Cron cada minuto que haga POST a la misma
   función, mismos headers, cuerpo `{}`. El webhook entrega inmediatamente; este
   trabajo drena hasta 25 eventos pendientes y reintenta fallos o peticiones
   interrumpidas cuando vence la reserva de dos minutos. Guardar el secreto del
   trabajo en Vault/configuración protegida, no en SQL publicado en Git.
6. Comprobar desde **dos dispositivos reales** el registro de tokens, permisos,
   entrega en segundo plano y con la app cerrada, y recepción por cada tipo de evento.

La función vuelve a consultar el destinatario en la base de datos; no confía en
un destinatario recibido por webhook. Tokens inválidos se eliminan. Una entrega
fallida no se marca como correcta. Se usa entrega al menos una vez, con IDs
deterministas para sustituir avisos repetidos en Android, no promesas de entrega
exactamente una vez. Los avisos no incluyen texto privado de conversaciones.
Sin tokens activos no se envía push; la actividad permanece en la bandeja.
Los avisos leídos no generan nuevas alertas push pendientes.

## Matriz de aceptación antes de considerar terminada la puesta en producción

Usar A (privada), B (seguidora), C (no seguidora) y dos teléfonos:

1. **Perfil**: editar todos los campos, subir una foto real y volver al feed,
   perfil y detalle desde la segunda cuenta. Verificar que C no lee teléfono,
   nacimiento, ubicación ni correo usando la API directamente.
2. **Privacidad**: C no ve posts/likes/comentarios ni descarga su archivo por URL
   autenticada. B solicita, A acepta, los contadores cambian una sola vez y B ve
   el contenido. Rechazar, cancelar, reintentar y pasar a pública. Intentar crear
   directamente una fila `follows` para saltarse la aprobación: debe fallar.
3. **Historias**: imagen/video, vista previa y reproducción vertical/horizontal;
   B ve las historias arriba, C no. Al cumplir 24 h desaparece una historia ya
   abierta y el servidor deja de servir su archivo. Probar cambio del reloj del
   teléfono: la autorización sigue basándose en el reloj del servidor.
4. **Chat**: A envía y B recibe sin refrescar; contador sin leer, abrir conversación,
   leído, historial >50 mensajes, desconexión/reconexión, rotación y reintento tras
   una respuesta de red ambigua. C no puede leer mensajes ni hacerse pasar por A.
5. **Notificaciones**: generar seguir, like, comentario y mensaje desde B; comprobar
   la bandeja de A y su teléfono en segundo plano. Denegar el permiso: la bandeja
   sigue funcionando. Cerrar sesión: no mostrar avisos de la cuenta anterior.
6. **Regresión**: registro, recuperación, publicar imagen/video, borrar publicación
   propia con comentarios/likes, buscar, seguidores y cambio de tema.

Estas comprobaciones en producción/dispositivo **no se han realizado** durante la
implementación local. No se fabrican cuentas ni se ejecutan migraciones remotas.

## Pruebas locales reproducibles

Android:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline --no-configuration-cache
.\gradlew.bat :app:lintDebug --offline --no-configuration-cache -I scripts/lint-without-bidi.init.gradle
```

El script lint conserva la exclusión Bidi preexistente y documentada. No equivale
a ejecutar ese detector. Las dependencias nuevas necesitan una primera descarga.

Base de datos y función push, sin servidor remoto ni envío real:

```powershell
npm --prefix build/security-tests install --no-save @electric-sql/pglite@0.5.12 deno@2.9.6
node scripts/community-db-test.mjs
.\build\security-tests\node_modules\.bin\deno.cmd check supabase/functions/deliver-push/index.ts
.\build\security-tests\node_modules\.bin\deno.cmd test --allow-env scripts/push-delivery-test.ts
```

PGlite ejecuta PostgreSQL real en WASM con esquemas auth/storage simulados; prueba
políticas y funciones, **no** el servicio HTTP Storage de Supabase ni su Realtime.
Las pruebas push sustituyen HTTP por respuestas simuladas y se ejecutan sin permiso
de red. En Windows, el runner Deno puede necesitar permiso para crear canales IPC.
Los resultados locales se anotan en `docs/community-verification.md`.

## Límites operativos

- Expirar oculta las historias y revoca nuevas lecturas; no borra físicamente el
  objeto. Programar limpieza de archivos vencidos/huérfanos con privilegios de
  servidor y un periodo de retención definido. No borrar filas de Storage a mano;
  eliminar objetos mediante su API administrativa.
- Storage y PostgreSQL no comparten transacción. Un cierre durante una subida
  puede dejar un objeto huérfano. Los reintentos de mensajes e historias mantienen
  el mismo UUID mientras vive el ViewModel; no se implementa una cola offline
  durable tras muerte abrupta del proceso. Comprobar el historial antes de reenviar
  manualmente tras ese caso.
- La privacidad impide nuevas consultas/descargas; no puede hacer que un receptor
  olvide contenido ya leído o guarde una captura. No se afirma cifrado E2E.
- La entrega push depende de configuración, permisos, red, Play Services y políticas
  de batería; un `force stop` de Android puede impedirla hasta reabrir la app.

Referencias oficiales: [Supabase Realtime](https://supabase.com/docs/guides/realtime/protocol),
[archivos privados](https://supabase.com/docs/guides/storage/serving/downloads),
[Database Webhooks](https://supabase.com/docs/guides/database/webhooks),
[FCM Android](https://firebase.google.com/docs/cloud-messaging/android/get-started),
[FCM HTTP v1](https://firebase.google.com/docs/cloud-messaging/send/v1-api).
