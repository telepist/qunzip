package qunzip.integration

import qunzip.*
import qunzip.platform.WindowsArchiveRepository
import qunzip.platform.WindowsFileSystemRepository
import qunzip.domain.entities.*
import qunzip.domain.repositories.NotificationRepository
import qunzip.domain.usecases.ExtractArchiveUseCase
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Integration tests for the full extraction pipeline using real repositories.
 * Tests ExtractArchiveUseCase with WindowsArchiveRepository and WindowsFileSystemRepository.
 */
class ExtractionPipelineIntegrationTest {

    private lateinit var extractArchiveUseCase: ExtractArchiveUseCase
    private lateinit var tempDir: String
    private val notifications = mutableListOf<String>()

    @BeforeTest
    fun setup() {
        val archiveRepository = WindowsArchiveRepository(sevenZipPath = get7zipPath())
        val fileSystemRepository = WindowsFileSystemRepository()
        val notificationRepository = TestNotificationRepository(notifications)

        extractArchiveUseCase = ExtractArchiveUseCase(
            archiveRepository = archiveRepository,
            fileSystemRepository = fileSystemRepository,
            notificationRepository = notificationRepository
        )

        tempDir = createTestTempDir("pipeline")
    }

    @AfterTest
    fun cleanup() {
        deleteRecursive(tempDir)
    }

    @Test
    fun `full pipeline extracts single-file zip successfully`() = runTest {
        val fixturePath = getFixturePath("single-file.zip")
        // Copy fixture to temp dir so extraction happens in our controlled area
        val archivePath = "$tempDir\\single-file.zip"
        copyFile(fixturePath, archivePath)

        val options = ExtractionOptions(
            moveToTrashAfterExtraction = false,
            autoCloseAfterExtraction = true
        )

        val progressUpdates = extractArchiveUseCase(archivePath, options).toList()

        // Verify pipeline completed
        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage, got: $stages")
    }

    @Test
    fun `full pipeline extracts multiple-files zip successfully`() = runTest {
        val fixturePath = getFixturePath("multiple-files.zip")
        val archivePath = "$tempDir\\multiple-files.zip"
        copyFile(fixturePath, archivePath)

        val options = ExtractionOptions(
            moveToTrashAfterExtraction = false,
            autoCloseAfterExtraction = true
        )

        val progressUpdates = extractArchiveUseCase(archivePath, options).toList()

        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage, got: $stages")
    }

    @Test
    fun `full pipeline extracts nested-folder zip successfully`() = runTest {
        val fixturePath = getFixturePath("nested-folder.zip")
        val archivePath = "$tempDir\\nested-folder.zip"
        copyFile(fixturePath, archivePath)

        val options = ExtractionOptions(
            moveToTrashAfterExtraction = false,
            autoCloseAfterExtraction = true
        )

        val progressUpdates = extractArchiveUseCase(archivePath, options).toList()

        val stages = progressUpdates.map { it.stage }
        assertTrue(stages.contains(ExtractionStage.COMPLETED), "Expected COMPLETED stage, got: $stages")
    }

    @Test
    fun `full pipeline fails for nonexistent archive`() = runTest {
        val archivePath = "$tempDir\\nonexistent.zip"

        val options = ExtractionOptions(
            moveToTrashAfterExtraction = false,
            autoCloseAfterExtraction = true
        )

        var caughtException: Throwable? = null
        try {
            extractArchiveUseCase(archivePath, options).toList()
            fail("Expected exception for nonexistent archive")
        } catch (e: ExtractionError.FileNotFound) {
            caughtException = e
        } catch (e: ExtractionError) {
            caughtException = e
        }
        assertNotNull(caughtException, "Expected an ExtractionError")
    }
}

/**
 * Simple notification repository for testing — records notification calls.
 */
private class TestNotificationRepository(
    private val log: MutableList<String>
) : NotificationRepository {
    override suspend fun showSuccessNotification(title: String, message: String, extractedPath: String?) {
        log += "success: $title - $message"
    }
    override suspend fun showErrorNotification(title: String, message: String, details: String?) {
        log += "error: $title - $message"
    }
    override suspend fun showProgressNotification(id: String, title: String, message: String, progress: Float, cancellable: Boolean) {
        log += "progress: $title - $message ($progress)"
    }
    override suspend fun updateProgressNotification(id: String, message: String, progress: Float) {
        log += "progress-update: $message ($progress)"
    }
    override suspend fun cancelProgressNotification(id: String) {
        log += "progress-cancel: $id"
    }
    override suspend fun showInfoNotification(title: String, message: String) {
        log += "info: $title - $message"
    }
    override fun areNotificationsSupported() = true
    override suspend fun requestNotificationPermission() = true
    override suspend fun showNotificationWithAction(title: String, message: String, actionLabel: String, actionPath: String) {
        log += "action: $title - $message"
    }
}
