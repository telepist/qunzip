@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package qunzip.presentation.ui

import cimgui.*
import kotlinx.cinterop.*
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
        val extractionVm = viewModel.extractionViewModel

        // Build window title from archive name or settings
        val archivePath = extractionVm.uiState.value.currentArchive
        val initialTitle = if (archivePath != null) {
            val name = archivePath.substringAfterLast("/").substringAfterLast("\\")
            "Quick Unzip - $name"
        } else {
            "Quick Unzip"
        }

        val windowWidth = if (archivePath != null) 480 else 400
        val windowHeight = if (archivePath != null) 180 else 200

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
        val stage = state.progress?.stage

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
        val pct = state.progress?.progressPercentage?.coerceIn(0f, 100f) ?: 0f
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

        // Status line
        when (stage) {
            ExtractionStage.EXTRACTING -> {
                val files = "${state.progress?.filesProcessed ?: 0} / ${state.progress?.totalFiles ?: 0} files"
                val bytes = formatBytes(state.progress?.bytesProcessed ?: 0)
                safeText("$files  -  $bytes")
                state.progress?.currentFile?.let { file ->
                    val display = if (file.length > 55) "..." + file.takeLast(52) else file
                    safeTextColored(0.5f, 0.5f, 0.5f, 1f, display)
                }
            }
            ExtractionStage.COMPLETED -> {
                val totalFiles = state.progress?.totalFiles ?: 0
                val totalBytes = formatBytes(state.progress?.totalBytes ?: 0)
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
