package qunzip.presentation.ui

import co.touchlab.kermit.Logger

/**
 * Linux GTK native GUI renderer
 * TODO: Implement GTK dialogs using C interop
 */
actual fun createNativeGuiRenderer(): UiRenderer? {
    val logger = Logger.withTag("GtkGui")
    logger.w { "GTK GUI not yet implemented" }
    // TODO: Return GtkGuiRenderer() when implemented
    return null
}
