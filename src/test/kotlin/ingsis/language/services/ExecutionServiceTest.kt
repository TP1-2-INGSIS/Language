package ingsis.language.services

import ingsis.language.domain.engine.CodeExecutor
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class ExecutionServiceTest {
    private val registry = Mockito.mock(LanguageRegistry::class.java)
    private val service = ExecutionService(registry)

    @Test
    fun `execute delegates to executor and returns execution report`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val expectedReport = ExecutionReport(outputs = listOf("Ana"))

        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))
        Mockito
            .`when`(executor.execute("println(\"Ana\");", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(expectedReport))

        val result = service.execute("printscript", "1.1", "println(\"Ana\");", emptyList())

        assertTrue(result.isSuccess)
        assertEquals(expectedReport, result.getOrNull())
    }

    @Test
    fun `execute fails when registry returns failure`() {
        Mockito
            .`when`(registry.getExecutor("unknown", "1.0"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.execute("unknown", "1.0", "println(\"Ana\");", emptyList())

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }
}
