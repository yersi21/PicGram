package com.example.picgram

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class PasswordRecoveryTest {
    private val base = RecoveryLinks.REDIRECT_URL
    private val session = AuthResult("recovery-jwt", "target-user", "user@example.test", "recovery-refresh")

    @Test fun parsesImplicitRecoveryWithoutConfusingQueryAndFragment() {
        val result = RecoveryLinks.parse("$base#access_token=a.b.c&refresh_token=refresh-1&type=recovery&token_type=bearer")
        assertTrue(result is RecoveryLink.Tokens)
        result as RecoveryLink.Tokens
        assertEquals("a.b.c", result.accessToken)
        assertEquals("refresh-1", result.refreshToken)
    }

    @Test fun parsesTokenHashAndUrlEncodedValues() {
        val result = RecoveryLinks.parse("$base?token_hash=hash%2Bvalue&type=recovery") as RecoveryLink.TokenHash
        assertEquals("hash+value", result.value)
        assertFalse(result.toString().contains("hash+value"))
    }

    @Test fun rejectsOtherOriginsPathsAndAuthorityTricks() {
        val invalid = listOf("https://auth/reset-password", "com.example.other://auth/reset-password",
            "com.example.picgram://other/reset-password", "com.example.picgram://auth/signup",
            "com.example.picgram://attacker@auth/reset-password", "com.example.picgram://auth:443/reset-password")
        invalid.forEach { url ->
            assertThrows(IllegalArgumentException::class.java) { RecoveryLinks.parse("$url#type=recovery&access_token=jwt") }
        }
    }

    @Test fun rejectsMissingWrongOrDuplicatedRecoveryParameters() {
        val invalid = listOf(null, "", base, "$base#type=signup&access_token=jwt",
            "$base#type=recovery", "$base?type=recovery#type=recovery&access_token=jwt",
            "$base#type=recovery&access_token=one&access_token=two",
            "$base#type=recovery&token_hash=hash&access_token=jwt",
            "$base#type=recovery&access_token=jwt&token_type=unknown")
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { RecoveryLinks.parse(it) } }
    }

    @Test fun rejectsExpiredAndMalformedLinksWithoutEchoingCredentials() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RecoveryLinks.parse("$base#error=access_denied&error_code=otp_expired&access_token=secret")
        }
        assertFalse(error.message.orEmpty().contains("secret"))
        assertThrows(IllegalArgumentException::class.java) { RecoveryLinks.parse("$base#type=recovery&token_hash=%XX") }
    }

    @Test fun updatesUsingOnlyTheSuppliedRecoveryJwt() {
        val calls = mutableListOf<String>()
        val result = executeRecoveryUpdate(session, refresh = { error("Must not refresh a valid session") }) { calls.add(it) }
        assertEquals(listOf("recovery-jwt"), calls)
        assertEquals("recovery-jwt", result)
    }

    @Test fun renewsOnlyTheRecoveryRefreshTokenAfterAnExplicit401() {
        val calls = mutableListOf<String>()
        val result = executeRecoveryUpdate(session, refresh = { supplied ->
            assertEquals("recovery-refresh", supplied)
            session.copy(accessToken = "renewed-recovery-jwt")
        }) { token ->
            calls.add(token)
            if (calls.size == 1) throw ApiException(401, "Expired")
        }
        assertEquals(listOf("recovery-jwt", "renewed-recovery-jwt"), calls)
        assertEquals("renewed-recovery-jwt", result)
    }

    @Test fun doesNotRetryAnAmbiguousWriteOrAForbiddenRequest() {
        var refreshes = 0
        val refresh: (String) -> AuthResult = { refreshes++; session }
        assertThrows(IOException::class.java) { executeRecoveryUpdate(session, refresh) { throw IOException("Lost response") } }
        assertThrows(ApiException::class.java) { executeRecoveryUpdate(session, refresh) { throw ApiException(403, "Forbidden") } }
        assertEquals(0, refreshes)
    }

    @Test fun rejectsARefreshThatReturnsADifferentUser() {
        var writes = 0
        assertThrows(RecoveryExpiredException::class.java) {
            executeRecoveryUpdate(session, refresh = { session.copy(userId = "another-user") }) {
                writes++
                throw ApiException(401, "Expired")
            }
        }
        assertEquals(1, writes)
    }

    @Test fun marksAnExpiredRefreshAsAnExpiredRecovery() {
        assertThrows(RecoveryExpiredException::class.java) {
            executeRecoveryUpdate(session, refresh = { throw ApiException(400, "Refresh invalid") }) {
                throw ApiException(401, "Expired")
            }
        }
    }

    @Test fun doesNotRefreshWhenTheLinkHasNoRefreshToken() {
        assertThrows(ApiException::class.java) {
            executeRecoveryUpdate(session.copy(refreshToken = ""), refresh = { error("No refresh token") }) {
                throw ApiException(401, "Expired")
            }
        }
    }

    @Test fun validatesPasswordsWithoutTrimmingOrChangingTheirContents() {
        assertNotNull(newPasswordError("short", "short"))
        assertNotNull(newPasswordError("secure-password", "other-password"))
        assertNull(newPasswordError("secure-password", "secure-password"))
        assertNull(newPasswordError("  secure-password  ", "  secure-password  "))
    }
}
