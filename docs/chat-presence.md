# Chat y presencia

Aplicar `supabase/migrations/012_user_presence.sql` después de 001–011 e instalar
el APK actualizado. No se necesitan secretos nuevos. Sin la migración el chat
sigue disponible y muestra «Estado no disponible».

La lista salta al último mensaje al recibir o enviar uno nuevo. Las confirmaciones
de lectura y la carga de historial no provocan desplazamiento. La recepción usa
la suscripción Realtime existente; confirmar `messages` en `supabase_realtime`.

Cada instalación envía una señal de actividad cada 20 segundos mientras la app
está visible y marca su sesión inactiva al pasar a segundo plano. El chat consulta
el estado del interlocutor cada 10 segundos. La hora proviene del servidor y la
última conexión se expresa en minutos, horas o días. El punto verde significa
que la app está activa, no necesariamente que el usuario está mirando ese chat.

Las sesiones sin señal durante 60 segundos dejan de contar como conectadas.
En varias instalaciones basta una activa para mostrar «En línea». Solo las RPC
pueden escribir el estado de la cuenta autenticada y consultar el estado agregado;
los identificadores de dispositivos no se exponen. Una salida abrupta o el cierre
de sesión puede tardar hasta 60 segundos en reflejarse.

Verificar con dos cuentas: recibir varios mensajes sin tocar la lista, cargar
historial, enviar un mensaje, dejar la app en segundo plano, volver a abrirla y
perder la conexión. Comprobar estado y última conexión en el otro teléfono.
