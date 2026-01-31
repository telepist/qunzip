# UX Design Document

## Design Philosophy

This application follows macOS-inspired design principles while maintaining cross-platform compatibility. The focus is on **invisible interaction** - users should be able to extract archives without thinking about the process.

## Core UX Principles

### 1. Zero-Configuration Extraction
- **Double-click to extract**: Primary interaction method
- **Smart extraction logic**: Automatically determines best extraction strategy
- **No dialogs**: Minimal user interruption

### 2. Intelligent Behavior
- **Single file archives**: Extract directly to same directory
- **Multi-file archives**: Create directory named after archive
- **Optional cleanup**: Move source archive to trash after extraction (user preference)

### 3. Seamless Integration
- **Native file associations**: Registers as default handler for supported formats
- **Platform-native notifications**: Uses OS notification system
- **Trash integration**: Uses platform-appropriate trash/recycle bin

## User Interaction Flows

### Primary Flow: Double-Click Extraction

```
User double-clicks archive.zip
        ↓
Application launches (background)
        ↓
Analysis: archive.zip contains multiple files
        ↓
Creates directory: archive/
        ↓
Extracts all files to archive/
        ↓
Shows progress notification (if large)
        ↓
Moves archive.zip to trash (if enabled)
        ↓
Shows success notification (if enabled)
        ↓
Application exits
```

### Alternative Flow: Single File Archive

```
User double-clicks document.zip
        ↓
Application launches (background)
        ↓
Analysis: document.zip contains single file
        ↓
Extracts document.pdf to same directory
        ↓
Moves document.zip to trash (if enabled)
        ↓
Shows success notification (if enabled)
        ↓
Application exits
```

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
1. **Corrupted archive**: Show error notification, don't move to trash
2. **Insufficient permissions**: Request elevation or show permission error
3. **Insufficient disk space**: Show space requirement and available space
4. **Archive password protected**: Show password dialog (future feature)

### Error Notification Format
```
Title: "Extraction Failed"
Message: "Could not extract [filename]: [specific reason]"
Actions: [View Details] [OK]
```

## Progress Indication

### Small Archives (< 10MB)
- No progress indication
- Simple completion notification

### Large Archives (> 10MB)
- Native progress notification
- Shows: filename, progress percentage, estimated time
- Cancellable operation

### Progress Notification Format
```
Title: "Extracting [filename]"
Progress: [██████████████▒▒▒▒▒▒] 70%
Message: "2.1 GB of 3.0 GB • 30 seconds remaining"
Actions: [Cancel]
```

## Success Feedback

### Completion Notification (when enabled)
```
Title: "Extraction Complete"
Message: "[filename] extracted successfully"
Actions: [OK]
```

When completion dialog is disabled (default), the application exits silently after successful extraction.

## Accessibility

### Visual Accessibility
- High contrast notification icons
- Clear, readable notification text
- Consistent visual language

### Motor Accessibility
- Large click targets in dialogs
- Keyboard navigation support
- No time-sensitive interactions

### Cognitive Accessibility
- Clear, jargon-free language
- Consistent behavior patterns
- Predictable outcomes

## Platform-Specific Adaptations

### Windows
- Uses Windows notification system
- Follows Windows file naming conventions
- Integrates with Windows Explorer context menu

### macOS
- Uses macOS notification center
- Follows macOS file naming conventions
- Integrates with Finder

### Linux
- Uses desktop environment notification system
- Follows XDG standards
- Integrates with default file manager

## Future UX Enhancements

### Phase 2 Features
- Password-protected archive support
- Extraction location selection
- Archive preview mode

### Phase 3 Features
- Drag-and-drop extraction
- Batch extraction
- Archive creation functionality