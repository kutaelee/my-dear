package app.mydear.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Coral = Color(0xFFCF3F34)
val WarmIvory = Color(0xFFFFFCF8)
val WarmInk = Color(0xFF211B18)

private val colors = lightColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDDD4),
    onPrimaryContainer = Color(0xFF6B1712),
    background = WarmIvory,
    onBackground = WarmInk,
    surface = Color.White,
    onSurface = WarmInk,
    outline = Color(0xFF766B65),
)

private val seniorTypography = Typography(
    bodyLarge = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    titleLarge = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    labelLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
)

@Composable fun MyDearTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = seniorTypography, content = content)
}
