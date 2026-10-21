package com.example.googlehome.auth

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleHomeAuthManagerTest {

    @Test
    fun testInvalidEmailRejected() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val authManager = GoogleHomeAuthManager(context)

        // Empty or invalid email without @ must be rejected
        val result = authManager.linkGoogleAccount("", "Test User")
        assertFalse(result)
        assertFalse(authManager.isLinked())

        val resultInvalid = authManager.linkGoogleAccount("invalid_email", "Test User")
        assertFalse(resultInvalid)
    }

    @Test
    fun testRealAccessTokenStorage() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val authManager = GoogleHomeAuthManager(context)

        val realToken = "ya29.a0AfH6SMD_real_production_google_oauth_token"
        val result = authManager.linkGoogleAccount(
            email = "tester@gmail.com",
            displayName = "Kyaw Gyi Tester",
            accessToken = realToken
        )

        assertTrue(result)
        assertTrue(authManager.isLinked())
        assertEquals("tester@gmail.com", authManager.accountProfile.value.email)
        assertEquals("Kyaw Gyi Tester", authManager.accountProfile.value.displayName)
        assertEquals(realToken, authManager.getAccessToken())
        assertTrue(authManager.accountProfile.value.grantedScopes.isNotEmpty())

        // Test unlinking
        authManager.unlinkGoogleAccount()
        assertFalse(authManager.isLinked())
        assertNull(authManager.getAccessToken())
        assertEquals("", authManager.accountProfile.value.email)
    }
}
