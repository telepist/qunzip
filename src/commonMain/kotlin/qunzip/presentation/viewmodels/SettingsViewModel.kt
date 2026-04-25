package qunzip.presentation.viewmodels

import qunzip.domain.entities.UserPreferences
import qunzip.domain.repositories.CliShimRepository
import qunzip.domain.repositories.NoOpCliShimRepository
import qunzip.domain.repositories.PreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import co.touchlab.kermit.Logger

/**
 * ViewModel for managing user preferences/settings
 */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val scope: CoroutineScope,
    private val cliShimRepository: CliShimRepository = NoOpCliShimRepository(),
    private val logger: Logger = Logger.withTag("SettingsViewModel")
) {
    private val _uiState = MutableStateFlow(
        SettingsUiState(cliShimSupported = cliShimRepository.isSupported)
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadPreferences()
        refreshCliShimState()
    }

    fun loadPreferences() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val preferences = preferencesRepository.loadPreferences()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    preferences = preferences,
                    preferencesPath = preferencesRepository.getPreferencesPath()
                )
                logger.d { "Preferences loaded: $preferences" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to load preferences" }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load preferences: ${e.message}"
                )
            }
        }
    }

    fun setMoveToTrashAfterExtraction(enabled: Boolean) {
        updatePreference { it.copy(moveToTrashAfterExtraction = enabled) }
    }

    fun setAutoCloseAfterExtraction(enabled: Boolean) {
        updatePreference { it.copy(autoCloseAfterExtraction = enabled) }
    }

    fun resetToDefaults() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            try {
                preferencesRepository.resetToDefaults()
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    preferences = UserPreferences.DEFAULT
                )
                _events.tryEmit(SettingsEvent.PreferencesReset)
                logger.i { "Preferences reset to defaults" }
            } catch (e: Exception) {
                logger.e(e) { "Failed to reset preferences" }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = "Failed to reset preferences: ${e.message}"
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Re-read whether the CLI shim is currently installed. */
    fun refreshCliShimState() {
        if (!cliShimRepository.isSupported) return
        scope.launch {
            try {
                val installed = cliShimRepository.isInstalled()
                _uiState.update { it.copy(cliShimInstalled = installed) }
            } catch (e: Exception) {
                logger.e(e) { "Failed to read CLI shim state" }
            }
        }
    }

    /**
     * Toggle whether `qunzip` is on the user's PATH. Optimistic — the UI
     * shows the new state immediately, and we revert on failure.
     */
    fun setCliShimInstalled(install: Boolean) {
        if (!cliShimRepository.isSupported) return
        scope.launch {
            val previous = _uiState.value.cliShimInstalled
            _uiState.update { it.copy(isCliShimWorking = true, cliShimInstalled = install, cliShimMessage = null) }
            val result = if (install) cliShimRepository.install() else cliShimRepository.uninstall()
            if (result.success) {
                _uiState.update { it.copy(isCliShimWorking = false, cliShimMessage = result.message) }
                _events.tryEmit(SettingsEvent.CliShimChanged(install))
            } else {
                logger.w { "CLI shim toggle failed: ${result.message}" }
                _uiState.update {
                    it.copy(
                        isCliShimWorking = false,
                        cliShimInstalled = previous,
                        cliShimMessage = result.message,
                        error = result.message
                    )
                }
            }
        }
    }

    private fun updatePreference(update: (UserPreferences) -> UserPreferences) {
        scope.launch {
            val currentPreferences = _uiState.value.preferences
            val newPreferences = update(currentPreferences)

            _uiState.value = _uiState.value.copy(isSaving = true, preferences = newPreferences)

            try {
                val success = preferencesRepository.savePreferences(newPreferences)
                if (success) {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                    _events.tryEmit(SettingsEvent.PreferencesSaved)
                    logger.d { "Preferences saved: $newPreferences" }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        preferences = currentPreferences, // Revert
                        error = "Failed to save preferences"
                    )
                }
            } catch (e: Exception) {
                logger.e(e) { "Failed to save preferences" }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    preferences = currentPreferences, // Revert
                    error = "Failed to save preferences: ${e.message}"
                )
            }
        }
    }
}

data class SettingsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val preferences: UserPreferences = UserPreferences.DEFAULT,
    val preferencesPath: String = "",
    val error: String? = null,
    /** True on platforms where install/uninstall actually does something. */
    val cliShimSupported: Boolean = false,
    /** True when the install dir is currently on the user's PATH. */
    val cliShimInstalled: Boolean = false,
    /** True while a shim install/uninstall is in flight. */
    val isCliShimWorking: Boolean = false,
    /** Last status message from a shim toggle (e.g. "Added to PATH"). */
    val cliShimMessage: String? = null,
)

sealed class SettingsEvent {
    object PreferencesSaved : SettingsEvent()
    object PreferencesReset : SettingsEvent()
    data class CliShimChanged(val installed: Boolean) : SettingsEvent()
}
