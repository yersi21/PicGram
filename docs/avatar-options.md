# Opciones de la foto de perfil

Al tocar la foto del perfil propio aparece un menu con Cambiar foto, Ver foto y
Eliminar foto. Ver y Eliminar se deshabilitan cuando no hay foto; Cambiar abre
el selector de imagenes existente. Las opciones de edicion no aparecen en perfiles
de otras personas.

Ver foto abre un visor a pantalla completa sin recortar la imagen, con boton para
cerrar, indicador de carga y reintento si falla la conexion.

Eliminar pide confirmacion. Primero guarda avatar_url vacio en public.users;
si falla esa escritura, no elimina el archivo. Tras confirmar el cambio guarda
la limpieza en la cola local existente y trata de quitar el objeto de Storage.
Si falla Storage se reintenta al cargar el feed. Solo se consideran archivos en
avatars/{userId}/ o el avatar antiguo avatars/{userId}.jpg, nunca publicaciones
ni fotos de otras cuentas. La inicial reemplaza la foto en el perfil.

El feed ya recarga los avatares al volver. Tambien se actualizan los avatares del
detalle/comentarios y los resultados de busqueda al regresar de un perfil.
No requiere columnas ni migraciones nuevas: usa avatar_url y las politicas de
edicion/eliminacion de avatares existentes en Supabase.

Comprobacion en dispositivo: abrir el menu con y sin foto, cambiarla, verla
completa, cancelar la eliminacion, confirmar la eliminacion y volver al feed,
comentarios y busqueda. Las pruebas locales verifican que la limpieza no acepta
rutas de publicaciones, otras cuentas o directorios con traversal.
