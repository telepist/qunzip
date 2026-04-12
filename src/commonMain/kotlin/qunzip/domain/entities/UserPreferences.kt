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
     * Whether to automatically close the window after extraction completes.
     * Default is true - the window closes after extraction.
     * When false, keeps the window open so the user can see the result.
     */
    val autoCloseAfterExtraction: Boolean = true
) {
    /**
     * Convert to ExtractionOptions for use in extraction use case
     */
    fun toExtractionOptions(): ExtractionOptions = ExtractionOptions(
        moveToTrashAfterExtraction = moveToTrashAfterExtraction,
        autoCloseAfterExtraction = autoCloseAfterExtraction
    )

    companion object {
        /**
         * Default preferences
         */
        val DEFAULT = UserPreferences()
    }
}
