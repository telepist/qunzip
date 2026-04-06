package qunzip.presentation.ui

import kotlinx.cinterop.*
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

/**
 * Read a line from the Windows console input handle using raw input events.
 * Used for password entry in standalone mode where Mosaic's NonInteractiveTerminal
 * doesn't process keyboard events. Returns null on Escape (cancel).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun readLineFromConsole(): String? = memScoped {
    val inputHandle = GetStdHandle(STD_INPUT_HANDLE)
    if (inputHandle == INVALID_HANDLE_VALUE) return null

    // Flush any pending input events so we start clean
    FlushConsoleInputBuffer(inputHandle)

    val eventsRead = alloc<UIntVar>()
    val inputRecord = alloc<INPUT_RECORD>()
    val result = StringBuilder()

    while (true) {
        if (ReadConsoleInputW(inputHandle, inputRecord.ptr, 1u, eventsRead.ptr) == 0) {
            return@memScoped null
        }
        if (eventsRead.value == 0u) continue

        // Only process key-down events
        if (inputRecord.EventType.toInt() != KEY_EVENT) continue
        val keyEvent = inputRecord.Event.KeyEvent
        if (keyEvent.bKeyDown == 0) continue

        val vk = keyEvent.wVirtualKeyCode.toInt()
        val ch = keyEvent.uChar.UnicodeChar.toInt().toChar()

        when {
            vk == VK_ESCAPE -> return@memScoped null
            vk == VK_RETURN -> break
            vk == VK_BACK -> {
                if (result.isNotEmpty()) {
                    result.deleteAt(result.length - 1)
                }
            }
            ch.code >= 32 -> {
                result.append(ch)
            }
        }
    }

    result.toString()
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
