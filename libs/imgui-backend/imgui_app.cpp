#include "imgui_app.h"
#include "../cimgui/imgui/imgui.h"
#include "../cimgui/imgui/backends/imgui_impl_win32.h"
#include "../cimgui/imgui/backends/imgui_impl_dx11.h"

#include <d3d11.h>
#include <windows.h>
#include <dwmapi.h>
#include <shellscalingapi.h>

#ifndef DWMWA_USE_IMMERSIVE_DARK_MODE
#define DWMWA_USE_IMMERSIVE_DARK_MODE 20
#endif

// Forward declare message handler from imgui_impl_win32.cpp
extern IMGUI_IMPL_API LRESULT ImGui_ImplWin32_WndProcHandler(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam);

struct ImGuiApp {
    HWND hwnd;
    WNDCLASSEXW wc;
    ID3D11Device* device;
    ID3D11DeviceContext* device_context;
    IDXGISwapChain* swap_chain;
    ID3D11RenderTargetView* render_target_view;
    UINT resize_width;
    UINT resize_height;
};

static ImGuiApp* g_app = nullptr;

static bool CreateRenderTarget(ImGuiApp* app) {
    ID3D11Texture2D* back_buffer = nullptr;
    HRESULT hr = app->swap_chain->GetBuffer(0, IID_ID3D11Texture2D, (void**)&back_buffer);
    if (FAILED(hr) || !back_buffer)
        return false;
    hr = app->device->CreateRenderTargetView(back_buffer, nullptr, &app->render_target_view);
    back_buffer->Release();
    return SUCCEEDED(hr) && app->render_target_view != nullptr;
}

static void CleanupRenderTarget(ImGuiApp* app) {
    if (app->render_target_view) {
        app->render_target_view->Release();
        app->render_target_view = nullptr;
    }
}

static void CleanupDevice(ImGuiApp* app) {
    CleanupRenderTarget(app);
    if (app->swap_chain)      { app->swap_chain->Release();      app->swap_chain = nullptr; }
    if (app->device_context)  { app->device_context->Release();  app->device_context = nullptr; }
    if (app->device)          { app->device->Release();          app->device = nullptr; }
}

static void CleanupWindow(ImGuiApp* app) {
    if (app->hwnd) {
        DestroyWindow(app->hwnd);
        app->hwnd = nullptr;
    }
    UnregisterClassW(app->wc.lpszClassName, app->wc.hInstance);
}

static LRESULT WINAPI WndProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    if (ImGui_ImplWin32_WndProcHandler(hWnd, msg, wParam, lParam))
        return true;

    switch (msg) {
    case WM_SIZE:
        if (g_app && g_app->device && wParam != SIZE_MINIMIZED) {
            g_app->resize_width = LOWORD(lParam);
            g_app->resize_height = HIWORD(lParam);
        }
        return 0;
    case WM_DESTROY:
        PostQuitMessage(0);
        return 0;
    }
    return DefWindowProcW(hWnd, msg, wParam, lParam);
}

