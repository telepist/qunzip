# UX Design Document

## Design Philosophy

This application follows macOS-inspired design principles while maintaining cross-platform compatibility. The focus is on **invisible interaction** - users should be able to extract archives without thinking about the process.

## Core UX Principles

### 1. Zero-Configuration Extraction
- **Double-click to extract**: Primary interaction method
- **Smart extraction logic**: Automatically determines best extraction strategy
- **Minimal UI**: Clean TUI with only essential information

### 2. Intelligent Behavior
- **Single file archives**: Extract directly to same directory
- **Multi-file archives**: Create directory named after archive
- **Optional cleanup**: Move source archive to trash after extraction (user preference)

### 3. Seamless Integration
- **Native file associations**: Registers as default handler for supported formats
- **Trash integration**: Uses platform-appropriate trash/recycle bin
- **Terminal-native**: Works in any terminal emulator with ANSI color support

## User Interaction Flows

### Primary Flow: Double-Click Extraction

```
User double-clicks archive.zip
        ↓
Console window opens with TUI
        ↓
Analysis: archive.zip contains multiple files
        ↓
Creates directory: archive/
        ↓
Extracts all files to archive/
        ↓
Shows progress in TUI (progress bar, file count)
        ↓
Moves archive.zip to trash (if enabled)
        ↓
Application exits (or stays open if completion dialog enabled)
```

### Alternative Flow: CLI Extraction

```
User runs: qunzip document.zip
        ↓
TUI renders in existing terminal
        ↓
Analysis: document.zip contains single file
        ↓
Extracts document.pdf to same directory
        ↓
Moves document.zip to trash (if enabled)
        ↓
TUI exits, user returns to shell prompt
```

## TUI Design

### Extraction Screen

The extraction TUI uses a clean, modern design with box-drawing characters and color coding:

```
  ╭──────────────────────────────────────────────────╮
  │ qunzip                                           │
  ╰──────────────────────────────────────────────────╯

    Archive   my-files.zip
    Format    ZIP  ·  15.2 MB

    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━  67%

    45 / 67 files  ·  10.2 MB
```

Color scheme:
- **Cyan**: Header border, progress bar (in progress)
- **Green**: Progress bar (complete), "Done" badge
- **Red**: Progress bar (failed), "Error" badge
- **White**: Archive name, file statistics
- **Gray**: Labels, status text

### Settings Screen

Shows current configuration and available commands in the same TUI style.

## Supported Archive Formats

### Primary Formats (Auto-associated)
- ZIP (.zip)
- 7-Zip (.7z)
- RAR (.rar)
- TAR (.tar, .tar.gz, .tar.bz2)

### Secondary Formats (Optional association)
- CAB (.cab)
- ARJ (.arj)
- LZH (.lzh)

## Error Handling UX

### Error Scenarios
1. **Corrupted archive**: Show error in TUI, don't move to trash
2. **Insufficient permissions**: Show permission error
3. **Insufficient disk space**: Show space requirement and available space
4. **Archive password protected**: Show error (not yet supported)

### Error Display
Errors are shown inline in the TUI with a red "Error" badge and descriptive message.

## Progress Indication

All archives show progress in the TUI:
- Progress bar with percentage
- File count (processed / total)
- Bytes processed
- Current file being extracted
- Stage indicator (Analyzing → Extracting → Finalizing → Complete)

## Success Feedback

### Completion Display
On successful extraction, the TUI shows:
- Green progress bar at 100%
- Green "Done" badge
- Total files and bytes extracted

When `showCompletionDialog` is enabled (standalone mode), the TUI stays open with a "Close this window to exit." hint. When disabled (default), the application exits automatically.

## Accessibility

### Visual Accessibility
- High contrast color choices
- Clear, readable text
- Consistent visual language

### Cognitive Accessibility
- Clear, jargon-free language
- Consistent behavior patterns
- Predictable outcomes

## Platform-Specific Adaptations

### Windows
- Console window opens on double-click (CONSOLE subsystem)
- VT processing enabled for ANSI colors in legacy conhost
- Console title set to "Qunzip"

### macOS
- Terminal detection via `isatty()`
- Works in Terminal.app, iTerm2, etc.

### Linux
- Terminal detection via `isatty()`
- Works in any terminal emulator with ANSI support

## Future UX Enhancements

### Phase 2 Features
- Password-protected archive support
- Extraction location selection
- Archive preview mode

### Phase 3 Features
- Drag-and-drop extraction
- Batch extraction
- Archive creation functionality
