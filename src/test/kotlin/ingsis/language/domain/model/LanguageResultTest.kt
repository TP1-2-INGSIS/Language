package ingsis.language.domain.model

import ingsis.language.resources.toResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LanguageResultTest {
    @Test
    fun `success handles map and flatMap correctly`() {
        val success = LanguageResult.Success("hello")

        assertTrue(success.isSuccess)
        assertFalse(success.isFailure)
        assertEquals("hello", success.getOrNull())
        assertNull(success.errorOrNull())

        val mapped = success.map { it.length }
        assertEquals(5, (mapped as LanguageResult.Success).value)

        val flatMapped = success.flatMap { LanguageResult.Success(it.uppercase()) }
        assertEquals("HELLO", (flatMapped as LanguageResult.Success).value)
    }

    @Test
    fun `failure preserves error on map and flatMap`() {
        val failure: LanguageResult<String> = LanguageResult.Failure(DomainError.LanguageNotFound("fake"))

        assertFalse(failure.isSuccess)
        assertTrue(failure.isFailure)
        assertNull(failure.getOrNull())
        assertEquals("LANGUAGE_NOT_FOUND", failure.errorOrNull()?.code)

        val mapped = failure.map { it.length }
        assertTrue(mapped is LanguageResult.Failure)

        val flatMapped = failure.flatMap { LanguageResult.Success(it.length) }
        assertTrue(flatMapped is LanguageResult.Failure)
    }

    @Test
    fun `domain errors map to appropriate HTTP status codes`() {
        val notFound = DomainError.LanguageNotFound("abc").toResponse()
        assertEquals(404, notFound.status)

        val unsupportedVersion = DomainError.UnsupportedVersion("abc", "1.0").toResponse()
        assertEquals(400, unsupportedVersion.status)

        val invalidRules = DomainError.InvalidRules("bad json").toResponse()
        assertEquals(400, invalidRules.status)

        val notImplemented = DomainError.CapabilityNotSupported("abc", "test").toResponse()
        assertEquals(501, notImplemented.status)

        val invalidSource = DomainError.InvalidSource("syntax error").toResponse()
        assertEquals(422, invalidSource.status)
    }

    @Test
    fun `toResponse on success builds HTTP 200 with transformed body`() {
        val success = LanguageResult.Success("hello")
        val response = success.toResponse { it.uppercase() }

        assertEquals(200, response.status)
        assertEquals("HELLO", response.entity)

        val directResponse = success.toResponse()
        assertEquals(200, directResponse.status)
        assertEquals("hello", directResponse.entity)
    }

    @Test
    fun `toResponse on failure delegates to error response`() {
        val failure: LanguageResult<String> = LanguageResult.Failure(DomainError.LanguageNotFound("missing"))
        val response = failure.toResponse { it.uppercase() }

        assertEquals(404, response.status)
    }
}
