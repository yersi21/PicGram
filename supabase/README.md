# Configuracion de PicGram

La app necesita `SUPABASE_URL` y una clave **anon/publishable** en `local.properties`.
BuildConfig se incluye en el APK: nunca usar una clave service_role o secret.

## Migracion pendiente en el servidor

Revisar `migrations/001_picgram_integrity.sql` contra el esquema real y ejecutarlo
en Supabase SQL Editor. No se ha ejecutado desde este proyecto. Preserva los datos
y agrega las restricciones de unicidad que necesitan likes y seguimientos, la
columna avatar_url, perfiles durante el registro, borrado de comentarios/likes
asociados y politicas de propiedad. Todo ocurre dentro de una transaccion.
Si existen duplicados o IDs que no sean UUID, se aborta; revisarlos manualmente.
Las tablas existentes deben tener las columnas y tipos del contrato del script
(timestamps en milisegundos, bigint). Revisar cualquier trigger preexistente de
creacion de perfiles; el nuevo trigger inserta con ON CONFLICT DO NOTHING.

Las politicas restrictivas bloquean escrituras anonimas aunque existan politicas
permisivas antiguas. Solo se reemplazan las politicas con prefijo picgram_ del
script. El bucket `picgram-posts` es publico para lectura y permite archivos de
hasta 50 MB. Los avatares se limitan a 10 MB en el cliente. Las rutas nuevas son
`posts/{userId}/{uuid}.{extension}` y `avatars/{userId}/{uuid}.{extension}`.

## Verificacion con dos cuentas de prueba

1. Registrar/iniciar sesion, incluyendo confirmacion por correo si esta habilitada.
2. Publicar imagen y video; comprobar que una publicacion de solo texto se rechaza.
   Reintentar una publicacion tras perder conexion.
3. Agregar un comentario y borrarlo inmediatamente; volver a abrir el detalle.
4. Dar/quitar like rapidamente y comprobar el resultado al recargar.
5. Cambiar avatar dos veces y verificarlo desde la segunda cuenta.
6. Borrar una publicacion con likes y comentarios; comprobar la eliminacion.
7. Con la cuenta B intentar modificar/borrar datos y archivos de A mediante la API:
   las escrituras deben rechazarse o afectar cero filas.
8. Comprobar renovacion de token, errores de red y cierre de sesion.

## Comprobaciones locales

Usar un JDK valido en JAVA_HOME (el daemon del proyecto utiliza JDK 25).
La compilacion debug y las pruebas unitarias se ejecutan con:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --offline
```

Las pruebas cubren limites de lectura, rutas de Storage, recuperacion, video,
avatares, seguidores, ViewModels y limites de arquitectura MVVM. La prueba de
ejemplo de la plantilla se retiro durante la limpieza. Estas pruebas no sustituyen
la lista de flujos con dos cuentas indicada arriba.

Durante esta revision, lint 32.3.2 quedo detenido en
`BidirectionalTextDetector -> KotlinUFile.getAllCommentsInFile`. Para verificar el
resto de los comprobadores se uso:

```powershell
.\gradlew.bat :app:lintDebug --offline --no-configuration-cache -I scripts/lint-without-bidi.init.gradle
```

El ajuste solo se activa con ese script; no deshabilita el comprobador en la
configuracion normal. Una busqueda separada no encontro controles Unicode
U+202A-U+202E ni U+2066-U+2069 en app/src y app/build.gradle.kts. El informe restante
no contiene errores; conserva avisos de mantenimiento y recursos del proyecto.

## Limitaciones operativas

Storage y PostgreSQL no comparten una transaccion. Tras confirmar un borrado de
publicacion, se guarda una cola local de limpieza, que se reintenta al cargar el
feed. Si se desinstala la app o se pierde el dispositivo, hace falta limpieza de
objetos huerfanos desde el servidor. Una subida interrumpida por cierre del proceso
puede dejar un objeto sin referencia. Los formatos de video dependen de los codecs
del dispositivo. No se han realizado pruebas con cuentas reales ni dispositivo
desde esta sesion.

Referencias oficiales: [sesiones y renovacion de tokens](https://supabase.com/docs/guides/auth/sessions)
y [politicas de Storage](https://supabase.com/docs/guides/storage/security/access-control).
