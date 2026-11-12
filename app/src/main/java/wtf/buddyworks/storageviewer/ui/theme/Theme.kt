package wtf.buddyworks.storageviewer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * One UI 9 surfaces, sampled from Samsung's own apps on a Galaxy Z Fold8. The accent follows
 * the system colour palette (Settings › Wallpaper and style), as Samsung's apps do.
 */
@Immutable
data class OneUiColors(
    val isDark: Boolean,
    val background: Color,
    val card: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textSection: Color,
    val divider: Color,
    val selection: Color,
    val highlight: Color,
    val pill: Color,
    val track: Color,
    val accent: Color,
    val onAccent: Color,
    val folder: Color,
    /** Categorical chart colours in fixed order (validated for colour-vision deficiencies). */
    val series: List<Color>,
    /** Neutral for "System" and "Other". */
    val neutral: Color,
    val scrim: Color,
)

private val LightSeries = listOf(
    Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A), Color(0xFFEDA100),
    Color(0xFFE87BA4), Color(0xFF008300), Color(0xFF6250D6),
)
private val DarkSeries = listOf(
    Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70), Color(0xFFC98500),
    Color(0xFFD55181), Color(0xFF008300), Color(0xFF9085E9),
)

/** One UI's default blue, used when no system palette is available. */
private val OneUiBlue = Color(0xFF387AF5)

private fun lightColors(accent: Color, onAccent: Color) = OneUiColors(
    isDark = false,
    background = Color(0xFFF0F0F0),
    card = Color(0xFFFCFCFC),
    textPrimary = Color(0xFF010101),
    textSecondary = Color(0xFF7A7A7A),
    textSection = Color(0xFF909090),
    divider = Color(0xFFE4E4E7),
    selection = Color(0xFFDDDDDD),
    highlight = Color(0xFFEDEDED),
    pill = Color(0xFFE5E5E5),
    track = Color(0xFFE3E3E3),
    accent = accent,
    onAccent = onAccent,
    folder = Color(0xFFF0B83A),
    series = LightSeries,
    neutral = Color(0xFFA6A6A6),
    scrim = Color(0x33000000),
)

private fun darkColors(accent: Color, onAccent: Color) = OneUiColors(
    isDark = true,
    background = Color(0xFF000000),
    card = Color(0xFF171717),
    textPrimary = Color(0xFFFAFAFA),
    textSecondary = Color(0xFF9C9C9C),
    textSection = Color(0xFF8C8C8C),
    divider = Color(0xFF2A2A2A),
    selection = Color(0xFF333333),
    highlight = Color(0xFF242424),
    pill = Color(0xFF262626),
    track = Color(0xFF2C2C2C),
    accent = accent,
    onAccent = onAccent,
    folder = Color(0xFFD9A632),
    series = DarkSeries,
    neutral = Color(0xFF6B6B6B),
    scrim = Color(0x66000000),
)

val LocalOneUiColors = staticCompositionLocalOf { lightColors(OneUiBlue, Color.White) }

/** Text styles sized to match One UI 9 (the system font on Samsung devices is One UI Sans). */
@Immutable
data class OneUiType(
    val largeTitle: TextStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp),
    val toolbarTitle: TextStyle = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp),
    val headline: TextStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 32.sp),
    val bigNumber: TextStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    val itemTitle: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    val itemSubtitle: TextStyle = TextStyle(fontSize = 14.sp, lineHeight = 19.sp),
    val itemValue: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val section: TextStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    val caption: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    val button: TextStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val nav: TextStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
)

val LocalOneUiType = staticCompositionLocalOf { OneUiType() }

object OneUi {
    val colors: OneUiColors
        @Composable @ReadOnlyComposable get() = LocalOneUiColors.current
    val type: OneUiType
        @Composable @ReadOnlyComposable get() = LocalOneUiType.current

    val cardShape = RoundedCornerShape(26.dp)
    val paneShape = RoundedCornerShape(28.dp)
    val cardRadius = 26.dp
}

@Composable
fun StorageViewerTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = remember(dark, context) {
        val system = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        if (dark) darkColors(system.primary, system.onPrimary) else lightColors(system.primary, system.onPrimary)
    }
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceContainer = colors.card, onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    } else {
        lightColorScheme(
            primary = colors.accent, onPrimary = colors.onAccent,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.card, onSurface = colors.textPrimary,
            surfaceContainer = colors.card, onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.divider,
        )
    }
    CompositionLocalProvider(
        LocalOneUiColors provides colors,
        LocalOneUiType provides OneUiType(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
