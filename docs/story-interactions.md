# Interacciones en historias

El visor se cierra automáticamente a los 10 segundos de abrir cada historia,
incluidos los videos. En la parte superior hay una barra pequeña por historia:
las anteriores se muestran completas, la actual se llena con el tiempo y las
siguientes permanecen vacías. Estas barras reemplazan el contador numérico. Cambiar de
historia inicia otros 10 segundos; las recargas del feed no reinician el reloj.
Mantener presionada la pantalla pausa el contador y su barra; al soltar continúa
desde el tiempo restante, sin contar el tiempo que estuvo presionada.
No hay botones de anterior/siguiente: un toque breve en la mitad izquierda de
la imagen o video abre la historia anterior; en la derecha, la siguiente. Si no
hay una historia en esa dirección dentro de la cuenta abierta, se cierra el visor
y se vuelve al feed. Soltar después de mantener presionado no cambia de historia.
Los videos del visor se reproducen automáticamente; los controles de likes,
mensajes y vistas conservan sus propias acciones al tocarlos.
El cierre también ocurre mientras se escribe. Los borradores y los reintentos
se conservan por historia mientras vive el ViewModel del inicio.

Las historias ajenas incluyen un corazón para dar/quitar like y un campo para
enviar mensajes privados al autor. Los mensajes aparecen en el chat existente
y usan su transporte de notificaciones. Un reintento reutiliza el mismo ID y
contenido para evitar mensajes duplicados tras una respuesta de red ambigua.
Las historias propias no permiten darse like ni enviarse mensajes a uno mismo.
La barra inferior usa un campo redondeado «Enviar mensaje» dentro del panel
Liquid Glass de la aplicación, con transparencias, bordes y colores del tema claro/oscuro,
seguido de iconos de corazón, conversación y envío. El corazón da/quita like;
la conversación abre el chat del autor; el avión envía el texto o reintenta un
envío pendiente. El estado de envío y sus errores siguen visibles en la barra.

Dar like genera una notificación «le gusta tu historia» para su autor. Tocarla
abre la historia si sigue vigente; si venció o se eliminó, se informa que ya no
está disponible. Quitar el like no genera otra notificación; volver a darlo sí.
Los reintentos de una misma inserción no duplican el aviso. Las respuestas por
mensaje generan el aviso de mensaje del chat existente, sin un segundo aviso.
Ambos eventos utilizan la entrega push existente. El teléfono muestra el tipo
de actividad sin incluir el texto privado del mensaje.

En cada historia propia, «Visto por N» abre la lista de personas que la vieron,
con su nombre, usuario y foto. Tocar una persona abre su perfil. La lista tiene
actualización manual y pausa el temporizador mientras está abierta. Se registra
una sola vista por cuenta e historia al abrir el visor, sin contar al autor;
la fecha de la primera vista la asigna el servidor. Solo el autor puede consultar
las vistas de sus historias vigentes. Las vistas anteriores a esta actualización
no se pueden reconstruir.

Antes de instalar el APK actualizado, aplicar una vez
`supabase/migrations/009_story_likes.sql` después de 001–008 y luego
`supabase/migrations/010_story_views.sql` para registrar y consultar las vistas,
y `supabase/migrations/011_story_notifications.sql` para notificaciones de likes.
La migración crea las reacciones con unicidad y políticas RLS: solo el usuario
puede consultar y modificar su reacción, y únicamente sobre historias visibles
y vigentes según el servidor.
Las migraciones deben desplegarse en el servidor; compilar el APK no las aplica.
Para los avisos del teléfono, desplegar también la función `deliver-push`
actualizada y completar la configuración FCM/webhook descrita en
`community-release.md`. Sin ella, las notificaciones siguen en la bandeja.

Verificar con dos cuentas: abrir imagen/video y comprobar el cierre a los 10 s;
mantener presionado, comprobar la pausa y soltar para continuar el tiempo restante;
dar like, cerrar/reabrir y quitarlo; responder y comprobar la recepción en el
chat del autor; probar fallo de red y reintento, cuenta privada e historia vencida.
Abrir una historia ajena varias veces y comprobar que el autor ve una sola
persona en «Visto por». Confirmar que otra cuenta no puede consultar esa lista
mediante la API ni registrar vistas para historias que no puede ver.
Dar like y responder desde B: A debe recibir un aviso de cada tipo en la bandeja
y, con FCM configurado y permisos concedidos, en su teléfono en segundo plano.
Reintentar tras una respuesta ambigua y comprobar que no se duplica el evento.
