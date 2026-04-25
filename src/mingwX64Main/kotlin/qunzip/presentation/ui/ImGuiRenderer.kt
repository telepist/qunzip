@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package qunzip.presentation.ui

import cimgui.*
import kotlinx.cinterop.*
import kotlinx.coroutines.flow.first
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.viewmodels.ApplicationMode
import qunzip.domain.entities.ExtractionStage

private fun vec2(x: Float, y: Float) = cValue<ImVec2> { this.x = x; this.y = y }

/** Safe text display — uses igTextUnformatted to avoid printf format string injection. */
private fun safeText(text: String) = igTextUnformatted(text, null)

/** Safe colored text — uses "%s" format to avoid injection. */
private fun safeTextColored(r: Float, g: Float, b: Float, a: Float, text: String) {
    igTextColored(cValue<ImVec4> { x = r; y = g; z = b; w = a }, "%s", text)
}

class ImGuiRenderer : UiRenderer {
    override suspend fun render(viewModel: ApplicationViewModel) {
        // Wait for the application to settle into a mode before creating the
        // OS window — handleApplicationStart populates targetFile / mode
        // asynchronously, so reading them synchronously here would always
        // see the initial defaults and we'd size the window for the wrong
        // mode (e.g. settings dimensions for an extraction launch).
        val initialAppState = viewModel.uiState.first { !it.isStarting }
        val isExtraction = initialAppState.mode == ApplicationMode.EXTRACTION

        val initialTitle = if (isExtraction) {
            val name = initialAppState.targetFile
                ?.substringAfterLast("/")?.substringAfterLast("\\")
                ?: ""
            if (name.isNotEmpty()) "Quick Unzip - $name" else "Quick Unzip"
        } else {
            "Quick Unzip"
        }

        // Extraction view is wider but shorter than the settings panel —
        // settings has more rows (preferences + supported formats), the
        // extraction view only needs archive name + progress + status.
        val windowWidth = if (isExtraction) 520 else 460
        val windowHeight = if (isExtraction) 140 else 220

        val app = imgui_app_create(initialTitle, windowWidth, windowHeight)
        if (app == null) {
            // DX11 init failed — fall back to TUI
            configureStandaloneConsole()
            MosaicTuiRenderer(isStandaloneLaunch = true).render(viewModel)
            return
        }

        var exitFrames = -1

        try {
            while (imgui_app_begin_frame(app) != 0) {
                val appState = viewModel.uiState.value

                // Full-window ImGui panel
                val viewport = igGetMainViewport()!!.pointed
                igSetNextWindowPos(viewport.WorkPos.readValue(), 0, vec2(0f, 0f))
                igSetNextWindowSize(viewport.WorkSize.readValue(), 0)
                igBegin(
                    "##main", null,
                    (ImGuiWindowFlags_NoTitleBar or ImGuiWindowFlags_NoResize or
                     ImGuiWindowFlags_NoMove or ImGuiWindowFlags_NoCollapse).toInt()
                )

                when (appState.mode) {
                    ApplicationMode.EXTRACTION -> drawExtraction(viewModel)
                    ApplicationMode.SETUP -> drawSettings(viewModel)
                }

                igEnd()
                imgui_app_end_frame(app)

                // Auto-close when ApplicationViewModel decides to exit
                // (respects autoCloseAfterExtraction preference)
                if (appState.shouldExit) {
                    if (exitFrames < 0) exitFrames = 60  // ~1s at 60fps vsync
                    if (exitFrames-- <= 0) break
                }
            }
        } finally {
            imgui_app_destroy(app)
        }
    }

