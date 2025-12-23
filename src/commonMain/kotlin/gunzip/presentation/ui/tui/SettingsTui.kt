package gunzip.presentation.ui.tui

import androidx.compose.runtime.*
import gunzip.presentation.viewmodels.FileAssociationViewModel
import gunzip.presentation.viewmodels.SettingsViewModel
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.*
import kotlin.system.exitProcess

/**
 * Menu items for the settings screen
 */
private enum class SettingsMenuItem(val label: String) {
    MOVE_TO_TRASH("Move archive to trash after extraction"),
    SHOW_NOTIFICATION("Show completion notification"),
    AUTO_CLOSE("Auto-close after extraction"),
    QUIT("Quit")
}

/**
 * Mosaic TUI for settings and file associations with keyboard navigation
 */
@Composable
fun SettingsTui(
    fileAssociationViewModel: FileAssociationViewModel,
    settingsViewModel: SettingsViewModel,
    onExit: () -> Unit = {}
) {
    val fileAssocState by fileAssociationViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val prefs = settingsState.preferences

    // Track selected menu item
    var selectedIndex by remember { mutableStateOf(0) }
    val menuItems = SettingsMenuItem.entries

    // Handle keyboard input
    Column(
        modifier = Modifier.onKeyEvent { event ->
            when (event) {
                KeyEvent("ArrowUp"), KeyEvent("k") -> {
                    selectedIndex = (selectedIndex - 1).coerceAtLeast(0)
                    true
                }
                KeyEvent("ArrowDown"), KeyEvent("j") -> {
                    selectedIndex = (selectedIndex + 1).coerceAtMost(menuItems.size - 1)
                    true
                }
                KeyEvent("Enter"), KeyEvent(" ") -> {
                    when (menuItems[selectedIndex]) {
                        SettingsMenuItem.MOVE_TO_TRASH -> {
                            settingsViewModel.setMoveToTrashAfterExtraction(!prefs.moveToTrashAfterExtraction)
                        }
                        SettingsMenuItem.SHOW_NOTIFICATION -> {
                            settingsViewModel.setShowCompletionNotification(!prefs.showCompletionNotification)
                        }
                        SettingsMenuItem.AUTO_CLOSE -> {
                            settingsViewModel.setAutoCloseAfterExtraction(!prefs.autoCloseAfterExtraction)
                        }
                        SettingsMenuItem.QUIT -> {
                            exitProcess(0)
                        }
                    }
                    true
                }
                KeyEvent("q") -> {
                    exitProcess(0)
                    true
                }
                else -> false
            }
        }
    ) {
        // Header
        Text("┌────────────── Gunzip Settings ──────────────┐", color = Color.Cyan)
        Text("│", color = Color.Cyan)

        // Instructions
        Text("│  Use ↑/↓ to navigate, Enter/Space to toggle", color = Color.Green)
        Text("│", color = Color.Cyan)

        // Preferences Section
        Text("│  Extraction Preferences", color = Color.White)
        Text("│", color = Color.Cyan)

        // Menu items
        menuItems.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            val prefix = if (isSelected) "│  ▶ " else "│    "

            when (item) {
                SettingsMenuItem.MOVE_TO_TRASH -> {
                    val icon = if (prefs.moveToTrashAfterExtraction) "✓" else "✗"
                    val valueColor = if (prefs.moveToTrashAfterExtraction) Color.Green else Color.Red
                    val textColor = if (isSelected) Color.Yellow else Color.White
                    Row {
                        Text(prefix, color = Color.Cyan)
                        Text("[$icon] ", color = valueColor)
                        Text(item.label, color = textColor)
                    }
                }
                SettingsMenuItem.SHOW_NOTIFICATION -> {
                    val icon = if (prefs.showCompletionNotification) "✓" else "✗"
                    val valueColor = if (prefs.showCompletionNotification) Color.Green else Color.Red
                    val textColor = if (isSelected) Color.Yellow else Color.White
                    Row {
                        Text(prefix, color = Color.Cyan)
                        Text("[$icon] ", color = valueColor)
                        Text(item.label, color = textColor)
                    }
                }
                SettingsMenuItem.AUTO_CLOSE -> {
                    val icon = if (prefs.autoCloseAfterExtraction) "✓" else "✗"
                    val valueColor = if (prefs.autoCloseAfterExtraction) Color.Green else Color.Red
                    val textColor = if (isSelected) Color.Yellow else Color.White
                    Row {
                        Text(prefix, color = Color.Cyan)
                        Text("[$icon] ", color = valueColor)
                        Text(item.label, color = textColor)
                    }
                }
                SettingsMenuItem.QUIT -> {
                    val textColor = if (isSelected) Color.Yellow else Color.Green
                    Text("$prefix${item.label}", color = textColor)
                }
            }
        }

        Text("│", color = Color.Cyan)

        // Config file location
        if (settingsState.preferencesPath.isNotEmpty()) {
            Text("│  Settings file:", color = Color.White)
            val displayPath = if (settingsState.preferencesPath.length > 40) {
                "..." + settingsState.preferencesPath.takeLast(37)
            } else {
                settingsState.preferencesPath
            }
            Text("│    $displayPath", color = Color.White)
            Text("│", color = Color.Cyan)
        }

        // Divider
        Text("│──────────────────────────────────────────────│", color = Color.Cyan)
        Text("│", color = Color.Cyan)

        // File Associations Section
        Text("│  File Associations", color = Color.White)
        Text("│", color = Color.Cyan)

        // Registration status
        val statusText = if (fileAssocState.isRegistered) "✅ Registered" else "⭕ Not Registered"
        val statusColor = if (fileAssocState.isRegistered) Color.Green else Color.Yellow
        Text("│  Status: $statusText", color = statusColor)

        // Extension list (compact)
        Text("│  Formats: .zip .7z .rar .tar .tar.gz .cab", color = Color.White)

        Text("│", color = Color.Cyan)

        // Footer
        Text("└──────────────────────────────────────────────┘", color = Color.Cyan)

        // Status messages
        if (settingsState.isSaving) {
            Text("  Saving...", color = Color.Yellow)
        }
        if (settingsState.error != null) {
            Text("  Error: ${settingsState.error}", color = Color.Red)
        }
    }
}
