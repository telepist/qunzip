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
     * Parse the output of `7z l -slt` into archive entries. Entries begin after
     * the `----------` separator; each starts with a `Path = ` line followed by
     * its fields. A new `Path = ` (or end of input) ends the current entry — so
     * this does not depend on blank lines between blocks, which lets the caller
     * stream trimmed, blank-stripped lines.
     */
    fun parseListOutput(output: String): List<ArchiveEntry> {
        val lines = output.split("\n")

        // Skip until the "----------" separator that marks the start of entries.
        var i = 0
        while (i < lines.size && !lines[i].trim().startsWith("----------")) i++
        if (i >= lines.size) return emptyList()
        i++ // past the separator

        val entries = mutableListOf<ArchiveEntry>()
        var path: String? = null
        var isDir = false
        var size = 0L
        var packed: Long? = null

        fun flush() {
            val p = path ?: return
            val normalized = p.replace('\\', '/')
            entries.add(
                ArchiveEntry(
                    path = normalized,
                    name = normalized.substringAfterLast('/'),
                    isDirectory = isDir,
                    size = size,
                    compressedSize = packed,
                )
            )
            path = null; isDir = false; size = 0L; packed = null
        }

        while (i < lines.size) {
            val line = lines[i].trim()
            when {
                line.startsWith("Path = ") -> { flush(); path = line.substring(7).trim() }
                line.startsWith("Folder = ") -> isDir = line.substring(9).trim() == "+"
                line.startsWith("Size = ") -> size = line.substring(7).trim().toLongOrNull() ?: 0
                line.startsWith("Packed Size = ") -> packed = line.substring(14).trim().toLongOrNull()
            }
            i++
        }
        flush()
        return entries
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
