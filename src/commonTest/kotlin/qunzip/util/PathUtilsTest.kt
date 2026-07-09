package qunzip.util

import kotlin.test.*

class PathUtilsTest {

    @Test
    fun `joins components with backslashes`() {
        assertEquals("C:\\a\\b\\7z.exe", joinWindowsPath("C:\\a", "b", "7z.exe"))
    }

    @Test
    fun `trims stray separators between components`() {
        assertEquals("C:\\dir\\bin\\7zip\\7z.exe", joinWindowsPath("C:\\dir\\", "\\bin\\", "7zip", "7z.exe"))
    }

    @Test
    fun `preserves the first component's leading root but trims its trailing separator`() {
        assertEquals("C:\\a", joinWindowsPath("C:\\a\\", "", ""))
    }

    @Test
    fun `handles relative parent segments`() {
        assertEquals("dir\\..\\..\\bin", joinWindowsPath("dir", "..", "..", "bin"))
    }
}
