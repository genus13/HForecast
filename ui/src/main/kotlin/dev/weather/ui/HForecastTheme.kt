package dev.weather.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.weather.core.AppTheme

private val LightColors = lightColorScheme(
    primary = Color(0xFF006C90),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC6E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF006A67),
    secondaryContainer = Color(0xFF9CF2ED),
    tertiary = Color(0xFF865300),
    tertiaryContainer = Color(0xFFFFDDB1),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFDDE3EA),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CD0FF),
    primaryContainer = Color(0xFF004C68),
    secondary = Color(0xFF80D5D1),
    secondaryContainer = Color(0xFF00504E),
    tertiary = Color(0xFFFFB951),
    tertiaryContainer = Color(0xFF653E00),
    surface = Color(0xFF0E1419),
    surfaceVariant = Color(0xFF3E484E),
)

private val HForecastTypography = Typography(
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold),
)

private val HForecastShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun HForecastTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val useDarkTheme = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = HForecastTypography,
        shapes = HForecastShapes,
        content = content,
    )
}
