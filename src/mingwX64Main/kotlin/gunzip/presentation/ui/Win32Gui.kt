package gunzip.presentation.ui

import gunzip.presentation.viewmodels.ApplicationViewModel
import gunzip.presentation.viewmodels.ApplicationMode
import gunzip.presentation.viewmodels.ExtractionUiState
import gunzip.domain.entities.ExtractionStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.cinterop.*
import platform.windows.*
import co.touchlab.kermit.Logger
import kotlin.system.exitProcess

// Progress bar messages
private const val PBM_SETRANGE32 = 0x406
private const val PBM_SETPOS = 0x402
private const val PBM_SETBARCOLOR = 0x409
private const val PBS_SMOOTH = 0x01

// Common Controls
private const val ICC_PROGRESS_CLASS = 0x00000020

/**
 * Windows Win32 native GUI renderer
 * Shows a simple progress window during extraction with completion notification
 */
@OptIn(ExperimentalForeignApi::class)
class Win32GuiRenderer : UiRenderer {
    private val logger = Logger.withTag("Win32Gui")

    private var hwnd: HWND? = null
    private var okButtonHwnd: HWND? = null
    private var progressBarHwnd: HWND? = null
    private var fileLabelHwnd: HWND? = null
    private var hFont: HFONT? = null
    private var statusText: String = "Initializing..."
    private var completionHandled = false
    private var showNotification = true

    companion object {
        private const val WINDOW_CLASS_NAME = "GunzipProgressWindow"
        private const val WINDOW_WIDTH = 450
        private const val WINDOW_HEIGHT = 200
        private const val IDC_STATUS_LABEL = 1001
        private const val IDC_OK_BUTTON = 1002
        private const val IDC_PROGRESS_BAR = 1003
        private const val IDC_FILE_LABEL = 1004

        // Layout constants
        private const val PADDING = 20
        private const val CONTROL_SPACING = 8
    }

