package qunzip.presentation.viewmodels

import qunzip.domain.entities.*
import qunzip.domain.repositories.*
import qunzip.domain.usecases.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import app.cash.turbine.test
import kotlin.test.*

class ApplicationViewModelTest {

    private lateinit var testScope: TestScope
    private lateinit var vmScope: CoroutineScope
    private lateinit var viewModel: ApplicationViewModel

    @BeforeTest
    fun setup() {
        testScope = TestScope()
        // Separate scope for the ViewModel's long-running init collectors
        vmScope = CoroutineScope(testScope.coroutineContext + SupervisorJob())
        viewModel = ApplicationViewModel(
            extractArchiveUseCase = MockExtractArchiveUseCase(),
            validateArchiveUseCase = MockValidateArchiveUseCase(),
            manageFileAssociationsUseCase = MockManageFileAssociationsUseCase(),
            preferencesRepository = MockPreferencesRepository(),
            scope = vmScope
        )
    }

    @AfterTest
    fun teardown() {
        vmScope.cancel()
    }

    @Test
    fun `initial mode is SETUP`() {
        assertEquals(ApplicationMode.SETUP, viewModel.uiState.value.mode)
    }

    @Test
    fun `handleApplicationStart with no args enters SETUP mode`() = testScope.runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.handleApplicationStart(emptyList())

            val starting = awaitItem()
            assertTrue(starting.isStarting)

            val settled = awaitItem()
            assertFalse(settled.isStarting)
            assertEquals(ApplicationMode.SETUP, settled.mode)
            assertNull(settled.targetFile)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handleApplicationStart with file arg enters EXTRACTION mode`() = testScope.runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.handleApplicationStart(listOf("/test/archive.zip"))

            val starting = awaitItem()
            assertTrue(starting.isStarting)

            val settled = awaitItem()
            assertFalse(settled.isStarting)
            assertEquals(ApplicationMode.EXTRACTION, settled.mode)
            assertEquals("/test/archive.zip", settled.targetFile)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `handleApplicationExit sets shouldExit`() = testScope.runTest {
        viewModel.handleApplicationExit()

        assertTrue(viewModel.uiState.value.shouldExit)
    }

    @Test
    fun `handleApplicationExit emits ApplicationExit event`() = testScope.runTest {
        viewModel.events.test {
            viewModel.handleApplicationExit()

            val event = awaitItem()
            assertTrue(event is ApplicationEvent.ApplicationExit)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // --- Mocks ---

    private class MockExtractArchiveUseCase : ExtractArchiveUseCase(
        archiveRepository = object : ArchiveRepository {
            override suspend fun getArchiveInfo(archivePath: String) = null
            override suspend fun getArchiveContents(archivePath: String, password: String?) = ArchiveContents(emptyList(), 0L)
            override suspend fun extractArchive(archivePath: String, destinationPath: String, password: String?) = flowOf<ExtractionProgress>()
            override suspend fun testArchive(archivePath: String, password: String?) = true
            override fun isFormatSupported(format: ArchiveFormat) = true
            override fun getSupportedFormats() = ArchiveFormat.values().toList()
            override suspend fun isPasswordRequired(archivePath: String) = false
        },
        fileSystemRepository = object : FileSystemRepository {
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
            override suspend fun deleteDirectory(path: String) = true
            override suspend fun getFileSize(path: String) = 1024L
            override fun normalizePath(path: String) = path
            override fun getAbsolutePath(path: String) = path
            override fun isAbsolutePath(path: String) = true
            override fun getFileExtension(path: String) = "zip"
            override fun getFilenameWithoutExtension(path: String) = "file"
            override fun getFilename(path: String) = "file.zip"
        },
        notificationRepository = object : NotificationRepository {
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
        override suspend operator fun invoke(archivePath: String, options: ExtractionOptions) = flowOf<ExtractionProgress>()
    }

    private class MockValidateArchiveUseCase : ValidateArchiveUseCase(
        archiveRepository = object : ArchiveRepository {
            override suspend fun getArchiveInfo(archivePath: String) = null
            override suspend fun getArchiveContents(archivePath: String, password: String?) = ArchiveContents(emptyList(), 0L)
            override suspend fun extractArchive(archivePath: String, destinationPath: String, password: String?) = flowOf<ExtractionProgress>()
            override suspend fun testArchive(archivePath: String, password: String?) = true
            override fun isFormatSupported(format: ArchiveFormat) = true
            override fun getSupportedFormats() = ArchiveFormat.values().toList()
            override suspend fun isPasswordRequired(archivePath: String) = false
        },
        fileSystemRepository = object : FileSystemRepository {
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
        override suspend operator fun invoke(archivePath: String) =
            ValidationResult.Valid(Archive("/test/default.zip", "default.zip", ArchiveFormat.ZIP, 1024L))
    }

    private class MockManageFileAssociationsUseCase : ManageFileAssociationsUseCase(
        fileAssociationRepository = object : FileAssociationRepository {
            override suspend fun registerAssociation(extension: String, applicationPath: String, applicationName: String, description: String) =
                AssociationResult(true, extension, "registered")
            override suspend fun unregisterAssociation(extension: String) =
                AssociationResult(true, extension, "unregistered")
            override suspend fun getAssociation(extension: String): FileAssociation? = null
            override suspend fun isAssociatedWithApplication(extension: String, applicationPath: String) = false
            override suspend fun getAllAssociations() = emptyList<FileAssociation>()
            override fun supportsFileAssociations() = true
            override suspend fun registerMultipleAssociations(extensions: List<String>, applicationPath: String, applicationName: String, description: String) =
                extensions.map { AssociationResult(true, it, "registered") }
            override suspend fun requestElevatedPrivileges() = true
        }
    )

    private class MockPreferencesRepository : PreferencesRepository {
        var preferences: UserPreferences = UserPreferences.DEFAULT

        override suspend fun loadPreferences() = preferences
        override suspend fun savePreferences(preferences: UserPreferences): Boolean {
            this.preferences = preferences
            return true
        }
        override fun getPreferencesPath() = "/mock/settings.json"
        override suspend fun preferencesExist() = true
        override suspend fun resetToDefaults(): Boolean {
            preferences = UserPreferences.DEFAULT
            return true
        }
    }
}
