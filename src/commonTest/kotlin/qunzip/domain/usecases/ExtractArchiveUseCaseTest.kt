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

        val options = ExtractionOptions(autoCloseAfterExtraction = false)
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

        val options = ExtractionOptions(autoCloseAfterExtraction = false)
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

        val options = ExtractionOptions(autoCloseAfterExtraction = true)
        useCase(archivePath, options).toList()

        assertFalse(mockNotificationRepository.successNotificationShown)
    }

    // ========== Error Tests ==========

    @Test
    fun `throws error when archive not found and emits FAILED progress before throwing`() = runTest {
        mockArchiveRepository.archiveInfo = null

        // Collect into a list rather than letting toList() rethrow on the
        // last-emit boundary; we want the partial sequence including the
        // FAILED emission that the use case's catch arm produces before
        // re-throwing.
        val collected = mutableListOf<ExtractionProgress>()
        val thrown: Throwable? = try {
            useCase("/test/nonexistent.zip").collect { collected += it }
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(thrown is ExtractionError.FileNotFound)
        assertTrue(mockNotificationRepository.errorNotificationShown)
        // The fix in commit 08ed177 / the auto-exit observer relies on a
        // FAILED stage being emitted on error paths — pin it here.
        assertEquals(ExtractionStage.FAILED, collected.last().stage)
    }

    @Test
    fun `multi-file extraction failure mid-stream cleans up the created directory`() = runTest {
        // The catch arms in ExtractArchiveUseCase have a "delete createdDir on
        // failure" branch that nothing was exercising. Force a multi-file
        // extraction to fail mid-stream and verify the destination dir gets
        // cleaned up so the user isn't left with an empty / partial folder.
        val archivePath = "/test/multi.zip"
        val archive = Archive(archivePath, "multi.zip", ArchiveFormat.ZIP, 4096L)
        val contents = ArchiveContents(
            entries = listOf(
                ArchiveEntry("a.txt", "a.txt", false, 1024L),
                ArchiveEntry("b.txt", "b.txt", false, 1024L)
            ),
            totalSize = 2048L
        )
        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"
        mockArchiveRepository.throwInExtractFlow = RuntimeException("unplugged the disk")

        try {
            useCase(archivePath).collect {}
        } catch (_: Throwable) {
            // Expected — we forced it.
        }

        assertTrue(mockFileSystemRepository.createDirectoryCalled, "destination dir should have been created")
        assertTrue(
            mockFileSystemRepository.deleteDirectoryCalled,
            "createdDir cleanup branch in the catch arm must delete the empty dest"
        )
    }

    @Test
    fun `compound tar with empty outer archive falls back to original path`() = runTest {
        // tarName == null branch in the compound-tar handling: outer .tar.gz
        // has no entries (degenerate case), so the use case proceeds with
        // the original path rather than dereferencing a null intermediate
        // tar name.
        val archivePath = "/test/empty.tar.gz"
        val archive = Archive(archivePath, "empty.tar.gz", ArchiveFormat.TAR_GZ, 64L)
        val emptyOuter = ArchiveContents(entries = emptyList(), totalSize = 0L)

        mockArchiveRepository.archiveInfoMap[archivePath] = archive
        mockArchiveRepository.archiveContentsMap[archivePath] = emptyOuter
        mockFileSystemRepository.parentDirectory = "/test"

        // Should not throw NullPointerException; should not attempt to
        // delete a non-existent intermediate tar.
        val progressList = useCase(archivePath).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertFalse(
            mockFileSystemRepository.deletedFiles.any { it.contains(".tar") },
            "no intermediate tar was created — nothing to delete"
        )
    }

    @Test
    fun `wraps non-ExtractionError throwables as UnknownError and emits FAILED`() = runTest {
        // The catch (throwable: Throwable) arm in the use case must
        // convert random failures into ExtractionError.UnknownError so
        // callers can pattern-match a single error hierarchy.
        val archivePath = "/test/boom.zip"
        val archive = Archive(archivePath, "boom.zip", ArchiveFormat.ZIP, 1024L)
        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.throwOnGetContents = IllegalStateException("repository exploded")

        val collected = mutableListOf<ExtractionProgress>()
        val thrown: Throwable? = try {
            useCase(archivePath).collect { collected += it }
            null
        } catch (e: Throwable) {
            e
        }

        assertTrue(thrown is ExtractionError.UnknownError, "expected UnknownError, got $thrown")
        // UnknownError wraps the original message with a "Unknown error: " prefix.
        assertEquals("Unknown error: repository exploded", thrown.message)
        assertTrue(mockNotificationRepository.errorNotificationShown)
        assertEquals(ExtractionStage.FAILED, collected.last().stage)
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

    // ========== Password Tests ==========

    @Test
    fun `extraction with password passes password to repository`() = runTest {
        val archivePath = "/test/encrypted.zip"
        val archive = Archive(archivePath, "encrypted.zip", ArchiveFormat.ZIP, 1024L)
        val contents = ArchiveContents(
            entries = listOf(ArchiveEntry("secret.txt", "secret.txt", false, 1024L)),
            totalSize = 1024L
        )

        mockArchiveRepository.archiveInfo = archive
        mockArchiveRepository.archiveContents = contents
        mockFileSystemRepository.parentDirectory = "/test"

        val options = ExtractionOptions(password = "mypassword")
        val progressList = useCase(archivePath, options).toList()

        assertEquals(ExtractionStage.COMPLETED, progressList.last().stage)
        assertEquals("mypassword", mockArchiveRepository.lastPassword)
    }

    @Test
    fun `extraction without password passes null to repository`() = runTest {
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
        assertNull(mockArchiveRepository.lastPassword)
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
        var lastPassword: String? = null

        // Optional fault injection — letting tests force the use case's
        // catch arms (especially the generic Throwable arm).
        var throwOnGetContents: Throwable? = null
        var throwInExtractFlow: Throwable? = null

        override suspend fun getArchiveInfo(archivePath: String) =
            archiveInfoMap[archivePath] ?: archiveInfo
        override suspend fun getArchiveContents(archivePath: String, password: String?): ArchiveContents {
            throwOnGetContents?.let { throw it }
            return archiveContentsMap[archivePath] ?: archiveContents
        }
        override suspend fun testArchive(archivePath: String, password: String?) = true

        override suspend fun extractArchive(archivePath: String, destinationPath: String, password: String?): Flow<ExtractionProgress> {
            extractCalled = true
            extractionPath = destinationPath
            extractCallCount++
            lastExtractionPath = destinationPath
            lastPassword = password
            val contents = archiveContentsMap[archivePath] ?: archiveContents
            val toThrow = throwInExtractFlow
            return if (toThrow != null) {
                kotlinx.coroutines.flow.flow {
                    emit(ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING))
                    throw toThrow
                }
            } else {
                flowOf(
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
        }

        override fun isFormatSupported(format: ArchiveFormat) = true
        override fun getSupportedFormats() = ArchiveFormat.entries
        override suspend fun isPasswordRequired(archivePath: String) = false
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
