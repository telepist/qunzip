#ifndef IMGUI_APP_H
#define IMGUI_APP_H

#ifdef __cplusplus
extern "C" {
#endif

typedef struct ImGuiApp ImGuiApp;

/* Create a window with DX11 rendering and ImGui initialized.
 * Returns NULL on failure. */
ImGuiApp* imgui_app_create(const char* title, int width, int height);

/* Destroy the app and free all resources. */
void imgui_app_destroy(ImGuiApp* app);

/* Process Win32 messages and begin a new ImGui frame.
 * Returns 0 if the window was closed (app should exit). */
int imgui_app_begin_frame(ImGuiApp* app);

/* End the ImGui frame: render and present. */
void imgui_app_end_frame(ImGuiApp* app);

#ifdef __cplusplus
}
#endif

#endif /* IMGUI_APP_H */
