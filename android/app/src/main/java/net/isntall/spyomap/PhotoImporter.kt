package net.isntall.spyomap

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.util.UUID

/** A photo copied into the app's cache, as described to the page (see src/definitions.ts). */
data class ImportedPhoto(val name: String, val mimeType: String, val size: Long, val file: File)

/**
 * Copies picked or shared photos into cache/picked so the page can fetch them through Capacitor's file server.
 * Opens the original bytes (GPS intact) where possible: MediaStore.setRequireOriginal for media documents, which
 * needs ACCESS_MEDIA_LOCATION; other providers (Files, Google Photos) are read directly.
 */
class PhotoImporter(private val context: Context) {
    private val dir = File(context.cacheDir, "picked")

    /** Null when the URI can't be read (revoked grant, cloud-only item that fails to download, ...). */
    fun import(uri: Uri): ImportedPhoto? {
        if (!ShareIntents.isAcceptable(uri, context)) return null
        val resolver = context.contentResolver
        val name = displayName(uri) ?: "photo.jpg"
        val mimeType = resolver.getType(uri) ?: "image/jpeg"
        return try {
            dir.mkdirs()
            val target = File(dir, "${UUID.randomUUID()}-${name.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
            val size = openOriginal(uri).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            ImportedPhoto(name, mimeType, size, target)
        } catch (_: Exception) {
            null
        }
    }

    /** Removes copies older than a day; the page reads them right after picking. */
    fun cleanUp(nowMillis: Long = System.currentTimeMillis()) {
        dir.listFiles()?.filter { nowMillis - it.lastModified() > DAY_MILLIS }?.forEach { it.delete() }
    }

    private fun openOriginal(uri: Uri): InputStream {
        val resolver = context.contentResolver
        try {
            val media = if (uri.authority == MediaStore.AUTHORITY) uri else MediaStore.getMediaUri(context, uri)
            if (media != null) {
                resolver.openInputStream(MediaStore.setRequireOriginal(media))?.let { return it }
            }
        } catch (_: Exception) {
            // Not a MediaStore item, or no permission for the original: fall back to the URI as given.
        }
        return resolver.openInputStream(uri) ?: throw IllegalStateException("Cannot open $uri")
    }

    fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
