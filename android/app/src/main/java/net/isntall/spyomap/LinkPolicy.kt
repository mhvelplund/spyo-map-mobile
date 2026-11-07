package net.isntall.spyomap

import java.net.URI

object LinkPolicy {
    const val SITE_HOST = "spyo-map.isntall.net"
    const val SITE_URL = "https://$SITE_HOST"
    const val UPLOAD_URL = "$SITE_URL/#/upload"

    /** True only for https URLs on the site itself; everything else belongs in another app. */
    fun isInApp(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            return false
        }
        return uri.scheme == "https" && uri.rawUserInfo == null && uri.host == SITE_HOST &&
            (uri.port == -1 || uri.port == 443)
    }
}
