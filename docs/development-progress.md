# Qunzip Development Progress

## Current Status

**Phase**: Dual UI (ImGui GUI + Mosaic TUI), Windows Platform Functional

## Completed Work

### ✅ Foundation Architecture

#### Epic 1: Foundation Architecture - **COMPLETE**
- ✅ Clean Architecture setup with MVVM
  - Domain layer with entities, use cases, and repository interfaces
  - Presentation layer with ViewModels using Kotlin Flow
  - Clear separation of concerns across layers
- ✅ Platform abstraction layer
  - Repository interfaces defined for all platform-specific operations
  - `ArchiveRepository` - Archive operations (extraction, validation, content analysis)
  - `FileSystemRepository` - File operations (read, write, trash, space checks)
  - `NotificationRepository` - User notifications (success, error, progress)
  - `FileAssociationRepository` - OS file type associations
- ✅ Kotlin Flow integration
  - StateFlow for UI state management
  - SharedFlow for one-time events
  - Flow-based progress tracking for long-running operations
- ✅ TDD testing framework with full test pyramid
  - 52 unit tests (domain entities, use cases, viewmodels)
  - 15 integration tests (real 7zip + filesystem)
  - 12 E2E tests (launches compiled qunzip.exe)
  - All tests run via Gradle (`./gradlew mingwX64Test`)
  - ViewModel testing with Turbine
  - Mock implementations for unit test isolation

#### Entities Implemented
- ✅ `Archive` - Represents archive files with metadata
- ✅ `ArchiveEntry` - Individual files/folders within archives
- ✅ `ArchiveContents` - Complete archive structure analysis
- ✅ `ArchiveFormat` - Supported formats (ZIP, 7Z, RAR, TAR, etc.)
- ✅ `ExtractionResult` - Success/Failure outcomes
- ✅ `ExtractionProgress` - Real-time progress tracking
- ✅ `ExtractionStage` - Extraction lifecycle stages
- ✅ `ExtractionStrategy` - Smart extraction logic
- ✅ `ExtractionError` - Typed error hierarchy (extends Throwable)
- ✅ `FileAssociation` - OS file associations
- ✅ `FileInfo` - File metadata

#### Use Cases Implemented
- ✅ `ExtractArchiveUseCase` - Core extraction orchestration
  - Archive validation and analysis
  - Smart extraction strategy determination
  - Disk space checking
  - Progress tracking with Flow
  - Error handling and notifications
- ✅ `ValidateArchiveUseCase` - Archive validation
  - File existence and readability checks
  - Format detection from filename
  - Archive integrity testing with 7zip
  - Comprehensive error scenarios
- ✅ `ManageFileAssociationsUseCase` - File association management
  - Register/unregister associations for all supported formats
  - Check current association status
  - Handle file open events

#### ViewModels Implemented
- ✅ `ExtractionViewModel` - Extraction state management
  - Loading and extraction states
  - Progress tracking
  - Error handling
  - Cancellation support
- ✅ `FileAssociationViewModel` - File association state
  - Association status tracking
  - File open event handling
- ✅ `ApplicationViewModel` - Main application orchestration
  - Coordinates child ViewModels
  - Application lifecycle management
  - Event routing
  - Standalone launch detection (controls exit behavior)
- ✅ `SettingsViewModel` - User preferences management

#### Application Structure
- ✅ `main.kt` - Application entry point
  - Coroutine scope management
  - Dependency initialization structure
  - Standalone launch detection and console configuration
  - Event handling
  - Application lifecycle

### ✅ ImGui GUI (Windows)

- ✅ **ImGui renderer with DX11 backend**
  - cimgui static library build integrated into Gradle
  - DX11 linking and GPU-accelerated rendering
  - GUI subsystem (`-mwindows`) for windowless launch
  - Dark title bar via `DwmSetWindowAttribute`
  - DPI awareness (per-monitor DPI scaling)
- ✅ **Settings UI in ImGui**
  - Full preferences management in graphical interface

### ✅ Mosaic TUI (CLI)

Mosaic TUI is used for CLI-mode launches across all platforms.

- ✅ **Mosaic Terminal UI**
  - Real-time progress bars with percentage and color coding
  - Archive info display (name, format, size)
  - Stage indicators (Starting, Analyzing, Extracting, Finalizing, Completed, Failed)
  - File count and byte statistics
  - Box drawing characters for header
  - Settings and file associations display
- ✅ **Standalone launch detection**
  - Windows: `GetConsoleProcessList` (count ≤ 1 = standalone)
  - Other platforms: `!isTerminal()`
- ✅ **Dual terminal mode**
  - CLI: Full interactive `TtyTerminal` with auto-detected ANSI level
  - Standalone: `NonInteractiveTerminal` with truecolor (avoids raw mode / blocking stdin)
  - Windows standalone: `configureStandaloneConsole()` enables VT processing and sets title

### Build & Test Infrastructure
- ✅ Multi-platform Kotlin/Native build configuration
  - Windows x64 (MinGW)
  - macOS ARM64 (Apple Silicon)
  - macOS x64 (Intel)
  - Linux x64
  - Linux ARM64
- ✅ Build successfully compiles for all platforms
- ✅ All 79 tests passing (52 unit + 15 integration + 12 E2E)
- ✅ Clean separation between common and platform-specific code
- ✅ Makefile with `install` target for updating local installations

