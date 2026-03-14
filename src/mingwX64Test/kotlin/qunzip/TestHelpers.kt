package qunzip

import kotlinx.cinterop.*
import platform.posix.*
import platform.windows.*

/**
 * Result of launching an external process.
 */
data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val timedOut: Boolean
)

/**
 * Launch a process, capture stdout, and wait for it to finish or time out.
 *
 * Drains stdout concurrently by polling with short waits, so the pipe buffer
 * doesn't fill up and block the child process (important for Mosaic TUI output).
 */
@OptIn(ExperimentalForeignApi::class)
fun executeProcess(
    command: String,
    timeoutMillis: UInt = 30_000u,
    workingDirectory: String? = null
): ProcessResult = memScoped {
    val securityAttrs = alloc<SECURITY_ATTRIBUTES>()
    securityAttrs.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
    securityAttrs.bInheritHandle = TRUE
    securityAttrs.lpSecurityDescriptor = null

    val stdoutReadHandle = alloc<HANDLEVar>()
    val stdoutWriteHandle = alloc<HANDLEVar>()

    if (CreatePipe(stdoutReadHandle.ptr, stdoutWriteHandle.ptr, securityAttrs.ptr, 0u) == 0) {
        error("Failed to create pipe: ${GetLastError()}")
    }
    SetHandleInformation(stdoutReadHandle.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)

    val startupInfo = alloc<STARTUPINFOW>()
    val processInfo = alloc<PROCESS_INFORMATION>()

    startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
    startupInfo.dwFlags = (STARTF_USESHOWWINDOW or STARTF_USESTDHANDLES).toUInt()
    startupInfo.wShowWindow = SW_HIDE.toUShort()
    startupInfo.hStdOutput = stdoutWriteHandle.value
    startupInfo.hStdError = stdoutWriteHandle.value
    startupInfo.hStdInput = null

    val success = CreateProcessW(
        lpApplicationName = null,
        lpCommandLine = command.wcstr.ptr,
        lpProcessAttributes = null,
        lpThreadAttributes = null,
        bInheritHandles = TRUE,
        dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
        lpEnvironment = null,
        lpCurrentDirectory = workingDirectory,
        lpStartupInfo = startupInfo.ptr,
        lpProcessInformation = processInfo.ptr
    )

    // Close write end in parent so reads return EOF when child exits
    CloseHandle(stdoutWriteHandle.value)

    if (success == 0) {
        CloseHandle(stdoutReadHandle.value)
        error("Failed to create process: ${GetLastError()}")
    }

    // Poll: wait briefly for process, drain pipe, repeat
    val output = StringBuilder()
    val buffer = allocArray<ByteVar>(8192)
    val bytesRead = alloc<UIntVar>()
    val bytesAvail = alloc<UIntVar>()
    var timedOut = false
    val startTime = GetTickCount64()
    var processExited = false

    while (!processExited) {
        // Drain all available pipe data
        while (true) {
            if (PeekNamedPipe(stdoutReadHandle.value, null, 0u, null, bytesAvail.ptr, null) == 0) break
            if (bytesAvail.value == 0u) break
            val toRead = minOf(bytesAvail.value, 8191u)
            val readOk = ReadFile(stdoutReadHandle.value, buffer, toRead, bytesRead.ptr, null)
            if (readOk == 0 || bytesRead.value == 0u) break
            for (i in 0 until bytesRead.value.toInt()) {
                val b = buffer[i]
                if (b != 0.toByte()) output.append(b.toInt().toChar())
            }
        }

        // Check if process has exited
        val waitResult = WaitForSingleObject(processInfo.hProcess, 100u)
        if (waitResult == 0u) { // WAIT_OBJECT_0
            processExited = true
        }

        // Check timeout
        val elapsed = GetTickCount64() - startTime
        if (elapsed >= timeoutMillis.toULong()) {
            timedOut = true
            TerminateProcess(processInfo.hProcess, 1u)
            WaitForSingleObject(processInfo.hProcess, 5000u)
            processExited = true
        }
    }

    // Final drain after process exits
    while (true) {
        if (PeekNamedPipe(stdoutReadHandle.value, null, 0u, null, bytesAvail.ptr, null) == 0) break
        if (bytesAvail.value == 0u) break
        val readOk = ReadFile(stdoutReadHandle.value, buffer, 8191u, bytesRead.ptr, null)
        if (readOk == 0 || bytesRead.value == 0u) break
        for (i in 0 until bytesRead.value.toInt()) {
            val b = buffer[i]
            if (b != 0.toByte()) output.append(b.toInt().toChar())
        }
    }

    val exitCode = alloc<UIntVar>()
    GetExitCodeProcess(processInfo.hProcess, exitCode.ptr)

    CloseHandle(processInfo.hProcess)
    CloseHandle(processInfo.hThread)
    CloseHandle(stdoutReadHandle.value)

    ProcessResult(
        exitCode = exitCode.value.toInt(),
        stdout = output.toString(),
        timedOut = timedOut
    )
}

