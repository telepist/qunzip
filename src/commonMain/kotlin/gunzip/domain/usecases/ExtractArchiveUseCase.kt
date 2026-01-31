package gunzip.domain.usecases

import gunzip.domain.entities.*
import gunzip.domain.repositories.ArchiveRepository
import gunzip.domain.repositories.FileSystemRepository
import gunzip.domain.repositories.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

open class ExtractArchiveUseCase(
    private val archiveRepository: ArchiveRepository,
    private val fileSystemRepository: FileSystemRepository,
    private val notificationRepository: NotificationRepository
) {
    open suspend operator fun invoke(
        archivePath: String,
        options: ExtractionOptions = ExtractionOptions()
    ): Flow<ExtractionProgress> = flow {
        try {
            emit(ExtractionProgress(archivePath, stage = ExtractionStage.STARTING))

            val archive = archiveRepository.getArchiveInfo(archivePath)
                ?: throw ExtractionError.FileNotFound(archivePath)

            emit(ExtractionProgress(archivePath, stage = ExtractionStage.ANALYZING))

            val contents = archiveRepository.getArchiveContents(archivePath)
            val strategy = determineExtractionStrategy(contents)
            val parentDir = fileSystemRepository.getParentDirectory(archivePath)

            // Check disk space
            val requiredSpace = contents.totalSize
            val availableSpace = fileSystemRepository.getAvailableSpace(parentDir)
            if (availableSpace < requiredSpace) {
                throw ExtractionError.InsufficientSpace(requiredSpace, availableSpace)
            }

            emit(ExtractionProgress(
                archivePath = archivePath,
                totalFiles = contents.fileCount,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.EXTRACTING
            ))

            // Determine target path and check for conflicts
            val targetName = when (strategy) {
                ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY -> contents.topLevelEntries.first().name
                ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY -> contents.topLevelEntries.first().name
                ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER -> archive.nameWithoutExtension
            }
            val targetPath = fileSystemRepository.joinPath(parentDir, targetName)
            val hasConflict = fileSystemRepository.exists(targetPath)

            // For multi-file, always create unique folder upfront (no temp needed)
            val finalPath: String
            if (strategy == ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER) {
                finalPath = generateUniquePath(targetPath)
                fileSystemRepository.createDirectory(finalPath)

                archiveRepository.extractArchive(archivePath, finalPath)
                    .collect { progress -> emit(progress.copy(stage = ExtractionStage.EXTRACTING)) }
            } else if (hasConflict) {
                // Single file or folder with conflict: use temp folder
                val tempFolder = createTempFolder(parentDir)
                fileSystemRepository.createDirectory(tempFolder)

                archiveRepository.extractArchive(archivePath, tempFolder)
                    .collect { progress -> emit(progress.copy(stage = ExtractionStage.EXTRACTING)) }

                // Move to final location
                val extractedItem = fileSystemRepository.joinPath(tempFolder, targetName)
                finalPath = if (strategy == ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY) {
                    generateUniqueFilePath(targetPath)
                } else {
                    generateUniquePath(targetPath)
                }

                fileSystemRepository.moveFile(extractedItem, finalPath)
                fileSystemRepository.deleteDirectory(tempFolder)
            } else {
                // No conflict: extract directly
                finalPath = targetPath
                archiveRepository.extractArchive(archivePath, parentDir)
                    .collect { progress -> emit(progress.copy(stage = ExtractionStage.EXTRACTING)) }
            }

            emit(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = contents.fileCount,
                totalFiles = contents.fileCount,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.FINALIZING
            ))

            if (options.moveToTrashAfterExtraction) {
                fileSystemRepository.moveToTrash(archivePath)
            }

            if (options.showCompletionDialog) {
                notificationRepository.showSuccessNotification(
                    title = "Extraction Complete",
                    message = "${archive.name} extracted successfully",
                    extractedPath = finalPath
                )
            }

            emit(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = contents.fileCount,
                totalFiles = contents.fileCount,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.COMPLETED
            ))

        } catch (error: ExtractionError) {
            notificationRepository.showErrorNotification(
                title = "Extraction Failed",
                message = error.message
            )
            emit(ExtractionProgress(archivePath = archivePath, stage = ExtractionStage.FAILED))
            throw error
        } catch (throwable: Throwable) {
            val error = ExtractionError.UnknownError(
                message = throwable.message ?: "Unknown error occurred",
                cause = throwable
            )
            notificationRepository.showErrorNotification(
                title = "Extraction Failed",
                message = error.message
            )
            emit(ExtractionProgress(archivePath = archivePath, stage = ExtractionStage.FAILED))
            throw error
        }
    }

    private fun determineExtractionStrategy(contents: ArchiveContents): ExtractionStrategy {
        return when {
            contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isFile -> {
                ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY
            }
            contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isDirectory -> {
                ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY
            }
            else -> {
                ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER
            }
        }
    }

    private fun createTempFolder(parentDir: String): String {
        val hash = Random.nextInt(0x100000, 0xFFFFFF).toString(16)
        return fileSystemRepository.joinPath(parentDir, "gunzip_$hash")
    }

    private suspend fun generateUniquePath(basePath: String): String {
        if (!fileSystemRepository.exists(basePath)) {
            return basePath
        }
        var counter = 1
        var uniquePath: String
        do {
            uniquePath = "$basePath-$counter"
            counter++
        } while (fileSystemRepository.exists(uniquePath))
        return uniquePath
    }

    private suspend fun generateUniqueFilePath(basePath: String): String {
        if (!fileSystemRepository.exists(basePath)) {
            return basePath
        }
        val fileName = fileSystemRepository.getFilename(basePath)
        val parentDir = fileSystemRepository.getParentDirectory(basePath)
        val extension = fileSystemRepository.getFileExtension(basePath)
        val nameWithoutExtension = fileSystemRepository.getFilenameWithoutExtension(fileName)

        var counter = 1
        var uniquePath: String
        do {
            val newName = if (extension.isNotEmpty()) {
                "$nameWithoutExtension-$counter.$extension"
            } else {
                "$nameWithoutExtension-$counter"
            }
            uniquePath = fileSystemRepository.joinPath(parentDir, newName)
            counter++
        } while (fileSystemRepository.exists(uniquePath))
        return uniquePath
    }
}
