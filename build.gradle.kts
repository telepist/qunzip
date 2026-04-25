plugins {
    kotlin("multiplatform") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    kotlin("plugin.compose") version "2.3.21"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

detekt {
    // Run on all our Kotlin source — common + per-platform mains and tests.
    source.setFrom(
        files(
            "src/commonMain/kotlin",
            "src/commonTest/kotlin",
            "src/mingwX64Main/kotlin",
            "src/mingwX64Test/kotlin",
            "src/linuxX64Main/kotlin",
            "src/linuxArm64Main/kotlin",
            "src/macosX64Main/kotlin",
            "src/macosArm64Main/kotlin",
        )
    )
    parallel = true
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
    autoCorrect = false
}

// Application version
version = "1.0.0"
group = "com.qunzip"

kotlin {
    macosArm64 {
        binaries {
            executable {
                baseName = "qunzip"
                entryPoint = "qunzip.main"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                baseName = "qunzip"
                entryPoint = "qunzip.main"
            }
        }
    }
    linuxArm64 {
        binaries {
            executable {
                baseName = "qunzip"
                entryPoint = "qunzip.main"
            }
        }
    }
    mingwX64 {
        compilations["main"].cinterops {
            val cimgui by creating {
                defFile(project.file("src/nativeInterop/cinterop/cimgui.def"))
                includeDirs(
                    project.file("libs/cimgui"),
                    project.file("libs/imgui-backend")
                )
            }
        }
        binaries {
            // CLI exe — console subsystem. Used from cmd.exe / PowerShell, by tests,
            // and by the installer for `--register-associations`.
            executable("cli") {
                baseName = "qunzip"
                entryPoint = "qunzip.mainCli"
                linkerOpts(file("build/resources/qunzip.res").absolutePath)
                linkerOpts(
                    "-L${project.file("libs/imgui-backend/build").absolutePath}",
                    "-lcimgui",
                    "-ld3d11", "-ldxgi", "-ld3dcompiler",
                    "-limm32",
                    "-ldwmapi",
                    "-Wl,-Bstatic", "-lstdc++", "-Wl,-Bdynamic"
                )
            }

            // GUI exe — Windows subsystem so file-association double-click and
            // drag-drop launches show the ImGui dialog with no console flash.
            executable("gui") {
                baseName = "QuickUnzip"
                entryPoint = "qunzip.mainGui"
                linkerOpts(file("build/resources/qunzip.res").absolutePath)
                linkerOpts("-Wl,--subsystem,windows")
                linkerOpts(
                    "-L${project.file("libs/imgui-backend/build").absolutePath}",
                    "-lcimgui",
                    "-ld3d11", "-ldxgi", "-ld3dcompiler",
                    "-limm32",
                    "-ldwmapi",
                    "-Wl,-Bstatic", "-lstdc++", "-Wl,-Bdynamic"
                )
            }
        }
    }

    // The Default Kotlin Hierarchy Template automatically creates intermediate source sets
    // (nativeMain, appleMain, linuxMain, etc.) and sets up the dependency hierarchy.
    // No manual sourceSets configuration needed!

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("co.touchlab:kermit:2.1.0") // Logging

                // Mosaic for TUI (local build with AnsiLevel support for NonInteractiveTerminal)
                implementation("com.jakewharton.mosaic:mosaic-runtime:0.19.0-SNAPSHOT")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
                implementation("app.cash.turbine:turbine:1.2.1") // Flow testing
            }
        }

        // Native targets get file system and process APIs
        nativeMain {
            dependencies {
                // Platform-specific dependencies will be added in platform-specific source sets
            }
        }

        nativeTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// ============================================================================
// Test Pyramid Tasks
// ============================================================================
//
// Test levels (run all at once or individually):
//   ./gradlew testAll          - Run entire test pyramid
//   ./gradlew unitTest         - Unit tests only (domain entities, use cases, viewmodels)
//   ./gradlew integrationTest  - Integration tests only (real 7zip, real filesystem)
//   ./gradlew e2eTest          - E2E tests only (launches compiled qunzip.exe)
//   ./gradlew mingwX64Test     - All Windows platform tests (integration + e2e)
//

tasks.register("testAll") {
    dependsOn("mingwX64Test")
    group = "verification"
    description = "Run entire test pyramid (unit + integration + e2e)"
}

