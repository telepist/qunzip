# UX Design Document

## Design Philosophy

This application follows macOS-inspired design principles while maintaining cross-platform compatibility. The focus is on **invisible interaction** - users should be able to extract archives without thinking about the process.

## Core UX Principles

### 1. Zero-Configuration Extraction
- **Double-click to extract**: Primary interaction method
- **Smart extraction logic**: Automatically determines best extraction strategy
- **Minimal UI**: ImGui GUI for standalone/double-click launches (Windows, DX11), Mosaic TUI for CLI

### 2. Intelligent Behavior
- **Single file archives**: Extract directly to same directory
- **Multi-file archives**: Create directory named after archive
- **Optional cleanup**: Move source archive to trash after extraction (user preference)

### 3. Seamless Integration
- **Native file associations**: Registers as default handler for supported formats
- **Trash integration**: Uses platform-appropriate trash/recycle bin
- **Dual UI**: Native ImGui GUI window on standalone launch, terminal TUI in CLI mode

## User Interaction Flows

### Primary Flow: Double-Click Extraction

```
User double-clicks archive.zip
        ↓
ImGui GUI window opens (Windows, DX11)
        ↓
Analysis: archive.zip contains multiple files
        ↓
Creates directory: archive/
        ↓
Extracts all files to archive/
        ↓
Shows progress in GUI (progress bar, file count)
        ↓
Moves archive.zip to trash (if enabled)
        ↓
Application closes automatically (or stays open if auto-close disabled)
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

## UI Design

### UI Renderers

The application uses two UI renderers depending on launch context:

- **ImGui GUI (Windows, DX11)**: Used for standalone/double-click launches. Renders a native window with graphical progress display.
- **Mosaic TUI**: Used for CLI launches. Renders in the existing terminal with ANSI colors and box-drawing characters.

### Extraction Screen (TUI / CLI Mode)

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

When `autoCloseAfterExtraction` is enabled (default), the application closes automatically after extraction completes. When disabled, the window stays open with a prompt to allow the user to review the results before closing.

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
- ImGui GUI window (DX11) opens on double-click (standalone mode)
- Mosaic TUI used when launched from an existing terminal (CLI mode)
- Standalone detection via `GetConsoleProcessList`

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
