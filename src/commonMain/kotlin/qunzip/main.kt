package qunzip

import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.ui.*
import qunzip.domain.usecases.*
import qunzip.domain.repositories.PreferencesRepository
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * Main entry point for the Qunzip application
 */
fun main(args: Array<String>) {
    // With GUI subsystem (-mwindows), no console exists at startup.
    // Detect launch mode early: standalone (drag-drop) vs CLI (shell).
    val earlyStandalone = isStandaloneLaunch()
    val useGui = args.contains("--gui") || earlyStandalone

    // For CLI mode, reattach to the parent shell's console for stdio.
    if (!useGui) {
        configureStandaloneConsole()
    }

    // Suppress logging to keep the TUI display clean
    Logger.setMinSeverity(Severity.Assert)
    UiConfig.enableTuiMode()

    val logger = Logger.withTag("Main")

    // Handle special CLI arguments for installer integration
    // --force-standalone: test standalone exit behavior from CLI
    val forceStandalone = args.contains("--force-standalone")

    when {
        args.contains("--register-associations") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val appPath = getCurrentExecutablePath()
                    val results = dependencies.manageFileAssociationsUseCase.registerAssociations(appPath)

                    val allSuccess = results.all { it.success }
                    val successCount = results.count { it.success }
                    val totalCount = results.size

                    if (allSuccess) {
                        println("Successfully registered all $totalCount file associations")
                        exitProcess(0)
                    } else {
                        results.filter { !it.success }.forEach {
                            println("Error: .${it.extension}: ${it.message}")
                        }
                        exitProcess(1)
                    }
                } catch (e: Exception) {
                    println("Error: ${e.message}")
                    exitProcess(1)
                }
            }
        }

        args.contains("--unregister-associations") -> {
            runBlocking {
                try {
                    val dependencies = initializeDependencies()
                    val results = dependencies.manageFileAssociationsUseCase.unregisterAssociations()

                    val allSuccess = results.all { it.success }
                    val successCount = results.count { it.success }
                    val totalCount = results.size

                    if (allSuccess) {
                        println("Successfully unregistered all $totalCount file associations")
                        exitProcess(0)
                    } else {
                        exitProcess(0)
                    }
                } catch (e: Exception) {
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
            val dependencies = initializeDependencies()

            val isStandalone = forceStandalone || earlyStandalone

            val applicationViewModel = ApplicationViewModel(
                extractArchiveUseCase = dependencies.extractArchiveUseCase,
                validateArchiveUseCase = dependencies.validateArchiveUseCase,
                manageFileAssociationsUseCase = dependencies.manageFileAssociationsUseCase,
                preferencesRepository = dependencies.preferencesRepository,
                scope = applicationScope,
                logger = logger,
                isStandaloneLaunch = isStandalone
            )

            val appArgs = args.toList().filterNot { it == "--force-standalone" || it == "--gui" }
            applicationViewModel.handleApplicationStart(appArgs)

            // GUI for standalone (drag-drop) or --gui flag, otherwise TUI
            val renderer: UiRenderer = if (useGui) {
                createGuiRenderer()
            } else {
                MosaicTuiRenderer(isStandaloneLaunch = false)
            }
            renderer.render(applicationViewModel)
            if (useGui) {
                exitProcess(0)
            }
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

/**
 * Create platform-specific GUI renderer (experimental)
 */
internal expect fun createGuiRenderer(): UiRenderer

