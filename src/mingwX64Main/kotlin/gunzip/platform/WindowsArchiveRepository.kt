package gunzip.platform

import gunzip.domain.entities.*
import gunzip.domain.repositories.ArchiveRepository
import gunzip.domain.usecases.FileInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*
import co.touchlab.kermit.Logger

/**
 * Windows implementation of ArchiveRepository using 7zip command-line tool
 * Uses bundled 7z.exe from bin/7zip/ directory relative to the executable
 *
 * Note: Uses 7z.exe (requires 7z.dll) for full format support including RAR
 */
@OptIn(ExperimentalForeignApi::class)
class WindowsArchiveRepository(
    private val sevenZipPath: String = getBundled7zipPath(),
    private val logger: Logger = Logger.withTag("WindowsArchiveRepository")
) : ArchiveRepository {

    init {
        logger.i { "Using 7-Zip at: $sevenZipPath" }
    }

    override suspend fun getArchiveInfo(archivePath: String): Archive? {
        logger.d { "Getting archive info for: $archivePath" }

        // Check if file exists
        if (!fileExists(archivePath)) {
            logger.w { "Archive file not found: $archivePath" }
            return null
        }

        // Get file size and modification time
        val fileInfo = getFileStats(archivePath) ?: return null

        // Extract filename and detect format
        val filename = archivePath.substringAfterLast('\\').substringAfterLast('/')
        val format = ArchiveFormat.fromFilename(filename) ?: return null

        return Archive(
            path = archivePath,
            name = filename,
            format = format,
            size = fileInfo.size,
            lastModified = fileInfo.lastModified
        )
    }

    override suspend fun getArchiveContents(archivePath: String): ArchiveContents {
        logger.d { "Analyzing archive contents: $archivePath" }

        // Execute 7zip list command: 7z l -slt archive.zip
        val output = execute7zipCommand(listOf("l", "-slt", archivePath))

        // Parse 7zip output to extract file entries
        val entries = parse7zipListOutput(output)

        val totalSize = entries.sumOf { it.size }
        val totalCompressedSize = entries.mapNotNull { it.compressedSize }.sum()

        return ArchiveContents(
            entries = entries,
            totalSize = totalSize,
            totalCompressedSize = if (totalCompressedSize > 0) totalCompressedSize else null
        )
    }

    override suspend fun testArchive(archivePath: String): Boolean {
        logger.d { "Testing archive integrity: $archivePath" }

        return try {
            // Execute 7zip test command: 7z t archive.zip
            val exitCode = execute7zipTest(archivePath)
            val isValid = exitCode == 0

            if (isValid) {
                logger.i { "Archive test passed: $archivePath" }
            } else {
                logger.w { "Archive test failed with exit code: $exitCode" }
            }

            isValid
        } catch (e: Exception) {
            logger.e(e) { "Error testing archive: $archivePath" }
            false
        }
    }

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String
    ): Flow<ExtractionProgress> = flow {
        logger.i { "Extracting archive: $archivePath to $destinationPath" }

        emit(ExtractionProgress(archivePath, stage = ExtractionStage.STARTING))

        try {
            // Get archive info for progress tracking
            val contents = getArchiveContents(archivePath)

            emit(ExtractionProgress(
                archivePath = archivePath,
                totalFiles = contents.fileCount,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.EXTRACTING
            ))

            // Execute 7zip extraction: 7z x archive.zip -o"destination"
            val exitCode = execute7zipExtract(archivePath, destinationPath)

            if (exitCode != 0) {
                throw ExtractionError.SevenZipError(exitCode, "7zip extraction failed")
            }

            emit(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = contents.fileCount,
                totalFiles = contents.fileCount,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.COMPLETED
            ))

            logger.i { "Extraction completed successfully" }

        } catch (e: ExtractionError) {
            logger.e { "Extraction failed: ${e.message}" }
            emit(ExtractionProgress(archivePath, stage = ExtractionStage.FAILED))
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Unexpected error during extraction" }
            emit(ExtractionProgress(archivePath, stage = ExtractionStage.FAILED))
            throw ExtractionError.UnknownError(e.message ?: "Unknown error", e)
        }
    }

    override fun isFormatSupported(format: ArchiveFormat): Boolean {
        // 7zip supports all our defined formats
        return true
    }

    override fun getSupportedFormats(): List<ArchiveFormat> {
        return ArchiveFormat.values().toList()
    }

    override suspend fun isPasswordRequired(archivePath: String): Boolean {
        logger.d { "Checking if password required: $archivePath" }

        // Try to test the archive; if it fails with password error, return true
        // For now, return false (password support is a future feature)
        return false
    }

    override suspend fun extractPasswordProtectedArchive(
        archivePath: String,
        destinationPath: String,
        password: String
    ): Flow<ExtractionProgress> = flow {
        // Future feature - not yet implemented
        throw ExtractionError.PasswordRequired("Password-protected archives not yet supported")
    }

    // Private helper methods

    private fun fileExists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    private fun getFileStats(path: String): FileInfo? = memScoped {
        val statBuf = alloc<stat>()
        if (stat(path, statBuf.ptr) != 0) {
            return null
        }

        FileInfo(
            path = path,
            size = statBuf.st_size.toLong(),
            lastModified = kotlinx.datetime.Instant.fromEpochSeconds(statBuf.st_mtime.toLong()),
            isReadable = true,
            isDirectory = (statBuf.st_mode.toInt() and S_IFDIR) != 0
        )
    }

    /**
     * Execute 7zip command and capture output without showing a console window
     * Uses CreateProcessW with CREATE_NO_WINDOW and pipes for stdout
     */
    private fun execute7zipCommand(args: List<String>): String = memScoped {
        logger.d { "Executing 7zip command: $sevenZipPath ${args.joinToString(" ")}" }

        val command = "$sevenZipPath ${args.joinToString(" ")}"

        // Create pipe for stdout
        val securityAttrs = alloc<SECURITY_ATTRIBUTES>()
        securityAttrs.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
        securityAttrs.bInheritHandle = TRUE
        securityAttrs.lpSecurityDescriptor = null

        val stdoutReadHandle = alloc<HANDLEVar>()
        val stdoutWriteHandle = alloc<HANDLEVar>()

        if (CreatePipe(stdoutReadHandle.ptr, stdoutWriteHandle.ptr, securityAttrs.ptr, 0u) == 0) {
            throw ExtractionError.IOError("Failed to create pipe for 7zip output")
        }

        // Ensure the read handle is not inherited
        SetHandleInformation(stdoutReadHandle.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)

        val startupInfo = alloc<STARTUPINFOW>()
        val processInfo = alloc<PROCESS_INFORMATION>()

        startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
        startupInfo.dwFlags = (STARTF_USESHOWWINDOW or STARTF_USESTDHANDLES).toUInt()
        startupInfo.wShowWindow = SW_HIDE.toUShort()
        startupInfo.hStdOutput = stdoutWriteHandle.value
        startupInfo.hStdError = stdoutWriteHandle.value
        startupInfo.hStdInput = null

        val success = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = command.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = TRUE,
            dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = startupInfo.ptr,
            lpProcessInformation = processInfo.ptr
        )

        // Close write end of pipe in parent process
        CloseHandle(stdoutWriteHandle.value)

        if (success == 0) {
            CloseHandle(stdoutReadHandle.value)
            throw ExtractionError.IOError("Failed to execute 7zip command: ${GetLastError()}")
        }

        // Read output from pipe
        val output = StringBuilder()
        val buffer = allocArray<ByteVar>(4096)
        val bytesRead = alloc<UIntVar>()

        while (true) {
            val readSuccess = ReadFile(
                stdoutReadHandle.value,
                buffer,
                4095u,
                bytesRead.ptr,
                null
            )
            if (readSuccess == 0 || bytesRead.value == 0u) break
            buffer[bytesRead.value.toInt()] = 0
            output.append(buffer.toKString())
        }

        // Wait for process and cleanup
        WaitForSingleObject(processInfo.hProcess, INFINITE)
        CloseHandle(processInfo.hProcess)
        CloseHandle(processInfo.hThread)
        CloseHandle(stdoutReadHandle.value)

        return@memScoped output.toString()
    }

    private fun execute7zipTest(archivePath: String): Int {
        val command = "$sevenZipPath t \"$archivePath\""
        return executeCommandSilently(command)
    }

    private fun execute7zipExtract(archivePath: String, destinationPath: String): Int {
        // Use -o flag for output directory (no space between -o and path)
        val command = "$sevenZipPath x \"$archivePath\" -o\"$destinationPath\" -y"
        logger.d { "Extraction command: $command" }
        return executeCommandSilently(command)
    }

    /**
     * Execute a command silently without showing a console window
     * Uses CreateProcessW with CREATE_NO_WINDOW flag
     * Returns the exit code
     */
    private fun executeCommandSilently(command: String): Int = memScoped {
        val startupInfo = alloc<STARTUPINFOW>()
        val processInfo = alloc<PROCESS_INFORMATION>()

        // Initialize startup info
        startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
        startupInfo.dwFlags = STARTF_USESHOWWINDOW.toUInt()
        startupInfo.wShowWindow = SW_HIDE.toUShort()

        // Create process with no window
        val success = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = command.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = FALSE,
            dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = startupInfo.ptr,
            lpProcessInformation = processInfo.ptr
        )

        if (success == 0) {
            logger.e { "Failed to create process: ${GetLastError()}" }
            return@memScoped -1
        }

        // Wait for process to complete
        WaitForSingleObject(processInfo.hProcess, INFINITE)

        // Get exit code
        val exitCode = alloc<UIntVar>()
        GetExitCodeProcess(processInfo.hProcess, exitCode.ptr)

        // Close handles
        CloseHandle(processInfo.hProcess)
        CloseHandle(processInfo.hThread)

        return@memScoped exitCode.value.toInt()
    }

    private fun parse7zipListOutput(output: String): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        val lines = output.split("\n")

        // Skip until we find the "----------" separator that marks the start of entries
        var i = 0
        var foundSeparator = false
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("----------")) {
                foundSeparator = true
                i++
                break
            }
            i++
        }

        if (!foundSeparator) {
            logger.w { "Could not find entry separator in 7zip output" }
            return emptyList()
        }

        // Now parse actual entry blocks
        while (i < lines.size) {
            val line = lines[i].trim()

            // Look for entry blocks starting with "Path = "
            if (line.startsWith("Path = ")) {
                val entry = parseEntryBlock(lines, i)
                if (entry != null) {
                    entries.add(entry)
                }
            }
            i++
        }

        return entries
    }

    private fun parseEntryBlock(lines: List<String>, startIndex: Int): ArchiveEntry? {
        var path: String? = null
        var isDirectory = false
        var size: Long = 0
        var compressedSize: Long? = null

        var i = startIndex
        while (i < lines.size && lines[i].trim().isNotEmpty()) {
            val line = lines[i].trim()

            when {
                line.startsWith("Path = ") -> {
                    path = line.substring(7).trim()
                }
                line.startsWith("Folder = ") -> {
                    isDirectory = line.substring(9).trim() == "+"
                }
                line.startsWith("Size = ") -> {
                    size = line.substring(7).trim().toLongOrNull() ?: 0
                }
                line.startsWith("Packed Size = ") -> {
                    compressedSize = line.substring(14).trim().toLongOrNull()
                }
            }

            i++
        }

        return if (path != null) {
            // Normalize path separators to forward slashes
            val normalizedPath = path.replace('\\', '/')
            val name = normalizedPath.substringAfterLast('/')

            ArchiveEntry(
                path = normalizedPath,
                name = name,
                isDirectory = isDirectory,
                size = size,
                compressedSize = compressedSize
            )
        } else {
            null
        }
    }
}

