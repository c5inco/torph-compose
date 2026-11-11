package des.c5inco.torph.compose

internal actual fun logMorphDebug(tag: String, message: String) {
    println("$tag: $message")
}

internal actual fun beginMorphTrace(name: String) {
    // No platform systrace sink on Desktop.
}

internal actual fun endMorphTrace() {
}
