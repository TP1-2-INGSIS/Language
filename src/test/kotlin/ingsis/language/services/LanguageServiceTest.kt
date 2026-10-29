package ingsis.language.services

import ingsis.language.domain.model.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class LanguageServiceTest {
    private val registry = Mockito.mock(LanguageRegistry::class.java)
    private val service = LanguageService(registry)

    @Test
    fun `getAllLanguages delegates to registry and returns success`() {
        val dummyLanguages = listOf(Language(name = "test-lang", versions = listOf("0.1", "1.0")))
        Mockito.`when`(registry.getAllLanguages()).thenReturn(dummyLanguages)

        val result = service.getAllLanguages()

        assertTrue(result.isSuccess)
        assertEquals(dummyLanguages, result.getOrNull())
    }

    @Test
    fun `getAllLanguages catches exceptions from registry and returns failure`() {
        Mockito.`when`(registry.getAllLanguages()).thenThrow(RuntimeException("Registry lookup failed"))

        val result = service.getAllLanguages()

        assertTrue(result.isFailure)
        assertEquals("Registry lookup failed", result.exceptionOrNull()?.message)
    }
}
