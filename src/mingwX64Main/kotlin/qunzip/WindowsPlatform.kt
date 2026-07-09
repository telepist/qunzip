package qunzip

import qunzip.domain.usecases.*
import qunzip.platform.*
import qunzip.presentation.ui.ImGuiRenderer
import qunzip.presentation.ui.UiRenderer
import co.touchlab.kermit.Logger
import kotlinx.cinterop.*
import platform.windows.ExitProcess
import platform.windows.GetModuleFileNameW
import platform.windows.MAX_PATH

/**
 * Windows-specific dependency initialization
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun initializeDependencies(): ApplicationDependencies {
    val logger = Logger.withTag("WindowsPlatform")
    logger.i { "Initializing Windows dependencies" }

    // Create platform-specific repositories
    val archiveRepository = WindowsArchiveRepository()
    val fileSystemRepository = WindowsFileSystemRepository()
    val notificationRepository = WindowsNotificationRepository()
    val fileAssociationRepository = WindowsFileAssociationRepository()
    val preferencesRepository = WindowsPreferencesRepository()

    // Create use cases
    val extractArchiveUseCase = ExtractArchiveUseCase(
        archiveRepository = archiveRepository,
        fileSystemRepository = fileSystemRepository,
        notificationRepository = notificationRepository
    )

    val validateArchiveUseCase = ValidateArchiveUseCase(
        fileSystemRepository = fileSystemRepository
    )

    val manageFileAssociationsUseCase = ManageFileAssociationsUseCase(
        fileAssociationRepository = fileAssociationRepository
    )

    // The CLI shim adds the install directory (parent of the running .exe)
    // to user PATH so `qunzip` is reachable from any new shell.
    val cliShimRepository = WindowsCliShimRepository(
        installDirProvider = { parentDirOf(getCurrentExecutablePath()) }
    )

    logger.i { "Windows dependencies initialized successfully" }

    return ApplicationDependencies(
        extractArchiveUseCase = extractArchiveUseCase,
        validateArchiveUseCase = validateArchiveUseCase,
        manageFileAssociationsUseCase = manageFileAssociationsUseCase,
        preferencesRepository = preferencesRepository,
        cliShimRepository = cliShimRepository
    )
}

private fun parentDirOf(path: String): String {
    val sep = if (path.contains('\\')) '\\' else '/'
    return path.substringBeforeLast(sep, missingDelimiterValue = "")
}

/**
 * Get the current executable's full path on Windows.
 *
 * Uses the wide-char API so Unicode install paths (accented usernames,
 * localized "Program Files" on some locales) survive without mojibake.
 * The path returned here flows into file-association registration and
 * the PATH-shim install dir, where corruption would be destructive.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun getCurrentExecutablePath(): String = memScoped {
    val buffer = allocArray<UShortVar>(MAX_PATH)
    val length = GetModuleFileNameW(null, buffer, MAX_PATH.toUInt())

    if (length > 0u) {
        buffer.toKStringFromUtf16()
    } else {
        // Fallback: return a placeholder if we can't get the path
        "qunzip.exe"
    }
}

/**
 * Path to the GUI executable (QuickUnzip.exe). Resolved as a sibling of the
 * current executable, so this works regardless of install location.
 */
internal actual fun getGuiExecutablePath(): String {
    val current = getCurrentExecutablePath()
    val sep = if (current.contains('\\')) '\\' else '/'
    val parent = current.substringBeforeLast(sep, missingDelimiterValue = "")
    return if (parent.isEmpty()) "QuickUnzip.exe" else "$parent${sep}QuickUnzip.exe"
}

/**
 * Windows-specific process exit.
 * Flushes all stdio buffers before terminating — ExitProcess() alone
 * would skip the flush, losing buffered output (e.g., piped/redirected stdout).
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun exitProcess(code: Int): Nothing {
    platform.posix.fflush(null) // flush all open stdio streams
    ExitProcess(code.toUInt())
    throw RuntimeException("ExitProcess() should not return")
}

internal actual fun createGuiRenderer(): UiRenderer = ImGuiRenderer()