    override suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope) {
        // Wait for the state to be ready (handleApplicationStart runs async)
        // Give it up to 2 seconds to switch to EXTRACTION mode
        var uiState = viewModel.uiState.value
        var waitCount = 0
        while (uiState.mode == ApplicationMode.SETUP && uiState.targetFile == null && waitCount < 20) {
            delay(100)
            uiState = viewModel.uiState.value
            waitCount++
        }

        logger.d { "Win32GuiRenderer: mode=${uiState.mode}, targetFile=${uiState.targetFile}" }

        // For settings mode, use native settings GUI
        if (uiState.mode == ApplicationMode.SETUP) {
            logger.i { "Settings mode - using Win32 settings GUI" }
            val settingsGui = Win32SettingsGui(viewModel, scope)
            if (settingsGui.show()) {
                settingsGui.runMessageLoop()
            } else {
                // Fall back to TUI if GUI fails
                logger.w { "Failed to create settings GUI, falling back to TUI" }
                val tuiRenderer = MosaicTuiRenderer()
                tuiRenderer.render(viewModel, scope)
            }
            return
        }

        // For extraction mode, use native GUI
        logger.i { "Starting Win32 GUI for extraction" }

        // Get notification preference
        showNotification = viewModel.settingsViewModel.uiState.value.preferences.showCompletionNotification

        val archiveName = uiState.targetFile?.substringAfterLast("\\")?.substringAfterLast("/") ?: "archive"

        // Create and show the progress window
        if (!createProgressWindow(archiveName)) {
            logger.e { "Failed to create progress window, falling back to TUI" }
            val tuiRenderer = MosaicTuiRenderer()
            tuiRenderer.render(viewModel, scope)
            return
        }

        // Launch coroutine to observe extraction state
        scope.launch {
            viewModel.extractionViewModel.uiState.collectLatest { state ->
                updateProgressWindow(state, archiveName)
            }
        }

        // Run the message loop
        runMessageLoop(viewModel, scope)
    }

    override fun isAvailable(): Boolean {
        // Win32 GUI is always available on Windows
        return true
    }

    private fun createProgressWindow(archiveName: String): Boolean = memScoped {
        val hInstance = GetModuleHandleW(null)

        // Initialize Common Controls for modern visual styles
        val icc = alloc<INITCOMMONCONTROLSEX>()
        icc.dwSize = sizeOf<INITCOMMONCONTROLSEX>().toUInt()
        icc.dwICC = ICC_PROGRESS_CLASS.toUInt()
        InitCommonControlsEx(icc.ptr)

        // Create modern font (Segoe UI, 10pt)
        hFont = CreateFontW(
            -16, 0, 0, 0,
            FW_NORMAL,
            0u, 0u, 0u,
            DEFAULT_CHARSET.toUInt(),
            OUT_DEFAULT_PRECIS.toUInt(),
            CLIP_DEFAULT_PRECIS.toUInt(),
            CLEARTYPE_QUALITY.toUInt(),
            (DEFAULT_PITCH or FF_DONTCARE).toUInt(),
            "Segoe UI"
        )

        // Register window class
        val wc = alloc<WNDCLASSEXW>()
        wc.cbSize = sizeOf<WNDCLASSEXW>().toUInt()
        wc.style = (CS_HREDRAW or CS_VREDRAW).toUInt()
        wc.lpfnWndProc = staticCFunction(::windowProc)
        wc.cbClsExtra = 0
        wc.cbWndExtra = 0
        wc.hInstance = hInstance
        wc.hIcon = LoadIconW(null, IDI_APPLICATION)
        wc.hCursor = LoadCursorW(null, IDC_ARROW)
        // Use a light gray background for modern look
        wc.hbrBackground = GetSysColorBrush(COLOR_BTNFACE)
        wc.lpszMenuName = null
        wc.lpszClassName = WINDOW_CLASS_NAME.wcstr.ptr
        wc.hIconSm = LoadIconW(null, IDI_APPLICATION)

        if (RegisterClassExW(wc.ptr) == 0.toUShort()) {
            val error = GetLastError()
            // Class might already be registered, continue
            if (error != ERROR_CLASS_ALREADY_EXISTS.toUInt()) {
                logger.e { "Failed to register window class: $error" }
                return false
            }
        }

        // Calculate center position
        val screenWidth = GetSystemMetrics(SM_CXSCREEN)
        val screenHeight = GetSystemMetrics(SM_CYSCREEN)
        val x = (screenWidth - WINDOW_WIDTH) / 2
        val y = (screenHeight - WINDOW_HEIGHT) / 2

        // Create window with modern extended style
        val windowTitle = "Gunzip - $archiveName"
        hwnd = CreateWindowExW(
            dwExStyle = WS_EX_DLGMODALFRAME.toUInt(),
            lpClassName = WINDOW_CLASS_NAME,
            lpWindowName = windowTitle,
            dwStyle = (WS_OVERLAPPED or WS_CAPTION or WS_SYSMENU).toUInt(),
            X = x,
            Y = y,
            nWidth = WINDOW_WIDTH,
            nHeight = WINDOW_HEIGHT,
            hWndParent = null,
            hMenu = null,
            hInstance = hInstance,
            lpParam = null
        )

        if (hwnd == null) {
            logger.e { "Failed to create window: ${GetLastError()}" }
            return false
        }

        // Layout calculations
        val contentWidth = WINDOW_WIDTH - (PADDING * 2) - 16  // Account for window borders
        var yPos = PADDING

        // Create status label (shows stage)
        val statusLabel = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "Extracting: $archiveName",
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 20,
            hWndParent = hwnd,
            hMenu = IDC_STATUS_LABEL.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(statusLabel)
        yPos += 20 + CONTROL_SPACING

        // Create progress bar
        progressBarHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "msctls_progress32",
            lpWindowName = null,
            dwStyle = (WS_CHILD or WS_VISIBLE or PBS_SMOOTH).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 20,
            hWndParent = hwnd,
            hMenu = IDC_PROGRESS_BAR.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        // Set progress bar range 0-100
        progressBarHwnd?.let {
            SendMessageW(it, PBM_SETRANGE32.toUInt(), 0u, 100)
        }
        yPos += 20 + CONTROL_SPACING

        // Create file label (shows current file being extracted)
        fileLabelHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "",
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt() or SS_PATHELLIPSIS.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 18,
            hWndParent = hwnd,
            hMenu = IDC_FILE_LABEL.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(fileLabelHwnd)
        yPos += 18 + CONTROL_SPACING + 4  // Extra space before button

        // Create OK button (initially hidden)
        val buttonWidth = 88
        val buttonHeight = 26
        okButtonHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "OK",
            dwStyle = (WS_CHILD or BS_DEFPUSHBUTTON.toInt()).toUInt(),
            X = (WINDOW_WIDTH - buttonWidth - 16) / 2,  // Center accounting for borders
            Y = yPos,
            nWidth = buttonWidth,
            nHeight = buttonHeight,
            hWndParent = hwnd,
            hMenu = IDC_OK_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(okButtonHwnd)

        ShowWindow(hwnd, SW_SHOW)
        UpdateWindow(hwnd)

        logger.i { "Progress window created successfully" }
        return true
    }

    private fun applyFont(hwnd: HWND?) {
        hFont?.let { font ->
            hwnd?.let { window ->
                SendMessageW(window, WM_SETFONT.toUInt(), font.toLong().toULong(), 1)
            }
        }
    }

    private fun updateProgressWindow(state: ExtractionUiState, archiveName: String) {
        val window = hwnd ?: return

        // Update status label with current stage
        val statusMessage = when (state.progress?.stage) {
            ExtractionStage.STARTING -> "Starting extraction..."
            ExtractionStage.ANALYZING -> "Analyzing archive..."
            ExtractionStage.EXTRACTING -> "Extracting: $archiveName"
            ExtractionStage.FINALIZING -> "Finalizing..."
            ExtractionStage.COMPLETED -> "Extraction complete!"
            ExtractionStage.FAILED -> "Extraction failed"
            else -> "Extracting: $archiveName"
        }

        val labelHwnd = GetDlgItem(window, IDC_STATUS_LABEL)
        labelHwnd?.let { SetWindowTextW(it, statusMessage) }

        // Update progress bar
        val percent = state.progress?.progressPercentage?.toInt() ?: 0
        progressBarHwnd?.let {
            SendMessageW(it, PBM_SETPOS.toUInt(), percent.toULong(), 0)
        }

        // Update file label with current file
        val fileText = when (state.progress?.stage) {
            ExtractionStage.EXTRACTING -> state.progress.currentFile ?: ""
            ExtractionStage.COMPLETED -> "All files extracted successfully"
            ExtractionStage.FAILED -> state.error ?: "Unknown error"
            else -> ""
        }
        fileLabelHwnd?.let { SetWindowTextW(it, fileText) }

        statusText = statusMessage
    }

    private suspend fun runMessageLoop(viewModel: ApplicationViewModel, scope: CoroutineScope): Nothing = memScoped {
        val msg = alloc<MSG>()
        var running = true
        var waitingForOk = false

        // Observe completion/failure events
        scope.launch {
            viewModel.extractionViewModel.uiState.collectLatest { state ->
                if (completionHandled) return@collectLatest

                when (state.progress?.stage) {
                    ExtractionStage.COMPLETED -> {
                        completionHandled = true
                        if (showNotification) {
                            // Show completion in window with OK button
                            showCompletionInWindow(state)
                            waitingForOk = true
                        } else {
                            // No notification, just exit
                            running = false
                            hwnd?.let { PostMessageW(it, WM_CLOSE.toUInt(), 0u, 0) }
                        }
                    }
                    ExtractionStage.FAILED -> {
                        completionHandled = true
                        // Always show errors
                        showErrorInWindow(state)
                        waitingForOk = true
                    }
                    else -> {}
                }
            }
        }

        // Message loop using PeekMessage for non-blocking operation
        while (running) {
            while (PeekMessageW(msg.ptr, null, 0u, 0u, PM_REMOVE.toUInt()) != 0) {
                if (msg.message == WM_QUIT.toUInt()) {
                    running = false
                    break
                }

                TranslateMessage(msg.ptr)
                DispatchMessageW(msg.ptr)
            }

            // Small delay to prevent CPU spinning
            delay(16) // ~60fps
        }

        // Cleanup
        cleanup()

        logger.i { "GUI message loop ended" }
        exitProcess(0)
    }

    private fun showCompletionInWindow(state: ExtractionUiState) {
        val window = hwnd ?: return
        val archiveName = state.archive?.name ?: "Archive"

        // Update status label
        val labelHwnd = GetDlgItem(window, IDC_STATUS_LABEL)
        labelHwnd?.let { SetWindowTextW(it, "Extraction Complete!") }

        // Set progress to 100%
        progressBarHwnd?.let {
            SendMessageW(it, PBM_SETPOS.toUInt(), 100u, 0)
        }

        // Update file label
        fileLabelHwnd?.let { SetWindowTextW(it, "$archiveName extracted successfully") }

        // Update window title
        SetWindowTextW(window, "Gunzip - Complete")

        // Show OK button
        okButtonHwnd?.let {
            ShowWindow(it, SW_SHOW)
            SetFocus(it)
        }
    }

    private fun showErrorInWindow(state: ExtractionUiState) {
        val window = hwnd ?: return
        val errorMsg = state.error ?: "Unknown error occurred"

        // Update status label
        val labelHwnd = GetDlgItem(window, IDC_STATUS_LABEL)
        labelHwnd?.let { SetWindowTextW(it, "Extraction Failed") }

        // Hide progress bar on error
        progressBarHwnd?.let { ShowWindow(it, SW_HIDE) }

        // Show error message in file label
        fileLabelHwnd?.let { SetWindowTextW(it, errorMsg) }

        // Update window title
        SetWindowTextW(window, "Gunzip - Error")

        // Show OK button
        okButtonHwnd?.let {
            ShowWindow(it, SW_SHOW)
            SetFocus(it)
        }
    }

    private fun cleanup() {
        hwnd?.let { DestroyWindow(it) }
        hwnd = null
        hFont?.let { DeleteObject(it) }
        hFont = null
    }
}

