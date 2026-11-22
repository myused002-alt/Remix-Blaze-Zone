package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val BlazeDarkColorScheme = darkColorScheme(
    primary = BlazeOrange,
    onPrimary = Color(0xFF140800),
    primaryContainer = Color(0xFF471F00),
    onPrimaryContainer = BlazeOrangeBright,
    secondary = BlazeGold,
    onSecondary = Color(0xFF1A1200),
    secondaryContainer = Color(0xFF3E2D00),
    onSecondaryContainer = BlazeGold,
    tertiary = ZoneCyan,
    onTertiary = Color(0xFF001F24),
    tertiaryContainer = Color(0xFF003B44),
    onTertiaryContainer = ZoneCyan,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = GunmetalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TacticalPanel,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = GunmetalSurface,
    surfaceContainer = TacticalPanel,
    surfaceContainerHigh = TacticalPanelHigh,
    outline = TacticalBorder,
    outlineVariant = Color(0xFF252D3D),
    error = CrimsonDanger,
    onError = Color.White,
    errorContainer = Color(0xFF4D0B18),
    onErrorContainer = Color(0xFFFFB3BE)
)

private val BlazeShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BlazeZoneTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BlazeDarkColorScheme,
        typography = BlazeTypography,
        shapes = BlazeShapes,
        content = content
    )
}