/**
 * Get the path to the bundled 7z.exe
 * The executable should be in the same directory as gunzip.exe (self-contained build)
 * Fallback to project root bin/7zip/ for development
 */
@OptIn(ExperimentalForeignApi::class)
private fun getBundled7zipPath(): String {
    memScoped {
        val candidates = mutableListOf<String>()

        // Allow overriding via environment variable for development/debugging
        getenv("GUNZIP_7ZIP_PATH")?.toKString()?.takeIf { it.isNotBlank() }?.let { envPath ->
            candidates += envPath
        }

        // PRIORITY 1: Same directory as gunzip.exe (self-contained build)
        val moduleBuffer = allocArray<ByteVar>(MAX_PATH)
        if (GetModuleFileNameA(null, moduleBuffer, MAX_PATH.toUInt()) > 0u) {
            val executablePath = moduleBuffer.toKString()
            val executableDir = executablePath.substringBeforeLast('\\', missingDelimiterValue = executablePath)

            // Same directory as the executable (MOST IMPORTANT - checked first)
            candidates += joinWindowsPath(executableDir, "7z.exe")
        }

        // PRIORITY 2: Current working directory bin/7zip (for development from project root)
        val cwdBuffer = allocArray<ByteVar>(MAX_PATH)
        if (GetCurrentDirectoryA(MAX_PATH.toUInt(), cwdBuffer) > 0u) {
            val cwd = cwdBuffer.toKString()
            candidates += joinWindowsPath(cwd, "bin", "7zip", "7z.exe")
        }

        // PRIORITY 3: Relative to executable for development builds
        if (GetModuleFileNameA(null, moduleBuffer, MAX_PATH.toUInt()) > 0u) {
            val executablePath = moduleBuffer.toKString()
            val executableDir = executablePath.substringBeforeLast('\\', missingDelimiterValue = executablePath)

            // Project layout: build/bin/.../gunzip.exe -> fallback to repo-level bin/7zip
            candidates += joinWindowsPath(executableDir, "..", "..", "..", "..", "bin", "7zip", "7z.exe")
        }

        // Return the first existing candidate
        candidates.distinct().forEach { candidate ->
            val normalized = resolveFullPath(candidate.replace('/', '\\'))
            if (access(normalized, F_OK) == 0) {
                return normalized
            }
        }

        throw ExtractionError.IOError(
            "Bundled 7z.exe not found. Ensure 7z.exe and 7z.dll are in the same directory as gunzip.exe, or in bin/7zip/ for development. Checked: ${candidates.distinct().joinToString()}"
        )
    }
}

private fun joinWindowsPath(vararg parts: String): String {
    val builder = StringBuilder()

    parts.filter { it.isNotEmpty() }.forEachIndexed { index, rawPart ->
        val part = when (index) {
            0 -> rawPart.trimEnd('\\', '/')
            else -> rawPart.trim('\\', '/')
        }

        if (part.isEmpty()) {
            return@forEachIndexed
        }

        if (builder.isNotEmpty()) {
            builder.append('\\')
        }

        builder.append(part)
    }

    return builder.toString().ifEmpty { "" }
}

@OptIn(ExperimentalForeignApi::class)
private fun resolveFullPath(path: String): String {
    memScoped {
        val buffer = allocArray<ByteVar>(MAX_PATH)
        val length = GetFullPathNameA(path, MAX_PATH.toUInt(), buffer, null)

        if (length in 1u until MAX_PATH.toUInt()) {
            return buffer.toKString()
        }
    }

    return path
}
