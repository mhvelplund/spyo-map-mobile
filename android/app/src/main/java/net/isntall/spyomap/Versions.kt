package net.isntall.spyomap

/** Release versions are plain `vMAJOR.MINOR.PATCH` git tags. Keep `code` in sync with android/app/build.gradle. */
object Versions {
    private val pattern = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    fun parse(tag: String): Triple<Int, Int, Int>? =
        pattern.matchEntire(tag)?.destructured?.let { (a, b, c) -> Triple(a.toInt(), b.toInt(), c.toInt()) }

    fun code(v: Triple<Int, Int, Int>): Int = v.first * 10000 + v.second * 100 + v.third

    fun isNewer(latestTag: String, current: String): Boolean {
        val latest = parse(latestTag) ?: return false
        val installed = parse(current) ?: return false
        return code(latest) > code(installed)
    }
}
