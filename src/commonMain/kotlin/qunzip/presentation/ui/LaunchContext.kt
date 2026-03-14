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
