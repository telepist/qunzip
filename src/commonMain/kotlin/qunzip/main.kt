package qunzip

import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.ui.*
import qunzip.domain.usecases.*
import qunzip.domain.repositories.CliShimRepository
import qunzip.domain.repositories.PreferencesRepository
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

/**
 * CLI entry point — used by qunzip.exe (console subsystem on Windows; the
 * single binary on Linux/macOS) and by debug/test builds.
 *
 * Always renders the Mosaic TUI in the current terminal. Handles all
 * settings/admin flags (--register-associations, --set-trash-on, etc.).
 */
fun mainCli(entryArgs: Array<String>) {
    Logger.setMinSeverity(Severity.Assert)
    UiConfig.enableTuiMode()
    val logger = Logger.withTag("MainCli")

    // Re-read argv as UTF-16 on Windows so non-ASCII paths survive (see
    // resolveCommandLineArgs). No-op on other platforms.
    val args = resolveCommandLineArgs(entryArgs)

    // --force-standalone: simulate the GUI exe's auto-close behavior from CLI for testing.
    val forceStandalone = args.contains("--force-standalone")

    when {
        args.contains("--register-associations") -> handleRegisterAssociations()
        args.contains("--unregister-associations") -> handleUnregisterAssociations()
        args.contains("--set-trash-on") -> handleSetTrash(true)
        args.contains("--set-trash-off") -> handleSetTrash(false)
        args.contains("--set-dialog-on") -> handleSetAutoClose(false)
        args.contains("--set-dialog-off") -> handleSetAutoClose(true)
        args.contains("--help") || args.contains("-h") -> {
            printHelp()
            exitProcess(0)
        }
        args.contains("--version") || args.contains("-v") -> {
            println("Quick Unzip version 1.0.0")
            println("Archive extraction utility for Windows, macOS, and Linux")
            exitProcess(0)
        }
    }

    val cleanedArgs = args.filterNot { it == "--force-standalone" }
    runApp(cleanedArgs, useGui = false, isStandalone = forceStandalone, logger = logger)
}

/**
 * GUI entry point — used by QuickUnzip.exe (Windows subsystem). Always
 * renders the ImGui dialog. No console flash, no CLI-flag handling.
 */
fun mainGui(entryArgs: Array<String>) {
    Logger.setMinSeverity(Severity.Assert)
    // UiConfig.enableTuiMode() suppresses println-based notifications that
    // would otherwise interleave with the TUI display. The GUI binary
    // doesn't render a TUI, but extraction code still emits those println
    // notifications, and the GUI has no console to print them to anyway —
    // suppressing keeps stderr clean if a parent shell is somehow attached.
    UiConfig.enableTuiMode()
    val logger = Logger.withTag("MainGui")

    // Re-read argv as UTF-16 on Windows so non-ASCII paths survive (see
    // resolveCommandLineArgs). No-op on other platforms.
    val args = resolveCommandLineArgs(entryArgs)

    // The GUI exe ignores any flags — it only consumes a file path. CLI flags
    // belong to qunzip.exe. If dropping flags leaves nothing but there WAS an
    // argument (e.g. a relative path like "-weird.zip"), keep the first one so we
    // still try to open it rather than silently falling into settings mode.
    val cleanedArgs = args.filterNot { it.startsWith("-") }
        .ifEmpty { args.take(1) }
    runApp(cleanedArgs, useGui = true, isStandalone = true, logger = logger)
}

/**
 * Backwards-compatible entry for build configurations that still reference
 * `qunzip.main`. Behaves like `mainCli`.
 */
fun main(args: Array<String>) = mainCli(args)

private fun runApp(
    args: List<String>,
    useGui: Boolean,
    isStandalone: Boolean,
    logger: Logger
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    runBlocking {
        try {
            val dependencies = initializeDependencies()
            val applicationViewModel = ApplicationViewModel(
                extractArchiveUseCase = dependencies.extractArchiveUseCase,
                validateArchiveUseCase = dependencies.validateArchiveUseCase,
                manageFileAssociationsUseCase = dependencies.manageFileAssociationsUseCase,
                preferencesRepository = dependencies.preferencesRepository,
                cliShimRepository = dependencies.cliShimRepository,
                scope = applicationScope,
                logger = logger,
                isStandaloneLaunch = isStandalone
            )

            applicationViewModel.handleApplicationStart(args)

            val renderer: UiRenderer = if (useGui) {
                createGuiRenderer()
            } else {
                if (isTerminal()) {
                    configureStandaloneConsole()
                    MosaicTuiRenderer(isStandaloneLaunch = false)
                } else {
                    // Piped output (test harness, redirected) — non-interactive
                    MosaicTuiRenderer(isStandaloneLaunch = true)
                }
            }
            renderer.render(applicationViewModel)
            // Exit nonzero when the extraction ended in failure, so scripts (and
            // the file-association caller) can detect it.
            exitProcess(if (applicationViewModel.didExtractionFail) 1 else 0)
        } catch (e: Throwable) {
            // Catch Throwable, not Exception: ExtractionError extends Throwable
            // (e.g. a missing bundled 7z.exe) and the not-yet-implemented
            // Linux/macOS platforms throw NotImplementedError (an Error). Either
            // would otherwise escape as an uncaught crash with no error surfaced.
            logger.e(e) { "Fatal error during application startup" }
            exitProcess(1)
        } finally {
            applicationScope.cancel()
        }
    }
}