extern "C" ImGuiApp* imgui_app_create(const char* title, int width, int height) {
    // DPI awareness — avoid blurry rendering on scaled displays.
    // Loaded dynamically since shcore.dll isn't available in MinGW link libraries.
    {
        typedef BOOL (WINAPI *SetProcessDpiAwarenessContextFn)(HANDLE);
        HMODULE user32 = GetModuleHandleW(L"user32.dll");
        if (user32) {
            auto fn = (SetProcessDpiAwarenessContextFn)GetProcAddress(user32, "SetProcessDpiAwarenessContext");
            if (fn) fn(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
        }
    }

    ImGuiApp* app = new ImGuiApp();
    memset(app, 0, sizeof(ImGuiApp));
    g_app = app;

    // Load embedded icon (resource ID 1, see qunzip.rc) at the right sizes
    // for the title bar (small) and Alt+Tab / taskbar (big). LoadImageW
    // returns NULL if the resource isn't compiled in (icon-less builds);
    // assigning NULL is safe — Windows just falls back to the default.
    HMODULE hInst = GetModuleHandleW(nullptr);
    HICON hIconBig = (HICON)LoadImageW(
        hInst, MAKEINTRESOURCEW(1), IMAGE_ICON,
        GetSystemMetrics(SM_CXICON), GetSystemMetrics(SM_CYICON),
        LR_DEFAULTCOLOR);
    HICON hIconSmall = (HICON)LoadImageW(
        hInst, MAKEINTRESOURCEW(1), IMAGE_ICON,
        GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON),
        LR_DEFAULTCOLOR);

    // Register window class
    app->wc = { sizeof(WNDCLASSEXW), CS_CLASSDC, WndProc, 0L, 0L,
                 hInst, hIconBig, nullptr, nullptr, nullptr,
                 L"ImGuiAppClass", hIconSmall };
    RegisterClassExW(&app->wc);

    // Convert title to wide string
    int len = MultiByteToWideChar(CP_UTF8, 0, title, -1, nullptr, 0);
    wchar_t* wtitle = new wchar_t[len];
    MultiByteToWideChar(CP_UTF8, 0, title, -1, wtitle, len);

    // Create window — non-resizable: drop the thick-frame resize handles
    // and the maximize button, keep title, close, minimize. ImGui content
    // is laid out for fixed dimensions.
    const DWORD style = WS_OVERLAPPEDWINDOW & ~(WS_THICKFRAME | WS_MAXIMIZEBOX);

    // The width/height parameters are the desired CLIENT area in DPI-scaled
    // (logical) pixels. With per-monitor V2 awareness Windows treats the
    // values passed to CreateWindowEx as physical pixels, so we have to
    // scale them ourselves and adjust for the title bar.
    UINT dpi = 96;
    {
        typedef UINT (WINAPI *GetDpiForSystemFn)();
        HMODULE user32 = GetModuleHandleW(L"user32.dll");
        if (user32) {
            auto fn = (GetDpiForSystemFn)GetProcAddress(user32, "GetDpiForSystem");
            if (fn) dpi = fn();
        }
    }
    RECT rc = { 0, 0, MulDiv(width, dpi, 96), MulDiv(height, dpi, 96) };
    {
        typedef BOOL (WINAPI *AdjustWindowRectExForDpiFn)(LPRECT, DWORD, BOOL, DWORD, UINT);
        HMODULE user32 = GetModuleHandleW(L"user32.dll");
        AdjustWindowRectExForDpiFn fn = nullptr;
        if (user32) fn = (AdjustWindowRectExForDpiFn)GetProcAddress(user32, "AdjustWindowRectExForDpi");
        if (fn) fn(&rc, style, FALSE, 0, dpi);
        else AdjustWindowRectEx(&rc, style, FALSE, 0);
    }
    const int outerWidth = rc.right - rc.left;
    const int outerHeight = rc.bottom - rc.top;

    app->hwnd = CreateWindowExW(0, app->wc.lpszClassName, wtitle,
        style, CW_USEDEFAULT, CW_USEDEFAULT, outerWidth, outerHeight,
        nullptr, nullptr, app->wc.hInstance, nullptr);
    delete[] wtitle;

    if (!app->hwnd) {
        CleanupWindow(app);
        g_app = nullptr;
        delete app;
        return nullptr;
    }

    // Match title bar to system dark/light theme setting
    {
        HKEY hkey;
        BOOL use_dark = TRUE;  // default to dark (matches ImGui::StyleColorsDark)
        if (RegOpenKeyExW(HKEY_CURRENT_USER,
                L"Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                0, KEY_READ, &hkey) == ERROR_SUCCESS) {
            DWORD val = 0, size = sizeof(val);
            if (RegQueryValueExW(hkey, L"AppsUseLightTheme", nullptr, nullptr,
                    (LPBYTE)&val, &size) == ERROR_SUCCESS) {
                use_dark = (val == 0) ? TRUE : FALSE;
            }
            RegCloseKey(hkey);
        }
        DwmSetWindowAttribute(app->hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE,
                              &use_dark, sizeof(use_dark));
    }

    // Create DX11 device and swap chain
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 2;
    sd.BufferDesc.Width = 0;
    sd.BufferDesc.Height = 0;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator = 60;
    sd.BufferDesc.RefreshRate.Denominator = 1;
    sd.Flags = DXGI_SWAP_CHAIN_FLAG_ALLOW_MODE_SWITCH;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = app->hwnd;
    sd.SampleDesc.Count = 1;
    sd.SampleDesc.Quality = 0;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;

    D3D_FEATURE_LEVEL feature_level;
    const D3D_FEATURE_LEVEL feature_levels[] = { D3D_FEATURE_LEVEL_11_0, D3D_FEATURE_LEVEL_10_0 };

    HRESULT hr = D3D11CreateDeviceAndSwapChain(
        nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
        feature_levels, 2, D3D11_SDK_VERSION,
        &sd, &app->swap_chain, &app->device, &feature_level, &app->device_context);
    if (FAILED(hr)) {
        CleanupWindow(app);
        g_app = nullptr;
        delete app;
        return nullptr;
    }

    if (!CreateRenderTarget(app)) {
        CleanupDevice(app);
        CleanupWindow(app);
        g_app = nullptr;
        delete app;
        return nullptr;
    }

    // Show window
    ShowWindow(app->hwnd, SW_SHOWDEFAULT);
    UpdateWindow(app->hwnd);

    // Setup ImGui context
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;  // Disable imgui.ini
    ImGui::StyleColorsDark();

    // Setup backends
    ImGui_ImplWin32_Init(app->hwnd);
    ImGui_ImplDX11_Init(app->device, app->device_context);

    return app;
}

extern "C" void imgui_app_destroy(ImGuiApp* app) {
    if (!app) return;

    ImGui_ImplDX11_Shutdown();
    ImGui_ImplWin32_Shutdown();
    ImGui::DestroyContext();

    CleanupDevice(app);
    CleanupWindow(app);

    g_app = nullptr;
    delete app;
}

extern "C" int imgui_app_begin_frame(ImGuiApp* app) {
    // Process Win32 messages
    MSG msg;
    while (PeekMessageW(&msg, nullptr, 0, 0, PM_REMOVE)) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
        if (msg.message == WM_QUIT)
            return 0;
    }

    // Handle resize
    if (app->resize_width != 0 && app->resize_height != 0) {
        CleanupRenderTarget(app);
        app->swap_chain->ResizeBuffers(0, app->resize_width, app->resize_height,
                                        DXGI_FORMAT_UNKNOWN, 0);
        app->resize_width = app->resize_height = 0;
        CreateRenderTarget(app);
    }

    // Start new frame
    ImGui_ImplDX11_NewFrame();
    ImGui_ImplWin32_NewFrame();
    ImGui::NewFrame();
    return 1;
}

extern "C" void imgui_app_end_frame(ImGuiApp* app) {
    ImGui::Render();

    if (app->render_target_view) {
        const float clear_color[] = { 0.1f, 0.1f, 0.1f, 1.0f };
        app->device_context->OMSetRenderTargets(1, &app->render_target_view, nullptr);
        app->device_context->ClearRenderTargetView(app->render_target_view, clear_color);
        ImGui_ImplDX11_RenderDrawData(ImGui::GetDrawData());
    }

    app->swap_chain->Present(1, 0);  // vsync
}
