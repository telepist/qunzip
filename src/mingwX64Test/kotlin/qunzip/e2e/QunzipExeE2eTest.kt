package qunzip.e2e

import qunzip.*
import kotlin.test.*

/**
 * End-to-end tests that launch the compiled qunzip.exe as an external process
 * and verify its behavior (exit codes, output, timeout behavior).
 */
class QunzipExeE2eTest {

    private val exe = getExecutablePath()
    private lateinit var tempDir: String

    @BeforeTest
    fun setup() {
        assertTrue(fileExistsAt(exe), "qunzip.exe not found at $exe — build it first")
        tempDir = createTestTempDir("e2e")
    }

    @AfterTest
    fun cleanup() {
        deleteRecursive(tempDir)
    }

    // --- CLI argument tests ---

    @Test
    fun `--help exits with code 0 and prints usage`() {
        val result = executeProcess("\"$exe\" --help", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode, "Expected exit code 0")
        assertTrue(result.stdout.contains("Usage", ignoreCase = true),
            "Expected usage text in stdout")
    }

    @Test
    fun `--version exits with code 0`() {
        val result = executeProcess("\"$exe\" --version", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode, "Expected exit code 0")
        assertTrue(result.stdout.contains("Quick Unzip", ignoreCase = true),
            "Expected version text in stdout")
    }

    @Test
    fun `-h is an alias for --help`() {
        val result = executeProcess("\"$exe\" -h", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Usage", ignoreCase = true))
    }

    @Test
    fun `-v is an alias for --version`() {
        val result = executeProcess("\"$exe\" -v", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("Quick Unzip", ignoreCase = true))
    }

    // --- Extraction exit tests ---

    @Test
    fun `CLI extraction of single-file zip exits within timeout`() {
        val archivePath = "$tempDir\\single-file.zip"
        copyFile(getFixturePath("single-file.zip"), archivePath)

        val result = executeProcess(
            "\"$exe\" \"$archivePath\"",
            timeoutMillis = 15_000u
        )

        assertFalse(result.timedOut,
            "Process did not exit within 15s — this is the exit hang bug")
        assertEquals(0, result.exitCode, "Expected exit code 0")
    }

    @Test
    fun `CLI extraction of multiple-files zip exits within timeout`() {
        val archivePath = "$tempDir\\multiple-files.zip"
        copyFile(getFixturePath("multiple-files.zip"), archivePath)

        val result = executeProcess(
            "\"$exe\" \"$archivePath\"",
            timeoutMillis = 15_000u
        )

        assertFalse(result.timedOut, "Process did not exit within 15s")
        assertEquals(0, result.exitCode, "Expected exit code 0")
    }

    @Test
    fun `forced standalone extraction exits within timeout`() {
        val archivePath = "$tempDir\\single-file-standalone.zip"
        copyFile(getFixturePath("single-file.zip"), archivePath)

        val result = executeProcess(
            "\"$exe\" --force-standalone \"$archivePath\"",
            timeoutMillis = 15_000u
        )

        assertFalse(result.timedOut,
            "Standalone process did not exit within 15s — this is the standalone exit hang bug")
        assertEquals(0, result.exitCode, "Expected exit code 0")
    }

    // --- Non-ASCII path tests (UTF-16 argv + UTF-8 code page fix) ---

    // The archive name/dir are created via wide-API subprocesses (cmd) because
    // the test process itself runs on the legacy ANSI code page; the narrow
    // copyFile/fileExistsAt helpers would mangle non-ASCII paths.

    @Test
    fun `extracts an archive whose name is non-ASCII`() {
        val archivePath = "$tempDir\\Pojat telttaretkellä.zip"
        assertEquals(0, cmd("copy /y \"${getFixturePath("multiple-files.zip")}\" \"$archivePath\"").exitCode)

        val result = executeProcess("\"$exe\" \"$archivePath\"", timeoutMillis = 15_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertFalse(result.stdout.contains("not found", ignoreCase = true),
            "Archive with a non-ASCII name was not found — argv/code-page regression")
        // Extraction actually produced the files (folder name is non-ASCII, but
        // the entries are ASCII so we can find them in a recursive listing).
        assertTrue(listDirRecursive(tempDir).contains("file1.txt"),
            "Expected extracted files under a non-ASCII folder")
    }

    @Test
    fun `extracts an archive inside a non-ASCII directory`() {
        val dir = "$tempDir\\Näyte ählä"
        assertEquals(0, cmd("mkdir \"$dir\"").exitCode)
        val archivePath = "$dir\\archive.zip"
        assertEquals(0, cmd("copy /y \"${getFixturePath("multiple-files.zip")}\" \"$archivePath\"").exitCode)

        val result = executeProcess("\"$exe\" \"$archivePath\"", timeoutMillis = 15_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertFalse(result.stdout.contains("not found", ignoreCase = true))
        assertTrue(listDirRecursive(dir).contains("file1.txt"))
    }

    // --- Path-with-spaces test (argument quoting fix) ---

    @Test
    fun `extracts an archive in a path containing spaces`() {
        val dir = "$tempDir\\a folder with spaces"
        assertEquals(0, cmd("mkdir \"$dir\"").exitCode)
        val archivePath = "$dir\\multiple-files.zip"
        assertTrue(copyFile(getFixturePath("multiple-files.zip"), archivePath))

        val result = executeProcess("\"$exe\" \"$archivePath\"", timeoutMillis = 15_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertFalse(result.stdout.contains("not found", ignoreCase = true))
        assertTrue(listDirRecursive(dir).contains("file1.txt"))
    }

    // --- Error-handling tests (must fail gracefully, never crash or hang) ---

    @Test
    fun `corrupt archive fails gracefully without crashing or hanging`() {
        val archivePath = "$tempDir\\corrupt.zip"
        assertTrue(copyFile(getFixturePath("corrupt.zip"), archivePath))

        val result = executeProcess("\"$exe\" \"$archivePath\"", timeoutMillis = 15_000u)

        // Graceful failure: no hang, and a nonzero exit so callers can detect it
        // (a crash would also be nonzero, but the no-timeout + clean shutdown
        // distinguish it; the unit/integration tests cover the FAILED-state path).
        assertFalse(result.timedOut, "Process hung on a corrupt archive")
        assertNotEquals(0, result.exitCode, "Corrupt archive should exit nonzero")
    }

    @Test
    fun `nonexistent archive fails gracefully`() {
        val archivePath = "$tempDir\\does-not-exist.zip"

        val result = executeProcess("\"$exe\" \"$archivePath\"", timeoutMillis = 10_000u)

        // Graceful: no hang, and a nonzero exit for the failure. (The error text is
        // shown interactively but the non-interactive renderer may exit before
        // flushing it, so we don't assert on stdout here.)
        assertFalse(result.timedOut, "Process hung on a missing archive")
        assertNotEquals(0, result.exitCode)
    }

    // --- Settings CLI tests ---

    @Test
    fun `--set-trash-on exits with code 0`() {
        val result = executeProcess("\"$exe\" --set-trash-on", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("trash", ignoreCase = true))
    }

    @Test
    fun `--set-trash-off exits with code 0`() {
        val result = executeProcess("\"$exe\" --set-trash-off", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `--set-dialog-on exits with code 0`() {
        val result = executeProcess("\"$exe\" --set-dialog-on", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
    }

    @Test
    fun `--set-dialog-off exits with code 0`() {
        val result = executeProcess("\"$exe\" --set-dialog-off", timeoutMillis = 10_000u)

        assertFalse(result.timedOut, "Process timed out")
        assertEquals(0, result.exitCode)
    }
}
