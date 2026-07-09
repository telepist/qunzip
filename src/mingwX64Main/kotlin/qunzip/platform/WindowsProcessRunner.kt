@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package qunzip.platform

import qunzip.data.process.ProcessRunner
import qunzip.util.buildWindowsCommandLine
import kotlinx.cinterop.*
import platform.windows.*
import co.touchlab.kermit.Logger

/**
 * The single place that spawns child processes on Windows. Consolidates the
 * previously-duplicated CreateProcessW + pipe + read-loop + job-object machinery
 * into one implementation: hidden console, stdout/stderr captured via a pipe,
 * the child placed in a kill-on-close job object (so it's terminated if we exit),
 * a non-blocking poll loop that honours cancellation, and UTF-8 per-line decoding
 * that survives multi-byte sequences straddling read boundaries.
 */
class WindowsProcessRunner(
    private val logger: Logger = Logger.withTag("WindowsProcessRunner"),
) : ProcessRunner {

    override fun run(
        program: String,
        args: List<String>,
        shouldContinue: () -> Boolean,
        onStdoutLine: (String) -> Unit,
    ): Int = memScoped {
        val command = buildWindowsCommandLine(program, args)
        logger.d { "Running: $command" }

        val securityAttrs = alloc<SECURITY_ATTRIBUTES>()
        securityAttrs.nLength = sizeOf<SECURITY_ATTRIBUTES>().toUInt()
        securityAttrs.bInheritHandle = TRUE
        securityAttrs.lpSecurityDescriptor = null

        val stdoutReadHandle = alloc<HANDLEVar>()
        val stdoutWriteHandle = alloc<HANDLEVar>()
        if (CreatePipe(stdoutReadHandle.ptr, stdoutWriteHandle.ptr, securityAttrs.ptr, 0u) == 0) {
            logger.e { "Failed to create stdout pipe: ${GetLastError()}" }
            return@memScoped -1
        }
        SetHandleInformation(stdoutReadHandle.value, HANDLE_FLAG_INHERIT.toUInt(), 0u)

        // Kill-on-close job object: if we exit while the child runs (window closed,
        // crash, exitProcess) Windows closes the job handle and terminates it, so
        // it can't keep running orphaned. Create suspended, assign, then resume.
        val job = CreateJobObjectW(null, null)
        if (job != null) {
            val jobInfo = alloc<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>()
            jobInfo.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE.toUInt()
            // JobObjectExtendedLimitInformation (9) — K/N maps JOBOBJECTINFOCLASS to UInt.
            if (SetInformationJobObject(job, 9u, jobInfo.ptr, sizeOf<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>().toUInt()) == 0) {
                logger.w { "SetInformationJobObject(kill-on-close) failed: ${GetLastError()}" }
            }
        }

        val startupInfo = alloc<STARTUPINFOW>()
        val processInfo = alloc<PROCESS_INFORMATION>()
        startupInfo.cb = sizeOf<STARTUPINFOW>().toUInt()
        startupInfo.dwFlags = (STARTF_USESHOWWINDOW or STARTF_USESTDHANDLES).toUInt()
        startupInfo.wShowWindow = SW_HIDE.toUShort()
        startupInfo.hStdOutput = stdoutWriteHandle.value
        startupInfo.hStdError = stdoutWriteHandle.value
        startupInfo.hStdInput = null

        val creationFlags = if (job != null) (CREATE_NO_WINDOW or CREATE_SUSPENDED).toUInt() else CREATE_NO_WINDOW.toUInt()
        val success = CreateProcessW(
            lpApplicationName = null,
            lpCommandLine = command.wcstr.ptr,
            lpProcessAttributes = null,
            lpThreadAttributes = null,
            bInheritHandles = TRUE,
            dwCreationFlags = creationFlags,
            lpEnvironment = null,
            lpCurrentDirectory = null,
            lpStartupInfo = startupInfo.ptr,
            lpProcessInformation = processInfo.ptr,
        )
        CloseHandle(stdoutWriteHandle.value)
        if (success == 0) {
            job?.let { CloseHandle(it) }
            CloseHandle(stdoutReadHandle.value)
            logger.e { "Failed to create process: ${GetLastError()}" }
            return@memScoped -1
        }
        if (job != null) {
            AssignProcessToJobObject(job, processInfo.hProcess)
            ResumeThread(processInfo.hThread)
        }

        // Accumulate bytes and emit each complete line (split on CR or LF) as a
        // trimmed, non-empty, UTF-8-decoded string.
        val buffer = allocArray<ByteVar>(4096)
        val bytesRead = alloc<UIntVar>()
        val bytesAvail = alloc<UIntVar>()
        val lineBytes = ArrayList<Byte>()

        fun flushLine() {
            if (lineBytes.isEmpty()) return
            val line = lineBytes.toByteArray().decodeToString().trim()
            lineBytes.clear()
            if (line.isNotEmpty()) onStdoutLine(line)
        }

        fun consume(n: Int) {
            for (i in 0 until n) {
                val b = buffer[i]
                when {
                    b == '\r'.code.toByte() || b == '\n'.code.toByte() -> flushLine()
                    b != 0.toByte() -> lineBytes.add(b)
                }
            }
        }

        fun drainAvailable() {
            while (true) {
                if (PeekNamedPipe(stdoutReadHandle.value, null, 0u, null, bytesAvail.ptr, null) == 0) break
                if (bytesAvail.value == 0u) break
                val toRead = minOf(bytesAvail.value, 4095u)
                if (ReadFile(stdoutReadHandle.value, buffer, toRead, bytesRead.ptr, null) == 0 || bytesRead.value == 0u) break
                consume(bytesRead.value.toInt())
            }
        }

        var processExited = false
        var cancelled = false
        while (!processExited) {
            drainAvailable()
            if (!shouldContinue()) {
                logger.i { "Cancelled — terminating child process" }
                TerminateProcess(processInfo.hProcess, 1u)
                cancelled = true
                WaitForSingleObject(processInfo.hProcess, 5000u)
                break
            }
            if (WaitForSingleObject(processInfo.hProcess, 100u) == 0u /* WAIT_OBJECT_0 */) {
                processExited = true
            }
        }
        drainAvailable()
        flushLine()

        val exitCode = alloc<UIntVar>()
        GetExitCodeProcess(processInfo.hProcess, exitCode.ptr)
        CloseHandle(processInfo.hProcess)
        CloseHandle(processInfo.hThread)
        CloseHandle(stdoutReadHandle.value)
        job?.let { CloseHandle(it) }

        return@memScoped if (cancelled) 1 else exitCode.value.toInt()
    }
}
