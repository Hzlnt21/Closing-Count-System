package com.closingcount.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = PaleBlue,
    onPrimaryContainer = ExveNavy,
    secondary = ExveBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC8EAF5),
    onSecondaryContainer = Color(0xFF002F3E),
    tertiary = Color(0xFF3F5F90),
    background = IceBlue,
    onBackground = Color(0xFF171C20),
    surface = IceBlue,
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFDCE4E9),
    onSurfaceVariant = Color(0xFF40484D),
    outline = Color(0xFF70787D),
)

private val DarkColors = darkColorScheme(
    primary = SkyBlue,
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF004D68),
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = Color(0xFF80CEE9),
    onSecondary = Color(0xFF003642),
    secondaryContainer = Color(0xFF174E5C),
    onSecondaryContainer = Color(0xFFBCEAF8),
    tertiary = Color(0xFFAAC7FF),
    background = DeepBlue,
    onBackground = Color(0xFFDEE3E7),
    surface = DarkBlueSurface,
    onSurface = Color(0xFFDEE3E7),
    surfaceVariant = Color(0xFF40484D),
    onSurfaceVariant = Color(0xFFC0C8CD),
)

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
