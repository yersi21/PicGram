package com.example.picgram.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

internal val LocalGlassDark = staticCompositionLocalOf { false }

/** Native Compose glass styling; no bitmap snapshots and no blur applied to text or photos. */
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(26.dp), accent: Boolean = false): Modifier = composed {
    val dark = LocalGlassDark.current
    val tint = if (accent) MaterialTheme.colorScheme.primaryContainer
        else if (dark) Color(0xFF18243C) else Color.White
    val rim = if (dark) Color.White.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.94f)
    this.shadow(if (accent) 8.dp else 6.dp, shape, clip = false,
        ambientColor = Color(0xFF0A1730).copy(alpha = 0.12f),
        spotColor = Color(0xFF0A1730).copy(alpha = 0.18f))
        .clip(shape)
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val fill = Brush.linearGradient(listOf(
                tint.copy(alpha = if (accent) 0.85f else if (dark) 0.78f else 0.68f),
                tint.copy(alpha = if (accent) 0.68f else if (dark) 0.62f else 0.48f)))
            val reflection = Brush.verticalGradient(listOf(
                Color.White.copy(alpha = if (dark) 0.09f else 0.28f), Color.Transparent),
                endY = size.height * 0.55f)
            val edge = Brush.linearGradient(listOf(rim, rim.copy(alpha = 0.07f), rim.copy(alpha = 0.26f)),
                start = Offset.Zero, end = Offset(size.width, size.height))
            onDrawWithContent {
                drawOutline(outline, fill)
                drawOutline(outline, reflection)
                drawContent()
                drawOutline(outline, edge, style = Stroke(1.dp.toPx()))
            }
        }
}

@Composable
internal fun GlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(if (dark)
            listOf(Color(0xFF080F20), Color(0xFF172341), Color(0xFF1C1532))
        else listOf(Color(0xFFECF2FC), Color(0xFFE1EAF7), Color(0xFFF6ECF5))))
        fun glow(color: Color, center: Offset, radius: Float) {
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center, radius), radius, center)
        }
        glow(Color(0xFF6D8AFF).copy(alpha = if (dark) 0.24f else 0.25f),
            Offset(size.width * 0.08f, size.height * 0.18f), size.width * 0.9f)
        glow(Color(0xFFAE70D5).copy(alpha = if (dark) 0.22f else 0.20f),
            Offset(size.width * 1.08f, size.height * 0.55f), size.width * 0.95f)
        glow(Color(0xFF53C6DA).copy(alpha = if (dark) 0.12f else 0.18f),
            Offset(size.width * 0.15f, size.height * 0.96f), size.width * 0.8f)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassTopAppBar(title: @Composable () -> Unit,
                   navigationIcon: @Composable () -> Unit = {},
                   actions: @Composable RowScope.() -> Unit = {}) {
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        TopAppBar(title = title, navigationIcon = navigationIcon, actions = actions,
            modifier = Modifier.fillMaxWidth().liquidGlass(),
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent))
    }
}

@Composable
fun GlassButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Button(onClick = onClick, enabled = enabled, shape = shape,
        modifier = modifier.heightIn(min = 50.dp).liquidGlass(shape, accent = true),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp), content = content)
}

@Composable
fun glassTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.40f),
    disabledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.20f),
    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f),
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary
)
