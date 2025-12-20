package gunzip.domain.entities

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
        get() {
            val rootItems = entries.filter { it.depth == 0 }
            return rootItems.size > 1
        }

    val singleRootDirectory: ArchiveEntry?
        get() {
            val rootItems = entries.filter { it.depth == 0 }
            return if (rootItems.size == 1 && rootItems.first().isDirectory) {
                rootItems.first()
            } else null
        }

    val topLevelEntries: List<ArchiveEntry>
        get() = entries.filter { it.depth == 0 }
}