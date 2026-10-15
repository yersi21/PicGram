# Vista previa y video adaptable

Se utiliza Media3/ExoPlayer 1.11.1 con PlayerView y una superficie TextureView,
apropiada para desplazamiento y recorte dentro de Compose. El reproductor conserva
las proporciones con RESIZE_MODE_FIT y limita su altura al 65% de la pantalla.
Los videos verticales pueden mostrar bandas laterales para verse completos.

Aplica a Nueva Publicacion, feed y detalle. Los controles incluyen reproduccion,
pausa, avance y pantalla completa. La pantalla completa usa el mismo reproductor,
conservando su posicion; al salir vuelve al lugar anterior. No reproduce audio
automaticamente, se pausa al enviar la app al fondo y se libera al salir del componente.

Se extrae un fotograma con MediaMetadataRetriever para vista previa y cuadricula
del perfil. Se limita su tamaño a 640 px, se mantienen hasta 12 MB de cache en
memoria y se permiten dos extracciones simultaneas. Si el proveedor o codec no
permite extraer el fotograma, el reproductor puede mostrar su propio primer frame
y el perfil conserva un indicador de video. No requiere columnas nuevas en Supabase.

Referencias oficiales: [PlayerView](https://developer.android.com/media/media3/ui/playerview)
y [MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever).

Verificacion local: pruebas de proporciones horizontales/verticales, metadatos
rotados, pixeles no cuadrados y limites de pantalla. Para comprobar la decodificacion
real: seleccionar MP4 horizontal y vertical, reproducir la vista previa, publicar,
abrir feed/detalle, entrar y salir de pantalla completa, girar el telefono y revisar
la miniatura del perfil. El soporte de cada formato depende del codec del dispositivo.
