# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Qunzip** (Quick Unzip) is a cross-platform archive extraction utility built with Kotlin Multiplatform. It provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives, inspired by macOS simplicity but targeting primarily Windows with Linux and macOS support.

The application follows **Clean Architecture** principles with **MVVM pattern**, uses **Kotlin Flow** for reactive programming, and strictly implements **Test-Driven Development (TDD)** with a target of **close to 100% test coverage**.

## Key Development Commands

### Build Commands
- `./gradlew buildAll` - Build debug executables for all platforms
- `./gradlew buildAllRelease` - Build optimized release executables for all platforms
- `./gradlew linkDebugExecutable<Platform>` - Build for specific platform (e.g., MacosArm64, LinuxX64, MingwX64)
- `./gradlew linkReleaseExecutable<Platform>` - Build release version for specific platform
- `./gradlew download7zip` - Download 7-Zip binaries (automatic during Windows build)

### Testing Commands (Test Pyramid)
- `./gradlew testAll` - Run entire test pyramid (unit + integration + e2e)
- `./gradlew mingwX64Test` - Run all Windows tests (unit + integration + e2e)
- `./gradlew mingwX64Test --tests "qunzip.domain.*"` - Unit tests only (entities, use cases)
- `./gradlew mingwX64Test --tests "qunzip.presentation.*"` - ViewModel tests only
- `./gradlew mingwX64Test --tests "qunzip.integration.*"` - Integration tests only (real 7zip)
- `./gradlew mingwX64Test --tests "qunzip.e2e.*"` - E2E tests only (launches qunzip.exe)

### Running the Application
- **Extract an archive**: `./build/bin/<platform>/debugExecutable/qunzip.kexe <archive-file>`
- **Settings mode**: `./build/bin/<platform>/debugExecutable/qunzip.kexe` (no file argument)
- The application uses Mosaic TUI for all rendering (both CLI and double-click launches)

### CLI Arguments
| Argument | Description |
|----------|-------------|
| `--help`, `-h` | Display help information |
| `--version`, `-v` | Display version information |
| `--register-associations` | Register file associations (installer integration) |
| `--unregister-associations` | Unregister file associations |
| `--set-trash-on` | Enable moving archives to trash after extraction |
| `--set-trash-off` | Disable moving archives to trash after extraction |
| `--set-dialog-on` | Enable completion dialog after extraction |
| `--set-dialog-off` | Disable completion dialog (silent exit, default) |
| `--force-standalone` | Force standalone mode (for testing standalone exit behavior) |

### Windows Installer Commands
- `./gradlew prepareInstallerResources` - Prepare files for installer
- `./gradlew buildWindowsInstaller` - Build Windows installer (requires Inno Setup 6)
- `./gradlew createPortableZip` - Create portable ZIP distribution
- `./gradlew packageWindows` - Build both installer and portable ZIP
- `./gradlew cleanInstaller` - Clean installer build artifacts

