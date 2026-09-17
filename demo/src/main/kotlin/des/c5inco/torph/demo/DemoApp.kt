package des.c5inco.torph.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import des.c5inco.torph.compose.TextMorph
import des.c5inco.torph.demo.screens.DebugScreen
import des.c5inco.torph.demo.screens.InterruptionScreen
import des.c5inco.torph.demo.screens.MultiLineScreen
import des.c5inco.torph.demo.screens.NumbersScreen
import des.c5inco.torph.demo.screens.PerfScreen
import des.c5inco.torph.demo.screens.PlaygroundScreen
import des.c5inco.torph.demo.screens.ScriptsScreen
import des.c5inco.torph.demo.screens.TypingScreen
import androidx.compose.foundation.clickable

enum class Screen(val title: String, val blurb: String) {
    Playground("Playground", "Type anything; tune duration, easing, springs, scale and numbers."),
    Numbers("Numbers", "Place-value rolling, locales, decimals."),
    Typing("Typing", "cursorIndex keeps digits in place while you edit."),
    MultiLine("Multi-line", "Wrapped paragraphs re-flow with measured line breaks."),
    Scripts("Scripts", "Arabic, Devanagari, Thai: grapheme vs word vs auto segmentation."),
    Interruption("Interruption", "Rapid updates retarget mid-flight with velocity preserved."),
    Debug("Debug overlay", "Segment rects, ids, enter/exit colouring."),
    Perf("Perf", "50 / 200 / 1000 characters with a frame-time readout."),
}

@Composable
fun DemoApp() {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9ECBFF)) else lightColorScheme(primary = Color(0xFF0B57D0))
    MaterialTheme(colorScheme = scheme, typography = DemoType.typography) {
        var screen by rememberSaveable { mutableStateOf<Screen?>(null) }
        BackHandler(enabled = screen != null) { screen = null }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val current = screen
            if (current == null) {
                Home(onOpen = { screen = it })
            } else {
                Detail(current, onBack = { screen = null })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(onOpen: (Screen) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Torph for Compose") }) }) { padding ->
        LazyColumn(contentPadding = padding) {
            item {
                HeroMorph()
                Spacer(Modifier.height(8.dp))
            }
            items(Screen.entries) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.blurb) },
                    modifier = Modifier.clickable { onOpen(s) },
                )
            }
        }
    }
}

@Composable
private fun HeroMorph() {
    val phrases = listOf("Text that morphs.", "Text that moves.", "Numbers that roll: 1,234", "Numbers that roll: 1,298", "Text that morphs.")
    val text by cycling(phrases, 1800L)
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        TextMorph(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Detail(screen: Screen, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(horizontal = 16.dp, vertical = 8.dp)),
        ) {
            when (screen) {
                Screen.Playground -> PlaygroundScreen()
                Screen.Numbers -> NumbersScreen()
                Screen.Typing -> TypingScreen()
                Screen.MultiLine -> MultiLineScreen()
                Screen.Scripts -> ScriptsScreen()
                Screen.Interruption -> InterruptionScreen()
                Screen.Debug -> DebugScreen()
                Screen.Perf -> PerfScreen()
            }
            Spacer(Modifier.fillMaxWidth().height(32.dp))
        }
    }
}
