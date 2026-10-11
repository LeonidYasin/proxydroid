package org.proxydroid.utils

import org.junit.Assert.*
import org.junit.Test

class NativeRuntimeHealthTest {
    @Test fun malformedSnapshotsCannotClaimHealthyOrStopped() {
        assertNull(NativeRuntimeHealth.parse(longArrayOf()))
        assertNull(NativeRuntimeHealth.parse(LongArray(16)))
        assertNull(NativeRuntimeHealth.parse(LongArray(17).apply { this[0] = 9 }))
    }
    @Test fun activeLoopCountersAreDistinctFromEndToEndInternet() {
        val values = LongArray(17).apply { this[0] = 1; this[2] = 1000; this[3] = 12; this[10] = 4 }
        val h = NativeRuntimeHealth.parse(values)!!
        assertTrue(h.running); assertFalse(h.taskFinished)
        values[0] = 0
        assertTrue("Immutable copied observation", h.running)
        val text = h.lines(1100).joinToString("\n")
        assertTrue(text.contains("TUN rx packets=12")); assertTrue(text.contains("unsupported UDP/non-DNS packets=4"))
        assertTrue(text.contains("NOT forwarding")); assertTrue(text.contains("100"))
    }
    @Test fun finishedNativeTaskAndCoreExitAreVisibleEvenIfJavaFlagIsStale() {
        val h = NativeRuntimeHealth.parse(LongArray(17).apply { this[1] = 1; this[15] = 2 })!!
        assertFalse(h.running); assertTrue(h.taskFinished); assertEquals(2L, h.coreExitCode)
    }
    @Test fun startFailureIsNotAutomaticallyBlamedOnProxyReachability() {
        assertTrue(NativeRuntimeHealth.startFailure(-10).contains("NATIVE_ALREADY_RUNNING"))
        assertTrue(NativeRuntimeHealth.startFailure(-13).contains("NOT proof"))
        assertTrue(NativeRuntimeHealth.startFailure(-2).contains("PANIC"))
        assertTrue(NativeRuntimeHealth.startFailure(-123).contains("rc=-123"))
    }
}
