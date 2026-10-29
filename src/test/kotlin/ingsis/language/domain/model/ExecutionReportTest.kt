package ingsis.language.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExecutionReportTest {
    @Test
    fun `isSuccess returns true when errors are empty`() {
        val report = ExecutionReport(outputs = listOf("Hello World"), errors = emptyList())
        assertTrue(report.isSuccess)
    }

    @Test
    fun `isSuccess returns false when errors are present`() {
        val report = ExecutionReport(outputs = emptyList(), errors = listOf("SyntaxError at line 1"))
        assertFalse(report.isSuccess)
    }

    @Test
    fun `default values are set properly`() {
        val report = ExecutionReport()
        assertTrue(report.outputs.isEmpty())
        assertTrue(report.errors.isEmpty())
        assertEquals(0L, report.executionTimeMs)
        assertTrue(report.isSuccess)
    }
}
