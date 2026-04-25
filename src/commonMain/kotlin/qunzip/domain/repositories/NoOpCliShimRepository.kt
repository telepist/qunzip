package qunzip.domain.repositories

/**
 * Default no-op shim used on platforms that don't yet have a real
 * implementation. Reports unsupported and refuses install/uninstall.
 */
class NoOpCliShimRepository : CliShimRepository {
    override val isSupported: Boolean = false
    override suspend fun isInstalled(): Boolean = false
    override suspend fun install(): ShimResult =
        ShimResult(success = false, message = "Adding to PATH is not implemented on this platform yet.")
    override suspend fun uninstall(): ShimResult =
        ShimResult(success = false, message = "Removing from PATH is not implemented on this platform yet.")
}
