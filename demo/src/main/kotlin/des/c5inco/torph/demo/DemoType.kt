package des.c5inco.torph.demo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

/**
 * The demo's typefaces, all bundled OFL-licensed Google Fonts: Geist for UI and word morphs, Geist
 * Mono for code and readouts, Bitcount Single for number morphs. Bitcount is monospaced, so digits
 * keep the same width as they roll.
 */
object DemoType {
    val geist = variableFamily(R.font.geist)
    val geistMono = variableFamily(R.font.geist_mono)
    val bitcountSingle = variableFamily(R.font.bitcount_single)

    val typography = typographyFor(geist)
    val numberTypography = typographyFor(bitcountSingle)
}

/** Re-themes [content] with the number typeface, for stages that morph numbers. */
@Composable
fun NumberText(content: @Composable () -> Unit) {
    MaterialTheme(typography = DemoType.numberTypography, content = content)
}

private fun typographyFor(f: FontFamily): Typography {
    val base = Typography()
    fun TextStyle.withFont() = copy(fontFamily = f)
    return Typography(
        displayLarge = base.displayLarge.withFont(),
        displayMedium = base.displayMedium.withFont(),
        displaySmall = base.displaySmall.withFont(),
        headlineLarge = base.headlineLarge.withFont(),
        headlineMedium = base.headlineMedium.withFont(),
        headlineSmall = base.headlineSmall.withFont(),
        titleLarge = base.titleLarge.withFont(),
        titleMedium = base.titleMedium.withFont(),
        titleSmall = base.titleSmall.withFont(),
        bodyLarge = base.bodyLarge.withFont(),
        bodyMedium = base.bodyMedium.withFont(),
        bodySmall = base.bodySmall.withFont(),
        labelLarge = base.labelLarge.withFont(),
        labelMedium = base.labelMedium.withFont(),
        labelSmall = base.labelSmall.withFont(),
    )
}

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(res: Int) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(res, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    },
)
