package gunzip

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.exit

/**
 * macOS ARM64-specific dependency initialization
 * TODO: Implement macOS repositories
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun initializeDependencies(): ApplicationDependencies {
    val logger = Logger.withTag("MacosPlatform")
    logger.e { "macOS platform not yet implemented" }
    throw NotImplementedError("macOS platform repositories not yet implemented")
}

/**
 * macOS ARM64-specific process exit
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun exitProcess(code: Int): Nothing {
    exit(code)
    throw RuntimeException("exit() should not return")
}

/**
 * macOS ARM64-specific executable path
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun getCurrentExecutablePath(): String {
    // TODO: Implement using _NSGetExecutablePath
    return "/usr/local/bin/gunzip"
}
