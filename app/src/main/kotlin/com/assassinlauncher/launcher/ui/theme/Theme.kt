package com.assassinlauncher.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.assassinlauncher.launcher.R

/**
 * Assassin Launcher's real visual identity - the "Phase 3" work the
 * previous placeholder theme's own comment explicitly deferred. Dark by
 * design, not by default: every reference launcher in this project's own
 * history is dark-themed (matches extended-use gaming context, and OLED
 * battery cost), and "Assassin" as a name calls for precision and
 * contrast rather than a bright, busy interface.
 *
 * Color: obsidian background with a cool undertone (not flat black -
 * flat black has no depth), a single considered crimson as the brand
 * accent, and a muted gold pulled directly from Minecraft's own world
 * (ingots, XP) rather than an arbitrary second accent. Error intentionally
 * leans orange-coral rather than reusing the brand crimson, so "this is
 * the main action" and "this is a warning" never read as the same color.
 *
 * Type: Space Grotesk for anything that needs presence (headlines,
 * numbers, the wordmark) - a geometric face with just enough edge to
 * suit the name without tipping into novelty. Manrope for everything
 * read at length (body copy, labels, dense settings text) - warmer and
 * more neutral at small sizes than Space Grotesk would be. Neither is
 * Android's default Roboto; both are real OFL-licensed families sourced
 * from their actual upstream repos, not assumed available.
 */

// ---- Color tokens ----
val ObsidianBackground = Color(0xFF0D0E12)
val SlateSurface = Color(0xFF1A1C23)
val SlateSurfaceHigh = Color(0xFF242730)
val BladeRed = Color(0xFFE1424C)
val BladeRedContainer = Color(0xFF5C1B20)
val EmberGold = Color(0xFFD9A94E)
val CoralError = Color(0xFFFF6B4A)
val MistWhite = Color(0xFFEDEEF2)
val AshGray = Color(0xFF9497A6)
val HairlineOutline = Color(0xFF33363F)

private val LauncherColorScheme = darkColorScheme(
    background = ObsidianBackground,
    onBackground = MistWhite,
    surface = SlateSurface,
    onSurface = MistWhite,
    surfaceVariant = SlateSurfaceHigh,
    onSurfaceVariant = AshGray,
    primary = BladeRed,
    onPrimary = MistWhite,
    primaryContainer = BladeRedContainer,
    onPrimaryContainer = MistWhite,
    secondary = EmberGold,
    onSecondary = ObsidianBackground,
    tertiary = EmberGold,
    onTertiary = ObsidianBackground,
    error = CoralError,
    onError = ObsidianBackground,
    outline = HairlineOutline,
    outlineVariant = HairlineOutline,
)

private val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_light, FontWeight.Light),
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

private val Manrope = FontFamily(
    Font(R.font.manrope_light, FontWeight.Light),
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

private val LauncherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 52.sp, lineHeight = 58.sp, letterSpacing = (-0.5).sp
    ),
    displayMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.25).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 32.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 18.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp
    ),
)

@Composable
fun AssassinLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LauncherColorScheme,
        typography = LauncherTypography,
        content = content
    )
}
