@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package qunzip.presentation.viewmodels

import qunzip.domain.entities.*
import qunzip.domain.repositories.PreferencesRepository
import qunzip.domain.usecases.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import app.cash.turbine.test
import kotlin.test.*

class ExtractionViewModelTest {

    private lateinit var mockExtractUseCase: MockExtractArchiveUseCase
    private lateinit var mockValidateUseCase: MockValidateArchiveUseCase
    private lateinit var mockPreferencesRepository: MockPreferencesRepository
    private lateinit var testScope: TestScope
    private lateinit var viewModel: ExtractionViewModel

    @BeforeTest
    fun setup() {
        mockExtractUseCase = MockExtractArchiveUseCase()
        mockValidateUseCase = MockValidateArchiveUseCase()
        mockPreferencesRepository = MockPreferencesRepository()
        testScope = TestScope()

        viewModel = ExtractionViewModel(
            extractArchiveUseCase = mockExtractUseCase,
            validateArchiveUseCase = mockValidateUseCase,
            preferencesRepository = mockPreferencesRepository,
            scope = testScope
        )
    }

    @Test
    fun `initial state is correct`() = testScope.runTest {
        viewModel.uiState.test {
            val initialState = awaitItem()

            assertFalse(initialState.isLoading)
            assertFalse(initialState.isExtracting)
            assertNull(initialState.currentArchive)
            assertNull(initialState.archive)
            assertNull(initialState.progress)
            assertNull(initialState.error)
        }
    }

