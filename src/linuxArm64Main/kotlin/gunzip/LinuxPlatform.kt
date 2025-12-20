package gunzip

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.exit

/**
 * Linux ARM64-specific dependency initialization
 * TODO: Implement Linux repositories
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun initializeDependencies(): ApplicationDependencies {
    val logger = Logger.withTag("LinuxPlatform")
    logger.e { "Linux platform not yet implemented" }
    throw NotImplementedError("Linux platform repositories not yet implemented")
}

/**
 * Linux ARM64-specific process exit
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun exitProcess(code: Int): Nothing {
    exit(code)
    throw RuntimeException("exit() should not return")
}
