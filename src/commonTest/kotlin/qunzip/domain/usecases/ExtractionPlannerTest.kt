package qunzip.domain.usecases

import qunzip.domain.entities.*
import kotlin.test.*

class ExtractionPlannerTest {

    private fun archive(name: String) = Archive("/x/$name", name, ArchiveFormat.ZIP, 100L)
    private fun file(name: String) = ArchiveEntry(name, name, isDirectory = false, size = 10L)
    private fun dir(name: String) = ArchiveEntry(name, name, isDirectory = true, size = 0L)
    private fun contents(vararg entries: ArchiveEntry) =
        ArchiveContents(entries.toList(), entries.sumOf { it.size })

    @Test
    fun `single file extracts to directory using the entry name`() {
        val plan = ExtractionPlanner.plan(archive("a.zip"), contents(file("doc.pdf")), isCompoundTar = false)
        assertEquals(ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY, plan.strategy)
        assertEquals("doc.pdf", plan.targetName)
    }

    @Test
    fun `single top-level folder extracts to directory using the folder name`() {
        // A single root dir (with children) -> SINGLE_FOLDER via topLevelEntries.
        val plan = ExtractionPlanner.plan(
            archive("proj.zip"),
            contents(dir("myproject"), file("myproject/main.kt")),
            isCompoundTar = false,
        )
        assertEquals(ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY, plan.strategy)
        assertEquals("myproject", plan.targetName)
    }

    @Test
    fun `multiple files go to a folder named after the archive`() {
        val plan = ExtractionPlanner.plan(
            archive("bundle.zip"),
            contents(file("a.txt"), file("b.txt")),
            isCompoundTar = false,
        )
        assertEquals(ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER, plan.strategy)
        assertEquals("bundle", plan.targetName)
    }

    @Test
    fun `compound tar strips two extensions for the folder name`() {
        val plan = ExtractionPlanner.plan(
            archive("release.tar.gz"),
            contents(file("a.txt"), file("b.txt")),
            isCompoundTar = true,
        )
        assertEquals("release", plan.targetName)
    }

    @Test
    fun `bare gz is not compound and keeps a single extension stripped`() {
        val plan = ExtractionPlanner.plan(
            archive("data.csv.gz"),
            contents(file("data.csv")),
            isCompoundTar = false,
        )
        // single file -> uses the entry name, not the archive-derived name
        assertEquals("data.csv", plan.targetName)
    }
}
