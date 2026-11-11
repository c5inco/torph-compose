package des.c5inco.torph.compose

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.AndroidFont
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CompletableDeferred

/** Real resolver loading with a test-controlled completion; no stale-font flags are mocked. */
internal fun deferredTestFont(loaded: CompletableDeferred<Typeface>): AndroidFont {
    val loader = object : AndroidFont.TypefaceLoader {
        override fun loadBlocking(context: Context, font: AndroidFont): Typeface? = error("Async only")
        override suspend fun awaitLoad(context: Context, font: AndroidFont): Typeface = loaded.await()
    }
    return object : AndroidFont(FontLoadingStrategy.Async, loader) {
        override val weight = FontWeight.Normal
        override val style = FontStyle.Normal
    }
}
