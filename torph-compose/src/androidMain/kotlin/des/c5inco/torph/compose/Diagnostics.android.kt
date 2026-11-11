package des.c5inco.torph.compose

import android.os.Trace
import android.util.Log

internal actual fun logMorphDebug(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun beginMorphTrace(name: String) {
    Trace.beginSection(name)
}

internal actual fun endMorphTrace() {
    Trace.endSection()
}
