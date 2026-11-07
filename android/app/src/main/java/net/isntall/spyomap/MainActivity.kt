package net.isntall.spyomap

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(SpyoNativePlugin::class.java)
        super.onCreate(savedInstanceState)
    }
}
