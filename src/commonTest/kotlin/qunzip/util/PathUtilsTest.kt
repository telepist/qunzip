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

    // --- windowsParentDirectory ---

    @Test
    fun `parent of a normal path`() {
        assertEquals("C:\\Downloads", windowsParentDirectory("C:\\Downloads\\a.zip"))
        assertEquals("C:\\Downloads", windowsParentDirectory("C:/Downloads/a.zip"))
    }

    @Test
    fun `parent of a drive-root file keeps the trailing backslash`() {
        assertEquals("C:\\", windowsParentDirectory("C:\\a.zip"))
    }

    @Test
    fun `parent of a bare name is dot`() {
        assertEquals(".", windowsParentDirectory("a.zip"))
    }

    // --- filename / extension ---

    @Test
    fun `filename takes the last segment`() {
        assertEquals("a.zip", windowsFilename("C:\\d\\a.zip"))
        assertEquals("a.zip", windowsFilename("d/a.zip"))
    }

    @Test
    fun `extension helpers split on the last dot`() {
        assertEquals("zip", windowsFileExtension("C:\\d\\a.tar.zip"))
        assertEquals("a.tar", windowsFilenameWithoutExtension("C:\\d\\a.tar.zip"))
    }

    @Test
    fun `no extension when none present or leading-dot name`() {
        assertEquals("", windowsFileExtension("README"))
        assertEquals("README", windowsFilenameWithoutExtension("README"))
        assertEquals("", windowsFileExtension(".gitignore"))
        assertEquals(".gitignore", windowsFilenameWithoutExtension(".gitignore"))
    }
}
