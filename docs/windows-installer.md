# Windows Installer Build Guide

This guide explains how to build the Windows installer for Qunzip.

## Overview

Qunzip provides two distribution formats for Windows:

1. **Windows Installer** (`qunzip-setup-{version}.exe`) - Full installation with file associations
2. **Portable ZIP** (`qunzip-{version}-windows-portable.zip`) - No installation required

## Prerequisites

### Required Software

1. **Windows Operating System**
   - Windows 10 or Windows 11
   - 64-bit architecture

2. **Java Development Kit (JDK)**
   - JDK 11 or later (for Gradle)
   - Set `JAVA_HOME` environment variable

3. **Inno Setup 6.x** (for installer only)
   - Download from: https://jrsoftware.org/isdl.php
   - Default installation path: `C:\Program Files (x86)\Inno Setup 6\`
   - Alternative: Set `ISCC_PATH` environment variable to custom location

4. **Kotlin Native Toolchain**
   - Automatically downloaded by Gradle
   - MinGW-w64 for Windows compilation

## Building the Installer

### Quick Start

```bash
# Build both installer and portable ZIP
./gradlew packageWindows
```

This single command will:
1. Compile Kotlin code to native Windows executable
2. Copy 7-Zip dependencies
3. Prepare installer resources
4. Build Inno Setup installer
5. Create portable ZIP archive

### Step-by-Step Build

#### 1. Build Release Executable

```bash
./gradlew linkReleaseExecutableMingwX64
```

Output: `build/bin/mingwX64/releaseExecutable/qunzip.exe`

#### 2. Prepare Installer Resources

```bash
./gradlew prepareInstallerResources
```

This copies:
- `qunzip.exe` - Main executable
- `7z.exe` + `7z.dll` - 7-Zip tools
- `License.txt` - Combined license

Output directory: `build/installer-staging/windows/`

#### 3. Build Installer

```bash
./gradlew buildWindowsInstaller
```

Output: `build/installer-output/qunzip-setup-{version}.exe`

**Note:** This step requires Inno Setup 6 to be installed.

#### 4. Create Portable ZIP (Optional)

```bash
./gradlew createPortableZip
```

Output: `build/dist/qunzip-{version}-windows-portable.zip`

## Icon

The installer uses `installer/windows/icon.ico` for branding. This icon is included in the repository.

## Installer Configuration

### Version Management

Version is defined in `build.gradle.kts`:

```kotlin
version = "1.0.0"
```

This version is:
- Embedded in the executable
- Used in installer filename (`qunzip-setup-1.0.0.exe`)
- Displayed in Add/Remove Programs
- Passed to Inno Setup via `QUNZIP_VERSION` environment variable

### Customizing Inno Setup Script

Edit `installer/windows/qunzip.iss`:

```ini
#define MyAppPublisher "Your Company Name"
#define MyAppURL "https://your-website.com"
```

### File Associations

The installer registers these formats by default:

- ZIP: `.zip`
- 7-Zip: `.7z`
- RAR: `.rar`
- TAR: `.tar`, `.tar.gz`, `.tar.bz2`, `.tar.xz`, `.tgz`, `.tbz2`, `.txz`
- Other: `.cab`, `.arj`, `.lzh`

To add/remove formats, edit the `[Registry]` section in `qunzip.iss`.

## Troubleshooting

### Error: "ISCC.exe not found"

**Problem:** Inno Setup not installed or not in default location.

**Solutions:**
1. Install Inno Setup 6 from https://jrsoftware.org/isdl.php
2. Or set environment variable:
   ```powershell
   $env:ISCC_PATH = "C:\Your\Path\To\ISCC.exe"
   ./gradlew buildWindowsInstaller
   ```

### Error: "Task 'buildWindowsInstaller' is skipped"

**Problem:** Not running on Windows OS.

**Solution:** The installer can only be built on Windows. Use a Windows machine or VM.

### Error: "Icon file not found"

**Problem:** `icon.ico` doesn't exist.

**Solution:** Ensure `installer/windows/icon.ico` exists. The icon should be included in the repository.

### Build Fails with "Access Denied"

**Problem:** Antivirus or file permissions.

**Solutions:**
1. Temporarily disable antivirus
2. Run PowerShell as Administrator
3. Check file permissions on build directories

## Testing the Installer

### Basic Installation Test

1. Build installer: `./gradlew buildWindowsInstaller`
2. Locate: `build/installer-output/qunzip-setup-{version}.exe`
3. Run installer on clean Windows VM (recommended) or local machine
4. Follow installation wizard
5. Check installation:
   - Files at `C:\Program Files\Qunzip\`
   - Start Menu shortcut
   - Add/Remove Programs entry

### File Association Test

1. Download test archives (.zip, .7z, .rar)
2. Double-click an archive
3. Verify:
   - Qunzip opens automatically
   - Archive is extracted
   - Original moved to Recycle Bin
4. Check "Open With" context menu shows Qunzip

### Uninstallation Test

1. Uninstall via "Add or Remove Programs"
2. Verify:
   - All files removed from `C:\Program Files\Qunzip\`
   - File associations removed
   - Start Menu shortcuts removed
   - Registry entries cleaned up

### Portable ZIP Test

1. Build: `./gradlew createPortableZip`
2. Extract ZIP to test folder (e.g., `C:\Temp\qunzip-test\`)
3. Run `qunzip.exe <archive-file>` from command line
4. Verify extraction works without installation

## Advanced Configuration

### Custom Inno Setup Compiler Path

```bash
# Windows PowerShell
$env:ISCC_PATH = "D:\Tools\InnoSetup6\ISCC.exe"
./gradlew buildWindowsInstaller
```

### Silent Installation (for deployment)

```powershell
# Install silently with all defaults
qunzip-setup-1.0.0.exe /VERYSILENT /SUPPRESSMSGBOXES

