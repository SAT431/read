package dev.folio.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightFolioColors = lightColorScheme(
    primary = Color(0xFF315B51), onPrimary = Color.White,
    background = Color(0xFFF7F6F2), surface = Color(0xFFFFFEFC),
    onBackground = Color(0xFF242729), onSurface = Color(0xFF242729), onSurfaceVariant = Color(0xFF62666B),
    surfaceContainer = Color(0xFFF0EFEB), surfaceContainerLow = Color(0xFFFAF9F6),
    secondary = Color(0xFF605577), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEAE5F2), onSecondaryContainer = Color(0xFF41354E),
    primaryContainer = Color(0xFFDDEAE4), onPrimaryContainer = Color(0xFF1E443A), outlineVariant = Color(0xFFE1E0DA),
)
internal val DarkFolioColors = darkColorScheme(
    primary = Color(0xFFA8D5C3), onPrimary = Color(0xFF163B2D), background = Color(0xFF181B1D),
    onBackground = Color(0xFFEAE9E5), surface = Color(0xFF212527), onSurface = Color(0xFFEAE9E5),
    onSurfaceVariant = Color(0xFFBEC3C4), surfaceContainer = Color(0xFF2C3032), surfaceContainerLow = Color(0xFF1D2022),
    secondary = Color(0xFFD0BEE8), secondaryContainer = Color(0xFF41384D), onSecondaryContainer = Color(0xFFE9DDF9),
    primaryContainer = Color(0xFF2E4D42), onPrimaryContainer = Color(0xFFCEEBDD), outlineVariant = Color(0xFF414647),
)
