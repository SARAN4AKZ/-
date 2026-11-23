package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class LedgerSemanticColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val accentBlue: Color,
    val accentBlueContainer: Color,
    val onAccentBlueContainer: Color
)

val LocalLedgerColors = staticCompositionLocalOf {
    LedgerSemanticColors(
        success = EmeraldSeal,
        successContainer = EmeraldContainer,
        onSuccessContainer = OnEmeraldContainer,
        warning = AmberDraft,
        warningContainer = AmberContainer,
        onWarningContainer = OnAmberContainer,
        accentBlue = CobaltAccent,
        accentBlueContainer = CobaltContainer,
        onAccentBlueContainer = OnCobaltContainer
    )
}

private val LightColorScheme = lightColorScheme(
    primary = NavyInk,
    onPrimary = PurePaper,
    primaryContainer = CobaltContainer,
    onPrimaryContainer = OnCobaltContainer,
    secondary = SlateSecondary,
    onSecondary = PurePaper,
    secondaryContainer = SlateContainer,
    onSecondaryContainer = OnSlateContainer,
    tertiary = CobaltAccent,
    onTertiary = PurePaper,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = AlabasterCanvas,
    onBackground = NavyInk,
    surface = PurePaper,
    onSurface = NavyInk,
    surfaceVariant = SoftPaperVariant,
    onSurfaceVariant = MutedText,
    outline = LedgerOutline,
    outlineVariant = LedgerOutlineVariant,
    error = CrimsonError,
    onError = PurePaper,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkCobalt,
    onPrimary = NavyInkDark,
    primaryContainer = DarkCobaltContainer,
    onPrimaryContainer = DarkTextPrimary,
    secondary = DarkTextMuted,
    onSecondary = DarkCanvas,
    secondaryContainer = DarkPaperVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = DarkEmerald,
    onTertiary = DarkCanvas,
    tertiaryContainer = DarkEmeraldContainer,
    onTertiaryContainer = DarkTextPrimary,
    background = DarkCanvas,
    onBackground = DarkTextPrimary,
    surface = DarkPaper,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkPaperVariant,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFF87171),
    onError = DarkCanvas,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (darkTheme) {
        LedgerSemanticColors(
            success = DarkEmerald,
            successContainer = DarkEmeraldContainer,
            onSuccessContainer = Color(0xFFA7F3D0),
            warning = DarkAmber,
            warningContainer = DarkAmberContainer,
            onWarningContainer = Color(0xFFFDE68A),
            accentBlue = DarkCobalt,
            accentBlueContainer = DarkCobaltContainer,
            onAccentBlueContainer = Color(0xFFBFDBFE)
        )
    } else {
        LedgerSemanticColors(
            success = EmeraldSeal,
            successContainer = EmeraldContainer,
            onSuccessContainer = OnEmeraldContainer,
            warning = AmberDraft,
            warningContainer = AmberContainer,
            onWarningContainer = OnAmberContainer,
            accentBlue = CobaltAccent,
            accentBlueContainer = CobaltContainer,
            onAccentBlueContainer = OnCobaltContainer
        )
    }

    CompositionLocalProvider(LocalLedgerColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
