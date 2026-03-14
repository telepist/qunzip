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
        assertTrue(result.stdout.contains("Qunzip", ignoreCase = true),
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
        assertTrue(result.stdout.contains("Qunzip", ignoreCase = true))
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
