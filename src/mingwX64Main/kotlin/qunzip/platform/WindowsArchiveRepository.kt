package qunzip.platform

import qunzip.domain.entities.*
import qunzip.domain.repositories.ArchiveRepository
import qunzip.domain.usecases.FileInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*
import co.touchlab.kermit.Logger
import kotlin.time.Instant

// Substrings 7-Zip prints (case-insensitive) when an archive is encrypted
// or the supplied password is wrong. Used to translate exit-code failures
// into ExtractionError.PasswordRequired so the GUI can re-prompt.
// Deliberately specific multi-word phrases. A bare "encrypted" would also match
// entry names (extraction output includes filenames via -bb1) or the archive
// path, so a CRC/disk error on an archive containing "encrypted" in a name would
// be misreported as a wrong password.
private val PASSWORD_INDICATORS = listOf(
    "wrong password",
    "can not open encrypted archive",
    "data error in encrypted file",
    "enter password",
)

private fun String.indicatesPasswordError(): Boolean {
    val lower = lowercase()
    return PASSWORD_INDICATORS.any { lower.contains(it) }
}

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

    override suspend fun getArchiveContents(archivePath: String, password: String?): ArchiveContents {
        val startMark = TimeSource.Monotonic.markNow()
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Analyzing archive contents: $archivePath" }

        // Execute 7zip list command: 7z l -slt archive.zip -p"password"
        // Always pass -p to prevent 7zip from blocking on stdin for encrypted archives.
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Running 7z l -slt..." }
        // -sccUTF-8: emit console output (paths) as UTF-8 instead of the OEM
        // console code page, so non-ASCII entry names decode correctly below.
        val args = mutableListOf("l", "-slt", "-sccUTF-8", archivePath, "-p${password ?: ""}")
        val output = execute7zipCommand(args)
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] 7z command completed, output size: ${output.length} chars" }

        // Parse 7zip output to extract file entries
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Parsing output (${output.length} chars)..." }
        val entries = parse7zipListOutput(output)

        // Header-encrypted archives fail the list step itself — surface that as a
        // password prompt so the validator no longer has to run `7z t` upfront.
        // Only treat the output as a password error when no entries were produced;
        // otherwise a benign filename containing "encrypted" could trip the check.
        if (entries.isEmpty() && output.indicatesPasswordError()) {
            throw ExtractionError.PasswordRequired(
                if (password.isNullOrEmpty()) "Password required" else "Wrong password"
            )
        }
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Parsing completed: ${entries.size} entries" }

        val totalSize = entries.sumOf { it.size }
        val totalCompressedSize = entries.mapNotNull { it.compressedSize }.sum()

        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] getArchiveContents done" }
        return ArchiveContents(
            entries = entries,
            totalSize = totalSize,
            totalCompressedSize = if (totalCompressedSize > 0) totalCompressedSize else null
        )
    }

    override suspend fun testArchive(archivePath: String, password: String?): Boolean {
        logger.d { "Testing archive integrity: $archivePath" }

        return try {
            // Execute 7zip test command: 7z t archive.zip [-p"password"]
            val exitCode = execute7zipTest(archivePath, password)
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
        destinationPath: String,
        password: String?
    ): Flow<ExtractionProgress> = channelFlow {
        val startMark = TimeSource.Monotonic.markNow()
        logger.i { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Extracting archive: $archivePath to $destinationPath" }

        trySend(ExtractionProgress(archivePath, stage = ExtractionStage.STARTING))

        try {
            // Get archive info for progress tracking
            logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Starting getArchiveContents..." }
            val contents = getArchiveContents(archivePath, password)
            logger.d {
                "[${startMark.elapsedNow().inWholeMilliseconds}ms] getArchiveContents completed: " +
                    "${contents.fileCount} files, ${contents.totalSize} bytes"
            }
            val totalFiles = contents.fileCount

            trySend(ExtractionProgress(
                archivePath = archivePath,
                totalFiles = totalFiles,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.EXTRACTING
            ))

            // Execute 7zip extraction with real-time byte-level progress tracking
            logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Starting extraction..." }
            val exitCode = execute7zipExtractWithProgress(
                archivePath,
                destinationPath,
                contents.totalSize,
                password,
                // Stop and kill the 7z child if the collecting coroutine is
                // cancelled (window closed / cancel pressed), so it doesn't keep
                // extracting in the background.
                shouldContinue = { isActive }
            ) { bytesExtracted, currentFile ->
                // Send progress update with actual bytes extracted
                trySend(ExtractionProgress(
                    archivePath = archivePath,
                    filesProcessed = if (totalFiles > 0 && contents.totalSize > 0)
                        ((bytesExtracted.toDouble() / contents.totalSize) * totalFiles).toInt().coerceAtMost(totalFiles)
                        else 0,
                    totalFiles = totalFiles,
                    totalBytes = contents.totalSize,
                    bytesProcessed = bytesExtracted,
                    currentFile = currentFile,
                    stage = ExtractionStage.EXTRACTING
                ))
            }

            if (exitCode != 0) {
                // If we were cancelled the child was terminated on purpose — don't
                // surface that as an extraction error.
                if (!isActive) return@channelFlow
                throw ExtractionError.SevenZipError(exitCode, "7zip extraction failed")
            }

            trySend(ExtractionProgress(
                archivePath = archivePath,
                filesProcessed = totalFiles,
                totalFiles = totalFiles,
                bytesProcessed = contents.totalSize,
                totalBytes = contents.totalSize,
                stage = ExtractionStage.COMPLETED
            ))

            logger.i { "Extraction completed successfully" }

        } catch (e: ExtractionError) {
            logger.e { "Extraction failed: ${e.message}" }
            trySend(ExtractionProgress(archivePath, stage = ExtractionStage.FAILED))
            throw e
        } catch (e: Exception) {
            logger.e(e) { "Unexpected error during extraction" }
            trySend(ExtractionProgress(archivePath, stage = ExtractionStage.FAILED))
            throw ExtractionError.UnknownError(e.message ?: "Unknown error", e)
        }
    }

    override fun isFormatSupported(format: ArchiveFormat): Boolean {
        // 7zip supports all our defined formats
        return true
    }

    override fun getSupportedFormats(): List<ArchiveFormat> {
        return ArchiveFormat.entries
    }

    override suspend fun isPasswordRequired(archivePath: String): Boolean {
        logger.d { "Checking if password required: $archivePath" }

        // Run 7z t with an empty password (-p) and capture output to check for password indicators.
        // The empty password prevents 7zip from blocking on stdin waiting for user input.
        val output = execute7zipCommand(listOf("t", "-p", archivePath))
        return output.indicatesPasswordError()
    }

    // Private helper methods

    private fun fileExists(path: String): Boolean {
        return access(path, F_OK) == 0
    }

    private fun getFileStats(path: String): FileInfo? = memScoped {
        // Use GetFileAttributesExW rather than stat(): mingw's struct stat has a
        // 32-bit st_size, which wraps for files >= 4 GiB (a 6 GB archive would
        // report ~1.5 GB). The Win32 API gives a full 64-bit size and is
        // Unicode-safe for the path.
        val data = alloc<WIN32_FILE_ATTRIBUTE_DATA>()
        if (GetFileAttributesExW(path, GET_FILEEX_INFO_LEVELS.GetFileExInfoStandard, data.ptr) == 0) {
            return null
        }

        val size = (data.nFileSizeHigh.toLong() shl 32) or data.nFileSizeLow.toLong()
        // FILETIME is 100-ns ticks since 1601-01-01; convert to Unix epoch seconds.
        val ticks = (data.ftLastWriteTime.dwHighDateTime.toLong() shl 32) or
            data.ftLastWriteTime.dwLowDateTime.toLong()
        val epochSeconds = (ticks - 116444736000000000L) / 10_000_000L

        FileInfo(
            path = path,
            size = size,
            lastModified = Instant.fromEpochSeconds(epochSeconds),
            isReadable = true,
            isDirectory = (data.dwFileAttributes and FILE_ATTRIBUTE_DIRECTORY.toUInt()) != 0u
        )
    }

    /**
     * Execute 7zip command and capture output without showing a console window
     * Uses CreateProcessW with CREATE_NO_WINDOW and pipes for stdout
     */
    private fun execute7zipCommand(args: List<String>): String = memScoped {
        // Quote the program path and every argument with proper Windows rules so
        // paths and passwords containing spaces or quotes survive intact.
        val command = buildWindowsCommandLine(sevenZipPath, args)
        logger.d { "Executing 7zip command: $command" }

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

        // Collect raw chunks, then decode once as UTF-8 (7-Zip is invoked with
        // -sccUTF-8). Decoding per-chunk would corrupt a multi-byte UTF-8 sequence
        // straddling a read boundary. Chunks (not a per-byte list) avoid boxing
        // for large listings.
        val chunks = mutableListOf<ByteArray>()
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
            val n = bytesRead.value.toInt()
            chunks.add(ByteArray(n) { buffer[it] })
        }

        // Wait for process and cleanup
        WaitForSingleObject(processInfo.hProcess, INFINITE)
        CloseHandle(processInfo.hProcess)
        CloseHandle(processInfo.hThread)
        CloseHandle(stdoutReadHandle.value)

        val total = chunks.sumOf { it.size }
        val combined = ByteArray(total)
        var offset = 0
        for (chunk in chunks) {
            chunk.copyInto(combined, offset)
            offset += chunk.size
        }
        return@memScoped combined.decodeToString()
    }

    private fun execute7zipTest(archivePath: String, password: String? = null): Int {
        // Always pass -p to prevent 7zip from blocking on stdin for encrypted archives.
        // Empty password (-p) is harmless for non-encrypted archives.
        val command = buildWindowsCommandLine(sevenZipPath, listOf("t", archivePath, "-p${password ?: ""}"))
        return executeCommandSilently(command)
    }

    /**
     * Execute 7zip extraction with real-time progress tracking
     * Parses 7zip's progress output (-bsp1) to get percentage updates
     * Calls the callback with percentage progress (0-100)
     */
    private fun execute7zipExtractWithProgress(
        archivePath: String,
        destinationPath: String,
        totalBytes: Long,
        password: String? = null,
        shouldContinue: () -> Boolean = { true },
        onProgress: (bytesExtracted: Long, currentFile: String?) -> Unit
    ): Int = memScoped {
        // Use -bsp1 to output progress to stdout, -bb1 for file names
        // Note: Conflict handling is done at application level, not by 7zip's -aou flag
        // Always pass -p to prevent 7zip from blocking on stdin for encrypted archives.
        // -sccUTF-8 makes 7-Zip print the "- <path>" progress lines as UTF-8, so
        // the current-file display shows non-ASCII names correctly (they're
        // decoded as UTF-8 below).
        val command = buildWindowsCommandLine(
            sevenZipPath,
            listOf("x", archivePath, "-o$destinationPath", "-y", "-bsp1", "-bb1", "-sccUTF-8", "-p${password ?: ""}")
        )
        logger.d { "Extraction command with progress: $command" }

        // Create pipe for stdout
        val securityAttrs = alloc<SECURITY_ATTRIBUTES>()
        securityAttrs.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
        securityAttrs.bInheritHandle = TRUE
        securityAttrs.lpSecurityDescriptor = null

        val stdoutReadHandle = alloc<HANDLEVar>()
        val stdoutWriteHandle = alloc<HANDLEVar>()

        if (CreatePipe(stdoutReadHandle.ptr, stdoutWriteHandle.ptr, securityAttrs.ptr, 0u) == 0) {
            logger.e { "Failed to create pipe for progress tracking" }
            return@memScoped executeCommandSilently(
                buildWindowsCommandLine(
                    sevenZipPath,
                    listOf("x", archivePath, "-o$destinationPath", "-y", "-p${password ?: ""}")
                )
            )
        }

        // Ensure the read handle is not inherited
        SetHandleInformation(stdoutReadHandle.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)

        // Put the 7z child in a kill-on-close job object: if this process exits
        // while extraction is still running (window closed, crash, exitProcess),
        // Windows closes the job handle and terminates the child, so it can't keep
        // extracting orphaned. Create the process suspended, assign it, then resume.
        val job = CreateJobObjectW(null, null)
        if (job != null) {
            val jobInfo = alloc<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>()
            jobInfo.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE.toUInt()
            SetInformationJobObject(
                job,
                // JobObjectExtendedLimitInformation (9) — passed by value; the K/N
                // Windows headers map JOBOBJECTINFOCLASS to a UInt.
                9u,
                jobInfo.ptr,
                sizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>().toUInt()
            )
        }

        val startupInfo = alloc<STARTUPINFOW>()
        val processInfo = alloc<PROCESS_INFORMATION>()

        startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
        startupInfo.dwFlags = (STARTF_USESHOWWINDOW or STARTF_USESTDHANDLES).toUInt()
        startupInfo.wShowWindow = SW_HIDE.toUShort()
        startupInfo.hStdOutput = stdoutWriteHandle.value
        startupInfo.hStdError = stdoutWriteHandle.value
        startupInfo.hStdInput = null

        val creationFlags = if (job != null) {
            (CREATE_NO_WINDOW or CREATE_SUSPENDED).toUInt()
        } else {
            CREATE_NO_WINDOW.toUInt()
        }
        val success = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = command.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = TRUE,
            dwCreationFlags = creationFlags,
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = startupInfo.ptr,
            lpProcessInformation = processInfo.ptr
        )

        // Close write end of pipe in parent process
        CloseHandle(stdoutWriteHandle.value)

        if (success == 0) {
            job?.let { CloseHandle(it) }
            CloseHandle(stdoutReadHandle.value)
            logger.e { "Failed to create process for extraction: ${GetLastError()}" }
            return@memScoped -1
        }

        // Assign to the job and resume (the process was created suspended).
        if (job != null) {
            AssignProcessToJobObject(job, processInfo.hProcess)
            ResumeThread(processInfo.hThread)
        }

        // Read output and parse progress. Accumulate raw bytes per line and decode
        // each complete line as UTF-8 (7-Zip runs with -sccUTF-8), so a multi-byte
        // filename that straddles a 4 KB read boundary isn't corrupted in the
        // "current file" display.
        val buffer = allocArray<ByteVar>(4096)
        val bytesRead = alloc<UIntVar>()
        val lineBytes = ArrayList<Byte>()
        val allOutput = StringBuilder()
        var lastPercentage = -1
        var currentFile: String? = null

        // Regex to match percentage like "  45%" or " 100%"
        val percentRegex = Regex("""^\s*(\d+)%""")

        fun handleLine() {
            if (lineBytes.isEmpty()) return
            val line = lineBytes.toByteArray().decodeToString().trim()
            lineBytes.clear()
            if (line.isEmpty()) return
            allOutput.append(line).append('\n')
            // Check for percentage progress
            val percentMatch = percentRegex.find(line)
            if (percentMatch != null) {
                val percent = percentMatch.groupValues[1].toIntOrNull() ?: 0
                if (percent != lastPercentage && percent in 0..100) {
                    lastPercentage = percent
                    onProgress(totalBytes * percent / 100, currentFile)
                }
            }
            // Check for file being extracted: "- filename"
            if (line.startsWith("- ")) {
                currentFile = line.substring(2).trim()
            }
        }

        // Treat \r and \n as line endings; skip embedded null bytes.
        fun consume(n: Int) {
            for (i in 0 until n) {
                val b = buffer[i]
                when {
                    b == '\r'.code.toByte() || b == '\n'.code.toByte() -> handleLine()
                    b != 0.toByte() -> lineBytes.add(b)
                }
            }
        }

        // Drain all currently-available pipe data without blocking, so the loop
        // below can poll for cancellation between reads.
        val bytesAvail = alloc<UIntVar>()
        fun drainAvailable() {
            while (true) {
                if (PeekNamedPipe(stdoutReadHandle.value, null, 0u, null, bytesAvail.ptr, null) == 0) break
                if (bytesAvail.value == 0u) break
                val toRead = minOf(bytesAvail.value, 4095u)
                if (ReadFile(stdoutReadHandle.value, buffer, toRead, bytesRead.ptr, null) == 0 ||
                    bytesRead.value == 0u) break
                consume(bytesRead.value.toInt())
            }
        }

        var processExited = false
        var cancelled = false
        while (!processExited) {
            drainAvailable()
            if (!shouldContinue()) {
                logger.i { "Extraction cancelled — terminating 7-Zip child process" }
                TerminateProcess(processInfo.hProcess, 1u)
                cancelled = true
                WaitForSingleObject(processInfo.hProcess, 5000u)
                break
            }
            // Wait briefly for exit; loop back to drain + re-check cancellation.
            if (WaitForSingleObject(processInfo.hProcess, 100u) == 0u /* WAIT_OBJECT_0 */) {
                processExited = true
            }
        }

        // Final drain of anything still buffered, then flush any trailing line.
        drainAvailable()
        handleLine()

        val exitCode = alloc<UIntVar>()
        GetExitCodeProcess(processInfo.hProcess, exitCode.ptr)

        // Cleanup. Closing the job handle after the child has exited is harmless;
        // if we were exiting abruptly the OS would close it and kill the child.
        CloseHandle(processInfo.hProcess)
        CloseHandle(processInfo.hThread)
        CloseHandle(stdoutReadHandle.value)
        job?.let { CloseHandle(it) }

        val code = if (cancelled) 1 else exitCode.value.toInt()

        // Check for password errors before returning
        if (code != 0 && allOutput.toString().indicatesPasswordError()) {
            throw ExtractionError.PasswordRequired("Wrong password")
        }

        return@memScoped code
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
 * The executable should be in the same directory as qunzip.exe (self-contained build)
 * Fallback to project root bin/7zip/ for development
 */
@OptIn(ExperimentalForeignApi::class)
private fun getBundled7zipPath(): String {
    memScoped {
        val candidates = mutableListOf<String>()

        // Allow overriding via environment variable for development/debugging
        getenv("QUNZIP_7ZIP_PATH")?.toKString()?.takeIf { it.isNotBlank() }?.let { envPath ->
            candidates += envPath
        }

        // PRIORITY 1: Same directory as qunzip.exe (self-contained build)
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

            // Project layout: build/bin/.../qunzip.exe -> fallback to repo-level bin/7zip
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
            "Bundled 7z.exe not found. Ensure 7z.exe and 7z.dll are in the same directory as " +
                "qunzip.exe, or in bin/7zip/ for development. " +
                "Checked: ${candidates.distinct().joinToString()}"
        )
    }
}

