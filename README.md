# Quick Unzip (qunzip) - Cross-Platform Archive Extraction Utility

<div align="center">

A fast, simple archive extraction utility inspired by macOS simplicity but built for Windows, Linux, and macOS.

**Just double-click any archive to extract it.**

> **⚠️ Development Status**: This project is under active development. Windows platform implementation is complete but not yet fully tested. Linux and macOS implementations are pending. See [development-progress.md](docs/development-progress.md) for current status.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-purple.svg)](https://kotlinlang.org/)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux%20%7C%20macOS-lightgrey.svg)

</div>

## Features

✨ **Smart Extraction**
- Single file → Extracts to same directory
- Multiple files → Creates new folder with archive name
- Single root folder → Extracts contents directly (no nested folders)
- Optional cleanup → Move archive to Recycle Bin/Trash after extraction

📦 **Wide Format Support**
- ZIP, 7-Zip, RAR, TAR (+ .gz, .bz2, .xz variants)
- Cabinet (.cab), ARJ (.arj), LZH (.lzh)

🖱️ **Simple to Use**
- Double-click any archive file
- File associations via installer or CLI registration
- Minimal configuration - just works

⚡ **Fast & Lightweight**
- Native executables (no JVM required)
- Powered by 7-Zip for fast decompression
- Minimal system resource usage

🏗️ **Clean Architecture**
- MVVM pattern with Kotlin Flow
- TDD with comprehensive test coverage
- Platform-specific implementations for optimal UX

## Installation

> **Note**: Pre-built releases are not yet available. To use Quick Unzip, you must build from source (see "Building from Source" section below).

### Windows (Build from Source)

Windows ships two binaries that share all source code:

| Binary | Subsystem | Used for |
|--------|-----------|----------|
| `qunzip.exe`     | Console | CLI (terminal use, `--register-associations`, tests) |
| `QuickUnzip.exe` | Windows GUI | File-association double-click and drag-drop (no console flash) |

```bash
# Build both debug binaries
./gradlew linkCliDebugExecutableMingwX64 linkGuiDebugExecutableMingwX64

# Or build via Make (handles both)
make build
```

Output:
- `build/bin/mingwX64/cliDebugExecutable/qunzip.exe`
- `build/bin/mingwX64/guiDebugExecutable/QuickUnzip.exe`

For a release build + installer, run `./gradlew packageWindows` (requires Inno Setup 6).

### macOS

Coming soon! Implementation pending.

### Linux

Coming soon! Implementation pending.

## Usage

### Automatic Extraction (Recommended)

Simply **double-click** any supported archive file. Quick Unzip will:
1. Extract the contents intelligently
2. Optionally move the original archive to trash (if enabled in settings)

### Command Line

```bash
# Extract an archive — renders the Mosaic TUI in the terminal
qunzip path/to/archive.zip

# Open settings TUI (run without arguments)
qunzip

# Configure preferences
qunzip --set-trash-on       # Move archives to trash after extraction
qunzip --set-trash-off      # Keep archives after extraction (default)
qunzip --set-dialog-on      # Keep window open after extraction
qunzip --set-dialog-off     # Close window automatically (default)

# Register file associations (Windows, requires admin)
qunzip --register-associations

# Unregister file associations
qunzip --unregister-associations

# Show help / version
qunzip --help
qunzip --version
```

## Supported Archive Formats

| Format | Extensions | Compression |
|--------|-----------|-------------|
| **ZIP** | `.zip` | DEFLATE |
| **7-Zip** | `.7z` | LZMA, LZMA2 |
| **RAR** | `.rar` | RAR |
| **TAR** | `.tar` | None (container) |
| **TAR+GZIP** | `.tar.gz`, `.tgz` | GZIP |
| **TAR+BZIP2** | `.tar.bz2`, `.tbz2` | BZIP2 |
| **TAR+XZ** | `.tar.xz`, `.txz` | XZ/LZMA2 |
| **Cabinet** | `.cab` | MSZIP, LZX |
| **ARJ** | `.arj` | ARJ |
| **LZH** | `.lzh` | LH |

## Building from Source

### Prerequisites

- **JDK 11+** (for Gradle)
- **Kotlin Native toolchain** (auto-downloaded by Gradle)
- **Windows only:**
  - Inno Setup 6.x (for installer builds)

### Build Commands

```bash
# Build release executables for Windows (both binaries)
./gradlew linkCliReleaseExecutableMingwX64 linkGuiReleaseExecutableMingwX64

# Build for all platforms
./gradlew buildAllRelease

# Run tests
./gradlew testAll

# Build Windows installer (Windows only, requires Inno Setup)
./gradlew packageWindows

# Build portable ZIP
./gradlew createPortableZip
```

**Output locations:**
- Windows CLI: `build/bin/mingwX64/cliReleaseExecutable/qunzip.exe`
- Windows GUI: `build/bin/mingwX64/guiReleaseExecutable/QuickUnzip.exe`
- Linux/macOS:  `build/bin/{platform}/releaseExecutable/`
- Windows installer: `build/installer-output/quick-unzip-setup-{version}.exe`
- Portable ZIP: `build/dist/quick-unzip-{version}-windows-portable.zip`

See [docs/windows-installer.md](docs/windows-installer.md) for detailed build instructions.

## Project Structure

```
src/
├── commonMain/kotlin/qunzip/
│   ├── domain/              # Business logic (platform-agnostic)
│   │   ├── entities/        # Core models
│   │   ├── usecases/        # Business operations
│   │   └── repositories/    # Repository interfaces
│   ├── presentation/        # ViewModels and UI state
│   └── main.kt             # Application entry point
│
├── mingwX64Main/kotlin/     # Windows-specific implementations
├── linuxX64Main/kotlin/     # Linux-specific implementations
└── macosX64Main/kotlin/     # macOS-specific implementations

installer/windows/           # Windows installer configuration
├── qunzip.iss              # Inno Setup script
├── LICENSE.txt             # Combined license
└── README.txt              # Post-install readme

docs/                       # Documentation
└── windows-installer.md    # Installer build guide
```

## Architecture

Quick Unzip follows **Clean Architecture** principles:

- **Domain Layer**: Platform-agnostic business logic
- **Data Layer**: Platform-specific repository implementations
- **Presentation Layer**: MVVM with Kotlin Flow

**Key design patterns:**
- MVVM (Model-View-ViewModel)
- Repository Pattern
- Use Cases (Interactors)
- Dependency Injection
- Test-Driven Development (TDD)

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## Development

### Running Tests

```bash
# Run entire test pyramid (unit + integration + e2e)
./gradlew testAll

# Run all Windows tests
./gradlew mingwX64Test

# Run selectively by layer
./gradlew mingwX64Test --tests "qunzip.domain.*"        # Unit tests
./gradlew mingwX64Test --tests "qunzip.presentation.*"  # ViewModel tests
./gradlew mingwX64Test --tests "qunzip.integration.*"    # Integration tests
./gradlew mingwX64Test --tests "qunzip.e2e.*"            # E2E tests
```

### Code Structure

- **Clean Architecture**: Domain, Data, Presentation layers
- **MVVM Pattern**: ViewModels with Kotlin Flow
- **TDD**: Full test pyramid (unit → integration → E2E) with Turbine for Flow testing
- **Platform Abstraction**: Expect/actual for platform-specific code

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Roadmap

### Completed
- [x] Core architecture with Clean Architecture + MVVM
- [x] Windows platform implementation (feature-complete)
- [x] Native Win32 GUI with progress window
- [x] Mosaic TUI for terminal usage
- [x] Windows installer with Inno Setup
- [x] User preferences (trash, completion dialog)
- [x] Smart duplicate file/folder handling
- [x] Black box E2E test framework

### In Progress
- [ ] Comprehensive Windows testing and validation
- [ ] Linux platform implementation
- [ ] macOS platform implementation

### Future Enhancements
- [ ] Advanced file association management (Windows Registry)
- [ ] Drag-and-drop support
- [ ] Multi-archive batch extraction
- [ ] Custom extraction location selection

## License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

**Bundled Components:**
- **7-Zip** (7z.exe, 7z.dll) - GNU LGPL 2.1 - Copyright © 1999-2024 Igor Pavlov
- See [installer/windows/LICENSE.txt](installer/windows/LICENSE.txt) for full license information

## Acknowledgments

- **7-Zip** by Igor Pavlov - Powerful compression library
- **Kotlin Multiplatform** - Cross-platform development framework
- **Kotlin/Native** - Native executable compilation
- **Inno Setup** - Windows installer framework

## Support

- **Documentation**: [docs/](docs/)
- **Development Progress**: [docs/development-progress.md](docs/development-progress.md)
- **Build Guide**: [docs/windows-installer.md](docs/windows-installer.md)

---

<div align="center">

**Made with ❤️ using Kotlin Multiplatform**

</div>
