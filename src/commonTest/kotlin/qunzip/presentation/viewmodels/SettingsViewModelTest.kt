@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package qunzip.presentation.viewmodels

import qunzip.domain.entities.UserPreferences
import qunzip.domain.repositories.CliShimRepository
import qunzip.domain.repositories.PreferencesRepository
import qunzip.domain.repositories.ShimResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.*
import app.cash.turbine.test
import kotlin.test.*

class SettingsViewModelTest {

    private lateinit var testScope: TestScope
    private lateinit var vmScope: CoroutineScope
    private lateinit var prefs: MockPreferencesRepository
    private lateinit var shim: MockCliShimRepository

    @BeforeTest
    fun setup() {
        testScope = TestScope()
        vmScope = CoroutineScope(testScope.coroutineContext + SupervisorJob())
        prefs = MockPreferencesRepository()
        shim = MockCliShimRepository()
    }

    @AfterTest
    fun teardown() {
        vmScope.cancel()
    }

    private fun build(): SettingsViewModel = SettingsViewModel(
        preferencesRepository = prefs,
        scope = vmScope,
        cliShimRepository = shim
    )

    // --- Preference toggles ---

    @Test
    fun `loadPreferences populates preferences and path`() = testScope.runTest {
        prefs.preferences = UserPreferences(moveToTrashAfterExtraction = true, autoCloseAfterExtraction = false)
        val vm = build()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.preferences.moveToTrashAfterExtraction)
        assertFalse(state.preferences.autoCloseAfterExtraction)
        assertEquals("/mock/settings.json", state.preferencesPath)
    }

    @Test
    fun `setMoveToTrashAfterExtraction persists and updates state`() = testScope.runTest {
        val vm = build()
        advanceUntilIdle()

        vm.setMoveToTrashAfterExtraction(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.preferences.moveToTrashAfterExtraction)
        assertTrue(prefs.preferences.moveToTrashAfterExtraction, "preference must be persisted")
    }

    @Test
    fun `failed save reverts the optimistic state and surfaces error`() = testScope.runTest {
        val vm = build()
        advanceUntilIdle()
        prefs.saveSuccess = false

        vm.setAutoCloseAfterExtraction(false)
        advanceUntilIdle()

        // Reverted to default true
        assertTrue(vm.uiState.value.preferences.autoCloseAfterExtraction)
        assertEquals("Failed to save preferences", vm.uiState.value.error)
    }

    // --- CLI shim toggle ---

    @Test
    fun `cliShimSupported reflects the repository capability`() = testScope.runTest {
        shim.supported = false
        val vm = build()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.cliShimSupported)
    }

    @Test
    fun `refreshCliShimState reads installed status from the repo`() = testScope.runTest {
        shim.installed = true
        val vm = build()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cliShimInstalled)
    }

    @Test
    fun `setCliShimInstalled true on success updates state and emits event`() = testScope.runTest {
        val vm = build()
        advanceUntilIdle()
        shim.installResult = ShimResult(success = true, message = "Added to PATH")

        vm.events.test {
            vm.setCliShimInstalled(true)
            advanceUntilIdle()
            val event = awaitItem()
            assertTrue(event is SettingsEvent.CliShimChanged && event.installed)
            cancelAndIgnoreRemainingEvents()
        }

        val state = vm.uiState.value
        assertTrue(state.cliShimInstalled)
        assertFalse(state.isCliShimWorking)
        assertEquals("Added to PATH", state.cliShimMessage)
        assertNull(state.error)
    }

    @Test
    fun `setCliShimInstalled true on failure reverts state and surfaces error`() = testScope.runTest {
        val vm = build()
        advanceUntilIdle()
        // Repo currently reports not installed; failure should keep it that way.
        shim.installed = false
        shim.installResult = ShimResult(success = false, message = "Failed to write user PATH")

        vm.setCliShimInstalled(true)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.cliShimInstalled, "failed install must revert to previous (not installed)")
        assertFalse(state.isCliShimWorking)
        assertEquals("Failed to write user PATH", state.error)
        assertEquals("Failed to write user PATH", state.cliShimMessage)
    }

    @Test
    fun `setCliShimInstalled is no-op when repository is unsupported`() = testScope.runTest {
        shim.supported = false
        val vm = build()
        advanceUntilIdle()

        vm.setCliShimInstalled(true)
        advanceUntilIdle()

        // Without isSupported the call must not touch the repository or state.
        assertEquals(0, shim.installCallCount, "install must not be called when unsupported")
        assertFalse(vm.uiState.value.cliShimInstalled)
        assertFalse(vm.uiState.value.isCliShimWorking)
    }

    @Test
    fun `setCliShimInstalled false routes through uninstall`() = testScope.runTest {
        shim.installed = true
        val vm = build()
        advanceUntilIdle()

        vm.setCliShimInstalled(false)
        advanceUntilIdle()

        assertEquals(1, shim.uninstallCallCount)
        assertEquals(0, shim.installCallCount)
        assertFalse(vm.uiState.value.cliShimInstalled)
    }

    // --- Mocks ---

    private class MockPreferencesRepository : PreferencesRepository {
        var preferences: UserPreferences = UserPreferences.DEFAULT
        var saveSuccess: Boolean = true
        override suspend fun loadPreferences() = preferences
        override suspend fun savePreferences(preferences: UserPreferences): Boolean {
            if (saveSuccess) this.preferences = preferences
            return saveSuccess
        }
        override fun getPreferencesPath() = "/mock/settings.json"
        override suspend fun preferencesExist() = true
        override suspend fun resetToDefaults(): Boolean {
            preferences = UserPreferences.DEFAULT
            return true
        }
    }

    private class MockCliShimRepository : CliShimRepository {
        var supported: Boolean = true
        var installed: Boolean = false
        var installResult: ShimResult = ShimResult(success = true, message = "Added to PATH")
        var uninstallResult: ShimResult = ShimResult(success = true, message = "Removed from PATH")
        var installCallCount: Int = 0
        var uninstallCallCount: Int = 0

        override val isSupported: Boolean get() = supported
        override suspend fun isInstalled(): Boolean = installed
        override suspend fun install(): ShimResult {
            installCallCount++
            installed = installResult.success
            return installResult
        }
        override suspend fun uninstall(): ShimResult {
            uninstallCallCount++
            if (uninstallResult.success) installed = false
            return uninstallResult
        }
    }
}
