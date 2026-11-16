package com.sampcity.social.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sampcity.social.R

// Colores de la web de SampCity
val Rojo = Color(0xFFE8392F)
val RojoOscuro = Color(0xFF9E150F)
val Rosa = Color(0xFFF59D99)

private val Dark = darkColorScheme(
    primary = Rojo, onPrimary = Color.White, primaryContainer = Color(0xFF5B1410), onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Rosa, onSecondary = Color(0xFF3A0B09), secondaryContainer = Color(0xFF3B2227), onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFF6EE7A1), onTertiary = Color(0xFF00391D),
    background = Color(0xFF0E0A0C), onBackground = Color(0xFFF6F1F2),
    surface = Color(0xFF181115), onSurface = Color(0xFFF6F1F2), surfaceVariant = Color(0xFF251A20), onSurfaceVariant = Color(0xFFB3A3A8),
    surfaceContainerLowest = Color(0xFF0E0A0C), surfaceContainerLow = Color(0xFF140E11), surfaceContainer = Color(0xFF181115),
    surfaceContainerHigh = Color(0xFF211820), surfaceContainerHighest = Color(0xFF2B1F26),
    outline = Color(0xFF6B5A60), outlineVariant = Color(0xFF33252B), error = Color(0xFFFF8B84), onError = Color(0xFF410002),
)
private val Light = lightColorScheme(
    primary = Color(0xFFC0312A), onPrimary = Color.White, primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFFC0312A), onSecondary = Color.White, secondaryContainer = Color(0xFFF0E3E6), onSecondaryContainer = Color(0xFF241A1E),
    tertiary = Color(0xFF1A7F45), onTertiary = Color.White,
    background = Color(0xFFF8F3F4), onBackground = Color(0xFF241A1E),
    surface = Color.White, onSurface = Color(0xFF241A1E), surfaceVariant = Color(0xFFF4ECEE), onSurfaceVariant = Color(0xFF6A5A60),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF7F8), surfaceContainer = Color(0xFFF4ECEE),
    surfaceContainerHigh = Color(0xFFEFE5E8), surfaceContainerHighest = Color(0xFFE9DDE1),
    outline = Color(0xFF8E7B82), outlineVariant = Color(0xFFE3D4D8), error = Color(0xFFB3261E), onError = Color.White,
)

val Poppins = FontFamily(Font(R.font.poppins_regular, FontWeight.Normal), Font(R.font.poppins_semibold, FontWeight.SemiBold), Font(R.font.poppins_bold, FontWeight.Bold))
val Russo = FontFamily(Font(R.font.russo_one, FontWeight.Normal))

private val base = Typography()
private val Type = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Russo), displayMedium = base.displayMedium.copy(fontFamily = Russo), displaySmall = base.displaySmall.copy(fontFamily = Russo),
    headlineLarge = base.headlineLarge.copy(fontFamily = Russo), headlineMedium = base.headlineMedium.copy(fontFamily = Russo), headlineSmall = base.headlineSmall.copy(fontFamily = Russo),
    titleLarge = TextStyle(fontFamily = Russo, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontSize = 11.sp, lineHeight = 15.sp),
)
private val Round = Shapes(extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(14.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(26.dp))

val LocalDark = staticCompositionLocalOf { true }

@Composable
fun SampTheme(mode: String = "system", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    androidx.compose.runtime.CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = Type, shapes = Round, content = content)
    }
}
