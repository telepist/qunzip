package qunzip.integration

import qunzip.*
import qunzip.platform.WindowsArchiveRepository
import qunzip.platform.WindowsFileSystemRepository
import qunzip.domain.entities.*
import qunzip.domain.repositories.NotificationRepository
import qunzip.domain.usecases.ExtractArchiveUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import platform.posix.access
import platform.posix.F_OK
import kotlin.test.*

/**
 * Integration tests that verify real archive extraction using 7zip and the Windows filesystem.
 * These tests use actual ZIP files and the real WindowsArchiveRepository.
 */
class ArchiveExtractionIntegrationTest {

    private lateinit var archiveRepository: WindowsArchiveRepository
    private lateinit var tempDir: String

    @BeforeTest
    fun setup() {
        archiveRepository = WindowsArchiveRepository(sevenZipPath = get7zipPath())
        tempDir = createTestTempDir("integration")
    }

    @AfterTest
    fun cleanup() {
        deleteRecursive(tempDir)
    }

    // --- getArchiveInfo ---

    @Test
    fun `getArchiveInfo returns correct info for single-file zip`() = runTest {
        val fixturePath = getFixturePath("single-file.zip")
        val info = archiveRepository.getArchiveInfo(fixturePath)

        assertNotNull(info)
        assertEquals("single-file.zip", info.name)
        assertEquals(ArchiveFormat.ZIP, info.format)
        assertTrue(info.size > 0)
    }

    @Test
    fun `getArchiveInfo returns correct info for multiple-files zip`() = runTest {
        val fixturePath = getFixturePath("multiple-files.zip")
        val info = archiveRepository.getArchiveInfo(fixturePath)

        assertNotNull(info)
        assertEquals("multiple-files.zip", info.name)
        assertEquals(ArchiveFormat.ZIP, info.format)
    }

    @Test
    fun `getArchiveInfo returns null for nonexistent file`() = runTest {
        val info = archiveRepository.getArchiveInfo("$tempDir\\nonexistent.zip")
        assertNull(info)
    }

    // --- getArchiveContents ---

    @Test
    fun `getArchiveContents lists files in single-file zip`() = runTest {
        val fixturePath = getFixturePath("single-file.zip")
        val contents = archiveRepository.getArchiveContents(fixturePath)

        assertTrue(contents.fileCount > 0, "Expected at least one file entry")
        assertFalse(contents.hasMultipleRootItems, "Single file should not have multiple root items")
    }

    @Test
    fun `getArchiveContents lists files in multiple-files zip`() = runTest {
        val fixturePath = getFixturePath("multiple-files.zip")
        val contents = archiveRepository.getArchiveContents(fixturePath)

        assertTrue(contents.fileCount >= 3, "Expected at least 3 file entries, got ${contents.fileCount}")
        assertTrue(contents.hasMultipleRootItems, "Multiple files should have multiple root items")
    }

    @Test
    fun `getArchiveContents detects nested folder structure`() = runTest {
        val fixturePath = getFixturePath("nested-folder.zip")
        val contents = archiveRepository.getArchiveContents(fixturePath)

        assertTrue(contents.entries.isNotEmpty(), "Expected entries in nested-folder.zip")
        // All entries should share a common root prefix
        val paths = contents.entries.map { it.path }
        assertTrue(paths.all { it.startsWith("nested/") || it.startsWith("nested\\") },
            "Expected all entries under 'nested/', got: $paths")
    }

    // --- testArchive ---

    @Test
    fun `testArchive returns true for valid zip`() = runTest {
        val fixturePath = getFixturePath("single-file.zip")
        assertTrue(archiveRepository.testArchive(fixturePath))
    }

    @Test
    fun `testArchive returns false for nonexistent file`() = runTest {
        assertFalse(archiveRepository.testArchive("$tempDir\\nonexistent.zip"))
    }

    // --- extractArchive ---

    @Test
    fun `extractArchive extracts single-file zip to destination`() = runTest {
        val fixturePath = getFixturePath("single-file.zip")
        val destDir = "$tempDir\\single-extract"

        val progressUpdates = archiveRepository.extractArchive(fixturePath, destDir).toList()

        // Verify progress stages
        assertTrue(progressUpdates.isNotEmpty(), "Expected progress updates")
        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage, got: $stages")

        // Verify extracted file exists
        assertTrue(fileExistsAt(destDir), "Destination directory should exist")
    }

    @Test
    fun `extractArchive extracts multiple-files zip to destination`() = runTest {
        val fixturePath = getFixturePath("multiple-files.zip")
        val destDir = "$tempDir\\multi-extract"

        val progressUpdates = archiveRepository.extractArchive(fixturePath, destDir).toList()

        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage")
        assertTrue(fileExistsAt(destDir), "Destination directory should exist")
    }

    @Test
    fun `extractArchive extracts nested-folder zip to destination`() = runTest {
        val fixturePath = getFixturePath("nested-folder.zip")
        val destDir = "$tempDir\\nested-extract"

        val progressUpdates = archiveRepository.extractArchive(fixturePath, destDir).toList()

        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage")
        assertTrue(fileExistsAt(destDir), "Destination directory should exist")
    }
}
