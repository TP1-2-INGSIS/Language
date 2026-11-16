package ingsis.language.resources

import ingsis.language.domain.engine.CodeExecutor
import ingsis.language.domain.engine.CodeFormatter
import ingsis.language.domain.engine.CodeLinter
import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.ExecutionReport
import ingsis.language.domain.model.Language
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
import ingsis.language.resources.dtos.ExecuteRequest
import ingsis.language.resources.dtos.FormatRequest
import ingsis.language.resources.dtos.LintRequest
import ingsis.language.resources.dtos.TestRequest
import ingsis.language.resources.dtos.ValidateRequest
import ingsis.language.services.ExecutionService
import ingsis.language.services.LanguageRegistry
import ingsis.language.services.LanguageService
import ingsis.language.services.TestingService
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper

@WebMvcTest(LanguageResource::class)
@Import(LanguageService::class, ExecutionService::class, TestingService::class)
@ActiveProfiles("test")
class LanguageResourceTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockitoBean
    private lateinit var registry: LanguageRegistry

    @Test
    fun `get languages returns 200 with languages list`() {
        val dummyLanguages = listOf(Language(name = "dummy-lang", versions = listOf("1.0", "1.1")))
        Mockito.`when`(registry.getAllLanguages()).thenReturn(dummyLanguages)

        mockMvc
            .perform(get("/languages"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("dummy-lang"))
            .andExpect(jsonPath("$[0].versions[0]").value("1.0"))
            .andExpect(jsonPath("$[0].versions[1]").value("1.1"))
    }

    @Test
    fun `validate returns 200 with valid true when code is valid`() {
        val validator = Mockito.mock(CodeValidator::class.java)
        Mockito
            .`when`(validator.validate("let a: number = 5;", "1.1"))
            .thenReturn(LanguageResult.Success(ValidationReport(isValid = true)))
        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(validator))

        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a: number = 5;", version = "1.1"))),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.errors.length()").value(0))
    }

    @Test
    fun `validate returns 200 with valid false and diagnostics when code is invalid`() {
        val validator = Mockito.mock(CodeValidator::class.java)
        val diagnostics =
            listOf(
                Diagnostic(
                    rule = "UNEXPECTED_TOKEN",
                    message = "Expected ';'",
                    line = 1,
                    column = 17,
                ),
            )
        Mockito
            .`when`(validator.validate("let a: number = 5", "1.1"))
            .thenReturn(LanguageResult.Success(ValidationReport(isValid = false, diagnostics = diagnostics)))
        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(validator))

        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a: number = 5", version = "1.1"))),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].rule").value("UNEXPECTED_TOKEN"))
            .andExpect(jsonPath("$.errors[0].message").value("Expected ';'"))
            .andExpect(jsonPath("$.errors[0].line").value(1))
            .andExpect(jsonPath("$.errors[0].column").value(17))
    }

    @Test
    fun `validate returns 404 when language is not found`() {
        Mockito
            .`when`(registry.getValidator("unknown", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        mockMvc
            .perform(
                post("/languages/unknown/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a = 1;", version = "1.1"))),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("LANGUAGE_NOT_FOUND"))
    }

    @Test
    fun `validate returns 400 when version is unsupported`() {
        Mockito
            .`when`(registry.getValidator("printscript", "9.9"))
            .thenReturn(LanguageResult.Failure(DomainError.UnsupportedVersion("printscript", "9.9")))

        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a = 1;", version = "9.9"))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("UNSUPPORTED_VERSION"))
    }

    @Test
    fun `validate returns 501 when capability not supported`() {
        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.CapabilityNotSupported("printscript", "validate")))

        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a = 1;", version = "1.1"))),
            ).andExpect(status().isNotImplemented)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("CAPABILITY_NOT_SUPPORTED"))
    }

    @Test
    fun `validate returns 400 when request has blank version`() {
        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ValidateRequest(content = "let a = 1;", version = ""))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `validate returns 400 when request body is malformed`() {
        mockMvc
            .perform(
                post("/languages/printscript/validate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{ invalid json"),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `format returns 200 with formatted code`() {
        val formatter = Mockito.mock(CodeFormatter::class.java)
        val rulesNode = objectMapper.readTree("{\"spaceBeforeColon\":true}")

        Mockito
            .`when`(formatter.format("let a:number=5;", "1.1", "{\"spaceBeforeColon\":true}"))
            .thenReturn(LanguageResult.Success("let a : number = 5;\n"))
        Mockito
            .`when`(registry.getFormatter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(formatter))

        mockMvc
            .perform(
                post("/languages/printscript/format")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(FormatRequest(content = "let a:number=5;", version = "1.1", rules = rulesNode)),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.formatted").value("let a : number = 5;\n"))
    }

    @Test
    fun `format returns 422 when source code is invalid`() {
        val formatter = Mockito.mock(CodeFormatter::class.java)

        Mockito
            .`when`(formatter.format("invalid source", "1.1", "{}"))
            .thenReturn(LanguageResult.Failure(DomainError.InvalidSource("Syntax error")))
        Mockito
            .`when`(registry.getFormatter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(formatter))

        mockMvc
            .perform(
                post("/languages/printscript/format")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FormatRequest(content = "invalid source", version = "1.1"))),
            ).andExpect(status().isUnprocessableContent)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("INVALID_SOURCE"))
    }

    @Test
    fun `format returns 400 when version is blank`() {
        mockMvc
            .perform(
                post("/languages/printscript/format")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(FormatRequest(content = "let a = 1;", version = ""))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `lint returns 200 with violations list`() {
        val linter = Mockito.mock(CodeLinter::class.java)
        val report =
            ValidationReport(
                isValid = false,
                diagnostics =
                    listOf(
                        Diagnostic(
                            rule = "identifierFormat",
                            message = "my_var is not camel case",
                            line = 1,
                            column = 5,
                        ),
                    ),
            )

        Mockito
            .`when`(linter.lint("let my_var: number = 5;", "1.1", "{}"))
            .thenReturn(LanguageResult.Success(report))
        Mockito
            .`when`(registry.getLinter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(linter))

        mockMvc
            .perform(
                post("/languages/printscript/lint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(LintRequest(content = "let my_var: number = 5;", version = "1.1"))),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.violations.length()").value(1))
            .andExpect(jsonPath("$.violations[0].rule").value("identifierFormat"))
            .andExpect(jsonPath("$.violations[0].message").value("my_var is not camel case"))
            .andExpect(jsonPath("$.violations[0].line").value(1))
            .andExpect(jsonPath("$.violations[0].column").value(5))
    }

    @Test
    fun `lint returns 422 when source code is invalid`() {
        val linter = Mockito.mock(CodeLinter::class.java)

        Mockito
            .`when`(linter.lint("invalid code", "1.1", "{}"))
            .thenReturn(LanguageResult.Failure(DomainError.InvalidSource("Cannot parse")))
        Mockito
            .`when`(registry.getLinter("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(linter))

        mockMvc
            .perform(
                post("/languages/printscript/lint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(LintRequest(content = "invalid code", version = "1.1"))),
            ).andExpect(status().isUnprocessableContent)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("INVALID_SOURCE"))
    }

    @Test
    fun `lint returns 400 when version is blank`() {
        mockMvc
            .perform(
                post("/languages/printscript/lint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(LintRequest(content = "let a = 1;", version = ""))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `execute returns 200 with outputs and null error`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val report = ExecutionReport(outputs = listOf("Ana"), error = null)

        Mockito
            .`when`(executor.execute("println(\"Ana\");", "1.1", listOf("Ana")))
            .thenReturn(LanguageResult.Success(report))
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))

        mockMvc
            .perform(
                post("/languages/printscript/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            ExecuteRequest(content = "println(\"Ana\");", version = "1.1", inputs = listOf("Ana")),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.outputs.length()").value(1))
            .andExpect(jsonPath("$.outputs[0]").value("Ana"))
            .andExpect(jsonPath("$.error").value(nullValue()))
    }

    @Test
    fun `execute returns 200 with runtime error in body`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val diagnostic =
            Diagnostic(
                rule = "INPUTS_EXHAUSTED",
                message = "Input requested but none left",
                line = 1,
                column = 17,
            )
        val report = ExecutionReport(outputs = emptyList(), error = diagnostic)

        Mockito
            .`when`(executor.execute("readInput();", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(report))
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))

        mockMvc
            .perform(
                post("/languages/printscript/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ExecuteRequest(content = "readInput();", version = "1.1"))),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.outputs.length()").value(0))
            .andExpect(jsonPath("$.error.rule").value("INPUTS_EXHAUSTED"))
            .andExpect(jsonPath("$.error.message").value("Input requested but none left"))
            .andExpect(jsonPath("$.error.line").value(1))
            .andExpect(jsonPath("$.error.column").value(17))
    }

    @Test
    fun `execute returns 400 when version is blank`() {
        mockMvc
            .perform(
                post("/languages/printscript/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ExecuteRequest(content = "let a = 1;", version = ""))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `execute returns 404 when language is not found`() {
        Mockito
            .`when`(registry.getExecutor("unknown", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        mockMvc
            .perform(
                post("/languages/unknown/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ExecuteRequest(content = "let a = 1;", version = "1.1"))),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("LANGUAGE_NOT_FOUND"))
    }

    @Test
    fun `execute returns 501 when capability not supported`() {
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.CapabilityNotSupported("printscript", "execute")))

        mockMvc
            .perform(
                post("/languages/printscript/execute")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ExecuteRequest(content = "let a = 1;", version = "1.1"))),
            ).andExpect(status().isNotImplemented)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("CAPABILITY_NOT_SUPPORTED"))
    }

    @Test
    fun `test returns 200 with passed true when actual outputs match expected outputs`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        Mockito
            .`when`(executor.execute("println(\"Hola Ana\");", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(ExecutionReport(outputs = listOf("Hola Ana"))))
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))

        mockMvc
            .perform(
                post("/languages/printscript/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            TestRequest(
                                content = "println(\"Hola Ana\");",
                                version = "1.1",
                                inputs = emptyList(),
                                expectedOutputs = listOf("Hola Ana"),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.expectedOutputs[0]").value("Hola Ana"))
            .andExpect(jsonPath("$.actualOutputs[0]").value("Hola Ana"))
    }

    @Test
    fun `test returns 200 with passed false when actual outputs do not match expected outputs`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        Mockito
            .`when`(executor.execute("println(\"Hola  Ana\");", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(ExecutionReport(outputs = listOf("Hola  Ana"))))
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))

        mockMvc
            .perform(
                post("/languages/printscript/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            TestRequest(
                                content = "println(\"Hola  Ana\");",
                                version = "1.1",
                                inputs = emptyList(),
                                expectedOutputs = listOf("Hola Ana"),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.passed").value(false))
            .andExpect(jsonPath("$.expectedOutputs[0]").value("Hola Ana"))
            .andExpect(jsonPath("$.actualOutputs[0]").value("Hola  Ana"))
    }

    @Test
    fun `test returns 200 with passed false and error diagnostic when execution encounters an error`() {
        val executor = Mockito.mock(CodeExecutor::class.java)
        val diagnostic =
            Diagnostic(
                rule = "INPUTS_EXHAUSTED",
                message = "Input requested but none left",
                line = 1,
                column = 17,
            )
        Mockito
            .`when`(executor.execute("readInput();", "1.1", emptyList()))
            .thenReturn(LanguageResult.Success(ExecutionReport(outputs = emptyList(), error = diagnostic)))
        Mockito
            .`when`(registry.getExecutor("printscript", "1.1"))
            .thenReturn(LanguageResult.Success(executor))

        mockMvc
            .perform(
                post("/languages/printscript/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            TestRequest(
                                content = "readInput();",
                                version = "1.1",
                                inputs = emptyList(),
                                expectedOutputs = listOf("Hola"),
                            ),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.passed").value(false))
            .andExpect(jsonPath("$.actualOutputs.length()").value(0))
            .andExpect(jsonPath("$.error.rule").value("INPUTS_EXHAUSTED"))
            .andExpect(jsonPath("$.error.message").value("Input requested but none left"))
            .andExpect(jsonPath("$.error.line").value(1))
            .andExpect(jsonPath("$.error.column").value(17))
    }

    @Test
    fun `test returns 400 when version is blank`() {
        mockMvc
            .perform(
                post("/languages/printscript/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(TestRequest(content = "let a = 1;", version = ""))),
            ).andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
    }

    @Test
    fun `test returns 404 when language is not found`() {
        Mockito
            .`when`(registry.getExecutor("unknown", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        mockMvc
            .perform(
                post("/languages/unknown/test")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(TestRequest(content = "let a = 1;", version = "1.1"))),
            ).andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.error").value("LANGUAGE_NOT_FOUND"))
    }
}