/**
 * Build a Windows command line from a program path and arguments, quoting each
 * token per the CommandLineToArgvW rules that CreateProcessW/7-Zip follow. This
 * keeps paths and passwords that contain spaces or double quotes intact, and
 * quotes the program path itself (the install dir is "…\Quick Unzip", which has
 * a space).
 */
private fun buildWindowsCommandLine(program: String, args: List<String>): String =
    (listOf(program) + args).joinToString(" ") { quoteWindowsArg(it) }

/**
 * Quote a single argument using the standard MSVCRT/CommandLineToArgvW algorithm:
 * wrap in double quotes when it contains whitespace or a quote, escape embedded
 * quotes with a backslash, and double any run of backslashes that immediately
 * precedes a quote (including the closing one).
 */
private fun quoteWindowsArg(arg: String): String {
    if (arg.isNotEmpty() && arg.none { it == ' ' || it == '\t' || it == '"' }) {
        return arg
    }
    val sb = StringBuilder()
    sb.append('"')
    var backslashes = 0
    for (c in arg) {
        when (c) {
            '\\' -> backslashes++
            '"' -> {
                repeat(backslashes * 2 + 1) { sb.append('\\') }
                backslashes = 0
                sb.append('"')
            }
            else -> {
                repeat(backslashes) { sb.append('\\') }
                backslashes = 0
                sb.append(c)
            }
        }
    }
    repeat(backslashes * 2) { sb.append('\\') }
    sb.append('"')
    return sb.toString()
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
