package qunzip.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*

/**
 * Check if running in a terminal on macOS
 * Returns true if stdout is a TTY
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isTerminal(): Boolean {
    return isatty(STDOUT_FILENO) == 1
}

actual fun isStandaloneLaunch(): Boolean {
    return !isTerminal()
}

actual fun configureStandaloneConsole() {
    // No-op on macOS
}

actual fun readLineFromConsole(): String? {
    return readLine()
}

actual fun hideConsole() {
    // No-op on this platform
}