    private fun drawExtraction(viewModel: ApplicationViewModel) {
        val state = viewModel.extractionViewModel.uiState.value
        val progress = state.progress
        val stage = progress?.stage

        // Archive name
        val archiveName = state.archive?.name
            ?: state.currentArchive?.substringAfterLast("/")?.substringAfterLast("\\")
            ?: ""
        if (archiveName.isNotEmpty()) {
            safeText(archiveName)
        }

        // Format info
        val archiveFormat = state.archive?.format?.name ?: ""
        val archiveSize = state.archive?.size?.let { formatBytes(it) } ?: ""
        if (archiveFormat.isNotEmpty()) {
            val info = if (archiveSize.isNotEmpty()) "$archiveFormat  -  $archiveSize" else archiveFormat
            safeTextColored(0.6f, 0.6f, 0.6f, 1f, info)
        }

        igSpacing()

        // Progress bar — hide during early stages
        val pct = progress?.progressPercentage?.coerceIn(0f, 100f) ?: 0f
        if (stage == null || stage == ExtractionStage.ANALYZING || stage == ExtractionStage.STARTING) {
            val msg = when (stage) {
                ExtractionStage.STARTING -> "Starting..."
                ExtractionStage.ANALYZING -> "Analyzing archive..."
                else -> "Preparing..."
            }
            safeTextColored(0.6f, 0.6f, 0.6f, 1f, msg)
        } else {
            val fraction = when (stage) {
                ExtractionStage.COMPLETED -> 1f
                else -> if (pct > 0f) pct / 100f else 0f
            }
            val overlay = when (stage) {
                ExtractionStage.EXTRACTING -> "${pct.toInt()}%"
                ExtractionStage.FINALIZING -> "Finalizing..."
                ExtractionStage.COMPLETED -> "Done!"
                ExtractionStage.FAILED -> "Failed"
                else -> ""
            }
            igProgressBar(fraction, vec2(-1f, 0f), overlay)
        }

        igSpacing()

        // Status line.
        // `progress` is smart-cast non-null in branches where stage matched a
        // specific ExtractionStage value (since stage = progress?.stage).
        when (stage) {
            ExtractionStage.EXTRACTING -> {
                val files = "${progress.filesProcessed} / ${progress.totalFiles} files"
                val bytes = formatBytes(progress.bytesProcessed)
                safeText("$files  -  $bytes")
                progress.currentFile?.let { file ->
                    val display = if (file.length > 55) "..." + file.takeLast(52) else file
                    safeTextColored(0.5f, 0.5f, 0.5f, 1f, display)
                }
            }
            ExtractionStage.COMPLETED -> {
                val totalFiles = progress.totalFiles
                val totalBytes = formatBytes(progress.totalBytes)
                safeTextColored(0.4f, 0.9f, 0.4f, 1f, "Done! $totalFiles files  -  $totalBytes")
            }
            ExtractionStage.FAILED -> {
                safeTextColored(1f, 0.3f, 0.3f, 1f, state.error ?: "Extraction failed")
            }
            else -> {
                if (state.isWaitingForPassword) {
                    safeTextColored(1f, 0.8f, 0.2f, 1f, "Password-protected archive.")
                    safeTextColored(0.6f, 0.6f, 0.6f, 1f, "Use CLI mode: qunzip <archive>")
                    viewModel.extractionViewModel.cancelExtraction()
                }
            }
        }
    }

    private fun drawSettings(viewModel: ApplicationViewModel) = memScoped {
        val settingsVm = viewModel.settingsViewModel
        val state = settingsVm.uiState.value
        val prefs = state.preferences

        safeText("Preferences")
        igSpacing()
        igSeparator()
        igSpacing()

        // Move to trash toggle
        val trash = alloc<BooleanVar> { value = prefs.moveToTrashAfterExtraction }
        if (igCheckbox("Move archive to trash after extraction", trash.ptr)) {
            settingsVm.setMoveToTrashAfterExtraction(trash.value)
        }

        // Auto-close toggle
        val autoClose = alloc<BooleanVar> { value = prefs.autoCloseAfterExtraction }
        if (igCheckbox("Close automatically after extraction", autoClose.ptr)) {
            settingsVm.setAutoCloseAfterExtraction(autoClose.value)
        }

        // CLI shim toggle (only shown on platforms that support it)
        if (state.cliShimSupported) {
            val shim = alloc<BooleanVar> { value = state.cliShimInstalled }
            val disabled = state.isCliShimWorking
            if (disabled) igBeginDisabled(true)
            if (igCheckbox("Add `qunzip` command to PATH", shim.ptr)) {
                settingsVm.setCliShimInstalled(shim.value)
            }
            if (disabled) igEndDisabled()
            val hint = state.cliShimMessage
                ?: "Lets you run `qunzip <archive>` from any terminal."
            safeTextColored(0.55f, 0.55f, 0.55f, 1f, hint)
        }

        igSpacing()
        igSeparator()
        igSpacing()

        // Supported formats
        safeTextColored(0.6f, 0.6f, 0.6f, 1f, "Supported formats:")
        safeTextColored(0.5f, 0.5f, 0.5f, 1f, ".zip  .7z  .rar  .tar  .cab  .arj  .lzh")

        igSpacing()

        // Status
        if (state.isSaving) {
            safeTextColored(1f, 0.8f, 0.2f, 1f, "Saving...")
        }
        if (state.error != null) {
            safeTextColored(1f, 0.3f, 0.3f, 1f, "Error: ${state.error}")
        }
    }
}
