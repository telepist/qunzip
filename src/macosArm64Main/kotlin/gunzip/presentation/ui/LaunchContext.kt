package gunzip.presentation.ui

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

/**
 * Check if native GUI is available on macOS
 * Currently false - Cocoa GUI not yet implemented
 */
actual fun isGuiAvailable(): Boolean {
    return false // TODO: Will be true when Cocoa GUI is implemented
}

/**
 * macOS uses terminal detection to decide UI mode
 */
actual fun preferGuiByDefault(): Boolean {
    return false
}
