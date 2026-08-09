package top.ntutn.tokenfamily.sdk.exception

import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class TokenFamilyExceptionTest {

    @Test
    fun `test base exception`() {
        val ex = TokenFamilyException("TEST", "test message")
        assertEquals("TEST", ex.errorCode)
        assertEquals("test message", ex.message)
        val throwable: Any = ex
        assertTrue(throwable is IOException)
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

        val payload = PayloadTooLargeException()
        assertEquals("PAYLOAD_TOO_LARGE", payload.errorCode)
    }

    @Test
    fun `test exception inheritance`() {
        val ex: IOException = NotInstalledException()
        assertTrue(ex is TokenFamilyException)
        assertTrue(ex is NotInstalledException)
    }
}
