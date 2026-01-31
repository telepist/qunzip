package qunzip.presentation.viewmodels

import qunzip.domain.entities.*
import qunzip.domain.usecases.ManageFileAssociationsUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import co.touchlab.kermit.Logger

class FileAssociationViewModel(
    private val manageFileAssociationsUseCase: ManageFileAssociationsUseCase,
    private val scope: CoroutineScope,
    private val logger: Logger = Logger.withTag("FileAssociationViewModel")
) {
    private val _uiState = MutableStateFlow(FileAssociationUiState())
    val uiState: StateFlow<FileAssociationUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FileAssociationEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FileAssociationEvent> = _events.asSharedFlow()

    // Note: checkCurrentAssociations() is NOT called on init for faster startup
    // Call refreshAssociations() when the associations need to be displayed

    fun registerAssociations(applicationPath: String) {
        scope.launch {
            try {
                logger.i { "Registering file associations for: $applicationPath" }

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )

                val results = manageFileAssociationsUseCase.registerAssociations(applicationPath)

                val successCount = results.count { it.success }
                val totalCount = results.size

                logger.i { "Association registration completed: $successCount/$totalCount successful" }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    registrationResults = results,
                    isRegistered = successCount > 0
                )

                if (successCount == totalCount) {
                    _events.tryEmit(FileAssociationEvent.RegistrationCompleted(successCount))
                } else {
                    _events.tryEmit(
                        FileAssociationEvent.PartialRegistrationCompleted(
                            successCount,
                            totalCount
                        )
                    )
                }

                // Refresh current associations
                checkCurrentAssociations()

            } catch (e: Exception) {
                logger.e(e) { "Error during association registration" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
                _events.tryEmit(FileAssociationEvent.RegistrationFailed(e))
            }
        }
    }

    fun unregisterAssociations() {
        scope.launch {
            try {
                logger.i { "Unregistering file associations" }

                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    error = null
                )

                val results = manageFileAssociationsUseCase.unregisterAssociations()

                val successCount = results.count { it.success }
                val totalCount = results.size

                logger.i { "Association unregistration completed: $successCount/$totalCount successful" }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    unregistrationResults = results,
                    isRegistered = false
                )

                if (successCount == totalCount) {
                    _events.tryEmit(FileAssociationEvent.UnregistrationCompleted(successCount))
                } else {
                    _events.tryEmit(
                        FileAssociationEvent.PartialUnregistrationCompleted(
                            successCount,
                            totalCount
                        )
                    )
                }

                // Refresh current associations
                checkCurrentAssociations()

            } catch (e: Exception) {
                logger.e(e) { "Error during association unregistration" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
                _events.tryEmit(FileAssociationEvent.UnregistrationFailed(e))
            }
        }
    }

    private fun checkCurrentAssociations() {
        scope.launch {
            try {
                val associations = manageFileAssociationsUseCase.checkAssociations()
                val qunzipAssociations = associations.filter {
                    it.applicationName.contains("Qunzip", ignoreCase = true)
                }

                _uiState.value = _uiState.value.copy(
                    currentAssociations = associations,
                    qunzipAssociations = qunzipAssociations,
                    isRegistered = qunzipAssociations.isNotEmpty()
                )

                logger.i { "Found ${qunzipAssociations.size} Qunzip associations out of ${associations.size} total" }

            } catch (e: Exception) {
                logger.e(e) { "Error checking current associations" }
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Error checking associations"
                )
            }
        }
    }

    fun handleFileOpened(filePath: String) {
        scope.launch {
            try {
                logger.i { "Handling file opened: $filePath" }

                val isSupported = manageFileAssociationsUseCase.handleFileOpened(filePath)

                if (isSupported) {
                    _events.tryEmit(FileAssociationEvent.SupportedFileOpened(filePath))
                } else {
                    _events.tryEmit(FileAssociationEvent.UnsupportedFileOpened(filePath))
                }

            } catch (e: Exception) {
                logger.e(e) { "Error handling file opened: $filePath" }
                _events.tryEmit(FileAssociationEvent.FileOpenError(filePath, e))
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refreshAssociations() {
        checkCurrentAssociations()
    }
}

data class FileAssociationUiState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val currentAssociations: List<FileAssociation> = emptyList(),
    val qunzipAssociations: List<FileAssociation> = emptyList(),
    val registrationResults: List<AssociationResult> = emptyList(),
    val unregistrationResults: List<AssociationResult> = emptyList(),
    val error: String? = null
) {
    val supportedExtensions: List<String>
        get() = ArchiveFormat.values().flatMap { it.extensions }

    val registeredExtensions: List<String>
        get() = qunzipAssociations.map { it.extension }

    val unregisteredExtensions: List<String>
        get() = supportedExtensions - registeredExtensions.toSet()

    val registrationProgress: Float
        get() = if (supportedExtensions.isEmpty()) 1f
                else registeredExtensions.size.toFloat() / supportedExtensions.size.toFloat()
}

sealed class FileAssociationEvent {
    data class RegistrationCompleted(val count: Int) : FileAssociationEvent()
    data class PartialRegistrationCompleted(val successful: Int, val total: Int) : FileAssociationEvent()
    data class RegistrationFailed(val error: Throwable) : FileAssociationEvent()

    data class UnregistrationCompleted(val count: Int) : FileAssociationEvent()
    data class PartialUnregistrationCompleted(val successful: Int, val total: Int) : FileAssociationEvent()
    data class UnregistrationFailed(val error: Throwable) : FileAssociationEvent()

    data class SupportedFileOpened(val filePath: String) : FileAssociationEvent()
    data class UnsupportedFileOpened(val filePath: String) : FileAssociationEvent()
    data class FileOpenError(val filePath: String, val error: Throwable) : FileAssociationEvent()
}