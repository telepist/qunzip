# Qunzip Icon Creation Guide

This directory should contain `icon.ico` for the Windows installer.

## Quick Start

### Option 1: Use PowerShell Script (Recommended)

```powershell
cd installer/windows
.\create-icon.ps1
```

This creates `icon-temp.png`. Then convert to .ico using one of the methods below.

### Option 2: Download Pre-made Icon

If available, download the official Qunzip icon and save as `icon.ico` in this directory.

## Converting PNG to ICO

After creating or obtaining a PNG image, convert it to .ico format:

### Method 1: Online Converter
1. Visit https://convertio.co/png-ico/
2. Upload your PNG file
3. Download the converted .ico file
4. Save as `installer/windows/icon.ico`

### Method 2: ImageMagick (if installed)
```bash
magick convert icon-temp.png -define icon:auto-resize=256,48,32,16 icon.ico
```

### Method 3: GIMP (Free Image Editor)
1. Open PNG in GIMP
2. File → Export As
3. Change extension to `.ico`
4. Select multiple sizes: 16x16, 32x32, 48x48, 256x256

### Method 4: Online ICO Editor
1. Visit https://www.icoconverter.com/
2. Upload your PNG
3. Select sizes: 16, 32, 48, 256
4. Download icon.ico

## Icon Specifications

- **Format**: Windows ICO file
- **Sizes included**: 16x16, 32x32, 48x48, 256x256 pixels
- **Color depth**: 32-bit (RGBA)
- **Design**:
  - Background: Blue gradient (#4682C8 to #1E508C)
  - Text: White "QZ" in bold sans-serif font (for "Quick unZip")
  - Optional: Subtle border or zipper visual element

## Temporary Workaround

If you don't have an icon yet, the installer will still work. Inno Setup will use the default Windows application icon. You can add the icon later and rebuild the installer.

To skip icon for now:
1. Comment out or remove the `SetupIconFile` line in `qunzip.iss`
2. Comment out or remove the `UninstallDisplayIcon` line in the `[Setup]` section

## Testing the Icon

After creating `icon.ico`:

```bash
# View in Windows Explorer
explorer installer\windows\

# Use in installer build
.\gradlew buildWindowsInstaller
```

The icon will appear:
- In the installer wizard
- In Add/Remove Programs
- On file associations (when you double-click archives)
- As the executable icon
