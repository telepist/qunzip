#!/bin/bash
# Build cimgui + imgui + Win32/DX11 backend into a static library for MinGW x64
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CIMGUI_DIR="$SCRIPT_DIR/../cimgui"
IMGUI_DIR="$CIMGUI_DIR/imgui"
BUILD_DIR="$SCRIPT_DIR/build"
OUTPUT="$BUILD_DIR/libcimgui.a"

mkdir -p "$BUILD_DIR"

CXX="${CXX:-g++}"
AR="${AR:-ar}"

CXXFLAGS="-O2 -I$IMGUI_DIR -I$IMGUI_DIR/backends -I$CIMGUI_DIR -I$SCRIPT_DIR"

SOURCES=(
    "$IMGUI_DIR/imgui.cpp"
    "$IMGUI_DIR/imgui_demo.cpp"
    "$IMGUI_DIR/imgui_draw.cpp"
    "$IMGUI_DIR/imgui_tables.cpp"
    "$IMGUI_DIR/imgui_widgets.cpp"
    "$IMGUI_DIR/backends/imgui_impl_win32.cpp"
    "$IMGUI_DIR/backends/imgui_impl_dx11.cpp"
    "$CIMGUI_DIR/cimgui.cpp"
    "$SCRIPT_DIR/imgui_app.cpp"
)

OBJECTS=()
for src in "${SOURCES[@]}"; do
    obj="$BUILD_DIR/$(basename "$src" .cpp).o"
    echo "Compiling $(basename "$src")..."
    $CXX $CXXFLAGS -c "$src" -o "$obj"
    OBJECTS+=("$obj")
done

echo "Creating static library..."
$AR rcs "$OUTPUT" "${OBJECTS[@]}"

echo "Built: $OUTPUT"
