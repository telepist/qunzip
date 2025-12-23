package gunzip.presentation.ui

import gunzip.presentation.viewmodels.ApplicationViewModel
import kotlinx.coroutines.CoroutineScope
import co.touchlab.kermit.Logger

/**
 * Windows Win32 native GUI renderer
 * TODO: Implement Win32 dialogs using C interop
 */
class Win32GuiRenderer : UiRenderer {
    private val logger = Logger.withTag("Win32Gui")

    override suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope) {
        logger.w { "Win32 GUI not yet implemented, falling back to TUI" }
        // TODO: Implement Win32 dialog rendering
        // For now, fall back to TUI
        val tuiRenderer = MosaicTuiRenderer()
        tuiRenderer.render(viewModel, scope)
    }

    override fun isAvailable(): Boolean {
        // Will be true once Win32 GUI is implemented
        return false
    }
}

/**
 * Create Windows native GUI renderer
 */
actual fun createNativeGuiRenderer(): UiRenderer? {
    return Win32GuiRenderer()
}
