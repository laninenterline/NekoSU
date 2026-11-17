package com.nekosu.data.susfs

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SusfsAvcLogSpoofingTest {
    private val gson = Gson()

    @Test
    fun avcLogSpoofingIsOptInByDefault() {
        val config = SuSFSConfig.createDefault()
        assertFalse(config.avc_log_spoofing)
        assertEquals(2, config.version)
        assertTrue(config.enabled)
    }

    @Test
    fun avcLogSpoofingSurvivesConfigRoundTrip() {
        val enabled = SuSFSConfig.createDefault().copy(avc_log_spoofing = true)
        val parsed = gson.fromJson(gson.toJson(enabled), SuSFSConfig::class.java)
        assertTrue(parsed.avc_log_spoofing)
        assertEquals(enabled.version, parsed.version)
        assertEquals(enabled.enabled, parsed.enabled)
        assertEquals(enabled.logging, parsed.logging)
    }

    @Test
    fun missingAvcFieldFallsBackToDisabled() {
        val parsed = gson.fromJson(
            """{"version":2,"enabled":true}""",
            SuSFSConfig::class.java,
        )
        assertFalse(parsed.avc_log_spoofing)
    }
}
