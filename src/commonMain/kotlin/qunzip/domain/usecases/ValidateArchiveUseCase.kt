package qunzip.domain.usecases

import qunzip.domain.entities.*
import qunzip.domain.repositories.ArchiveRepository
import qunzip.domain.repositories.FileSystemRepository
import co.touchlab.kermit.Logger

open class ValidateArchiveUseCase(
    private val archiveRepository: ArchiveRepository,
    private val fileSystemRepository: FileSystemRepository,
    private val logger: Logger = Logger.withTag("ArchiveValidator")
) {
    open suspend operator fun invoke(archivePath: String): ValidationResult {
        return try {
            logger.i { "Validating archive: $archivePath" }

            // Check if file exists
            if (!fileSystemRepository.exists(archivePath)) {
                return ValidationResult.Invalid(
                    error = ExtractionError.FileNotFound(archivePath)
                )
            }

            // Check if file is readable
            if (!fileSystemRepository.isReadable(archivePath)) {
                return ValidationResult.Invalid(
                    error = ExtractionError.PermissionDenied(archivePath)
                )
            }

            // Get file info
            val fileInfo = fileSystemRepository.getFileInfo(archivePath)
            if (fileInfo.size == 0L) {
                return ValidationResult.Invalid(
                    error = ExtractionError.CorruptedArchive("Archive file is empty")
                )
            }

            // Determine format from filename
            val filename = archivePath.substringAfterLast('/')
                .substringAfterLast('\\') // Handle both Unix and Windows paths

            val format = ArchiveFormat.fromFilename(filename)
                ?: return ValidationResult.Invalid(
                    error = ExtractionError.UnsupportedFormat(filename.substringAfterLast('.'))
                )

            // Create archive entity
            val archive = Archive(
                path = archivePath,
                name = filename,
                format = format,
                size = fileInfo.size,
                lastModified = fileInfo.lastModified
            )

            // Test archive integrity using 7zip
            val isValid = archiveRepository.testArchive(archivePath)
            if (!isValid) {
                return ValidationResult.Invalid(
                    error = ExtractionError.CorruptedArchive("Archive integrity check failed")
                )
            }

            logger.i { "Archive validation successful: ${archive.name} (${archive.format.displayName})" }

            ValidationResult.Valid(archive)

        } catch (e: ExtractionError) {
            logger.e { "Archive validation failed: ${e.message}" }
            ValidationResult.Invalid(error = e)
        } catch (e: Exception) {
            logger.e(e) { "Unexpected error during archive validation" }
            ValidationResult.Invalid(
                error = ExtractionError.UnknownError(
                    message = e.message ?: "Unknown validation error",
                    cause = e
                )
            )
        }
    }
}

sealed class ValidationResult {
    data class Valid(val archive: Archive) : ValidationResult()
    data class Invalid(val error: ExtractionError) : ValidationResult()
}

data class FileInfo(
    val path: String,
    val size: Long,
    val lastModified: kotlinx.datetime.Instant? = null,
    val isReadable: Boolean = true,
    val isDirectory: Boolean = false
)