package top.ntutn.tokenfamily.sdk.exception

import org.junit.Test
import org.junit.Assert.*

class TokenFamilyExceptionTest {

    @Test
    fun `test base exception`() {
        val ex = TokenFamilyException("TEST", "test message")
        assertEquals("TEST", ex.errorCode)
        assertEquals("test message", ex.message)
    }

    @Test
    fun `test subclass exceptions`() {
        val notInstalled = NotInstalledException()
        assertEquals("NOT_INSTALLED", notInstalled.errorCode)

        val bindFailed = BindFailedException()
        assertEquals("BIND_FAILED", bindFailed.errorCode)

        val unauthorized = UnauthorizedException()
        assertEquals("UNAUTHORIZED", unauthorized.errorCode)

        val keyMissing = KeyMissingException()
        assertEquals("KEY_MISSING", keyMissing.errorCode)

        val streamCancelled = StreamCancelledException()
        assertEquals("STREAM_CANCELLED", streamCancelled.errorCode)

        val network = NetworkException()
        assertEquals("NETWORK_ERROR", network.errorCode)
    }

    @Test
    fun `test exception inheritance`() {
        val ex: TokenFamilyException = NotInstalledException()
        assertTrue(ex is TokenFamilyException)
        assertTrue(ex is NotInstalledException)
    }
}
