@echo off
REM ============================================================================
REM Qunzip End-to-End Black Box Tests for Windows
REM ============================================================================
REM This script tests the actual qunzip.exe executable as a black box
REM by running it with test fixtures and verifying the filesystem results.
REM
REM Requirements:
REM   - qunzip.exe built and available
REM   - Test fixtures in ../fixtures/
REM   - 7-Zip installed (for qunzip to work)
REM
REM Usage: run-tests.bat [path-to-qunzip.exe]
REM ============================================================================

setlocal enabledelayedexpansion

REM Configuration
set "QUNZIP_EXE=%~1"
if "%QUNZIP_EXE%"=="" set "QUNZIP_EXE=..\..\..\build\bin\mingwX64\debugExecutable\qunzip.exe"

REM Convert QUNZIP_EXE to absolute path
pushd "%~dp0"
for %%i in ("%QUNZIP_EXE%") do set "QUNZIP_EXE=%%~fi"
popd

set "FIXTURES_DIR=%~dp0..\fixtures"
set "WORK_DIR=%~dp0..\results"
set "TEST_COUNT=0"
set "PASS_COUNT=0"
set "FAIL_COUNT=0"

REM Helper to copy fixtures into test directory
set "COPY_CMD=copy /Y"

REM Colors (if terminal supports ANSI)
set "GREEN=[92m"
set "RED=[91m"
set "YELLOW=[93m"
set "BLUE=[94m"
set "RESET=[0m"

echo.
echo ============================================================================
echo                    Qunzip E2E Black Box Tests
echo ============================================================================
echo.

REM Check if qunzip.exe exists
if not exist "%QUNZIP_EXE%" (
    echo %RED%ERROR: qunzip.exe not found at: %QUNZIP_EXE%%RESET%
    echo Please build the project first: gradlew buildAll
    exit /b 1
)

echo Using qunzip.exe: %QUNZIP_EXE%
echo Fixtures directory: %FIXTURES_DIR%
echo Work directory: %WORK_DIR%
echo.

REM Clean and create work directory
if exist "%WORK_DIR%" rmdir /s /q "%WORK_DIR%" 2>nul
mkdir "%WORK_DIR%" 2>nul

REM ============================================================================
REM Test 1: Single File Extraction
REM ============================================================================
call :test_start "Single file extraction"

set "TEST_DIR=%WORK_DIR%\test-single"
mkdir "%TEST_DIR%" 2>nul
call :prepare_fixture "%FIXTURES_DIR%\single-file.zip" "%TEST_DIR%\single-file.zip" || goto :test_single_end

cd /d "%TEST_DIR%"
"%QUNZIP_EXE%" single-file.zip

REM Verify: file1.txt should exist
if exist "file1.txt" (
    REM Verify: original ZIP should be gone (moved to trash)
    if not exist "single-file.zip" (
        call :test_pass "File extracted and ZIP removed"
    ) else (
        call :test_fail "ZIP file was not moved to trash"
    )
) else (
    call :test_fail "file1.txt was not extracted"
)

cd /d "%~dp0"
:test_single_end

REM ============================================================================
REM Test 2: Multiple Files - Should Create Folder
REM ============================================================================
call :test_start "Multiple files extraction with folder creation"

set "TEST_DIR=%WORK_DIR%\test-multiple"
mkdir "%TEST_DIR%" 2>nul
call :prepare_fixture "%FIXTURES_DIR%\multiple-files.zip" "%TEST_DIR%\multiple-files.zip" || goto :test_multiple_end

cd /d "%TEST_DIR%"
"%QUNZIP_EXE%" multiple-files.zip

REM Verify: folder should be created
if exist "multiple-files" (
    if exist "multiple-files\file1.txt" (
        if exist "multiple-files\file2.txt" (
            if exist "multiple-files\file3.txt" (
                if not exist "multiple-files.zip" (
                    call :test_pass "Folder created with all files, ZIP removed"
                ) else (
                    call :test_fail "ZIP was not moved to trash"
                )
            ) else (
                call :test_fail "file3.txt missing"
            )
        ) else (
            call :test_fail "file2.txt missing"
        )
    ) else (
        call :test_fail "file1.txt missing"
    )
) else (
    call :test_fail "Folder 'multiple-files' was not created"
)

cd /d "%~dp0"
:test_multiple_end

REM ============================================================================
REM Test 3: Nested Folder - Should Extract Contents
REM ============================================================================
call :test_start "Nested folder extraction"

set "TEST_DIR=%WORK_DIR%\test-nested"
mkdir "%TEST_DIR%" 2>nul
call :prepare_fixture "%FIXTURES_DIR%\nested-folder.zip" "%TEST_DIR%\nested-folder.zip" || goto :test_nested_end

cd /d "%TEST_DIR%"
"%QUNZIP_EXE%" nested-folder.zip

REM Verify: nested directory structure should exist
if exist "nested\nested-file.txt" (
    if exist "nested\subfolder\deep-file.txt" (
        if not exist "nested-folder.zip" (
            call :test_pass "Nested structure extracted, ZIP removed"
        ) else (
            call :test_fail "ZIP was not moved to trash"
        )
    ) else (
        call :test_fail "Deep nested file missing"
    )
) else (
    call :test_fail "Nested directory not extracted correctly"
)

cd /d "%~dp0"
:test_nested_end

REM ============================================================================
REM Test Summary
REM ============================================================================
echo.
echo ============================================================================
echo                          TEST SUMMARY
echo ============================================================================
echo Total Tests:  %TEST_COUNT%
echo %GREEN%Passed:       %PASS_COUNT%%RESET%
echo %RED%Failed:       %FAIL_COUNT%%RESET%
echo ============================================================================
echo.

if %FAIL_COUNT% EQU 0 (
    echo %GREEN%ALL TESTS PASSED!%RESET%
    exit /b 0
) else (
    echo %RED%SOME TESTS FAILED!%RESET%
    exit /b 1
)

REM ============================================================================
REM Helper Functions
REM ============================================================================

:test_start
set /a TEST_COUNT+=1
echo.
echo %BLUE%[TEST %TEST_COUNT%]%RESET% %~1
echo ----------------------------------------------------------------------------
goto :eof

:test_pass
set /a PASS_COUNT+=1
echo %GREEN%[PASS]%RESET% %~1
goto :eof

:test_fail
set /a FAIL_COUNT+=1
echo %RED%[FAIL]%RESET% %~1
goto :eof

:prepare_fixture
set "SOURCE=%~1"
set "DESTINATION=%~2"
if not exist "%SOURCE%" (
    call :test_fail "Fixture missing: %SOURCE%"
    exit /b 1
)
%COPY_CMD% "%SOURCE%" "%DESTINATION%" >nul 2>&1
if errorlevel 1 (
    call :test_fail "Failed to copy fixture: %SOURCE%"
    exit /b 1
)
if not exist "%DESTINATION%" (
    call :test_fail "Fixture copy not found: %DESTINATION%"
    exit /b 1
)
exit /b 0
