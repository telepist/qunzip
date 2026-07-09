package qunzip.platform

import qunzip.data.process.ProcessRunner
import qunzip.domain.entities.ExtractionError
import qunzip.domain.entities.ExtractionStage
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Unit tests for the repository's orchestration, using a fake ProcessRunner —
 * no real 7z.exe, no spawned processes. Enabled by the ProcessRunner seam.
 */
class WindowsArchiveRepositoryTest {

    private data class Response(val exitCode: Int, val stdout: List<String>)

    private class FakeProcessRunner(private val responses: MutableList<Response>) : ProcessRunner {
        val calls = mutableListOf<List<String>>()
        override fun run(
            program: String,
            args: List<String>,
            shouldContinue: () -> Boolean,
            onStdoutLine: (String) -> Unit,
        ): Int {
            calls.add(args)
            val r = if (responses.size > 1) responses.removeAt(0) else responses.first()
            r.stdout.forEach(onStdoutLine)
            return r.exitCode
        }
    }

    private fun repo(vararg responses: Response) =
        WindowsArchiveRepository(
            sevenZipPath = "C:\\fake\\7z.exe",
            processRunner = FakeProcessRunner(responses.toMutableList()),
        )

    private val listOutput = listOf(
        "----------",
        "Path = folder", "Folder = +", "Size = 0",
        "Path = folder/doc.pdf", "Folder = -", "Size = 1024", "Packed Size = 512",
    )

    @Test
    fun `getArchiveContents parses runner output`() = runTest {
        val contents = repo(Response(0, listOutput)).getArchiveContents("a.zip")
        assertEquals(2, contents.entries.size)
        assertEquals(1024L, contents.totalSize)
        assertEquals("folder/doc.pdf", contents.entries[1].path)
    }

    @Test
    fun `getArchiveContents throws PasswordRequired on encrypted header`() = runTest {
        val r = repo(Response(2, listOf("Can not open encrypted archive. Wrong password?")))
        assertFailsWith<ExtractionError.PasswordRequired> { r.getArchiveContents("enc.zip") }
    }

    @Test
    fun `testArchive maps exit code to validity`() = runTest {
        assertTrue(repo(Response(0, emptyList())).testArchive("a.zip"))
        assertFalse(repo(Response(2, emptyList())).testArchive("a.zip"))
    }

    @Test
    fun `isPasswordRequired detects indicator in output`() = runTest {
        assertTrue(repo(Response(2, listOf("Enter password:"))).isPasswordRequired("a.zip"))
        assertFalse(repo(Response(0, listOf("Everything is Ok"))).isPasswordRequired("a.zip"))
    }

    @Test
    fun `extractArchive emits COMPLETED on success`() = runTest {
        // First run() = list (getArchiveContents), second = extraction.
        val r = repo(Response(0, listOutput), Response(0, listOf("Everything is Ok")))
        val stages = r.extractArchive("a.zip", "C:\\out").toList().map { it.stage }
        assertEquals(ExtractionStage.COMPLETED, stages.last())
    }

    @Test
    fun `extractArchive throws SevenZipError on nonzero exit`() = runTest {
        val r = repo(Response(0, listOutput), Response(2, listOf("ERROR: CRC failed")))
        assertFailsWith<ExtractionError.SevenZipError> {
            r.extractArchive("a.zip", "C:\\out").toList()
        }
    }

    @Test
    fun `extractArchive throws PasswordRequired when extraction reports wrong password`() = runTest {
        val r = repo(Response(0, listOutput), Response(2, listOf("Wrong password?")))
        assertFailsWith<ExtractionError.PasswordRequired> {
            r.extractArchive("a.zip", "C:\\out").toList()
        }
    }
}
