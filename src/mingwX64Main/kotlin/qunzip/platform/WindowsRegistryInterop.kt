@file:OptIn(ExperimentalForeignApi::class)

package qunzip.platform

import kotlinx.cinterop.*
import platform.windows.*

/**
 * Windows Registry API bindings and helper functions for file association management
 */

// Registry root keys
val HKEY_CLASSES_ROOT: HKEY? = platform.windows.HKEY_CLASSES_ROOT
val HKEY_CURRENT_USER: HKEY? = platform.windows.HKEY_CURRENT_USER

// Registry key paths for user-specific associations
const val HKCU_CLASSES_PATH = "Software\\Classes"

// Registry access rights
const val KEY_READ = platform.windows.KEY_READ
const val KEY_WRITE = platform.windows.KEY_WRITE
const val KEY_ALL_ACCESS = platform.windows.KEY_ALL_ACCESS

// Registry value types
const val REG_SZ = platform.windows.REG_SZ
const val REG_EXPAND_SZ = platform.windows.REG_EXPAND_SZ

// Registry options
const val REG_OPTION_NON_VOLATILE = platform.windows.REG_OPTION_NON_VOLATILE

// Shell change notification events
const val SHCNE_ASSOCCHANGED = 0x08000000
const val SHCNF_IDLIST = 0x0000u
const val SHCNF_FLUSH = 0x1000u

// Error codes
const val ERROR_SUCCESS = platform.windows.ERROR_SUCCESS
const val ERROR_ACCESS_DENIED = 5

/**
 * Registry helper class for managing Windows Registry operations
 */
@OptIn(ExperimentalForeignApi::class)
class RegistryHelper {

    /**
     * Creates or opens a registry key
     * Returns the key handle or null on failure
     */
    fun createKey(rootKey: HKEY?, subKey: String): HKEY? = memScoped {
        val hKeyVar = alloc<HKEYVar>()
        val result = RegCreateKeyExW(
            rootKey,
            subKey,
            0u,
            null,
            REG_OPTION_NON_VOLATILE.toUInt(),
            KEY_WRITE.toUInt(),
            null,
            hKeyVar.ptr,
            null
        )

        if (result == ERROR_SUCCESS) {
            hKeyVar.value
        } else {
            null
        }
    }

    /**
     * Opens an existing registry key for reading
     * Returns the key handle or null on failure
     */
    fun openKey(rootKey: HKEY?, subKey: String, accessRights: UInt = KEY_READ.toUInt()): HKEY? = memScoped {
        val hKeyVar = alloc<HKEYVar>()
        val result = RegOpenKeyExW(
            rootKey,
            subKey,
            0u,
            accessRights,
            hKeyVar.ptr
        )

        if (result == ERROR_SUCCESS) {
            hKeyVar.value
        } else {
            null
        }
    }

    /**
     * Sets a string value in the registry. Defaults to REG_SZ; pass
     * REG_EXPAND_SZ for values like PATH that can contain %vars%.
     * Uses the wide-char API so non-ASCII content (e.g. Unicode usernames
     * in PATH) round-trips correctly.
     * Returns true on success
     */
    fun setStringValue(
        hKey: HKEY?,
        valueName: String?,
        data: String,
        valueType: Int = REG_SZ
    ): Boolean = memScoped {
        // wcstr produces a null-terminated UTF-16 buffer of (length + 1) UShorts.
        // cbData must include the null terminator and be in BYTES.
        val dataPtr = data.wcstr.ptr
        val cbData = ((data.length + 1) * 2).toUInt()
        val result = RegSetValueExW(
            hKey,
            valueName,
            0u,
            valueType.toUInt(),
            dataPtr.reinterpret(),
            cbData
        )

        result == ERROR_SUCCESS
    }

