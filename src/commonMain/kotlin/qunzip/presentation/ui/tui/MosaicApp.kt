package qunzip.presentation.ui.tui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.viewmodels.ApplicationMode
import com.jakewharton.mosaic.ui.Column

/**
 * Root Mosaic composable that switches between extraction and settings modes.
 *
 * Exit from extraction mode is handled by the renderer (UiRenderer) which
 * observes ExtractionViewModel state directly. The [onExit] callback is used
 * only by SettingsTui for its "Quit" action.
 */
@Composable
fun MosaicApp(viewModel: ApplicationViewModel, onExit: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        when (uiState.mode) {
            ApplicationMode.EXTRACTION -> {
                ExtractionTui(viewModel.extractionViewModel, showCloseHint = uiState.showCloseHint)
            }
            ApplicationMode.SETUP -> {
                SettingsTui(
                    fileAssociationViewModel = viewModel.fileAssociationViewModel,
                    settingsViewModel = viewModel.settingsViewModel,
                    onExit = onExit
                )
            }
        }
    }
}
