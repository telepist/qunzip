package qunzip.data.process

/**
 * Runs a child process and streams its stdout back as decoded, trimmed,
 * non-empty lines (split on both CR and LF, so 7-Zip's `\r`-overwritten progress
 * counter yields one call per update). Abstracts the platform process machinery
 * behind a single seam so callers hold no syscall code and can be unit-tested
 * with a fake.
 */
interface ProcessRunner {
    /**
     * Launch [program] with [args] (each quoted per the platform's rules),
     * invoking [onStdoutLine] for each stdout line as it arrives. If
     * [shouldContinue] returns false the child is terminated. Returns the process
     * exit code (nonzero — e.g. 1 — if it was terminated), or a negative value if
     * the process could not be started.
     */
    fun run(
        program: String,
        args: List<String>,
        shouldContinue: () -> Boolean = { true },
        onStdoutLine: (String) -> Unit = {},
    ): Int
}
