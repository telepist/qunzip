package qunzip.presentation.ui

import androidx.compose.runtime.*
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.domain.entities.ExtractionStage
import qunzip.presentation.ui.tui.MosaicApp
import qunzip.exitProcess
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
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
 * Signal that coordinates exit between UI callbacks and the Mosaic composition.
 * Uses CompletableDeferred so it can be tested without Mosaic/Compose runtime.
 */
internal class ExitSignal {
    private val deferred = CompletableDeferred<Unit>()
    fun signal() { deferred.complete(Unit) }
    suspend fun await() { deferred.await() }
}

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

                    // In standalone mode, Mosaic's NonInteractiveTerminal doesn't process
                    // keyboard events. Handle password input by reading directly from the
                    // console using platform APIs.
                    LaunchedEffect(Unit) {
                        handleStandalonePasswordInput(viewModel)
                    }

                    // Monitor extraction completion directly via the ExtractionViewModel's
                    // state. This avoids the ApplicationViewModel event chain which has race
                    // conditions with multiple layers of scope.launch on Dispatchers.Default.
                    LaunchedEffect(Unit) {
                        awaitExtractionDone(viewModel)
                        delay(300)
                        // Exit nonzero on failure so callers can detect it. This
                        // path self-exits (it can't cleanly return to runApp from
                        // inside runMosaic), so the exit code is decided here.
                        exitProcess(if (viewModel.didExtractionFail) 1 else 0)
                    }
                }
            } else {
                // CLI: full interactive terminal with auto-detected capabilities
                val exitSignal = ExitSignal()
                runMosaic {
                    MosaicApp(viewModel, onExit = { exitSignal.signal() })

                    LaunchedEffect(Unit) {
                        awaitExtractionDone(viewModel)
                        delay(150)
                        throw ExitException()
                    }

                    LaunchedEffect(Unit) {
                        exitSignal.await()
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

/**
 * Handle password input in standalone mode by monitoring the ViewModel state
 * and reading from the console directly when a password is needed.
 * Loops to handle wrong password retries.
 */
private suspend fun handleStandalonePasswordInput(viewModel: ApplicationViewModel) {
    val extractionVm = viewModel.extractionViewModel
    // Wait for password-required state, then read from console
    extractionVm.uiState
        .filter { it.isWaitingForPassword }
        .collect {
            // Read password on a background thread to avoid blocking the composition
            val password = withContext(Dispatchers.Default) {
                readLineFromConsole()
            }
            if (password != null && password.isNotEmpty()) {
                extractionVm.submitPassword(password)
            } else {
                // Escape or empty input — cancel
                extractionVm.cancelExtraction()
            }
        }
}
