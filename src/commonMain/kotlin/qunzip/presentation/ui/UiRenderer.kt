package qunzip.presentation.ui

import androidx.compose.runtime.*
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.domain.entities.ExtractionStage
import qunzip.presentation.ui.tui.MosaicApp
import qunzip.exitProcess
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import com.jakewharton.mosaic.NonInteractivePolicy
import com.jakewharton.mosaic.runMosaic
import com.jakewharton.mosaic.terminal.AnsiLevel

/**
 * Common interface for UI backends
 */
interface UiRenderer {
    suspend fun render(viewModel: ApplicationViewModel)
}

/**
 * Exception thrown to cleanly exit the Mosaic composition.
 * Mosaic doesn't yet have a proper exit API, so we throw from a LaunchedEffect
 * and catch outside runMosaic. This lets Mosaic restore terminal state before exit.
 * See: https://github.com/JakeWharton/mosaic/issues/963
 */
class ExitException : RuntimeException()

/**
 * Mosaic-based TUI renderer.
 *
 * @param isStandaloneLaunch When true (double-click / file association launch), uses
 *   NonInteractiveTerminal with truecolor to avoid raw mode and blocking event reads
 *   that prevent clean process exit. When false (CLI), uses full interactive TtyTerminal.
 */
class MosaicTuiRenderer(
    private val isStandaloneLaunch: Boolean = false,
) : UiRenderer {
    override suspend fun render(viewModel: ApplicationViewModel) {
        try {
            if (isStandaloneLaunch) {
                // Standalone: skip TTY binding to avoid raw mode / blocking stdin reads.
                // Use NonInteractiveTerminal with truecolor so ANSI colors render correctly.
                // VT processing on the console handle is enabled by configureStandaloneConsole().
                runMosaic(
                    onNonInteractive = NonInteractivePolicy.AssumeAndIgnore,
                    ansiLevel = AnsiLevel.TRUECOLOR,
                ) {
                    MosaicApp(viewModel)

                    // Monitor extraction completion directly via the ExtractionViewModel's
                    // state. This avoids the ApplicationViewModel event chain which has race
                    // conditions with multiple layers of scope.launch on Dispatchers.Default.
                    LaunchedEffect(Unit) {
                        awaitExtractionDone(viewModel)
                        delay(300)
                        exitProcess(0)
                    }
                }
            } else {
                // CLI: full interactive terminal with auto-detected capabilities
                runMosaic {
                    MosaicApp(viewModel)

                    LaunchedEffect(Unit) {
                        awaitExtractionDone(viewModel)
                        delay(150)
                        throw ExitException()
                    }
                }
            }
        } catch (_: ExitException) {
            // Mosaic has cleaned up terminal state, exit normally
        }
    }
}

/**
 * Suspend until the extraction reaches a terminal state (COMPLETED or FAILED).
 * Observes ExtractionViewModel.uiState directly rather than going through the
 * ApplicationViewModel event chain, which avoids race conditions caused by
 * multiple layers of scope.launch on Dispatchers.Default.
 */
private suspend fun awaitExtractionDone(viewModel: ApplicationViewModel) {
    viewModel.extractionViewModel.uiState
        .mapNotNull { it.progress?.stage }
        .filter { it == ExtractionStage.COMPLETED || it == ExtractionStage.FAILED }
        .first()
}
