package gunzip.presentation.ui.tui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import gunzip.presentation.viewmodels.ApplicationViewModel
import gunzip.presentation.viewmodels.ApplicationMode
import com.jakewharton.mosaic.ui.Column

/**
 * Root Mosaic composable that switches between extraction and settings modes
 */
@Composable
fun MosaicApp(viewModel: ApplicationViewModel, onExit: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        when (uiState.mode) {
            ApplicationMode.EXTRACTION -> {
                ExtractionTui(viewModel.extractionViewModel)
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
