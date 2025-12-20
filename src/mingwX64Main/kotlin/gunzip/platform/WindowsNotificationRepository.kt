package gunzip.platform

import gunzip.domain.repositories.NotificationRepository
import co.touchlab.kermit.Logger

/**
 * Windows implementation of NotificationRepository
 * Uses console output for now (Windows Toast notifications would require more complex setup)
 */
class WindowsNotificationRepository(
    private val logger: Logger = Logger.withTag("WindowsNotificationRepository")
) : NotificationRepository {

    override suspend fun showSuccessNotification(
        title: String,
        message: String,
        extractedPath: String?
    ) {
        logger.i { "SUCCESS: $title - $message" }
        println("✓ $title")
        println("  $message")
        if (extractedPath != null) {
            println("  Location: $extractedPath")
        }
    }

    override suspend fun showErrorNotification(
        title: String,
        message: String,
        details: String?
    ) {
        logger.e { "ERROR: $title - $message" }
        println("✗ $title")
        println("  $message")
        if (details != null) {
            println("  Details: $details")
        }
    }

    override suspend fun showProgressNotification(
        id: String,
        title: String,
        message: String,
        progress: Float,
        cancellable: Boolean
    ) {
        logger.d { "PROGRESS ($id): $title - $message (${(progress * 100).toInt()}%)" }
        val percentage = (progress * 100).toInt()
        val bar = "=".repeat(percentage / 5) + " ".repeat(20 - percentage / 5)
        println("\r[$bar] $percentage% - $message")
    }

    override suspend fun updateProgressNotification(
        id: String,
        message: String,
        progress: Float
    ) {
        showProgressNotification(id, "Processing", message, progress, false)
    }

    override suspend fun cancelProgressNotification(id: String) {
        logger.d { "Cancelling progress notification: $id" }
        println() // New line after progress
    }

    override suspend fun showInfoNotification(title: String, message: String) {
        logger.i { "INFO: $title - $message" }
        println("ℹ $title")
        println("  $message")
    }

    override fun areNotificationsSupported(): Boolean {
        return true // Console output is always supported
    }

    override suspend fun requestNotificationPermission(): Boolean {
        return true // No permission needed for console output
    }

    override suspend fun showNotificationWithAction(
        title: String,
        message: String,
        actionLabel: String,
        actionPath: String
    ) {
        showSuccessNotification(title, message, actionPath)
        println("  Action: $actionLabel")
    }
}
