package qunzip.domain.entities

import kotlinx.datetime.Instant

data class ArchiveEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val compressedSize: Long? = null,
    val lastModified: Instant? = null,
    val permissions: String? = null
) {
    val isFile: Boolean get() = !isDirectory

    val parentPath: String?
        get() = if (path.contains('/')) {
            path.substringBeforeLast('/')
        } else null

    val depth: Int
        get() = path.count { it == '/' }
}

data class ArchiveContents(
    val entries: List<ArchiveEntry>,
    val totalSize: Long,
    val totalCompressedSize: Long? = null
) {
    val fileCount: Int get() = entries.count { it.isFile }
    val directoryCount: Int get() = entries.count { it.isDirectory }
    val isEmpty: Boolean get() = entries.isEmpty()

    val hasMultipleRootItems: Boolean
        get() = topLevelEntries.size > 1

    val singleRootDirectory: ArchiveEntry?
        get() {
            val topLevel = topLevelEntries
            return if (topLevel.size == 1 && topLevel.first().isDirectory) {
                topLevel.first()
            } else null
        }

    /**
     * Returns top-level entries, including implicit directories.
     * Some archives don't include explicit directory entries for parent folders
     * (e.g., listing "End/Binaries" but not "End" itself). This computes the
     * true top-level entries by also considering the first path segment of
     * nested entries.
     */
    val topLevelEntries: List<ArchiveEntry>
        get() {
            val explicit = entries.filter { it.depth == 0 }
            val explicitNames = explicit.map { it.name }.toSet()

            // Find implicit top-level directories from nested entries
            val implicitDirNames = entries
                .filter { it.depth > 0 }
                .map { it.path.substringBefore('/') }
                .toSet()
                .minus(explicitNames)

            val implicitDirs = implicitDirNames.map { name ->
                ArchiveEntry(
                    path = name,
                    name = name,
                    isDirectory = true,
                    size = 0L
                )
            }

            return explicit + implicitDirs
        }
}