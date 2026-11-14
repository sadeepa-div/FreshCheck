package com.freshcheck.app.ui.theme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Forest, onPrimary = Color.White, secondary = Fresh,
    secondaryContainer = Color(0xFFD9EEDC), onSecondaryContainer = Color(0xFF103C28),
    surfaceVariant = Color(0xFFE1E8DA), outlineVariant = Color(0xFFC4D0BF),
    background = Ivory, surface = Ivory, surfaceContainer = Color(0xFFEBF0E4),
    primaryContainer = Color(0xFFD9EEDC), onPrimaryContainer = Color(0xFF103C28),
    onBackground = Color(0xFF192D22), onSurface = Color(0xFF192D22),
    onSurfaceVariant = Color(0xFF526357), outline = Color(0xFF748577),
    tertiary = Color(0xFF79520C), error = Color(0xFFAC3833)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF89D8A7), onPrimary = Color(0xFF00391F),
    secondaryContainer = Color(0xFF224D35), onSecondaryContainer = Color(0xFFD9EEDC),
    surfaceVariant = Color(0xFF334539), outlineVariant = Color(0xFF435549),
    secondary = Fresh, background = Color(0xFF101B15), surface = Color(0xFF101B15),
    surfaceContainer = Color(0xFF1D2E23), primaryContainer = Color(0xFF224D35),
    onPrimaryContainer = Color(0xFFD9EEDC), onBackground = Color(0xFFE1EBDF),
    onSurface = Color(0xFFE1EBDF), onSurfaceVariant = Color(0xFFBCCABD),
    tertiary = Amber, error = Coral
)
@Composable
fun FreshCheckTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}
