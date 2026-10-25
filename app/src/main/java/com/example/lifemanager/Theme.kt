package com.example.lifemanager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val IncomeColor = Color(0xFF16A34A)
val ExpenseColor = Color(0xFFDC2626)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF042F2E),
    secondary = Color(0xFF0F766E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF042F2E),
    background = Color(0xFFF1F5F4),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFF1F5F4),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8E7),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainer = Color.White,
    surfaceContainerHighest = Color.White,
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF134E4A),
    onSecondaryContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0B1211),
    onBackground = Color(0xFFE6F1EF),
    surface = Color(0xFF0B1211),
    onSurface = Color(0xFFE6F1EF),
    surfaceVariant = Color(0xFF1E2B29),
    onSurfaceVariant = Color(0xFFA7B8B5),
    surfaceContainer = Color(0xFF111A19),
    surfaceContainerHighest = Color(0xFF172321),
    outline = Color(0xFF3B4B48),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val typography = Typography(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold)
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = typography,
        content = content
    )
}
