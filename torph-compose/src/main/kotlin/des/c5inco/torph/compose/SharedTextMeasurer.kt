package des.c5inco.torph.compose

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import java.lang.ref.WeakReference

/** Compose's own LRU handles text/style/constraints and stale resolved fonts. */
internal const val SHARED_LAYOUT_CAPACITY = 256

/**
 * Small LRU of environments, not layouts. Weak owners, resolvers and measurers prevent this
 * registry from extending the lifetime of a view or its text resources after its states disappear.
 * Eviction only loses future sharing: existing states still own their measurer and live layouts.
 */
internal class TextMeasurerPool(private val capacity: Int = 16) {
    private class Entry(
        owner: Any,
        resolver: FontFamily.Resolver,
        density: Density,
        val densityValue: Float,
        val fontScale: Float,
        val direction: LayoutDirection,
        val locales: LocaleList,
        measurer: TextMeasurer,
    ) {
        val density = WeakReference(density)
        val owner = WeakReference(owner)
        val resolver = WeakReference(resolver)
        val measurer = WeakReference(measurer)
    }

    init { require(capacity > 0) }

    private val entries = ArrayList<Entry>()

    @Synchronized
    fun get(
        owner: Any,
        resolver: FontFamily.Resolver,
        density: Density,
        direction: LayoutDirection,
        locales: LocaleList,
    ): TextMeasurer {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Shared text measurers are main-thread confined" }
        entries.removeAll { it.owner.get() == null || it.resolver.get() == null || it.measurer.get() == null }
        val index = entries.indexOfFirst {
            it.owner.get() === owner && it.resolver.get() === resolver && it.density.get() == density &&
                it.densityValue == density.density && it.fontScale == density.fontScale &&
                it.direction == direction && it.locales == locales
        }
        if (index >= 0) {
            val entry = entries.removeAt(index)
            entry.measurer.get()?.let { entries.add(entry); return it }
        }
        val measurer = TextMeasurer(
            defaultFontFamilyResolver = resolver,
            defaultDensity = density,
            defaultLayoutDirection = direction,
            cacheSize = SHARED_LAYOUT_CAPACITY,
        )
        if (entries.size == capacity) entries.removeAt(0)
        entries.add(Entry(owner, resolver, density, density.density, density.fontScale, direction, locales, measurer))
        return measurer
    }
}

private val textMeasurerPool = TextMeasurerPool()

/** Sharing is automatic within one Compose view and matching measurement environment. */
@Composable
internal fun rememberSharedTextMeasurer(): TextMeasurer {
    val view = LocalView.current
    val resolver = LocalFontFamilyResolver.current
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    LocalConfiguration.current // Re-read platform default locales when configuration changes.
    val locales = LocaleList.current
    return remember(view, resolver, density, density.density, density.fontScale, direction, locales) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            textMeasurerPool.get(view, resolver, density, direction, locales)
        } else {
            // Background composition must not borrow a measurer that the UI thread may be using.
            TextMeasurer(resolver, density, direction, cacheSize = SHARED_LAYOUT_CAPACITY)
        }
    }
}
