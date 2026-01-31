# Makefile for Qunzip - Cross-platform Archive Extraction Utility
# Automatically detects current platform and builds accordingly

.PHONY: help build run clean build-all build-release run-release test \
	prepare-installer build-windows-installer create-portable-zip \
	package-windows clean-installer info

# Detect current OS and architecture
UNAME_S := $(shell uname -s 2>/dev/null || echo Unknown)
UNAME_M := $(shell uname -m 2>/dev/null || echo Unknown)

# Determine platform-specific settings
ifeq ($(UNAME_S),Darwin)
    # macOS
    ifeq ($(UNAME_M),arm64)
        PLATFORM := MacosArm64
        PLATFORM_LOWER := macosArm64
        EXECUTABLE := qunzip.kexe
    else
        PLATFORM := MacosX64
        PLATFORM_LOWER := macosX64
        EXECUTABLE := qunzip.kexe
    endif
else ifeq ($(UNAME_S),Linux)
    # Linux
    ifeq ($(UNAME_M),aarch64)
        PLATFORM := LinuxArm64
        PLATFORM_LOWER := linuxArm64
        EXECUTABLE := qunzip.kexe
    else
        PLATFORM := LinuxX64
        PLATFORM_LOWER := linuxX64
        EXECUTABLE := qunzip.kexe
    endif
else ifneq (,$(findstring MINGW,$(UNAME_S)))
    # Windows (Git Bash/MINGW)
    PLATFORM := MingwX64
    PLATFORM_LOWER := mingwX64
    EXECUTABLE := qunzip.exe
else ifneq (,$(findstring MSYS,$(UNAME_S)))
    # Windows (MSYS)
    PLATFORM := MingwX64
    PLATFORM_LOWER := mingwX64
    EXECUTABLE := qunzip.exe
else ifneq (,$(findstring CYGWIN,$(UNAME_S)))
    # Windows (Cygwin)
    PLATFORM := MingwX64
    PLATFORM_LOWER := mingwX64
    EXECUTABLE := qunzip.exe
else
    # Fallback for Windows when uname is not available
    ifeq ($(OS),Windows_NT)
        PLATFORM := MingwX64
        PLATFORM_LOWER := mingwX64
        EXECUTABLE := qunzip.exe
    else
        $(error Unsupported platform: $(UNAME_S))
    endif
endif

# Build directories
BUILD_DIR := build/bin/$(PLATFORM_LOWER)/debugExecutable
RELEASE_BUILD_DIR := build/bin/$(PLATFORM_LOWER)/releaseExecutable
BUILD_PATH := $(BUILD_DIR)/$(EXECUTABLE)
RELEASE_BUILD_PATH := $(RELEASE_BUILD_DIR)/$(EXECUTABLE)
INSTALLER_STAGE_DIR := build/installer-staging/windows
INSTALLER_OUTPUT_DIR := build/installer-output
PORTABLE_ZIP_DIR := build/dist

# Gradle wrapper - use bash explicitly on Windows for cmd.exe compatibility
ifeq ($(PLATFORM),MingwX64)
    GRADLEW := bash ./gradlew
else
    GRADLEW := ./gradlew
endif

# Default target - show help
help:
	@echo "=================================="
	@echo "Qunzip Build System"
	@echo "=================================="
	@echo ""
	@echo "Detected Platform: $(PLATFORM) ($(UNAME_S) $(UNAME_M))"
	@echo "Build Target: $(EXECUTABLE)"
	@echo ""
	@echo "Available targets:"
	@echo "  make build         - Build debug executable for current platform"
	@echo "  make run           - Build and run qunzip (debug)"
	@echo "  make test          - Run unit tests"
	@echo "  make build-all     - Build debug executables for all platforms"
	@echo "  make build-release - Build release executable for current platform"
	@echo "  make run-release   - Build and run release version"
	@echo "  make clean         - Clean build artifacts"
	@echo "  make info          - Show detected platform information"
	@echo ""
	@echo "Platform-specific builds:"
	@echo "  make build-windows - Build for Windows x64"
	@echo "  make build-linux   - Build for Linux x64"
	@echo "  make build-macos   - Build for macOS ARM64"
	@echo ""
	@echo "Windows installer & packaging:"
	@echo "  make prepare-installer      - Stage files for Windows installer/ZIP"
	@echo "  make build-windows-installer - Build Inno Setup installer (Windows + ISCC)"
	@echo "  make create-portable-zip    - Create portable Windows ZIP"
	@echo "  make package-windows        - Build installer and portable ZIP"
	@echo "  make clean-installer        - Remove installer artifacts"
	@echo ""

