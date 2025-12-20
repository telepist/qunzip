package gunzip

import gunzip.presentation.viewmodels.ApplicationViewModel
import gunzip.presentation.viewmodels.ApplicationEvent
import gunzip.domain.usecases.*
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger

/**
 * Main entry point for the Gunzip application
 */
fun main(args: Array<String>) {
    // Configure logging
    val logger = Logger.withTag("Main")

    logger.i { "Gunzip application starting..." }
    logger.i { "Arguments: ${args.toList()}" }

    // Handle special CLI arguments for installer integration
    when {
        args.contains("--register-associations") -> {
            logger.i { "Registering file associations..." }
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val appPath = getCurrentExecutablePath()
                    val results = dependencies.manageFileAssociationsUseCase.registerAssociations(appPath)

                    val allSuccess = results.all { it.success }
                    val successCount = results.count { it.success }
                    val totalCount = results.size

                    if (allSuccess) {
                        logger.i { "Successfully registered all $totalCount file associations" }
                        println("Successfully registered all $totalCount file associations")
                        exitProcess(0)
                    } else {
                        logger.e { "Failed to register some file associations ($successCount/$totalCount succeeded)" }
                        results.filter { !it.success }.forEach {
                            logger.e { "  - .${it.extension}: ${it.message}" }
                            println("Error: .${it.extension}: ${it.message}")
                        }
                        exitProcess(1)
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Fatal error during file association registration" }
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--unregister-associations") -> {
            logger.i { "Unregistering file associations..." }
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val results = dependencies.manageFileAssociationsUseCase.unregisterAssociations()

                    val allSuccess = results.all { it.success }
                    val successCount = results.count { it.success }
                    val totalCount = results.size

                    if (allSuccess) {
                        logger.i { "Successfully unregistered all $totalCount file associations" }
                        println("Successfully unregistered all $totalCount file associations")
                        exitProcess(0)
                    } else {
                        logger.w { "Some file associations could not be unregistered ($successCount/$totalCount succeeded)" }
                        results.filter { !it.success }.forEach {
                            logger.w { "  - .${it.extension}: ${it.message}" }
                        }
                        // Exit with success even if some failed (they may not have been registered)
                        exitProcess(0)
                    }
                } catch (e: Exception) {
                    logger.e(e) { "Fatal error during file association unregistration" }
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--help") || args.contains("-h") -> {
            printHelp()
            exitProcess(0)
        }

        args.contains("--version") || args.contains("-v") -> {
            println("Gunzip version 1.0.0")
            println("Archive extraction utility for Windows, macOS, and Linux")
            exitProcess(0)
        }
    }

    // Create application scope
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default
    )

    runBlocking {
        try {
            // Initialize dependency injection (would be replaced with proper DI in real implementation)
            val dependencies = initializeDependencies()

            // Create main ViewModel
            val applicationViewModel = ApplicationViewModel(
                extractArchiveUseCase = dependencies.extractArchiveUseCase,
                validateArchiveUseCase = dependencies.validateArchiveUseCase,
                manageFileAssociationsUseCase = dependencies.manageFileAssociationsUseCase,
                scope = applicationScope,
                logger = logger
            )

            // Start the application
            val app = GunzipApplication(
                viewModel = applicationViewModel,
                logger = logger
            )

            app.run(args.toList())

        } catch (e: Exception) {
            logger.e(e) { "Fatal error during application startup" }
            exitProcess(1)
        } finally {
            applicationScope.cancel()
        }
    }
}

/**
 * Print help message
 */
fun printHelp() {
    println("""
        Gunzip - Archive Extraction Utility

        Usage: gunzip [OPTIONS] <archive-file>

        Arguments:
          <archive-file>              Path to archive file to extract

        Options:
          --register-associations     Register file associations for supported formats
          --unregister-associations   Remove file associations
          --help, -h                  Show this help message
          --version, -v               Show version information

        Supported Formats:
          .zip, .7z, .rar, .tar, .tar.gz, .tar.bz2, .tar.xz,
          .tgz, .tbz2, .txz, .cab, .arj, .lzh

        Examples:
          gunzip archive.zip                    Extract archive.zip
          gunzip --register-associations        Register file associations
          gunzip --unregister-associations      Remove file associations
    """.trimIndent())
}

/**
 * Main application class that handles the UI-less extraction process
 */
class GunzipApplication(
    private val viewModel: ApplicationViewModel,
    private val logger: Logger
) {
    suspend fun run(args: List<String>) {
        logger.i { "Running Gunzip application" }

        // Handle application start
        viewModel.handleApplicationStart(args)

        // Collect application events and handle them
        val eventJob = CoroutineScope(Dispatchers.Default).launch {
            viewModel.events.collect { event ->
                handleApplicationEvent(event)
            }
        }

        // Wait for application to complete
        val exitJob = CoroutineScope(Dispatchers.Default).launch {
            viewModel.uiState.collect { state ->
                if (state.shouldExit) {
                    logger.i { "Application exit requested" }
                    // Cancel this job to exit the collect loop
                    this.cancel()
                }
            }
        }

        // Wait for exit condition
        exitJob.join()

        // Cancel event collection
        eventJob.cancel()

        logger.i { "Gunzip application completed" }
    }

    private fun handleApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is ApplicationEvent.AutoExtractionStarted -> {
                logger.i { "Auto-extraction started for: ${event.filePath}" }
            }

            is ApplicationEvent.ExtractionCompleted -> {
                logger.i { "Extraction completed successfully" }
                // Application will exit automatically
            }

            is ApplicationEvent.ExtractionFailed -> {
                logger.e { "Extraction failed: ${event.error?.message}" }
                exitProcess(1)
            }

            is ApplicationEvent.UnsupportedFileOpened -> {
                logger.w { "Unsupported file opened: ${event.filePath}" }
                println("Error: Unsupported file format")
                exitProcess(1)
            }

            is ApplicationEvent.StartupError -> {
                logger.e(event.error) { "Startup error occurred" }
                exitProcess(1)
            }

            is ApplicationEvent.ApplicationExit -> {
                logger.i { "Application exit event received" }
            }
        }
    }
}

/**
 * Application dependencies container
 */
data class ApplicationDependencies(
    val extractArchiveUseCase: ExtractArchiveUseCase,
    val validateArchiveUseCase: ValidateArchiveUseCase,
    val manageFileAssociationsUseCase: ManageFileAssociationsUseCase
)

/**
 * Initialize application dependencies
 * Platform-specific implementation
 */
internal expect fun initializeDependencies(): ApplicationDependencies

/**
 * Get the current executable's full path
 * Platform-specific implementation
 */
internal expect fun getCurrentExecutablePath(): String

/**
 * Exit the application process
 * Platform-specific implementation
 */
internal expect fun exitProcess(code: Int): Nothing