tasks.register("unitTest") {
    dependsOn("mingwX64Test")
    group = "verification"
    description = "Run unit tests (common tests on mingwX64)"
}

tasks.register("integrationTest") {
    dependsOn("mingwX64Test")
    group = "verification"
    description = "Run integration tests (real 7zip, real filesystem)"
}

tasks.register("e2eTest") {
    dependsOn("mingwX64Test")
    group = "verification"
    description = "Run end-to-end tests (launches compiled qunzip.exe)"
}

// Ensure mingwX64Test has the executable and 7zip available
tasks.named("mingwX64Test") {
    dependsOn("linkCliDebugExecutableMingwX64", "copy7zipToCliDebugMingwX64")
    dependsOn("download7zip")
}

// Build tasks for all platforms
tasks.register("buildAll") {
    dependsOn(
        "linkDebugExecutableMacosArm64",
        "linkDebugExecutableLinuxX64",
        "linkDebugExecutableLinuxArm64",
        "linkCliDebugExecutableMingwX64",
        "linkGuiDebugExecutableMingwX64"
    )
    group = "build"
    description = "Build debug executables for all platforms"
}

tasks.register("buildAllRelease") {
    dependsOn(
        "linkReleaseExecutableMacosArm64",
        "linkReleaseExecutableLinuxX64",
        "linkReleaseExecutableLinuxArm64",
        "linkCliReleaseExecutableMingwX64",
        "linkGuiReleaseExecutableMingwX64"
    )
    group = "build"
    description = "Build release executables for all platforms"
}

// Compile Windows resource file (icon and version info)
tasks.register<Exec>("compileWindowsResources") {
    val rcFile = file("src/mingwX64Main/resources/qunzip.rc")
    val resFile = file("build/resources/qunzip.res")
    val iconFile = file("installer/windows/icon.ico")

    inputs.file(rcFile)
    inputs.file(iconFile).optional()
    outputs.file(resFile)

    doFirst {
        resFile.parentFile.mkdirs()
    }

    // Only compile if icon exists
    onlyIf {
        iconFile.exists()
    }

    // Use windres from MinGW to compile the resource file
    // Set working directory to project root so relative paths work
    workingDir = projectDir
    commandLine("windres",
        "--include-dir=${iconFile.parentFile.absolutePath}",
        rcFile.absolutePath,
        "-O", "coff",
        "-o", resFile.absolutePath)

    group = "build"
    description = "Compile Windows resource file (icon and version info)"
}

// Build cimgui static library (ImGui + Win32/DX11 backend).
// Calls g++ / ar directly so we don't depend on bash being on PATH —
// IntelliJ's Gradle daemon doesn't inherit Git Bash / MSYS2's PATH the
// way an interactive terminal does.
tasks.register("buildCimgui") {
    val backendDir = file("libs/imgui-backend")
    val cimguiDir = file("libs/cimgui")
    val imguiDir = file("libs/cimgui/imgui")
    val buildDir = file("libs/imgui-backend/build")
    val outputLib = file("libs/imgui-backend/build/libcimgui.a")

    val sources = listOf(
        file("libs/cimgui/imgui/imgui.cpp"),
        file("libs/cimgui/imgui/imgui_demo.cpp"),
        file("libs/cimgui/imgui/imgui_draw.cpp"),
        file("libs/cimgui/imgui/imgui_tables.cpp"),
        file("libs/cimgui/imgui/imgui_widgets.cpp"),
        file("libs/cimgui/imgui/backends/imgui_impl_win32.cpp"),
        file("libs/cimgui/imgui/backends/imgui_impl_dx11.cpp"),
        file("libs/cimgui/cimgui.cpp"),
        file("libs/imgui-backend/imgui_app.cpp"),
    )

    inputs.files(fileTree("libs/imgui-backend") { exclude("build") })
    inputs.files(fileTree("libs/cimgui") {
        include("*.cpp", "*.h", "imgui/*.cpp", "imgui/*.h",
                "imgui/backends/imgui_impl_win32.*", "imgui/backends/imgui_impl_dx11.*")
    })
    outputs.file(outputLib)

    val cxxFlags = listOf(
        "-O2",
        "-I${imguiDir.absolutePath}",
        "-I${imguiDir.absolutePath}/backends",
        "-I${cimguiDir.absolutePath}",
        "-I${backendDir.absolutePath}",
    )

    doLast {
        buildDir.mkdirs()
        val objects = mutableListOf<File>()
        for (src in sources) {
            val obj = File(buildDir, src.nameWithoutExtension + ".o")
            logger.lifecycle("Compiling ${src.name}...")
            project.exec {
                commandLine(listOf("g++") + cxxFlags + listOf("-c", src.absolutePath, "-o", obj.absolutePath))
            }
            objects += obj
        }
        logger.lifecycle("Creating static library...")
        project.exec {
            commandLine(listOf("ar", "rcs", outputLib.absolutePath) + objects.map { it.absolutePath })
        }
        logger.lifecycle("Built: ${outputLib.absolutePath}")
    }

    group = "build"
    description = "Build cimgui static library for ImGui + Win32/DX11"
}

