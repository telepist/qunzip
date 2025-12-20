package gunzip.domain.repositories

interface NotificationRepository {

    /**
     * Show success notification to user
     */
    suspend fun showSuccessNotification(
        title: String,
        message: String,
        extractedPath: String? = null
    )

    /**
     * Show error notification to user
     */
    suspend fun showErrorNotification(
        title: String,
        message: String,
        details: String? = null
    )

    /**
     * Show progress notification for long-running operations
     */
    suspend fun showProgressNotification(
        id: String,
        title: String,
        message: String,
        progress: Float, // 0.0 to 1.0
        cancellable: Boolean = false
    )

    /**
     * Update existing progress notification
     */
    suspend fun updateProgressNotification(
        id: String,
        message: String,
        progress: Float
    )

    /**
     * Cancel/hide progress notification
     */
    suspend fun cancelProgressNotification(id: String)

    /**
     * Show information notification
     */
    suspend fun showInfoNotification(
        title: String,
        message: String
    )

    /**
     * Check if notifications are supported/enabled on the platform
     */
    fun areNotificationsSupported(): Boolean

    /**
     * Request notification permissions if needed (mobile platforms)
     */
    suspend fun requestNotificationPermission(): Boolean

    /**
     * Open file manager at specified path when notification is clicked
     */
    suspend fun showNotificationWithAction(
        title: String,
        message: String,
        actionLabel: String,
        actionPath: String
    )
}