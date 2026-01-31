package qunzip.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.windows.*
import platform.posix.getenv

/**
 * Check if running in a terminal on Windows
 * Returns true if stdout is attached to a console or we're in MSYS2/Cygwin
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isTerminal(): Boolean {
    // Check for Windows native console
    val stdHandle = GetStdHandle(STD_OUTPUT_HANDLE)
    val fileType = GetFileType(stdHandle)
    if (fileType == FILE_TYPE_CHAR.toUInt()) {
        return true
    }

    // Check for MSYS2/Cygwin/MinGW terminal (uses pipes but is still a terminal)
    // These terminals set TERM or MSYSTEM environment variables
    val term = getenv("TERM")?.toKString()
    val msystem = getenv("MSYSTEM")?.toKString()

    // If TERM is set to something other than "dumb", we're likely in a terminal
    if (term != null && term != "dumb" && term.isNotEmpty()) {
        return true
    }

    // If MSYSTEM is set, we're in MSYS2/MinGW environment
    if (msystem != null && msystem.isNotEmpty()) {
        return true
    }

    return false
}

/**
 * Check if GUI is available on Windows
 * Always true since Win32 GUI is always available
 */
actual fun isGuiAvailable(): Boolean {
    return true
}

/**
 * Windows prefers GUI mode by default
 * Users can still use --tui to force terminal mode
 */
actual fun preferGuiByDefault(): Boolean {
    return true
}
