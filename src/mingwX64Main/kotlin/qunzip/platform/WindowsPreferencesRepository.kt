package qunzip.platform

import qunzip.domain.entities.UserPreferences
import qunzip.domain.repositories.PreferencesRepository
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

    private val preferencesFile: String by lazy {
        // Store settings.json in the same directory as the executable
        val executableDir = getExecutableDirectory()
        "$executableDir\\settings.json"
    }

    override suspend fun loadPreferences(): UserPreferences {
        logger.d { "Loading preferences from: $preferencesFile" }

        if (!fileExists(preferencesFile)) {
            logger.d { "No preferences file found, using defaults" }
            return UserPreferences.DEFAULT
        }

        return try {
            val content = readFile(preferencesFile)
            if (content.isBlank()) {
                logger.w { "Preferences file is empty, using defaults" }
                return UserPreferences.DEFAULT
            }
            json.decodeFromString<UserPreferences>(content).also {
                logger.d { "Loaded preferences: $it" }
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
            writeFile(preferencesFile, content)
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

    private fun writeFile(path: String, content: String): Boolean {
        val file = fopen(path, "w") ?: return false
        try {
            fputs(content, file)
            return true
        } finally {
            fclose(file)
        }
    }
}
