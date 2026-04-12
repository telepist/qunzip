package qunzip.presentation.ui

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${formatDecimal(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${formatDecimal(bytes / (1024.0 * 1024.0))} MB"
        else -> "${formatDecimal(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

private fun formatDecimal(value: Double): String {
    val whole = value.toLong()
    val frac = ((value - whole) * 10).toLong().coerceIn(0, 9)
    return "$whole.$frac"
}