private fun handleRegisterAssociations() {
    runBlocking {
        try {
            val dependencies = initializeDependencies()
            val results = dependencies.manageFileAssociationsUseCase
                .registerAssociations(getGuiExecutablePath())

            val allSuccess = results.all { it.success }
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
        } catch (e: Throwable) {
            println("Error: ${e.message}")
            exitProcess(1)
        }
    }
}

private fun handleUnregisterAssociations() {
    runBlocking {
        try {
            val dependencies = initializeDependencies()
            val results = dependencies.manageFileAssociationsUseCase.unregisterAssociations()
            val allSuccess = results.all { it.success }
            val totalCount = results.size
            if (allSuccess) {
                println("Successfully unregistered all $totalCount file associations")
            }
            exitProcess(0)
        } catch (e: Throwable) {
            println("Error: ${e.message}")
            exitProcess(1)
        }
    }
}

private fun handleSetTrash(enabled: Boolean) {
    runBlocking {
        try {
            val dependencies = initializeDependencies()
            val current = dependencies.preferencesRepository.loadPreferences()
            val updated = current.copy(moveToTrashAfterExtraction = enabled)
            if (dependencies.preferencesRepository.savePreferences(updated)) {
                println("Setting updated: Move archive to trash after extraction = ${if (enabled) "ON" else "OFF"}")
                exitProcess(0)
            } else {
                println("Error: Failed to save preferences")
                exitProcess(1)
            }
        } catch (e: Throwable) {
            println("Error: ${e.message}")
            exitProcess(1)
        }
    }
}

private fun handleSetAutoClose(autoClose: Boolean) {
    runBlocking {
        try {
            val dependencies = initializeDependencies()
            val current = dependencies.preferencesRepository.loadPreferences()
            val updated = current.copy(autoCloseAfterExtraction = autoClose)
            if (dependencies.preferencesRepository.savePreferences(updated)) {
                val label = if (autoClose) "ON (default)" else "OFF (dialog stays open)"
                println("Setting updated: Auto-close after extraction = $label")
                exitProcess(0)
            } else {
                println("Error: Failed to save preferences")
                exitProcess(1)
            }
        } catch (e: Throwable) {
            println("Error: ${e.message}")
            exitProcess(1)
        }
    }
}

/**
 * Print help message
 */
fun printHelp() {
    println("""
        Quick Unzip - Archive Extraction Utility

        Usage: qunzip [OPTIONS] <archive-file>

        Arguments:
          <archive-file>              Path to archive file to extract

        Options:
          --set-trash-on              Enable moving archive to trash after extraction
          --set-trash-off             Disable moving archive to trash (default)
          --set-dialog-on             Keep window open after extraction
          --set-dialog-off            Close window automatically after extraction (default)
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
    val preferencesRepository: PreferencesRepository,
    val cliShimRepository: CliShimRepository
)

/**
 * Initialize application dependencies
 * Platform-specific implementation
 */
internal expect fun initializeDependencies(): ApplicationDependencies

/**
 * Get the path of the current executable (e.g., the qunzip.exe that's running).
 * Platform-specific implementation.
 */
internal expect fun getCurrentExecutablePath(): String

/**
 * Get the path to the GUI executable (QuickUnzip.exe on Windows). On platforms
 * that have only one binary, returns the same as `getCurrentExecutablePath()`.
 * Used by `--register-associations` so file associations point at the GUI.
 */
internal expect fun getGuiExecutablePath(): String

/**
 * Exit the application process
 * Platform-specific implementation
 */
internal expect fun exitProcess(code: Int): Nothing

/**
 * Create platform-specific GUI renderer (experimental)
 */
internal expect fun createGuiRenderer(): UiRenderer
