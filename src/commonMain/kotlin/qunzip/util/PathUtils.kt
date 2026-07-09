package qunzip.util

/**
 * Pure Windows path-string helpers (no cinterop), so the string manipulation is
 * unit-testable in commonTest and shared instead of duplicated across the
 * platform repositories.
 */

/** Join path components with backslashes, trimming stray separators between them. */
internal fun joinWindowsPath(vararg parts: String): String {
    val builder = StringBuilder()
    parts.filter { it.isNotEmpty() }.forEachIndexed { index, rawPart ->
        val part = when (index) {
            0 -> rawPart.trimEnd('\\', '/')
            else -> rawPart.trim('\\', '/')
        }
        if (part.isEmpty()) return@forEachIndexed
        if (builder.isNotEmpty()) builder.append('\\')
        builder.append(part)
    }
    return builder.toString()
}
