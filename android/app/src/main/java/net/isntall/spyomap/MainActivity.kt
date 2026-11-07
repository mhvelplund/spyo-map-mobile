package net.isntall.spyomap

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SpyoNativePlugin::class.java)
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                BackNavigation.onBack(
                    bridge.webView.canGoBack(),
                    goBack = { bridge.webView.goBack() },
                    leave = { moveTaskToBack(true) },
                )
            }
        })
        // A recreated activity (e.g. rotation) gets the original intent again; it was handled the first time.
        if (savedInstanceState == null) handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        val shared = ShareIntents.urisFrom(intent)
        if (shared.isNotEmpty()) {
            val plugin = bridge.getPlugin("SpyoNative")?.instance as? SpyoNativePlugin
            if (plugin != null) plugin.onPhotosShared(shared) else SpyoNativePlugin.sharedPhotos.add(shared)
            open(LinkPolicy.UPLOAD_URL)
        } else if (intent.action == Intent.ACTION_VIEW && LinkPolicy.isInApp(intent.dataString)) {
            // Launcher shortcut (Upload) or a link to the site.
            open(intent.dataString!!)
        }
    }

    private fun open(url: String) {
        bridge.webView.post { bridge.webView.loadUrl(url) }
    }
}
