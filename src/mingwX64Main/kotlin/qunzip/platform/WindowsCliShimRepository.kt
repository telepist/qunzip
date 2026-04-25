@file:OptIn(ExperimentalForeignApi::class)

package qunzip.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.*
import platform.windows.*
import qunzip.domain.repositories.CliShimRepository
import qunzip.domain.repositories.ShimResult

/**
 * Adds the install directory to the user's PATH environment variable so
 * `qunzip` resolves in any new shell. We modify the per-user PATH only
 * (HKCU\Environment\Path) — never machine-wide — so this works without
 * elevation.
 *
 * After a write we broadcast WM_SETTINGCHANGE("Environment") so newly
 * spawned shells (and Explorer) pick up the change without a logoff.
 * Already-running shells keep their old PATH; that's a Windows limitation,
 * not ours.
 *
 * @param installDirProvider returns the directory to add (parent of qunzip.exe).
 *        Injected so tests don't depend on the real executable path.
 */
class WindowsCliShimRepository(
    private val installDirProvider: () -> String,
    private val logger: Logger = Logger.withTag("CliShim"),
    private val registry: RegistryHelper = RegistryHelper()
) : CliShimRepository {

    override val isSupported: Boolean = true

    override suspend fun isInstalled(): Boolean {
        val dir = installDirProvider().normalizedDir()
        if (dir.isEmpty()) return false
        val current = readUserPath() ?: return false
        return current.containsDir(dir)
    }

    override suspend fun install(): ShimResult {
        val dir = installDirProvider().normalizedDir()
        if (dir.isEmpty()) {
            // Defensive: refuse to write an empty entry into PATH. This can
            // happen if getCurrentExecutablePath() falls back to a bare
            // filename (no parent), e.g. in unusual launch contexts.
            logger.w { "Refusing to add empty install dir to PATH" }
            return ShimResult(success = false, message = "Could not determine install directory")
        }
        val (current, type) = readUserPathWithType() ?: ("" to REG_EXPAND_SZ)

        if (current.containsDir(dir)) {
            logger.i { "Install dir already on user PATH: $dir" }
            return ShimResult(success = true, message = "Already on PATH")
        }

        // Append with a single semicolon separator. Avoid a leading or
        // double semicolon if `current` is blank or already trailing.
        val trimmed = current.trimEnd(';')
        val updated = if (trimmed.isEmpty()) dir else "$trimmed;$dir"
        val ok = writeUserPath(updated, type)
        if (ok) {
            broadcastEnvironmentChange()
            logger.i { "Added install dir to user PATH: $dir" }
            return ShimResult(success = true, message = "Added to PATH (new terminals only)")
        }
        return ShimResult(success = false, message = "Failed to write user PATH")
    }

    override suspend fun uninstall(): ShimResult {
        val dir = installDirProvider().normalizedDir()
        if (dir.isEmpty()) return ShimResult(success = true, message = "Not on PATH")
        val (current, type) = readUserPathWithType() ?: return ShimResult(success = true, message = "PATH not set")

        if (!current.containsDir(dir)) {
            logger.i { "Install dir already absent from user PATH: $dir" }
            return ShimResult(success = true, message = "Not on PATH")
        }

        val updated = current.split(';')
            .filter { it.isNotEmpty() && !it.normalizedDir().equals(dir, ignoreCase = true) }
            .joinToString(";")

        val ok = writeUserPath(updated, type)
        if (ok) {
            broadcastEnvironmentChange()
            logger.i { "Removed install dir from user PATH: $dir" }
            return ShimResult(success = true, message = "Removed from PATH")
        }
        return ShimResult(success = false, message = "Failed to write user PATH")
    }

    private fun readUserPath(): String? = readUserPathWithType()?.first

    private fun readUserPathWithType(): Pair<String, Int>? {
        val key = registry.openKey(HKEY_CURRENT_USER, "Environment", KEY_READ.toUInt())
            ?: return null
        try {
            return registry.getStringValueWithType(key, "Path")
        } finally {
            registry.closeKey(key)
        }
    }

    private fun writeUserPath(value: String, type: Int): Boolean {
        val key = registry.openKey(HKEY_CURRENT_USER, "Environment", KEY_WRITE.toUInt())
            ?: registry.createKey(HKEY_CURRENT_USER, "Environment")
            ?: return false
        try {
            return registry.setStringValue(key, "Path", value, type)
        } finally {
            registry.closeKey(key)
        }
    }

    /**
     * Tell already-running processes that the environment has changed.
     * They get a chance to refresh; most don't, but Explorer does, and any
     * new shell spawned after this picks up the new PATH.
     */
    private fun broadcastEnvironmentChange() = memScoped {
        val result = alloc<DWORD_PTRVar>()
        SendMessageTimeoutW(
            HWND_BROADCAST,
            WM_SETTINGCHANGE.toUInt(),
            0.toULong(),
            "Environment".wcstr.ptr.toLong(),
            SMTO_ABORTIFHUNG.toUInt(),
            5_000u,
            result.ptr
        )
    }
}

private fun String.normalizedDir(): String =
    this.trim()
        .trim('"')                 // strip surrounding quotes (`"C:\Foo"` is a common PATH form)
        .trimEnd('\\', '/')
        .replace('/', '\\')

private fun String.containsDir(dir: String): Boolean =
    this.split(';').any { it.normalizedDir().equals(dir, ignoreCase = true) }
