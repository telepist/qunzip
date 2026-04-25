# Architecture Documentation

## Overview

This project implements a cross-platform unzip application using Clean Architecture principles with MVVM pattern, targeting primarily Windows but supporting Linux and macOS. The application follows macOS-inspired UX patterns for seamless archive extraction.

## Clean Architecture Layers

### 1. Domain Layer (Business Logic)
- **Entities**: Core business objects (Archive, ExtractionResult, etc.)
- **Use Cases**: Business rules and application logic
- **Repository Interfaces**: Abstract contracts for data access

### 2. Data Layer
- **Repository Implementations**: Platform-specific data access
- **Data Sources**: File system, 7zip integration, OS services
- **Models**: Data transfer objects

### 3. Presentation Layer
- **ViewModels**: State management with Kotlin Flow
- **UI Layer**: Dual renderer architecture via `UiRenderer` interface
  - **ImGui GUI** (`ImGuiRenderer`): Native Windows GUI using Dear ImGui with DX11 backend, used for standalone/drag-drop launches
  - **Mosaic TUI** (`MosaicTuiRenderer`): Cross-platform terminal UI with progress bars, colors, and real-time updates, used for CLI launches
- **Mappers**: Convert between domain and presentation models

## MVVM with Kotlin Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   View/UI       │◄──►│   ViewModel     │◄──►│   Repository    │
│                 │    │                 │    │                 │
│ - GUI (ImGui)   │    │ - State Flow    │    │ - File Ops      │
│ - TUI (Mosaic)  │    │ - Commands      │    │ - 7zip Calls    │
│ - Notifications │    │ - Error Handle  │    │ - OS Integration│
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## UI Architecture

### Dual Renderer Design

The application has two UI renderers that implement the common `UiRenderer` interface, selected based on the launch context:

- **`ImGuiRenderer`** (Windows, standalone/drag-drop): Native GUI window using Dear ImGui with a DirectX 11 backend. Used when the application is launched by double-clicking an archive or via file association. The Windows executable is built with the GUI subsystem (`-mwindows`), so no console window appears at startup.
- **`MosaicTuiRenderer`** (cross-platform, CLI): Terminal UI using Mosaic. Used when the application is invoked from a terminal/shell. On Windows, CLI mode reattaches to the parent console via `AttachConsole(ATTACH_PARENT_PROCESS)` since the GUI subsystem detaches from it by default.

```kotlin
interface UiRenderer {
    suspend fun render(viewModel: ApplicationViewModel)
}

class ImGuiRenderer : UiRenderer       // Windows GUI (Dear ImGui + DX11)
class MosaicTuiRenderer : UiRenderer   // Cross-platform TUI (Mosaic)
```

### ImGui Native Stack (Windows)

The ImGui GUI renderer is backed by a native C/C++ stack integrated via Kotlin/Native cinterop:

- **cimgui** (`libs/cimgui/` submodule): C bindings to Dear ImGui, providing a C-compatible API surface for Kotlin/Native interop.
- **Win32/DX11 wrapper** (`libs/imgui-backend/imgui_app.cpp`): Custom application wrapper that manages the Win32 window, DirectX 11 device, and ImGui render loop.
- **cinterop definition** (`src/nativeInterop/cinterop/cimgui.def`): Kotlin/Native `.def` file that generates Kotlin bindings from the C headers.

### Launch Context Detection

The application detects how it was launched to select the appropriate renderer:

- **Windows**: The executable uses the GUI subsystem (`-mwindows`), so it has no console by default. CLI mode is detected by attempting `AttachConsole(ATTACH_PARENT_PROCESS)` -- success means a parent shell exists (CLI launch), and the Mosaic TUI renderer is used. Failure means standalone launch, and the ImGui GUI renderer is used.
- **macOS/Linux**: Uses `isatty()` -- if stdout is a TTY, it's a CLI launch (Mosaic TUI). Otherwise, it's a standalone launch.

### UI Flow

```
Application Start (GUI subsystem, no console)
       ↓
┌──────────────┐
│ Parse Args   │
└──────┬───────┘
       ↓
┌──────────────────────┐
│ Detect Launch Mode   │
│ (AttachConsole on    │
│  Windows, isatty on  │
│  macOS/Linux)        │
└──────┬───────────────┘
       ↓
┌──────────────────────────────────────────┐
│              UiRenderer                  │
├──────────────────┬───────────────────────┤
│ Standalone/GUI   │ CLI/Terminal          │
│ → ImGuiRenderer  │ → MosaicTuiRenderer  │
│   (DX11 window)  │   (interactive TUI)  │
└──────┬───────────┴───────────┬───────────┘
       ↓                       ↓
┌──────────────────┐  ┌──────────────────────┐
│ renderer.render()│  │ renderer.render()    │
│ - ImGui loop     │  │ - Mosaic composables │
│ - Observe VM     │  │ - Observe VM         │
│ - Native window  │  │ - Terminal output    │
└──────────────────┘  └──────────────────────┘
```

### Mosaic Composables Structure (TUI)

