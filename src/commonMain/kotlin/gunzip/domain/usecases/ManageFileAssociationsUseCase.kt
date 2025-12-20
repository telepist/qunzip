package gunzip.domain.usecases

import gunzip.domain.entities.*
import gunzip.domain.repositories.FileAssociationRepository
import co.touchlab.kermit.Logger

open class ManageFileAssociationsUseCase(
    private val fileAssociationRepository: FileAssociationRepository,
    private val logger: Logger = Logger.withTag("FileAssociations")
) {
    suspend fun registerAssociations(applicationPath: String): List<AssociationResult> {
        val supportedFormats = ArchiveFormat.values()
        val results = mutableListOf<AssociationResult>()

        for (format in supportedFormats) {
            for (extension in format.extensions) {
                try {
                    val result = fileAssociationRepository.registerAssociation(
                        extension = extension,
                        applicationPath = applicationPath,
                        applicationName = "Gunzip",
                        description = format.displayName
                    )

                    results.add(result)

                    if (result.success) {
                        logger.i { "Successfully registered association for .$extension" }
                    } else {
                        logger.w { "Failed to register association for .$extension: ${result.message}" }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error registering association for .$extension" }
                    results.add(AssociationResult(
                        success = false,
                        extension = extension,
                        message = e.message
                    ))
                }
            }
        }

        return results
    }

    suspend fun unregisterAssociations(): List<AssociationResult> {
        val supportedFormats = ArchiveFormat.values()
        val results = mutableListOf<AssociationResult>()

        for (format in supportedFormats) {
            for (extension in format.extensions) {
                try {
                    val result = fileAssociationRepository.unregisterAssociation(extension)
                    results.add(result)

                    if (result.success) {
                        logger.i { "Successfully unregistered association for .$extension" }
                    } else {
                        logger.w { "Failed to unregister association for .$extension: ${result.message}" }
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error unregistering association for .$extension" }
                    results.add(AssociationResult(
                        success = false,
                        extension = extension,
                        message = e.message
                    ))
                }
            }
        }

        return results
    }

    suspend fun checkAssociations(): List<FileAssociation> {
        val supportedFormats = ArchiveFormat.values()
        val associations = mutableListOf<FileAssociation>()

        for (format in supportedFormats) {
            for (extension in format.extensions) {
                try {
                    val association = fileAssociationRepository.getAssociation(extension)
                    if (association != null) {
                        associations.add(association)
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Error checking association for .$extension" }
                }
            }
        }

        return associations
    }

    suspend fun isAssociatedWithGunzip(extension: String): Boolean {
        return try {
            val association = fileAssociationRepository.getAssociation(extension)
            association?.applicationName?.contains("Gunzip", ignoreCase = true) == true
        } catch (e: Exception) {
            logger.e(e) { "Error checking if .$extension is associated with Gunzip" }
            false
        }
    }

    suspend fun handleFileOpened(filePath: String): Boolean {
        logger.i { "File opened: $filePath" }

        // Validate file exists and is a supported archive
        val archive = try {
            val filename = filePath.substringAfterLast('/')
                .substringAfterLast('\\') // Handle both Unix and Windows paths
            val format = ArchiveFormat.fromFilename(filename)

            if (format == null) {
                logger.w { "Unsupported file format: $filename" }
                return false
            }

            Archive(
                path = filePath,
                name = filename,
                format = format,
                size = 0L // Will be populated by repository if needed
            )
        } catch (e: Exception) {
            logger.e(e) { "Error processing opened file: $filePath" }
            return false
        }

        logger.i { "Processing ${archive.format.displayName}: ${archive.name}" }
        return true
    }
}