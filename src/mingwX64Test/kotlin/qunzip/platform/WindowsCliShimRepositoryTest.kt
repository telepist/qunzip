package qunzip.platform

import kotlin.test.*

/**
 * Pure-string-helper tests for WindowsCliShimRepository. The registry-IO
 * side (readUserPath / writeUserPath / broadcastEnvironmentChange) is not
 * covered here — that's a structural gap until RegistryHelper grows a
 * test seam. These tests cover the parsing/normalization logic that makes
 * the install/uninstall idempotent and quote-tolerant.
 */
class WindowsCliShimRepositoryTest {

    // --- normalizedDir ---

    @Test
    fun `normalizedDir trims whitespace`() {
        assertEquals("C:\\Foo", "  C:\\Foo  ".normalizedDir())
    }

    @Test
    fun `normalizedDir strips surrounding double quotes`() {
        // Many installers / users add quotes around paths with spaces in PATH.
        // We must compare against the unquoted form to avoid duplicate entries.
        assertEquals("C:\\Some Dir", "\"C:\\Some Dir\"".normalizedDir())
    }

    @Test
    fun `normalizedDir trims trailing backslash and forward slash`() {
        assertEquals("C:\\Foo", "C:\\Foo\\".normalizedDir())
        assertEquals("C:\\Foo", "C:\\Foo/".normalizedDir())
    }

    @Test
    fun `normalizedDir converts forward slashes to backslashes`() {
        assertEquals("C:\\Users\\teemu\\bin", "C:/Users/teemu/bin".normalizedDir())
    }

    @Test
    fun `normalizedDir composes all transforms`() {
        assertEquals("C:\\Some Dir", "  \"C:/Some Dir/\"  ".normalizedDir())
    }

    // --- containsDir ---

    @Test
    fun `containsDir returns true for an exact case-insensitive match`() {
        val path = "C:\\foo;C:\\Users\\teemu\\AppData\\Local\\Programs\\Quick Unzip;C:\\bar"
        val install = "C:\\Users\\teemu\\AppData\\Local\\Programs\\Quick Unzip"
        assertTrue(path.containsDir(install))
        // Case folding (Windows paths are case-insensitive in practice).
        assertTrue(path.lowercase().containsDir(install))
    }

    @Test
    fun `containsDir is false when the install dir is absent`() {
        val path = "C:\\foo;C:\\bar"
        assertFalse(path.containsDir("C:\\Users\\teemu\\bin"))
    }

    @Test
    fun `containsDir does not prefix-match a longer entry`() {
        // C:\Foo must not match C:\Foo\Bar — the shim install dir is exact.
        val path = "C:\\Foo\\Bar"
        assertFalse(path.containsDir("C:\\Foo"))
    }

    @Test
    fun `containsDir matches a quoted PATH entry against the unquoted dir`() {
        // The point of stripping quotes in normalizedDir: a user with a
        // quoted entry should NOT see a duplicate entry on re-install.
        val path = "C:\\foo;\"C:\\Users\\teemu\\bin\";C:\\bar"
        assertTrue(path.containsDir("C:\\Users\\teemu\\bin"))
    }

    @Test
    fun `containsDir matches a trailing-slash entry against the no-slash dir`() {
        val path = "C:\\foo;C:\\Users\\teemu\\bin\\;C:\\bar"
        assertTrue(path.containsDir("C:\\Users\\teemu\\bin"))
    }

    @Test
    fun `containsDir tolerates mixed separators on the PATH side`() {
        val path = "C:\\foo;C:/Users/teemu/bin;C:\\bar"
        assertTrue(path.containsDir("C:\\Users\\teemu\\bin"))
    }

    @Test
    fun `containsDir matches the install dir even with trailing or double semicolons in PATH`() {
        val path = "C:\\foo;;C:\\Users\\teemu\\bin;"
        assertTrue(path.containsDir("C:\\Users\\teemu\\bin"))
    }

    @Test
    fun `containsDir matches empty entries against an empty dir — caller must guard`() {
        // Empty PATH segments (`;;` or trailing `;`) do match the empty
        // string. This is intentional; callers must refuse empty install
        // dirs *before* calling containsDir — install() does, see the guard
        // at the top of WindowsCliShimRepository.install. If that guard
        // ever regressed, this test pins the underlying behaviour.
        val path = "C:\\foo;;C:\\bar"
        assertTrue(path.containsDir(""))
    }
}