// Make cinterop depend on cimgui build
tasks.named("cinteropCimguiMingwX64") {
    dependsOn("buildCimgui")
}

// Make link tasks depend on resource compilation
tasks.named("compileKotlinMingwX64") {
    dependsOn("compileWindowsResources")
    dependsOn("buildCimgui")
}

// ============================================================================
// 7-Zip Download Task
// ============================================================================

val sevenZipVersion = "2501"
val sevenZipDir = file("bin/7zip")

// Task to download 7-Zip binaries from official source
tasks.register("download7zip") {
    group = "setup"
    description = "Download 7-Zip command-line tools from official source"

    val sevenZipExe = file("$sevenZipDir/7z.exe")
    val sevenZipDll = file("$sevenZipDir/7z.dll")

    outputs.files(sevenZipExe, sevenZipDll)

    onlyIf {
        !sevenZipExe.exists() || !sevenZipDll.exists()
    }

    doLast {
        val tempDir = file("build/tmp/7zip-download")
        tempDir.mkdirs()
        sevenZipDir.mkdirs()

        val sevenZrExe = file("$tempDir/7zr.exe")
        val installerExe = file("$tempDir/7z${sevenZipVersion}-x64.exe")

        // Download 7zr.exe (standalone console version that can extract archives)
        println("Downloading 7zr.exe...")
        val sevenZrUrl = "https://www.7-zip.org/a/7zr.exe"
        ant.withGroovyBuilder {
            "get"("src" to sevenZrUrl, "dest" to sevenZrExe, "skipexisting" to "false")
        }

        // Download main 7-Zip installer (can be extracted as an archive)
        println("Downloading 7z${sevenZipVersion}-x64.exe...")
        val installerUrl = "https://www.7-zip.org/a/7z${sevenZipVersion}-x64.exe"
        ant.withGroovyBuilder {
            "get"("src" to installerUrl, "dest" to installerExe, "skipexisting" to "false")
        }

        // Extract 7z.exe and 7z.dll from the installer using 7zr.exe
        println("Extracting 7z.exe and 7z.dll...")
        exec {
            workingDir = tempDir
            commandLine(sevenZrExe.absolutePath, "e", installerExe.absolutePath, "7z.exe", "7z.dll", "-o${sevenZipDir.absolutePath}", "-y")
        }

        // Clean up temp files
        println("Cleaning up...")
        tempDir.deleteRecursively()

        println("7-Zip ${sevenZipVersion} downloaded to ${sevenZipDir.absolutePath}")
    }
}

// Copy 7-Zip dependencies and per-binary side-by-side manifest to each output dir.
// Windows looks for `<exe>.manifest` next to the exe — so qunzip.exe.manifest for
// the CLI binary and QuickUnzip.exe.manifest for the GUI binary.
fun Copy.copyRuntimeResources(intoDir: String, manifestName: String) {
    from("bin/7zip") {
        include("7z.exe", "7z.dll")
    }
    from("src/mingwX64Main/resources") {
        include("qunzip.exe.manifest")
        rename("qunzip.exe.manifest", manifestName)
    }
    into(intoDir)
}

