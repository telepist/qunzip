package qunzip.domain.entities

import kotlinx.datetime.Instant

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
    TAR_GZ(listOf("tar.gz", "tgz"), "Gzipped TAR Archive"),
    TAR_BZ2(listOf("tar.bz2", "tbz2"), "Bzip2 TAR Archive"),
    TAR_XZ(listOf("tar.xz", "txz"), "XZ TAR Archive"),
    CAB(listOf("cab"), "Cabinet Archive"),
    ARJ(listOf("arj"), "ARJ Archive"),
    LZH(listOf("lzh"), "LZH Archive");

    companion object {
        fun fromExtension(extension: String): ArchiveFormat? {
            val lowerExt = extension.lowercase()
            return values().find { format ->
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