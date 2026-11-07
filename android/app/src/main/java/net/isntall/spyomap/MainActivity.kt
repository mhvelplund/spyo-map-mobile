package net.isntall.spyomap

import android.content.Intent
import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SpyoNativePlugin::class.java)
        super.onCreate(savedInstanceState)
        // A recreated activity (e.g. rotation) gets the original intent again; it was handled the first time.
        if (savedInstanceState == null) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        val uris = intent?.let(ShareIntents::urisFrom).orEmpty()
        if (uris.isEmpty()) return
        val plugin = bridge.getPlugin("SpyoNative")?.instance as? SpyoNativePlugin
        if (plugin != null) plugin.onPhotosShared(uris) else SpyoNativePlugin.sharedPhotos.add(uris)
        bridge.webView.post { bridge.webView.loadUrl(UPLOAD_URL) }
    }

    private companion object {
        const val UPLOAD_URL = "https://spyo-map.isntall.net/#/upload"
    }
}
