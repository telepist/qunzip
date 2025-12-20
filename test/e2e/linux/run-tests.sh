#!/bin/bash
# ============================================================================
# Gunzip End-to-End Black Box Tests for Linux
# ============================================================================
# This script tests the actual gunzip executable as a black box
# by running it with test fixtures and verifying the filesystem results.
#
# Requirements:
#   - gunzip built for Linux (linuxX64)
#   - Test fixtures in ../fixtures/
#   - 7-Zip installed (p7zip-full package)
#
# Usage: ./run-tests.sh [path-to-gunzip]
# ============================================================================

set -e

# Configuration
GUNZIP_EXE="${1:-../../../build/bin/linuxX64/debugExecutable/gunzip.kexe}"
FIXTURES_DIR="$(dirname "$0")/../fixtures"
WORK_DIR="$(dirname "$0")/../results/linux"
TEST_COUNT=0
PASS_COUNT=0
FAIL_COUNT=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo ""
echo "============================================================================"
echo "                    Gunzip E2E Black Box Tests (Linux)"
echo "============================================================================"
echo ""

# Check if gunzip exists
if [ ! -f "$GUNZIP_EXE" ]; then
    echo -e "${RED}ERROR: gunzip not found at: $GUNZIP_EXE${NC}"
    echo "Please build the project first: ./gradlew buildAll"
    exit 1
fi

echo "Using gunzip: $GUNZIP_EXE"
echo "Fixtures directory: $FIXTURES_DIR"
echo "Work directory: $WORK_DIR"
echo ""

# Clean and create work directory
rm -rf "$WORK_DIR" 2>/dev/null
mkdir -p "$WORK_DIR"

# Helper functions
test_start() {
    ((TEST_COUNT++))
    echo ""
    echo -e "${BLUE}[TEST $TEST_COUNT]${NC} $1"
    echo "----------------------------------------------------------------------------"
}

test_pass() {
    ((PASS_COUNT++))
    echo -e "${GREEN}[PASS]${NC} $1"
}

test_fail() {
    ((FAIL_COUNT++))
    echo -e "${RED}[FAIL]${NC} $1"
}

# ============================================================================
# Test 1: Single File Extraction
# ============================================================================
test_start "Single file extraction"

TEST_DIR="$WORK_DIR/test-single"
mkdir -p "$TEST_DIR"
cp "$FIXTURES_DIR/single-file.zip" "$TEST_DIR/"

cd "$TEST_DIR"
"$GUNZIP_EXE" single-file.zip 2>/dev/null || true

# Verify: file1.txt should exist
if [ -f "file1.txt" ]; then
    # Verify: original ZIP should be gone (moved to trash)
    if [ ! -f "single-file.zip" ]; then
        test_pass "File extracted and ZIP removed"
    else
        test_fail "ZIP file was not moved to trash"
    fi
else
    test_fail "file1.txt was not extracted"
fi

cd - > /dev/null

# ============================================================================
# Test 2: Multiple Files - Should Create Folder
# ============================================================================
test_start "Multiple files extraction with folder creation"

TEST_DIR="$WORK_DIR/test-multiple"
mkdir -p "$TEST_DIR"
cp "$FIXTURES_DIR/multiple-files.zip" "$TEST_DIR/"

cd "$TEST_DIR"
"$GUNZIP_EXE" multiple-files.zip 2>/dev/null || true

# Verify: folder should be created
if [ -d "multiple-files" ] && \
   [ -f "multiple-files/file1.txt" ] && \
   [ -f "multiple-files/file2.txt" ] && \
   [ -f "multiple-files/file3.txt" ]; then
    if [ ! -f "multiple-files.zip" ]; then
        test_pass "Folder created with all files, ZIP removed"
    else
        test_fail "ZIP was not moved to trash"
    fi
else
    test_fail "Folder 'multiple-files' was not created with all files"
fi

cd - > /dev/null

# ============================================================================
# Test 3: Nested Folder - Should Extract Contents
# ============================================================================
test_start "Nested folder extraction"

TEST_DIR="$WORK_DIR/test-nested"
mkdir -p "$TEST_DIR"
cp "$FIXTURES_DIR/nested-folder.zip" "$TEST_DIR/"

cd "$TEST_DIR"
"$GUNZIP_EXE" nested-folder.zip 2>/dev/null || true

# Verify: nested directory structure should exist
if [ -f "nested/nested-file.txt" ] && \
   [ -f "nested/subfolder/deep-file.txt" ]; then
    if [ ! -f "nested-folder.zip" ]; then
        test_pass "Nested structure extracted, ZIP removed"
    else
        test_fail "ZIP was not moved to trash"
    fi
else
    test_fail "Nested directory not extracted correctly"
fi

cd - > /dev/null

# ============================================================================
# Test Summary
# ============================================================================
echo ""
echo "============================================================================"
echo "                          TEST SUMMARY"
echo "============================================================================"
echo "Total Tests:  $TEST_COUNT"
echo -e "${GREEN}Passed:       $PASS_COUNT${NC}"
echo -e "${RED}Failed:       $FAIL_COUNT${NC}"
echo "============================================================================"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}ALL TESTS PASSED!${NC}"
    exit 0
else
    echo -e "${RED}SOME TESTS FAILED!${NC}"
    exit 1
fi
