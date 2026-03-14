package qunzip.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsFileAssociationRepositoryTest {

    @Test
    fun `progIdForExtension returns Qunzip dot extension for simple extensions`() {
        assertEquals("Qunzip.zip", WindowsFileAssociationRepository.progIdForExtension("zip"))
        assertEquals("Qunzip.7z", WindowsFileAssociationRepository.progIdForExtension("7z"))
        assertEquals("Qunzip.rar", WindowsFileAssociationRepository.progIdForExtension("rar"))
        assertEquals("Qunzip.tar", WindowsFileAssociationRepository.progIdForExtension("tar"))
        assertEquals("Qunzip.cab", WindowsFileAssociationRepository.progIdForExtension("cab"))
        assertEquals("Qunzip.arj", WindowsFileAssociationRepository.progIdForExtension("arj"))
        assertEquals("Qunzip.lzh", WindowsFileAssociationRepository.progIdForExtension("lzh"))
    }

    @Test
    fun `progIdForExtension replaces dots with underscores for compound extensions`() {
        assertEquals("Qunzip.tar_gz", WindowsFileAssociationRepository.progIdForExtension("tar.gz"))
        assertEquals("Qunzip.tar_bz2", WindowsFileAssociationRepository.progIdForExtension("tar.bz2"))
        assertEquals("Qunzip.tar_xz", WindowsFileAssociationRepository.progIdForExtension("tar.xz"))
    }

    @Test
    fun `progIdForExtension strips leading dot if present`() {
        assertEquals("Qunzip.zip", WindowsFileAssociationRepository.progIdForExtension(".zip"))
        assertEquals("Qunzip.tar_gz", WindowsFileAssociationRepository.progIdForExtension(".tar.gz"))
    }

    @Test
    fun `progIdForExtension handles short TAR aliases and simple compression extensions`() {
        assertEquals("Qunzip.tgz", WindowsFileAssociationRepository.progIdForExtension("tgz"))
        assertEquals("Qunzip.tbz2", WindowsFileAssociationRepository.progIdForExtension("tbz2"))
        assertEquals("Qunzip.txz", WindowsFileAssociationRepository.progIdForExtension("txz"))
        assertEquals("Qunzip.gz", WindowsFileAssociationRepository.progIdForExtension("gz"))
        assertEquals("Qunzip.bz2", WindowsFileAssociationRepository.progIdForExtension("bz2"))
        assertEquals("Qunzip.xz", WindowsFileAssociationRepository.progIdForExtension("xz"))
    }

    @Test
    fun `allProgIds contains all expected ProgIDs plus legacy`() {
        val progIds = WindowsFileAssociationRepository.allProgIds
        // All per-format ProgIDs
        assertEquals(true, progIds.contains("Qunzip.zip"))
        assertEquals(true, progIds.contains("Qunzip.7z"))
        assertEquals(true, progIds.contains("Qunzip.rar"))
        assertEquals(true, progIds.contains("Qunzip.tar"))
        assertEquals(true, progIds.contains("Qunzip.tar_gz"))
        assertEquals(true, progIds.contains("Qunzip.tar_bz2"))
        assertEquals(true, progIds.contains("Qunzip.tar_xz"))
        assertEquals(true, progIds.contains("Qunzip.gz"))
        assertEquals(true, progIds.contains("Qunzip.bz2"))
        assertEquals(true, progIds.contains("Qunzip.xz"))
        assertEquals(true, progIds.contains("Qunzip.tgz"))
        assertEquals(true, progIds.contains("Qunzip.tbz2"))
        assertEquals(true, progIds.contains("Qunzip.txz"))
        assertEquals(true, progIds.contains("Qunzip.cab"))
        assertEquals(true, progIds.contains("Qunzip.arj"))
        assertEquals(true, progIds.contains("Qunzip.lzh"))
        // Legacy ProgID for cleanup
        assertEquals(true, progIds.contains("Qunzip.ArchiveFile"))
    }
}
