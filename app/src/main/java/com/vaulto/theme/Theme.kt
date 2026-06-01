// FILE PATH: app/src/main/java/com/vaulto/ui/theme/Theme.kt
//
// ⚠️  CRITICAL ACTION REQUIRED BEFORE BUILDING:
//     DELETE the old file at:
//         app/src/main/java/com/vaulto/theme/Theme.kt   ← WRONG directory
//     Keep ONLY this file at:
//         app/src/main/java/com/vaulto/ui/theme/Theme.kt ← CORRECT
//
//     Android maps directory structure to package names. Having both files
//     on disk produces a "duplicate class VaultoTheme" compile error because
//     both declare `fun VaultoTheme(...)` in different packages but Kotlin's
//     R8/D8 merges them into one DEX class namespace.

package com.vaulto.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Warm Indian palette ───────────────────────────────────────────────────────
val Saffron        = Color(0xFFFF6B00)
val SaffronLight   = Color(0xFFFF8C3A)
val SaffronDark    = Color(0xFFCC5500)
val DeepGreen      = Color(0xFF1B8A4E)
val Cream          = Color(0xFFFFF8F0)
val CardBg         = Color(0xFFFFFFFF)
val TextPrimary    = Color(0xFF1A1A2E)
val TextSecondary  = Color(0xFF6B6B8A)
val DividerColor   = Color(0xFFEEEAE4)
val FamilyBlue     = Color(0xFF3A86FF)
val PersonalPurple = Color(0xFF9C27B0)

val BarColors = listOf(
    Color(0xFFFF6B6B), Color(0xFF4ECDC4), Color(0xFFFFBE0B),
    Color(0xFF3A86FF), Color(0xFFFF006E), Color(0xFFAB47BC),
    Color(0xFF78C552), Color(0xFFFF9F43)
)

private val LightColors = lightColorScheme(
    primary            = Saffron,
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFFFEDD8),
    onPrimaryContainer = SaffronDark,
    secondary          = DeepGreen,
    onSecondary        = Color.White,
    secondaryContainer = Color(0xFFD4F5E5),
    background         = Cream,
    onBackground       = TextPrimary,
    surface            = Color(0xFFFFFBF7),
    onSurface          = TextPrimary,
    surfaceVariant     = Color(0xFFF5EFE6),
    outline            = DividerColor,
    error              = Color(0xFFD32F2F)
)

val AppTypography = Typography(
    displayMedium  = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall  = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 18.sp, lineHeight = 26.sp),
    titleLarge     = TextStyle(fontWeight = FontWeight.Bold,      fontSize = 16.sp, lineHeight = 24.sp),
    titleMedium    = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 22.sp),
    bodyLarge      = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium     = TextStyle(fontWeight = FontWeight.Normal,    fontSize = 14.sp, lineHeight = 22.sp),
    labelLarge     = TextStyle(fontWeight = FontWeight.SemiBold,  fontSize = 13.sp, letterSpacing = 0.2.sp),
    labelSmall     = TextStyle(fontWeight = FontWeight.Medium,    fontSize = 11.sp, letterSpacing = 0.4.sp)
)

@Composable
fun VaultoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, typography = AppTypography, content = content)
}