# Build debug executable for current platform
build:
	@echo "Building Qunzip for $(PLATFORM)..."
	$(GRADLEW) linkDebugExecutable$(PLATFORM)
	@echo ""
	@echo "Build complete: $(BUILD_PATH)"
	@echo ""

# Build and run the application
run: build
	@echo "Running Qunzip..."
	@echo ""
	$(BUILD_PATH)

# Run unit tests
test:
	@echo "Running tests..."
	$(GRADLEW) test
	@echo "Tests complete!"

# Build debug executables for all platforms
build-all:
	@echo "Building Qunzip for all platforms..."
	$(GRADLEW) buildAll
	@echo ""
	@echo "All builds complete!"
	@echo ""

# Build release executable for current platform
build-release:
	@echo "Building Qunzip RELEASE for $(PLATFORM)..."
	$(GRADLEW) linkReleaseExecutable$(PLATFORM)
	@echo ""
	@echo "Release build complete: $(RELEASE_BUILD_PATH)"
	@echo ""

# Build and run release version
run-release: build-release
	@echo "Running Qunzip (RELEASE)..."
	@echo ""
	$(RELEASE_BUILD_PATH)

# Platform-specific build targets
build-windows:
	@echo "Building Qunzip for Windows x64..."
	$(GRADLEW) linkDebugExecutableMingwX64
	@echo "Build complete: build/bin/mingwX64/debugExecutable/qunzip.exe"

build-linux:
	@echo "Building Qunzip for Linux x64..."
	$(GRADLEW) linkDebugExecutableLinuxX64
	@echo "Build complete: build/bin/linuxX64/debugExecutable/qunzip.kexe"

build-macos:
	@echo "Building Qunzip for macOS ARM64..."
	$(GRADLEW) linkDebugExecutableMacosArm64
	@echo "Build complete: build/bin/macosArm64/debugExecutable/qunzip.kexe"

# Clean build artifacts
clean:
	@echo "Cleaning build artifacts..."
	$(GRADLEW) clean
	@echo "Build artifacts cleaned!"

# Show current platform info
info:
	@echo "Platform Information:"
	@echo "  OS: $(UNAME_S)"
	@echo "  Architecture: $(UNAME_M)"
	@echo "  Target Platform: $(PLATFORM)"
	@echo "  Executable: $(EXECUTABLE)"
	@echo "  Build Directory: $(BUILD_DIR)"

# Windows installer/packaging helpers
prepare-installer:
	@echo "Preparing Windows installer resources..."
	$(GRADLEW) prepareInstallerResources
	@echo "Staged files at $(INSTALLER_STAGE_DIR)"

build-windows-installer:
	@echo "Building Windows installer (requires Inno Setup 6)..."
	$(GRADLEW) buildWindowsInstaller
	@echo "Installer output in $(INSTALLER_OUTPUT_DIR)"

create-portable-zip:
	@echo "Creating portable Windows ZIP..."
	$(GRADLEW) createPortableZip
	@echo "ZIP output in $(PORTABLE_ZIP_DIR)"

package-windows:
	@echo "Building Windows installer and portable ZIP..."
	$(GRADLEW) packageWindows
	@echo "Installer: $(INSTALLER_OUTPUT_DIR)"
	@echo "ZIP:       $(PORTABLE_ZIP_DIR)"

clean-installer:
	@echo "Cleaning Windows installer artifacts..."
	$(GRADLEW) cleanInstaller
	@echo "Installer artifacts cleaned!"
