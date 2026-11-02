package ingsis.language.services

import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.LanguageResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class TestingServiceTest {
    private val executionService = Mockito.mock(ExecutionService::class.java)
    private val service = TestingService(executionService)

    @Test
    fun `test returns passed = true when execution succeeds and outputs match expectedOutputs`() {
        val executionReport = ExecutionReport(outputs = listOf("Hola Ana"))

        Mockito
            .`when`(executionService.execute("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana")))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana"), listOf("Hola Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertTrue(report.passed)
        assertEquals(listOf("Hola Ana"), report.expectedOutputs)
        assertEquals(listOf("Hola Ana"), report.actualOutputs)
        assertNull(report.error)
    }

    @Test
    fun `test returns passed = false when execution succeeds but outputs do not match expectedOutputs`() {
        val executionReport = ExecutionReport(outputs = listOf("Hola  Ana"))

        Mockito
            .`when`(executionService.execute("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana")))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "println(\"Hola \" + n);", listOf("Ana"), listOf("Hola Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertFalse(report.passed)
        assertEquals(listOf("Hola Ana"), report.expectedOutputs)
        assertEquals(listOf("Hola  Ana"), report.actualOutputs)
        assertNull(report.error)
    }

    @Test
    fun `test returns passed = false when execution has error`() {
        val diagnostic = Diagnostic("INPUTS_EXHAUSTED", "Input requested but none left", 1, 17)
        val executionReport = ExecutionReport(outputs = emptyList(), error = diagnostic)

        Mockito
            .`when`(executionService.execute("printscript", "1.1", "readInput();", emptyList()))
            .thenReturn(LanguageResult.Success(executionReport))

        val result = service.test("printscript", "1.1", "readInput();", emptyList(), listOf("Ana"))

        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertFalse(report.passed)
        assertEquals(listOf("Ana"), report.expectedOutputs)
        assertEquals(emptyList<String>(), report.actualOutputs)
        assertEquals(diagnostic, report.error)
    }

    @Test
    fun `test fails when execution service returns failure`() {
        Mockito
            .`when`(executionService.execute("unknown", "1.0", "readInput();", emptyList()))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        val result = service.test("unknown", "1.0", "readInput();", emptyList(), listOf("Ana"))

        assertTrue(result.isFailure)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }
}