# Install with file associations
qunzip-setup-1.0.0.exe /VERYSILENT /TASKS="fileassoc"

# Install without file associations
qunzip-setup-1.0.0.exe /VERYSILENT /TASKS="!fileassoc"
```

### Build for Distribution

```bash
# Clean previous builds
./gradlew cleanInstaller

# Build release executables
./gradlew buildAllRelease

# Package Windows distributions
./gradlew packageWindows

# Outputs:
# - build/installer-output/qunzip-setup-1.0.0.exe
# - build/dist/qunzip-1.0.0-windows-portable.zip
```

### Code Signing (Future)

For production releases, sign the installer:

```powershell
# Using SignTool (Windows SDK)
signtool sign /f certificate.pfx /p password /t http://timestamp.digicert.com qunzip-setup-1.0.0.exe

# Verify signature
signtool verify /pa qunzip-setup-1.0.0.exe
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Build Windows Installer

on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: windows-latest

    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'

      - name: Install Inno Setup
        run: |
          choco install innosetup -y

      - name: Build Installer
        run: |
          ./gradlew packageWindows

      - name: Upload Installer
        uses: actions/upload-artifact@v3
        with:
          name: windows-installer
          path: build/installer-output/*.exe

      - name: Upload Portable ZIP
        uses: actions/upload-artifact@v3
        with:
          name: windows-portable
          path: build/dist/*.zip
```

## Distribution Checklist

Before releasing a new version:

- [ ] Update version in `build.gradle.kts`
- [ ] Update CHANGELOG (if exists)
- [ ] Test on clean Windows 10 VM
- [ ] Test on clean Windows 11 VM
- [ ] Verify all file associations work
- [ ] Test uninstallation
- [ ] Test portable ZIP
- [ ] Create release notes
- [ ] Sign installer (if applicable)
- [ ] Upload to release platform (GitHub Releases, website, etc.)

## Gradle Task Reference

| Task | Description | Output |
|------|-------------|--------|
| `prepareInstallerResources` | Copy files to staging | `build/installer-staging/windows/` |
| `buildWindowsInstaller` | Compile Inno Setup script | `build/installer-output/*.exe` |
| `createPortableZip` | Create portable distribution | `build/dist/*.zip` |
| `packageWindows` | Build both installer and ZIP | Both outputs |
| `cleanInstaller` | Clean installer artifacts | Removes `build/installer-*` and `build/dist` |

## Support

For issues or questions:

- GitHub Issues: https://github.com/yourusername/qunzip/issues
- Documentation: `docs/`
- Inno Setup Help: https://jrsoftware.org/ishelp/

## References

- [Inno Setup Documentation](https://jrsoftware.org/isinfo.php)
- [Kotlin Native Windows](https://kotlinlang.org/docs/native-target-support.html)
- [Gradle Build Tool](https://gradle.org/)
- [Windows Registry for File Associations](https://docs.microsoft.com/en-us/windows/win32/shell/fa-file-types)
