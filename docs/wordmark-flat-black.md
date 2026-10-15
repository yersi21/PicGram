# PicGram negro mate

Herramienta: imagegen integrada, edicion de la version anterior del logotipo.
Asset final: app/src/main/res/drawable-nodpi/picgram_wordmark_flat_black.png.
Se usa en inicio de sesion y feed. Se mantiene la transparencia y se aplica
ColorFilter.tint(Color.Black) al mostrarlo para garantizar un color negro uniforme.
No se agrega ningun fondo, brillo, borde, relieve ni sombra al logotipo.
La version anterior ya no se empaqueta en la app; está en la copia local de
limpieza de 2026-10-13.

Prompt final utilizado:

Use case: precise-object-edit. Image 1 is the existing PicGram logo edit target. Change only its material and shading: make the entire letter silhouette a completely flat solid pure black (#000000) wordmark on genuine transparent RGBA background. Exact text: "PicGram" (P i c G r a m). Preserve the existing cursive lettering, slant, connected strokes, proportions, letter placement, large capital P, capital G with its descending loop, complete outline and framing as closely as possible. Remove ALL bevels, extrusion, inner outlines, gray highlights, glossy streaks, reflections, gradients, shadows, glow, texture, and 3D appearance. The letters must look like a single solid black ink silhouette, with smooth antialiased outside edges only. All letter counters and open spaces remain fully transparent. Keep the complete wordmark including G descender unclipped and tightly framed with a small transparent safe margin. No checkerboard, no white panel, no background, no additional graphics or text.

