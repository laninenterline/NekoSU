package com.nekosu.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UiModeTest {
    @Test
    fun persistenceValuesAreStable() {
        assertEquals("material", UiMode.Material.value)
        assertEquals("miuix", UiMode.Miuix.value)
        assertEquals(UiMode.Material, UiMode.DEFAULT)
    }

    @Test
    fun storedValuesRoundTrip() {
        UiMode.entries.forEach { mode ->
            assertEquals(mode, UiMode.fromValue(mode.value))
        }
    }

    @Test
    fun unknownStoredValuesFallBackToMaterial() {
        assertEquals(UiMode.Material, UiMode.fromValue(null))
        assertEquals(UiMode.Material, UiMode.fromValue(""))
        assertEquals(UiMode.Material, UiMode.fromValue("MATERIAL"))
        assertEquals(UiMode.Material, UiMode.fromValue("unknown"))
    }
}
