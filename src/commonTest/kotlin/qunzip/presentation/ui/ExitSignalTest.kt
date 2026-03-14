package qunzip.presentation.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ExitSignalTest {

    @Test
    fun `await suspends until signal is called`() = runTest {
        val exitSignal = ExitSignal()
        var awaited = false

        val job = launch {
            exitSignal.await()
            awaited = true
        }

        assertFalse(awaited, "await should suspend until signaled")

        exitSignal.signal()
        job.join()

        assertTrue(awaited, "await should complete after signal")
    }

    @Test
    fun `signal before await completes immediately`() = runTest {
        val exitSignal = ExitSignal()

        exitSignal.signal()

        var awaited = false
        val job = launch {
            exitSignal.await()
            awaited = true
        }
        job.join()

        assertTrue(awaited, "await should complete immediately when already signaled")
    }

    @Test
    fun `multiple signals are idempotent`() = runTest {
        val exitSignal = ExitSignal()

        exitSignal.signal()
        exitSignal.signal() // should not throw

        var awaited = false
        val job = launch {
            exitSignal.await()
            awaited = true
        }
        job.join()

        assertTrue(awaited)
    }

    @Test
    fun `multiple awaiters all complete on signal`() = runTest {
        val exitSignal = ExitSignal()
        var count = 0

        val job1 = launch { exitSignal.await(); count++ }
        val job2 = launch { exitSignal.await(); count++ }

        exitSignal.signal()
        job1.join()
        job2.join()

        assertEquals(2, count, "All awaiters should complete")
    }
}
