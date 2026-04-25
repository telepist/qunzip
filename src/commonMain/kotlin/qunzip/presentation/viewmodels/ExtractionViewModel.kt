package qunzip.presentation.viewmodels

import qunzip.domain.entities.*
import qunzip.domain.repositories.PreferencesRepository
import qunzip.domain.usecases.ExtractArchiveUseCase
import qunzip.domain.usecases.ValidateArchiveUseCase
import qunzip.domain.usecases.ValidationResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

            } catch (e: ExtractionError.PasswordRequired) {
                // Wrong password - go back to password prompt
                logger.w { "Wrong password, prompting again" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isExtracting = false,
                        isWaitingForPassword = true,
                        error = e.message
                    )
                }
                _events.tryEmit(ExtractionEvent.PasswordRequired)
            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during extraction" }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isExtracting = false,
                        isWaitingForPassword = false,
                        error = e.message ?: "Unknown error occurred",
                        // Mark FAILED so the auto-exit observer fires for thrown
                        // errors too (e.g., file system issues mid-extraction).
                        progress = ExtractionProgress(
                            archivePath = archivePath,
                            stage = ExtractionStage.FAILED
                        )
                    )
                }
                _events.tryEmit(ExtractionEvent.ExtractionFailed(e))
            }
        }
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
