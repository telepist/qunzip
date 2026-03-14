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
- **UI Layer**: Mosaic TUI for all platforms
  - **Mosaic TUI**: Terminal UI with progress bars, colors, and real-time updates
  - **UI Renderer**: `MosaicTuiRenderer` with standalone/CLI mode support
- **Mappers**: Convert between domain and presentation models

## MVVM with Kotlin Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   View/UI       │◄──►│   ViewModel     │◄──►│   Repository    │
│                 │    │                 │    │                 │
│ - TUI (Mosaic)  │    │ - State Flow    │    │ - File Ops      │
│ - Notifications │    │ - Commands      │    │ - 7zip Calls    │
│                 │    │ - Error Handle  │    │ - OS Integration│
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## UI Architecture

### MosaicTuiRenderer

The application uses a single `MosaicTuiRenderer` that adapts its behavior based on how the application was launched:

- **CLI mode** (launched from terminal): Uses Mosaic's full interactive `TtyTerminal` with auto-detected ANSI capabilities. Exits cleanly when extraction completes, returning control to the shell.
- **Standalone mode** (double-click / file association): Uses Mosaic's `NonInteractiveTerminal` with truecolor ANSI level. This avoids raw mode and blocking stdin reads that would prevent clean process exit. Console VT processing is enabled via `SetConsoleMode` on Windows.

```kotlin
class MosaicTuiRenderer(
    private val isStandaloneLaunch: Boolean = false,
) : UiRenderer {
    // Standalone: runMosaic(onNonInteractive = AssumeAndIgnore, ansiLevel = TRUECOLOR)
    // CLI: runMosaic { ... } (full interactive terminal)
}
```

### Launch Context Detection

The application detects how it was launched to select the appropriate terminal mode:

- **Windows**: Uses `GetConsoleProcessList` — if only 1 process is attached to the console, it means Windows created the console for this process (standalone launch). If >1 processes, we're sharing a console with a shell (CLI launch).
- **macOS/Linux**: Uses `isatty()` — if stdout is not a TTY, it's a standalone launch.

For standalone launches on Windows, `configureStandaloneConsole()` sets the console title and enables ANSI/VT100 escape sequence processing via `ENABLE_VIRTUAL_TERMINAL_PROCESSING`.

### UI Flow

```
Application Start
       ↓
┌──────────────┐
│ Parse Args   │
└──────┬───────┘
       ↓
┌──────────────────┐
│ Detect Launch    │
│ Mode (standalone │
│ vs CLI)          │
└──────┬───────────┘
       ↓
┌──────────────────────┐
│ MosaicTuiRenderer    │
│ - NonInteractive     │
│   (standalone)       │
│ - Interactive (CLI)  │
└──────┬───────────────┘
       ↓
┌──────────────────────┐
│ renderer.render()    │
│ - Observe ViewModel  │
│ - Update TUI         │
│ - Handle exit        │
└──────────────────────┘
```

### Mosaic Composables Structure

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
    │   │   │   └── Close hint (standalone + completion dialog)
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
src/
├── commonMain/kotlin/qunzip/
│   ├── domain/
│   │   ├── entities/          # Core business objects
│   │   ├── usecases/          # Business logic
│   │   └── repositories/      # Repository interfaces
│   ├── presentation/
│   │   ├── viewmodels/        # State management
│   │   └── ui/                # UI layer
│   │       ├── UiRenderer.kt       # Mosaic TUI renderer
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
│   │   └── LaunchContext.kt   # Windows standalone detection (GetConsoleProcessList)
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
