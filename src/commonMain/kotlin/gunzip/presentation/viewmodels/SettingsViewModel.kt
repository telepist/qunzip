package gunzip.presentation.viewmodels

import gunzip.domain.entities.UserPreferences
import gunzip.domain.repositories.PreferencesRepository
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
    private val logger: Logger = Logger.withTag("SettingsViewModel")
) {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        loadPreferences()
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

    fun setShowCompletionDialog(enabled: Boolean) {
        updatePreference { it.copy(showCompletionDialog = enabled) }
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
        _uiState.value = _uiState.value.copy(error = null)
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
    val error: String? = null
)

sealed class SettingsEvent {
    object PreferencesSaved : SettingsEvent()
    object PreferencesReset : SettingsEvent()
}