**Prerequisites for installer build:**
- Inno Setup 6.x installed at `C:\Program Files (x86)\Inno Setup 6\` (or set `ISCC_PATH` environment variable)
- Run on Windows OS

**Installer outputs:**
- `build/installer-output/qunzip-setup-{version}.exe` - Windows installer
- `build/dist/qunzip-{version}-windows-portable.zip` - Portable ZIP

### Other Commands
- `./gradlew clean` - Clean build artifacts
- `make install` - Build release and update local installation (detects existing install path)

## Architecture

### Clean Architecture Layers

#### Domain Layer (`src/commonMain/kotlin/qunzip/domain/`)
- **Entities**: `Archive`, `ArchiveEntry`, `ExtractionResult`, `FileAssociation`, `UserPreferences`
- **Use Cases**: `ExtractArchiveUseCase`, `ValidateArchiveUseCase`, `ManageFileAssociationsUseCase`
- **Repository Interfaces**: Abstract contracts for platform-specific implementations (`ArchiveRepository`, `FileSystemRepository`, `NotificationRepository`, `FileAssociationRepository`, `PreferencesRepository`)

#### Data Layer (Platform-specific `src/<platform>Main/kotlin/`)
- **Repository Implementations**: Platform-specific data access
- **7zip Integration**: Archive extraction via 7zip executable/library
- **OS Services**: File system operations, notifications, file associations

#### Presentation Layer (`src/commonMain/kotlin/qunzip/presentation/`)
- **ViewModels**: `ExtractionViewModel`, `FileAssociationViewModel`, `ApplicationViewModel`, `SettingsViewModel`
- **State Management**: Kotlin Flow with StateFlow/SharedFlow patterns
- **UI Layer**: Mosaic TUI for all platforms
  - **Mosaic TUI**: Terminal UI with progress bars, colors, and real-time updates
  - **Standalone detection**: On Windows, detects double-click vs CLI launch via `GetConsoleProcessList`
  - **Standalone mode**: Uses Mosaic's `NonInteractiveTerminal` with truecolor to avoid raw mode issues

### MVVM with Kotlin Flow
- ViewModels expose `StateFlow` for UI state
- Events communicated via `SharedFlow`
- Reactive programming with coroutines and Flow operators
- Platform-agnostic business logic

### Platform Abstraction
Common interfaces in domain layer, platform-specific implementations:
- **Windows**: Win32 API for trash, Registry for file associations
- **macOS**: NSFileManager, Launch Services
- **Linux**: XDG standards, desktop integration

### Supported Platforms
- Windows x64 (MinGW) - **primary target**
- macOS ARM64 (Apple Silicon)
- macOS x64 (Intel)
- Linux x64
- Linux ARM64

### Dependencies
- **Kotlin Multiplatform**: `2.3.10`
- **Kotlin Compose Compiler Plugin**: `2.3.10` - Required for Mosaic composables
- **Kotlinx Coroutines**: `1.8.0` - Async operations
- **Kotlinx Serialization**: `1.6.3` - Data serialization
- **Kotlinx DateTime**: `0.5.0` - Date/time handling
- **Kermit**: `2.0.3` - Multiplatform logging
- **Mosaic**: `0.19.0-SNAPSHOT` - Terminal UI framework for Kotlin/Native (local build with AnsiLevel support)
- **Turbine**: `1.0.0` - Flow testing (test only)

### Build Configuration
- Executables named `qunzip` across all platforms
- Debug builds: `build/bin/<platform>/debugExecutable/`
- Release builds: `build/bin/<platform>/releaseExecutable/`
- Entry point: `qunzip.main`

## Project Structure

```
src/
├── commonMain/kotlin/qunzip/
│   ├── domain/
│   │   ├── entities/          # Core business objects
│   │   ├── usecases/          # Business logic
│   │   └── repositories/      # Abstract contracts
│   ├── presentation/
│   │   ├── viewmodels/        # State management
│   │   └── ui/                # UI layer
│   │       ├── UiRenderer.kt       # Mosaic TUI renderer
│   │       ├── LaunchContext.kt    # Launch mode detection
│   │       └── tui/                # Mosaic Terminal UI
│   │           ├── MosaicApp.kt         # Root Mosaic composable
│   │           ├── ExtractionTui.kt     # Extraction progress TUI
│   │           └── SettingsTui.kt       # Settings display TUI
│   └── main.kt               # Application entry point
├── commonTest/kotlin/qunzip/  # Unit tests (entities, use cases, viewmodels)
├── mingwX64Main/kotlin/qunzip/     # Windows x64 (MinGW) implementations
│   ├── platform/                   # Windows repository implementations
│   │   ├── WindowsArchiveRepository.kt
│   │   ├── WindowsFileSystemRepository.kt
│   │   ├── WindowsNotificationRepository.kt
│   │   ├── WindowsFileAssociationRepository.kt
│   │   └── WindowsPreferencesRepository.kt
│   ├── presentation/ui/
│   │   └── LaunchContext.kt        # Windows standalone launch detection
│   ├── resources/                  # Windows resources
│   │   ├── qunzip.rc               # Resource file (icon, version info)
│   │   └── qunzip.exe.manifest     # Windows manifest
│   └── WindowsPlatform.kt          # DI and platform utilities
├── mingwX64Test/kotlin/qunzip/     # Windows platform tests
│   ├── TestHelpers.kt              # Process execution, temp dirs, fixtures
│   ├── integration/                # Integration tests (real 7zip + filesystem)
│   │   ├── ArchiveExtractionIntegrationTest.kt
│   │   └── ExtractionPipelineIntegrationTest.kt
│   ├── e2e/                        # End-to-end tests (launches qunzip.exe)
│   │   └── QunzipExeE2eTest.kt
│   └── resources/fixtures/         # Test archive files
├── linuxX64Main/kotlin/qunzip/     # Linux x64 implementations
│   └── presentation/ui/
│       └── LaunchContext.kt        # Linux terminal detection
├── linuxArm64Main/kotlin/qunzip/   # Linux ARM64 implementations
├── macosX64Main/kotlin/qunzip/     # macOS Intel implementations
│   └── presentation/ui/
│       └── LaunchContext.kt        # macOS terminal detection
└── macosArm64Main/kotlin/qunzip/   # macOS Apple Silicon implementations
```

## Core Functionality

### Archive Extraction Logic
1. **Single file archive** → Extract to same directory
2. **Multiple files** → Create directory with archive name
3. **Single root directory** → Extract contents to same directory
4. **Post-extraction** → Move original archive to trash

### File/Folder Conflict Handling
When extracting would overwrite an existing file or folder, the application handles conflicts manually (not via 7zip flags):
- **Single file archives**: Extract to temp folder (`qunzip_<hash>`), move file with unique name (`file-1.pdf`, `file-2.pdf`, etc.), delete temp folder
- **Single folder archives**: Extract to temp folder, move folder with unique name (`folder-1`, `folder-2`, etc.), delete temp folder
- **Multi-file archives**: Create uniquely named destination folder directly (`project-1`, `project-2`, etc.) - no temp folder needed

### Supported Archive Formats
- ZIP (.zip)
- 7-Zip (.7z)
- RAR (.rar)
- TAR (.tar, .tar.gz, .tar.bz2, .tar.xz)
- CAB (.cab), ARJ (.arj), LZH (.lzh)

### File Association Handling
- Registers as default handler for supported formats
- Handles double-click events through OS integration
- Platform-specific registry/launch services management

### User Preferences
Stored in JSON format at `~/.qunzip/preferences.json`:
- `moveToTrashAfterExtraction` - Move archive to trash after successful extraction (default: false)
- `showCompletionDialog` - Show completion dialog after extraction; when false, app silently closes (default: false)

## Development Notes

### Testing Strategy (CRITICAL)

**This project strictly follows Test-Driven Development (TDD) with a target of close to 100% test coverage.**

#### TDD Workflow (MANDATORY)
1. **Write tests FIRST** before implementing any new feature or bug fix
2. **Red**: Write a failing test that defines the expected behavior
3. **Green**: Write the minimum code to make the test pass
4. **Refactor**: Clean up the code while keeping tests green
5. **Repeat** for each new behavior or edge case

#### Test Coverage Requirements
- **Domain layer (entities, use cases)**: Must have 100% test coverage
- **ViewModels**: Must have comprehensive state and event tests
- **Repository implementations**: Must have integration tests
- **Edge cases**: Every edge case and error condition must be tested
- **Conflict handling**: All file/folder naming conflicts must be tested

#### Test Pyramid Organization
- **Unit tests** in `src/commonTest/kotlin/qunzip/domain/` and `src/commonTest/kotlin/qunzip/presentation/viewmodels/`
  - Domain entities, use cases, viewmodels with mock dependencies
  - Flow testing using Turbine library
- **Integration tests** in `src/mingwX64Test/kotlin/qunzip/integration/`
  - Real `WindowsArchiveRepository` with 7zip, real `WindowsFileSystemRepository`
  - Full `ExtractArchiveUseCase` pipeline with real archive files
- **E2E tests** in `src/mingwX64Test/kotlin/qunzip/e2e/`
  - Launch compiled `qunzip.exe` as external process
  - Verify CLI arguments, exit codes, timeout behavior (standalone exit)
- **Test helpers** in `src/mingwX64Test/kotlin/qunzip/TestHelpers.kt`
  - Process execution with pipe draining and timeout
  - Temp directory management, fixture paths
- **Test fixtures** in `src/mingwX64Test/resources/fixtures/`

#### When Adding New Features
1. Identify all scenarios and edge cases
2. Write tests for the happy path
3. Write tests for each edge case (e.g., file conflicts, errors)
4. Write tests for error handling
5. Implement the feature to pass all tests
6. Verify no existing tests are broken

#### Example: File Conflict Handling Tests
When a feature like "avoid overwriting files" is added, tests must cover:
- No conflict (direct extraction)
- Single conflict (-1 suffix)
- Multiple conflicts (-1, -2, -3, etc.)
- Files with extensions (name-1.ext)
- Files without extensions (name-1)
- Folders (folder-1)
- Temp folder conflicts
- Notification shows correct final path
- Cleanup of temp resources

### Logging
- Uses Kermit for structured, multiplatform logging
- Tagged loggers for different components
- Configurable log levels per platform

### Error Handling
- Sealed class hierarchy for typed errors
- Comprehensive error scenarios covered
- User-friendly error messages via notifications

### State Management
- Immutable state objects
- Unidirectional data flow
- Clear separation of UI state and business logic

### 7zip Integration
- Platform-specific implementations using 7zip executables
- Progress tracking for large archive extractions
- Comprehensive format support through 7zip

## Current Development Status

**Phase**: TUI-Only Redesign
**Progress**: Core architecture complete, Windows platform functional, Mosaic TUI is the sole UI

### ✅ Completed
- Clean Architecture with MVVM setup (100%)
- Domain layer entities and use cases (100%)
- Repository interfaces defined (100%)
- ViewModels with Kotlin Flow (100%)
- TDD framework with unit tests (100%)
- Build configuration for all platforms (100%)
- **Windows Platform Implementation (100%)**
  - WindowsArchiveRepository (7zip integration with real-time progress)
  - WindowsFileSystemRepository (file ops, Recycle Bin via SHFileOperation)
  - WindowsNotificationRepository (console notifications)
  - WindowsFileAssociationRepository (stub)
  - WindowsPreferencesRepository (JSON file-based settings storage)
  - WindowsPlatform.kt (DI and platform utilities)
  - Embedded application icon in executable
- **Mosaic Terminal UI (100%)**
  - TUI with real-time progress updates
  - Progress bars, colors for visual feedback
  - Extraction progress display (stages, files, bytes)
  - Settings/file associations display
  - Standalone launch detection (Windows: `GetConsoleProcessList`)
  - Standalone mode uses `NonInteractiveTerminal` with truecolor
  - CLI mode uses full interactive `TtyTerminal`
- **Black Box E2E Test Scripts**
  - Windows, Linux, and macOS test scripts created
  - Test fixtures prepared (single-file.zip, multiple-files.zip, nested-folder.zip)
  - TUI extraction tested and working
- **Windows Installer (100%)**
  - Inno Setup script for Windows installer
  - Portable ZIP distribution support
  - Icon and resource compilation

### ⏳ Pending
- Linux/macOS platform full repository implementations (stubs with NotImplementedError)
- End-to-end integration testing on Linux/macOS
- Windows Registry file associations (advanced feature)

See `docs/development-progress.md` for detailed status and next steps.

## Documentation

Comprehensive documentation available in `/docs/`:
- `architecture.md` - Detailed architecture overview
- `ux-design.md` - User experience design principles
- `user-manual.md` - End-user documentation
- `project-management.md` - Agile development process
- `development-progress.md` - Current development status and progress tracking
- `windows-installer.md` - Windows installer build process and configuration
