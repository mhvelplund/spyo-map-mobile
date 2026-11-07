package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareIntentsTest {
    private val send = "android.intent.action.SEND"
    private val sendMultiple = "android.intent.action.SEND_MULTIPLE"

    @Test
    fun sendWithAnImageReturnsTheStream() {
        assertEquals(listOf("a"), ShareIntents.urisFrom(send, "image/jpeg", "a", null))
    }

    @Test
    fun sendMultipleReturnsAllStreams() {
        assertEquals(listOf("a", "b"), ShareIntents.urisFrom(sendMultiple, "image/*", null, listOf("a", "b")))
    }

    @Test
    fun nonImageTypeIsIgnored() {
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(send, "text/plain", "a", null))
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(sendMultiple, "video/mp4", null, listOf("a")))
    }

    @Test
    fun otherActionsAreIgnored() {
        assertEquals(emptyList<String>(), ShareIntents.urisFrom("android.intent.action.MAIN", "image/jpeg", "a", listOf("b")))
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(null, "image/jpeg", "a", null))
    }

    @Test
    fun nullsGiveAnEmptyList() {
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(send, null, "a", null))
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(send, "image/jpeg", null, null))
        assertEquals(emptyList<String>(), ShareIntents.urisFrom(sendMultiple, "image/jpeg", null, null))
    }

    @Test
    fun duplicateStreamsInOneIntentAreDropped() {
        assertEquals(listOf("a", "b"), ShareIntents.urisFrom(sendMultiple, "image/*", null, listOf("a", "b", "a")))
    }

    private val own = "net.isntall.spyomap.fileprovider"

    @Test
    fun contentUrisFromOtherAppsAreAccepted() {
        assertTrue(ShareIntents.isAcceptable("content", "com.google.android.apps.photos.contentprovider", own))
        assertTrue(ShareIntents.isAcceptable("content", "media", own))
    }

    @Test
    fun fileUrisAndOurOwnProviderAreRejected() {
        assertFalse(ShareIntents.isAcceptable("file", null, own))
        assertFalse(ShareIntents.isAcceptable("file", "", own))
        assertFalse(ShareIntents.isAcceptable("content", own, own))
        assertFalse(ShareIntents.isAcceptable("content", null, own))
        assertFalse(ShareIntents.isAcceptable("https", "evil.test", own))
        assertFalse(ShareIntents.isAcceptable(null, "media", own))
    }
}
