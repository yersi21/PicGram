# Liquid Glass en PicGram

Adaptacion visual para Android hecha con Jetpack Compose: superficies translucidas,
reflejos suaves, bordes de cristal, esquinas amplias y fondo con luces difusas.
Incluye modo claro y oscuro. Se aplica al feed, barras superiores, perfil,
formularios, botones principales y barra de comentarios.

Es un estilo inspirado en Liquid Glass. No reproduce el material nativo de Apple
ni refracta/desenfoca en tiempo real el contenido detras de cada panel. Las fotos,
videos y el texto se dibujan nitidos. No agrega dependencias ni eleva el minSdk 29.

Los componentes reutilizables estan en `ui/theme/LiquidGlass.kt`. Las vistas
previas de acceso claro/oscuro y feed estan en `ui/theme/LiquidGlassPreview.kt`;
pueden abrirse en la vista Design/Split de Android Studio sin iniciar sesion.
Los colores y esquinas se configuran en `ui/theme/Theme.kt`.

Para comprobarlo en la app: ejecutar la version debug, cambiar el tema del sistema,
abrir perfiles desde sus miniaturas y escribir un comentario con el teclado abierto.
Tambien comprobar campos de registro y controles deshabilitados mientras se carga.
