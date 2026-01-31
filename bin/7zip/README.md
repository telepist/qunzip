# 7-Zip Dependencies

This directory contains the official 7-Zip command-line tools bundled with Qunzip.

## Files

- **7z.exe** - 7-Zip command-line executable
- **7z.dll** - Required library for 7z.exe
- **License.txt** - 7-Zip license (GNU LGPL)

**Note:** `7z.exe` and `7z.dll` are not stored in git. They are downloaded automatically during build.

## Downloading

Run the Gradle task to download 7-Zip binaries:

```bash
./gradlew download7zip
```

This downloads from the official 7-Zip website and extracts the required files.

The download happens automatically when building Windows executables if the files don't exist.

## Source

Downloaded from the official 7-Zip website: https://www.7-zip.org/

**Version**: 25.01
**Package**: 7z2501-x64.exe (Windows x64 installer, extracted)

## Format Support

With 7z.exe + 7z.dll, Qunzip supports **all** archive formats:

✅ ZIP, 7Z, RAR (Rar5), TAR
✅ GZIP (.gz, .tgz), BZIP2 (.bz2, .tbz2), XZ (.xz, .txz)
✅ CAB, ARJ, LZH
✅ ISO, WIM, and many more

## Build Integration

During build, these files are automatically copied to the output directory:
- **Debug**: `build/bin/mingwX64/debugExecutable/`
- **Release**: `build/bin/mingwX64/releaseExecutable/`

This creates a **self-contained distribution** - the Qunzip executable and 7-Zip tools can run from anywhere without installation.

## License

7-Zip is licensed under the GNU LGPL license. See `License.txt` for full details.

**Copyright**: Igor Pavlov (1999-2025)
**Website**: https://www.7-zip.org/
