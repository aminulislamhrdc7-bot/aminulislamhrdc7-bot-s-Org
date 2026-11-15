package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = OnEmeraldPrimaryContainer,
    secondary = GoldSecondary,
    onSecondary = OnGoldSecondary,
    secondaryContainer = GoldSecondaryContainer,
    onSecondaryContainer = OnGoldSecondaryContainer,
    tertiary = TealTertiary,
    onTertiary = OnTealTertiary,
    tertiaryContainer = TealTertiaryContainer,
    onTertiaryContainer = OnTealTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ExpenseCoral,
    errorContainer = ExpenseCoralContainer
)

private val DarkColors = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = OnDarkEmeraldPrimary,
    primaryContainer = DarkEmeraldPrimaryContainer,
    onPrimaryContainer = OnDarkEmeraldPrimaryContainer,
    secondary = DarkGoldSecondary,
    onSecondary = OnDarkGoldSecondary,
    secondaryContainer = DarkGoldSecondaryContainer,
    onSecondaryContainer = OnDarkGoldSecondaryContainer,
    tertiary = DarkTealTertiary,
    onTertiary = OnDarkTealTertiary,
    tertiaryContainer = DarkTealTertiaryContainer,
    onTertiaryContainer = OnDarkTealTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ExpenseCoralLight,
    errorContainer = ExpenseCoralDarkContainer
)

val MyHisabShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyHisabTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) DarkColors else LightColors,
        typography = MyHisabTypography,
        shapes = MyHisabShapes,
        content = content
    )
}
