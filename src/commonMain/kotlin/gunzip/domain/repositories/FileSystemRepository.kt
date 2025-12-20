package gunzip.domain.repositories

import gunzip.domain.usecases.FileInfo

interface FileSystemRepository {

    /**
     * Check if file or directory exists
     */
    suspend fun exists(path: String): Boolean

    /**
     * Check if path is readable
     */
    suspend fun isReadable(path: String): Boolean

    /**
     * Check if path is writable
     */
    suspend fun isWritable(path: String): Boolean

    /**
     * Get file information
     */
    suspend fun getFileInfo(path: String): FileInfo

    /**
     * Get parent directory of a file path
     */
    fun getParentDirectory(filePath: String): String

    /**
     * Join path components in a platform-appropriate way
     */
    fun joinPath(vararg components: String): String

    /**
     * Create directory (including parent directories if needed)
     */
    suspend fun createDirectory(path: String): Boolean

    /**
     * Get available disk space at given path (in bytes)
     */
    suspend fun getAvailableSpace(path: String): Long

    /**
     * Move file to platform-appropriate trash/recycle bin
     */
    suspend fun moveToTrash(filePath: String): Boolean

    /**
     * Get platform-specific trash/recycle bin path
     */
    suspend fun getTrashPath(): String?

    /**
     * List files in a directory
     */
    suspend fun listFiles(directoryPath: String): List<FileInfo>

    /**
     * Copy file from source to destination
     */
    suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean

    /**
     * Move/rename file
     */
    suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean

    /**
     * Delete file
     */
    suspend fun deleteFile(path: String): Boolean

    /**
     * Get file size in bytes
     */
    suspend fun getFileSize(path: String): Long

    /**
     * Normalize path to platform-specific format
     */
    fun normalizePath(path: String): String

    /**
     * Get absolute path
     */
    fun getAbsolutePath(path: String): String

    /**
     * Check if path is absolute
     */
    fun isAbsolutePath(path: String): Boolean

    /**
     * Get file extension (without dot)
     */
    fun getFileExtension(path: String): String

    /**
     * Get filename without extension
     */
    fun getFilenameWithoutExtension(path: String): String

    /**
     * Get filename with extension
     */
    fun getFilename(path: String): String
}