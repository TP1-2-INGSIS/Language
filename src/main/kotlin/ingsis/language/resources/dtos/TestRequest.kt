package ingsis.language.resources.dtos

data class TestRequest(
    val content: String = "",
    val version: String = "",
    val inputs: List<String> = emptyList(),
    val expectedOutputs: List<String> = emptyList(),
)
