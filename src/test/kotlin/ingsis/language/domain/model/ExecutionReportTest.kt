package ingsis.language.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionReportTest {
    @Test
    fun `isSuccess returns true when error is null`() {
        val report = ExecutionReport(outputs = listOf("Hello World"), error = null)
        assertTrue(report.isSuccess)
    }

    @Test
    fun `isSuccess returns false when error is present`() {
        val diagnostic = Diagnostic(rule = "RUNTIME_ERROR", message = "Division by zero", line = 1, column = 10)
        val report = ExecutionReport(outputs = emptyList(), error = diagnostic)
        assertFalse(report.isSuccess)
        assertEquals(diagnostic, report.error)
    }

    @Test
    fun `default values are set properly`() {
        val report = ExecutionReport()
        assertTrue(report.outputs.isEmpty())
        assertNull(report.error)
        assertEquals(0L, report.executionTimeMs)
        assertTrue(report.isSuccess)
    }
}
