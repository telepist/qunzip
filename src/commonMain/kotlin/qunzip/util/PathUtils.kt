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

/**
 * Parent directory of [filePath] (both separators accepted). A drive root keeps
 * its trailing backslash — "C:" alone means the drive's current directory, not
 * its root, so extracting there would land in the wrong place. Returns "." when
 * there is no directory component.
 */
internal fun windowsParentDirectory(filePath: String): String {
    val normalized = filePath.replace('/', '\\')
    val lastSeparator = normalized.lastIndexOf('\\')
    return if (lastSeparator > 0) {
        val parent = normalized.substring(0, lastSeparator)
        if (parent.length == 2 && parent[1] == ':') "$parent\\" else parent
    } else {
        "."
    }
}

/** Final path segment (both separators accepted). */
internal fun windowsFilename(path: String): String =
    path.replace('/', '\\').substringAfterLast('\\')

/** Extension without the dot, or "" if none (a leading-dot name has no extension). */
internal fun windowsFileExtension(path: String): String {
    val name = windowsFilename(path)
    val lastDot = name.lastIndexOf('.')
    return if (lastDot > 0) name.substring(lastDot + 1) else ""
}

/** Filename with its final extension removed (a leading-dot name is unchanged). */
internal fun windowsFilenameWithoutExtension(path: String): String {
    val name = windowsFilename(path)
    val lastDot = name.lastIndexOf('.')
    return if (lastDot > 0) name.substring(0, lastDot) else name
}
