@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package qunzip.presentation.ui

import cimgui.*
import kotlinx.cinterop.*
import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.domain.entities.ExtractionStage
import platform.windows.Sleep

private fun vec2(x: Float, y: Float) = cValue<ImVec2> { this.x = x; this.y = y }
private fun vec4(r: Float, g: Float, b: Float, a: Float) = cValue<ImVec4> { x = r; y = g; z = b; w = a }

class ImGuiRenderer : UiRenderer {
    override suspend fun render(viewModel: ApplicationViewModel) {
        val app = imgui_app_create("qunzip", 480, 180) ?: return
        val extractionVm = viewModel.extractionViewModel

        try {
            while (imgui_app_begin_frame(app) != 0) {
                val state = extractionVm.uiState.value
                val stage = state.progress?.stage

                // Full-window ImGui panel (no title bar, no resize, fixed to window)
                val viewport = igGetMainViewport()!!.pointed
                igSetNextWindowPos(viewport.WorkPos.readValue(), 0, vec2(0f, 0f))
                igSetNextWindowSize(viewport.WorkSize.readValue(), 0)
                igBegin(
                    "##main",
                    null,
                    (ImGuiWindowFlags_NoTitleBar or ImGuiWindowFlags_NoResize or
                     ImGuiWindowFlags_NoMove or ImGuiWindowFlags_NoCollapse).toInt()
                )

                // Archive name
                val archiveName = state.archive?.name
                    ?: state.currentArchive?.substringAfterLast("/")?.substringAfterLast("\\")
                    ?: ""
                if (archiveName.isNotEmpty()) {
                    igText(archiveName)
                }

                // Format info
                val archiveFormat = state.archive?.format?.name ?: ""
                val archiveSize = state.archive?.size?.let { formatBytes(it) } ?: ""
                if (archiveFormat.isNotEmpty()) {
                    val info = if (archiveSize.isNotEmpty()) "$archiveFormat  -  $archiveSize" else archiveFormat
                    igTextColored(vec4(0.6f, 0.6f, 0.6f, 1f), info)
                }

                igSpacing()

                // Progress bar
                val pct = state.progress?.progressPercentage?.coerceIn(0f, 100f) ?: 0f
                val fraction = when {
                    stage == ExtractionStage.COMPLETED -> 1f
                    stage == ExtractionStage.ANALYZING || stage == ExtractionStage.STARTING -> -1f * igGetTime().toFloat()
                    pct > 0f -> pct / 100f
                    else -> 0f
                }
                val overlay = when (stage) {
                    ExtractionStage.STARTING -> "Starting..."
                    ExtractionStage.ANALYZING -> "Analyzing archive..."
                    ExtractionStage.EXTRACTING -> "${pct.toInt()}%"
                    ExtractionStage.FINALIZING -> "Finalizing..."
                    ExtractionStage.COMPLETED -> "Done!"
                    ExtractionStage.FAILED -> "Failed"
                    else -> ""
                }
                // Hide progress bar during early stages, show text instead
                if (stage == null || stage == ExtractionStage.ANALYZING || stage == ExtractionStage.STARTING) {
                    igTextColored(vec4(0.6f, 0.6f, 0.6f, 1f), if (overlay.isEmpty()) "Preparing..." else overlay)
                } else {
                    igProgressBar(fraction, vec2(-1f, 0f), overlay)
                }

                igSpacing()

                // Status line
                when (stage) {
                    ExtractionStage.EXTRACTING -> {
                        val files = "${state.progress?.filesProcessed ?: 0} / ${state.progress?.totalFiles ?: 0} files"
                        val bytes = formatBytes(state.progress?.bytesProcessed ?: 0)
                        igText("$files  -  $bytes")
                        state.progress?.currentFile?.let { file ->
                            val display = if (file.length > 55) "..." + file.takeLast(52) else file
                            igTextColored(vec4(0.5f, 0.5f, 0.5f, 1f), display)
                        }
                    }
                    ExtractionStage.COMPLETED -> {
                        val totalFiles = state.progress?.totalFiles ?: 0
                        val totalBytes = formatBytes(state.progress?.totalBytes ?: 0)
                        igTextColored(vec4(0.4f, 0.9f, 0.4f, 1f), "Done! $totalFiles files  -  $totalBytes")
                    }
                    ExtractionStage.FAILED -> {
                        igTextColored(vec4(1f, 0.3f, 0.3f, 1f), state.error ?: "Extraction failed")
                    }
                    else -> {}
                }

                igEnd()
                imgui_app_end_frame(app)

                // Auto-close after completion
                if (stage == ExtractionStage.COMPLETED || stage == ExtractionStage.FAILED) {
                    Sleep(500u)
                    break
                }
            }
        } finally {
            imgui_app_destroy(app)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> {
            val mb = bytes / (1024.0 * 1024.0)
            "${(mb * 10).toLong() / 10.0} MB"
        }
        else -> {
            val gb = bytes / (1024.0 * 1024.0 * 1024.0)
            "${(gb * 10).toLong() / 10.0} GB"
        }
    }
}
