# User Manual

> **Note**: This manual describes the functionality of Qunzip. The project is currently under active development. **Windows is functional** with Mosaic TUI. Linux and macOS platform implementations are pending. See [development-progress.md](development-progress.md) for current status.

## Overview

**Qunzip** is a cross-platform archive extraction utility that provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives. Inspired by macOS simplicity, it works intelligently to extract your files exactly where you expect them.

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

### Terminal UI

Qunzip uses a Mosaic-based terminal UI for all interactions. The same TUI renders in both terminal and double-click launch scenarios.

### Extracting Archives

Run from your terminal:
```bash
qunzip archive.zip
```

You'll see a progress display:
```
  ╭──────────────────────────────────────────────────╮
  │ qunzip                                           │
  ╰──────────────────────────────────────────────────╯

    Archive   my-files.zip
    Format    ZIP  ·  15.2 MB

    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  67%

    45 / 67 files  ·  10.2 MB
```

The TUI shows:
- Real-time progress bar with percentage
- Archive info (name, format, size)
- File count and data statistics
- Color-coded status:
  - Cyan progress bar during extraction
  - Green bar and "Done" badge on completion
  - Red bar and "Error" badge on failure

### Double-Click Extraction

**Simply double-click any supported archive file.**

The application will:
1. Open a console window with the TUI
2. Analyze the archive contents
3. Extract files intelligently:
   - **Single file**: Extracts directly to the same folder
   - **Multiple files**: Creates a new folder named after the archive
4. Optionally move the original archive to trash (if enabled in settings)
5. Exit when complete (or stay open if completion dialog is enabled)

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
- Cancel option

### Error Recovery
If extraction fails:
- The original archive remains untouched
- An error message is displayed in the TUI
- No partial files are left behind

### Smart Conflict Resolution
If extracted files would overwrite existing files or folders:
- Files are automatically renamed with a number suffix
- Example: `document.pdf` becomes `document-1.pdf`, then `document-2.pdf`, etc.
- Folders follow the same pattern: `project` becomes `project-1`, `project-2`, etc.

## Settings and Configuration

### Viewing Settings

Run without any archive file to see the settings display:
```bash
qunzip
```

Shows settings in TUI format with file association status, supported formats, current preferences, and available commands.

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

```bash
qunzip --register-associations    # Register all supported formats
qunzip --unregister-associations  # Unregister all formats
```

**Manual Configuration**:
- **Windows**: Right-click archive → "Open with" → "Choose another app" → Select Qunzip
- **macOS**: Right-click archive → "Get Info" → "Open with" → Select Qunzip → "Change All"
- **Linux**: Depends on desktop environment (usually in file manager preferences)

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

#### Original Archive Not Moved to Trash
This happens when:
- The "move to trash" setting is disabled (default behavior)
- Extraction failed (archive remains for retry)
- Insufficient permissions to delete
- Archive is on read-only media (CD/DVD)

### Getting Help

#### Reporting Issues
For development feedback and bug reports, see the project's documentation:
- [Development Progress](development-progress.md)
- [Architecture Documentation](architecture.md)

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
