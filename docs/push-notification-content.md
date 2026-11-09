# Contenido de las notificaciones push

La función `supabase/functions/deliver-push/index.ts` envía `actor_name` y
`message_preview` como datos FCM. El nombre se obtiene de `users.display_name`,
con respaldo en `username`. Para mensajes, la vista previa se consulta por
`message_id` y se verifica que coincidan el remitente y el destinatario.
Se limita a 500 caracteres Unicode para mantener acotado el payload.

Android muestra el nombre como título y la acción como cuerpo; en mensajes
incluye también la vista previa. Al expandir la notificación se puede leer más
texto. Se conserva la visibilidad privada en la pantalla de bloqueo.

Ejemplos:

- Ana — Te envió un mensaje: Hola, ¿cómo estás?
- Luis — Le gusta tu publicación
- María — Comentó tu publicación
- Pedro — Solicitó seguirte

## Activación

Se necesitan tanto la nueva app Android como la función actualizada en Supabase.
Desplegar `deliver-push` en el proyecto correspondiente con el procedimiento
habitual; no se requieren nuevas migraciones ni cambios de secretos.
Los APK anteriores ignoran los campos nuevos. La nueva app tolera los payloads
anteriores e intenta recuperar sus datos desde Supabase; la función actualizada
evita esa consulta adicional.

Verificar con dos cuentas: enviar un mensaje con la app receptora en segundo
plano, expandir la notificación y comprobar nombre, acción y texto. Repetir con
like, comentario, seguimiento, solicitud y like de historia.

Los mensajes usan `MessagingStyle`, foto de perfil y el canal
«Mensajes directos», con acceso al chat al tocarlos. Los demás eventos usan
el canal «Actividad de PicGram», otro icono y acceso a la bandeja.

La foto se obtiene del campo `users.avatar_url` con la sesión del destinatario
y se carga con Coil (128 px, recorte circular, máximo cuatro segundos).
El avatar incluye una insignia del icono real de la aplicación. Sin foto o si
falla la descarga, se usan iniciales; sin remitente, se muestra el icono de la
app. El icono pequeño utiliza una versión monocroma del logo para Android.
La consulta de perfil conserva los datos recuperados aunque falle una consulta
posterior del mensaje, y usa el nombre de usuario como título.

Si un payload antiguo carece de nombre, tipo o remitente, el servicio intenta
recuperar la notificación por ID con la sesión del destinatario y las políticas
RLS. Sin conexión o si la fila no existe, se conserva un aviso de respaldo.
Los eventos de prueba sin una notificación real no pueden recuperar un remitente.
