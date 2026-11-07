package net.isntall.spyomap

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPolicyTest {
    @Test
    fun siteRootAndHashRoutesAreInApp() {
        assertTrue(LinkPolicy.isInApp("https://spyo-map.isntall.net"))
        assertTrue(LinkPolicy.isInApp("https://spyo-map.isntall.net/"))
        assertTrue(LinkPolicy.isInApp("https://spyo-map.isntall.net/#/upload"))
        assertTrue(LinkPolicy.isInApp("https://spyo-map.isntall.net/#/image/abc"))
    }

    @Test
    fun otherHostsAreNotInApp() {
        assertFalse(LinkPolicy.isInApp("https://spyo-img.isntall.net/images/a.jpg"))
        assertFalse(LinkPolicy.isInApp("https://github.com/mhvelplund/spyo-map"))
        assertFalse(LinkPolicy.isInApp("https://spyo-map.isntall.net.evil.test/"))
        assertFalse(LinkPolicy.isInApp("https://evil.test/?https://spyo-map.isntall.net/"))
    }

    @Test
    fun nonHttpsOrJunkIsNotInApp() {
        assertFalse(LinkPolicy.isInApp("http://spyo-map.isntall.net/"))
        assertFalse(LinkPolicy.isInApp("mailto:someone@example.com"))
        assertFalse(LinkPolicy.isInApp("javascript:alert(1)"))
        assertFalse(LinkPolicy.isInApp(""))
        assertFalse(LinkPolicy.isInApp(null))
    }

    @Test
    fun userInfoTricksAreNotInApp() {
        assertFalse(LinkPolicy.isInApp("https://spyo-map.isntall.net@evil.test/"))
    }
}
