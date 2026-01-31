plugins {
    kotlin("multiplatform") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    kotlin("plugin.compose") version "2.2.20"
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
    macosX64 {
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
        binaries {
            executable {
                baseName = "qunzip"
                entryPoint = "qunzip.main"
                // Use Windows subsystem for release builds (no console window)
                if (buildType == org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE) {
                    linkerOpts("-Wl,--subsystem,windows")
                }
                // Link the compiled Windows resource file (contains icon and version info)
                // The resource file is compiled by the compileWindowsResources task
                linkerOpts(file("build/resources/qunzip.res").absolutePath)
            }
        }
    }

    // The Default Kotlin Hierarchy Template automatically creates intermediate source sets
    // (nativeMain, appleMain, linuxMain, etc.) and sets up the dependency hierarchy.
    // No manual sourceSets configuration needed!

    sourceSets {
        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                implementation("co.touchlab:kermit:2.0.3") // Logging

                // Mosaic for TUI
                implementation("com.jakewharton.mosaic:mosaic-runtime:0.18.0")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
                implementation("app.cash.turbine:turbine:1.0.0") // Flow testing
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

// Test tasks for all platforms
tasks.register("testAll") {
    dependsOn("allTests")
    group = "verification"
    description = "Run tests on all platforms"
}

// Build tasks for all platforms
tasks.register("buildAll") {
    dependsOn(
        "linkDebugExecutableMacosArm64",
        "linkDebugExecutableMacosX64",
        "linkDebugExecutableLinuxX64",
        "linkDebugExecutableLinuxArm64",
        "linkDebugExecutableMingwX64"
    )
    group = "build"
    description = "Build debug executables for all platforms"
}

tasks.register("buildAllRelease") {
    dependsOn(
        "linkReleaseExecutableMacosArm64",
        "linkReleaseExecutableMacosX64",
        "linkReleaseExecutableLinuxX64",
        "linkReleaseExecutableLinuxArm64",
        "linkReleaseExecutableMingwX64"
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

// Make link tasks depend on resource compilation
tasks.named("compileKotlinMingwX64") {
    dependsOn("compileWindowsResources")
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

// Copy 7-Zip dependencies and manifest to Windows build directories
tasks.register<Copy>("copy7zipToDebugMingwX64") {
    dependsOn("download7zip")
    from("bin/7zip") {
        include("7z.exe", "7z.dll")
    }
    from("src/mingwX64Main/resources") {
        include("qunzip.exe.manifest")
    }
    into("build/bin/mingwX64/debugExecutable")
    dependsOn("linkDebugExecutableMingwX64")
}

tasks.register<Copy>("copy7zipToReleaseMingwX64") {
    dependsOn("download7zip")
    from("bin/7zip") {
        include("7z.exe", "7z.dll")
    }
    from("src/mingwX64Main/resources") {
        include("qunzip.exe.manifest")
    }
    into("build/bin/mingwX64/releaseExecutable")
    dependsOn("linkReleaseExecutableMingwX64")
}

// Make link tasks depend on copying 7zip files
tasks.named("linkDebugExecutableMingwX64") {
    finalizedBy("copy7zipToDebugMingwX64")
}

tasks.named("linkReleaseExecutableMingwX64") {
    finalizedBy("copy7zipToReleaseMingwX64")
}

// ============================================================================
// Windows Installer Tasks
// ============================================================================

// Task to prepare installer resources (staging directory)
tasks.register<Copy>("prepareInstallerResources") {
    dependsOn("copy7zipToReleaseMingwX64")

    from("build/bin/mingwX64/releaseExecutable") {
        include("qunzip.exe", "qunzip.exe.manifest", "7z.exe", "7z.dll")
    }
    from("bin/7zip") {
        include("License.txt")
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
        "/F" + "qunzip-setup-${version}",
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
        include("qunzip.exe", "7z.exe", "7z.dll", "License.txt")
    }
    from("installer/windows") {
        include("README.txt")
    }

    archiveFileName.set("qunzip-${version}-windows-portable.zip")
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
