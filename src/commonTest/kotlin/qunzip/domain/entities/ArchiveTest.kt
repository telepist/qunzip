package qunzip.domain.entities

import kotlin.test.*

class ArchiveTest {

    @Test
    fun `nameWithoutExtension removes single extension correctly`() {
        val archive = Archive(
            path = "/test/document.zip",
            name = "document.zip",
            format = ArchiveFormat.ZIP,
            size = 1024L
        )

        assertEquals("document", archive.nameWithoutExtension)
    }

    @Test
    fun `nameWithoutExtension handles compound extensions`() {
        val archive = Archive(
            path = "/test/archive.tar.gz",
            name = "archive.tar.gz",
            format = ArchiveFormat.TAR_GZ,
            size = 2048L
        )

        assertEquals("archive.tar", archive.nameWithoutExtension)
    }

    @Test
    fun `nameWithoutExtension handles files without extension`() {
        val archive = Archive(
            path = "/test/noextension",
            name = "noextension",
            format = ArchiveFormat.ZIP,
            size = 512L
        )

        assertEquals("noextension", archive.nameWithoutExtension)
    }
}

class ArchiveFormatTest {

    @Test
    fun `fromExtension returns correct format for zip`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromExtension("zip"))
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromExtension("ZIP"))
    }

    @Test
    fun `fromExtension returns correct format for 7z`() {
        assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormat.fromExtension("7z"))
    }

    @Test
    fun `fromExtension returns null for unsupported extension`() {
        assertNull(ArchiveFormat.fromExtension("txt"))
        assertNull(ArchiveFormat.fromExtension("unknown"))
    }

    @Test
    fun `fromFilename handles compound extensions correctly`() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.fromFilename("archive.tar.gz"))
        assertEquals(ArchiveFormat.TAR_BZ2, ArchiveFormat.fromFilename("backup.tar.bz2"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.fromFilename("data.tar.xz"))
    }

    @Test
    fun `fromFilename handles simple extensions`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromFilename("document.zip"))
        assertEquals(ArchiveFormat.RAR, ArchiveFormat.fromFilename("archive.rar"))
        assertEquals(ArchiveFormat.SEVEN_ZIP, ArchiveFormat.fromFilename("compressed.7z"))
    }

    @Test
    fun `fromFilename is case insensitive`() {
        assertEquals(ArchiveFormat.ZIP, ArchiveFormat.fromFilename("Document.ZIP"))
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.fromFilename("Archive.TAR.GZ"))
    }

    @Test
    fun `fromFilename returns null for unsupported files`() {
        assertNull(ArchiveFormat.fromFilename("document.txt"))
        assertNull(ArchiveFormat.fromFilename("image.jpg"))
        assertNull(ArchiveFormat.fromFilename("noextension"))
    }

    @Test
    fun `fromFilename prioritizes compound extensions over simple ones`() {
        // Should detect tar.gz, not just gz
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.fromFilename("file.tar.gz"))

        // Should detect tar.bz2, not just bz2
        assertEquals(ArchiveFormat.TAR_BZ2, ArchiveFormat.fromFilename("archive.tar.bz2"))
    }

    @Test
    fun `all formats have non-empty extensions list`() {
        ArchiveFormat.values().forEach { format ->
            assertTrue(format.extensions.isNotEmpty(), "Format ${format.name} should have at least one extension")
            format.extensions.forEach { ext ->
                assertFalse(ext.isEmpty(), "Extension should not be empty for format ${format.name}")
                assertFalse(ext.startsWith("."), "Extension should not start with dot for format ${format.name}")
            }
        }
    }

    @Test
    fun `all formats have non-empty display names`() {
        ArchiveFormat.values().forEach { format ->
            assertTrue(format.displayName.isNotEmpty(), "Format ${format.name} should have a display name")
        }
    }
}