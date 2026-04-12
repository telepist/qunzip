package qunzip.presentation.ui

import kotlinx.cinterop.*
import platform.windows.*
import platform.posix.getenv

// With -mwindows (GUI subsystem), the process starts with no console.
// CLI mode must attach to the parent shell's console to get stdio.
private val ATTACH_PARENT_PROCESS_ID = 0xFFFFFFFF.toUInt()

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
 *
 * With GUI subsystem (-mwindows), the process starts with no console.
 * We try to attach to the parent's console: if it succeeds, we were launched
 * from a shell (CLI). If it fails, there is no parent console (standalone).
 *
 * After detection, we immediately detach again — the caller decides
 * whether to reattach (CLI mode) or stay detached (GUI mode).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isStandaloneLaunch(): Boolean {
    val attached = AttachConsole(ATTACH_PARENT_PROCESS_ID)
    if (attached != 0) {
        // Successfully attached — launched from a shell. Detach for now.
        FreeConsole()
        return false
    }
    // Could not attach — no parent console, standalone launch.
    return true
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

/**
 * No-op for GUI subsystem — there's no console to hide.
 */
actual fun hideConsole() {
    // With -mwindows, there is no console to hide.
}

/**
 * Attach to the parent shell's console for CLI mode.
 * Reattaches stdio so print/println work in the terminal.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun configureStandaloneConsole() {
    // For CLI mode: reattach to parent console
    AttachConsole(ATTACH_PARENT_PROCESS_ID)

    // Reopen stdio to point to the attached console
    platform.posix.freopen("CONOUT$", "w", platform.posix.stdout)
    platform.posix.freopen("CONOUT$", "w", platform.posix.stderr)
    platform.posix.freopen("CONIN$", "r", platform.posix.stdin)

    // Enable ANSI/VT100 escape sequences on the stdout handle
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
