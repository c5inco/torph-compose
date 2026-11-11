package des.c5inco.torph.compose

import androidx.compose.runtime.Composable

/** Reads the platform reduced-motion setting; call [TextMorph] with `respectReducedMotion = false` to ignore it. */
@Composable
public expect fun rememberReducedMotion(): Boolean
