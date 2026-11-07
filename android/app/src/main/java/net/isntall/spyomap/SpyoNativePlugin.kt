package net.isntall.spyomap

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.getcapacitor.Bridge
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Native side of the contract in src/definitions.ts. */
@CapacitorPlugin(
    name = "SpyoNative",
    permissions = [
        Permission(
            alias = SpyoNativePlugin.MEDIA_LOCATION,
            strings = [Manifest.permission.ACCESS_MEDIA_LOCATION],
        ),
    ],
)
class SpyoNativePlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    @PluginMethod
    fun signIn(call: PluginCall) {
        val silent = call.getBoolean("silent", false) == true
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(silent)
            .setAutoSelectEnabled(silent)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        scope.launch {
            try {
                val credential = CredentialManager.create(activity).getCredential(activity, request).credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    prefs.edit().putBoolean(KEY_SIGNED_IN_BEFORE, true).apply()
                    call.resolve(JSObject().put("idToken", idToken))
                } else {
                    call.reject("Unexpected credential type", "FAILED")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "Sign-in failed", SignInErrors.codeFor(e), e)
            }
        }
    }

    @PluginMethod
    fun signOut(call: PluginCall) {
        prefs.edit().remove(KEY_SIGNED_IN_BEFORE).apply()
        scope.launch {
            try {
                CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
            } catch (_: Exception) {
                // The flag is already cleared, which is what stops silent re-sign-in.
            }
            call.resolve()
        }
    }

    @PluginMethod
    fun hasSignedInBefore(call: PluginCall) {
        call.resolve(JSObject().put("value", prefs.getBoolean(KEY_SIGNED_IN_BEFORE, false)))
    }

    @PluginMethod
    fun pickPhotos(call: PluginCall) = withMediaLocation(call, "pickAfterPermission") { pickAfterPermission(call) }

    @PermissionCallback
    private fun pickAfterPermission(call: PluginCall) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("image/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(call, intent, "pickResult")
    }

    @ActivityCallback
    private fun pickResult(call: PluginCall, result: ActivityResult) {
        val data = result.data
        val uris = buildList {
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) add(clip.getItemAt(i).uri)
                } else {
                    data.data?.let(::add)
                }
            }
        }
        resolvePhotos(call, uris)
    }

    @PluginMethod
    fun getSharedPhotos(call: PluginCall) =
        withMediaLocation(call, "sharedAfterPermission") { sharedAfterPermission(call) }

    @PermissionCallback
    private fun sharedAfterPermission(call: PluginCall) = resolvePhotos(call, sharedPhotos.drain())

    /** Called by MainActivity when photos are shared to the app. */
    fun onPhotosShared(uris: List<Uri>) {
        if (sharedPhotos.add(uris)) notifyListeners("photosShared", JSObject(), true)
    }

    /**
     * Without ACCESS_MEDIA_LOCATION, Android hands out photos with GPS removed. Ask once; if refused, carry on
     * anyway (the page rejects photos without location and explains why).
     */
    private fun withMediaLocation(call: PluginCall, callback: String, granted: () -> Unit) {
        if (getPermissionState(MEDIA_LOCATION) == PermissionState.GRANTED) {
            granted()
        } else {
            requestPermissionForAlias(MEDIA_LOCATION, call, callback)
        }
    }

    private fun resolvePhotos(call: PluginCall, uris: List<Uri>) {
        val importer = PhotoImporter(context)
        val base = (bridge.serverUrl ?: bridge.localUrl).trimEnd('/')
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                importer.cleanUp()
                PhotoImports.importAll(uris, importer::import, importer::displayName)
            }
            val array = JSArray()
            for (p in result.imported) {
                array.put(
                    JSObject()
                        .put("name", p.name)
                        .put("mimeType", p.mimeType)
                        .put("size", p.size)
                        .put("webPath", base + Bridge.CAPACITOR_FILE_START + p.file.absolutePath),
                )
            }
            call.resolve(JSObject().put("photos", array).put("failed", JSArray(result.failed)))
        }
    }

    override fun handleOnDestroy() {
        scope.cancel()
        super.handleOnDestroy()
    }

    companion object {
        private const val PREFS = "spyo"
        private const val KEY_SIGNED_IN_BEFORE = "signedInBefore"
        const val MEDIA_LOCATION = "mediaLocation"

        /** Survives the plugin; MainActivity may receive a share before the page asks for it. */
        val sharedPhotos = PendingShares<Uri>()
    }
}
