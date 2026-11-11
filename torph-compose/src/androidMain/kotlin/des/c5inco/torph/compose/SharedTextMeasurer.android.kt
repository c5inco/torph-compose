package des.c5inco.torph.compose

import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView

internal actual fun isMainUiThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

@Composable
internal actual fun currentMeasurerOwner(): Any {
    LocalConfiguration.current // Re-read platform default locales when configuration changes.
    return LocalView.current
}
