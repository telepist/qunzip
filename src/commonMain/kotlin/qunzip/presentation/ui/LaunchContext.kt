package qunzip.presentation.ui

/**
 * Global UI configuration
 * Used to coordinate between different parts of the app
 */
object UiConfig {
    /**
     * When true, console output (println, notifications) should be suppressed
     * because the TUI is handling all display
     */
    var suppressConsoleOutput: Boolean = false
        private set

    /**
     * Enable console output suppression for TUI mode
     */
    fun enableTuiMode() {
        suppressConsoleOutput = true
    }
}

/**
 * Resolve the real command-line arguments for the process.
 *
 * On Windows the Kotlin/Native entry point receives argv from the C runtime,
 * which decodes the command line with the legacy ANSI code page. Non-ASCII
 * paths (e.g. an archive named "Pojat telttaretkellä.zip") arrive corrupted —
 * bytes that aren't valid UTF-8 become U+FFFD — so the archive is later
 * reported "not found". The Windows actual re-reads the true UTF-16 command
 * line via GetCommandLineW/CommandLineToArgvW and drops argv[0]. Other
 * platforms already receive UTF-8 argv and return [entryArgs] unchanged.
 */
expect fun resolveCommandLineArgs(entryArgs: Array<String>): Array<String>

/**
 * Detect how the application was launched
 * Returns true if running in a terminal/console
 */
expect fun isTerminal(): Boolean

/**
 * Detect if the application was launched standalone (e.g. double-click, file association).
 * On Windows, uses GetConsoleProcessList to check if we're the only process in the console.
 * When true, the console window was created for this process and will close when we exit.
 * When false, we're sharing a console with a shell (CLI launch).
 * On non-Windows platforms, returns !isTerminal().
 */
expect fun isStandaloneLaunch(): Boolean

/**
 * Configure the console window for standalone (double-click) launches.
 * On Windows, sets the console title. No-op on other platforms.
 */
expect fun configureStandaloneConsole()

/**
 * Read a line of text from the console, used for password input in standalone mode
 * where Mosaic's NonInteractiveTerminal doesn't process keyboard events.
 * Returns null if reading fails.
 */
expect fun readLineFromConsole(): String?

/**
 * Hide/detach the console window for GUI mode.
 * On Windows, calls FreeConsole(). No-op on other platforms.
 */
expect fun hideConsole()
