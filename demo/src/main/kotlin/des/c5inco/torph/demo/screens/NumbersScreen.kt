package des.c5inco.torph.demo.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import des.c5inco.torph.compose.MorphEase
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.core.formatNumber
import des.c5inco.torph.demo.Caption
import des.c5inco.torph.demo.Choice
import des.c5inco.torph.demo.CodeBlock
import des.c5inco.torph.demo.Gap
import des.c5inco.torph.demo.LabeledSlider
import des.c5inco.torph.demo.SectionTitle
import des.c5inco.torph.demo.Stage
import des.c5inco.torph.demo.ToggleRow
import kotlin.random.Random
import androidx.compose.runtime.DisposableEffect
import des.c5inco.torph.compose.TextMorphDiagnostics

private val locales = listOf("en-US", "de-DE", "fr-FR", "ar-EG", "hi-IN")

@Composable
fun NumbersScreen() {
    // Log a per-change timing breakdown to logcat (adb logcat -s TextMorphPerf) while this
    // screen is open. Off elsewhere so it never distorts the other screens.
    DisposableEffect(Unit) {
        TextMorphDiagnostics.logTimings = true
        onDispose { TextMorphDiagnostics.logTimings = false }
    }
    var value by rememberSaveable { mutableDoubleStateOf(1234.5) }
    var decimals by rememberSaveable { mutableIntStateOf(2) }
    var localeTag by rememberSaveable { mutableStateOf("en-US") }
    var currency by rememberSaveable { mutableStateOf(true) }
    var spring by rememberSaveable { mutableStateOf(false) }
    val locale = Locale(localeTag)
    val javaLocale = java.util.Locale.forLanguageTag(localeTag)
    val symbol = if (currency) java.text.NumberFormat.getCurrencyInstance(javaLocale).currency?.symbol ?: "$" else ""
    val formatted = formatNumber(value, decimals, javaLocale)
    val text = if (currency) (if (localeTag.startsWith("en")) "$symbol$formatted" else "$formatted $symbol") else formatted

    Stage(120) {
        TextMorph(
            text = text,
            style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurface,
            locale = locale,
            ease = if (spring) MorphEase.Spring(stiffness = 180f, damping = 18f) else MorphEase.Curve(),
        )
    }
    Gap(12)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { value -= 1 }) { Text("−1") }
        Button(onClick = { value += 1 }) { Text("+1") }
        Button(onClick = { value += 100 }) { Text("+100") }
        OutlinedButton(onClick = { value = Random.nextDouble(0.0, 99_999.0) }) { Text("Random") }
    }
    Gap()
    Caption("Digits at the same place value persist; changed ones roll up when the number grows and down when it shrinks. Separators and currency travel with their place.")

    SectionTitle("Locale")
    Choice(locales, localeTag, { it }) { localeTag = it }
    Gap()
    ToggleRow("currency symbol", currency) { currency = it }
    ToggleRow("spring ease", spring) { spring = it }
    LabeledSlider("decimals", decimals.toFloat(), 0f..4f, { it.toInt().toString() }) { decimals = it.toInt() }

    CodeBlock(
        """
        // Numeric overload formats via the locale:
        TextMorph(value = $value, decimals = $decimals, locale = Locale("$localeTag"))

        // Or format yourself and pass a String:
        TextMorph(text = "$text", locale = Locale("$localeTag"))
        """,
    )
}