// Button control ID for window procedure
private const val WM_COMMAND_OK_BUTTON_ID = 1002

/**
 * Window procedure for handling messages
 */
@OptIn(ExperimentalForeignApi::class)
private fun windowProc(hwnd: HWND?, msg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
    return when (msg.toInt()) {
        WM_COMMAND -> {
            val controlId = (wParam.toLong() and 0xFFFF).toInt()
            val notifyCode = ((wParam.toLong() shr 16) and 0xFFFF).toInt()
            // BN_CLICKED = 0
            if (controlId == WM_COMMAND_OK_BUTTON_ID && notifyCode == 0) {
                // Post quit message to exit the message loop
                PostQuitMessage(0)
            }
            0
        }
        WM_DESTROY -> {
            PostQuitMessage(0)
            0
        }
        WM_CLOSE -> {
            DestroyWindow(hwnd)
            0
        }
        else -> DefWindowProcW(hwnd, msg, wParam, lParam)
    }
}

// ============================================================================
// Win32 Settings GUI
// ============================================================================

// Settings window control IDs
private const val IDC_SETTINGS_MOVE_TO_TRASH = 2001
private const val IDC_SETTINGS_SHOW_NOTIFICATION = 2002
private const val IDC_SETTINGS_AUTO_CLOSE = 2003
private const val IDC_SETTINGS_CLOSE_BUTTON = 2004
private const val IDC_SETTINGS_RESET_BUTTON = 2005

