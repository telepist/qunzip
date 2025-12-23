package gunzip.presentation.ui

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
 * Returns true if running in a terminal/console, false if launched from GUI
 */
expect fun isTerminal(): Boolean

/**
 * Check if native GUI is available on this platform
 */
expect fun isGuiAvailable(): Boolean

/**
 * Determine which UI mode to use based on launch context and arguments
 */
fun selectUiMode(args: List<String>): UiMode {
    return when {
        args.contains("--tui") -> UiMode.TUI
        args.contains("--gui") -> UiMode.GUI
        isGuiAvailable() && !isTerminal() -> UiMode.GUI
        else -> UiMode.TUI
    }
}

/**
 * UI rendering mode
 */
enum class UiMode {
    GUI,  // Native platform GUI
    TUI   // Mosaic terminal UI
}
