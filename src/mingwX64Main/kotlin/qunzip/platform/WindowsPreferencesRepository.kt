package qunzip.platform

import qunzip.domain.entities.UserPreferences
import qunzip.domain.repositories.PreferencesRepository
import qunzip.data.prefs.PreferencesPaths
import co.touchlab.kermit.Logger
import kotlinx.cinterop.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.posix.*
import platform.windows.*

/**
 * Windows implementation of PreferencesRepository.
 * Stores preferences in the same directory as the executable (settings.json)
 */
@OptIn(ExperimentalForeignApi::class)
class WindowsPreferencesRepository(
    private val logger: Logger = Logger.withTag("WindowsPreferencesRepository")
) : PreferencesRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // Store settings under %APPDATA%\QuickUnzip so they're writable on every
    // install (a system-wide Program Files install is not writable unelevated),
    // and shared between qunzip.exe and QuickUnzip.exe. Falls back to the exe
    // directory when APPDATA is unavailable (unusual / portable use).
    private val preferencesFile: String by lazy {
        PreferencesPaths.preferencesFile(
            PreferencesPaths.preferencesDirectory(getenv("APPDATA")?.toKString(), getExecutableDirectory())
        )
    }

    // Older versions stored settings.json next to the exe; read it once for
    // migration if the per-user file doesn't exist yet.
    private val legacyPreferencesFile: String by lazy {
        PreferencesPaths.preferencesFile(getExecutableDirectory())
    }

    override suspend fun loadPreferences(): UserPreferences {
        val path = PreferencesPaths.chooseLoadPath(
            newPath = preferencesFile,
            legacyPath = legacyPreferencesFile,
            newExists = fileExists(preferencesFile),
            legacyExists = fileExists(legacyPreferencesFile),
        )
        logger.d { "Loading preferences from: ${path ?: "(defaults)"}" }

        if (path == null) {
            logger.d { "No preferences file found, using defaults" }
            return UserPreferences.DEFAULT
        }

        return try {
            val content = readFile(path)
            if (content.isBlank()) {
                logger.w { "Preferences file is empty, using defaults" }
                return UserPreferences.DEFAULT
            }
            json.decodeFromString<UserPreferences>(content).also {
                logger.d { "Loaded preferences: $it" }
                // One-time migration: if we loaded from the old exe-dir location,
                // copy it to %APPDATA% and remove the legacy file so subsequent
                // loads/saves use the new, always-writable path.
                if (path == legacyPreferencesFile) {
                    if (writeFileAtomically(preferencesFile, content)) {
                        remove(legacyPreferencesFile)
                        logger.i { "Migrated preferences to $preferencesFile" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Failed to parse preferences, using defaults" }
            UserPreferences.DEFAULT
        }
    }

    override suspend fun savePreferences(preferences: UserPreferences): Boolean {
        logger.d { "Saving preferences to: $preferencesFile" }

        return try {
            val content = json.encodeToString(preferences)
            if (!writeFileAtomically(preferencesFile, content)) {
                logger.e { "Failed to write preferences file" }
                return false
            }
            logger.i { "Preferences saved successfully" }
            true
        } catch (e: Exception) {
            logger.e(e) { "Failed to save preferences" }
            false
        }
    }

    override fun getPreferencesPath(): String = preferencesFile

    override suspend fun preferencesExist(): Boolean = fileExists(preferencesFile)

    override suspend fun resetToDefaults(): Boolean {
        return savePreferences(UserPreferences.DEFAULT)
    }

    // Helper methods

    private fun getExecutableDirectory(): String = memScoped {
        val buffer = allocArray<ByteVar>(MAX_PATH)
        val length = GetModuleFileNameA(null, buffer, MAX_PATH.toUInt())

        if (length > 0u) {
            val executablePath = buffer.toKString()
            // Return the directory containing the executable
            executablePath.substringBeforeLast('\\', missingDelimiterValue = executablePath)
        } else {
            // Fallback to current directory
            "."
        }
    }

    private fun fileExists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    private fun readFile(path: String): String {
        val file = fopen(path, "r") ?: return ""
        try {
            val content = StringBuilder()
            memScoped {
                val buffer = allocArray<ByteVar>(4096)
                while (fgets(buffer, 4096, file) != null) {
                    content.append(buffer.toKString())
                }
            }
            return content.toString()
        } finally {
            fclose(file)
        }
    }

    /**
     * Write [content] to [path] atomically: create the parent directory if
     * needed, write to a temp file (checking every step), then replace the
     * target with MoveFileEx. Returns false — without touching the existing
     * file — on any failure, so a full disk or permission error can't leave a
     * truncated settings.json (which would load as defaults).
     */
    private fun writeFileAtomically(path: String, content: String): Boolean {
        val dir = path.substringBeforeLast('\\', missingDelimiterValue = "")
        if (dir.isNotEmpty() && access(dir, F_OK) != 0) {
            mkdir(dir)
        }

        val tempPath = "$path.tmp"
        val file = fopen(tempPath, "w") ?: return false
        var ok = true
        try {
            if (fputs(content, file) < 0) ok = false
        } finally {
            if (fclose(file) != 0) ok = false
        }

        if (!ok) {
            remove(tempPath)
            return false
        }

        // Atomically move the fully-written temp file over the target.
        if (MoveFileExA(tempPath, path, MOVEFILE_REPLACE_EXISTING.toUInt()) == 0) {
            remove(tempPath)
            return false
        }
        return true
    }
}
