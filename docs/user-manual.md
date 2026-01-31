# User Manual

> **⚠️ Note**: This manual describes the functionality of Qunzip. The project is currently under active development. **Windows is feature-complete** with both native Win32 GUI and Terminal UI (TUI). Linux and macOS platform implementations are pending. See [development-progress.md](development-progress.md) for current status.

## Overview

**Qunzip** is a cross-platform archive extraction utility that provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives. Inspired by macOS simplicity, it works intelligently in the background to extract your files exactly where you expect them.

## Installation

### Windows

**Current Status**: Build from source required. Pre-built installers coming soon.

1. Build the executable:
   ```bash
   ./gradlew linkReleaseExecutableMingwX64
   ```
2. Executable location: `build/bin/mingwX64/releaseExecutable/qunzip.exe`
3. (Optional) Build installer:
   ```bash
   ./gradlew packageWindows  # Requires Inno Setup 6
   ```
4. For build requirements, see [windows-installer.md](windows-installer.md)

### macOS

**Status**: Implementation pending. Not yet available.

### Linux

**Status**: Implementation pending. Not yet available.

## Basic Usage

### Two User Interface Modes

Qunzip provides two user interfaces:

1. **Terminal UI (TUI)** - Interactive terminal interface with live progress updates
2. **Native GUI** - Platform-specific graphical dialogs (Windows implemented, macOS/Linux planned)

The application automatically chooses the appropriate interface:
- **Double-clicking** an archive in File Explorer → Native GUI mode (Windows)
- **Running from terminal** → Terminal UI with interactive progress display

You can also force a specific mode:
- `qunzip --tui archive.zip` - Force terminal UI
- `qunzip --gui archive.zip` - Force GUI mode

### Extracting Archives (Terminal UI)

Run from your terminal:
```bash
qunzip archive.zip
```

You'll see an interactive progress display:
```
┌─── Qunzip Archive Extractor ───┐
│
│ Archive: my-files.zip
│
│ [████████████████████░░░░░░░] 67.3%
│ 45/67 files  •  128 MB
│
│ 📦 Extracting files...
└────────────────────────────────┘
```

The Terminal UI shows:
- Real-time progress bar with percentage
- File count and data statistics
- Current extraction stage with visual indicators:
  - 🔍 Analyzing archive...
  - 📦 Extracting files...
  - ✨ Finalizing...
  - ✅ Complete!

### Extracting Archives (GUI Mode)

**Simply double-click any supported archive file.**

The application will:
1. Analyze the archive contents
2. Extract files intelligently:
   - **Single file**: Extracts directly to the same folder
   - **Multiple files**: Creates a new folder named after the archive
3. Optionally move the original archive to trash (if enabled in settings)
4. Optionally show a completion dialog (if enabled in settings)

### Example Scenarios

#### Single File Archive
```
Before: /Documents/report.zip (contains report.pdf)
After:  /Documents/report.pdf
        (archive moved to Trash if "move to trash" setting enabled)
```

#### Multiple File Archive
```
Before: /Downloads/project.zip (contains src/, docs/, README.md)
After:  /Downloads/project/src/
        /Downloads/project/docs/
        /Downloads/project/README.md
        (archive moved to Trash if "move to trash" setting enabled)
```

## Supported Formats

### Fully Supported
- **ZIP** (.zip) - Most common archive format
- **7-Zip** (.7z) - High compression format
- **TAR** (.tar, .tar.gz, .tar.bz2, .tar.xz) - Unix archive format
- **RAR** (.rar) - Popular compression format

### Additional Formats
- CAB (.cab) - Windows cabinet files
- ARJ (.arj) - Legacy archive format
- LZH (.lzh) - Japanese archive format

## Advanced Features

### Large File Handling
For archives larger than 10MB, you'll see a progress notification showing:
- Current progress percentage
- Data transferred vs. total size
- Estimated time remaining
- Cancel option

### Error Recovery
If extraction fails:
- The original archive remains untouched
- An error notification explains what went wrong
- No partial files are left behind

### Smart Conflict Resolution
If extracted files would overwrite existing files or folders:
- Files are automatically renamed with a number suffix
- Example: `document.pdf` becomes `document-1.pdf`, then `document-2.pdf`, etc.
- Folders follow the same pattern: `project` becomes `project-1`, `project-2`, etc.

