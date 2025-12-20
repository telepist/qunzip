# User Manual

## Overview

**Gunzip** is a cross-platform archive extraction utility that provides seamless, double-click extraction for ZIP, 7Z, RAR, and TAR archives. Inspired by macOS simplicity, it works intelligently in the background to extract your files exactly where you expect them.

## Installation

### Windows
1. Download `gunzip-windows.exe` from releases
2. Run the installer
3. Archive file associations will be configured automatically

### macOS
1. Download `gunzip-macos.dmg` from releases
2. Drag Gunzip to Applications folder
3. Grant necessary permissions when prompted

### Linux
1. Download `gunzip-linux.tar.gz` from releases
2. Extract and run: `sudo ./install.sh`
3. File associations will be configured for your desktop environment

## Basic Usage

### Extracting Archives

**Simply double-click any supported archive file.** That's it!

The application will:
1. Analyze the archive contents
2. Extract files intelligently:
   - **Single file**: Extracts directly to the same folder
   - **Multiple files**: Creates a new folder named after the archive
3. Move the original archive to trash
4. Show a success notification

### Example Scenarios

#### Single File Archive
```
Before: /Documents/report.zip (contains report.pdf)
After:  /Documents/report.pdf
        /Trash/report.zip
```

#### Multiple File Archive
```
Before: /Downloads/project.zip (contains src/, docs/, README.md)
After:  /Downloads/project/src/
        /Downloads/project/docs/
        /Downloads/project/README.md
        /Trash/project.zip
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
If extracted files would overwrite existing files:
- Existing files are automatically renamed with a number suffix
- Example: `document.pdf` becomes `document (1).pdf`

## Settings and Configuration

### File Associations
The application automatically registers itself for supported file types during installation. To manually manage associations:

**Windows**: Right-click archive → "Open with" → "Choose another app" → Select Gunzip

**macOS**: Right-click archive → "Get Info" → "Open with" → Select Gunzip → "Change All"

**Linux**: Depends on desktop environment (usually in file manager preferences)

### Notification Preferences
Notifications follow your system preferences:
- **Windows**: Control Panel → System → Notifications
- **macOS**: System Preferences → Notifications → Gunzip
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
- Extraction failed (archive remains for retry)
- Insufficient permissions to delete
- Archive is on read-only media (CD/DVD)

### Getting Help

#### Log Files
Application logs are stored at:
- **Windows**: `%APPDATA%\Gunzip\logs\`
- **macOS**: `~/Library/Logs/Gunzip/`
- **Linux**: `~/.local/share/gunzip/logs/`

#### Reporting Issues
1. Check the log files for error details
2. Note your operating system and version
3. Include the archive format and approximate size
4. Report at: [GitHub Issues](https://github.com/your-repo/gunzip/issues)

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