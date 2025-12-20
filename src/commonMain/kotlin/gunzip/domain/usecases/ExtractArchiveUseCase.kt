package gunzip.domain.usecases

import gunzip.domain.entities.*
import gunzip.domain.repositories.ArchiveRepository
import gunzip.domain.repositories.FileSystemRepository
import gunzip.domain.repositories.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock

open class ExtractArchiveUseCase(
    private val archiveRepository: ArchiveRepository,
    private val fileSystemRepository: FileSystemRepository,
    private val notificationRepository: NotificationRepository
) {
    open suspend operator fun invoke(archivePath: String): Flow<ExtractionProgress> = flow {
        try {
            emit(ExtractionProgress(archivePath, stage = ExtractionStage.STARTING))

            // Validate archive exists and is readable
            val archive = archiveRepository.getArchiveInfo(archivePath)
                ?: throw ExtractionError.FileNotFound(archivePath)

            emit(ExtractionProgress(archivePath, stage = ExtractionStage.ANALYZING))

            // Analyze archive contents
            val contents = archiveRepository.getArchiveContents(archivePath)
            val strategy = determineExtractionStrategy(contents)

            // Check available disk space
            val requiredSpace = contents.totalSize
            val availableSpace = fileSystemRepository.getAvailableSpace(
                fileSystemRepository.getParentDirectory(archivePath)
            )

            if (availableSpace < requiredSpace) {
                throw ExtractionError.InsufficientSpace(requiredSpace, availableSpace)
            }

            emit(ExtractionProgress(
                archivePath = archivePath,
                totalFiles = contents.fileCount,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.EXTRACTING
            ))

            // Determine extraction destination
            val extractionPath = determineExtractionPath(archive, contents, strategy)

            // Create destination directory if needed
            if (strategy == ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER) {
                fileSystemRepository.createDirectory(extractionPath)
            }

            // Extract archive with progress tracking
            archiveRepository.extractArchive(archivePath, extractionPath)
                .collect { progress ->
                    emit(progress.copy(stage = ExtractionStage.EXTRACTING))
                }

            emit(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = contents.fileCount,
                totalFiles = contents.fileCount,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.FINALIZING
            ))

            // Move original archive to trash
            fileSystemRepository.moveToTrash(archivePath)

            // Get list of extracted files for result
            val extractedFiles = when (strategy) {
                ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY -> {
                    listOf(fileSystemRepository.joinPath(
                        fileSystemRepository.getParentDirectory(archivePath),
                        contents.topLevelEntries.first().name
                    ))
                }
                ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER -> {
                    contents.topLevelEntries.map { entry ->
                        fileSystemRepository.joinPath(extractionPath, entry.name)
                    }
                }
                ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY -> {
                    contents.entries.filter { it.depth == 1 }.map { entry ->
                        fileSystemRepository.joinPath(
                            fileSystemRepository.getParentDirectory(archivePath),
                            entry.name
                        )
                    }
                }
            }

            // Show success notification
            notificationRepository.showSuccessNotification(
                title = "Extraction Complete",
                message = "${archive.name} extracted successfully",
                extractedPath = extractionPath
            )

            emit(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = contents.fileCount,
                totalFiles = contents.fileCount,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.COMPLETED
            ))

        } catch (error: ExtractionError) {
            // Show error notification
            notificationRepository.showErrorNotification(
                title = "Extraction Failed",
                message = error.message
            )

            emit(ExtractionProgress(
                archivePath = archivePath,
                stage = ExtractionStage.FAILED
            ))

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

            emit(ExtractionProgress(
                archivePath = archivePath,
                stage = ExtractionStage.FAILED
            ))

            throw error
        }
    }

    private fun determineExtractionStrategy(contents: ArchiveContents): ExtractionStrategy {
        return when {
            // Single file archive -> extract to same directory
            contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isFile -> {
                ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY
            }
            // Single directory with everything inside -> extract contents to same directory
            contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isDirectory -> {
                ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY
            }
            // Multiple items at root level -> create folder
            else -> {
                ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER
            }
        }
    }

    private suspend fun determineExtractionPath(
        archive: Archive,
        contents: ArchiveContents,
        strategy: ExtractionStrategy
    ): String {
        val parentDir = fileSystemRepository.getParentDirectory(archive.path)

        return when (strategy) {
            ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY,
            ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY -> parentDir

            ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER -> {
                val folderName = archive.nameWithoutExtension
                var extractionPath = fileSystemRepository.joinPath(parentDir, folderName)

                // Handle name conflicts by adding number suffix
                var counter = 1
                while (fileSystemRepository.exists(extractionPath)) {
                    extractionPath = fileSystemRepository.joinPath(parentDir, "$folderName ($counter)")
                    counter++
                }

                extractionPath
            }
        }
    }
}