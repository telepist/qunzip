package qunzip.presentation.ui

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import kotlinx.cinterop.UIntVar
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
    val term = getenv("TERM")?.toKString()
    val msystem = getenv("MSYSTEM")?.toKString()

    if (term != null && term != "dumb" && term.isNotEmpty()) {
        return true
    }

    if (msystem != null && msystem.isNotEmpty()) {
        return true
    }

    return false
}

/**
 * Detect if launched standalone (double-click / file association) on Windows.
 * Uses GetConsoleProcessList: if only 1 process is attached to the console,
 * it means Windows created the console for us (standalone launch).
 * If >1 processes, we're sharing with a shell (CLI launch).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isStandaloneLaunch(): Boolean = memScoped {
    val processList = allocArray<UIntVar>(1)
    val count = GetConsoleProcessList(processList, 1u)
    count <= 1u
}

@OptIn(ExperimentalForeignApi::class)
actual fun configureStandaloneConsole() {
    SetConsoleTitleA("Qunzip")

    // Enable ANSI/VT100 escape sequences on the stdout handle.
    // Needed for legacy conhost.exe; Windows Terminal handles ANSI natively.
    // Mosaic's NonInteractiveTerminal writes via print() which goes to this handle.
    val handle = GetStdHandle(STD_OUTPUT_HANDLE)
    if (handle != INVALID_HANDLE_VALUE) {
        memScoped {
            val mode = alloc<UIntVar>()
            if (GetConsoleMode(handle, mode.ptr) != 0) {
                val ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004u
                SetConsoleMode(handle, mode.value or ENABLE_VIRTUAL_TERMINAL_PROCESSING)
            }
        }
    }
}
