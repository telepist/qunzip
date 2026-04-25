package qunzip.presentation.ui.tui

import androidx.compose.runtime.*
import qunzip.presentation.viewmodels.ExtractionViewModel
import qunzip.domain.entities.ExtractionStage
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.*

private const val BOX_WIDTH = 52

@Composable
fun ExtractionTui(viewModel: ExtractionViewModel, showCloseHint: Boolean = false) {
    val uiState by viewModel.uiState.collectAsState()
    val progress = uiState.progress
    val stage = progress?.stage

    // Password input state
    var passwordInput by remember { mutableStateOf("") }

    Column(
        modifier = if (uiState.isWaitingForPassword) {
            Modifier.onKeyEvent { event ->
                when (event) {
                    KeyEvent("Enter") -> {
                        if (passwordInput.isNotEmpty()) {
                            val pw = passwordInput
                            passwordInput = ""
                            viewModel.submitPassword(pw)
                        }
                        true
                    }
                    KeyEvent("Escape") -> {
                        passwordInput = ""
                        viewModel.cancelExtraction()
                        true
                    }
                    KeyEvent("Backspace") -> {
                        if (passwordInput.isNotEmpty()) {
                            passwordInput = passwordInput.dropLast(1)
                        }
                        true
                    }
                    else -> {
                        // Append printable characters
                        val key = event.key
                        if (key.length == 1) {
                            passwordInput += key
                            viewModel.clearPasswordError()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        } else Modifier
    ) {
        Text("")
        // Header
        Header("Quick Unzip")
        Text("")

        // Archive info
        val archiveName = uiState.archive?.name
            ?: uiState.currentArchive?.substringAfterLast("/")?.substringAfterLast("\\")
            ?: ""
        val archiveFormat = uiState.archive?.format?.name ?: ""
        val archiveSize = uiState.archive?.size?.let { formatBytes(it) } ?: ""
        val formatInfo = if (archiveFormat.isNotEmpty() && archiveSize.isNotEmpty()) {
            "$archiveFormat  ·  $archiveSize"
        } else {
            archiveFormat
        }

        if (archiveName.isNotEmpty()) {
            InfoRow("Archive", archiveName, Color.White)
        }
        if (formatInfo.isNotEmpty()) {
            InfoRow("Format", formatInfo, Color(170, 170, 170))
        }

        Text("")

        if (uiState.isWaitingForPassword) {
            // Password prompt
            PasswordPrompt(passwordInput, uiState.error)
        } else {
            // Progress bar
            val percentage = progress?.progressPercentage?.toInt()?.coerceIn(0, 100) ?: 0
            ProgressBar(percentage, stage == ExtractionStage.COMPLETED, stage == ExtractionStage.FAILED)

            Text("")

            // Status & current file
            when (stage) {
                ExtractionStage.STARTING -> {
                    StatusLine("Starting...")
                }
                ExtractionStage.ANALYZING -> {
                    StatusLine("Analyzing archive...")
                }
                ExtractionStage.EXTRACTING -> {
                    val currentFile = progress?.currentFile?.let { truncatePath(it, 42) } ?: ""
                    if (currentFile.isNotEmpty()) {
                        StatusLine(currentFile)
                    }
                    val filesText = "${progress?.filesProcessed ?: 0} / ${progress?.totalFiles ?: 0} files"
                    val bytesText = formatBytes(progress?.bytesProcessed ?: 0)
                    InfoRow("Progress", "$filesText  ·  $bytesText", Color.White)
                }
                ExtractionStage.FINALIZING -> {
                    StatusLine("Finalizing...")
                }
                ExtractionStage.COMPLETED -> {
                    Row {
                        Text("  ")
                        Text(" Done ", color = Color.Black, background = Color.Green, textStyle = TextStyle.Bold)
                        Text("  ")
                        val totalFiles = progress?.totalFiles ?: 0
                        val totalBytes = formatBytes(progress?.totalBytes ?: 0)
                        Text("$totalFiles files  ·  $totalBytes", color = Color.White)
                    }
                }
                ExtractionStage.FAILED -> {
                    Row {
                        Text("  ")
                        Text(" Error ", color = Color.White, background = Color.Red, textStyle = TextStyle.Bold)
                        Text("  ")
                        Text(uiState.error ?: "Extraction failed", color = Color.Red)
                    }
                }
                else -> {
                    StatusLine("Waiting...")
                }
            }
        }

        Text("")

        if (showCloseHint && (stage == ExtractionStage.COMPLETED || stage == ExtractionStage.FAILED)) {
            StatusLine("Close this window to exit.")
            Text("")
        }
    }
}

@Composable
private fun PasswordPrompt(passwordInput: String, error: String?) {
    Row {
        Text("    ")
        Text("Password required", color = Color.Yellow, textStyle = TextStyle.Bold)
    }
    Text("")

    if (error != null) {
        Row {
            Text("    ")
            Text(error, color = Color.Red)
        }
        Text("")
    }

    Row {
        Text("    ")
        Text("Password: ", color = Color(170, 170, 170))
        val masked = "*".repeat(passwordInput.length)
        Text(masked, color = Color.White)
        Text("_", color = Color(100, 100, 100))
    }

    Text("")

    Row {
        Text("    ")
        Text("enter", color = Color(170, 170, 170), background = Color(40, 40, 40))
        Text(" submit  ", color = Color(100, 100, 100))
        Text("esc", color = Color(170, 170, 170), background = Color(40, 40, 40))
        Text(" cancel", color = Color(100, 100, 100))
    }
}

@Composable
private fun Header(title: String) {
    val inner = BOX_WIDTH - 4
    Text("  ╭${"─".repeat(inner + 2)}╮", color = Color.Cyan)
    Row {
        Text("  │ ", color = Color.Cyan)
        Text(title, color = Color.White, textStyle = TextStyle.Bold)
        Text(" ".repeat((inner - title.length).coerceAtLeast(0)))
        Text(" │", color = Color.Cyan)
    }
    Text("  ╰${"─".repeat(inner + 2)}╯", color = Color.Cyan)
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color) {
    Row {
        Text("    ")
        Text(label.padEnd(10), color = Color(100, 100, 100))
        Text(value, color = valueColor)
    }
}

@Composable
private fun StatusLine(text: String) {
    Row {
        Text("    ")
        Text(text, color = Color(170, 170, 170))
    }
}

@Composable
private fun ProgressBar(percentage: Int, isComplete: Boolean, isFailed: Boolean) {
    val barWidth = BOX_WIDTH - 10
    val filled = (barWidth * percentage / 100).coerceIn(0, barWidth)
    val empty = barWidth - filled

    val barColor = when {
        isFailed -> Color.Red
        isComplete -> Color.Green
        else -> Color.Cyan
    }

    Row {
        Text("    ")
        Text("━".repeat(filled), color = barColor)
        if (empty > 0) {
            Text("━".repeat(empty), color = Color(60, 60, 60))
        }
        Text("  ")
        Text("${percentage}%".padStart(4), color = barColor, textStyle = TextStyle.Bold)
    }
}

private fun truncatePath(path: String, maxLen: Int): String {
    if (path.length <= maxLen) return path
    return "..." + path.takeLast(maxLen - 3)
}

private fun formatBytes(bytes: Long) = qunzip.presentation.ui.formatBytes(bytes)
