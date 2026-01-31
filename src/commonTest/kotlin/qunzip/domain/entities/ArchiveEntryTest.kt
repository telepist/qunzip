package qunzip.domain.entities

import kotlin.test.*

class ArchiveEntryTest {

    @Test
    fun `isFile returns correct value`() {
        val file = ArchiveEntry(
            path = "documents/file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 1024L
        )

        val directory = ArchiveEntry(
            path = "documents",
            name = "documents",
            isDirectory = true,
            size = 0L
        )

        assertTrue(file.isFile)
        assertFalse(directory.isFile)
    }

    @Test
    fun `parentPath returns correct parent for nested paths`() {
        val entry = ArchiveEntry(
            path = "folder/subfolder/file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 512L
        )

        assertEquals("folder/subfolder", entry.parentPath)
    }

    @Test
    fun `parentPath returns null for root level entries`() {
        val entry = ArchiveEntry(
            path = "file.txt",
            name = "file.txt",
            isDirectory = false,
            size = 512L
        )

        assertNull(entry.parentPath)
    }

    @Test
    fun `depth calculation is correct`() {
        val rootFile = ArchiveEntry("file.txt", "file.txt", false, 100L)
        val nestedFile = ArchiveEntry("folder/file.txt", "file.txt", false, 100L)
        val deeplyNested = ArchiveEntry("a/b/c/d/file.txt", "file.txt", false, 100L)

        assertEquals(0, rootFile.depth)
        assertEquals(1, nestedFile.depth)
        assertEquals(4, deeplyNested.depth)
    }
}

class ArchiveContentsTest {

    private fun createTestContents(): ArchiveContents {
        val entries = listOf(
            ArchiveEntry("file1.txt", "file1.txt", false, 100L),
            ArchiveEntry("file2.txt", "file2.txt", false, 200L),
            ArchiveEntry("folder", "folder", true, 0L),
            ArchiveEntry("folder/nested.txt", "nested.txt", false, 300L)
        )

        return ArchiveContents(
            entries = entries,
            totalSize = 600L,
            totalCompressedSize = 400L
        )
    }

    @Test
    fun `fileCount returns correct count`() {
        val contents = createTestContents()
        assertEquals(3, contents.fileCount) // file1.txt, file2.txt, folder/nested.txt
    }

    @Test
    fun `directoryCount returns correct count`() {
        val contents = createTestContents()
        assertEquals(1, contents.directoryCount) // folder
    }

    @Test
    fun `isEmpty returns false for non-empty archive`() {
        val contents = createTestContents()
        assertFalse(contents.isEmpty)
    }

    @Test
    fun `isEmpty returns true for empty archive`() {
        val contents = ArchiveContents(emptyList(), 0L)
        assertTrue(contents.isEmpty)
    }

    @Test
    fun `hasMultipleRootItems detects multiple root items`() {
        val contents = createTestContents()
        assertTrue(contents.hasMultipleRootItems) // file1.txt, file2.txt, folder
    }

    @Test
    fun `hasMultipleRootItems detects single root item`() {
        val entries = listOf(
            ArchiveEntry("folder", "folder", true, 0L),
            ArchiveEntry("folder/file1.txt", "file1.txt", false, 100L),
            ArchiveEntry("folder/file2.txt", "file2.txt", false, 200L)
        )

        val contents = ArchiveContents(entries, 300L)
        assertFalse(contents.hasMultipleRootItems)
    }

    @Test
    fun `singleRootDirectory returns directory when archive has single root directory`() {
        val entries = listOf(
            ArchiveEntry("project", "project", true, 0L),
            ArchiveEntry("project/src/main.kt", "main.kt", false, 500L),
            ArchiveEntry("project/README.md", "README.md", false, 100L)
        )

        val contents = ArchiveContents(entries, 600L)
        val rootDir = contents.singleRootDirectory

        assertNotNull(rootDir)
        assertEquals("project", rootDir.name)
        assertTrue(rootDir.isDirectory)
    }

    @Test
    fun `singleRootDirectory returns null when archive has multiple root items`() {
        val contents = createTestContents()
        assertNull(contents.singleRootDirectory)
    }

    @Test
    fun `singleRootDirectory returns null when single root item is file`() {
        val entries = listOf(
            ArchiveEntry("document.pdf", "document.pdf", false, 1024L)
        )

        val contents = ArchiveContents(entries, 1024L)
        assertNull(contents.singleRootDirectory)
    }

    @Test
    fun `topLevelEntries returns only root level entries`() {
        val contents = createTestContents()
        val topLevel = contents.topLevelEntries

        assertEquals(3, topLevel.size)
        assertTrue(topLevel.any { it.name == "file1.txt" })
        assertTrue(topLevel.any { it.name == "file2.txt" })
        assertTrue(topLevel.any { it.name == "folder" })
        assertFalse(topLevel.any { it.name == "nested.txt" })
    }

    @Test
    fun `topLevelEntries handles empty archive`() {
        val contents = ArchiveContents(emptyList(), 0L)
        assertTrue(contents.topLevelEntries.isEmpty())
    }
}