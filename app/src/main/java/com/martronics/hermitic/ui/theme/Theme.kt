package com.martronics.hermitic.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val WalnutBg = Color(0xFF1C1410)
private val WalnutSurface = Color(0xFF2C2019)
private val WalnutSurfaceVariant = Color(0xFF241A14)
private val WalnutText = Color(0xFFF3E9DC)
private val WalnutMuted = Color(0xFFC4AF9A)
private val OakAccentDark = Color(0xFFD9A066)
private val WalnutOutline = Color(0xFF3D2D22)

private val CreamBg = Color(0xFFF6EFE4)
private val CreamSurface = Color(0xFFFBF7F0)
private val CreamSurfaceVariant = Color(0xFFEFE4D3)
private val CreamText = Color(0xFF2A1D15)
private val CreamMuted = Color(0xFF6A5545)
private val OakAccentLight = Color(0xFFC8843E)
private val CreamOutline = Color(0xFFE2D3BE)

private val DarkColors = darkColorScheme(
    primary = OakAccentDark,
    onPrimary = Color(0xFF24170F),
    secondary = Color(0xFFE7B57E),
    onSecondary = Color(0xFF24170F),
    background = WalnutBg,
    onBackground = WalnutText,
    surface = WalnutSurface,
    onSurface = WalnutText,
    surfaceVariant = WalnutSurfaceVariant,
    onSurfaceVariant = WalnutMuted,
    outline = WalnutOutline,
    error = Color(0xFFF07B72),
    onError = Color(0xFF2C0B09),
)

private val LightColors = lightColorScheme(
    primary = OakAccentLight,
    onPrimary = Color(0xFF24170F),
    secondary = Color(0xFF8F5420),
    onSecondary = Color(0xFFFBF7F0),
    background = CreamBg,
    onBackground = CreamText,
    surface = CreamSurface,
    onSurface = CreamText,
    surfaceVariant = CreamSurfaceVariant,
    onSurfaceVariant = CreamMuted,
    outline = CreamOutline,
    error = Color(0xFFA82C24),
    onError = Color(0xFFFFFFFF),
)

private val HermiticTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

@Composable
fun HermiticTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HermiticTypography,
        content = content,
    )
}