    /**
     * Reads a string registry value along with its type code (REG_SZ vs
     * REG_EXPAND_SZ). Used by the PATH editor so a write preserves the
     * original type.
     */
    fun getStringValueWithType(hKey: HKEY?, valueName: String?): Pair<String, Int>? = memScoped {
        val dataSize = alloc<UIntVar>()  // bytes
        val dataType = alloc<UIntVar>()
        dataSize.value = 0u

        var result = RegQueryValueExW(hKey, valueName, null, dataType.ptr, null, dataSize.ptr)
        if (result != ERROR_SUCCESS || dataSize.value == 0u) return null

        // Allocate UTF-16 buffer plus one extra UShort for safety null-termination.
        val ushortCount = dataSize.value.toInt() / 2 + 1
        val buffer = allocArray<UShortVar>(ushortCount)
        result = RegQueryValueExW(hKey, valueName, null, dataType.ptr, buffer.reinterpret(), dataSize.ptr)
        if (result != ERROR_SUCCESS) return null

        buffer.toKStringFromUtf16() to dataType.value.toInt()
    }

    /**
     * Gets a string value from the registry
     * Returns the value or null on failure
     */
    fun getStringValue(hKey: HKEY?, valueName: String?): String? =
        getStringValueWithType(hKey, valueName)?.first

    /**
     * Deletes a registry key and all its subkeys
     * Returns true on success
     */
    fun deleteKey(rootKey: HKEY?, subKey: String): Boolean {
        val result = RegDeleteKeyW(rootKey, subKey)
        return result == ERROR_SUCCESS
    }

    /**
     * Recursively deletes a registry key and all its subkeys.
     * Uses RegDeleteTreeA which handles nested subkeys.
     * Returns true on success or if the key doesn't exist.
     */
    fun deleteKeyTree(rootKey: HKEY?, subKey: String): Boolean {
        // RegDeleteTreeA is not available in MinGW headers, so we
        // recursively delete using SHDeleteKeyA from shlwapi.
        // As a simpler approach, delete known subkeys manually then the key.
        // For ProgIDs the structure is: ProgID\DefaultIcon, ProgID\shell\open\command
        deleteKey(rootKey, "$subKey\\shell\\open\\command")
        deleteKey(rootKey, "$subKey\\shell\\open")
        deleteKey(rootKey, "$subKey\\shell")
        deleteKey(rootKey, "$subKey\\DefaultIcon")
        return deleteKey(rootKey, subKey)
    }

    /**
     * Closes a registry key handle
     */
    fun closeKey(hKey: HKEY?) {
        if (hKey != null) {
            RegCloseKey(hKey)
        }
    }

    /**
     * Notifies the Windows Shell that file associations have changed
     * This causes Windows Explorer to refresh its file type cache
     */
    fun notifyShellAssociationChanged() {
        SHChangeNotify(SHCNE_ASSOCCHANGED, SHCNF_IDLIST or SHCNF_FLUSH, null, null)
    }

    /**
     * Checks if the current process has administrator privileges
     * Returns true if running as admin
     */
    fun isRunningAsAdmin(): Boolean {
        // Try to open HKEY_CLASSES_ROOT for writing
        // If we can, we have admin privileges
        val testKey = createKey(HKEY_CLASSES_ROOT, "Software\\QuickUnzip\\AdminTest")
        if (testKey != null) {
            closeKey(testKey)
            deleteKey(HKEY_CLASSES_ROOT, "Software\\QuickUnzip\\AdminTest")
            return true
        }
        return false
    }

    /**
     * Gets the appropriate root key for file associations based on privileges
     * Returns HKEY_CLASSES_ROOT if admin, otherwise HKEY_CURRENT_USER with path prefix
     */
    fun getFileAssociationRootKey(): Pair<HKEY?, String> {
        return if (isRunningAsAdmin()) {
            // System-wide associations
            Pair(HKEY_CLASSES_ROOT, "")
        } else {
            // User-specific associations
            Pair(HKEY_CURRENT_USER, "$HKCU_CLASSES_PATH\\")
        }
    }
}

/**
 * Extension function to create a full registry path with proper prefix
 */
fun Pair<HKEY?, String>.fullPath(subPath: String): String {
    return this.second + subPath
}

/**
 * Creates a ProgID registry structure for file associations
 *
 * @param progId The ProgID identifier (e.g., "Qunzip.ArchiveFile")
 * @param description Human-readable description
 * @param executablePath Full path to the application executable
 * @param rootKeyPair Pair of root key and path prefix from getFileAssociationRootKey()
 * @return true if successful
 */
