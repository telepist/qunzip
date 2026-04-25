package qunzip.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WindowsFileAssociationRepositoryTest {

    @Test
    fun `progIdForExtension returns QuickUnzip dot extension for simple extensions`() {
        assertEquals("QuickUnzip.zip", WindowsFileAssociationRepository.progIdForExtension("zip"))
        assertEquals("QuickUnzip.7z", WindowsFileAssociationRepository.progIdForExtension("7z"))
        assertEquals("QuickUnzip.rar", WindowsFileAssociationRepository.progIdForExtension("rar"))
        assertEquals("QuickUnzip.tar", WindowsFileAssociationRepository.progIdForExtension("tar"))
        assertEquals("QuickUnzip.cab", WindowsFileAssociationRepository.progIdForExtension("cab"))
        assertEquals("QuickUnzip.arj", WindowsFileAssociationRepository.progIdForExtension("arj"))
        assertEquals("QuickUnzip.lzh", WindowsFileAssociationRepository.progIdForExtension("lzh"))
    }

    @Test
    fun `progIdForExtension replaces dots with underscores for compound extensions`() {
        assertEquals("QuickUnzip.tar_gz", WindowsFileAssociationRepository.progIdForExtension("tar.gz"))
        assertEquals("QuickUnzip.tar_bz2", WindowsFileAssociationRepository.progIdForExtension("tar.bz2"))
        assertEquals("QuickUnzip.tar_xz", WindowsFileAssociationRepository.progIdForExtension("tar.xz"))
    }

    @Test
    fun `progIdForExtension strips leading dot if present`() {
        assertEquals("QuickUnzip.zip", WindowsFileAssociationRepository.progIdForExtension(".zip"))
        assertEquals("QuickUnzip.tar_gz", WindowsFileAssociationRepository.progIdForExtension(".tar.gz"))
    }

    @Test
    fun `progIdForExtension handles short TAR aliases and simple compression extensions`() {
        assertEquals("QuickUnzip.tgz", WindowsFileAssociationRepository.progIdForExtension("tgz"))
        assertEquals("QuickUnzip.tbz2", WindowsFileAssociationRepository.progIdForExtension("tbz2"))
        assertEquals("QuickUnzip.txz", WindowsFileAssociationRepository.progIdForExtension("txz"))
        assertEquals("QuickUnzip.gz", WindowsFileAssociationRepository.progIdForExtension("gz"))
        assertEquals("QuickUnzip.bz2", WindowsFileAssociationRepository.progIdForExtension("bz2"))
        assertEquals("QuickUnzip.xz", WindowsFileAssociationRepository.progIdForExtension("xz"))
    }

    @Test
    fun `allProgIds contains all current and legacy ProgIDs for cleanup`() {
        val progIds = WindowsFileAssociationRepository.allProgIds
        // Current per-format ProgIDs
        listOf(
            "zip", "7z", "rar", "tar", "tar_gz", "tar_bz2", "tar_xz",
            "gz", "bz2", "xz", "tgz", "tbz2", "txz", "cab", "arj", "lzh"
        ).forEach { ext ->
            assertTrue("missing QuickUnzip.$ext") { progIds.contains("QuickUnzip.$ext") }
            assertTrue("missing legacy Qunzip.$ext") { progIds.contains("Qunzip.$ext") }
        }
        // Both single-ProgID legacy names
        assertTrue { progIds.contains("QuickUnzip.ArchiveFile") }
        assertTrue { progIds.contains("Qunzip.ArchiveFile") }
    }
}