/**
 * Get the project root directory (Gradle sets CWD to project root for native tests).
 */
@OptIn(ExperimentalForeignApi::class)
fun getProjectRoot(): String = memScoped {
    val buffer = allocArray<ByteVar>(MAX_PATH)
    GetCurrentDirectoryA(MAX_PATH.toUInt(), buffer)
    buffer.toKString()
}

/**
 * Get path to a test fixture in src/mingwX64Test/resources/fixtures/.
 */
fun getFixturePath(name: String): String {
    return "${getProjectRoot()}\\src\\mingwX64Test\\resources\\fixtures\\$name"
}

/**
 * Get path to the debug executable.
 */
fun getExecutablePath(): String {
    return "${getProjectRoot()}\\build\\bin\\mingwX64\\debugExecutable\\qunzip.exe"
}

/**
 * Get path to the bundled 7zip executable.
 */
fun get7zipPath(): String {
    return "${getProjectRoot()}\\bin\\7zip\\7z.exe"
}

/**
 * Create a temporary directory for test isolation.
 */
@OptIn(ExperimentalForeignApi::class)
fun createTestTempDir(prefix: String = "qunzip-test"): String = memScoped {
    val tempPath = allocArray<ByteVar>(MAX_PATH)
    GetTempPathA(MAX_PATH.toUInt(), tempPath)
    val base = tempPath.toKString()

    val dirName = "${prefix}-${kotlin.random.Random.nextLong().toULong()}"
    val fullPath = "$base$dirName"
    mkdir(fullPath)
    fullPath
}

/**
 * Recursively delete a directory.
 */
@OptIn(ExperimentalForeignApi::class)
fun deleteRecursive(path: String) {
    memScoped {
        val command = "cmd /c rd /s /q \"$path\""
        val startupInfo = alloc<STARTUPINFOW>()
        val processInfo = alloc<PROCESS_INFORMATION>()
        startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
        startupInfo.dwFlags = STARTF_USESHOWWINDOW.toUInt()
        startupInfo.wShowWindow = SW_HIDE.toUShort()

        val ok = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = command.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = FALSE,
            dwCreationFlags = CREATE_NO_WINDOW.toUInt(),
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = startupInfo.ptr,
            lpProcessInformation = processInfo.ptr
        )
        if (ok != 0) {
            WaitForSingleObject(processInfo.hProcess, 10_000u)
            CloseHandle(processInfo.hProcess)
            CloseHandle(processInfo.hThread)
        }
    }
}

/**
 * Check if a file exists at the given path.
 */
fun fileExistsAt(path: String): Boolean {
    return access(path, F_OK) == 0
}

/**
 * Copy a file from src to dst.
 */
@OptIn(ExperimentalForeignApi::class)
fun copyFile(src: String, dst: String): Boolean {
    return CopyFileA(src, dst, FALSE) != 0
}
