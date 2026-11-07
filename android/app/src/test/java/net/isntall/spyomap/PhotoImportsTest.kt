package net.isntall.spyomap

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoImportsTest {
    @Test
    fun unreadablePhotosAreReportedByNameAndTheRestKept() {
        val result = PhotoImports.importAll(
            listOf("a", "cloud", "b"),
            import = { if (it == "cloud") null else it.uppercase() },
            nameOf = { "$it.jpg" },
        )
        assertEquals(listOf("A", "B"), result.imported)
        assertEquals(listOf("cloud.jpg"), result.failed)
    }

    @Test
    fun anImportThatThrowsCountsAsFailed() {
        val result = PhotoImports.importAll(listOf("x"), import = { error("boom") }, nameOf = { "x.jpg" })
        assertEquals(emptyList<String>(), result.imported)
        assertEquals(listOf("x.jpg"), result.failed)
    }

    @Test
    fun aMissingNameFallsBackToPhoto() {
        val result = PhotoImports.importAll(listOf("x"), import = { null }, nameOf = { null })
        assertEquals(listOf("photo"), result.failed)
    }
}
