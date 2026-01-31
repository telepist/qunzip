package gunzip.presentation.viewmodels

import gunzip.domain.usecases.ExtractArchiveUseCase
import gunzip.domain.usecases.ManageFileAssociationsUseCase
import gunzip.domain.usecases.ValidateArchiveUseCase
import gunzip.domain.repositories.PreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import co.touchlab.kermit.Logger

/**
 * Main application ViewModel that coordinates between extraction and file association ViewModels
 */
class ApplicationViewModel(
    private val extractArchiveUseCase: ExtractArchiveUseCase,
    private val validateArchiveUseCase: ValidateArchiveUseCase,
    private val manageFileAssociationsUseCase: ManageFileAssociationsUseCase,
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope,
    private val logger: Logger = Logger.withTag("ApplicationViewModel")
) {
    // Child ViewModels
    val extractionViewModel = ExtractionViewModel(
        extractArchiveUseCase = extractArchiveUseCase,
        validateArchiveUseCase = validateArchiveUseCase,
        preferencesRepository = preferencesRepository,
        scope = scope,
        logger = logger
    )

    val fileAssociationViewModel = FileAssociationViewModel(
        manageFileAssociationsUseCase = manageFileAssociationsUseCase,
        scope = scope,
        logger = logger
    )

    val settingsViewModel = SettingsViewModel(
        preferencesRepository = preferencesRepository,
        scope = scope,
        logger = logger
    )

    private val _uiState = MutableStateFlow(ApplicationUiState())
    val uiState: StateFlow<ApplicationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ApplicationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ApplicationEvent> = _events.asSharedFlow()

    init {
        // Observe child ViewModel events and coordinate
        scope.launch {
            fileAssociationViewModel.events.collect { event ->
                when (event) {
                    is FileAssociationEvent.SupportedFileOpened -> {
                        logger.i { "Supported file opened, starting extraction: ${event.filePath}" }
                        extractionViewModel.extractArchive(event.filePath)
                        _events.tryEmit(ApplicationEvent.AutoExtractionStarted(event.filePath))
                    }
                    is FileAssociationEvent.UnsupportedFileOpened -> {
                        logger.w { "Unsupported file opened: ${event.filePath}" }
                        _events.tryEmit(ApplicationEvent.UnsupportedFileOpened(event.filePath))
                    }
                    else -> { /* Handle other file association events if needed */ }
                }
            }
        }

        scope.launch {
            extractionViewModel.events.collect { event ->
                when (event) {
                    is ExtractionEvent.ExtractionCompleted -> {
                        _events.tryEmit(ApplicationEvent.ExtractionCompleted)
                        // Always signal exit after extraction completes
                        // (GUI layer handles showing completion dialog if needed based on showCompletionDialog preference)
                        _uiState.value = _uiState.value.copy(shouldExit = true)
                    }
                    is ExtractionEvent.ExtractionFailed -> {
                        _events.tryEmit(ApplicationEvent.ExtractionFailed(event.throwable))
                    }
                    else -> { /* Forward other extraction events if needed */ }
                }
            }
        }
    }

    fun handleApplicationStart(args: List<String>) {
        logger.i { "Application started with args: $args" }

        _uiState.value = _uiState.value.copy(isStarting = true)

        scope.launch {
            try {
                // Check if started with file argument (double-click scenario)
                val filePath = args.firstOrNull()

                if (filePath != null) {
                    logger.i { "Started with file argument: $filePath" }
                    fileAssociationViewModel.handleFileOpened(filePath)
                    _uiState.value = _uiState.value.copy(
                        isStarting = false,
                        mode = ApplicationMode.EXTRACTION,
                        targetFile = filePath
                    )
                } else {
                    logger.i { "Started without file argument, checking associations" }
                    _uiState.value = _uiState.value.copy(
                        isStarting = false,
                        mode = ApplicationMode.SETUP
                    )
                }

            } catch (e: Exception) {
                logger.e(e) { "Error during application startup" }
                _uiState.value = _uiState.value.copy(
                    isStarting = false,
                    error = e.message ?: "Startup error"
                )
                _events.tryEmit(ApplicationEvent.StartupError(e))
            }
        }
    }

    fun handleApplicationExit() {
        logger.i { "Application exit requested" }

        // Cancel any ongoing operations
        extractionViewModel.cancelExtraction()

        _uiState.value = _uiState.value.copy(shouldExit = true)
        _events.tryEmit(ApplicationEvent.ApplicationExit)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
        extractionViewModel.clearError()
        fileAssociationViewModel.clearError()
    }

    fun setMode(mode: ApplicationMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun acknowledgeExit() {
        _uiState.value = _uiState.value.copy(shouldExit = false)
    }
}

data class ApplicationUiState(
    val isStarting: Boolean = false,
    val mode: ApplicationMode = ApplicationMode.SETUP,
    val targetFile: String? = null,
    val shouldExit: Boolean = false,
    val error: String? = null
) {
    val isInExtractionMode: Boolean get() = mode == ApplicationMode.EXTRACTION
    val isInSetupMode: Boolean get() = mode == ApplicationMode.SETUP
}

enum class ApplicationMode {
    SETUP,      // Setting up file associations
    EXTRACTION  // Extracting an archive
}

sealed class ApplicationEvent {
    data class AutoExtractionStarted(val filePath: String) : ApplicationEvent()
    data class UnsupportedFileOpened(val filePath: String) : ApplicationEvent()
    object ExtractionCompleted : ApplicationEvent()
    data class ExtractionFailed(val error: Throwable?) : ApplicationEvent()
    data class StartupError(val error: Throwable) : ApplicationEvent()
    object ApplicationExit : ApplicationEvent()
}