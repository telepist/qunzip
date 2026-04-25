package qunzip.domain.repositories

/**
 * Manages whether the CLI binary (`qunzip`) is reachable from the user's
 * shell. On Windows this means adding the install directory to user PATH;
 * other platforms may use shell rc files or symlinks into a bin directory.
 *
 * The shim is intentionally a one-bit toggle from the user's perspective:
 * either `qunzip` works in a fresh terminal or it doesn't. The repository
 * hides the platform-specific mechanism behind isInstalled / install /
 * uninstall.
 */
interface CliShimRepository {
    /** True if a fresh shell can run `qunzip` without a path. */
    suspend fun isInstalled(): Boolean

    /** Install the shim. Returns false on platforms that don't support it. */
    suspend fun install(): ShimResult

    /** Uninstall the shim. Idempotent — succeeds if the shim is already absent. */
    suspend fun uninstall(): ShimResult

    /** True on platforms where install/uninstall actually do something. */
    val isSupported: Boolean
}

data class ShimResult(
    val success: Boolean,
    val message: String? = null
)
