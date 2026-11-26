package ingsis.language.domain.engine.printscript

import ingsis.language.domain.model.InvalidRulesException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

/**
 * Translates the rule keys Snippets sends to the keys CNC understands.
 * Unknown keys are ignored.
 */
object RuleConfigMapper {
    const val IDENTIFIER_FORMAT = "identifierFormat"
    const val PRINTLN_RULE = "mandatory-variable-or-literal-in-println"
    const val READ_INPUT_RULE = "mandatory-variable-or-literal-in-readInput"

    /** Lint rule key -> CNC key. */
    val lintKeys: Map<String, String> =
        mapOf(
            IDENTIFIER_FORMAT to "identifier_format",
            "identifier_format" to "identifier_format",
            PRINTLN_RULE to PRINTLN_RULE,
            READ_INPUT_RULE to READ_INPUT_RULE,
        )

    /** Format rules that rewrite horizontal spacing; they compose in a single CNC pass. */
    val spacingFormatKeys: Map<String, String> =
        mapOf(
            "enforce-spacing-before-colon-in-declaration" to "enforce-spacing-before-colon-in-declaration",
            "enforce-spacing-after-colon-in-declaration" to "enforce-spacing-after-colon-in-declaration",
            "enforce-spacing-around-equals" to "enforce-spacing-around-equals",
            "enforce-no-spacing-around-equals" to "enforce-no-spacing-around-equals",
            "mandatory-single-space-separation" to "mandatory-single-space-separation",
            "mandatory-space-surrounding-operations" to "mandatory-space-surrounding-operations",
        )

    private const val LINE_BREAKS_PRINTLN = "line-breaks-after-println"
    private const val LINE_BREAK_STATEMENT = "mandatory-line-break-after-statement"
    private const val BRACE_BELOW = "if-brace-below-line"
    private const val BRACE_SAME = "if-brace-same-line"
    private const val INDENT = "indent-inside-braces"

    /** Every format key accepted, with its value type. */
    val formatKeyTypes: Map<String, String> =
        spacingFormatKeys.keys.associateWith { "boolean" } +
            mapOf(
                LINE_BREAKS_PRINTLN to "integer",
                LINE_BREAK_STATEMENT to "boolean",
                BRACE_BELOW to "boolean",
                BRACE_SAME to "boolean",
                INDENT to "integer",
            )

    private val mapper = JsonMapper.builder().build()

    fun lintConfig(rules: String): String = toJson(select(parse(rules), lintKeys))

    /**
     * CNC applies at most one "structure" rule per call, so each group becomes its own pass
     * (run in order): spacing + println breaks, statement breaks, if brace, indentation.
     */
    fun formatPasses(rules: String): List<String> {
        val node = parse(rules)
        val spacingAndPrintln = select(node, spacingFormatKeys + (LINE_BREAKS_PRINTLN to LINE_BREAKS_PRINTLN))
        val passes =
            listOf(
                spacingAndPrintln,
                select(node, mapOf(LINE_BREAK_STATEMENT to LINE_BREAK_STATEMENT)),
                select(node, mapOf(BRACE_BELOW to BRACE_BELOW, BRACE_SAME to BRACE_SAME)),
                select(node, mapOf(INDENT to "indent-inside-if", "indent-inside-if" to "indent-inside-if")),
            )
        return passes.filter { it.isNotEmpty() }.map(::toJson)
    }

    private fun parse(rules: String): JsonNode {
        val node =
            try {
                mapper.readTree(rules.ifBlank { "{}" })
            } catch (e: tools.jackson.core.JacksonException) {
                throw InvalidRulesException("Las reglas no son un JSON válido: ${e.originalMessage}")
            }
        if (!node.isObject) throw InvalidRulesException("Las reglas deben ser un objeto JSON")
        return node
    }

    private fun select(
        node: JsonNode,
        keys: Map<String, String>,
    ): Map<String, JsonNode> =
        keys.entries
            .filter { (from, _) -> node.has(from) && !node.get(from).isNull }
            .associate { (from, to) -> to to node.get(from) }

    private fun toJson(values: Map<String, JsonNode>): String = mapper.writeValueAsString(values)
}
