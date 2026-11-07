package net.isntall.spyomap

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

    fun urisFrom(intent: Intent): List<Uri> = urisFrom(
        intent.action,
        intent.type,
        intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java),
        intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java),
    )
}
