package qunzip.platform

import qunzip.domain.entities.*
import qunzip.domain.repositories.ArchiveRepository
import qunzip.domain.usecases.FileInfo
import qunzip.data.sevenzip.SevenZipOutputParser
import qunzip.data.sevenzip.SevenZipProgressLine
import qunzip.data.process.ProcessRunner
import qunzip.util.joinWindowsPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlin.time.TimeSource
import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*
import co.touchlab.kermit.Logger
import kotlin.time.Instant

/**
 * Windows implementation of ArchiveRepository using 7zip command-line tool
 * Uses bundled 7z.exe from bin/7zip/ directory relative to the executable
 *
 * Note: Uses 7z.exe (requires 7z.dll) for full format support including RAR
 */
@OptIn(ExperimentalForeignApi::class)
class WindowsArchiveRepository(
    private val sevenZipPath: String = getBundled7zipPath(),
    private val processRunner: ProcessRunner = WindowsProcessRunner(),
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
        val args = listOf("l", "-slt", "-sccUTF-8", archivePath, "-p${password ?: ""}")
        val sb = StringBuilder()
        processRunner.run(sevenZipPath, args) { line -> sb.append(line).append('\n') }
        val output = sb.toString()
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] 7z command completed, output size: ${output.length} chars" }

        // Parse 7zip output to extract file entries
        logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Parsing output (${output.length} chars)..." }
        val entries = SevenZipOutputParser.parseListOutput(output)

        // Header-encrypted archives fail the list step itself — surface that as a
        // password prompt so the validator no longer has to run `7z t` upfront.
        // Only treat the output as a password error when no entries were produced;
        // otherwise a benign filename containing "encrypted" could trip the check.
        if (entries.isEmpty() && SevenZipOutputParser.indicatesPasswordError(output)) {
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
            // 7z t archive.zip -p"password" (always pass -p so it never blocks on
            // stdin for encrypted archives).
            val exitCode = processRunner.run(
                sevenZipPath,
                listOf("t", archivePath, "-p${password ?: ""}"),
            )
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

            // Extract with real-time progress. -bsp1 emits percentages, -bb1 the
            // "- <file>" lines, -sccUTF-8 makes both UTF-8. shouldContinue kills the
            // child if the collecting coroutine is cancelled (window closed / cancel).
            logger.d { "[${startMark.elapsedNow().inWholeMilliseconds}ms] Starting extraction..." }
            val args = listOf(
                "x", archivePath, "-o$destinationPath", "-y", "-bsp1", "-bb1", "-sccUTF-8", "-p${password ?: ""}"
            )
            val allOutput = StringBuilder()
            var lastPercentage = -1
            var currentFile: String? = null
            val exitCode = processRunner.run(sevenZipPath, args, shouldContinue = { isActive }) { line ->
                allOutput.append(line).append('\n')
                when (val parsed = SevenZipOutputParser.parseProgressLine(line)) {
                    is SevenZipProgressLine.Percent -> if (parsed.value != lastPercentage) {
                        lastPercentage = parsed.value
                        val bytesExtracted = contents.totalSize * parsed.value / 100
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
                    is SevenZipProgressLine.CurrentFile -> currentFile = parsed.name
                    null -> { /* not a progress line */ }
                }
            }

            if (exitCode != 0) {
                // If we were cancelled the child was terminated on purpose — don't
                // surface that as an extraction error.
                if (!isActive) return@channelFlow
                if (SevenZipOutputParser.indicatesPasswordError(allOutput.toString())) {
                    throw ExtractionError.PasswordRequired("Wrong password")
                }
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
        val sb = StringBuilder()
        processRunner.run(sevenZipPath, listOf("t", "-p", archivePath)) { line -> sb.append(line).append('\n') }
        return SevenZipOutputParser.indicatesPasswordError(sb.toString())
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
