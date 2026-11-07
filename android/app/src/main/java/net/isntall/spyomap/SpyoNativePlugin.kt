package net.isntall.spyomap

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Native side of the contract in src/definitions.ts. */
@CapacitorPlugin(name = "SpyoNative")
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

    override fun handleOnDestroy() {
        scope.cancel()
        super.handleOnDestroy()
    }

    companion object {
        private const val PREFS = "spyo"
        private const val KEY_SIGNED_IN_BEFORE = "signedInBefore"
    }
}
