package net.isntall.spyomap

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException

/** Maps Credential Manager failures to the contract's SignInErrorCode (src/definitions.ts). */
object SignInErrors {
    fun codeFor(e: Exception): String = when (e) {
        is GetCredentialCancellationException -> "CANCELED"
        is NoCredentialException -> "NO_CREDENTIAL"
        else -> "FAILED"
    }
}
