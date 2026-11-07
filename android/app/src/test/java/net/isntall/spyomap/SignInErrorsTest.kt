package net.isntall.spyomap

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Test

class SignInErrorsTest {
    @Test
    fun cancellationMapsToCanceled() {
        assertEquals("CANCELED", SignInErrors.codeFor(GetCredentialCancellationException("x")))
    }

    @Test
    fun noCredentialMapsToNoCredential() {
        assertEquals("NO_CREDENTIAL", SignInErrors.codeFor(NoCredentialException("x")))
    }

    @Test
    fun anythingElseMapsToFailed() {
        assertEquals("FAILED", SignInErrors.codeFor(GetCredentialUnknownException("x")))
        assertEquals("FAILED", SignInErrors.codeFor(IllegalStateException("x")))
    }
}
