package org.sahara.app.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// =============================================================================
// SHEGUARD MODERN PINK + WHITE COLOR SYSTEM
// =============================================================================

object SheGuardColors {
    // Primary Brand & Accents (Rose Pink + Warm Accents)
    val Primary = Color(0xFFE11D48)          // Elegant Rose Pink
    val PrimaryDark = Color(0xFFBE123C)      // Deep Rose Pink
    val PrimaryLight = Color(0xFFFDA4AF)     // Soft Pastel Pink
    val PrimaryContainer = Color(0xFFFFF1F2) // Delicate Blush Pink Container

    val CyanAccent = Color(0xFF0284C7)       // Sky / Mesh Blue
    val CyanLight = Color(0xFF38BDF8)        // Soft Sky
    val CyanContainer = Color(0xFFE0F2FE)    // Delicate Sky Container

    // Semantic Pipeline States
    val EmeraldSuccess = Color(0xFF059669)   // Multi-signal Verified / Safe
    val EmeraldBg = Color(0xFFECFDF5)        // Soft Emerald Container
    val EmeraldText = Color(0xFF065F46)      // Deep Emerald Text
    val EmeraldBorder = Color(0xFFA7F3D0)    // Emerald Border

    val AmberWarning = Color(0xFFD97706)     // Candidate Pattern / Attention
    val AmberBg = Color(0xFFFFFBEB)          // Soft Amber Container
    val AmberText = Color(0xFF92400E)        // Deep Amber Text
    val AmberBorder = Color(0xFFFDE68A)      // Amber Border

    val RoseDanger = Color(0xFFE11D48)       // Rising Pattern Alert / Immediate
    val RoseBg = Color(0xFFFFF1F2)           // Soft Rose Container
    val RoseText = Color(0xFF9F1239)         // Deep Rose Text
    val RoseBorder = Color(0xFFFECDD3)       // Rose Border

    val VioletSeal = Color(0xFF7C3AED)       // Keystore / Merkle Crypto Proof
    val VioletBg = Color(0xFFF5F3FF)         // Soft Violet Container
    val VioletText = Color(0xFF5B21B6)       // Deep Violet Text

    // Light Pink & White Surfaces & Backgrounds
    val Background = Color(0xFFFFFBFB)       // Ultra-clean Soft Warm White
    val SurfaceCard = Color(0xFFFFFFFF)      // Pure White Card
    val SurfaceElevated = Color(0xFFFFF5F6)  // Soft Blush Elevated Surface
    val SurfaceSubtle = Color(0xFFFFF0F2)    // Inset Subtle Blush
    val BorderSubtle = Color(0xFFF7D5DC)     // Crisp Soft Pink Border
    val BorderHighlight = Color(0xFFFDA4AF)  // Accent Rose Border

    // Typography
    val TextPrimary = Color(0xFF1E1B22)      // Deep Charcoal Black
    val TextSecondary = Color(0xFF5F5864)    // Warm Charcoal Gray
    val TextMuted = Color(0xFF948A96)        // Soft Muted Gray
    val TextInverse = Color(0xFFFFFFFF)      // Pure White

    // Shadows
    val ShadowTint = Color(0x18E11D48)
}

// Backward-compatible alias for existing components
object SaharaColors {
    val PinkPrimary = SheGuardColors.Primary
    val SoftPink = SheGuardColors.PrimaryContainer
    val VerySoftPink = SheGuardColors.SurfaceElevated
    val PinkDeep = SheGuardColors.PrimaryDark

    val SoftBlue = SheGuardColors.CyanAccent
    val VerySoftBlue = SheGuardColors.CyanContainer
    val BlueMuted = SheGuardColors.CyanAccent

    val SoftYellow = SheGuardColors.AmberWarning
    val VerySoftYellow = SheGuardColors.AmberBg
    val YellowDark = SheGuardColors.AmberText

    val WarmWhite = SheGuardColors.Background
    val PureWhite = SheGuardColors.SurfaceCard
    val SurfaceCard = SheGuardColors.SurfaceCard
    val SurfaceSubtle = SheGuardColors.SurfaceElevated
    val BorderSubtle = SheGuardColors.BorderSubtle

    val TextPrimary = SheGuardColors.TextPrimary
    val TextSecondary = SheGuardColors.TextSecondary
    val TextDisabled = SheGuardColors.TextMuted
    val TextInverse = SheGuardColors.TextInverse

    val SuccessGreen = SheGuardColors.EmeraldSuccess
    val SuccessGreenBg = SheGuardColors.EmeraldBg
    val DangerCoral = SheGuardColors.RoseDanger
    val DangerCoralBg = SheGuardColors.RoseBg

    val ShadowTint = SheGuardColors.ShadowTint
}

// =============================================================================
// TYPOGRAPHY HIERARCHY
// =============================================================================

val SheGuardTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        color = SheGuardColors.TextPrimary,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        color = SheGuardColors.TextPrimary,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = SheGuardColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        color = SheGuardColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = SheGuardColors.TextSecondary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SheGuardColors.TextSecondary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        color = SheGuardColors.TextMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        color = SheGuardColors.TextPrimary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        color = SheGuardColors.TextSecondary
    )
)

val SaharaTypography = SheGuardTypography

// =============================================================================
// SHAPES
// =============================================================================

val SheGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

val SaharaShapes = SheGuardShapes

val SheGuardColorScheme = lightColorScheme(
    primary = SheGuardColors.Primary,
    onPrimary = Color.White,
    primaryContainer = SheGuardColors.PrimaryContainer,
    onPrimaryContainer = SheGuardColors.PrimaryDark,
    secondary = SheGuardColors.CyanAccent,
    onSecondary = Color.White,
    secondaryContainer = SheGuardColors.CyanContainer,
    onSecondaryContainer = SheGuardColors.CyanAccent,
    tertiary = SheGuardColors.AmberWarning,
    onTertiary = Color.White,
    background = SheGuardColors.Background,
    onBackground = SheGuardColors.TextPrimary,
    surface = SheGuardColors.SurfaceCard,
    onSurface = SheGuardColors.TextPrimary,
    surfaceVariant = SheGuardColors.SurfaceElevated,
    onSurfaceVariant = SheGuardColors.TextSecondary,
    outline = SheGuardColors.BorderSubtle
)

val SaharaColorScheme = SheGuardColorScheme

@Composable
fun SheGuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SheGuardColorScheme,
        typography = SheGuardTypography,
        shapes = SheGuardShapes,
        content = content
    )
}

@Composable
fun SaharaTheme(content: @Composable () -> Unit) {
    SheGuardTheme(content = content)
}
