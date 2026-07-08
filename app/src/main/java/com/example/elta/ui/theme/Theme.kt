package com.example.elta.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.elta.CurrencyInfo

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    secondary = Color(0xFF9E9E9E),
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C0C)
)

private val LightColorScheme = lightColorScheme(
    primary = Color.Black,
    secondary = Color(0xFF555555),
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5)
)

data class DeltaColorScheme(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val buttonBackground: Color,
    val checkBackground: Color,
    val checkContent: Color,
    val positive: Color,
    val negative: Color,
    val cardBorder: Color,
    val heatmapExpenseLevels: List<Color>,
    val heatmapIncomeLevels: List<Color>
)

val DarkDeltaColors = DeltaColorScheme(
    background = Color(0xFF000000),
    surface = Color(0xFF0C0C0C),
    border = Color(0xFF1A1A1A),
    textPrimary = Color.White,
    textSecondary = Color(0xFF9E9E9E),
    buttonBackground = Color(0xFF161616),
    checkBackground = Color.White,
    checkContent = Color.Black,
    positive = Color(0xFF00FF66),
    negative = Color(0xFFFF3333),
    cardBorder = Color(0xFF1A1A1A),
    heatmapExpenseLevels = listOf(
        Color(0xFF161616),
        Color(0xFF6E1818),
        Color(0xFF9E2222),
        Color(0xFFCE2C2C),
        Color(0xFFFF3333)
    ),
    heatmapIncomeLevels = listOf(
        Color(0xFF161616),
        Color(0xFF00662A),
        Color(0xFF00993F),
        Color(0xFF00CC54),
        Color(0xFF00FF66)
    )
)

val LightDeltaColors = DeltaColorScheme(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF5F5F5),
    border = Color(0xFFE5E5E5),
    textPrimary = Color.Black,
    textSecondary = Color(0xFF555555),
    buttonBackground = Color(0xFFEBEBEB),
    checkBackground = Color.Black,
    checkContent = Color.White,
    positive = Color(0xFF007A3E),
    negative = Color(0xFFC62828),
    cardBorder = Color(0xFFE5E5E5),
    heatmapExpenseLevels = listOf(
        Color(0xFFEBEBEB),
        Color(0xFFFFC0C0),
        Color(0xFFFFA0A0),
        Color(0xFFEE5D5D),
        Color(0xFFC62828)
    ),
    heatmapIncomeLevels = listOf(
        Color(0xFFEBEBEB),
        Color(0xFFC3F3C9),
        Color(0xFF8CE99A),
        Color(0xFF40C057),
        Color(0xFF007A3E)
    )
)

val LocalDeltaColors = staticCompositionLocalOf { DarkDeltaColors }
val LocalAppCurrency = staticCompositionLocalOf { CurrencyInfo("EUR", "€") }

@Composable
fun DeltaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val deltaColors = if (darkTheme) DarkDeltaColors else LightDeltaColors

    val currentDensity = LocalDensity.current
    val customDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density * 1.05f,
            fontScale = currentDensity.fontScale
        )
    }

    CompositionLocalProvider(
        LocalDeltaColors provides deltaColors,
        LocalDensity provides customDensity
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}