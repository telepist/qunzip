package gunzip.presentation.ui.tui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import gunzip.presentation.viewmodels.ExtractionViewModel
import gunzip.domain.entities.ExtractionStage
import com.jakewharton.mosaic.ui.*

/**
 * Mosaic TUI for extraction progress
 * Shows real-time progress with bars, colors, and status
 */
@Composable
fun ExtractionTui(viewModel: ExtractionViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    Column {
        // Header with border
        Text("┌─── Gunzip Archive Extractor ───┐", color = Color.Cyan)
        Text("│", color = Color.Cyan)

        // Archive name
        uiState.archive?.let { archive ->
            Text("│ Archive: ${archive.name}", color = Color.White)
        }

        // Current file being extracted
        uiState.progress?.currentFile?.let { currentFile ->
            val displayFile = if (currentFile.length > 30) {
                "..." + currentFile.takeLast(27)
            } else {
                currentFile
            }
            Text("│ Current: $displayFile", color = Color.Black)
        }

        Text("│", color = Color.Cyan)

        // Progress bar
        uiState.progress?.let { progress ->
            val percentage = progress.progressPercentage
            val barWidth = 40
            val filled = (barWidth * percentage / 100).toInt().coerceIn(0, barWidth)
            val bar = "█".repeat(filled) + "░".repeat(barWidth - filled)

            Text("│ [$bar] $percentage%", color = Color.Green)

            // Stats line
            val filesText = "${progress.filesProcessed}/${progress.totalFiles} files"
            val bytesText = formatBytes(progress.bytesProcessed)
            Text("│ $filesText  •  $bytesText", color = Color.White)
        }

        Text("│", color = Color.Cyan)

        // Stage indicator with emoji
        val stageText = when (uiState.progress?.stage) {
            ExtractionStage.STARTING -> "⏳ Starting..."
            ExtractionStage.ANALYZING -> "🔍 Analyzing archive..."
            ExtractionStage.EXTRACTING -> "📦 Extracting files..."
            ExtractionStage.FINALIZING -> "✨ Finalizing..."
            ExtractionStage.COMPLETED -> "✅ Complete!"
            ExtractionStage.FAILED -> "❌ Failed"
            else -> "⏸ Idle"
        }
        Text("│ $stageText", color = Color.Yellow)

        // Error display
        if (uiState.error != null) {
            Text("│", color = Color.Cyan)
            Text("│ Error: ${uiState.error}", color = Color.Red)
        }

        // Footer border
        Text("└────────────────────────────────┘", color = Color.Cyan)
    }
}

/**
 * Format bytes to human-readable format
 */
private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
