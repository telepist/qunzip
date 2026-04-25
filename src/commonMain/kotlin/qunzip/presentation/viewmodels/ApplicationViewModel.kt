package qunzip.presentation.viewmodels

import qunzip.domain.entities.ExtractionStage
import qunzip.domain.usecases.ExtractArchiveUseCase
import qunzip.domain.usecases.ManageFileAssociationsUseCase
import qunzip.domain.usecases.ValidateArchiveUseCase
import qunzip.domain.repositories.CliShimRepository
import qunzip.domain.repositories.NoOpCliShimRepository
import qunzip.domain.repositories.PreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
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
    private val cliShimRepository: CliShimRepository = NoOpCliShimRepository(),
    private val logger: Logger = Logger.withTag("ApplicationViewModel"),
    private val isStandaloneLaunch: Boolean = false
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
        cliShimRepository = cliShimRepository,
        logger = logger
    )

    private val _uiState = MutableStateFlow(ApplicationUiState())
    val uiState: StateFlow<ApplicationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ApplicationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ApplicationEvent> = _events.asSharedFlow()

    init {
        // Observe child ViewModel events and coordinate.
        // Use UNDISPATCHED start so collectors subscribe synchronously before any
        // extraction events can be emitted (prevents race with fast extractions).
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
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

        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            extractionViewModel.events.collect { event ->
                when (event) {
                    is ExtractionEvent.ExtractionCompleted -> {
                        _events.tryEmit(ApplicationEvent.ExtractionCompleted)
                    }
                    is ExtractionEvent.ExtractionFailed -> {
                        _events.tryEmit(ApplicationEvent.ExtractionFailed(event.throwable))
                    }
                    else -> { /* Forward other extraction events if needed */ }
                }
            }
        }

        // Drive auto-exit off the StateFlow rather than the event SharedFlow.
        // Events use tryEmit with extraBufferCapacity=1, so for small/fast extractions
        // ExtractionCompleted can be dropped when emissions outpace the collector.
        // StateFlow conflates the latest value, so observing the COMPLETED/FAILED stage
        // here is reliable regardless of how fast the extraction finished.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            extractionViewModel.uiState
                .mapNotNull { it.progress?.stage }
                .filter { it == ExtractionStage.COMPLETED || it == ExtractionStage.FAILED }
                .collect { handleExtractionFinished() }
        }
    }

    /**
     * Decide whether to auto-exit after extraction finishes.
     * - CLI launch: always exit (user gets their shell back, output stays in scrollback)
     * - Standalone launch (double-click): exit if autoCloseAfterExtraction is true (default).
     *   When false, keeps the window open so the user can see the result.
     */
    private fun handleExtractionFinished() {
        if (!isStandaloneLaunch) {
            // CLI: always exit
            _uiState.update { it.copy(shouldExit = true) }
        } else {
            // Standalone (double-click): check user preference
            scope.launch {
                val prefs = preferencesRepository.loadPreferences()
                if (prefs.autoCloseAfterExtraction) {
                    _uiState.update { it.copy(shouldExit = true) }
                } else {
                    // Keep window visible, show hint to close
                    _uiState.update { it.copy(showCloseHint = true) }
                }
            }
        }
    }

    fun handleApplicationStart(args: List<String>) {
        logger.i { "Application started with args: $args" }

        _uiState.update { it.copy(isStarting = true) }

        scope.launch {
            try {
                // Check if started with file argument (double-click scenario)
                val filePath = args.firstOrNull()

                if (filePath != null) {
                    logger.i { "Started with file argument: $filePath" }
                    fileAssociationViewModel.handleFileOpened(filePath)
                    _uiState.update {
                        it.copy(
                            isStarting = false,
                            mode = ApplicationMode.EXTRACTION,
                            targetFile = filePath
                        )
                    }
                } else {
                    logger.i { "Started without file argument, checking associations" }
                    _uiState.update {
                        it.copy(
                            isStarting = false,
                            mode = ApplicationMode.SETUP
                        )
                    }
                }

            } catch (e: Exception) {
                logger.e(e) { "Error during application startup" }
                _uiState.update {
                    it.copy(
                        isStarting = false,
                        error = e.message ?: "Startup error"
                    )
                }
                _events.tryEmit(ApplicationEvent.StartupError(e))
            }
        }
    }

    fun handleApplicationExit() {
        logger.i { "Application exit requested" }

        // Cancel any ongoing operations
        extractionViewModel.cancelExtraction()

        _uiState.update { it.copy(shouldExit = true) }
        _events.tryEmit(ApplicationEvent.ApplicationExit)
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
        extractionViewModel.clearError()
        fileAssociationViewModel.clearError()
    }

    fun setMode(mode: ApplicationMode) {
        _uiState.update { it.copy(mode = mode) }
    }

}

data class ApplicationUiState(
    val isStarting: Boolean = false,
    val mode: ApplicationMode = ApplicationMode.SETUP,
    val targetFile: String? = null,
    val shouldExit: Boolean = false,
    val showCloseHint: Boolean = false,
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