    @Test
    fun `extraction with valid archive succeeds`() = testScope.runTest {
        // Arrange
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.STARTING),
            ExtractionProgress(archivePath, stage = ExtractionStage.ANALYZING),
            ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING),
            ExtractionProgress(archivePath, stage = ExtractionStage.COMPLETED)
        )

        // Act
        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Assert
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(archive, finalState.archive)
        // The final stage must be COMPLETED — this is what drives the
        // ApplicationViewModel auto-exit observer. Without this assertion a
        // regression dropping the COMPLETED emission would silently pass.
        assertEquals(ExtractionStage.COMPLETED, finalState.progress?.stage)
    }

    @Test
    fun `extraction with invalid archive fails`() = testScope.runTest {
        // Arrange
        val archivePath = "/test/corrupted.zip"
        val error = ExtractionError.CorruptedArchive()

        mockValidateUseCase.result = ValidationResult.Invalid(error)

        // Act
        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Assert: error surfaced AND progress.stage = FAILED so the
        // auto-exit observer in ApplicationViewModel actually fires.
        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertEquals(error.message, finalState.error)
        assertEquals(ExtractionStage.FAILED, finalState.progress?.stage)
    }

    @Test
    fun `unexpected exception during extraction sets FAILED stage`() = testScope.runTest {
        // The catch-Exception arm in extractArchive must mark progress as
        // FAILED, otherwise the GUI dialog hangs after a generic mid-stream
        // failure (e.g. file system error).
        val archivePath = "/test/broken.zip"
        val archive = Archive(archivePath, "broken.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.throwOnInvoke = RuntimeException("disk full")

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertFalse(finalState.isExtracting)
        assertEquals("disk full", finalState.error)
        assertEquals(ExtractionStage.FAILED, finalState.progress?.stage)
    }

    @Test
    fun `retry after FAILED clears stale progress before re-running`() = testScope.runTest {
        // First attempt fails with FAILED stage. The ApplicationViewModel
        // auto-exit observer would otherwise immediately fire on retry from
        // the leftover FAILED, before the new extraction has a chance to
        // emit STARTING. extractArchive() must reset progress = null first.
        val archivePath = "/test/retry.zip"
        val archive = Archive(archivePath, "retry.zip", ArchiveFormat.ZIP, 1024L)

        // First attempt: validation fails (sets progress = FAILED)
        mockValidateUseCase.result = ValidationResult.Invalid(ExtractionError.CorruptedArchive())
        viewModel.extractArchive(archivePath)
        advanceUntilIdle()
        assertEquals(ExtractionStage.FAILED, viewModel.uiState.value.progress?.stage)

        // Retry: validation now succeeds. The validate use case is suspending
        // (it's a `suspend operator fun` per the mock), so the very first
        // _uiState.update {} at the top of extractArchive() runs eagerly and
        // is observable before any subsequent progress is emitted. We use
        // Turbine to capture that intermediate state and verify that
        // progress was reset to null.
        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.STARTING),
            ExtractionProgress(archivePath, stage = ExtractionStage.COMPLETED)
        )
        viewModel.uiState.test {
            // Skip the FAILED state from the first attempt.
            awaitItem()
            viewModel.extractArchive(archivePath)
            // First emission after the call: leading update {} reset progress.
            val resetState = awaitItem()
            assertNull(resetState.progress)
            assertTrue(resetState.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancellation stops extraction and marks FAILED`() = testScope.runTest {
        // Arrange
        val archivePath = "/test/large.zip"
        val archive = Archive(archivePath, "large.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING)
            // Never completes
        )

        // Act
        viewModel.extractArchive(archivePath)
        advanceUntilIdle()
        viewModel.cancelExtraction()
        advanceUntilIdle()

        // Assert: cancel marks FAILED so the auto-exit observer fires.
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isExtracting)
        assertEquals(ExtractionStage.FAILED, state.progress?.stage)
        assertEquals(archivePath, state.progress?.archivePath)
    }

    @Test
    fun `clearError removes error from state`() = testScope.runTest {
        // Arrange - set an error
        val archivePath = "/test/invalid.zip"
        mockValidateUseCase.result = ValidationResult.Invalid(ExtractionError.FileNotFound(archivePath))

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Act
        viewModel.clearError()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertNull(state.error)
        }
    }

    @Test
    fun `reset clears all state`() = testScope.runTest {
        // Arrange - set some state
        val archivePath = "/test/document.zip"
        val archive = Archive(archivePath, "document.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING)
        )

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Act
        viewModel.reset()

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isExtracting)
            assertNull(state.currentArchive)
            assertNull(state.archive)
            assertNull(state.progress)
            assertNull(state.error)
        }
    }

    @Test
    fun `progress percentage calculation works correctly`() = testScope.runTest {
        val progress = ExtractionProgress(
            archivePath = "/test/file.zip",
            bytesProcessed = 512L,
            totalBytes = 1024L,
            stage = ExtractionStage.EXTRACTING
        )

        val state = ExtractionUiState(progress = progress, isExtracting = true)
        assertEquals(50f, state.progressPercentage)
        assertTrue(state.showProgress)
    }

    @Test
    fun `password required sets waiting state`() = testScope.runTest {
        val archivePath = "/test/encrypted.zip"
        val archive = Archive(archivePath, "encrypted.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.PasswordRequired(archive)

        viewModel.uiState.test {
            awaitItem() // Initial state

            viewModel.extractArchive(archivePath)

            awaitItem() // Loading state

            val waitingState = awaitItem()
            assertTrue(waitingState.isWaitingForPassword)
            assertFalse(waitingState.isLoading)
            assertEquals(archive, waitingState.archive)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `submit password retries extraction with password`() = testScope.runTest {
        val archivePath = "/test/encrypted.zip"
        val archive = Archive(archivePath, "encrypted.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.PasswordRequired(archive)

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Now submit password - switch validation to Valid for the retry
        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.STARTING),
            ExtractionProgress(archivePath, stage = ExtractionStage.COMPLETED)
        )

        viewModel.submitPassword("secret123")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isWaitingForPassword)
        assertFalse(finalState.isLoading)
    }

    @Test
    fun `submit wrong password shows error and waits again`() = testScope.runTest {
        val archivePath = "/test/encrypted.zip"
        val archive = Archive(archivePath, "encrypted.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.PasswordRequired(archive)

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        // Submit wrong password - extraction fails with PasswordRequired
        mockExtractUseCase.progressFlow = flowOf(
            ExtractionProgress(archivePath, stage = ExtractionStage.STARTING)
        )
        mockExtractUseCase.throwOnInvoke = ExtractionError.PasswordRequired("Wrong password")

        viewModel.submitPassword("wrongpassword")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isWaitingForPassword)
        assertEquals("Wrong password", state.error)
    }

    @Test
    fun `extraction error thrown mid-flow is handled gracefully as FAILED`() = testScope.runTest {
        // Regression: the extraction repository is a channelFlow that rethrows an
        // ExtractionError (e.g. SevenZipError) after emitting FAILED. That rethrow
        // must be handled in-flow, not escape and crash the process. Verify the
        // ViewModel surfaces it as an error + FAILED progress (which keeps the
        // standalone window open) rather than letting it propagate.
        val archivePath = "/test/broken.zip"
        val archive = Archive(archivePath, "broken.zip", ArchiveFormat.ZIP, 1024L)
        mockValidateUseCase.result = ValidationResult.Valid(archive)
        mockExtractUseCase.progressFlow = flow {
            emit(ExtractionProgress(archivePath, stage = ExtractionStage.EXTRACTING))
            throw ExtractionError.SevenZipError(2, "7zip extraction failed")
        }

        // Must not throw out of the launch / crash the test.
        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ExtractionStage.FAILED, state.progress?.stage)
        assertFalse(state.isWaitingForPassword)
        assertNotNull(state.error)
    }

    @Test
    fun `cancel password clears waiting state and sets failed`() = testScope.runTest {
        val archivePath = "/test/encrypted.zip"
        val archive = Archive(archivePath, "encrypted.zip", ArchiveFormat.ZIP, 1024L)

        mockValidateUseCase.result = ValidationResult.PasswordRequired(archive)

        viewModel.extractArchive(archivePath)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isWaitingForPassword)

        viewModel.cancelExtraction()

        val state = viewModel.uiState.value
        assertFalse(state.isWaitingForPassword)
        assertFalse(state.isLoading)
        assertEquals(ExtractionStage.FAILED, state.progress?.stage)
    }

    // Mock implementations
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class MockExtractArchiveUseCase(
        archiveRepository: qunzip.domain.repositories.ArchiveRepository? = null,
        fileSystemRepository: qunzip.domain.repositories.FileSystemRepository? = null,
        notificationRepository: qunzip.domain.repositories.NotificationRepository? = null
    ) : ExtractArchiveUseCase(
        archiveRepository = archiveRepository ?: object : qunzip.domain.repositories.ArchiveRepository {
            override suspend fun getArchiveInfo(archivePath: String) = null
            override suspend fun getArchiveContents(archivePath: String, password: String?) = ArchiveContents(emptyList(), 0L)
            override suspend fun extractArchive(archivePath: String, destinationPath: String, password: String?) = flowOf<ExtractionProgress>()
            override suspend fun testArchive(archivePath: String, password: String?) = true
            override fun isFormatSupported(format: ArchiveFormat) = true
            override fun getSupportedFormats() = ArchiveFormat.entries
            override suspend fun isPasswordRequired(archivePath: String) = false
        },
        fileSystemRepository = fileSystemRepository ?: object : qunzip.domain.repositories.FileSystemRepository {
            override suspend fun exists(path: String) = true
            override suspend fun isReadable(path: String) = true
            override suspend fun isWritable(path: String) = true
            override suspend fun getFileInfo(path: String) = FileInfo(path, 1024L)
            override fun getParentDirectory(filePath: String) = ""
            override fun joinPath(vararg components: String) = components.joinToString("/")
            override suspend fun createDirectory(path: String) = true
            override suspend fun moveToTrash(filePath: String) = true
            override suspend fun getAvailableSpace(path: String) = 1024L * 1024L * 1024L
            override suspend fun getTrashPath() = "/trash"
            override suspend fun listFiles(directoryPath: String) = emptyList<FileInfo>()
            override suspend fun copyFile(sourcePath: String, destinationPath: String) = true
            override suspend fun moveFile(sourcePath: String, destinationPath: String) = true
            override suspend fun deleteFile(path: String) = true
            override suspend fun deleteDirectory(path: String) = true
            override suspend fun getFileSize(path: String) = 1024L
            override fun normalizePath(path: String) = path
            override fun getAbsolutePath(path: String) = path
            override fun isAbsolutePath(path: String) = true
            override fun getFileExtension(path: String) = "zip"
            override fun getFilenameWithoutExtension(path: String) = "file"
            override fun getFilename(path: String) = "file.zip"
        },
        notificationRepository = notificationRepository ?: object : qunzip.domain.repositories.NotificationRepository {
            override suspend fun showSuccessNotification(title: String, message: String, extractedPath: String?) {}
            override suspend fun showErrorNotification(title: String, message: String, details: String?) {}
            override suspend fun showProgressNotification(id: String, title: String, message: String, progress: Float, cancellable: Boolean) {}
            override suspend fun updateProgressNotification(id: String, message: String, progress: Float) {}
            override suspend fun cancelProgressNotification(id: String) {}
            override suspend fun showInfoNotification(title: String, message: String) {}
            override fun areNotificationsSupported() = true
            override suspend fun requestNotificationPermission() = true
            override suspend fun showNotificationWithAction(title: String, message: String, actionLabel: String, actionPath: String) {}
        }
    ) {
        var progressFlow = flowOf<ExtractionProgress>()
        var throwOnInvoke: Throwable? = null
        var lastOptions: ExtractionOptions? = null

        override suspend operator fun invoke(archivePath: String, options: ExtractionOptions): kotlinx.coroutines.flow.Flow<ExtractionProgress> {
            lastOptions = options
            val error = throwOnInvoke
            if (error != null) {
                throwOnInvoke = null // Reset after throwing
                throw error
            }
            return progressFlow
        }
    }

    private class MockValidateArchiveUseCase(
        fileSystemRepository: qunzip.domain.repositories.FileSystemRepository? = null
    ) : ValidateArchiveUseCase(
        fileSystemRepository = fileSystemRepository ?: object : qunzip.domain.repositories.FileSystemRepository {
            override suspend fun exists(path: String) = true
            override suspend fun isReadable(path: String) = true
            override suspend fun isWritable(path: String) = true
            override suspend fun getFileInfo(path: String) = FileInfo(path, 1024L)
            override fun getParentDirectory(filePath: String) = ""
            override fun joinPath(vararg components: String) = components.joinToString("/")
            override suspend fun createDirectory(path: String) = true
            override suspend fun moveToTrash(filePath: String) = true
            override suspend fun getAvailableSpace(path: String) = 1024L * 1024L * 1024L
            override suspend fun getTrashPath() = "/trash"
            override suspend fun listFiles(directoryPath: String) = emptyList<FileInfo>()
            override suspend fun copyFile(sourcePath: String, destinationPath: String) = true
            override suspend fun moveFile(sourcePath: String, destinationPath: String) = true
            override suspend fun deleteFile(path: String) = true
            override suspend fun deleteDirectory(path: String) = true
            override suspend fun getFileSize(path: String) = 1024L
            override fun normalizePath(path: String) = path
            override fun getAbsolutePath(path: String) = path
            override fun isAbsolutePath(path: String) = true
            override fun getFileExtension(path: String) = "zip"
            override fun getFilenameWithoutExtension(path: String) = "file"
            override fun getFilename(path: String) = "file.zip"
        }
    ) {
        var result: ValidationResult = ValidationResult.Valid(
            Archive("/test/default.zip", "default.zip", ArchiveFormat.ZIP, 1024L)
        )

        override suspend operator fun invoke(archivePath: String) = result
    }

    private class MockPreferencesRepository : PreferencesRepository {
        var preferences: UserPreferences = UserPreferences.DEFAULT
        var saveSuccess: Boolean = true

        override suspend fun loadPreferences() = preferences
        override suspend fun savePreferences(preferences: UserPreferences): Boolean {
            if (saveSuccess) {
                this.preferences = preferences
            }
            return saveSuccess
        }
        override fun getPreferencesPath() = "/mock/settings.json"
        override suspend fun preferencesExist() = true
        override suspend fun resetToDefaults(): Boolean {
            preferences = UserPreferences.DEFAULT
            return true
        }
    }
}
