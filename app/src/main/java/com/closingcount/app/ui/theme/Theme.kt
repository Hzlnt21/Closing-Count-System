package com.closingcount.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CoffeeBrown,
    onPrimary = Cream,
    primaryContainer = ColorTokens.LightPrimaryContainer,
    onPrimaryContainer = Espresso,
    secondary = Sage,
    tertiary = Caramel,
    background = Cream,
    surface = Cream,
)

private val DarkColors = darkColorScheme(
    primary = Caramel,
    secondary = Sage,
    background = Espresso,
    surface = CoffeeBrownDark,
)

private object ColorTokens {
    val LightPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFF4DDC4)
}

@Composable
fun ClosingCountTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