tasks.register<Copy>("copy7zipToCliDebugMingwX64") {
    dependsOn("download7zip", "linkCliDebugExecutableMingwX64")
    copyRuntimeResources("build/bin/mingwX64/cliDebugExecutable", "qunzip.exe.manifest")
}
tasks.register<Copy>("copy7zipToCliReleaseMingwX64") {
    dependsOn("download7zip", "linkCliReleaseExecutableMingwX64")
    copyRuntimeResources("build/bin/mingwX64/cliReleaseExecutable", "qunzip.exe.manifest")
}
tasks.register<Copy>("copy7zipToGuiDebugMingwX64") {
    dependsOn("download7zip", "linkGuiDebugExecutableMingwX64")
    copyRuntimeResources("build/bin/mingwX64/guiDebugExecutable", "QuickUnzip.exe.manifest")
}
tasks.register<Copy>("copy7zipToGuiReleaseMingwX64") {
    dependsOn("download7zip", "linkGuiReleaseExecutableMingwX64")
    copyRuntimeResources("build/bin/mingwX64/guiReleaseExecutable", "QuickUnzip.exe.manifest")
}

tasks.named("linkCliDebugExecutableMingwX64")   { finalizedBy("copy7zipToCliDebugMingwX64") }
tasks.named("linkCliReleaseExecutableMingwX64") { finalizedBy("copy7zipToCliReleaseMingwX64") }
tasks.named("linkGuiDebugExecutableMingwX64")   { finalizedBy("copy7zipToGuiDebugMingwX64") }
tasks.named("linkGuiReleaseExecutableMingwX64") { finalizedBy("copy7zipToGuiReleaseMingwX64") }

// ============================================================================
// Windows Installer Tasks
// ============================================================================

// Task to prepare installer resources (staging directory).
// Both qunzip.exe (CLI) and QuickUnzip.exe (GUI) ship side-by-side.
tasks.register<Copy>("prepareInstallerResources") {
    dependsOn("copy7zipToCliReleaseMingwX64", "copy7zipToGuiReleaseMingwX64")

    from("build/bin/mingwX64/cliReleaseExecutable") {
        include("qunzip.exe", "qunzip.exe.manifest")
    }
    from("build/bin/mingwX64/guiReleaseExecutable") {
        include("QuickUnzip.exe", "QuickUnzip.exe.manifest")
    }
    from("bin/7zip") {
        include("7z.exe", "7z.dll", "License.txt")
    }
    into("build/installer-staging/windows")

    group = "installer"
    description = "Prepare files for Windows installer"
}

// Task to compile Inno Setup script into installer executable
tasks.register<Exec>("buildWindowsInstaller") {
    dependsOn("prepareInstallerResources")

    // Inno Setup compiler location (default installation path)
    val iscc = if (System.getenv("ISCC_PATH") != null) {
        System.getenv("ISCC_PATH")
    } else {
        "C:/Program Files (x86)/Inno Setup 6/ISCC.exe"
    }

    // Create output directory
    doFirst {
        file("build/installer-output").mkdirs()
    }

    commandLine(
        iscc,
        "/O" + file("build/installer-output").absolutePath,
        "/F" + "quick-unzip-setup-${version}",
        file("installer/windows/qunzip.iss").absolutePath
    )

    // Pass version to Inno Setup via environment variable
    environment("QUNZIP_VERSION", version.toString())

    group = "installer"
    description = "Build Windows installer using Inno Setup (requires Inno Setup 6 installed)"

    // Only run on Windows
    onlyIf {
        System.getProperty("os.name").lowercase().contains("windows")
    }
}

// Task to create portable ZIP distribution
tasks.register<Zip>("createPortableZip") {
    dependsOn("prepareInstallerResources")

    from("build/installer-staging/windows") {
        include("qunzip.exe", "QuickUnzip.exe", "7z.exe", "7z.dll", "License.txt")
    }
    from("installer/windows") {
        include("README.txt")
    }

    archiveFileName.set("quick-unzip-${version}-windows-portable.zip")
    destinationDirectory.set(file("build/dist"))

    group = "distribution"
    description = "Create portable ZIP distribution for Windows"
}

// Convenience task to build both installer and portable ZIP
tasks.register("packageWindows") {
    dependsOn("buildWindowsInstaller", "createPortableZip")

    group = "distribution"
    description = "Build both Windows installer and portable ZIP"
}

// Task to clean installer artifacts
tasks.register<Delete>("cleanInstaller") {
    delete("build/installer-staging")
    delete("build/installer-output")
    delete("build/dist")

    group = "installer"
    description = "Clean installer build artifacts"
}
