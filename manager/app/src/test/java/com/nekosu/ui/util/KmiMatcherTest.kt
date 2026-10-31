package com.nekosu.ui.util

import com.nekosu.KernelVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KmiMatcherTest {
    @Test
    fun matchesKnownKernels() {
        assertEquals("android14-6.1", matchKmi(KernelVersion(6, 1, 166))?.kmi)
        assertEquals("android15-6.6", matchKmi(KernelVersion(6, 6, 89))?.kmi)
        assertEquals("android16-6.12", matchKmi(KernelVersion(6, 12, 76))?.kmi)
        assertEquals("android17-6.18", matchKmi(KernelVersion(6, 18, 32))?.kmi)
    }

    @Test
    fun unknownKernelHasNoMatch() {
        assertNull(matchKmi(KernelVersion(5, 10, 0)))
        assertNull(matchKmi(KernelVersion(6, 6, 0).copy(major = 7)))
    }
}
