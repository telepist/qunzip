package qunzip.presentation.ui

import kotlinx.cinterop.*
import platform.windows.*
import platform.posix.getenv

// Subsystem differs between build types (see build.gradle.kts):
//   Release → Windows subsystem: no console at startup; attach to parent for CLI.
//   Debug   → console subsystem: a console always exists; hide it for GUI launches.
// All detection here keys off GetConsoleWindow() so the same code handles both.

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

private val ATTACH_PARENT_PROCESS_ID = 0xFFFFFFFF.toUInt()

/**
 * Detect if launched standalone (double-click / file association) on Windows.
 *
 * Release (Windows subsystem) — no console at startup:
 *   AttachConsole(parent) succeeds → CLI launch from a shell. Detach again.
 *   AttachConsole(parent) fails    → no parent console, standalone launch.
 *
 * Debug (console subsystem) — a console always exists:
 *   GetConsoleProcessList count == 1 → Windows created it for us (standalone).
 *   count >= 2                       → sharing with a shell (CLI launch).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun isStandaloneLaunch(): Boolean = memScoped {
    if (GetConsoleWindow() == null) {
        val attached = AttachConsole(ATTACH_PARENT_PROCESS_ID)
        if (attached != 0) {
            FreeConsole()
            return@memScoped false
        }
        return@memScoped true
    }
    val pids = allocArray<UIntVar>(16)
    val count = GetConsoleProcessList(pids.reinterpret(), 16u)
    count == 1u
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
 * Hide and detach the console window for GUI mode.
 * Hides the window first (fast, avoids visible flash), then detaches.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun hideConsole() {
    val consoleWindow = GetConsoleWindow()
    if (consoleWindow != null) {
        ShowWindow(consoleWindow, SW_HIDE)
    }
    FreeConsole()
}

/**
 * Ensure a console is available and stdio points to it.
 *
 * Debug (console subsystem): a console already exists, just enable VT processing.
 * Release (Windows subsystem) CLI: attach to the parent shell's console and
 * reopen stdio so the TUI renders in the user's terminal.
 * Release standalone TUI (rare): no parent console — allocate a fresh one.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun configureStandaloneConsole() {
    if (GetConsoleWindow() == null) {
        // No console — try parent first (CLI), fall back to a fresh window.
        if (AttachConsole(ATTACH_PARENT_PROCESS_ID) == 0) {
            AllocConsole()
            SetConsoleTitleA("Qunzip")
        }
        platform.posix.freopen("CONOUT$", "w", platform.posix.stdout)
        platform.posix.freopen("CONOUT$", "w", platform.posix.stderr)
        platform.posix.freopen("CONIN$", "r", platform.posix.stdin)
    }

    // Enable ANSI/VT100 escape sequences
    val handle = GetStdHandle(STD_OUTPUT_HANDLE)
    if (handle != null && handle != INVALID_HANDLE_VALUE) {
        memScoped {
            val mode = alloc<UIntVar>()
            if (GetConsoleMode(handle, mode.ptr) != 0) {
                val ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004u
                SetConsoleMode(handle, mode.value or ENABLE_VIRTUAL_TERMINAL_PROCESSING)
            }
        }
    }
}
