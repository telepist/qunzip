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
- Auto-cleanup → Moves archive to Recycle Bin/Trash after extraction

📦 **Wide Format Support**
- ZIP, 7-Zip, RAR, TAR (+ .gz, .bz2, .xz variants)
- Cabinet (.cab), ARJ (.arj), LZH (.lzh)

🖱️ **Simple to Use**
- Double-click any archive file
- File associations automatically configured
- No complex options or settings

⚡ **Fast & Lightweight**
- Native executables (no JVM required)
- Powered by 7-Zip for fast decompression
- Minimal system resource usage

🏗️ **Clean Architecture**
- MVVM pattern with Kotlin Flow
- TDD with comprehensive test coverage
- Platform-specific implementations for optimal UX

## Installation

> **Note**: Pre-built releases are not yet available. To use Qunzip, you must build from source (see "Building from Source" section below).

### Windows (Build from Source)

1. Build the executable: `./gradlew linkReleaseExecutableMingwX64`
2. Executable location: `build/bin/mingwX64/releaseExecutable/qunzip.exe`
3. (Optional) Build installer: `./gradlew packageWindows` (requires Inno Setup 6)

### macOS

Coming soon! Implementation pending.

### Linux

Coming soon! Implementation pending.

## Usage

### Automatic Extraction (Recommended)

Simply **double-click** any supported archive file. Qunzip will:
1. Extract the contents intelligently
2. Open the extraction folder (optional)
3. Move the original archive to trash

### Command Line

```bash
# Extract an archive
qunzip path/to/archive.zip

# Register file associations (Windows, requires admin)
qunzip --register-associations

# Unregister file associations
qunzip --unregister-associations

# Show help
qunzip --help

# Show version
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
# Build release executable for Windows
./gradlew linkReleaseExecutableMingwX64

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
- Executables: `build/bin/{platform}/releaseExecutable/`
- Windows installer: `build/installer-output/qunzip-setup-{version}.exe`
- Portable ZIP: `build/dist/qunzip-{version}-windows-portable.zip`

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
├── README.txt              # Post-install readme
└── create-icon.ps1         # Icon generation script

docs/                       # Documentation
└── windows-installer.md    # Installer build guide
```

## Architecture

Qunzip follows **Clean Architecture** principles:

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
# Run all tests
./gradlew testAll

# Run common (platform-agnostic) tests
./gradlew test

# Run Windows-specific tests
./gradlew mingwX64Test
```

### Code Structure

- **Clean Architecture**: Domain, Data, Presentation layers
- **MVVM Pattern**: ViewModels with Kotlin Flow
- **TDD**: Unit tests with Turbine for Flow testing
- **Platform Abstraction**: Expect/actual for platform-specific code

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Roadmap

### Current Phase (In Progress)
- [x] Core architecture with Clean Architecture + MVVM
- [x] Windows platform repository implementations
- [x] Black box E2E test framework
- [ ] Fix build errors (missing platform implementations)
- [ ] Windows E2E testing and validation
- [x] Windows installer with file associations

### Next Phase
- [ ] Linux platform implementation
- [ ] macOS platform implementation
- [ ] Cross-platform testing

### Future Enhancements
- [ ] Real-time progress notifications during extraction
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
