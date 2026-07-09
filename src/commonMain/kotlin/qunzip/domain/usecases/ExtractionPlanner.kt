package qunzip.domain.usecases

import qunzip.domain.entities.Archive
import qunzip.domain.entities.ArchiveContents
import qunzip.domain.entities.ExtractionStrategy

/**
 * The pure decision of *how* to lay out an extraction: which strategy applies
 * and what the output should be named. Kept free of any filesystem effect so it
 * can be exhaustively table-tested; ExtractArchiveUseCase executes the plan.
 */
data class ExtractionPlan(
    val strategy: ExtractionStrategy,
    /** Base name of the produced item: the single entry's name, or the folder
     *  name derived from the archive for a multi-file archive. */
    val targetName: String,
)

object ExtractionPlanner {

    /**
     * @param isCompoundTar true when the archive is being handled via the
     *   decompress-then-untar path (foo.tar.gz), so the folder name strips two
     *   extensions; a bare foo.sql.gz is not compound and keeps one.
     */
    fun plan(archive: Archive, contents: ArchiveContents, isCompoundTar: Boolean): ExtractionPlan {
        val strategy = determineStrategy(contents)
        val archiveNameForFolder = if (isCompoundTar) {
            archive.name.substringBeforeLast('.').substringBeforeLast('.')
        } else {
            archive.nameWithoutExtension
        }
        val targetName = when (strategy) {
            ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY,
            ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY -> contents.topLevelEntries.first().name
            ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER -> archiveNameForFolder
        }
        return ExtractionPlan(strategy, targetName)
    }

    fun determineStrategy(contents: ArchiveContents): ExtractionStrategy = when {
        contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isFile ->
            ExtractionStrategy.SINGLE_FILE_TO_DIRECTORY
        contents.topLevelEntries.size == 1 && contents.topLevelEntries.first().isDirectory ->
            ExtractionStrategy.SINGLE_FOLDER_TO_DIRECTORY
        else ->
            ExtractionStrategy.MULTIPLE_FILES_TO_FOLDER
    }
}
