package com.example.ui.theme

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

import com.example.data.ThemeMode

private val AngelDarkColorScheme =
    darkColorScheme(
        primary = AngelNeonGreen,
        secondary = AngelCyanAccent,
        tertiary = ModeExpertColor,
        background = AngelDarkBackground,
        surface = AngelSurfaceDark,
        onPrimary = AngelDarkBackground,
        onSecondary = AngelDarkBackground,
        onBackground = AngelTextPrimary,
        onSurface = AngelTextPrimary,
        surfaceVariant = AngelCardDark
    )

private val AngelLightColorScheme =
    lightColorScheme(
        primary = AngelLightPrimary,
        secondary = AngelLightSecondary,
        tertiary = AngelLightTertiary,
        background = AngelLightBackground,
        surface = AngelLightSurface,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AngelLightTextPrimary,
        onSurface = AngelLightTextPrimary,
        surfaceVariant = AngelLightSurfaceVariant,
        outline = AngelLightOutline
    )

@Composable
fun AngelTheme(
    themeMode: ThemeMode? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        null -> darkTheme
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> AngelDarkColorScheme
        else -> AngelLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
