package des.c5inco.torph.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo
import java.awt.EventQueue

internal actual fun isMainUiThread(): Boolean = EventQueue.isDispatchThread()

@Composable
internal actual fun currentMeasurerOwner(): Any = LocalWindowInfo.current
