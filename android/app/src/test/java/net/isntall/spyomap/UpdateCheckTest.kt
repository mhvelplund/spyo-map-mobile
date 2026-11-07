package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckTest {
    private fun release(tag: String, vararg assets: String) = """
        {"tag_name": "$tag", "assets": [${assets.joinToString(",") { """{"name": "${it.substringAfterLast('/')}", "browser_download_url": "$it"}""" }}]}
    """.trimIndent()

    private val apk = "https://github.com/mhvelplund/spyo-map-mobile/releases/download/v0.2.0/spyo-map-v0.2.0.apk"

    @Test
    fun aNewerReleaseWithAnApkIsAnUpdate() {
        assertEquals(Update("v0.2.0", apk), UpdateCheck.pickUpdate(release("v0.2.0", "https://x/notes.txt", apk), "0.1.0"))
    }

    @Test
    fun sameVersionIsNoUpdate() {
        assertNull(UpdateCheck.pickUpdate(release("v0.1.0", apk), "0.1.0"))
    }

    @Test
    fun aReleaseWithoutAnApkIsNoUpdate() {
        assertNull(UpdateCheck.pickUpdate(release("v0.2.0", "https://x/notes.txt"), "0.1.0"))
    }

    @Test
    fun malformedJsonIsNoUpdate() {
        assertNull(UpdateCheck.pickUpdate("not json", "0.1.0"))
        assertNull(UpdateCheck.pickUpdate("""{"message": "Not Found"}""", "0.1.0"))
    }

    @Test
    fun aDevBuildNeverPrompts() {
        assertNull(UpdateCheck.pickUpdate(release("v0.2.0", apk), "0.0.0-dev"))
    }
}
