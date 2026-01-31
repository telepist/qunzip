package qunzip.domain.entities

import kotlinx.serialization.Serializable

/**
 * User preferences for the Qunzip application.
 * Persisted to a JSON file in the user's config directory.
 */
@Serializable
data class UserPreferences(
    /**
     * Whether to move the original archive to trash after successful extraction.
     * Default is false for safety - users must opt-in to this behavior.
     */
    val moveToTrashAfterExtraction: Boolean = false,

    /**
     * Whether to show a completion dialog with OK button when extraction completes.
     * When false (default), the application silently closes after successful extraction.
     * When true, shows "Extraction complete!" dialog and waits for user to click OK.
     */
    val showCompletionDialog: Boolean = false
) {
    /**
     * Convert to ExtractionOptions for use in extraction use case
     */
    fun toExtractionOptions(): ExtractionOptions = ExtractionOptions(
        moveToTrashAfterExtraction = moveToTrashAfterExtraction,
        showCompletionDialog = showCompletionDialog
    )

    companion object {
        /**
         * Default preferences
         */
        val DEFAULT = UserPreferences()
    }
}
