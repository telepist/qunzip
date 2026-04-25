=============================================================
  QUICK UNZIP - Archive Extraction Utility
  Version 1.0.0
=============================================================

Thank you for installing Quick Unzip!

WHAT IS QUICK UNZIP?
--------------------
Quick Unzip is a cross-platform archive extraction utility that makes it
easy to extract compressed files. Just double-click any supported archive
file and Quick Unzip will automatically extract it to the right location.


SUPPORTED FORMATS
-----------------
Quick Unzip supports the following archive formats:

  - ZIP       (.zip)
  - 7-Zip     (.7z)
  - RAR       (.rar)
  - TAR       (.tar, .tar.gz, .tar.bz2, .tar.xz, .tgz, .tbz2, .txz)
  - Cabinet   (.cab)
  - ARJ       (.arj)
  - LZH       (.lzh)


HOW TO USE
----------
1. AUTOMATIC EXTRACTION (Recommended)
   Double-click any archive file in Windows Explorer. The Quick Unzip
   window opens and:
   - Extracts single files to the same directory as the archive
   - Extracts multiple files to a new folder named after the archive
   - Optionally moves the original archive to the Recycle Bin (if enabled)

2. COMMAND-LINE USAGE
   The "qunzip" command is also available for terminal use:

   qunzip <archive-file>

   Examples:
     qunzip C:\Downloads\example.zip
     qunzip "D:\My Files\archive.7z"


FILE ASSOCIATIONS
-----------------
If you selected "Register file associations" during installation, Quick
Unzip is now the default application for opening archive files.

To manually register or unregister:

  Register:   qunzip --register-associations
  Unregister: qunzip --unregister-associations


EXTRACTION BEHAVIOR
-------------------
Quick Unzip follows these smart extraction rules:

  - Single file archive  -> Extracted to same directory as archive
  - Multiple files       -> New folder created with archive name
  - Single root folder   -> Contents extracted directly (no nested folder)
  - After extraction     -> Original archive moved to Recycle Bin (if enabled)


SETTINGS
--------
Open the Quick Unzip window without an archive to access the settings
panel, or use the command line:

  qunzip --set-trash-on       Enable moving archives to Recycle Bin
  qunzip --set-trash-off      Keep archives after extraction (default)
  qunzip --set-dialog-on      Keep window open after extraction
  qunzip --set-dialog-off     Close window automatically (default)


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
To uninstall Quick Unzip:
1. Open "Add or Remove Programs" in Windows Settings
2. Find "Quick Unzip" in the list
3. Click "Uninstall"

All files and file associations will be removed automatically.


TROUBLESHOOTING
---------------
If file associations aren't working:
  1. Right-click an archive file
  2. Choose "Open with" -> "Choose another app"
  3. Select Quick Unzip
  4. Check "Always use this app"

If you encounter errors:
  - Make sure you have permissions to write to the extraction directory
  - Check that the archive file isn't corrupted
  - Ensure you have enough disk space


Thank you for using Quick Unzip!

=============================================================
