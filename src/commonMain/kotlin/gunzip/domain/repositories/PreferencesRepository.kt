package gunzip.domain.repositories

import gunzip.domain.entities.UserPreferences

/**
 * Repository for loading and saving user preferences.
 * Implementations handle platform-specific storage locations.
 */
interface PreferencesRepository {
    /**
     * Load user preferences from storage.
     * Returns default preferences if no saved preferences exist.
     */
    suspend fun loadPreferences(): UserPreferences

    /**
     * Save user preferences to storage.
     * @return true if save was successful, false otherwise
     */
    suspend fun savePreferences(preferences: UserPreferences): Boolean

    /**
     * Get the path where preferences are stored.
     * Useful for debugging and user information.
     */
    fun getPreferencesPath(): String

    /**
     * Check if preferences file exists.
     */
    suspend fun preferencesExist(): Boolean

    /**
     * Reset preferences to defaults.
     * @return true if reset was successful, false otherwise
     */
    suspend fun resetToDefaults(): Boolean
}