## Settings and Configuration

### Viewing and Configuring Settings

Run without any archive file to open the settings window:
```bash
qunzip
```

**Windows**: Opens native settings window with checkboxes for preferences.

**Terminal**: Shows settings in TUI format:
```
┌─────── Qunzip Settings ───────┐
│
│ File Associations
│
│ Status: ⭕ Not Registered
│
│ Supported formats:
│   · .zip
│   · .7z
│   · .rar
│   · .tar
│   · .tar.gz
│   · .tar.bz2
│   · .tar.xz
│
│ Commands:
│   --register-associations
│   --unregister-associations
└───────────────────────────────┘
```

### Configuring Preferences via Command Line

```bash
# Enable moving archives to trash after extraction
qunzip --set-trash-on

# Disable moving archives to trash (default)
qunzip --set-trash-off

# Enable completion dialog after extraction
qunzip --set-dialog-on

# Disable completion dialog - silent exit (default)
qunzip --set-dialog-off
```

Preferences are stored in `~/.qunzip/preferences.json`.

### File Associations
The application automatically registers itself for supported file types during installation. To manually manage associations:

**Command Line**:
```bash
qunzip --register-associations    # Register all supported formats
qunzip --unregister-associations  # Unregister all formats
```

**Manual Configuration**:
- **Windows**: Right-click archive → "Open with" → "Choose another app" → Select Qunzip
- **macOS**: Right-click archive → "Get Info" → "Open with" → Select Qunzip → "Change All"
- **Linux**: Depends on desktop environment (usually in file manager preferences)

### Notification Preferences
Notifications follow your system preferences:
- **Windows**: Control Panel → System → Notifications
- **macOS**: System Preferences → Notifications → Qunzip
- **Linux**: Desktop environment notification settings

## Troubleshooting

### Common Issues

#### "Permission Denied" Error
**Solution**:
- Windows: Run as Administrator or move archive to a writable location
- macOS: Grant Full Disk Access in Security & Privacy preferences
- Linux: Check file permissions with `ls -la`

#### Archive Won't Extract
**Possible causes**:
- Corrupted archive file
- Unsupported archive format
- Password-protected archive (not yet supported)

**Solutions**:
- Try extracting with another tool to verify the archive
- Check if the file extension matches the actual format
- Ensure the archive isn't password-protected

#### No Progress Notification
**Normal behavior** for archives under 10MB. Large archives will show progress automatically.

#### Original Archive Not Moved to Trash
This happens when:
- The "move to trash" setting is disabled (default behavior)
- Extraction failed (archive remains for retry)
- Insufficient permissions to delete
- Archive is on read-only media (CD/DVD)

### Getting Help

> **Note**: Logging features are implemented but not yet tested in the current development build.

#### Log Files
Application logs are stored at:
- **Windows**: `%APPDATA%\Qunzip\logs/` _(planned)_
- **macOS**: `~/Library/Logs/Qunzip/` _(pending implementation)_
- **Linux**: `~/.local/share/qunzip/logs/` _(pending implementation)_

#### Reporting Issues
For development feedback and bug reports, see the project's documentation:
- [Development Progress](development-progress.md)
- [Architecture Documentation](architecture.md)

## Keyboard Shortcuts

### During Extraction
- **Escape**: Cancel ongoing extraction (for large files)

### In Error Dialogs
- **Enter**: Dismiss dialog
- **Tab**: Navigate between buttons

## Privacy and Security

### Data Handling
- No data is sent to external servers
- Archives are processed entirely on your local machine
- No usage analytics or telemetry collected

### Security Considerations
- Archives are extracted to predictable locations
- Malicious archives with directory traversal attacks are blocked
- Extracted files maintain original permissions (Unix systems)

## Performance Tips

### Optimal Performance
- Extract archives to fast storage (SSD preferred)
- Ensure sufficient free space (at least 2x archive size)
- Close other disk-intensive applications during large extractions

### Batch Processing
To extract multiple archives:
1. Select all archive files
2. Press Enter (or double-click the last selected file)
3. Each archive will be processed sequentially