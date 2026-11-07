package net.isntall.spyomap

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class Update(val tag: String, val apkUrl: String)

/** On start, offers the newest GitHub Release's APK if it is newer than the installed version. */
object UpdateCheck {
    private const val LATEST = "https://api.github.com/repos/mhvelplund/spyo-map-mobile/releases/latest"

    fun pickUpdate(json: String, currentVersion: String): Update? = try {
        val release = JSONObject(json)
        val tag = release.getString("tag_name")
        val assets = release.getJSONArray("assets")
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }
            ?.getString("browser_download_url")
        if (apk != null && Versions.isNewer(tag, currentVersion)) Update(tag, apk) else null
    } catch (_: Exception) {
        null
    }

    /** Fire and forget; any network or parsing failure means no prompt. */
    fun run(activity: Activity) {
        Thread {
            val update = try {
                val conn = URL(LATEST).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "spyo-map-mobile")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try {
                    if (conn.responseCode == 200) {
                        pickUpdate(conn.inputStream.bufferedReader().readText(), BuildConfig.VERSION_NAME)
                    } else {
                        null
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (_: Exception) {
                null
            } ?: return@Thread
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                AlertDialog.Builder(activity)
                    .setTitle("Update available")
                    .setMessage("Spyo Map ${update.tag} is available. Download and install it?")
                    .setPositiveButton("Download") { _, _ ->
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.apkUrl)))
                    }
                    .setNegativeButton("Later", null)
                    .show()
            }
        }.start()
    }
}
