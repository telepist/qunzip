package gunzip.presentation.ui

import co.touchlab.kermit.Logger

/**
 * macOS Cocoa native GUI renderer
 * TODO: Implement Cocoa/AppKit dialogs using Objective-C interop
 */
actual fun createNativeGuiRenderer(): UiRenderer? {
    val logger = Logger.withTag("CocoaGui")
    logger.w { "Cocoa GUI not yet implemented" }
    // TODO: Return CocoaGuiRenderer() when implemented
    return null
}
