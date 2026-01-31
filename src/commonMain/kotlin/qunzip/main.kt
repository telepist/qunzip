package qunzip

import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.viewmodels.ApplicationEvent
import qunzip.presentation.ui.*
import qunzip.domain.usecases.*
import qunzip.domain.repositories.PreferencesRepository
import qunzip.domain.entities.UserPreferences
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Main entry point for the Qunzip application
 */
fun main(args: Array<String>) {
    // Determine UI mode early to configure logging appropriately
    val uiMode = selectUiMode(args.toList())

    // Suppress logging and console output in TUI mode to keep the display clean
    // Don't suppress if --gui was explicitly requested
    val forceGui = args.contains("--gui")
    val shouldSuppressForTui = uiMode == UiMode.TUI && !forceGui
    if (shouldSuppressForTui) {
        Logger.setMinSeverity(Severity.Assert) // Effectively disables logging
        UiConfig.enableTuiMode() // Suppress println notifications
    }

    // Configure logging
    val logger = Logger.withTag("Main")

    logger.i { "Qunzip application starting..." }
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

        args.contains("--set-trash-on") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val currentPrefs = dependencies.preferencesRepository.loadPreferences()
                    val newPrefs = currentPrefs.copy(moveToTrashAfterExtraction = true)
                    if (dependencies.preferencesRepository.savePreferences(newPrefs)) {
                        println("Setting updated: Move archive to trash after extraction = ON")
                        exitProcess(0)
                    } else {
                        println("Error: Failed to save preferences")
                        exitProcess(1)
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--set-trash-off") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val currentPrefs = dependencies.preferencesRepository.loadPreferences()
                    val newPrefs = currentPrefs.copy(moveToTrashAfterExtraction = false)
                    if (dependencies.preferencesRepository.savePreferences(newPrefs)) {
                        println("Setting updated: Move archive to trash after extraction = OFF")
                        exitProcess(0)
                    } else {
                        println("Error: Failed to save preferences")
                        exitProcess(1)
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--set-dialog-on") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val currentPrefs = dependencies.preferencesRepository.loadPreferences()
                    val newPrefs = currentPrefs.copy(showCompletionDialog = true)
                    if (dependencies.preferencesRepository.savePreferences(newPrefs)) {
                        println("Setting updated: Show completion dialog = ON")
                        exitProcess(0)
                    } else {
                        println("Error: Failed to save preferences")
                        exitProcess(1)
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--set-dialog-off") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val currentPrefs = dependencies.preferencesRepository.loadPreferences()
                    val newPrefs = currentPrefs.copy(showCompletionDialog = false)
                    if (dependencies.preferencesRepository.savePreferences(newPrefs)) {
                        println("Setting updated: Show completion dialog = OFF")
                        exitProcess(0)
                    } else {
                        println("Error: Failed to save preferences")
                        exitProcess(1)
                    }
                } catch (e: Exception) {
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
            println("Qunzip version 1.0.0")
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
            // Initialize dependency injection
            val dependencies = initializeDependencies()

            // Create main ViewModel
            val applicationViewModel = ApplicationViewModel(
                extractArchiveUseCase = dependencies.extractArchiveUseCase,
                validateArchiveUseCase = dependencies.validateArchiveUseCase,
                manageFileAssociationsUseCase = dependencies.manageFileAssociationsUseCase,
                preferencesRepository = dependencies.preferencesRepository,
                scope = applicationScope,
                logger = logger
            )

            // Initialize application with arguments (filter out UI mode flags)
            val appArgs = args.toList().filterNot { it == "--gui" || it == "--tui" }
            applicationViewModel.handleApplicationStart(appArgs)

            // Determine UI mode (GUI or TUI)
            val uiMode = selectUiMode(args.toList())
            logger.i { "Selected UI mode: $uiMode" }

            // Select appropriate renderer
            val renderer = when (uiMode) {
                UiMode.GUI -> {
                    val nativeGui = createNativeGuiRenderer()
                    if (nativeGui?.isAvailable() == true) {
                        logger.i { "Using native GUI renderer" }
                        nativeGui
                    } else {
                        logger.w { "Native GUI not available, falling back to TUI" }
                        // Enable TUI mode suppression since we're falling back to TUI
                        Logger.setMinSeverity(Severity.Assert)
                        UiConfig.enableTuiMode()
                        MosaicTuiRenderer()
                    }
                }
                UiMode.TUI -> {
                    logger.i { "Using Mosaic TUI renderer" }
                    MosaicTuiRenderer()
                }
            }

            // Render UI
            renderer.render(applicationViewModel, applicationScope)

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
        Qunzip - Quick Unzip - Archive Extraction Utility

        Usage: qunzip [OPTIONS] <archive-file>

        Arguments:
          <archive-file>              Path to archive file to extract

        Options:
          --gui                       Force GUI mode (native dialogs)
          --tui                       Force TUI mode (terminal UI)
          --set-trash-on              Enable moving archive to trash after extraction
          --set-trash-off             Disable moving archive to trash (default)
          --set-dialog-on             Enable completion dialog after extraction
          --set-dialog-off            Disable completion dialog (silent exit, default)
          --register-associations     Register file associations for supported formats
          --unregister-associations   Remove file associations
          --help, -h                  Show this help message
          --version, -v               Show version information

        Supported Formats:
          .zip, .7z, .rar, .tar, .tar.gz, .tar.bz2, .tar.xz,
          .tgz, .tbz2, .txz, .cab, .arj, .lzh

        Examples:
          qunzip archive.zip                    Extract archive.zip
          qunzip --tui archive.zip              Extract with terminal UI
          qunzip --gui archive.zip              Extract with native GUI
          qunzip --register-associations        Register file associations
          qunzip --unregister-associations      Remove file associations
    """.trimIndent())
}

/**
 * Application dependencies container
 */
data class ApplicationDependencies(
    val extractArchiveUseCase: ExtractArchiveUseCase,
    val validateArchiveUseCase: ValidateArchiveUseCase,
    val manageFileAssociationsUseCase: ManageFileAssociationsUseCase,
    val preferencesRepository: PreferencesRepository
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