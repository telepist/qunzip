package qunzip.presentation.ui

import androidx.compose.runtime.*
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.ui.tui.MosaicApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import com.jakewharton.mosaic.runMosaic

/**
 * Common interface for UI backends (TUI and native GUI)
 */
interface UiRenderer {
    /**
     * Start rendering the UI
     * @param viewModel Application ViewModel with state
     * @param scope Coroutine scope for UI operations
     */
    suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope)

    /**
     * Check if this renderer is available on current platform
     */
    fun isAvailable(): Boolean
}

/**
 * Create platform-specific native GUI renderer
 * Returns null if native GUI is not available on this platform
 */
expect fun createNativeGuiRenderer(): UiRenderer?

/**
 * Mosaic-based TUI renderer
 * Available on all platforms
 */
class MosaicTuiRenderer : UiRenderer {
    override suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope) {
        runMosaic {
            var exit by remember { mutableStateOf(false) }

            MosaicApp(viewModel) { exit = true }

            // Keep the app running until exit is requested
            // Using exit as key so the effect restarts when exit changes
            LaunchedEffect(exit) {
                if (!exit) {
                    awaitCancellation()
                }
                // When exit is true, this effect completes immediately
                // allowing runMosaic to finish
            }
        }
    }

    override fun isAvailable(): Boolean = isTerminal()
}
