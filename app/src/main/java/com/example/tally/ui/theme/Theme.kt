package com.example.tally.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

// Every role is set so no Material default purple leaks into components. The brand colors are
// also in res/values/colors.xml, for the launcher icon and splash screen.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5EBD3),
    onPrimaryContainer = Color(0xFF002116),
    inversePrimary = Color(0xFF8CD5B5),
    secondary = Color(0xFF4C635A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCEE9DC),
    onSecondaryContainer = Color(0xFF082018),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2C1800),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8F5),
    onBackground = Color(0xFF191C1A),
    surface = Color(0xFFF7F8F5),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404944),
    surfaceTint = Color(0xFF1E6B52),
    inverseSurface = Color(0xFF2E312F),
    inverseOnSurface = Color(0xFFEFF1EE),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C2),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF7F8F5),
    surfaceDim = Color(0xFFD8DBD7),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F0),
    surfaceContainer = Color(0xFFEBEFEA),
    surfaceContainerHigh = Color(0xFFE6E9E4),
    surfaceContainerHighest = Color(0xFFE0E3DF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD5B5),
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA8F2D0),
    inversePrimary = Color(0xFF1E6B52),
    secondary = Color(0xFFB3CCC0),
    onSecondary = Color(0xFF1F352C),
    secondaryContainer = Color(0xFF354B42),
    onSecondaryContainer = Color(0xFFCEE9DC),
    tertiary = Color(0xFFFFB94E),
    onTertiary = Color(0xFF482A00),
    tertiaryContainer = Color(0xFF673E00),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1412),
    onBackground = Color(0xFFDFE4E0),
    surface = Color(0xFF0F1412),
    onSurface = Color(0xFFDFE4E0),
    surfaceVariant = Color(0xFF404944),
    onSurfaceVariant = Color(0xFFC0C9C2),
    surfaceTint = Color(0xFF8CD5B5),
    inverseSurface = Color(0xFFDFE4E0),
    inverseOnSurface = Color(0xFF2C322F),
    outline = Color(0xFF8A938D),
    outlineVariant = Color(0xFF404944),
    scrim = Color.Black,
    surfaceBright = Color(0xFF353A37),
    surfaceDim = Color(0xFF0F1412),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D1A),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303633),
)

/** Digits of equal width, so amounts line up in lists. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

// Dynamic (wallpaper) color is intentionally not used so the Tally palette always shows.
@Composable
fun TallyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
