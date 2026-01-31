package qunzip.presentation.ui

import qunzip.presentation.viewmodels.ApplicationViewModel
import qunzip.presentation.viewmodels.ApplicationMode
import qunzip.presentation.viewmodels.ExtractionUiState
import qunzip.domain.entities.ExtractionStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.cinterop.*
import platform.windows.*
import co.touchlab.kermit.Logger
import kotlin.system.exitProcess
import qunzip.getCurrentExecutablePath

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
    private var cancelButtonHwnd: HWND? = null
    private var progressBarHwnd: HWND? = null
    private var fileLabelHwnd: HWND? = null
    private var hFont: HFONT? = null
    private var statusText: String = "Initializing..."
    private var completionHandled = false
    private var showCompletionDialog = false
    private var extractionCancelled = false
    private var currentViewModel: ApplicationViewModel? = null

    companion object {
        private const val WINDOW_CLASS_NAME = "QunzipProgressWindow"
        private const val WINDOW_WIDTH = 420
        private const val WINDOW_HEIGHT = 180
        private const val IDC_STATUS_LABEL = 1001
        private const val IDC_OK_BUTTON = 1002
        private const val IDC_PROGRESS_BAR = 1003
        private const val IDC_FILE_LABEL = 1004
        private const val IDC_CANCEL_BUTTON = 1005

        // Layout constants - following Windows UX guidelines
        private const val PADDING = 24
        private const val ELEMENT_SPACING = 12
        private const val PROGRESS_BAR_HEIGHT = 8
        private const val BUTTON_WIDTH = 88
        private const val BUTTON_HEIGHT = 26
    }

    override suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope) {
        // Wait for handleApplicationStart to complete (runs async)
        // Wait until either:
        // - mode changes to EXTRACTION (file argument provided), OR
        // - targetFile is set, OR
        // - error is set, OR
        // - timeout expires (settings mode with no file)
        var uiState = viewModel.uiState.value
        var waitCount = 0
        while (uiState.mode == ApplicationMode.SETUP &&
               uiState.targetFile == null &&
               uiState.error == null &&
               waitCount < 40) {  // Up to 2 seconds
            delay(50)
            uiState = viewModel.uiState.value
            waitCount++
        }

        logger.d { "Win32GuiRenderer: mode=${uiState.mode}, targetFile=${uiState.targetFile}, error=${uiState.error}" }

        // Handle startup error - show error message box
        if (uiState.error != null) {
            logger.e { "Startup error: ${uiState.error}" }
            memScoped {
                MessageBoxW(
                    null,
                    "Error: ${uiState.error}",
                    "Qunzip - Error",
                    (MB_OK or MB_ICONERROR).toUInt()
                )
            }
            exitProcess(1)
        }

        // For settings mode (no file argument), use native settings GUI
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

        // Store viewModel reference for cancel handling
        currentViewModel = viewModel
        activeGuiRenderer = this

        // Get completion dialog preference
        showCompletionDialog = viewModel.settingsViewModel.uiState.value.preferences.showCompletionDialog

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

        // Create modern font (Segoe UI, 9pt - matches Windows system dialogs)
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

        // Load the embedded icon from the executable's resources (ID 1)
        // Falls back to default application icon if not found
        val iconResource = 1.toLong().toCPointer<UShortVar>()
        val windowIcon = LoadIconW(hInstance, iconResource?.reinterpret())
            ?: LoadIconW(null, IDI_APPLICATION)

        // Register window class
        val wc = alloc<WNDCLASSEXW>()
        wc.cbSize = sizeOf<WNDCLASSEXW>().toUInt()
        wc.style = (CS_HREDRAW or CS_VREDRAW).toUInt()
        wc.lpfnWndProc = staticCFunction(::windowProc)
        wc.cbClsExtra = 0
        wc.cbWndExtra = 0
        wc.hInstance = hInstance
        wc.hIcon = windowIcon
        wc.hCursor = LoadCursorW(null, IDC_ARROW)
        // Use a light gray background for modern look
        wc.hbrBackground = GetSysColorBrush(COLOR_BTNFACE)
        wc.lpszMenuName = null
        wc.lpszClassName = WINDOW_CLASS_NAME.wcstr.ptr
        wc.hIconSm = windowIcon

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
        val windowTitle = "Qunzip - $archiveName"
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

        // Layout calculations - account for window borders (~16px total)
        val borderWidth = 16
        val contentWidth = WINDOW_WIDTH - (PADDING * 2) - borderWidth
        var yPos = PADDING - 4  // Start slightly higher for balanced look

        // Create status label (shows stage) - semibold for emphasis
        // Height 20px to avoid clipping descenders (g, y, p, etc.)
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
        applySemiboldFont(statusLabel)
        yPos += 20 + ELEMENT_SPACING

        // Create progress bar - thin modern style
        progressBarHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "msctls_progress32",
            lpWindowName = null,
            dwStyle = (WS_CHILD or WS_VISIBLE or PBS_SMOOTH).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = PROGRESS_BAR_HEIGHT,
            hWndParent = hwnd,
            hMenu = IDC_PROGRESS_BAR.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        // Set progress bar range 0-100
        progressBarHwnd?.let {
            SendMessageW(it, PBM_SETRANGE32.toUInt(), 0u, 100)
        }
        yPos += PROGRESS_BAR_HEIGHT + ELEMENT_SPACING - 2

        // Create file label (shows current file being extracted)
        // Height 20px to avoid clipping descenders (g, y, p, etc.)
        fileLabelHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "",
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt() or SS_PATHELLIPSIS.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 20,
            hWndParent = hwnd,
            hMenu = IDC_FILE_LABEL.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(fileLabelHwnd)
        yPos += 20 + ELEMENT_SPACING + 4  // Extra space before button

        // Calculate button X position for centering
        val buttonX = (WINDOW_WIDTH - BUTTON_WIDTH - borderWidth) / 2

        // Cancel button (visible during extraction)
        cancelButtonHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Cancel",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_PUSHBUTTON.toInt()).toUInt(),
            X = buttonX,
            Y = yPos,
            nWidth = BUTTON_WIDTH,
            nHeight = BUTTON_HEIGHT,
            hWndParent = hwnd,
            hMenu = IDC_CANCEL_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(cancelButtonHwnd)

        // OK button (initially hidden, shown after completion)
        okButtonHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "OK",
            dwStyle = (WS_CHILD or BS_DEFPUSHBUTTON.toInt()).toUInt(),
            X = buttonX,
            Y = yPos,
            nWidth = BUTTON_WIDTH,
            nHeight = BUTTON_HEIGHT,
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

    private fun applySemiboldFont(hwnd: HWND?) {
        val semiboldFont = CreateFontW(
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
            SendMessageW(window, WM_SETFONT.toUInt(), semiboldFont.toLong().toULong(), 1)
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
                        if (showCompletionDialog) {
                            // Show completion dialog with OK button
                            showCompletionInWindow(state)
                            waitingForOk = true
                        } else {
                            // No dialog, just exit
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

        // Update status label
        val labelHwnd = GetDlgItem(window, IDC_STATUS_LABEL)
        labelHwnd?.let { SetWindowTextW(it, "Extraction complete!") }

        // Set progress to 100%
        progressBarHwnd?.let {
            SendMessageW(it, PBM_SETPOS.toUInt(), 100u, 0)
        }

        // Update file label with success message
        fileLabelHwnd?.let { SetWindowTextW(it, "All files extracted successfully") }

        // Update window title
        SetWindowTextW(window, "Qunzip - Complete")

        // Hide cancel, show OK button
        cancelButtonHwnd?.let { ShowWindow(it, SW_HIDE) }
        okButtonHwnd?.let {
            ShowWindow(it, SW_SHOW)
            SetFocus(it)
        }
    }

    private fun showErrorInWindow(state: ExtractionUiState) {
        val window = hwnd ?: return
        val errorMsg = if (extractionCancelled) "Extraction cancelled" else (state.error ?: "Unknown error occurred")

        // Update status label
        val labelHwnd = GetDlgItem(window, IDC_STATUS_LABEL)
        labelHwnd?.let { SetWindowTextW(it, if (extractionCancelled) "Cancelled" else "Extraction Failed") }

        // Hide progress bar on error
        progressBarHwnd?.let { ShowWindow(it, SW_HIDE) }

        // Show error message in file label
        fileLabelHwnd?.let { SetWindowTextW(it, errorMsg) }

        // Update window title
        SetWindowTextW(window, if (extractionCancelled) "Qunzip - Cancelled" else "Qunzip - Error")

        // Hide cancel, show OK button
        cancelButtonHwnd?.let { ShowWindow(it, SW_HIDE) }
        okButtonHwnd?.let {
            ShowWindow(it, SW_SHOW)
            SetFocus(it)
        }
    }

    fun handleCancelClicked() {
        logger.i { "Cancel button clicked" }
        extractionCancelled = true
        currentViewModel?.extractionViewModel?.cancelExtraction()

        // Close the dialog immediately
        PostQuitMessage(0)
    }

    private fun cleanup() {
        hwnd?.let { DestroyWindow(it) }
        hwnd = null
        hFont?.let { DeleteObject(it) }
        hFont = null
        activeGuiRenderer = null
        currentViewModel = null
    }
}

// Button control IDs for window procedure
private const val WM_COMMAND_OK_BUTTON_ID = 1002
private const val WM_COMMAND_CANCEL_BUTTON_ID = 1005

// Global reference for cancel handling
private var activeGuiRenderer: Win32GuiRenderer? = null

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
            if (notifyCode == 0) {
                when (controlId) {
                    WM_COMMAND_OK_BUTTON_ID -> {
                        PostQuitMessage(0)
                    }
                    WM_COMMAND_CANCEL_BUTTON_ID -> {
                        activeGuiRenderer?.handleCancelClicked()
                    }
                }
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
private const val IDC_SETTINGS_SHOW_COMPLETION_DIALOG = 2002
private const val IDC_SETTINGS_CLOSE_BUTTON = 2004
private const val IDC_SETTINGS_RESET_BUTTON = 2005
private const val IDC_SETTINGS_FILE_ASSOC_STATUS = 2006
private const val IDC_SETTINGS_REGISTER_BUTTON = 2007
private const val IDC_SETTINGS_UNREGISTER_BUTTON = 2008

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
    private var fileAssocStatusHwnd: HWND? = null
    private var registerButtonHwnd: HWND? = null
    private var unregisterButtonHwnd: HWND? = null

    companion object {
        private const val WINDOW_CLASS_NAME = "QunzipSettingsWindow"
        private const val WINDOW_WIDTH = 400
        private const val WINDOW_HEIGHT = 380
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
        // Load the embedded icon from the executable's resources (ID 1)
        val iconResource = 1.toLong().toCPointer<UShortVar>()
        val windowIcon = LoadIconW(hInstance, iconResource?.reinterpret())
            ?: LoadIconW(null, IDI_APPLICATION)
        wc.hIcon = windowIcon
        wc.hCursor = LoadCursorW(null, IDC_ARROW)
        wc.hbrBackground = GetSysColorBrush(COLOR_BTNFACE)
        wc.lpszMenuName = null
        wc.lpszClassName = WINDOW_CLASS_NAME.wcstr.ptr
        wc.hIconSm = windowIcon

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
            lpWindowName = "Qunzip Settings",
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

        // Start observing file association state
        startStateObservation()

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

        // Checkbox: Show completion dialog
        val chkShowCompletionDialog = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Show completion dialog",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_AUTOCHECKBOX.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = CHECKBOX_HEIGHT,
            hWndParent = window,
            hMenu = IDC_SETTINGS_SHOW_COMPLETION_DIALOG.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(chkShowCompletionDialog)
        if (prefs.showCompletionDialog) {
            SendMessageW(chkShowCompletionDialog, BM_SETCHECK.toUInt(), BST_CHECKED.toULong(), 0)
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

        // File Associations section title
        val fileAssocTitle = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "File Associations",
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
        applyBoldFont(fileAssocTitle)
        yPos += 28

        // File association status label
        fileAssocStatusHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "STATIC",
            lpWindowName = "Checking status...",
            dwStyle = (WS_CHILD or WS_VISIBLE or SS_LEFT.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = contentWidth,
            nHeight = 20,
            hWndParent = window,
            hMenu = IDC_SETTINGS_FILE_ASSOC_STATUS.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(fileAssocStatusHwnd)
        yPos += 24 + CONTROL_SPACING

        // Register/Unregister buttons
        val assocButtonWidth = 120
        val assocButtonHeight = 28
        val buttonSpacing = 12

        registerButtonHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Register All",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_PUSHBUTTON.toInt()).toUInt(),
            X = PADDING,
            Y = yPos,
            nWidth = assocButtonWidth,
            nHeight = assocButtonHeight,
            hWndParent = window,
            hMenu = IDC_SETTINGS_REGISTER_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(registerButtonHwnd)

        unregisterButtonHwnd = CreateWindowExW(
            dwExStyle = 0u,
            lpClassName = "BUTTON",
            lpWindowName = "Unregister All",
            dwStyle = (WS_CHILD or WS_VISIBLE or BS_PUSHBUTTON.toInt()).toUInt(),
            X = PADDING + assocButtonWidth + buttonSpacing,
            Y = yPos,
            nWidth = assocButtonWidth,
            nHeight = assocButtonHeight,
            hWndParent = window,
            hMenu = IDC_SETTINGS_UNREGISTER_BUTTON.toLong().toCPointer(),
            hInstance = hInstance,
            lpParam = null
        )
        applyFont(unregisterButtonHwnd)
        yPos += assocButtonHeight + CONTROL_SPACING + 8

        // Second separator line
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

        // Buttons at bottom
        yPos += CONTROL_SPACING
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
            IDC_SETTINGS_SHOW_COMPLETION_DIALOG -> {
                val checkState = SendMessageW(
                    GetDlgItem(window, IDC_SETTINGS_SHOW_COMPLETION_DIALOG),
                    BM_GETCHECK.toUInt(), 0u, 0
                )
                viewModel.settingsViewModel.setShowCompletionDialog(checkState == BST_CHECKED.toLong())
            }
            IDC_SETTINGS_RESET_BUTTON -> {
                viewModel.settingsViewModel.resetToDefaults()
                // Update checkboxes to reflect reset values
                updateCheckboxes()
            }
            IDC_SETTINGS_CLOSE_BUTTON -> {
                PostQuitMessage(0)
            }
            IDC_SETTINGS_REGISTER_BUTTON -> {
                val appPath = getCurrentExecutablePath()
                logger.i { "Registering file associations with path: $appPath" }
                viewModel.fileAssociationViewModel.registerAssociations(appPath)
            }
            IDC_SETTINGS_UNREGISTER_BUTTON -> {
                logger.i { "Unregistering file associations" }
                viewModel.fileAssociationViewModel.unregisterAssociations()
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
            GetDlgItem(window, IDC_SETTINGS_SHOW_COMPLETION_DIALOG),
            BM_SETCHECK.toUInt(),
            if (prefs.showCompletionDialog) BST_CHECKED.toULong() else BST_UNCHECKED.toULong(),
            0
        )
    }

    private fun updateFileAssociationStatus() {
        val state = viewModel.fileAssociationViewModel.uiState.value

        // Update status label
        val statusText = when {
            state.isLoading -> "Checking..."
            state.error != null -> "Error: ${state.error}"
            state.qunzipAssociations.isEmpty() -> "Not registered"
            else -> {
                val total = state.supportedExtensions.size
                val registered = state.registeredExtensions.size
                if (registered == total) {
                    "Registered: All $total formats"
                } else {
                    "Registered: $registered of $total formats"
                }
            }
        }
        fileAssocStatusHwnd?.let { SetWindowTextW(it, statusText) }

        // Enable/disable buttons based on loading state
        val enableState = if (state.isLoading) 0 else 1
        registerButtonHwnd?.let { EnableWindow(it, enableState) }
        unregisterButtonHwnd?.let { EnableWindow(it, enableState) }
    }

    private fun startStateObservation() {
        // Observe file association state changes
        scope.launch {
            viewModel.fileAssociationViewModel.uiState.collectLatest { _ ->
                updateFileAssociationStatus()
            }
        }

        // Initial refresh of associations
        viewModel.fileAssociationViewModel.refreshAssociations()
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
