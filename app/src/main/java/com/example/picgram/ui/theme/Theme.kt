package com.example.picgram.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.picgram.data.PicGramApplication

private val GlassDarkColors = darkColorScheme(
    primary = Color(0xFFCDD8FF), onPrimary = Color(0xFF16254D),
    secondary = Color(0xFFB9DFE9), tertiary = Pink80,
    background = Color.Transparent, onBackground = Color(0xFFF5F7FF),
    surface = Color(0xFF1B2942), onSurface = Color(0xFFF5F7FF),
    surfaceVariant = Color(0x8C293955), onSurfaceVariant = Color(0xFFCBD4E7),
    primaryContainer = Color(0xFF394F85), onPrimaryContainer = Color(0xFFF5F7FF),
    secondaryContainer = Color(0x80405171), onSecondaryContainer = Color(0xFFE8EEFF),
    outline = Color(0xFF8593B2), outlineVariant = Color(0x668593B2),
    surfaceContainer = Color(0xEA1B2942), surfaceContainerHigh = Color(0xF025334C)
)

private val GlassLightColors = lightColorScheme(
    primary = Color(0xFF354E89), onPrimary = Color.White,
    secondary = Color(0xFF366879), tertiary = Purple40,
    background = Color.Transparent, onBackground = Color(0xFF17233C),
    surface = Color(0xFFF3F6FC), onSurface = Color(0xFF17233C),
    surfaceVariant = Color(0x99DFE7F6), onSurfaceVariant = Color(0xFF4C5A72),
    primaryContainer = Color(0xFFAFC5F3), onPrimaryContainer = Color(0xFF172D58),
    secondaryContainer = Color(0xBFD7E9EF), onSecondaryContainer = Color(0xFF243E4D),
    outline = Color(0xFF74839F), outlineVariant = Color(0x808895B1),
    surfaceContainer = Color(0xF2F3F6FC), surfaceContainerHigh = Color(0xF5FFFFFF)
)

@Composable
fun PicGramTheme(darkTheme: Boolean? = null, dynamicColor: Boolean = false,
                 content: @Composable () -> Unit) {
    val appearance = (LocalContext.current.applicationContext as? PicGramApplication)?.container?.theme
    val preference = appearance?.darkMode?.collectAsStateWithLifecycle()?.value
    val resolvedDarkTheme = darkTheme ?: preference ?: isSystemInDarkTheme()
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (resolvedDarkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dynamic.copy(background = Color.Transparent)
        }
        resolvedDarkTheme -> GlassDarkColors
        else -> GlassLightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !resolvedDarkTheme
                isAppearanceLightNavigationBars = !resolvedDarkTheme
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = Typography, shapes = Shapes(
        extraSmall = RoundedCornerShape(18.dp), small = RoundedCornerShape(20.dp),
        medium = RoundedCornerShape(24.dp), large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )) {
        CompositionLocalProvider(LocalGlassDark provides resolvedDarkTheme) {
            Box(Modifier.fillMaxSize()) {
                GlassBackdrop(resolvedDarkTheme, Modifier.fillMaxSize())
                content()
            }
        }
    }
}