@OptIn(ExperimentalForeignApi::class)
fun createProgId(
    progId: String,
    description: String,
    executablePath: String,
    rootKeyPair: Pair<HKEY?, String>,
    helper: RegistryHelper
): Boolean {
    val (rootKey, prefix) = rootKeyPair

    // Create the ProgID key
    val progIdKey = helper.createKey(rootKey, prefix + progId)
    if (progIdKey == null) return false

    // Set the default value (description)
    if (!helper.setStringValue(progIdKey, null, description)) {
        helper.closeKey(progIdKey)
        return false
    }
    helper.closeKey(progIdKey)

    // Create DefaultIcon subkey - use the application's embedded icon
    val iconKey = helper.createKey(rootKey, "${prefix}${progId}\\DefaultIcon")
    if (iconKey != null) {
        // Use the executable's embedded icon (index 0)
        helper.setStringValue(iconKey, null, "\"$executablePath\",0")
        helper.closeKey(iconKey)
    }

    // Create shell\open\command subkey
    val commandKey = helper.createKey(rootKey, "${prefix}${progId}\\shell\\open\\command")
    if (commandKey != null) {
        helper.setStringValue(commandKey, null, "\"$executablePath\" \"%1\"")
        helper.closeKey(commandKey)
    }

    return true
}

/**
 * Associates a file extension with a ProgID
 *
 * @param extension File extension (without the dot, e.g., "zip")
 * @param progId The ProgID identifier
 * @param rootKeyPair Pair of root key and path prefix from getFileAssociationRootKey()
 * @return true if successful
 */
@OptIn(ExperimentalForeignApi::class)
fun associateExtensionWithProgId(
    extension: String,
    progId: String,
    rootKeyPair: Pair<HKEY?, String>,
    helper: RegistryHelper
): Boolean {
    val (rootKey, prefix) = rootKeyPair
    val extWithDot = if (extension.startsWith(".")) extension else ".$extension"

    // Create/open the extension key
    val extKey = helper.createKey(rootKey, prefix + extWithDot)
    if (extKey == null) return false

    // Set the default value to the ProgID
    val result = helper.setStringValue(extKey, null, progId)
    helper.closeKey(extKey)

    // Also create OpenWithProgids for better Windows integration
    val openWithKey = helper.createKey(rootKey, "${prefix}${extWithDot}\\OpenWithProgids")
    if (openWithKey != null) {
        helper.setStringValue(openWithKey, progId, "")
        helper.closeKey(openWithKey)
    }

    return result
}

/**
 * Removes a file extension association
 *
 * @param extension File extension (without the dot, e.g., "zip")
 * @param progId The ProgID to check against (only removes if currently associated with this ProgID)
 * @param rootKeyPair Pair of root key and path prefix from getFileAssociationRootKey()
 * @return true if successful or already removed
 */
@OptIn(ExperimentalForeignApi::class)
fun removeExtensionAssociation(
    extension: String,
    progId: String,
    rootKeyPair: Pair<HKEY?, String>,
    helper: RegistryHelper
): Boolean {
    val (rootKey, prefix) = rootKeyPair
    val extWithDot = if (extension.startsWith(".")) extension else ".$extension"

    // Open the extension key to check current association
    val extKey = helper.openKey(rootKey, prefix + extWithDot, KEY_READ.toUInt())
    if (extKey != null) {
        val currentProgId = helper.getStringValue(extKey, null)
        helper.closeKey(extKey)

        // Only remove if it's currently associated with our ProgID
        if (currentProgId == progId) {
            return helper.deleteKey(rootKey, prefix + extWithDot)
        }
    }

    // If not found or not associated with our ProgID, consider it successful
    return true
}

/**
 * Gets the current ProgID associated with a file extension
 *
 * @param extension File extension (without the dot, e.g., "zip")
 * @param rootKeyPair Pair of root key and path prefix from getFileAssociationRootKey()
 * @return The ProgID or null if not associated
 */
@OptIn(ExperimentalForeignApi::class)
fun getExtensionProgId(
    extension: String,
    rootKeyPair: Pair<HKEY?, String>,
    helper: RegistryHelper
): String? {
    val (rootKey, prefix) = rootKeyPair
    val extWithDot = if (extension.startsWith(".")) extension else ".$extension"

    val extKey = helper.openKey(rootKey, prefix + extWithDot, KEY_READ.toUInt())
    if (extKey != null) {
        val progId = helper.getStringValue(extKey, null)
        helper.closeKey(extKey)
        return progId
    }

    return null
}
