package des.c5inco.torph.demo.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.intl.Locale
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.core.Segmentation
import des.c5inco.torph.demo.Caption
import des.c5inco.torph.demo.Choice
import des.c5inco.torph.demo.CodeBlock
import des.c5inco.torph.demo.Gap
import des.c5inco.torph.demo.SectionTitle
import des.c5inco.torph.demo.Stage
import des.c5inco.torph.demo.ToggleRow
import des.c5inco.torph.demo.cycling
import kotlin.time.Duration.Companion.milliseconds

private data class Sample(val name: String, val locale: String, val a: String, val b: String)

private val samples = listOf(
    Sample("Latin", "en", "The quick brown fox", "The quick red fox jumps"),
    Sample("Arabic", "ar", "مرحبا بالعالم الجميل", "مرحبا بالعالم الواسع"),
    Sample("Devanagari", "hi", "नमस्ते दुनिया, कैसे हो", "नमस्ते दुनिया, ठीक हो"),
    Sample("Thai", "th", "สวัสดีชาวโลก ยินดีต้อนรับ", "สวัสดีชาวโลก ขอบคุณมาก"),
    Sample("CJK", "ja", "こんにちは世界", "こんにちは宇宙"),
    Sample("Emoji", "en", "Ship it 🚀 today 👍🏽", "Ship it 🚀 tomorrow 🎉"),
)

@Composable
fun ScriptsScreen() {
    var mode by rememberSaveable { mutableStateOf(Segmentation.AUTO) }
    var running by rememberSaveable { mutableStateOf(true) }
    Caption("Grapheme mode morphs each cluster; in shaping-sensitive scripts that breaks joining forms. Word mode keeps words intact. Auto picks per word from its script.")
    Gap(12)
    Choice(Segmentation.entries, mode, { it.name }) { mode = it }
    ToggleRow("auto-cycle", running) { running = it }
    for (s in samples) {
        SectionTitle("${s.name} (${s.locale})")
        val text by cycling(listOf(s.a, s.b), 2200L, running)
        Stage(64) {
            TextMorph(
                text = text,
                locale = Locale(s.locale),
                segmentation = mode,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                duration = 700.milliseconds,
            )
        }
    }
    CodeBlock(
        """
        TextMorph(
            text = arabic,
            locale = Locale("ar"),
            segmentation = Segmentation.${mode.name},
        )
        """,
    )
    Text("")
}
