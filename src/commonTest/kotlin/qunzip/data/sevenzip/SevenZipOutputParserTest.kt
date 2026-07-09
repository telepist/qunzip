package qunzip.data.sevenzip

import kotlin.test.*

class SevenZipOutputParserTest {

    // --- parseListOutput ---

    private val listHeader = """
        7-Zip 25.01 (x64)

        Listing archive: test.zip

        --
        Path = test.zip
        Type = zip

        ----------
    """.trimIndent()

    @Test
    fun `parses file and folder entries with sizes`() {
        val output = listHeader + "\n" + """
            Path = folder
            Folder = +
            Size = 0
            Packed Size = 0

            Path = folder/doc.pdf
            Folder = -
            Size = 1024
            Packed Size = 512
        """.trimIndent()

        val entries = SevenZipOutputParser.parseListOutput(output)

        assertEquals(2, entries.size)
        assertEquals("folder", entries[0].name)
        assertTrue(entries[0].isDirectory)
        assertEquals("folder/doc.pdf", entries[1].path)
        assertEquals(1024L, entries[1].size)
        assertEquals(512L, entries[1].compressedSize)
        assertFalse(entries[1].isDirectory)
    }

    @Test
    fun `normalizes backslashes to forward slashes and derives name`() {
        val output = listHeader + "\n" + """
            Path = Pojat telttaretkellä\VID001.mp4
            Folder = -
            Size = 2000
        """.trimIndent()

        val entries = SevenZipOutputParser.parseListOutput(output)

        assertEquals(1, entries.size)
        assertEquals("Pojat telttaretkellä/VID001.mp4", entries[0].path)
        assertEquals("VID001.mp4", entries[0].name)
    }

    @Test
    fun `parses 64-bit sizes above 4 GiB`() {
        val output = listHeader + "\n" + """
            Path = big.bin
            Folder = -
            Size = 6003284468
        """.trimIndent()

        val entries = SevenZipOutputParser.parseListOutput(output)
        assertEquals(6003284468L, entries.single().size)
    }

    @Test
    fun `returns empty when no separator present`() {
        assertTrue(SevenZipOutputParser.parseListOutput("garbage\nno entries here").isEmpty())
    }

    // --- indicatesPasswordError ---

    @Test
    fun `detects specific password-error phrases`() {
        assertTrue(SevenZipOutputParser.indicatesPasswordError("ERROR: Wrong password?"))
        assertTrue(SevenZipOutputParser.indicatesPasswordError("Can not open encrypted archive. Wrong password?"))
        assertTrue(SevenZipOutputParser.indicatesPasswordError("Data Error in encrypted file. Wrong password?"))
    }

    @Test
    fun `does not treat a filename containing encrypted as a password error`() {
        // Regression for the bare-"encrypted" false positive.
        assertFalse(SevenZipOutputParser.indicatesPasswordError("- backup/encrypted-keys.txt\nERROR: CRC failed"))
        assertFalse(SevenZipOutputParser.indicatesPasswordError("Extracting D:\\encrypted\\a.zip"))
    }

    // --- parseProgressLine ---

    @Test
    fun `parses percentage lines`() {
        assertEquals(SevenZipProgressLine.Percent(45), SevenZipOutputParser.parseProgressLine("  45% - foo"))
        assertEquals(SevenZipProgressLine.Percent(100), SevenZipOutputParser.parseProgressLine("100%"))
    }

    @Test
    fun `parses current-file lines`() {
        assertEquals(
            SevenZipProgressLine.CurrentFile("Näyte ählä/vidäö.mp4"),
            SevenZipOutputParser.parseProgressLine("- Näyte ählä/vidäö.mp4")
        )
    }

    @Test
    fun `returns null for non-progress lines`() {
        assertNull(SevenZipOutputParser.parseProgressLine("Everything is Ok"))
        assertNull(SevenZipOutputParser.parseProgressLine(""))
    }
}
