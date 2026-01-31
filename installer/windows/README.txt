=============================================================
  QUNZIP - Archive Extraction Utility
  Version 1.0.0
=============================================================

Thank you for installing Qunzip!

WHAT IS QUNZIP?
---------------
Qunzip is a cross-platform archive extraction utility that makes it easy to
extract compressed files. Just double-click any supported archive file and
Qunzip will automatically extract it to the right location.


SUPPORTED FORMATS
-----------------
Qunzip supports the following archive formats:

  • ZIP       (.zip)
  • 7-Zip     (.7z)
  • RAR       (.rar)
  • TAR       (.tar, .tar.gz, .tar.bz2, .tar.xz, .tgz, .tbz2, .txz)
  • Cabinet   (.cab)
  • ARJ       (.arj)
  • LZH       (.lzh)


HOW TO USE
----------
1. AUTOMATIC EXTRACTION (Recommended)
   Simply double-click any archive file in Windows Explorer. Qunzip will:
   - Extract single files to the same directory as the archive
   - Extract multiple files to a new folder with the archive's name
   - Optionally move the original archive to the Recycle Bin (if enabled)

2. COMMAND-LINE USAGE
   You can also use Qunzip from the command line:

   qunzip <archive-file>

   Examples:
     qunzip C:\Downloads\example.zip
     qunzip "D:\My Files\archive.7z"

   Force GUI or TUI mode:
     qunzip --gui archive.zip
     qunzip --tui archive.zip


FILE ASSOCIATIONS
-----------------
If you selected "Register file associations" during installation, Qunzip is
now the default application for opening archive files.

To manually register or unregister file associations:

  Register:   qunzip --register-associations
  Unregister: qunzip --unregister-associations


EXTRACTION BEHAVIOR
-------------------
Qunzip follows these smart extraction rules:

  • Single file archive → Extracted to same directory as archive
  • Multiple files → New folder created with archive name
  • Single root folder → Contents extracted directly (no nested folder)
  • After extraction → Original archive moved to Recycle Bin (if enabled)


SETTINGS
--------
Configure Qunzip preferences via command line:

  qunzip --set-trash-on       Enable moving archives to Recycle Bin
  qunzip --set-trash-off      Keep archives after extraction (default)
  qunzip --set-dialog-on      Show completion dialog
  qunzip --set-dialog-off     Silent exit after extraction (default)

Run "qunzip" without arguments to open the settings window.


GETTING HELP
------------
For help and additional options:

  qunzip --help

For version information:

  qunzip --version


PROJECT INFORMATION
-------------------
Website:     https://github.com/telepist/qunzip
License:     MIT License (see LICENSE.txt)
7-Zip Tools: GNU LGPL (bundled with permission)


UNINSTALLING
------------
To uninstall Qunzip:
1. Open "Add or Remove Programs" in Windows Settings
2. Find "Qunzip" in the list
3. Click "Uninstall"

All files and file associations will be removed automatically.


TROUBLESHOOTING
---------------
If file associations aren't working:
  1. Right-click an archive file
  2. Choose "Open with" → "Choose another app"
  3. Select Qunzip
  4. Check "Always use this app"

If you encounter errors:
  • Make sure you have permissions to write to the extraction directory
  • Check that the archive file isn't corrupted
  • Ensure you have enough disk space


Thank you for using Qunzip!

=============================================================
