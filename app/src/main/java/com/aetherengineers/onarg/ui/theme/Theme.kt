package com.aetherengineers.onarg.ui.theme

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

private val Light = lightColorScheme(
    primary = Color(0xFF5B4BC4), secondary = Color(0xFF7A5F00), tertiary = Color(0xFF00696E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFFC6BFFF), secondary = Color(0xFFF0C04A), tertiary = Color(0xFF6FD6DC),
)

/** Uses Material You colours from the wallpaper on Android 12+ (e.g. Galaxy S22), branded colours otherwise. */
@Composable
fun OnargTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
