package qunzip.data.sevenzip

import qunzip.domain.entities.ArchiveEntry

/**
 * Pure parser for 7-Zip's textual console output. Kept free of any platform /
 * cinterop dependency so it can be unit-tested against captured fixture strings
 * (it's the most logic-dense, regression-prone part of the 7-Zip integration).
 */
object SevenZipOutputParser {

    // Deliberately specific multi-word phrases 7-Zip prints when an archive is
    // encrypted or the password is wrong. A bare "encrypted" would also match
    // entry names (extraction output includes filenames via -bb1) or the archive
    // path, so a CRC/disk error on an archive containing "encrypted" in a name
    // would be misreported as a wrong password.
    private val PASSWORD_INDICATORS = listOf(
        "wrong password",
        "can not open encrypted archive",
        "data error in encrypted file",
        "enter password",
    )

    /** True if [output] contains a 7-Zip password/encryption error indicator. */
    fun indicatesPasswordError(output: String): Boolean {
        val lower = output.lowercase()
        return PASSWORD_INDICATORS.any { lower.contains(it) }
    }

    /**
     * Parse the output of `7z l -slt` into archive entries. Entry blocks start
     * after the `----------` separator, each beginning with `Path = `.
     */
    fun parseListOutput(output: String): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        val lines = output.split("\n")

        // Skip until the "----------" separator that marks the start of entries.
        var i = 0
        var foundSeparator = false
        while (i < lines.size) {
            if (lines[i].trim().startsWith("----------")) {
                foundSeparator = true
                i++
                break
            }
            i++
        }
        if (!foundSeparator) return emptyList()

        while (i < lines.size) {
            if (lines[i].trim().startsWith("Path = ")) {
                parseEntryBlock(lines, i)?.let { entries.add(it) }
            }
            i++
        }
        return entries
    }

    private fun parseEntryBlock(lines: List<String>, startIndex: Int): ArchiveEntry? {
        var path: String? = null
        var isDirectory = false
        var size: Long = 0
        var compressedSize: Long? = null

        var i = startIndex
        while (i < lines.size && lines[i].trim().isNotEmpty()) {
            val line = lines[i].trim()
            when {
                line.startsWith("Path = ") -> path = line.substring(7).trim()
                line.startsWith("Folder = ") -> isDirectory = line.substring(9).trim() == "+"
                line.startsWith("Size = ") -> size = line.substring(7).trim().toLongOrNull() ?: 0
                line.startsWith("Packed Size = ") -> compressedSize = line.substring(14).trim().toLongOrNull()
            }
            i++
        }

        val p = path ?: return null
        // Normalize path separators to forward slashes.
        val normalizedPath = p.replace('\\', '/')
        return ArchiveEntry(
            path = normalizedPath,
            name = normalizedPath.substringAfterLast('/'),
            isDirectory = isDirectory,
            size = size,
            compressedSize = compressedSize,
        )
    }

    private val percentRegex = Regex("""^\s*(\d+)%""")

    /**
     * Interpret one line of `7z x -bsp1 -bb1` progress output: a percentage
     * (e.g. " 45%") or a current-file marker ("- <path>"). Returns null for lines
     * that are neither.
     */
    fun parseProgressLine(line: String): SevenZipProgressLine? {
        val percent = percentRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
        if (percent != null && percent in 0..100) {
            return SevenZipProgressLine.Percent(percent)
        }
        if (line.startsWith("- ")) {
            return SevenZipProgressLine.CurrentFile(line.substring(2).trim())
        }
        return null
    }
}

/** A single meaningful line of 7-Zip extraction progress output. */
sealed interface SevenZipProgressLine {
    data class Percent(val value: Int) : SevenZipProgressLine
    data class CurrentFile(val name: String) : SevenZipProgressLine
}
