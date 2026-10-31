package com.nekosu.ui.util

import com.nekosu.KernelVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KmiMatcherTest {
    @Test
    fun matchesKnownSixXKernels() {
        assertEquals("android14-6.1", matchKmi(KernelVersion(6, 1, 166))?.kmi)
        assertEquals("android15-6.6", matchKmi(KernelVersion(6, 6, 89))?.kmi)
        assertEquals("android16-6.12", matchKmi(KernelVersion(6, 12, 76))?.kmi)
        assertEquals("android17-6.18", matchKmi(KernelVersion(6, 18, 32))?.kmi)
    }

    @Test
    fun matchesFiveSeriesByReleaseTag() {
        assertEquals("android12-5.10", matchKmi(KernelVersion(5, 10, 252), "5.10.252-android12-9-gabc", 32)?.kmi)
        assertEquals("android13-5.10", matchKmi(KernelVersion(5, 10, 245), "5.10.245-android13-8-gabc", 33)?.kmi)
        assertEquals("android13-5.15", matchKmi(KernelVersion(5, 15, 202), "5.15.202-android13-8-gabc", 33)?.kmi)
        assertEquals("android14-5.15", matchKmi(KernelVersion(5, 15, 202), "5.15.202-android14-8-gabc", 34)?.kmi)
    }

    @Test
    fun fallsBackToSdkWhenReleaseHasNoTag() {
        assertEquals("android13-5.10", matchKmi(KernelVersion(5, 10, 245), "5.10.245", 33)?.kmi)
        assertEquals("android14-5.15", matchKmi(KernelVersion(5, 15, 202), "5.15.202", 34)?.kmi)
    }

    @Test
    fun unknownKernelHasNoMatch() {
        assertNull(matchKmi(KernelVersion(5, 4, 0)))
        assertNull(matchKmi(KernelVersion(6, 6, 0).copy(major = 7)))
    }
}
