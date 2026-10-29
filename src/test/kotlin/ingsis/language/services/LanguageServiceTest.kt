package ingsis.language.services

import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.Language
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
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
    fun `validate delegates to validator and returns report`() {
        val validator = Mockito.mock(CodeValidator::class.java)
        val expectedReport =
            ValidationReport(
                isValid = false,
                diagnostics = listOf(Diagnostic("UNEXPECTED_TOKEN", "Missing ;", 1, 10)),
            )

        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(validator))
        Mockito
            .`when`(validator.validate("let a = 1", "1.1"))
            .thenReturn(LanguageResult.Success(expectedReport))

        val result = service.validate("printscript", "1.1", "let a = 1")

        assertTrue(result.isSuccess)
        assertEquals(expectedReport, result.getOrNull())
    }

    @Test
    fun `validate fails when registry returns failure`() {
        Mockito
            .`when`(registry.getValidator("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.validate("unknown", "1.0", "let a = 1")

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }
}
