# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gunzip** is a cross-platform archive extraction utility built with Kotlin Multiplatform. It provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives, inspired by macOS simplicity but targeting primarily Windows with Linux and macOS support.

The application follows **Clean Architecture** principles with **MVVM pattern**, uses **Kotlin Flow** for reactive programming, and strictly implements **Test-Driven Development (TDD)** with a target of **close to 100% test coverage**.

## Key Development Commands

### Build Commands
- `./gradlew buildAll` - Build debug executables for all platforms
- `./gradlew buildAllRelease` - Build optimized release executables for all platforms
- `./gradlew linkDebugExecutable<Platform>` - Build for specific platform (e.g., MacosArm64, LinuxX64, MingwX64)
- `./gradlew linkReleaseExecutable<Platform>` - Build release version for specific platform

### Testing Commands
- `./gradlew testAll` - Run tests on all platforms
- `./gradlew test` - Run common tests
- `./gradlew <platform>Test` - Run platform-specific tests

### Running the Application
- **GUI mode** (double-click or auto-detect): `./build/bin/<platform>/debugExecutable/gunzip.kexe <archive-file>`
- **TUI mode** (terminal UI): `./build/bin/<platform>/debugExecutable/gunzip.kexe --tui <archive-file>`
- **Force GUI mode**: `./build/bin/<platform>/debugExecutable/gunzip.kexe --gui <archive-file>`
- **Settings mode**: `./build/bin/<platform>/debugExecutable/gunzip.kexe` (no file argument)
- The application auto-detects launch context and chooses appropriate UI (native GUI or terminal TUI)

### CLI Arguments
| Argument | Description |
|----------|-------------|
| `--gui` | Force GUI mode |
| `--tui` | Force TUI (terminal UI) mode |
| `--help`, `-h` | Display help information |
| `--version`, `-v` | Display version information |
| `--register-associations` | Register file associations (installer integration) |
| `--unregister-associations` | Unregister file associations |
| `--set-trash-on` | Enable moving archives to trash after extraction |
| `--set-trash-off` | Disable moving archives to trash after extraction |
| `--set-dialog-on` | Enable completion dialog after extraction |
| `--set-dialog-off` | Disable completion dialog (silent exit, default) |

### Windows Installer Commands
- `./gradlew prepareInstallerResources` - Prepare files for installer
- `./gradlew buildWindowsInstaller` - Build Windows installer (requires Inno Setup 6)
- `./gradlew createPortableZip` - Create portable ZIP distribution
- `./gradlew packageWindows` - Build both installer and portable ZIP
- `./gradlew cleanInstaller` - Clean installer build artifacts

