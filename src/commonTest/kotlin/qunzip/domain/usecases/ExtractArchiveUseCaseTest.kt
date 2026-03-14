package qunzip.domain.usecases

import qunzip.domain.entities.*
import qunzip.domain.repositories.ArchiveRepository
import qunzip.domain.repositories.FileSystemRepository
import qunzip.domain.repositories.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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

    // ========== Single File Tests ==========

    @Test
    fun `single file extraction without conflict extracts to parent directory`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("document.pdf", "document.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test", mockArchiveRepository.extractionPath)
        assertFalse(mockFileSystemRepository.createDirectoryCalled)
        assertFalse(mockFileSystemRepository.moveFileCalled)
    }

    @Test
    fun `single file extraction with conflict uses temp folder and renames`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("document.pdf", "document.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/document.pdf")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        // Should create temp folder
        assertTrue(mockFileSystemRepository.createDirectoryCalled)
        assertTrue(mockFileSystemRepository.createdDirectory!!.startsWith("/test/qunzip_"))
        // Should move file to unique name
        assertTrue(mockFileSystemRepository.moveFileCalled)
        assertEquals("/test/document-1.pdf", mockFileSystemRepository.moveFileDestination)
        // Should delete temp folder
        assertTrue(mockFileSystemRepository.deleteDirectoryCalled)
    }

    @Test
    fun `single file extraction with multiple conflicts finds next available name`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("report.pdf", "report.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf(
            "/test/report.pdf",
            "/test/report-1.pdf",
            "/test/report-2.pdf"
        )

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/report-3.pdf", mockFileSystemRepository.moveFileDestination)
    }

    @Test
    fun `single file without extension handles conflict correctly`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("README", "README", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/README")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/README-1", mockFileSystemRepository.moveFileDestination)
    }

    @Test
    fun `single file conflict notification shows renamed path`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("data.csv", "data.csv", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/data.csv")

        val options = ExtractionOptions(showCompletionDialog = true)
        useCase(archivePath, options).toList()

        assertTrue(mockNotificationRepository.successNotificationShown)
        assertEquals("/test/data-1.csv", mockNotificationRepository.lastExtractedPath)
    }

    // ========== Single Folder Tests ==========

    @Test
    fun `single folder extraction without conflict extracts to parent directory`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("myproject", "myproject", true, 0L),
                ArchiveEntry("myproject/file.txt", "file.txt", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test", mockArchiveRepository.extractionPath)
        assertFalse(mockFileSystemRepository.moveFileCalled)
    }

    @Test
    fun `single folder extraction with conflict uses temp folder and renames`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("myproject", "myproject", true, 0L),
                ArchiveEntry("myproject/file.txt", "file.txt", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/myproject")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        // Should create temp folder
        assertTrue(mockFileSystemRepository.createDirectoryCalled)
        assertTrue(mockFileSystemRepository.createdDirectory!!.startsWith("/test/qunzip_"))
        // Should move folder to unique name
        assertTrue(mockFileSystemRepository.moveFileCalled)
        assertEquals("/test/myproject-1", mockFileSystemRepository.moveFileDestination)
        // Should delete temp folder
        assertTrue(mockFileSystemRepository.deleteDirectoryCalled)
    }

    @Test
    fun `single folder extraction with multiple conflicts finds next name`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("data", "data", true, 0L),
                ArchiveEntry("data/file.txt", "file.txt", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/data", "/test/data-1", "/test/data-2")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/data-3", mockFileSystemRepository.moveFileDestination)
    }

    @Test
    fun `single folder conflict notification shows renamed path`() = runTest {
        val archivePath = "/test/archive.zip"
        val archive = Archive(archivePath, "archive.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("project", "project", true, 0L),
                ArchiveEntry("project/main.kt", "main.kt", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/project")

        val options = ExtractionOptions(showCompletionDialog = true)
        useCase(archivePath, options).toList()

        assertTrue(mockNotificationRepository.successNotificationShown)
        assertEquals("/test/project-1", mockNotificationRepository.lastExtractedPath)
    }

    // ========== Multi-file Tests ==========

    @Test
    fun `multi-file archive creates folder without conflict`() = runTest {
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
        mockFileSystemRepository.parentDirectory = "/test"

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertTrue(mockFileSystemRepository.createDirectoryCalled)
        assertEquals("/test/project", mockFileSystemRepository.createdDirectory)
        assertEquals("/test/project", mockArchiveRepository.extractionPath)
    }

    @Test
    fun `multi-file archive creates unique folder on conflict`() = runTest {
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
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/project")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/project-1", mockFileSystemRepository.createdDirectory)
    }

    @Test
    fun `multi-file archive finds next available folder name`() = runTest {
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
        mockFileSystemRepository.parentDirectory = "/test"
        mockFileSystemRepository.existingPaths = setOf("/test/project", "/test/project-1", "/test/project-2")

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("/test/project-3", mockFileSystemRepository.createdDirectory)
    }

    // ========== Compound TAR Format Tests ==========

    @Test
    fun `tar_gz archive extracts both stages and deletes intermediate tar`() = runTest {
        val archivePath = "/test/archive.tar.gz"
        val archive = Archive(archivePath, "archive.tar.gz", ArchiveFormat.TAR_GZ, 2048L)

        // Outer archive (tar.gz) contains a single .tar entry
        val outerContents = ArchiveContents(
            entries = listOf(ArchiveEntry("archive.tar", "archive.tar", false, 4096L)),
            totalSize = 4096L
        )

        // Inner archive (.tar) contains the actual files
        val innerContents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("readme.txt", "readme.txt", false, 1024L),
                ArchiveEntry("data.csv", "data.csv", false, 2048L)
            ),
            totalSize = 3072L
        )

        mockArchiveRepository.archiveInfoMap[archivePath] = archive
        mockArchiveRepository.archiveContentsMap[archivePath] = outerContents
        mockArchiveRepository.archiveContentsMap["/test/archive.tar"] = innerContents
        // Inner tar is recognized as a TAR archive
        mockArchiveRepository.archiveInfoMap["/test/archive.tar"] = Archive(
            "/test/archive.tar", "archive.tar", ArchiveFormat.TAR, 4096L
        )
        mockFileSystemRepository.parentDirectory = "/test"

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        // Should have extracted twice: first the .tar.gz, then the .tar
        assertEquals(2, mockArchiveRepository.extractCallCount)
        // The final extraction should use the inner contents' strategy (multi-file -> folder)
        assertTrue(mockFileSystemRepository.createDirectoryCalled)
        assertEquals("/test/archive", mockFileSystemRepository.createdDirectory)
        // Should delete the intermediate .tar file
        assertTrue(mockFileSystemRepository.deletedFiles.contains("/test/archive.tar"))
    }

    @Test
    fun `tar_gz with single folder in tar extracts directly`() = runTest {
        val archivePath = "/test/project.tar.gz"
        val archive = Archive(archivePath, "project.tar.gz", ArchiveFormat.TAR_GZ, 2048L)

        val outerContents = ArchiveContents(
            entries = listOf(ArchiveEntry("project.tar", "project.tar", false, 4096L)),
            totalSize = 4096L
        )

        val innerContents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("myproject", "myproject", true, 0L),
                ArchiveEntry("myproject/main.kt", "main.kt", false, 1024L)
            ),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfoMap[archivePath] = archive
        mockArchiveRepository.archiveContentsMap[archivePath] = outerContents
        mockArchiveRepository.archiveContentsMap["/test/project.tar"] = innerContents
        mockArchiveRepository.archiveInfoMap["/test/project.tar"] = Archive(
            "/test/project.tar", "project.tar", ArchiveFormat.TAR, 4096L
        )
        mockFileSystemRepository.parentDirectory = "/test"

        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        // Single folder -> extract to parent dir
        assertEquals("/test", mockArchiveRepository.lastExtractionPath)
        // Intermediate .tar cleaned up
        assertTrue(mockFileSystemRepository.deletedFiles.contains("/test/project.tar"))
    }

    // ========== Options Tests ==========

    @Test
    fun `moves archive to trash when option enabled`() = runTest {
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("document.pdf", "document.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val options = ExtractionOptions(moveToTrashAfterExtraction = true)
        val progressList = useCase(archivePath, options).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertTrue(mockFileSystemRepository.moveToTrashCalled)
    }

    @Test
    fun `skips notification when disabled`() = runTest {
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("document.pdf", "document.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val options = ExtractionOptions(showCompletionDialog = false)
        useCase(archivePath, options).toList()

        assertFalse(mockNotificationRepository.successNotificationShown)
    }

    // ========== Error Tests ==========

    @Test
    fun `throws error when archive not found`() = runTest {
        mockArchiveRepository.archiveInfo = null

        assertFailsWith<ExtractionError.FileNotFound> {
            useCase("/test/nonexistent.zip").toList()
        }
        assertTrue(mockNotificationRepository.errorNotificationShown)
    }

    @Test
    fun `throws error when insufficient disk space`() = runTest {
        val archivePath = "/test/large.zip"
        val archive = Archive(archivePath, "large.zip", ArchiveFormat.ZIP, 2048L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("large.bin", "large.bin", false, 2048L)),
            totalSize = 2048L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.availableSpace = 1024L // Less than required

        assertFailsWith<ExtractionError.InsufficientSpace> {
            useCase(archivePath).toList()
        }
        assertTrue(mockNotificationRepository.errorNotificationShown)
    }

    // ========== Progress Tests ==========

    @Test
    fun `emits progress stages in correct order`() = runTest {
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("document.pdf", "document.pdf", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val stages = useCase(archivePath).toList().map { it.stage }

        assertTrue(stages.indexOf(ExtractionStage.STARTING) < stages.indexOf(ExtractionStage.ANALYZING))
        assertTrue(stages.indexOf(ExtractionStage.ANALYZING) < stages.indexOfFirst { it == ExtractionStage.EXTRACTING })
        assertTrue(stages.indexOfLast { it == ExtractionStage.EXTRACTING } < stages.indexOf(ExtractionStage.FINALIZING))
        assertTrue(stages.indexOf(ExtractionStage.FINALIZING) < stages.indexOf(ExtractionStage.COMPLETED))
    }

    // ========== Mock Implementations ==========

    private class MockArchiveRepository : ArchiveRepository {
        var archiveInfo: Archive? = null
        var archiveContents: ArchiveContents = ArchiveContents(emptyList(), 0L)
        var extractCalled = false
        var extractionPath: String? = null

        // Per-path overrides for compound format testing
        val archiveInfoMap = mutableMapOf<String, Archive>()
        val archiveContentsMap = mutableMapOf<String, ArchiveContents>()
        var extractCallCount = 0
        var lastExtractionPath: String? = null

        override suspend fun getArchiveInfo(archivePath: String) =
            archiveInfoMap[archivePath] ?: archiveInfo
        override suspend fun getArchiveContents(archivePath: String) =
            archiveContentsMap[archivePath] ?: archiveContents
        override suspend fun testArchive(archivePath: String) = true

        override suspend fun extractArchive(archivePath: String, destinationPath: String): Flow<ExtractionProgress> {
            extractCalled = true
            extractionPath = destinationPath
            extractCallCount++
            lastExtractionPath = destinationPath
            val contents = archiveContentsMap[archivePath] ?: archiveContents
            return flowOf(
                ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING),
                ExtractionProgress(
                    archivePath,
                    filesProcessed = contents.fileCount,
                    totalFiles = contents.fileCount,
                    bytesProcessed = contents.totalSize,
                    totalBytes = contents.totalSize,
                    stage = ExtractionStage.EXTRACTING
                )
            )
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
        var moveFileCalled = false
        var moveFileDestination: String? = null
        var deleteDirectoryCalled = false
        val deletedFiles = mutableListOf<String>()

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

        override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
            moveFileCalled = true
            moveFileDestination = destinationPath
            return true
        }

        override suspend fun deleteFile(path: String): Boolean {
            deletedFiles.add(path)
            return true
        }

        override suspend fun deleteDirectory(path: String): Boolean {
            deleteDirectoryCalled = true
            return true
        }

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
        var lastExtractedPath: String? = null

        override suspend fun showSuccessNotification(title: String, message: String, extractedPath: String?) {
            successNotificationShown = true
            lastExtractedPath = extractedPath
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