```
@Composable
MosaicApp(viewModel)
    ├── when (mode)
    │   ├── EXTRACTION → ExtractionTui
    │   │   ├── Column
    │   │   │   ├── Header (box-drawn title)
    │   │   │   ├── Archive info (name, format, size)
    │   │   │   ├── Progress Bar (━━━━━━━━ 67%)
    │   │   │   ├── Status (stage, file counts, bytes)
    │   │   │   └── Close hint (standalone + auto-close)
    │   │
    │   └── SETUP → SettingsTui
    │       ├── Column
    │       │   ├── File Associations Status
    │       │   ├── Supported Formats List
    │       │   ├── Settings Display
    │       │   └── Available Commands
```

## Platform Abstraction

### Common Interface
```kotlin
interface FileSystemRepository {
    suspend fun extractArchive(archivePath: String): Flow<ExtractionProgress>
    suspend fun moveToTrash(filePath: String): Result<Unit>
    suspend fun getArchiveContents(archivePath: String): List<ArchiveEntry>
}
```

### Platform-Specific Implementations
- **Windows**: Win32 API for trash, Registry for file associations
- **macOS**: NSFileManager, Launch Services
- **Linux**: XDG standards, desktop integration

## Core Use Cases

1. **ExtractArchiveUseCase**
   - Analyzes archive contents
   - Determines extraction strategy (single file vs directory)
   - Coordinates extraction process

2. **ManageFileAssociationsUseCase**
   - Registers/unregisters file type associations
   - Handles double-click events

3. **TrashManagementUseCase**
   - Moves files to platform-appropriate trash location
   - Handles trash operation failures

## Data Flow

```
File Double-Click → OS Handler → Application Entry Point
                                        ↓
                                 Extract Command
                                        ↓
                               ExtractArchiveUseCase
                                        ↓
                           ┌─────────────┴─────────────┐
                           ↓                           ↓
                  Analyze Contents              Extract Files
                           ↓                           ↓
                  Determine Strategy           Update Progress
                           ↓                           ↓
                  Execute Extraction           Notify UI
                           ↓                           ↓
                    Move to Trash              Complete
```

## Technology Stack

- **Kotlin Multiplatform**: Cross-platform business logic
- **Kotlin Compose Compiler Plugin**: Required for Mosaic composables
- **Kotlin Coroutines**: Async operations
- **Kotlin Flow**: Reactive state management
- **Dear ImGui** (via cimgui C bindings): Native GUI for Windows standalone mode
- **DirectX 11**: GPU-accelerated rendering backend for ImGui on Windows
- **Mosaic**: Terminal UI framework for Kotlin/Native (0.19.0-SNAPSHOT with AnsiLevel support)
- **7zip**: Archive handling (via executable or library)
- **Platform APIs**: File system, trash, associations

## Testing Strategy

Gradle-integrated test pyramid, all run via `./gradlew mingwX64Test`:

- **Unit Tests** (`src/commonTest/`): Domain entities, use cases, viewmodels with mock dependencies. Flow testing via Turbine.
- **Integration Tests** (`src/mingwX64Test/.../integration/`): Real 7zip extraction, real filesystem operations, full ExtractArchiveUseCase pipeline.
- **E2E Tests** (`src/mingwX64Test/.../e2e/`): Launches compiled `qunzip.exe` as external process, verifies CLI args, exit codes, and timeout behavior.

Run selectively with `--tests` filter: `./gradlew mingwX64Test --tests "qunzip.e2e.*"`

## File Structure

```
libs/
├── cimgui/                    # Git submodule: C bindings to Dear ImGui
└── imgui-backend/
    └── imgui_app.cpp          # Custom Win32/DX11 wrapper for ImGui

src/
├── nativeInterop/cinterop/
│   └── cimgui.def             # Kotlin/Native cinterop definition for cimgui
├── commonMain/kotlin/qunzip/
│   ├── domain/
│   │   ├── entities/          # Core business objects
│   │   ├── usecases/          # Business logic
│   │   └── repositories/      # Repository interfaces
│   ├── presentation/
│   │   ├── viewmodels/        # State management
│   │   └── ui/                # UI layer
│   │       ├── UiRenderer.kt       # UiRenderer interface
│   │       ├── LaunchContext.kt    # Launch mode detection
│   │       └── tui/                # Mosaic Terminal UI
│   │           ├── MosaicApp.kt         # Root composable
│   │           ├── ExtractionTui.kt     # Extraction UI
│   │           └── SettingsTui.kt       # Settings UI
│   └── main.kt               # Application entry point
├── commonTest/kotlin/qunzip/  # Shared unit tests
├── mingwX64Main/kotlin/qunzip/
│   ├── platform/              # Windows repository implementations
│   ├── presentation/ui/
│   │   ├── LaunchContext.kt   # Windows launch detection (AttachConsole)
│   │   └── ImGuiRenderer.kt  # ImGui GUI renderer (DX11)
│   └── WindowsPlatform.kt     # DI and platform utilities
├── linuxX64Main/kotlin/qunzip/
│   ├── platform/              # Linux repository implementations
│   └── presentation/ui/
│       └── LaunchContext.kt   # Linux terminal detection
├── linuxArm64Main/kotlin/qunzip/
│   ├── platform/              # Linux ARM64 repository implementations
│   └── LinuxPlatform.kt
├── macosX64Main/kotlin/qunzip/
│   ├── platform/              # macOS Intel repository implementations
│   └── presentation/ui/
│       └── LaunchContext.kt   # macOS terminal detection
└── macosArm64Main/kotlin/qunzip/
    ├── platform/              # macOS ARM64 repository implementations
    └── MacosPlatform.kt
```
