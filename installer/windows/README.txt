================================================================================
  GUNZIP - Archive Extraction Utility
  Version 1.0.0
================================================================================

Thank you for installing Gunzip!

WHAT IS GUNZIP?
---------------
Gunzip is a cross-platform archive extraction utility that makes it easy to
extract compressed files. Just double-click any supported archive file and
Gunzip will automatically extract it to the right location.


SUPPORTED FORMATS
-----------------
Gunzip supports the following archive formats:

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
   Simply double-click any archive file in Windows Explorer. Gunzip will:
   - Extract single files to the same directory as the archive
   - Extract multiple files to a new folder with the archive's name
   - Automatically move the original archive to the Recycle Bin after extraction

2. COMMAND-LINE USAGE
   You can also use Gunzip from the command line:

   gunzip <archive-file>

   Examples:
     gunzip C:\Downloads\example.zip
     gunzip "D:\My Files\archive.7z"


FILE ASSOCIATIONS
-----------------
If you selected "Register file associations" during installation, Gunzip is
now the default application for opening archive files.

To manually register or unregister file associations:

  Register:   gunzip --register-associations
  Unregister: gunzip --unregister-associations


EXTRACTION BEHAVIOR
-------------------
Gunzip follows these smart extraction rules:

  • Single file archive → Extracted to same directory as archive
  • Multiple files → New folder created with archive name
  • Single root folder → Contents extracted directly (no nested folder)
  • After extraction → Original archive moved to Recycle Bin


GETTING HELP
------------
For help and additional options:

  gunzip --help

For version information:

  gunzip --version


PROJECT INFORMATION
-------------------
Website:     https://github.com/yourusername/gunzip
License:     MIT License (see LICENSE.txt)
7-Zip Tools: GNU LGPL (bundled with permission)


UNINSTALLING
------------
To uninstall Gunzip:
1. Open "Add or Remove Programs" in Windows Settings
2. Find "Gunzip" in the list
3. Click "Uninstall"

All files and file associations will be removed automatically.


TROUBLESHOOTING
---------------
If file associations aren't working:
  1. Right-click an archive file
  2. Choose "Open with" → "Choose another app"
  3. Select Gunzip
  4. Check "Always use this app"

If you encounter errors:
  • Make sure you have permissions to write to the extraction directory
  • Check that the archive file isn't corrupted
  • Ensure you have enough disk space


Thank you for using Gunzip!

================================================================================
