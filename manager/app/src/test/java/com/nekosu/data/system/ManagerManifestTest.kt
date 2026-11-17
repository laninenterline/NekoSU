package com.nekosu.data.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagerManifestTest {
    @Test
    fun acceptsWellFormedManagerPackages() {
        assertTrue(ManagerManifest.validPackage("com.nekosu"))
        assertTrue(ManagerManifest.validPackage("com.nekosu.hidden"))
        assertTrue(ManagerManifest.validPackage("a.b"))
        assertTrue(ManagerManifest.validPackage("com.example_app.module_2"))
    }

    @Test
    fun rejectsMalformedManagerPackages() {
        listOf(
            "",
            "ab",
            "abc",
            "NekoSU",
            "com.nekosu.",
            "com..nekosu",
            ".com.nekosu",
            "com.Nekosu",
            "com.nekosu/Hidden",
            "com.nekosu space",
            "1com.nekosu",
            "com.9nekosu",
        ).forEach { value ->
            assertFalse("expected invalid package: $value", ManagerManifest.validPackage(value))
        }
        assertFalse(ManagerManifest.validPackage("com." + "a".repeat(200)))
    }

    @Test
    fun trimsAndValidatesHiddenAppNames() {
        assertEquals("Notes", ManagerAppearance.validateName("  Notes  "))
        assertEquals("便签", ManagerAppearance.validateName("便签"))
        assertEquals("Notes", ManagerAppearance.validateHiddenName(" Notes "))
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance.validateName("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance.validateName("a".repeat(41))
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance.validateName("line\nbreak")
        }
    }

    @Test
    fun hiddenNamesRejectIdentityKeywords() {
        assertTrue(ManagerAppearance.isHiddenTextAllowed("Notes"))
        assertTrue(ManagerAppearance.isHiddenTextAllowed("便签"))
        assertFalse(ManagerAppearance.isHiddenTextAllowed("MySU"))
        assertFalse(ManagerAppearance.isHiddenTextAllowed("SuperUser"))
        assertFalse(ManagerAppearance.isHiddenTextAllowed("studio"))
        assertFalse(ManagerAppearance.isHiddenTextAllowed("Manager"))
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance.validateHiddenName("MySU")
        }
    }

    @Test
    fun appearanceRejectsUnsafeIconEntries() {
        ManagerAppearance("Notes", "res/drawable/ic_launcher.png")
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance("Notes", "../icon.png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance("Notes", "res/../icon.png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance("Notes", "res\\drawable\\icon.png")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerAppearance("   ", null)
        }
    }
}
