package gunzip.presentation.viewmodels

import gunzip.domain.entities.*
import gunzip.domain.repositories.PreferencesRepository
import gunzip.domain.usecases.*
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
    }

    @Test
    fun `extraction with invalid archive fails`() = testScope.runTest {
        // Arrange
        val archivePath = "/test/corrupted.zip"
        val error = ExtractionError.CorruptedArchive()

        mockValidateUseCase.result = ValidationResult.Invalid(error)

        // Act & Assert
        viewModel.uiState.test {
            awaitItem() // Initial state

            viewModel.extractArchive(archivePath)

            awaitItem() // Loading state

            val errorState = awaitItem()
            assertFalse(errorState.isLoading)
            assertEquals(error.message, errorState.error)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cancellation stops extraction`() = testScope.runTest {
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

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertFalse(state.isExtracting)

            cancelAndIgnoreRemainingEvents()
        }
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

    // Mock implementations
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private class MockExtractArchiveUseCase(
        archiveRepository: gunzip.domain.repositories.ArchiveRepository? = null,
        fileSystemRepository: gunzip.domain.repositories.FileSystemRepository? = null,
        notificationRepository: gunzip.domain.repositories.NotificationRepository? = null
    ) : ExtractArchiveUseCase(
        archiveRepository = archiveRepository ?: object : gunzip.domain.repositories.ArchiveRepository {
            override suspend fun getArchiveInfo(archivePath: String) = null
            override suspend fun getArchiveContents(archivePath: String) = ArchiveContents(emptyList(), 0L)
            override suspend fun extractArchive(archivePath: String, destinationPath: String) = flowOf<ExtractionProgress>()
            override suspend fun testArchive(archivePath: String) = true
            override fun isFormatSupported(format: ArchiveFormat) = true
            override fun getSupportedFormats() = ArchiveFormat.values().toList()
            override suspend fun isPasswordRequired(archivePath: String) = false
            override suspend fun extractPasswordProtectedArchive(archivePath: String, destinationPath: String, password: String) = flowOf<ExtractionProgress>()
        },
        fileSystemRepository = fileSystemRepository ?: object : gunzip.domain.repositories.FileSystemRepository {
            override suspend fun exists(path: String) = true
            override suspend fun isReadable(path: String) = true
            override suspend fun isWritable(path: String) = true
            override suspend fun getFileInfo(path: String) = FileInfo(path, 1024L)
            override fun getParentDirectory(path: String) = ""
            override fun joinPath(vararg parts: String) = parts.joinToString("/")
            override suspend fun createDirectory(path: String) = true
            override suspend fun moveToTrash(path: String) = true
            override suspend fun getAvailableSpace(path: String) = 1024L * 1024L * 1024L
            override suspend fun getTrashPath() = "/trash"
            override suspend fun listFiles(directoryPath: String) = emptyList<FileInfo>()
            override suspend fun copyFile(sourcePath: String, destinationPath: String) = true
            override suspend fun moveFile(sourcePath: String, destinationPath: String) = true
            override suspend fun deleteFile(path: String) = true
            override suspend fun getFileSize(path: String) = 1024L
            override fun normalizePath(path: String) = path
            override fun getAbsolutePath(path: String) = path
            override fun isAbsolutePath(path: String) = true
            override fun getFileExtension(path: String) = "zip"
            override fun getFilenameWithoutExtension(path: String) = "file"
            override fun getFilename(path: String) = "file.zip"
        },
        notificationRepository = notificationRepository ?: object : gunzip.domain.repositories.NotificationRepository {
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

        override suspend operator fun invoke(archivePath: String, options: ExtractionOptions) = progressFlow
    }

    private class MockValidateArchiveUseCase(
        archiveRepository: gunzip.domain.repositories.ArchiveRepository? = null,
        fileSystemRepository: gunzip.domain.repositories.FileSystemRepository? = null
    ) : ValidateArchiveUseCase(
        archiveRepository = archiveRepository ?: object : gunzip.domain.repositories.ArchiveRepository {
            override suspend fun getArchiveInfo(archivePath: String) = null
            override suspend fun getArchiveContents(archivePath: String) = ArchiveContents(emptyList(), 0L)
            override suspend fun extractArchive(archivePath: String, destinationPath: String) = flowOf<ExtractionProgress>()
            override suspend fun testArchive(archivePath: String) = true
            override fun isFormatSupported(format: ArchiveFormat) = true
            override fun getSupportedFormats() = ArchiveFormat.values().toList()
            override suspend fun isPasswordRequired(archivePath: String) = false
            override suspend fun extractPasswordProtectedArchive(archivePath: String, destinationPath: String, password: String) = flowOf<ExtractionProgress>()
        },
        fileSystemRepository = fileSystemRepository ?: object : gunzip.domain.repositories.FileSystemRepository {
            override suspend fun exists(path: String) = true
            override suspend fun isReadable(path: String) = true
            override suspend fun isWritable(path: String) = true
            override suspend fun getFileInfo(path: String) = FileInfo(path, 1024L)
            override fun getParentDirectory(path: String) = ""
            override fun joinPath(vararg parts: String) = parts.joinToString("/")
            override suspend fun createDirectory(path: String) = true
            override suspend fun moveToTrash(path: String) = true
            override suspend fun getAvailableSpace(path: String) = 1024L * 1024L * 1024L
            override suspend fun getTrashPath() = "/trash"
            override suspend fun listFiles(directoryPath: String) = emptyList<FileInfo>()
            override suspend fun copyFile(sourcePath: String, destinationPath: String) = true
            override suspend fun moveFile(sourcePath: String, destinationPath: String) = true
            override suspend fun deleteFile(path: String) = true
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