package net.shehane.androidapiplayground.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Cyan500,
    secondary = Slate500,
    tertiary = Pink80,
    background = Slate900,
    surface = Slate800,
    onPrimary = Slate900,
    onSecondary = Slate200,
    onTertiary = Slate200,
    onBackground = Slate200,
    onSurface = Slate200,
)

private val LightColorScheme = lightColorScheme(
    primary = Cyan700,
    secondary = Slate700,
    tertiary = Pink40,
    background = Slate200,
    surface = Slate500, // Slightly darker for surface
    onPrimary = Slate200,
    onSecondary = Slate200,
    onTertiary = Slate900,
    onBackground = Slate900,
    onSurface = Slate900,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
