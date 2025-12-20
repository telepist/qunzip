# End-to-End Black Box Tests

This directory contains **true end-to-end black box tests** for Gunzip. These tests run the actual compiled executable and verify real filesystem operations.

## Philosophy

These tests are **completely black box**:
- ✅ Test the actual compiled binary (not code/classes)
- ✅ Use real filesystem operations (no mocks)
- ✅ Run via shell scripts (`.bat` for Windows, `.sh` for Linux/macOS)
- ✅ Platform-specific (tests only run on the platform they're built for)
- ✅ Verify behavior by inspecting actual file system changes

## Directory Structure

```
test/e2e/
├── fixtures/                   # Test ZIP files (shared across platforms)
│   ├── single-file.zip        # Single file archive
│   ├── multiple-files.zip     # Multiple files at root
│   └── nested-folder.zip      # Nested directory structure
├── windows/
│   └── run-tests.bat          # Windows E2E test script
├── linux/
│   └── run-tests.sh           # Linux E2E test script
├── macos/
│   └── run-tests.sh           # macOS E2E test script (same as Linux)
└── results/                   # Test execution workspace (git-ignored)
    ├── linux/                 # Linux test results
    ├── macos/                 # macOS test results
    └── windows/               # Windows test results
```

## Test Fixtures

All platforms share the same test fixtures in `fixtures/`:

### single-file.zip
- **Contents**: Single `file1.txt` at root
- **Expected**: Extract file to same directory, move ZIP to trash
- **Tests**: Basic extraction logic

### multiple-files.zip
- **Contents**: `file1.txt`, `file2.txt`, `file3.txt` at root level
- **Expected**: Create folder named "multiple-files", extract files inside, move ZIP to trash
- **Tests**: Smart folder creation strategy

### nested-folder.zip
- **Contents**: Single root directory `nested/` with subdirectories
- **Expected**: Extract nested folder contents to same directory, move ZIP to trash
- **Tests**: Single root directory strategy

## Running Tests

### Prerequisites

**Windows:**
- Gunzip built: `./gradlew linkDebugExecutableMingwX64` (or `buildAll`)
- 7-Zip installed and in PATH
- Executable at: `build/bin/mingwX64/debugExecutable/gunzip.exe`

**Linux:**
- Gunzip built: `./gradlew linkDebugExecutableLinuxX64` (or `buildAll`)
- p7zip-full package installed (`sudo apt-get install p7zip-full`)
- Executable at: `build/bin/linuxX64/debugExecutable/gunzip.kexe`

**macOS:**
- Gunzip built: `./gradlew linkDebugExecutableMacosX64` or `MacosArm64`
- p7zip installed (`brew install p7zip`)
- Executable at: `build/bin/macosX64/debugExecutable/gunzip.kexe`

### Execution

**Windows (Command Prompt or PowerShell):**
```cmd
cd test\e2e\windows
run-tests.bat

REM Or specify custom gunzip path:
run-tests.bat C:\path\to\gunzip.exe
```

**Linux:**
```bash
cd test/e2e/linux
chmod +x run-tests.sh
./run-tests.sh

# Or specify custom gunzip path:
./run-tests.sh /path/to/gunzip.kexe
```

**macOS:**
```bash
cd test/e2e/macos
chmod +x run-tests.sh
./run-tests.sh

# Or specify custom gunzip path:
./run-tests.sh /path/to/gunzip.kexe
```

## Test Output

Tests provide clear, color-coded output:

```
============================================================================
                    Gunzip E2E Black Box Tests
============================================================================

Using gunzip.exe: ..\..\..\build\bin\mingwX64\debugExecutable\gunzip.exe
Fixtures directory: ..\fixtures
Work directory: ..\results

[TEST 1] Single file extraction
----------------------------------------------------------------------------
[PASS] File extracted and ZIP removed

[TEST 2] Multiple files extraction with folder creation
----------------------------------------------------------------------------
[PASS] Folder created with all files, ZIP removed

[TEST 3] Nested folder extraction
----------------------------------------------------------------------------
[PASS] Nested structure extracted, ZIP removed

============================================================================
                          TEST SUMMARY
============================================================================
Total Tests:  3
Passed:       3
Failed:       0
============================================================================

ALL TESTS PASSED!
```

## What Each Test Verifies

### Test 1: Single File Extraction
1. Copies `single-file.zip` to temp directory
2. Runs: `gunzip single-file.zip`
3. Verifies: `file1.txt` exists in same directory
4. Verifies: `single-file.zip` no longer exists (moved to trash)

### Test 2: Multiple Files with Folder Creation
1. Copies `multiple-files.zip` to temp directory
2. Runs: `gunzip multiple-files.zip`
3. Verifies: Folder `multiple-files/` was created
4. Verifies: All files exist inside folder (`file1.txt`, `file2.txt`, `file3.txt`)
5. Verifies: Original ZIP no longer exists

### Test 3: Nested Folder Extraction
1. Copies `nested-folder.zip` to temp directory
2. Runs: `gunzip nested-folder.zip`
3. Verifies: Nested directory structure exists (`nested/nested-file.txt`, `nested/subfolder/deep-file.txt`)
4. Verifies: Original ZIP no longer exists

## Advantages of Black Box Testing

1. **True End-to-End**: Tests the actual compiled binary, not just code
2. **Platform-Specific**: Each platform tests its own binary with platform tools
3. **Simple**: No complex test framework, just shell scripts
4. **Realistic**: Uses actual filesystem, actual trash, actual 7-Zip
5. **Fast**: Scripts run in seconds
6. **Debuggable**: Easy to inspect `results/` directory when tests fail
7. **CI-Friendly**: Easy to integrate into CI/CD pipelines

## Limitations

- ⚠️ Tests only run on the platform they're designed for
- ⚠️ Require the binary to be built before testing
- ⚠️ Require 7-Zip installed and available
- ⚠️ Cannot test internal state, only observable filesystem changes
- ⚠️ No parallel execution (tests run sequentially)

## Adding New Tests

To add a new test case:

1. Add test fixture ZIP to `fixtures/` if needed
2. Edit platform-specific script (e.g., `windows/run-tests.bat`)
3. Follow the pattern:
   ```batch
   call :test_start "Test description"
   REM Setup
   REM Execute gunzip
   REM Verify results
   call :test_pass "Success message"
   REM or
   call :test_fail "Failure message"
   ```

## Integration with Development Workflow

```bash
# Build the application
./gradlew buildAll

# Run unit tests
./gradlew check

# Run E2E black box tests (Windows)
cd test\e2e\windows
run-tests.bat

# Run E2E black box tests (Linux)
cd test/e2e/linux
./run-tests.sh
```

## Troubleshooting

**Tests fail with "gunzip not found":**
- Build the project: `./gradlew buildAll`
- Or specify path: `run-tests.bat path\to\gunzip.exe`

**Tests fail with "7z command not found":**
- Install 7-Zip and add to PATH
- Windows: Download from https://www.7-zip.org/
- Linux: `sudo apt-get install p7zip-full`
- macOS: `brew install p7zip`

**Tests fail but files are extracted:**
- Check `test/e2e/results/` to inspect actual results
- Verify trash functionality works on your platform
- Original ZIP might still be there if trash failed

**Permission errors:**
- Windows: Run as Administrator if needed
- Linux/macOS: Ensure execute permissions: `chmod +x run-tests.sh`
