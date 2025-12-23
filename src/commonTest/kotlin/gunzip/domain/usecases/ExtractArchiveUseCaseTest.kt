package gunzip.domain.usecases

import gunzip.domain.entities.*
import gunzip.domain.repositories.ArchiveRepository
import gunzip.domain.repositories.FileSystemRepository
import gunzip.domain.repositories.NotificationRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlin.test.*

class ExtractArchiveUseCaseTest {

    private lateinit var mockArchiveRepository: MockArchiveRepository
    private lateinit var mockFileSystemRepository: MockFileSystemRepository
    private lateinit var mockNotificationRepository: MockNotificationRepository
    private lateinit var useCase: ExtractArchiveUseCase

    @BeforeTest
    fun setup() {
        mockArchiveRepository = MockArchiveRepository()
        mockFileSystemRepository = MockFileSystemRepository()
        mockNotificationRepository = MockNotificationRepository()

        useCase = ExtractArchiveUseCase(
            archiveRepository = mockArchiveRepository,
            fileSystemRepository = mockFileSystemRepository,
            notificationRepository = mockNotificationRepository
        )
    }

    @Test
    fun `single file archive extraction follows correct strategy`() = runTest {
        // Arrange
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("document.pdf", "document.pdf", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 2048L
        mockFileSystemRepository.parentDirectory = "/test"

        // Act
        val progressList = useCase(archivePath).toList()

        // Assert
        assertTrue(progressList.first().stage == ExtractionStage.STARTING)
        assertTrue(progressList.any { it.stage == ExtractionStage.ANALYZING })
        assertTrue(progressList.any { it.stage == ExtractionStage.EXTRACTING })
        assertTrue(progressList.last().stage == ExtractionStage.COMPLETED)

        assertTrue(mockArchiveRepository.extractCalled)
        assertEquals("/test", mockArchiveRepository.extractionPath)
        assertFalse(mockFileSystemRepository.moveToTrashCalled) // Default: don't move to trash
        assertTrue(mockNotificationRepository.successNotificationShown)
    }

    @Test
    fun `moves archive to trash when option enabled`() = runTest {
        // Arrange
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("document.pdf", "document.pdf", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 2048L
        mockFileSystemRepository.parentDirectory = "/test"

        val options = ExtractionOptions(moveToTrashAfterExtraction = true)

        // Act
        val progressList = useCase(archivePath, options).toList()

        // Assert
        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertTrue(mockFileSystemRepository.moveToTrashCalled)
    }

    @Test
    fun `skips notification when showCompletionNotification is disabled`() = runTest {
        // Arrange
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("document.pdf", "document.pdf", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 2048L
        mockFileSystemRepository.parentDirectory = "/test"

        val options = ExtractionOptions(showCompletionNotification = false)

        // Act
        val progressList = useCase(archivePath, options).toList()

        // Assert
        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertFalse(mockNotificationRepository.successNotificationShown)
    }

    @Test
    fun `multiple files archive creates directory`() = runTest {
        // Arrange
        val archivePath = "/test/project.zip"
        val archive = Archive(archivePath, "project.zip", ArchiveFormat.ZIP, 2048L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("README.md", "README.md", false, 512L),
                ArchiveEntry("src/main.kt", "main.kt", false, 1024L),
                ArchiveEntry("build.gradle", "build.gradle", false, 512L)
            ),
            totalSize = 2048L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 4096L
        mockFileSystemRepository.parentDirectory = "/test"

        // Act
        val progressList = useCase(archivePath).toList()

        // Assert
        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertTrue(mockFileSystemRepository.createDirectoryCalled)
        assertEquals("/test/project", mockFileSystemRepository.createdDirectory)
        assertEquals("/test/project", mockArchiveRepository.extractionPath)
    }

    @Test
    fun `insufficient disk space throws error`() = runTest {
        // Arrange
        val archivePath = "/test/large.zip"
        val archive = Archive(archivePath, "large.zip", ArchiveFormat.ZIP, 2048L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("large.bin", "large.bin", false, 2048L)),
            totalSize = 2048L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 1024L // Less than required

        // Act & Assert
        assertFailsWith<ExtractionError.InsufficientSpace> {
            useCase(archivePath).toList()
        }

        assertTrue(mockNotificationRepository.errorNotificationShown)
        assertFalse(mockArchiveRepository.extractCalled)
    }

    @Test
    fun `archive not found throws error`() = runTest {
        // Arrange
        val archivePath = "/test/nonexistent.zip"
        mockArchiveRepository.archiveInfo = null

        // Act & Assert
        assertFailsWith<ExtractionError.FileNotFound> {
            useCase(archivePath).toList()
        }

        assertTrue(mockNotificationRepository.errorNotificationShown)
        assertFalse(mockArchiveRepository.extractCalled)
    }

    @Test
    fun `handles conflicting directory names`() = runTest {
        // Arrange
        val archivePath = "/test/project.zip"
        val archive = Archive(archivePath, "project.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("file1.txt", "file1.txt", false, 512L),
                ArchiveEntry("file2.txt", "file2.txt", false, 512L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 2048L
        mockFileSystemRepository.parentDirectory = "/test"

        // Simulate existing directory
        mockFileSystemRepository.existingPaths = setOf("/test/project")

        // Act
        val progressList = useCase(archivePath).toList()

        // Assert
        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/project (1)", mockFileSystemRepository.createdDirectory)
    }

    // Mock implementations
    private class MockArchiveRepository : ArchiveRepository {
        var archiveInfo: Archive? = null
        var archiveContents: ArchiveContents = ArchiveContents(emptyList(), 0L)
        var extractCalled = false
        var extractionPath: String? = null

        override suspend fun getArchiveInfo(archivePath: String) = archiveInfo

        override suspend fun getArchiveContents(archivePath: String) = archiveContents

        override suspend fun testArchive(archivePath: String) = true

        override suspend fun extractArchive(archivePath: String, destinationPath: String) = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING),
            ExtractionProgress(
                archivePath,
                filesProcessed = archiveContents.fileCount,
                totalFiles = archiveContents.fileCount,
                bytesProcessed = archiveContents.totalSize,
                totalBytes = archiveContents.totalSize,
                stage = ExtractionStage.EXTRACTING
            )
        ).also {
            extractCalled = true
            extractionPath = destinationPath
        }

        override fun isFormatSupported(format: ArchiveFormat) = true
        override fun getSupportedFormats() = ArchiveFormat.values().toList()
        override suspend fun isPasswordRequired(archivePath: String) = false
        override suspend fun extractPasswordProtectedArchive(archivePath: String, destinationPath: String, password: String) =
            flowOf(ExtractionProgress(archivePath))
    }

    private class MockFileSystemRepository : FileSystemRepository {
        var availableSpace: Long = Long.MAX_VALUE
        var parentDirectory: String = ""
        var existingPaths: Set<String> = emptySet()
        var createDirectoryCalled = false
        var createdDirectory: String? = null
        var moveToTrashCalled = false

        override suspend fun exists(path: String) = path in existingPaths
        override suspend fun isReadable(path: String) = true
        override suspend fun isWritable(path: String) = true
        override suspend fun getFileInfo(path: String) = FileInfo(path, 1024L)
        override fun getParentDirectory(filePath: String) = parentDirectory
        override fun joinPath(vararg components: String) = components.joinToString("/")

        override suspend fun createDirectory(path: String): Boolean {
            createDirectoryCalled = true
            createdDirectory = path
            return true
        }

        override suspend fun getAvailableSpace(path: String) = availableSpace

        override suspend fun moveToTrash(filePath: String): Boolean {
            moveToTrashCalled = true
            return true
        }

        override suspend fun getTrashPath() = "/trash"
        override suspend fun listFiles(directoryPath: String) = emptyList<FileInfo>()
        override suspend fun copyFile(sourcePath: String, destinationPath: String) = true
        override suspend fun moveFile(sourcePath: String, destinationPath: String) = true
        override suspend fun deleteFile(path: String) = true
        override suspend fun getFileSize(path: String) = 1024L
        override fun normalizePath(path: String) = path
        override fun getAbsolutePath(path: String) = path
        override fun isAbsolutePath(path: String) = path.startsWith("/")
        override fun getFileExtension(path: String) = path.substringAfterLast(".", "")
        override fun getFilenameWithoutExtension(path: String) = path.substringBeforeLast(".")
        override fun getFilename(path: String) = path.substringAfterLast("/")
    }

    private class MockNotificationRepository : NotificationRepository {
        var successNotificationShown = false
        var errorNotificationShown = false

        override suspend fun showSuccessNotification(title: String, message: String, extractedPath: String?) {
            successNotificationShown = true
        }

        override suspend fun showErrorNotification(title: String, message: String, details: String?) {
            errorNotificationShown = true
        }

        override suspend fun showProgressNotification(id: String, title: String, message: String, progress: Float, cancellable: Boolean) {}
        override suspend fun updateProgressNotification(id: String, message: String, progress: Float) {}
        override suspend fun cancelProgressNotification(id: String) {}
        override suspend fun showInfoNotification(title: String, message: String) {}
        override fun areNotificationsSupported() = true
        override suspend fun requestNotificationPermission() = true
        override suspend fun showNotificationWithAction(title: String, message: String, actionLabel: String, actionPath: String) {}
    }
}