package qunzip.platform

import qunzip.domain.entities.FileAssociation
import qunzip.domain.entities.AssociationResult
import qunzip.domain.entities.ArchiveFormat
import qunzip.domain.repositories.FileAssociationRepository
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

            // Each extension gets its own ProgID so Windows Explorer shows
            // the correct type name (e.g., "ZIP Archive", "RAR Archive")
            val progId = progIdForExtension(extension)

            // Create the ProgID structure for this format
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
            // Remove the current ProgID and any legacy `Qunzip.*` prefix older
            // installs wrote. Try BOTH the per-user (HKCU\Software\Classes) and
            // system-wide (HKCR) hive: an association may have been registered
            // under a different privilege level than this unregister run (the
            // installer registers elevated; a later CLI call may be unelevated).
            // Deletes in a hive we can't write just fail harmlessly.
            val sanitized = extension.removePrefix(".").replace('.', '_')
            val progIds = listOf("QuickUnzip.$sanitized", "Qunzip.$sanitized")
            val hives = listOf(
                Pair(HKEY_CURRENT_USER, "$HKCU_CLASSES_PATH\\"),
                Pair(HKEY_CLASSES_ROOT, "")
            )

            var removedAny = false
            for (hive in hives) {
                val (rootKey, prefix) = hive
                for (progId in progIds) {
                    if (removeExtensionAssociation(extension, progId, hive, registryHelper)) {
                        removedAny = true
                    }
                    // Best-effort delete of the ProgID tree itself; true only if it existed.
                    if (registryHelper.deleteKeyTree(rootKey, "${prefix}${progId}")) {
                        removedAny = true
                    }
                }
            }

            // Notify Windows Shell of the change
            registryHelper.notifyShellAssociationChanged()

            val message = if (removedAny) {
                logger.i { "Unregistered association for .$extension" }
                "Successfully unregistered .$extension"
            } else {
                logger.w { "No association found to remove for .$extension" }
                "No association found for .$extension"
            }
            AssociationResult(success = true, extension = extension, message = message)
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
            supportedExtensions.mapNotNull { ext ->
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

    companion object {
        // Single source of truth: every extension any ArchiveFormat declares.
        private val supportedExtensions: List<String> =
            ArchiveFormat.entries.flatMap { it.extensions }.distinct()

        /**
         * Maps a file extension to its current (post-rename) ProgID.
         * e.g., "zip" -> "QuickUnzip.zip", "tar.gz" -> "QuickUnzip.tar_gz"
         */
        fun progIdForExtension(extension: String): String {
            val sanitized = extension.removePrefix(".").replace('.', '_')
            return "QuickUnzip.$sanitized"
        }

        /**
         * Pre-rename ProgID prefix; kept so we can clean up registry entries
         * left behind by older installs during --unregister-associations.
         */
        private fun legacyProgIdForExtension(extension: String): String {
            val sanitized = extension.removePrefix(".").replace('.', '_')
            return "Qunzip.$sanitized"
        }

        /** All ProgIDs that may have been registered (current + legacy), for cleanup. */
        val allProgIds: List<String>
            get() = supportedExtensions.map { progIdForExtension(it) } +
                supportedExtensions.map { legacyProgIdForExtension(it) } +
                listOf("QuickUnzip.ArchiveFile", "Qunzip.ArchiveFile") // legacy single ProgIDs
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