### ✅ Windows Platform Implementation
- ✅ `WindowsArchiveRepository` - 7zip integration with real-time progress
- ✅ `WindowsFileSystemRepository` - File operations, Recycle Bin via SHFileOperation
- ✅ `WindowsNotificationRepository` - Console-based notifications
- ✅ `WindowsFileAssociationRepository` - Stub for registry access
- ✅ `WindowsPreferencesRepository` - JSON file-based settings storage
- ✅ `WindowsPlatform.kt` - DI and platform utilities
- ✅ Embedded application icon in executable
- ✅ Windows installer (Inno Setup) and portable ZIP distribution

### ✅ User Preferences System
- ✅ Move to trash after extraction (optional, default: off)
- ✅ Auto-close after extraction (optional, default: on)
- ✅ CLI flags for preference configuration

### ✅ Smart Duplicate Handling
- ✅ Manual file/folder conflict detection
- ✅ Unique naming with numeric suffixes (file-1, file-2, etc.)

### ✅ Gradle-Integrated Test Pyramid
- ✅ Unit tests in `src/commonTest/` — domain entities, use cases, viewmodels
- ✅ Integration tests in `src/mingwX64Test/kotlin/qunzip/integration/` — real 7zip + filesystem
  - `ArchiveExtractionIntegrationTest` (11 tests) — getArchiveInfo, getArchiveContents, testArchive, extractArchive
  - `ExtractionPipelineIntegrationTest` (4 tests) — full ExtractArchiveUseCase with real repositories
- ✅ E2E tests in `src/mingwX64Test/kotlin/qunzip/e2e/` — launches compiled qunzip.exe
  - `QunzipExeE2eTest` (12 tests) — CLI args, extraction exit codes, standalone exit, settings flags
- ✅ Test helpers in `src/mingwX64Test/kotlin/qunzip/TestHelpers.kt` — process execution with pipe draining, temp dirs, fixtures
- ✅ Test fixtures in `src/mingwX64Test/resources/fixtures/` (single-file.zip, multiple-files.zip, nested-folder.zip)

## Pending

- ⏳ Linux platform repositories (full implementation - stubs exist)
- ⏳ macOS platform repositories (full implementation - stubs exist)
- ⏳ Windows Registry file associations (advanced feature)
- ⏳ End-to-end integration testing on Linux/macOS

## Technical Debt & Issues

### Resolved
- ✅ ExtractionError type hierarchy (fixed to extend Throwable)
- ✅ Test mock implementations (fixed TODO() calls)
- ✅ Test timing issues with SharedFlow (simplified test approach)
- ✅ Use case extensibility for testing (made classes open)
- ✅ Windows platform repositories implemented
- ✅ Removed native GUI code (Win32, Cocoa, GTK) - TUI only

### Current
- ⚠️ Windows disk space check is stubbed (returns MAX_VALUE)
- ⚠️ Windows file associations are stubbed (no Registry access yet)
- ⚠️ Linux platform has only stub implementations (not functional)
- ⚠️ macOS platform has only stub implementations (not functional)
- ✅ E2E tests validated and passing (Gradle-integrated)

### Future Considerations
- Consider dependency injection framework (Koin)
- Password-protected archive support (Phase 3)
- Progress cancellation implementation
- Performance optimization for large archives

## Code Quality Metrics

### Current Status
- **Test Coverage**: Domain layer ~95%, Presentation layer ~90%
- **Tests**: 79 total (52 unit + 15 integration + 12 E2E), all Gradle-integrated
- **Build Status**: ✅ All platforms building successfully
- **Test Fixtures**: 3 sample ZIP files (single-file, multiple-files, nested-folder)

### Quality Gates
- ✅ Windows build working successfully
- ✅ Unit tests passing
- ✅ Clean Architecture principles followed
- ✅ Windows platform repositories implemented
- ✅ Mosaic TUI functional for all launch modes
- ✅ Windows integration complete (DI + main.kt wired)
- ⚠️ Linux/macOS platforms have stubs (not functional)
- ✅ Test pyramid integrated in Gradle (unit + integration + E2E)

## Design Decisions

### Dual UI Architecture
- **ImGui GUI** for standalone/double-click launches on Windows (DX11 backend, dark title bar, DPI-aware)
- **Mosaic TUI** for CLI launches across all platforms
- Standalone launches detected via `GetConsoleProcessList` on Windows
- GUI subsystem (`-mwindows`) used for ImGui builds to suppress console window
- cimgui (C bindings for Dear ImGui) built as a static library and linked into the Kotlin/Native executable

### Standalone Launch Detection (Windows)
- `GetConsoleProcessList` returns count of processes attached to console
- Count ≤ 1 means standalone launch (Windows created the console for us)
- Count > 1 means CLI launch (sharing console with shell)
- Standalone mode sets console title and enables VT processing for ANSI colors

### Mosaic Customization
- Using local build of Mosaic (0.19.0-SNAPSHOT) published to mavenLocal
- Added `ansiLevel` parameter to `runMosaic()` API
- `NonInteractiveTerminal` accepts configurable `AnsiLevel` (was hardcoded NONE)
- Allows truecolor rendering in standalone mode without TTY binding
