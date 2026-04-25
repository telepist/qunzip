package qunzip.presentation.ui.tui

import androidx.compose.runtime.*
import qunzip.presentation.viewmodels.FileAssociationViewModel
import qunzip.presentation.viewmodels.SettingsViewModel
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.*

private const val BOX_WIDTH = 52

private enum class MenuItem(val label: String) {
    MOVE_TO_TRASH("Move archive to trash after extraction"),
    AUTO_CLOSE("Close automatically after extraction"),
    CLI_SHIM("Add `qunzip` command to PATH"),
    QUIT("Quit")
}

@Composable
fun SettingsTui(
    fileAssociationViewModel: FileAssociationViewModel,
    settingsViewModel: SettingsViewModel,
    onExit: () -> Unit = {}
) {
    val fileAssocState by fileAssociationViewModel.uiState.collectAsState()
    val settingsState by settingsViewModel.uiState.collectAsState()
    val prefs = settingsState.preferences

    var selectedIndex by remember { mutableStateOf(0) }
    // Hide the CLI shim row on platforms where install/uninstall is a no-op,
    // so we don't offer a switch that does nothing.
    val menuItems = MenuItem.entries.filter { item ->
        item != MenuItem.CLI_SHIM || settingsState.cliShimSupported
    }

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
                        MenuItem.MOVE_TO_TRASH -> {
                            settingsViewModel.setMoveToTrashAfterExtraction(!prefs.moveToTrashAfterExtraction)
                        }
                        MenuItem.AUTO_CLOSE -> {
                            settingsViewModel.setAutoCloseAfterExtraction(!prefs.autoCloseAfterExtraction)
                        }
                        MenuItem.CLI_SHIM -> {
                            settingsViewModel.setCliShimInstalled(!settingsState.cliShimInstalled)
                        }
                        MenuItem.QUIT -> {
                            onExit()
                        }
                    }
                    true
                }
                KeyEvent("q") -> {
                    onExit()
                    true
                }
                else -> false
            }
        }
    ) {
        Text("")

        // Header
        SettingsHeader("Quick Unzip Settings")
        Text("")

        // Preferences section
        Text("  Preferences", color = Color.White, textStyle = TextStyle.Bold)
        Text("")

        // Menu items
        menuItems.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex

            when (item) {
                MenuItem.MOVE_TO_TRASH -> {
                    ToggleRow(
                        label = item.label,
                        enabled = prefs.moveToTrashAfterExtraction,
                        selected = isSelected
                    )
                }
                MenuItem.AUTO_CLOSE -> {
                    ToggleRow(
                        label = item.label,
                        enabled = prefs.autoCloseAfterExtraction,
                        selected = isSelected
                    )
                }
                MenuItem.CLI_SHIM -> {
                    ToggleRow(
                        label = item.label,
                        enabled = settingsState.cliShimInstalled,
                        selected = isSelected
                    )
                }
                MenuItem.QUIT -> {
                    Text("")
                    Row {
                        val cursor = if (isSelected) ">" else " "
                        val textColor = if (isSelected) Color.Cyan else Color(170, 170, 170)
                        Text("  $cursor ", color = Color.Cyan)
                        Text(item.label, color = textColor)
                    }
                }
            }
        }

        Text("")

        // File Associations box
        val inner = BOX_WIDTH - 4
        Text("  ╭${"─".repeat(inner + 2)}╮", color = Color(80, 80, 80))

        Row {
            Text("  │ ", color = Color(80, 80, 80))
            Text("File Associations", color = Color.White, textStyle = TextStyle.Bold)
            Text(" ".repeat((inner - 17).coerceAtLeast(0)))
            Text(" │", color = Color(80, 80, 80))
        }

        Row {
            Text("  │ ", color = Color(80, 80, 80))
            val statusText: String
            val statusColor: Color
            if (fileAssocState.isRegistered) {
                statusText = "Registered"
                statusColor = Color.Green
            } else {
                statusText = "Not registered"
                statusColor = Color(170, 170, 170)
            }
            Text("Status: ", color = Color(100, 100, 100))
            Text(statusText, color = statusColor)
            Text(" ".repeat((inner - 8 - statusText.length).coerceAtLeast(0)))
            Text(" │", color = Color(80, 80, 80))
        }

        Row {
            Text("  │ ", color = Color(80, 80, 80))
            val formats = ".zip .7z .rar .tar .cab .arj"
            Text(formats, color = Color(100, 100, 100))
            Text(" ".repeat((inner - formats.length).coerceAtLeast(0)))
            Text(" │", color = Color(80, 80, 80))
        }

        Text("  ╰${"─".repeat(inner + 2)}╯", color = Color(80, 80, 80))

        Text("")

        // Keybindings hint
        Row {
            Text("  ")
            Text("^", color = Color(170, 170, 170), background = Color(40, 40, 40))
            Text("v", color = Color(170, 170, 170), background = Color(40, 40, 40))
            Text(" navigate  ", color = Color(100, 100, 100))
            Text("space", color = Color(170, 170, 170), background = Color(40, 40, 40))
            Text(" toggle  ", color = Color(100, 100, 100))
            Text("q", color = Color(170, 170, 170), background = Color(40, 40, 40))
            Text(" quit", color = Color(100, 100, 100))
        }

        // Status messages
        if (settingsState.isSaving) {
            Text("")
            Text("  Saving...", color = Color.Yellow)
        }
        if (settingsState.error != null) {
            Text("")
            Text("  Error: ${settingsState.error}", color = Color.Red)
        }
    }
}

@Composable
private fun SettingsHeader(title: String) {
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
private fun ToggleRow(label: String, enabled: Boolean, selected: Boolean) {
    Row {
        val cursor = if (selected) ">" else " "
        Text("  $cursor ", color = Color.Cyan)

        if (enabled) {
            Text("[", color = Color(80, 80, 80))
            Text("*", color = Color.Green)
            Text("] ", color = Color(80, 80, 80))
        } else {
            Text("[ ] ", color = Color(80, 80, 80))
        }

        val textColor = if (selected) Color.White else Color(170, 170, 170)
        Text(label, color = textColor)
    }
}
