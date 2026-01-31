package qunzip.domain.repositories

import qunzip.domain.entities.AssociationResult
import qunzip.domain.entities.FileAssociation

interface FileAssociationRepository {

    /**
     * Register file association for a specific extension
     */
    suspend fun registerAssociation(
        extension: String,
        applicationPath: String,
        applicationName: String,
        description: String
    ): AssociationResult

    /**
     * Unregister file association for a specific extension
     */
    suspend fun unregisterAssociation(extension: String): AssociationResult

    /**
     * Get current file association for an extension
     */
    suspend fun getAssociation(extension: String): FileAssociation?

    /**
     * Check if extension is currently associated with this application
     */
    suspend fun isAssociatedWithApplication(extension: String, applicationPath: String): Boolean

    /**
     * Get all registered associations for this application
     */
    suspend fun getAllAssociations(): List<FileAssociation>

    /**
     * Check if system supports file associations
     */
    fun supportsFileAssociations(): Boolean

    /**
     * Register as default application for multiple extensions at once
     */
    suspend fun registerMultipleAssociations(
        extensions: List<String>,
        applicationPath: String,
        applicationName: String,
        description: String
    ): List<AssociationResult>

    /**
     * Request administrator privileges for registration (platform-specific)
     */
    suspend fun requestElevatedPrivileges(): Boolean
}