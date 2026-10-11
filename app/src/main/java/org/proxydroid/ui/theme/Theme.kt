package org.proxydroid.ui.theme

import android.os.Build
import android.content.res.Configuration
import android.content.pm.PackageManager
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
    primary = Color(0xFF006D5B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5DF), onPrimaryContainer = Color(0xFF00382C),
    secondary = Color(0xFF075AA8), onSecondary = Color.White,
    secondaryContainer = Color(0xFFD9EAFF), onSecondaryContainer = Color(0xFF10243B),
    tertiary = Color(0xFF6B4FA3),
    background = Color(0xFFF3F7FC), onBackground = Color(0xFF10243B),
    surface = Color.White, onSurface = Color(0xFF10243B),
    surfaceVariant = Color(0xFFE6EEF7), onSurfaceVariant = Color(0xFF334D66),
    outline = Color(0xFF637B92), surfaceTint = Color(0xFF006D5B),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF55E3BF), onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF164F48), onPrimaryContainer = Color(0xFFB8F5DF),
    secondary = Color(0xFF8BC8FF), onSecondary = Color(0xFF10243B),
    secondaryContainer = Color(0xFF214866), onSecondaryContainer = Color(0xFFD9EAFF),
    tertiary = Color(0xFFD6BCFF),
    background = Color(0xFF0C192B), onBackground = Color(0xFFF2F6FF),
    surface = Color(0xFF14283F), onSurface = Color(0xFFF2F6FF),
    surfaceVariant = Color(0xFF203B55), onSurfaceVariant = Color(0xFFC8D9EA),
    outline = Color(0xFF8FA9C1), surfaceTint = Color(0xFF55E3BF),
)

@Composable
fun ProxyDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val tv = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION ||
        ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val useDark = darkTheme || tv
    val colors = when {
        dynamicColor && !tv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        useDark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
