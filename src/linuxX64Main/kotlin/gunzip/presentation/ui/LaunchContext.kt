package gunzip.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.*

/**
 * Check if running in a terminal on Linux
 * Returns true if stdout is a TTY
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isTerminal(): Boolean {
    return isatty(STDOUT_FILENO) == 1
}

/**
 * Check if native GUI is available on Linux
 * Currently false - GTK GUI not yet implemented
 */
actual fun isGuiAvailable(): Boolean {
    return false // TODO: Will be true when GTK GUI is implemented
}
