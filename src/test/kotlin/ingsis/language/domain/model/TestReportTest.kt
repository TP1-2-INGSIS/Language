package ingsis.language.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestReportTest {
    @Test
    fun `test report with matching outputs passes`() {
        val report =
            TestReport(
                passed = true,
                expectedOutputs = listOf("Hello"),
                actualOutputs = listOf("Hello"),
                error = null,
            )

        assertTrue(report.passed)
        assertEquals(listOf("Hello"), report.expectedOutputs)
        assertEquals(listOf("Hello"), report.actualOutputs)
        assertNull(report.error)
    }

    @Test
    fun `test report with error fails`() {
        val diagnostic = Diagnostic("RUNTIME_ERROR", "Division by zero", 1, 5)
        val report =
            TestReport(
                passed = false,
                expectedOutputs = listOf("Hello"),
                actualOutputs = emptyList(),
                error = diagnostic,
            )

        assertFalse(report.passed)
        assertEquals(diagnostic, report.error)
    }
}
