# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Gunzip** is a cross-platform archive extraction utility built with Kotlin Multiplatform. It provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives, inspired by macOS simplicity but targeting primarily Windows with Linux and macOS support.

The application follows **Clean Architecture** principles with **MVVM pattern**, uses **Kotlin Flow** for reactive programming, and implements **TDD** practices.

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
- Build and run directly: `./build/bin/<platform>/debugExecutable/gunzip.kexe <archive-file>`
- The application is designed to be invoked by double-clicking archive files

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
- **Entities**: `Archive`, `ArchiveEntry`, `ExtractionResult`, `FileAssociation`
- **Use Cases**: `ExtractArchiveUseCase`, `ValidateArchiveUseCase`, `ManageFileAssociationsUseCase`
- **Repository Interfaces**: Abstract contracts for platform-specific implementations

#### Data Layer (Platform-specific `src/<platform>Main/kotlin/`)
- **Repository Implementations**: Platform-specific data access
- **7zip Integration**: Archive extraction via 7zip executable/library
- **OS Services**: File system operations, notifications, file associations

#### Presentation Layer (`src/commonMain/kotlin/gunzip/presentation/`)
- **ViewModels**: `ExtractionViewModel`, `FileAssociationViewModel`, `ApplicationViewModel`
- **State Management**: Kotlin Flow with StateFlow/SharedFlow patterns

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
- **Kotlinx Coroutines**: `1.8.0` - Async operations
- **Kotlinx Serialization**: `1.6.3` - Data serialization
- **Kotlinx DateTime**: `0.5.0` - Date/time handling
- **Kermit**: `2.0.3` - Multiplatform logging
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
│   │   └── viewmodels/        # State management
│   └── main.kt               # Application entry point
├── commonTest/kotlin/gunzip/  # Shared tests
├── windowsMain/kotlin/        # Windows-specific implementations
├── linuxMain/kotlin/          # Linux-specific implementations
└── macosMain/kotlin/          # macOS-specific implementations
```

## Core Functionality

### Archive Extraction Logic
1. **Single file archive** → Extract to same directory
2. **Multiple files** → Create directory with archive name
3. **Single root directory** → Extract contents to same directory
4. **Post-extraction** → Move original archive to trash

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

## Development Notes

### Testing Strategy
- **TDD approach** with comprehensive test coverage
- **Unit tests** for domain layer (entities, use cases)
- **Integration tests** for repository implementations
- **Mock implementations** for testing ViewModels
- **Flow testing** using Turbine library

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

**Phase**: Sprint 1 - Foundation Architecture
**Progress**: Core architecture complete, platform implementations pending

### ✅ Completed
- Clean Architecture with MVVM setup (100%)
- Domain layer entities and use cases (100%)
- Repository interfaces defined (100%)
- ViewModels with Kotlin Flow (100%)
- TDD framework with 40 passing tests (100%)
- Build configuration for all platforms (100%)

### 🔄 In Progress
- Platform-specific repository implementations (0%)
- 7zip integration (pending)
- File association registration (pending)

### ⏳ Pending
- Windows platform implementation
- Linux platform implementation
- macOS platform implementation
- End-to-end integration testing

See `docs/development-progress.md` for detailed status and next steps.

## Documentation

Comprehensive documentation available in `/docs/`:
- `architecture.md` - Detailed architecture overview
- `ux-design.md` - User experience design principles
- `user-manual.md` - End-user documentation
- `project-management.md` - Agile development process
- `development-progress.md` - Current development status and progress tracking