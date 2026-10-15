# Logotipo PicGram

Fuente: imagen PicGramInicio.jfif proporcionada por el usuario.
Se preparo una version RGBA sin el fondo cuadriculado con la herramienta integrada
de edicion de imagenes; el original en Downloads no se modifico.

Asset utilizado: app/src/main/res/drawable-nodpi/picgram_wordmark_flat_black.png.
La version actual es negra y plana, sin reflejos ni volumen; se conserva la
version anterior en la copia local de limpieza de 2026-10-13. PicGramWordmark.kt aplica un tinte negro
uniforme y ContentScale.Fit para conservar las
proporciones. No agrega ningun fondo ni panel, en modo claro u oscuro.
Se utiliza en el encabezado del inicio de sesion y en la barra del feed.

El prompt de la version negra actual esta en wordmark-flat-black.md.

Prompt de la extraccion inicial (herramienta integrada, modo background-extraction):

> Use case: background-extraction. Image 1 is the edit target, a supplied PicGram
> wordmark. Remove ONLY the baked-in grey/white checkerboard background, including all
> background inside letter counters and loops. Preserve the exact word "PicGram"
> (P i c G r a m), its connected cursive letter shapes, slant, proportions,
> charcoal-black beveled glossy 3D material, highlights and soft dark edge shadow.
> Do not redesign, replace fonts, change capitalization, add elements or change
> the logo colors. Output a genuine transparent RGBA PNG cutout of this exact
> logo. Frame the complete wordmark tightly in a wide horizontal image, leaving
> only a small uniform transparent safe margin around its full silhouette including
> the descending G loop; no clipping, no large empty top/bottom padding, no
> checkerboard drawn into the pixels, no colored or solid background.
> Intended use: login heading and compact Android toolbar brand image.
