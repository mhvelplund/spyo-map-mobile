package net.isntall.spyomap

import android.content.Context
import android.content.Intent
import android.net.Uri

object ShareIntents {
    /** The shared image streams of a SEND / SEND_MULTIPLE intent, de-duplicated; empty for anything else. */
    fun <T> urisFrom(action: String?, type: String?, single: T?, multiple: List<T>?): List<T> {
        if (type == null || !type.startsWith("image/")) return emptyList()
        return when (action) {
            Intent.ACTION_SEND -> listOfNotNull(single)
            Intent.ACTION_SEND_MULTIPLE -> multiple.orEmpty().distinct()
            else -> emptyList()
        }
    }

    /**
     * MainActivity is exported, so any app can send it a share. Only content:// URIs from other apps are read:
     * file:// or our own FileProvider would let a caller make us copy our private files where the page can read them.
     */
    fun isAcceptable(scheme: String?, authority: String?, ownAuthority: String): Boolean =
        scheme == "content" && !authority.isNullOrEmpty() && authority != ownAuthority

    fun isAcceptable(uri: Uri, context: Context): Boolean =
        isAcceptable(uri.scheme, uri.authority, ownAuthority(context))

    fun ownAuthority(context: Context) = "${context.packageName}.fileprovider"

    fun urisFrom(intent: Intent, context: Context): List<Uri> = urisFrom(
        intent.action,
        intent.type,
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java),
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java),
    ).filter { isAcceptable(it, context) }
}
