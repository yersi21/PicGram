# Publicaciones con multimedia obligatoria

La app deshabilita Compartir hasta seleccionar una imagen o video. La descripcion
es opcional. La subida y SupabaseDatabase.insertPost tambien validan este requisito.
Los borradores antiguos de solo texto conservan su descripcion y piden adjuntar
multimedia; las publicaciones ya guardadas no se eliminan ni se modifican.

Para aplicar la regla tambien a escrituras directas a Supabase, ejecutar
supabase/migrations/002_posts_require_media.sql en SQL Editor. No se ejecuto desde
esta sesion. NOT VALID conserva filas antiguas, pero exige multimedia en las
nuevas inserciones y actualizaciones. La restriccion valida URL no vacia y tipo
image/video; la lectura del archivo y su MIME se validan en el cliente.
