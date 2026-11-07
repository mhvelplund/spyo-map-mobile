package net.isntall.spyomap

data class ImportResult<P>(val imported: List<P>, val failed: List<String>)

object PhotoImports {
    /** Imports each item; ones that can't be read are reported by name so the page can tell the user. */
    fun <T, P> importAll(items: List<T>, import: (T) -> P?, nameOf: (T) -> String?): ImportResult<P> {
        val imported = mutableListOf<P>()
        val failed = mutableListOf<String>()
        for (item in items) {
            val photo = try {
                import(item)
            } catch (_: Exception) {
                null
            }
            if (photo != null) imported += photo else failed += (nameOf(item) ?: "photo")
        }
        return ImportResult(imported, failed)
    }
}
