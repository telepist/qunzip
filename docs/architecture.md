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
- **UI Layer**: Hybrid approach with multiple UI backends
  - **Mosaic TUI**: Interactive terminal UI (all platforms)
  - **Native GUIs**: Platform-specific dialogs (Windows, macOS, Linux)
  - **UI Renderer Interface**: Common abstraction for UI backends
- **Mappers**: Convert between domain and presentation models

## MVVM with Kotlin Flow

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   View/UI       │◄──►│   ViewModel     │◄──►│   Repository    │
│                 │    │                 │    │                 │
│ - TUI (Mosaic)  │    │ - State Flow    │    │ - File Ops      │
│ - Native GUI    │    │ - Commands      │    │ - 7zip Calls    │
│ - Notifications │    │ - Error Handle  │    │ - OS Integration│
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## UI Architecture (Hybrid Approach)

### UI Renderer Interface

The application uses a common `UiRenderer` interface that allows multiple UI backends:

```kotlin
interface UiRenderer {
    suspend fun render(viewModel: ApplicationViewModel, scope: CoroutineScope)
    fun isAvailable(): Boolean
}
```

### UI Backends

#### 1. Mosaic Terminal UI (Implemented)
- **Technology**: [Mosaic](https://github.com/JakeWharton/mosaic) library for terminal UI
- **Availability**: All platforms (Windows, macOS, Linux)
- **Features**:
  - Real-time progress bars with percentage
  - Color-coded stages (Analyzing, Extracting, Finalizing, Complete)
  - File count and byte statistics
  - Emojis for visual feedback
  - Box drawing characters for UI elements
  - Dynamic updates using ANSI escape codes

**TUI Components**:
- `MosaicApp`: Root composable that switches between modes
- `ExtractionTui`: Extraction progress display
- `SettingsTui`: File associations and settings display

#### 2. Native GUI
- **Windows**: Win32 API dialogs (implemented - progress window, settings window, MessageBox notifications)
- **macOS**: Cocoa/AppKit dialogs (planned - stubs exist)
- **Linux**: GTK dialogs (planned - stubs exist)
- **Implementation**: Platform-specific using C interop (Windows complete, others pending)

### Launch Context Detection

The application auto-detects how it was launched and selects the appropriate UI:

```kotlin
fun selectUiMode(args: List<String>): UiMode {
    return when {
        args.contains("--tui") -> UiMode.TUI        // Force TUI
        args.contains("--gui") -> UiMode.GUI        // Force GUI
        isGuiAvailable() && !isTerminal() -> UiMode.GUI  // Auto-detect GUI
        else -> UiMode.TUI                          // Default to TUI
    }
}
```

**Detection Logic**:
- **Windows**: Uses `GetStdHandle` and `GetFileType` to check if stdout is a console
- **macOS/Linux**: Uses POSIX `isatty()` to check if stdout is a TTY
- **GUI Launch**: Double-clicking file in Explorer/Finder (no console attached)
- **Terminal Launch**: Running from cmd.exe, PowerShell, bash, etc.

### UI Flow

```
Application Start
       ↓
┌──────────────┐
│ Parse Args   │
└──────┬───────┘
       ↓
┌──────────────────┐
│ Select UI Mode   │
│ (GUI or TUI)     │
└──────┬───────────┘
       ↓
┌──────────────────────┐
│ Create Renderer      │
│ - NativeGuiRenderer  │
│ - MosaicTuiRenderer  │
└──────┬───────────────┘
       ↓
┌──────────────────────┐
│ renderer.render()    │
│ - Observe ViewModel  │
│ - Update UI          │
│ - Handle events      │
└──────────────────────┘
```

### Mosaic Composables Structure

```
@Composable
MosaicApp(viewModel)
    ├── when (mode)
    │   ├── EXTRACTION → ExtractionTui
    │   │   ├── Column
    │   │   │   ├── Header (archive name)
    │   │   │   ├── Progress Bar ([████░░░] 67%)
    │   │   │   ├── Statistics (files, bytes)
    │   │   │   ├── Stage Indicator (🔍📦✨✅)
    │   │   │   └── Error Display (if any)
    │   │
    │   └── SETUP → SettingsTui
    │       ├── Column
    │       │   ├── File Associations Status
    │       │   ├── Supported Formats List
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
- **Mosaic**: Terminal UI framework for Kotlin/Native
- **7zip**: Archive handling (via executable or library)
- **Platform APIs**: File system, trash, associations, native GUIs

## Testing Strategy

- **Unit Tests**: Domain layer (use cases, entities)
- **Integration Tests**: Repository implementations
- **Platform Tests**: OS-specific functionality
- **E2E Tests**: Complete extraction workflows

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
│   │       ├── UiRenderer.kt       # UI backend interface
│   │       ├── LaunchContext.kt    # UI mode detection
│   │       └── tui/                # Mosaic Terminal UI
│   │           ├── MosaicApp.kt         # Root composable
│   │           ├── ExtractionTui.kt     # Extraction UI
│   │           └── SettingsTui.kt       # Settings UI
│   └── main.kt               # Application entry point
├── commonTest/kotlin/qunzip/  # Shared unit tests
├── mingwX64Main/kotlin/qunzip/
│   ├── platform/              # Windows repository implementations
│   ├── presentation/ui/       # Windows GUI
│   │   ├── Win32Gui.kt        # Win32 renderer (implemented)
│   │   └── LaunchContext.kt   # Terminal detection
│   └── WindowsPlatform.kt     # DI and platform utilities
├── linuxX64Main/kotlin/qunzip/
│   ├── platform/              # Linux repository implementations
│   ├── presentation/ui/       # Linux GUI
│   │   ├── GtkGui.kt          # GTK renderer (stub)
│   │   └── LaunchContext.kt   # Terminal detection
│   └── LinuxPlatform.kt       # DI and platform utilities
├── linuxArm64Main/kotlin/qunzip/
│   ├── platform/              # Linux ARM64 repository implementations
│   └── LinuxPlatform.kt
├── macosX64Main/kotlin/qunzip/
│   ├── platform/              # macOS Intel repository implementations
│   ├── presentation/ui/       # macOS GUI
│   │   ├── CocoaGui.kt        # Cocoa renderer (stub)
│   │   └── LaunchContext.kt   # Terminal detection
│   └── MacosPlatform.kt       # DI and platform utilities
└── macosArm64Main/kotlin/qunzip/
    ├── platform/              # macOS ARM64 repository implementations
    └── MacosPlatform.kt
```