// Global reference for settings window procedure callbacks
private var settingsGuiInstance: Win32SettingsGui? = null

/**
 * Win32 native GUI for settings/preferences
 */
@OptIn(ExperimentalForeignApi::class)
class Win32SettingsGui(
    private val viewModel: ApplicationViewModel,
    private val scope: CoroutineScope
) {
    private val logger = Logger.withTag("Win32SettingsGui")

    private var hwnd: HWND? = null
    private var hFont: HFONT? = null

    companion object {
        private const val WINDOW_CLASS_NAME = "GunzipSettingsWindow"
        private const val WINDOW_WIDTH = 400
        private const val WINDOW_HEIGHT = 320
        private const val PADDING = 20
        private const val CONTROL_SPACING = 12
        private const val CHECKBOX_HEIGHT = 24
    }

    fun show(): Boolean = memScoped {
        settingsGuiInstance = this@Win32SettingsGui
        val hInstance = GetModuleHandleW(null)

        // Initialize Common Controls
        val icc = alloc<INITCOMMONCONTROLSEX>()
        icc.dwSize = sizeOf<INITCOMMONCONTROLSEX>().toUInt()
        icc.dwICC = 0x0000FFFFu  // ICC_WIN95_CLASSES
        InitCommonControlsEx(icc.ptr)

        // Create modern font
        hFont = CreateFontW(
            -14, 0, 0, 0,
            FW_NORMAL,
            0u, 0u, 0u,
            DEFAULT_CHARSET.toUInt(),
            OUT_DEFAULT_PRECIS.toUInt(),
            CLIP_DEFAULT_PRECIS.toUInt(),
            CLEARTYPE_QUALITY.toUInt(),
            (DEFAULT_PITCH or FF_DONTCARE).toUInt(),
            "Segoe UI"
        )

        // Register window class
        val wc = alloc<WNDCLASSEXW>()
        wc.cbSize = sizeOf<WNDCLASSEXW>().toUInt()
        wc.style = (CS_HREDRAW or CS_VREDRAW).toUInt()
        wc.lpfnWndProc = staticCFunction(::settingsWindowProc)
        wc.cbClsExtra = 0
        wc.cbWndExtra = 0
        wc.hInstance = hInstance
        wc.hIcon = LoadIconW(null, IDI_APPLICATION)
        wc.hCursor = LoadCursorW(null, IDC_ARROW)
        wc.hbrBackground = GetSysColorBrush(COLOR_BTNFACE)
        wc.lpszMenuName = null
        wc.lpszClassName = WINDOW_CLASS_NAME.wcstr.ptr
        wc.hIconSm = LoadIconW(null, IDI_APPLICATION)

        if (RegisterClassExW(wc.ptr) == 0.toUShort()) {
            val error = GetLastError()
            if (error != ERROR_CLASS_ALREADY_EXISTS.toUInt()) {
                logger.e { "Failed to register settings window class: $error" }
                return false
            }
        }

        // Calculate center position
        val screenWidth = GetSystemMetrics(SM_CXSCREEN)
        val screenHeight = GetSystemMetrics(SM_CYSCREEN)
        val x = (screenWidth - WINDOW_WIDTH) / 2
        val y = (screenHeight - WINDOW_HEIGHT) / 2

        // Create window
        hwnd = CreateWindowExW(
            dwExStyle = WS_EX_DLGMODALFRAME.toUInt(),
            lpClassName = WINDOW_CLASS_NAME,
            lpWindowName = "Gunzip Settings",
            dwStyle = (WS_OVERLAPPED or WS_CAPTION or WS_SYSMENU).toUInt(),
            X = x,
            Y = y,
            nWidth = WINDOW_WIDTH,
            nHeight = WINDOW_HEIGHT,
            hWndParent = null,
            hMenu = null,
            hInstance = hInstance,
            lpParam = null
        )

        if (hwnd == null) {
            logger.e { "Failed to create settings window: ${GetLastError()}" }
            return false
        }

        createControls(hInstance)

        ShowWindow(hwnd, SW_SHOW)
        UpdateWindow(hwnd)

        logger.i { "Settings window created successfully" }
        return true
    }

    private fun createControls(hInstance: HINSTANCE?) = memScoped {
        val window = hwnd ?: return@memScoped
        val contentWidth = WINDOW_WIDTH - (PADDING * 2) - 16
        var yPos = PADDING

        // Title label
        val titleLabel = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "Extraction Preferences",
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 20,
            hWndParent = window,
            hMenu = null,
            hInstance = hInstance,
            lpParam = null
        )
        applyBoldFont(titleLabel)
        yPos += 28

        val prefs = viewModel.settingsViewModel.uiState.value.preferences

        // Checkbox: Move to trash
        val chkMoveToTrash = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Move archive to trash after extraction",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_AUTOCHECKBOX.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = CHECKBOX_HEIGHT,
            hWndParent = window,
            hMenu = IDC_SETTINGS_MOVE_TO_TRASH.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(chkMoveToTrash)
        if (prefs.moveToTrashAfterExtraction) {
            SendMessageW(chkMoveToTrash, BM_SETCHECK.toUInt(), BST_CHECKED.toULong(), 0)
        }
        yPos += CHECKBOX_HEIGHT + CONTROL_SPACING

        // Checkbox: Show notification
        val chkShowNotification = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Show completion notification",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_AUTOCHECKBOX.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = CHECKBOX_HEIGHT,
            hWndParent = window,
            hMenu = IDC_SETTINGS_SHOW_NOTIFICATION.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(chkShowNotification)
        if (prefs.showCompletionNotification) {
            SendMessageW(chkShowNotification, BM_SETCHECK.toUInt(), BST_CHECKED.toULong(), 0)
        }
        yPos += CHECKBOX_HEIGHT + CONTROL_SPACING

        // Checkbox: Auto-close
        val chkAutoClose = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Auto-close after extraction",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_AUTOCHECKBOX.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = CHECKBOX_HEIGHT,
            hWndParent = window,
            hMenu = IDC_SETTINGS_AUTO_CLOSE.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(chkAutoClose)
        if (prefs.autoCloseAfterExtraction) {
            SendMessageW(chkAutoClose, BM_SETCHECK.toUInt(), BST_CHECKED.toULong(), 0)
        }
        yPos += CHECKBOX_HEIGHT + CONTROL_SPACING + 8

        // Separator line
        CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = null,
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_ETCHEDHORZ.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 2,
            hWndParent = window,
            hMenu = null,
            hInstance = hInstance,
            lpParam = null
        )
        yPos += 16

        // Settings file path
        val settingsPath = viewModel.settingsViewModel.uiState.value.preferencesPath
        if (settingsPath.isNotEmpty()) {
            val pathLabel = CreateWindowExW(
                dwExStyle = 0u,
                lpClassName = "STATIC",
                lpWindowName = "Settings file:",
                dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt()).toUInt(),
                X = PADDING,
                Y = yPos,
                nWidth = contentWidth,
                nHeight = 16,
                hWndParent = window,
                hMenu = null,
                hInstance = hInstance,
                lpParam = null
            )
            applySmallFont(pathLabel)
            yPos += 18

            val displayPath = if (settingsPath.length > 50) "...${settingsPath.takeLast(47)}" else settingsPath
            val pathValueLabel = CreateWindowExW(
                dwExStyle = 0u,
                lpClassName = "STATIC",
                lpWindowName = displayPath,
                dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt() or SS_PATHELLIPSIS.toInt()).toUInt(),
                X = PADDING,
                Y = yPos,
                nWidth = contentWidth,
                nHeight = 16,
                hWndParent = window,
                hMenu = null,
                hInstance = hInstance,
                lpParam = null
            )
            applySmallFont(pathValueLabel)
            yPos += 20
        }

        // Buttons at bottom - use yPos to position below content
        yPos += CONTROL_SPACING + 8
        val buttonWidth = 100
        val buttonHeight = 28

        // Reset button
        val resetButton = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Reset",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_PUSHBUTTON.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = buttonWidth,
            nHeight = buttonHeight,
            hWndParent = window,
            hMenu = IDC_SETTINGS_RESET_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(resetButton)

        // Close button
        val closeButton = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Close",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_DEFPUSHBUTTON.toInt()).toUInt(),
            X = WINDOW_WIDTH - buttonWidth - PADDING - 16,
            Y = yPos,
            nWidth = buttonWidth,
            nHeight = buttonHeight,
            hWndParent = window,
            hMenu = IDC_SETTINGS_CLOSE_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(closeButton)
    }

    private fun applyFont(hwnd: HWND?) {
        hFont?.let { font ->
            hwnd?.let { window ->
                SendMessageW(window, WM_SETFONT.toUInt(), font.toLong().toULong(), 1)
            }
        }
    }

    private fun applyBoldFont(hwnd: HWND?) {
        val boldFont = CreateFontW(
            -14, 0, 0, 0,
            FW_SEMIBOLD,
            0u, 0u, 0u,
            DEFAULT_CHARSET.toUInt(),
            OUT_DEFAULT_PRECIS.toUInt(),
            CLIP_DEFAULT_PRECIS.toUInt(),
            CLEARTYPE_QUALITY.toUInt(),
            (DEFAULT_PITCH or FF_DONTCARE).toUInt(),
            "Segoe UI"
        )
        hwnd?.let { window ->
            SendMessageW(window, WM_SETFONT.toUInt(), boldFont.toLong().toULong(), 1)
        }
    }

    private fun applySmallFont(hwnd: HWND?) {
        val smallFont = CreateFontW(
            -12, 0, 0, 0,
            FW_NORMAL,
            0u, 0u, 0u,
            DEFAULT_CHARSET.toUInt(),
            OUT_DEFAULT_PRECIS.toUInt(),
            CLIP_DEFAULT_PRECIS.toUInt(),
            CLEARTYPE_QUALITY.toUInt(),
            (DEFAULT_PITCH or FF_DONTCARE).toUInt(),
            "Segoe UI"
        )
        hwnd?.let { window ->
            SendMessageW(window, WM_SETFONT.toUInt(), smallFont.toLong().toULong(), 1)
        }
    }

    fun handleCommand(controlId: Int) {
        val window = hwnd ?: return

        when (controlId) {
            IDC_SETTINGS_MOVE_TO_TRASH -> {
                val checkState = SendMessageW(
                    GetDlgItem(window, IDC_SETTINGS_MOVE_TO_TRASH),
                    BM_GETCHECK.toUInt(), 0u, 0
                )
                viewModel.settingsViewModel.setMoveToTrashAfterExtraction(checkState == BST_CHECKED.toLong())
            }
            IDC_SETTINGS_SHOW_NOTIFICATION -> {
                val checkState = SendMessageW(
                    GetDlgItem(window, IDC_SETTINGS_SHOW_NOTIFICATION),
                    BM_GETCHECK.toUInt(), 0u, 0
                )
                viewModel.settingsViewModel.setShowCompletionNotification(checkState == BST_CHECKED.toLong())
            }
            IDC_SETTINGS_AUTO_CLOSE -> {
                val checkState = SendMessageW(
                    GetDlgItem(window, IDC_SETTINGS_AUTO_CLOSE),
                    BM_GETCHECK.toUInt(), 0u, 0
                )
                viewModel.settingsViewModel.setAutoCloseAfterExtraction(checkState == BST_CHECKED.toLong())
            }
            IDC_SETTINGS_RESET_BUTTON -> {
                viewModel.settingsViewModel.resetToDefaults()
                // Update checkboxes to reflect reset values
                updateCheckboxes()
            }
            IDC_SETTINGS_CLOSE_BUTTON -> {
                PostQuitMessage(0)
            }
        }
    }

    private fun updateCheckboxes() {
        val window = hwnd ?: return
        val prefs = viewModel.settingsViewModel.uiState.value.preferences

        SendMessageW(
            GetDlgItem(window, IDC_SETTINGS_MOVE_TO_TRASH),
            BM_SETCHECK.toUInt(),
            if (prefs.moveToTrashAfterExtraction) BST_CHECKED.toULong() else BST_UNCHECKED.toULong(),
            0
        )
        SendMessageW(
            GetDlgItem(window, IDC_SETTINGS_SHOW_NOTIFICATION),
            BM_SETCHECK.toUInt(),
            if (prefs.showCompletionNotification) BST_CHECKED.toULong() else BST_UNCHECKED.toULong(),
            0
        )
        SendMessageW(
            GetDlgItem(window, IDC_SETTINGS_AUTO_CLOSE),
            BM_SETCHECK.toUInt(),
            if (prefs.autoCloseAfterExtraction) BST_CHECKED.toULong() else BST_UNCHECKED.toULong(),
            0
        )
    }

    suspend fun runMessageLoop(): Nothing = memScoped {
        val msg = alloc<MSG>()
        var running = true

        while (running) {
            while (PeekMessageW(msg.ptr, null, 0u, 0u, PM_REMOVE.toUInt()) != 0) {
                if (msg.message == WM_QUIT.toUInt()) {
                    running = false
                    break
                }
                TranslateMessage(msg.ptr)
                DispatchMessageW(msg.ptr)
            }
            delay(16)
        }

        cleanup()
        logger.i { "Settings GUI message loop ended" }
        exitProcess(0)
    }

    private fun cleanup() {
        hwnd?.let { DestroyWindow(it) }
        hwnd = null
        hFont?.let { DeleteObject(it) }
        hFont = null
        settingsGuiInstance = null
    }
}

/**
 * Window procedure for settings window
 */
@OptIn(ExperimentalForeignApi::class)
private fun settingsWindowProc(hwnd: HWND?, msg: UINT, wParam: WPARAM, lParam: LPARAM): LRESULT {
    return when (msg.toInt()) {
        WM_COMMAND -> {
            val controlId = (wParam.toLong() and 0xFFFF).toInt()
            settingsGuiInstance?.handleCommand(controlId)
            0
        }
        WM_DESTROY -> {
            PostQuitMessage(0)
            0
        }
        WM_CLOSE -> {
            DestroyWindow(hwnd)
            0
        }
        else -> DefWindowProcW(hwnd, msg, wParam, lParam)
    }
}

/**
 * Create Windows native GUI renderer
 */
actual fun createNativeGuiRenderer(): UiRenderer? {
    return Win32GuiRenderer()
}
