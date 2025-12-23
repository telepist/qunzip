package gunzip

import gunzip.domain.usecases.*
import gunzip.platform.*
import co.touchlab.kermit.Logger
import kotlinx.cinterop.*
import platform.posix.exit
import platform.windows.GetModuleFileNameA
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
        archiveRepository = archiveRepository,
        fileSystemRepository = fileSystemRepository
    )

    val manageFileAssociationsUseCase = ManageFileAssociationsUseCase(
        fileAssociationRepository = fileAssociationRepository
    )

    logger.i { "Windows dependencies initialized successfully" }

    return ApplicationDependencies(
        extractArchiveUseCase = extractArchiveUseCase,
        validateArchiveUseCase = validateArchiveUseCase,
        manageFileAssociationsUseCase = manageFileAssociationsUseCase,
        preferencesRepository = preferencesRepository
    )
}

/**
 * Get the current executable's full path on Windows
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun getCurrentExecutablePath(): String = memScoped {
    val buffer = allocArray<ByteVar>(MAX_PATH)
    val length = GetModuleFileNameA(null, buffer, MAX_PATH.toUInt())

    if (length > 0u) {
        buffer.toKString()
    } else {
        // Fallback: return a placeholder if we can't get the path
        "gunzip.exe"
    }
}

/**
 * Windows-specific process exit
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun exitProcess(code: Int): Nothing {
    exit(code)
    throw RuntimeException("exit() should not return")
}
