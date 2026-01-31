@file:OptIn(ExperimentalForeignApi::class)

package gunzip.platform

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
        val result = RegCreateKeyExA(
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
        val result = RegOpenKeyExA(
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
     * Sets a string value in the registry
     * Returns true on success
     */
    fun setStringValue(hKey: HKEY?, valueName: String?, data: String): Boolean = memScoped {
        val dataBytes = data.encodeToByteArray().toUByteArray()
        val result = RegSetValueExA(
            hKey,
            valueName,
            0u,
            REG_SZ.toUInt(),
            dataBytes.refTo(0),
            (dataBytes.size + 1).toUInt() // Include null terminator
        )

        result == ERROR_SUCCESS
    }

    /**
     * Gets a string value from the registry
     * Returns the value or null on failure
     */
    fun getStringValue(hKey: HKEY?, valueName: String?): String? = memScoped {
        val dataSize = alloc<UIntVar>()
        dataSize.value = 0u

        // First call to get the size
        var result = RegQueryValueExA(
            hKey,
            valueName,
            null,
            null,
            null,
            dataSize.ptr
        )

        if (result != ERROR_SUCCESS || dataSize.value == 0u) {
            return null
        }

        // Allocate buffer and get the actual value
        val buffer = allocArray<UByteVar>(dataSize.value.toInt())
        result = RegQueryValueExA(
            hKey,
            valueName,
            null,
            null,
            buffer,
            dataSize.ptr
        )

        if (result == ERROR_SUCCESS) {
            buffer.reinterpret<ByteVar>().toKString()
        } else {
            null
        }
    }

    /**
     * Deletes a registry key and all its subkeys
     * Returns true on success
     */
    fun deleteKey(rootKey: HKEY?, subKey: String): Boolean {
        val result = RegDeleteKeyA(rootKey, subKey)
        return result == ERROR_SUCCESS
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
        val testKey = createKey(HKEY_CLASSES_ROOT, "Software\\Gunzip\\AdminTest")
        if (testKey != null) {
            closeKey(testKey)
            deleteKey(HKEY_CLASSES_ROOT, "Software\\Gunzip\\AdminTest")
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
 * @param progId The ProgID identifier (e.g., "Gunzip.ArchiveFile")
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
