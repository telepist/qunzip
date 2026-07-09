package qunzip.data.prefs

/**
 * Pure decisions about where the preferences file lives and which one to load.
 * Separated from the syscall/JSON machinery in WindowsPreferencesRepository so
 * the directory selection and legacy-migration precedence are unit-testable
 * without a real filesystem or environment.
 */
object PreferencesPaths {
    const val FILE_NAME = "settings.json"
    private const val APPDATA_SUBDIR = "QuickUnzip"

    /**
     * Directory that holds the per-user preferences file, or [exeDir] when
     * APPDATA is unavailable (unusual / portable use).
     */
    fun preferencesDirectory(appData: String?, exeDir: String): String =
        if (!appData.isNullOrBlank()) "$appData\\$APPDATA_SUBDIR" else exeDir

    fun preferencesFile(dir: String): String = "$dir\\$FILE_NAME"

    /**
     * Which existing file to load: the per-user file if present, else the legacy
     * exe-dir file (for migration), else null (use defaults).
     */
    fun chooseLoadPath(
        newPath: String,
        legacyPath: String,
        newExists: Boolean,
        legacyExists: Boolean,
    ): String? = when {
        newExists -> newPath
        legacyExists -> legacyPath
        else -> null
    }
}
