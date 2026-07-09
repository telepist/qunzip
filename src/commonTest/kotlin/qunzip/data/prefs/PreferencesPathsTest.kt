package qunzip.data.prefs

import kotlin.test.*

class PreferencesPathsTest {

    @Test
    fun `uses APPDATA QuickUnzip when APPDATA is set`() {
        assertEquals(
            "C:\\Users\\me\\AppData\\Roaming\\QuickUnzip",
            PreferencesPaths.preferencesDirectory("C:\\Users\\me\\AppData\\Roaming", "C:\\exe")
        )
    }

    @Test
    fun `falls back to exe dir when APPDATA is null or blank`() {
        assertEquals("C:\\exe", PreferencesPaths.preferencesDirectory(null, "C:\\exe"))
        assertEquals("C:\\exe", PreferencesPaths.preferencesDirectory("   ", "C:\\exe"))
    }

    @Test
    fun `preferencesFile appends the settings file name`() {
        assertEquals("C:\\d\\settings.json", PreferencesPaths.preferencesFile("C:\\d"))
    }

    @Test
    fun `chooseLoadPath prefers the new per-user path`() {
        assertEquals(
            "new",
            PreferencesPaths.chooseLoadPath("new", "legacy", newExists = true, legacyExists = true)
        )
    }

    @Test
    fun `chooseLoadPath falls back to legacy for migration`() {
        assertEquals(
            "legacy",
            PreferencesPaths.chooseLoadPath("new", "legacy", newExists = false, legacyExists = true)
        )
    }

    @Test
    fun `chooseLoadPath returns null when neither exists`() {
        assertNull(PreferencesPaths.chooseLoadPath("new", "legacy", newExists = false, legacyExists = false))
    }
}
