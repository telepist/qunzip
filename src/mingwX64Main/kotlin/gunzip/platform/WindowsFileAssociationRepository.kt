package gunzip.platform

import gunzip.domain.entities.FileAssociation
import gunzip.domain.entities.AssociationResult
import gunzip.domain.repositories.FileAssociationRepository
import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Windows implementation of FileAssociationRepository
 * Uses Windows Registry API for file association management
 */
@OptIn(ExperimentalForeignApi::class)
class WindowsFileAssociationRepository(
    private val logger: Logger = Logger.withTag("WindowsFileAssociationRepository")
) : FileAssociationRepository {

    private val registryHelper = RegistryHelper()

    // ProgID used for all Gunzip archive associations
    private val progId = "Gunzip.ArchiveFile"

    override suspend fun registerAssociation(
        extension: String,
        applicationPath: String,
        applicationName: String,
        description: String
    ): AssociationResult = withContext(Dispatchers.IO) {
        logger.i { "Registering association for .$extension with $applicationPath" }

        try {
            // Get the appropriate root key based on privileges
            val rootKeyPair = registryHelper.getFileAssociationRootKey()
            val (rootKey, prefix) = rootKeyPair

            val isAdmin = prefix.isEmpty()
            logger.d { "Using ${if (isAdmin) "system-wide" else "user-specific"} registry" }

            // Create the ProgID structure (if not already created)
            // This is safe to call multiple times
            if (!createProgId(progId, description, applicationPath, rootKeyPair, registryHelper)) {
                logger.e { "Failed to create ProgID for .$extension" }
                return@withContext AssociationResult(
                    success = false,
                    extension = extension,
                    message = "Failed to create ProgID in registry. ${if (!isAdmin) "Try running as administrator." else ""}"
                )
            }

            // Associate the extension with the ProgID
            if (!associateExtensionWithProgId(extension, progId, rootKeyPair, registryHelper)) {
                logger.e { "Failed to associate .$extension with $progId" }
                return@withContext AssociationResult(
                    success = false,
                    extension = extension,
                    message = "Failed to create extension registry entry. ${if (!isAdmin) "Try running as administrator." else ""}"
                )
            }

            // Notify Windows Shell of the change
            registryHelper.notifyShellAssociationChanged()

            logger.i { "Successfully registered association for .$extension" }
            AssociationResult(
                success = true,
                extension = extension,
                message = "Successfully registered .$extension with $applicationName"
            )
        } catch (e: Exception) {
            logger.e(e) { "Exception while registering association for .$extension" }
            AssociationResult(
                success = false,
                extension = extension,
                message = "Error: ${e.message}"
            )
        }
    }

    override suspend fun unregisterAssociation(extension: String): AssociationResult = withContext(Dispatchers.IO) {
        logger.i { "Unregistering association for .$extension" }

        try {
            val rootKeyPair = registryHelper.getFileAssociationRootKey()

            // Remove the extension association
            if (!removeExtensionAssociation(extension, progId, rootKeyPair, registryHelper)) {
                logger.w { "Could not remove association for .$extension (may not exist)" }
            }

            // Notify Windows Shell of the change
            registryHelper.notifyShellAssociationChanged()

            logger.i { "Successfully unregistered association for .$extension" }
            AssociationResult(
                success = true,
                extension = extension,
                message = "Successfully unregistered .$extension"
            )
        } catch (e: Exception) {
            logger.e(e) { "Exception while unregistering association for .$extension" }
            AssociationResult(
                success = false,
                extension = extension,
                message = "Error: ${e.message}"
            )
        }
    }

    override suspend fun getAssociation(extension: String): FileAssociation? = withContext(Dispatchers.IO) {
        logger.d { "Getting association for .$extension" }

        try {
            val rootKeyPair = registryHelper.getFileAssociationRootKey()

            // Get the ProgID associated with this extension
            val currentProgId = getExtensionProgId(extension, rootKeyPair, registryHelper)

            if (currentProgId != null) {
                // Read the application path from the ProgID's command key
                val (rootKey, prefix) = rootKeyPair
                val commandKey = registryHelper.openKey(
                    rootKey,
                    "${prefix}${currentProgId}\\shell\\open\\command",
                    KEY_READ.toUInt()
                )

                if (commandKey != null) {
                    val commandLine = registryHelper.getStringValue(commandKey, null)
                    registryHelper.closeKey(commandKey)

                    if (commandLine != null) {
                        // Extract the application path from the command line
                        // Format is: "C:\Path\To\App.exe" "%1"
                        val appPath = commandLine
                            .substringBefore("\" \"")
                            .removePrefix("\"")

                        return@withContext FileAssociation(
                            extension = extension,
                            applicationName = currentProgId,
                            applicationPath = appPath,
                            isDefault = true
                        )
                    }
                }
            }

            null
        } catch (e: Exception) {
            logger.e(e) { "Exception while getting association for .$extension" }
            null
        }
    }

    override suspend fun isAssociatedWithApplication(
        extension: String,
        applicationPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        logger.d { "Checking if .$extension is associated with $applicationPath" }

        try {
            val association = getAssociation(extension)
            if (association == null) {
                return@withContext false
            }

            // Normalize paths for comparison (case-insensitive on Windows)
            val normalizedExpected = applicationPath.lowercase().replace("/", "\\")
            val normalizedActual = association.applicationPath.lowercase().replace("/", "\\")

            normalizedExpected == normalizedActual
        } catch (e: Exception) {
            logger.e(e) { "Exception while checking association for .$extension" }
            false
        }
    }

    override suspend fun getAllAssociations(): List<FileAssociation> = withContext(Dispatchers.IO) {
        logger.d { "Getting all associations" }

        try {
            // Get all common archive extensions
            val commonExtensions = listOf(
                "zip", "7z", "rar", "tar", "tar.gz", "tar.bz2", "tar.xz",
                "tgz", "tbz2", "txz", "cab", "arj", "lzh"
            )

            commonExtensions.mapNotNull { ext ->
                getAssociation(ext)
            }
        } catch (e: Exception) {
            logger.e(e) { "Exception while getting all associations" }
            emptyList()
        }
    }

    override fun supportsFileAssociations(): Boolean {
        return true // Windows supports file associations
    }

    override suspend fun registerMultipleAssociations(
        extensions: List<String>,
        applicationPath: String,
        applicationName: String,
        description: String
    ): List<AssociationResult> = withContext(Dispatchers.IO) {
        logger.i { "Registering ${extensions.size} file associations" }

        extensions.map { extension ->
            registerAssociation(extension, applicationPath, applicationName, description)
        }
    }

    override suspend fun requestElevatedPrivileges(): Boolean {
        logger.w { "UAC elevation request not implemented - use 'Run as Administrator'" }

        // UAC elevation would require:
        // 1. Creating a new process with ShellExecuteEx and "runas" verb
        // 2. Passing the current command-line arguments
        // 3. Exiting the current process
        // This is complex and better handled by the installer

        return false
    }
}
