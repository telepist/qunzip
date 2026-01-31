package gunzip.platform

import gunzip.domain.repositories.FileSystemRepository
import gunzip.domain.usecases.FileInfo
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*
import co.touchlab.kermit.Logger

/**
 * Windows implementation of FileSystemRepository
 * Uses Windows API for trash operations and POSIX for file operations
 */
@OptIn(ExperimentalForeignApi::class)
class WindowsFileSystemRepository(
    private val logger: Logger = Logger.withTag("WindowsFileSystemRepository")
) : FileSystemRepository {

    override suspend fun exists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    override suspend fun isReadable(path: String): Boolean {
        return access(path, R_OK) == 0
    }

    override suspend fun isWritable(path: String): Boolean {
        return access(path, W_OK) == 0
    }

    override suspend fun getFileInfo(path: String): FileInfo = memScoped {
        val statBuf = alloc<stat>()
        if (stat(path, statBuf.ptr) != 0) {
            throw Exception("Failed to get file info for: $path")
        }

        FileInfo(
            path = path,
            size = statBuf.st_size.toLong(),
            lastModified = kotlinx.datetime.Instant.fromEpochSeconds(statBuf.st_mtime.toLong()),
            isReadable = isReadable(path),
            isDirectory = (statBuf.st_mode.toInt() and S_IFDIR) != 0
        )
    }

    override fun getParentDirectory(filePath: String): String {
        // Handle both forward and backslashes
        val normalizedPath = filePath.replace('/', '\\')
        val lastSeparator = normalizedPath.lastIndexOf('\\')

        return if (lastSeparator > 0) {
            normalizedPath.substring(0, lastSeparator)
        } else {
            "."
        }
    }

    override fun joinPath(vararg components: String): String {
        return components.joinToString("\\")
    }

    override suspend fun createDirectory(path: String): Boolean {
        logger.d { "Creating directory: $path" }

        // Create directory including all parent directories
        return createDirectoryRecursive(path)
    }

    private fun createDirectoryRecursive(path: String): Boolean {
        // Check exists without suspend
        if (access(path, F_OK) == 0) {
            return true
        }

        // Create parent directory first
        val parent = getParentDirectory(path)
        if (parent != "." && parent != path && access(parent, F_OK) != 0) {
            if (!createDirectoryRecursive(parent)) {
                return false
            }
        }

        // Create this directory
        return mkdir(path) == 0
    }

    override suspend fun getAvailableSpace(path: String): Long {
        logger.d { "Getting available space for: $path" }

        // For now, return a large value (proper implementation requires Windows-specific structs)
        // This is a simplified version
        logger.w { "Disk space check not fully implemented, returning large default" }
        return Long.MAX_VALUE
    }

    override suspend fun moveToTrash(filePath: String): Boolean {
        logger.i { "Moving to Recycle Bin: $filePath" }

        return try {
            // Use SHFileOperation to move to recycle bin
            memScoped {
                val fileOp = alloc<SHFILEOPSTRUCTA>()

                // Double-null terminated string
                val pathBuffer = "$filePath\u0000\u0000".encodeToByteArray()

                fileOp.hwnd = null
                fileOp.wFunc = FO_DELETE.toUInt()
                fileOp.pFrom = pathBuffer.refTo(0).getPointer(this)
                fileOp.pTo = null
                fileOp.fFlags = (FOF_ALLOWUNDO or FOF_NOCONFIRMATION or FOF_SILENT).toUShort()
                fileOp.fAnyOperationsAborted = 0
                fileOp.hNameMappings = null
                fileOp.lpszProgressTitle = null

                val result = SHFileOperationA(fileOp.ptr)

                if (result == 0) {
                    logger.i { "Successfully moved to Recycle Bin: $filePath" }
                    true
                } else {
                    logger.e { "Failed to move to Recycle Bin: $filePath (error code: $result)" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.e(e) { "Exception moving to trash: $filePath" }
            false
        }
    }

    override suspend fun getTrashPath(): String? {
        // Windows doesn't have a single trash path - it's per-drive
        return null
    }

    override suspend fun listFiles(directoryPath: String): List<FileInfo> {
        logger.d { "Listing files in: $directoryPath" }

        val files = mutableListOf<FileInfo>()

        memScoped {
            val findData = alloc<_WIN32_FIND_DATAA>()
            val searchPath = "$directoryPath\\*"

            val handle = FindFirstFileA(searchPath, findData.ptr)

            if (handle == INVALID_HANDLE_VALUE) {
                logger.w { "Failed to list directory: $directoryPath" }
                return emptyList()
            }

            try {
                do {
                    val fileName = findData.cFileName.toKString()

                    // Skip . and ..
                    if (fileName != "." && fileName != "..") {
                        val fullPath = joinPath(directoryPath, fileName)
                        val isDirectory = (findData.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u

                        files.add(
                            FileInfo(
                                path = fullPath,
                                size = ((findData.nFileSizeHigh.toLong() shl 32) or findData.nFileSizeLow.toLong()),
                                isReadable = true,
                                isDirectory = isDirectory
                            )
                        )
                    }
                } while (FindNextFileA(handle, findData.ptr) != 0)
            } finally {
                FindClose(handle)
            }
        }

        return files
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String): Boolean {
        logger.d { "Copying file: $sourcePath to $destinationPath" }
        val result = CopyFileA(sourcePath, destinationPath, 0)
        return result != 0
    }

    override suspend fun moveFile(sourcePath: String, destinationPath: String): Boolean {
        logger.d { "Moving file: $sourcePath to $destinationPath" }
        val result = MoveFileA(sourcePath, destinationPath)
        return result != 0
    }

    override suspend fun deleteFile(path: String): Boolean {
        logger.d { "Deleting file: $path" }
        val result = DeleteFileA(path)
        return result != 0
    }

    override suspend fun deleteDirectory(path: String): Boolean {
        logger.d { "Deleting directory: $path" }
        val result = RemoveDirectoryA(path)
        return result != 0
    }

    override suspend fun getFileSize(path: String): Long {
        return getFileInfo(path).size
    }

    override fun normalizePath(path: String): String {
        return path.replace('/', '\\')
    }

    override fun getAbsolutePath(path: String): String {
        memScoped {
            val buffer = allocArray<ByteVar>(MAX_PATH)
            val result = GetFullPathNameA(path, MAX_PATH.toUInt(), buffer, null)

            return if (result > 0u) {
                buffer.toKString()
            } else {
                path
            }
        }
    }

    override fun isAbsolutePath(path: String): Boolean {
        // Windows absolute paths: C:\... or \\server\share
        return (path.length >= 3 && path[1] == ':' && path[2] == '\\') ||
                (path.startsWith("\\\\"))
    }

    override fun getFileExtension(path: String): String {
        val filename = getFilename(path)
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot > 0) {
            filename.substring(lastDot + 1)
        } else {
            ""
        }
    }

    override fun getFilenameWithoutExtension(path: String): String {
        val filename = getFilename(path)
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot > 0) {
            filename.substring(0, lastDot)
        } else {
            filename
        }
    }

    override fun getFilename(path: String): String {
        val normalizedPath = path.replace('/', '\\')
        return normalizedPath.substringAfterLast('\\')
    }
}
