package qunzip.presentation.viewmodels

import qunzip.domain.entities.*
import qunzip.domain.repositories.PreferencesRepository
import qunzip.domain.usecases.ExtractArchiveUseCase
import qunzip.domain.usecases.ValidateArchiveUseCase
import qunzip.domain.usecases.ValidationResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import co.touchlab.kermit.Logger

class ExtractionViewModel(
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val validateArchiveUseCase: ValidateArchiveUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope,
    private val logger: Logger = Logger.withTag("ExtractionViewModel")
) {
    private val _uiState = MutableStateFlow(ExtractionUiState())
    val uiState: StateFlow<ExtractionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExtractionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ExtractionEvent> = _events.asSharedFlow()

    private var currentExtractionJob: Job? = null

    fun extractArchive(archivePath: String, password: String? = null) {
        // Cancel any ongoing extraction
        currentExtractionJob?.cancel()

        currentExtractionJob = scope.launch {
            try {
                logger.i { "Starting extraction process for: $archivePath" }

                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        currentArchive = archivePath,
                        isWaitingForPassword = false,
                        // Clear any FAILED progress from a previous attempt so the
                        // ApplicationViewModel auto-exit observer doesn't fire
                        // immediately on a retry.
                        progress = null
                    )
                }

                // Skip validation when retrying with a password (already validated)
                if (password == null) {
                    // First validate the archive
                    when (val validationResult = validateArchiveUseCase(archivePath)) {
                        is ValidationResult.Invalid -> {
                            logger.e { "Archive validation failed: ${validationResult.error.message}" }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = validationResult.error.message,
                                    // Mark progress as FAILED so the auto-exit observer
                                    // in ApplicationViewModel triggers; otherwise the GUI
                                    // dialog would hang on a fast validation failure.
                                    progress = ExtractionProgress(
                                        archivePath = archivePath,
                                        stage = ExtractionStage.FAILED
                                    )
                                )
                            }
                            _events.tryEmit(ExtractionEvent.ValidationFailed(validationResult.error))
                            return@launch
                        }
                        is ValidationResult.Valid -> {
                            logger.i { "Archive validation successful: ${validationResult.archive.name}" }
                            _uiState.update { it.copy(archive = validationResult.archive) }
                        }
                        is ValidationResult.PasswordRequired -> {
                            logger.i { "Archive requires password: ${validationResult.archive.name}" }
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    archive = validationResult.archive,
                                    isWaitingForPassword = true
                                )
                            }
                            _events.tryEmit(ExtractionEvent.PasswordRequired)
                            return@launch
                        }
                    }
                }

                // Load user preferences for extraction options
                val preferences = preferencesRepository.loadPreferences()
                val extractionOptions = preferences.toExtractionOptions().copy(password = password)
                logger.d { "Using extraction options: $extractionOptions" }

                // Start extraction with progress tracking
                extractArchiveUseCase(archivePath, extractionOptions)
                    .onStart {
                        logger.i { "Extraction started" }
                        _events.tryEmit(ExtractionEvent.ExtractionStarted)
                    }
                    .onCompletion { throwable ->
                        _uiState.update { it.copy(isLoading = false) }

                        if (throwable != null) {
                            logger.e(throwable) { "Extraction completed with error" }
                            _events.tryEmit(ExtractionEvent.ExtractionFailed(throwable))
                        } else {
                            logger.i { "Extraction completed successfully" }
                            _events.tryEmit(ExtractionEvent.ExtractionCompleted)
                        }
                    }
                    // The use case emits a FAILED progress and then rethrows the
                    // error. Handle that rethrow here, inside the flow: the
                    // extraction repository is a channelFlow whose producer runs
                    // as a child coroutine, so on Kotlin/Native its failure
                    // propagates up the Job hierarchy to this launch's uncaught
                    // handler and crashes the whole process — a plain try/catch
                    // around collect does NOT stop it. Flow.catch intercepts the
                    // upstream failure in-flow and lets us surface it as UI state.
                    .catch { throwable ->
                        handleExtractionThrowable(archivePath, throwable)
                    }
                    .collect { progress ->
                        _uiState.update {
                            it.copy(
                                progress = progress,
                                isExtracting = progress.stage == ExtractionStage.EXTRACTING
                            )
                        }

                        // Emit specific events based on progress stage
                        when (progress.stage) {
                            ExtractionStage.ANALYZING -> {
                                _events.tryEmit(ExtractionEvent.AnalyzingArchive)
                            }
                            ExtractionStage.EXTRACTING -> {
                                _events.tryEmit(ExtractionEvent.ProgressUpdated(progress))
                            }
                            ExtractionStage.FINALIZING -> {
                                _events.tryEmit(ExtractionEvent.Finalizing)
                            }
                            ExtractionStage.COMPLETED -> {
                                _events.tryEmit(ExtractionEvent.ExtractionCompleted)
                            }
                            ExtractionStage.FAILED -> {
                                _events.tryEmit(ExtractionEvent.ExtractionFailed(null))
                            }
                            else -> { /* No specific event for other stages */ }
                        }
                    }

            } catch (e: CancellationException) {
                throw e // never swallow cancellation
            } catch (e: Throwable) {
                // Safety net for the validation phase (validation, preference load)
                // and any error thrown at use-case invocation before the flow is
                // collected; flow-phase errors are handled by the in-flow .catch
                // above. Note ExtractionError extends Throwable (not Exception),
                // so this must catch Throwable to handle it. Same handling either way.
                handleExtractionThrowable(archivePath, e)
            }
        }
    }

    /**
     * Translate a thrown extraction error into UI state. Shared by the in-flow
     * [kotlinx.coroutines.flow.catch] handler and the outer try/catch so a
     * failure never escapes to crash the process, and the window stays open
     * showing the error (see ApplicationViewModel's auto-exit observer, which
     * keeps standalone windows open on FAILED).
     */
    private fun handleExtractionThrowable(archivePath: String, throwable: Throwable) {
        if (throwable is ExtractionError.PasswordRequired) {
            // Wrong/missing password — go back to the password prompt.
            logger.w { "Wrong password, prompting again" }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isExtracting = false,
                    isWaitingForPassword = true,
                    error = throwable.message
                )
            }
            _events.tryEmit(ExtractionEvent.PasswordRequired)
        } else {
            logger.e(throwable) { "Unexpected error during extraction" }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isExtracting = false,
                    isWaitingForPassword = false,
                    error = throwable.message ?: "Unknown error occurred",
                    // Mark FAILED so the auto-exit observer fires; for a standalone
                    // (double-click) launch this keeps the window open on error.
                    progress = ExtractionProgress(
                        archivePath = archivePath,
                        stage = ExtractionStage.FAILED
                    )
                )
            }
            _events.tryEmit(ExtractionEvent.ExtractionFailed(throwable))
        }
    }

    /**
     * Mark extraction as failed without running a flow — used for pre-extraction
     * failures (e.g. an unsupported file type or a file-open error) so the UI
     * reaches a FAILED state instead of hanging on "Preparing…" forever. The
     * FAILED progress drives the ApplicationViewModel auto-exit observer.
     */
    fun reportError(archivePath: String, message: String) {
        logger.w { "Reporting extraction error for $archivePath: $message" }
        _uiState.update {
            it.copy(
                isLoading = false,
                isExtracting = false,
                isWaitingForPassword = false,
                currentArchive = archivePath,
                error = message,
                progress = ExtractionProgress(archivePath, stage = ExtractionStage.FAILED)
            )
        }
        _events.tryEmit(ExtractionEvent.ExtractionFailed(null))
    }

    fun submitPassword(password: String) {
        val archivePath = _uiState.value.currentArchive ?: return
        logger.i { "Password submitted, retrying extraction" }
        extractArchive(archivePath, password)
    }

    fun cancelExtraction() {
        logger.i { "Cancelling extraction" }
        currentExtractionJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = false,
                isExtracting = false,
                isWaitingForPassword = false,
                progress = ExtractionProgress(
                    archivePath = it.currentArchive ?: "",
                    stage = ExtractionStage.FAILED
                ),
                error = "Cancelled"
            )
        }
        _events.tryEmit(ExtractionEvent.ExtractionCancelled)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        currentExtractionJob?.cancel()
        _uiState.value = ExtractionUiState()
        logger.i { "ViewModel state reset" }
    }

    fun clearPasswordError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class ExtractionUiState(
    val isLoading: Boolean = false,
    val isExtracting: Boolean = false,
    val isWaitingForPassword: Boolean = false,
    val currentArchive: String? = null,
    val archive: Archive? = null,
    val progress: ExtractionProgress? = null,
    val error: String? = null
) {
    val canCancel: Boolean get() = isExtracting || isWaitingForPassword
    val showProgress: Boolean get() = progress != null && isExtracting
    val progressPercentage: Float get() = progress?.progressPercentage ?: 0f
}

sealed class ExtractionEvent {
    object ExtractionStarted : ExtractionEvent()
    object AnalyzingArchive : ExtractionEvent()
    data class ProgressUpdated(val progress: ExtractionProgress) : ExtractionEvent()
    object Finalizing : ExtractionEvent()
    object ExtractionCompleted : ExtractionEvent()
    object ExtractionCancelled : ExtractionEvent()
    object PasswordRequired : ExtractionEvent()
    data class ExtractionFailed(val throwable: Throwable?) : ExtractionEvent()
    data class ValidationFailed(val error: ExtractionError) : ExtractionEvent()
}
