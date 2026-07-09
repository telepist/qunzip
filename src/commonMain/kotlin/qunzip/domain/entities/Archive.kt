package qunzip.domain.entities

import kotlin.time.Instant

data class Archive(
    val path: String,
    val name: String,
    val format: ArchiveFormat,
    val size: Long,
    val lastModified: Instant? = null
) {
    val nameWithoutExtension: String
        get() = name.substringBeforeLast('.')
}

enum class ArchiveFormat(val extensions: List<String>, val displayName: String) {
    ZIP(listOf("zip"), "ZIP Archive"),
    SEVEN_ZIP(listOf("7z"), "7-Zip Archive"),
    RAR(listOf("rar"), "RAR Archive"),
    TAR(listOf("tar"), "TAR Archive"),
    TAR_GZ(listOf("tar.gz", "tgz", "gz"), "Gzipped TAR Archive"),
    TAR_BZ2(listOf("tar.bz2", "tbz2", "bz2"), "Bzip2 TAR Archive"),
    TAR_XZ(listOf("tar.xz", "txz", "xz"), "XZ TAR Archive"),
    CAB(listOf("cab"), "Cabinet Archive"),
    ARJ(listOf("arj"), "ARJ Archive"),
    LZH(listOf("lzh"), "LZH Archive");

    /**
     * Whether this format *may* wrap a tar and thus need two-stage extraction
     * (decompress, then untar). It only "may" because these extensions also cover
     * a bare compressed single file (e.g. backup.sql.gz), which is extracted
     * directly — the use case checks the inner entry to decide.
     */
    val mayContainTar: Boolean
        get() = this == TAR_GZ || this == TAR_BZ2 || this == TAR_XZ

    companion object {
        fun fromExtension(extension: String): ArchiveFormat? {
            val lowerExt = extension.lowercase()
            return entries.find { format ->
                format.extensions.any { it == lowerExt }
            }
        }

        fun fromFilename(filename: String): ArchiveFormat? {
            val lowerFilename = filename.lowercase()

            // Check compound extensions first (e.g., tar.gz)
            val compoundFormats = listOf(TAR_GZ, TAR_BZ2, TAR_XZ)
            for (format in compoundFormats) {
                if (format.extensions.any { lowerFilename.endsWith(".$it") }) {
                    return format
                }
            }

            // Check simple extensions
            val extension = lowerFilename.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}
