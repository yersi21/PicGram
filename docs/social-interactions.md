# Interacciones de publicaciones y perfiles

- En el feed, un toque abre el detalle; dos toques dan like sin quitarlo si ya
  estaba marcado. El botón del corazón sigue permitiendo dar/quitar like.
- En el detalle, dos toques en la imagen o el área del video dan like.
- El reproductor distingue el toque simple del doble con
  [GestureDetector](https://developer.android.com/reference/android/view/GestureDetector.SimpleOnGestureListener).
  Los botones de reproducción conservan sus eventos propios.
- En otro perfil, tocar la foto la abre ampliada. Si no tiene foto, no se abre
  la inicial como imagen. Las opciones de cambiar/eliminar siguen siendo privadas.
- Tocar seguidores o siguiendo abre su lista, tanto en perfiles propios como
  ajenos. Cada fila abre el perfil correspondiente. Las consultas se paginan
  y las listas se recargan al regresar de un perfil.

## Verificación manual pendiente en dispositivo, con dos cuentas

1. Dar doble toque a una imagen y un video en feed/detalle; comprobar el corazón
   y recargar. Repetir sobre un like existente: no debe quitarse ni duplicarse.
2. Probar toque simple, desplazamiento y controles del video, también ampliado.
3. Desconectar la red al dar like: el estado optimista debe revertirse.
4. Abrir la foto de otro perfil, cerrar con la flecha y con el botón atrás.
5. Si A sigue a B, A debe aparecer en seguidores de B y B en seguidos de A.
6. Abrir una fila, seguir/dejar de seguir y regresar; revisar listas y contadores.
7. Revisar listas vacías, usuarios sin foto y reintento tras un error de red.

No se ejecutaron cambios en el servidor ni pruebas con cuentas reales desde esta
sesión. Se utilizan las tablas y permisos existentes de `users` y `follows`.
