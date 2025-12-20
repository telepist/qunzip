package gunzip.presentation.viewmodels

import gunzip.domain.entities.*
import gunzip.domain.usecases.ExtractArchiveUseCase
import gunzip.domain.usecases.ValidateArchiveUseCase
import gunzip.domain.usecases.ValidationResult
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import co.touchlab.kermit.Logger

class ExtractionViewModel(
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val validateArchiveUseCase: ValidateArchiveUseCase,
    private val scope: CoroutineScope,
    private val logger: Logger = Logger.withTag("ExtractionViewModel")
) {
    private val _uiState = MutableStateFlow(ExtractionUiState())
    val uiState: StateFlow<ExtractionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ExtractionEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ExtractionEvent> = _events.asSharedFlow()

    private var currentExtractionJob: Job? = null

    fun extractArchive(archivePath: String) {
        // Cancel any ongoing extraction
        currentExtractionJob?.cancel()

        currentExtractionJob = scope.launch {
            try {
                logger.i { "Starting extraction process for: $archivePath" }

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null,
                    currentArchive = archivePath
                )

                // First validate the archive
                when (val validationResult = validateArchiveUseCase(archivePath)) {
                    is ValidationResult.Invalid -> {
                        logger.e { "Archive validation failed: ${validationResult.error.message}" }
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = validationResult.error.message
                        )
                        _events.tryEmit(ExtractionEvent.ValidationFailed(validationResult.error))
                        return@launch
                    }
                    is ValidationResult.Valid -> {
                        logger.i { "Archive validation successful: ${validationResult.archive.name}" }
                        _uiState.value = _uiState.value.copy(
                            archive = validationResult.archive
                        )
                    }
                }

                // Start extraction with progress tracking
                extractArchiveUseCase(archivePath)
                    .onStart {
                        logger.i { "Extraction started" }
                        _events.tryEmit(ExtractionEvent.ExtractionStarted)
                    }
                    .onCompletion { throwable ->
                        _uiState.value = _uiState.value.copy(isLoading = false)

                        if (throwable != null) {
                            logger.e(throwable) { "Extraction completed with error" }
                            _events.tryEmit(ExtractionEvent.ExtractionFailed(throwable))
                        } else {
                            logger.i { "Extraction completed successfully" }
                            _events.tryEmit(ExtractionEvent.ExtractionCompleted)
                        }
                    }
                    .collect { progress ->
                        _uiState.value = _uiState.value.copy(
                            progress = progress,
                            isExtracting = progress.stage == ExtractionStage.EXTRACTING
                        )

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

            } catch (e: Exception) {
                logger.e(e) { "Unexpected error during extraction" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isExtracting = false,
                    error = e.message ?: "Unknown error occurred"
                )
                _events.tryEmit(ExtractionEvent.ExtractionFailed(e))
            }
        }
    }

    fun cancelExtraction() {
        logger.i { "Cancelling extraction" }
        currentExtractionJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isExtracting = false
        )
        _events.tryEmit(ExtractionEvent.ExtractionCancelled)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun reset() {
        currentExtractionJob?.cancel()
        _uiState.value = ExtractionUiState()
        logger.i { "ViewModel state reset" }
    }
}

data class ExtractionUiState(
    val isLoading: Boolean = false,
    val isExtracting: Boolean = false,
    val currentArchive: String? = null,
    val archive: Archive? = null,
    val progress: ExtractionProgress? = null,
    val error: String? = null
) {
    val canCancel: Boolean get() = isExtracting
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
    data class ExtractionFailed(val throwable: Throwable?) : ExtractionEvent()
    data class ValidationFailed(val error: ExtractionError) : ExtractionEvent()
}