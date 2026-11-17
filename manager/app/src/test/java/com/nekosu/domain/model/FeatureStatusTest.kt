package com.nekosu.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureStatusTest {
    @Test
    fun avcFeatureIsUnknownUntilKernelReportsIt() {
        val status = PlatformFeatureStatus()
        assertEquals("", status.avcSpoofStatus)
        assertNull(status.avcSpoofValue)
    }

    @Test
    fun avcFeatureStateTracksKernelReportedValue() {
        val supported = PlatformFeatureStatus(avcSpoofStatus = "supported", avcSpoofValue = 1L)
        assertEquals("supported", supported.avcSpoofStatus)
        assertEquals(1L, supported.avcSpoofValue)

        val forcedOff = supported.copy(avcSpoofStatus = "forced", avcSpoofValue = 0L)
        assertEquals("forced", forcedOff.avcSpoofStatus)
        assertEquals(0L, forcedOff.avcSpoofValue)
    }
}
