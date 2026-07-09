package qunzip.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*

// argv already arrives as UTF-8 here — nothing to correct.
actual fun resolveCommandLineArgs(entryArgs: Array<String>): Array<String> = entryArgs

/**
 * Check if running in a terminal on Linux
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
    // No-op on Linux
}

actual fun readLineFromConsole(): String? {
    return readLine()
}

actual fun hideConsole() {
    // No-op on this platform
}
