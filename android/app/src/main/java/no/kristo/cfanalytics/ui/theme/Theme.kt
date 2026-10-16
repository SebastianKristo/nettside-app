package no.kristo.cfanalytics.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

private val FallbackDark = darkColorScheme(
    background = Color(0xFF141218),
    surface = Color(0xFF141218),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    onBackground = Color(0xFFE6E0E9),
    onSurface = Color(0xFFE6E0E9),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    primary = Color(0xFFFFB1C8),
    onPrimary = Color(0xFF5E1133),
    primaryContainer = Color(0xFF7B2949),
    onPrimaryContainer = Color(0xFFFFD9E2),
    secondary = Color(0xFFA0CAFD),
    secondaryContainer = Color(0xFF7B2949),
    onSecondaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE6E0E9),
    inverseOnSurface = Color(0xFF322F35),
)

/** Chart palette from the design. */
object ChartColors {
    val Pink = Color(0xFFFFB1C8)
    val Blue = Color(0xFFA0CAFD)
    val Amber = Color(0xFFF3BD6E)
    val Green = Color(0xFF8FD6A0)
    val Purple = Color(0xFFC9B6FF)
    val Red = Color(0xFFFFB4AB)
}

/** Bottom bar pill from the design: #FFD9E2 with a dark filled icon. */
object NavColors {
    val Pill = Color(0xFFFFD9E2)
    val Icon = Color(0xFF3F0020)
}

private val ExpressiveTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun CfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> FallbackDark
    }
    MaterialTheme(colorScheme = scheme, typography = ExpressiveTypography, content = content)
}
