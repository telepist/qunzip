package qunzip.util

/**
 * Pure Windows command-line construction. Lives in commonMain (no cinterop) so
 * the subtle quoting algorithm can be unit-tested directly — a quoting bug means
 * silent "file not found" failures or worse when paths/passwords contain spaces
 * or quotes.
 */

/**
 * Build a command line from a program path and arguments, quoting each token per
 * the CommandLineToArgvW rules that CreateProcessW/7-Zip follow. Quotes the
 * program path too (the install dir is "…\Quick Unzip", which has a space).
 */
internal fun buildWindowsCommandLine(program: String, args: List<String>): String =
    (listOf(program) + args).joinToString(" ") { quoteWindowsArg(it) }

/**
 * Quote a single argument using the standard MSVCRT/CommandLineToArgvW algorithm:
 * wrap in double quotes when it contains whitespace or a quote, escape embedded
 * quotes with a backslash, and double any run of backslashes that immediately
 * precedes a quote (including the closing one).
 */
internal fun quoteWindowsArg(arg: String): String {
    if (arg.isNotEmpty() && arg.none(::needsWindowsQuoting)) {
        return arg
    }
    val sb = StringBuilder()
    sb.append('"')
    var backslashes = 0
    for (c in arg) {
        when (c) {
            '\\' -> backslashes++
            '"' -> {
                repeat(backslashes * 2 + 1) { sb.append('\\') }
                backslashes = 0
                sb.append('"')
            }
            else -> {
                repeat(backslashes) { sb.append('\\') }
                backslashes = 0
                sb.append(c)
            }
        }
    }
    repeat(backslashes * 2) { sb.append('\\') }
    sb.append('"')
    return sb.toString()
}

/** A character that forces an argument to be wrapped in double quotes. */
private fun needsWindowsQuoting(c: Char): Boolean = c == ' ' || c == '\t' || c == '"'
