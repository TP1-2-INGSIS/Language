package ingsis.language.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TestCaseTest {
    @Test
    fun `test report calculates statistics correctly when all pass`() {
        val testCase = TestCase(name = "Add test", inputs = listOf("2", "3"), expectedOutputs = listOf("5"))
        val result =
            TestCaseResult(
                testCase = testCase,
                status = TestStatus.PASSED,
                actualOutputs = listOf("5"),
            )
        val report = TestReport(results = listOf(result))

        assertEquals(1, report.totalCount)
        assertEquals(1, report.passedCount)
        assertTrue(report.isAllPassed)
    }

    @Test
    fun `test report detects failed test cases`() {
        val testCase1 = TestCase(name = "Test 1")
        val testCase2 = TestCase(name = "Test 2")

        val result1 = TestCaseResult(testCase = testCase1, status = TestStatus.PASSED)
        val result2 =
            TestCaseResult(
                testCase = testCase2,
                status = TestStatus.FAILED,
                actualOutputs = listOf("4"),
                errorMessage = "Expected 5 but got 4",
            )

        val report = TestReport(results = listOf(result1, result2))

        assertEquals(2, report.totalCount)
        assertEquals(1, report.passedCount)
        assertFalse(report.isAllPassed)
    }

    @Test
    fun `empty test report has zero counts`() {
        val report = TestReport()
        assertEquals(0, report.totalCount)
        assertEquals(0, report.passedCount)
        assertTrue(report.isAllPassed)
    }
}
