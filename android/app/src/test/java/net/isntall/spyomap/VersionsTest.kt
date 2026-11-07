package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun parsesTagsWithAndWithoutV() {
        assertEquals(Triple(1, 2, 3), Versions.parse("v1.2.3"))
        assertEquals(Triple(0, 10, 0), Versions.parse("0.10.0"))
    }

    @Test
    fun garbageDoesNotParse() {
        assertNull(Versions.parse("garbage"))
        assertNull(Versions.parse("v1.2"))
        assertNull(Versions.parse("v1.2.3-beta"))
        assertNull(Versions.parse(""))
    }

    @Test
    fun versionCodeIsMajorMinorPatch() {
        assertEquals(10203, Versions.code(Triple(1, 2, 3)))
        assertEquals(0, Versions.code(Triple(0, 0, 0)))
    }

    @Test
    fun newerComparesNumerically() {
        assertTrue(Versions.isNewer("v0.2.0", "0.1.9"))
        assertTrue(Versions.isNewer("v0.10.0", "0.9.0"))
        assertTrue(Versions.isNewer("v1.0.0", "0.99.99"))
    }

    @Test
    fun sameOrOlderOrGarbageIsNotNewer() {
        assertFalse(Versions.isNewer("v0.1.0", "0.1.0"))
        assertFalse(Versions.isNewer("v0.1.0", "0.2.0"))
        assertFalse(Versions.isNewer("garbage", "0.1.0"))
        assertFalse(Versions.isNewer("v0.2.0", "1.0"))
    }
}
