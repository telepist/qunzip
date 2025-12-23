package gunzip.domain.entities

import kotlinx.serialization.Serializable

/**
 * User preferences for the Gunzip application.
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
     * Whether to show a notification when extraction completes.
     * Default is true.
     */
    val showCompletionNotification: Boolean = true,

    /**
     * Whether to automatically close the application after successful extraction.
     * Default is true for seamless double-click experience.
     */
    val autoCloseAfterExtraction: Boolean = true
) {
    /**
     * Convert to ExtractionOptions for use in extraction use case
     */
    fun toExtractionOptions(): ExtractionOptions = ExtractionOptions(
        moveToTrashAfterExtraction = moveToTrashAfterExtraction,
        showCompletionNotification = showCompletionNotification
    )

    companion object {
        /**
         * Default preferences
         */
        val DEFAULT = UserPreferences()
    }
}
