package gunzip.domain.entities

import kotlinx.datetime.Instant

sealed class ExtractionResult {
    data class Success(
        val extractedPath: String,
        val extractedFiles: List<String>,
        val originalArchivePath: String,
        val strategy: ExtractionStrategy,
        val completedAt: Instant
    ) : ExtractionResult()

    data class Failure(
        val archivePath: String,
        val error: ExtractionError,
        val failedAt: Instant
    ) : ExtractionResult()
}

data class ExtractionProgress(
    val archivePath: String,
    val currentFile: String? = null,
    val filesProcessed: Int = 0,
    val totalFiles: Int = 0,
    val bytesProcessed: Long = 0,
    val totalBytes: Long = 0,
    val stage: ExtractionStage = ExtractionStage.STARTING
) {
    val progressPercentage: Float
        get() = if (totalBytes > 0) {
            (bytesProcessed.toFloat() / totalBytes.toFloat() * 100f).coerceIn(0f, 100f)
        } else if (totalFiles > 0) {
            (filesProcessed.toFloat() / totalFiles.toFloat() * 100f).coerceIn(0f, 100f)
        } else 0f

    val isComplete: Boolean
        get() = stage == ExtractionStage.COMPLETED || stage == ExtractionStage.FAILED
}

enum class ExtractionStage {
    STARTING,
    ANALYZING,
    EXTRACTING,
    FINALIZING,
    COMPLETED,
    FAILED
}

enum class ExtractionStrategy {
    SINGLE_FILE_TO_DIRECTORY,  // Single file archive -> extract to same directory
    MULTIPLE_FILES_TO_FOLDER,  // Multiple files -> create folder with archive name
    SINGLE_FOLDER_TO_DIRECTORY // Single root folder -> extract contents to same directory
}

sealed class ExtractionError(override val message: String, override val cause: Throwable? = null) : Throwable(message, cause) {
    class CorruptedArchive(message: String = "Archive is corrupted or invalid") : ExtractionError(message)
    class UnsupportedFormat(format: String) : ExtractionError("Unsupported archive format: $format")
    class PasswordRequired(message: String = "Archive is password protected") : ExtractionError(message)
    class InsufficientSpace(required: Long, available: Long) :
        ExtractionError("Insufficient disk space. Required: ${required / 1024 / 1024}MB, Available: ${available / 1024 / 1024}MB")
    class PermissionDenied(path: String) : ExtractionError("Permission denied accessing: $path")
    class FileNotFound(path: String) : ExtractionError("Archive file not found: $path")
    class IOError(message: String, cause: Throwable? = null) : ExtractionError("IO error: $message", cause)
    class SevenZipError(exitCode: Int, message: String) : ExtractionError("7zip error (code $exitCode): $message")
    class UnknownError(message: String, cause: Throwable? = null) : ExtractionError("Unknown error: $message", cause)
}