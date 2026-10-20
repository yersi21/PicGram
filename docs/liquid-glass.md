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

El inicio incluye un botón de luna/sol para activar o desactivar el modo oscuro.
La elección se aplica a todas las pantallas y se guarda localmente en preferencias
de apariencia, independientes de la sesión; se conserva al reiniciar o cerrar sesión.
Antes de la primera elección se usa el tema del teléfono. El cambio es local:
no requiere internet ni migraciones de Supabase.
En pantallas estrechas, «Actualizar» y «Cerrar sesión» están en «Más opciones»
para mantener visibles el logotipo y los controles con áreas táctiles accesibles.

Para comprobarlo en la app: ejecutar la version debug, pulsar el botón de luna/sol,
abrir otras pantallas, volver y reiniciar la app para comprobar la persistencia;
abrir perfiles desde sus miniaturas y escribir un comentario con el teclado abierto.
Tambien comprobar campos de registro y controles deshabilitados mientras se carga.