**Prerequisites for installer build:**
- Inno Setup 6.x installed at `C:\Program Files (x86)\Inno Setup 6\` (or set `ISCC_PATH` environment variable)
- Run on Windows OS
- Icon file at `installer/windows/icon.ico` (optional, see `installer/windows/ICON-README.md`)

**Installer outputs:**
- `build/installer-output/gunzip-setup-{version}.exe` - Windows installer
- `build/dist/gunzip-{version}-windows-portable.zip` - Portable ZIP

### Other Commands
- `./gradlew clean` - Clean build artifacts

## Architecture

### Clean Architecture Layers

#### Domain Layer (`src/commonMain/kotlin/gunzip/domain/`)
- **Entities**: `Archive`, `ArchiveEntry`, `ExtractionResult`, `FileAssociation`, `UserPreferences`
- **Use Cases**: `ExtractArchiveUseCase`, `ValidateArchiveUseCase`, `ManageFileAssociationsUseCase`
- **Repository Interfaces**: Abstract contracts for platform-specific implementations (`ArchiveRepository`, `FileSystemRepository`, `NotificationRepository`, `FileAssociationRepository`, `PreferencesRepository`)

#### Data Layer (Platform-specific `src/<platform>Main/kotlin/`)
- **Repository Implementations**: Platform-specific data access
- **7zip Integration**: Archive extraction via 7zip executable/library
- **OS Services**: File system operations, notifications, file associations

#### Presentation Layer (`src/commonMain/kotlin/gunzip/presentation/`)
- **ViewModels**: `ExtractionViewModel`, `FileAssociationViewModel`, `ApplicationViewModel`, `SettingsViewModel`
- **State Management**: Kotlin Flow with StateFlow/SharedFlow patterns
- **UI Layer**: Hybrid approach with Mosaic TUI and platform-native GUIs
  - **Mosaic TUI**: Interactive terminal UI with progress bars, colors, and real-time updates (all platforms)
  - **Native GUIs**: Platform-specific dialogs (Windows Win32 complete, macOS/Linux stubs)
  - **Auto-detection**: Chooses GUI for double-click, TUI for terminal launch

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
- **Kotlin Multiplatform**: `2.2.20`
- **Kotlin Compose Compiler Plugin**: `2.2.20` - Required for Mosaic composables
- **Kotlinx Coroutines**: `1.8.0` - Async operations
- **Kotlinx Serialization**: `1.6.3` - Data serialization
- **Kotlinx DateTime**: `0.5.0` - Date/time handling
- **Kermit**: `2.0.3` - Multiplatform logging
- **Mosaic**: `0.18.0` - Terminal UI framework for Kotlin/Native
- **Turbine**: `1.0.0` - Flow testing (test only)

### Build Configuration
- Executables named `gunzip` across all platforms
- Debug builds: `build/bin/<platform>/debugExecutable/`
- Release builds: `build/bin/<platform>/releaseExecutable/`
- Entry point: `gunzip.main`

## Project Structure

```
src/
├── commonMain/kotlin/gunzip/
│   ├── domain/
│   │   ├── entities/          # Core business objects
│   │   ├── usecases/          # Business logic
│   │   └── repositories/      # Abstract contracts
│   ├── presentation/
│   │   ├── viewmodels/        # State management
│   │   └── ui/                # UI layer
│   │       ├── UiRenderer.kt       # UI backend interface
│   │       ├── LaunchContext.kt    # UI mode detection
│   │       └── tui/                # Mosaic Terminal UI
│   │           ├── MosaicApp.kt         # Root Mosaic composable
│   │           ├── ExtractionTui.kt     # Extraction progress TUI
│   │           └── SettingsTui.kt       # Settings display TUI
│   └── main.kt               # Application entry point
├── commonTest/kotlin/gunzip/  # Shared tests
├── mingwX64Main/kotlin/gunzip/     # Windows x64 (MinGW) implementations
│   ├── platform/                   # Windows repository implementations
│   │   ├── WindowsArchiveRepository.kt
│   │   ├── WindowsFileSystemRepository.kt
│   │   ├── WindowsNotificationRepository.kt
│   │   ├── WindowsFileAssociationRepository.kt
│   │   └── WindowsPreferencesRepository.kt
│   ├── presentation/ui/            # Windows native GUI
│   │   ├── Win32Gui.kt             # Win32 GUI renderer with progress window + settings window
│   │   └── LaunchContext.kt        # Windows terminal detection
│   ├── resources/                  # Windows resources
│   │   ├── gunzip.rc               # Resource file (icon, version info)
│   │   └── gunzip.exe.manifest     # Windows manifest
│   └── WindowsPlatform.kt          # DI and platform utilities
├── linuxX64Main/kotlin/gunzip/     # Linux x64 implementations
│   └── presentation/ui/            # Linux GUI (stubs)
│       ├── GtkGui.kt               # GTK GUI renderer (future)
│       └── LaunchContext.kt        # Linux terminal detection
├── linuxArm64Main/kotlin/gunzip/   # Linux ARM64 implementations
├── macosX64Main/kotlin/gunzip/     # macOS Intel implementations
│   └── presentation/ui/            # macOS GUI (stubs)
│       ├── CocoaGui.kt             # Cocoa GUI renderer (future)
│       └── LaunchContext.kt        # macOS terminal detection
└── macosArm64Main/kotlin/gunzip/   # macOS Apple Silicon implementations
```

## Core Functionality

### Archive Extraction Logic
1. **Single file archive** → Extract to same directory
2. **Multiple files** → Create directory with archive name
3. **Single root directory** → Extract contents to same directory
4. **Post-extraction** → Move original archive to trash

### File/Folder Conflict Handling
When extracting would overwrite an existing file or folder, the application handles conflicts manually (not via 7zip flags):
- **Single file archives**: Extract to temp folder (`gunzip_<hash>`), move file with unique name (`file-1.pdf`, `file-2.pdf`, etc.), delete temp folder
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
Stored in JSON format at `~/.gunzip/preferences.json`:
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

#### Test Organization
- **Unit tests** for domain layer in `src/commonTest/kotlin/gunzip/domain/`
- **ViewModel tests** in `src/commonTest/kotlin/gunzip/presentation/viewmodels/`
- **Mock implementations** for isolating units under test
- **Flow testing** using Turbine library for reactive streams

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

**Phase**: Windows Feature-Complete
**Progress**: Core architecture complete, Windows platform feature-complete with GUI and settings, Mosaic TUI fully functional

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
  - Interactive TUI with real-time progress updates
  - Progress bars, colors, emojis for visual feedback
  - Extraction progress display (stages, files, bytes)
  - Settings/file associations display
  - Auto-detection of terminal launch vs GUI launch
  - `--tui` and `--gui` flags for manual override
- **Black Box E2E Test Scripts**
  - Windows, Linux, and macOS test scripts created
  - Test fixtures prepared (single-file.zip, multiple-files.zip, nested-folder.zip)
  - TUI extraction tested and working
- **Windows Win32 Native GUI (100%)**
  - Native progress window during extraction (420x180px, modern Segoe UI styling)
  - Native settings window with preferences checkboxes
  - MessageBox dialogs for completion/error notifications
  - Cancel button for extraction operations
  - Embedded icon loading from executable resources
  - Auto-detects GUI vs TUI mode based on launch context
  - Seamless integration with MVVM architecture
- **Windows Installer (100%)**
  - Inno Setup script for Windows installer
  - Portable ZIP distribution support
  - Icon and resource compilation

### ⏳ Pending
- macOS Cocoa native GUI implementation (stubs exist)
- Linux GTK native GUI implementation (stubs exist)
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