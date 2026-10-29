package ingsis.language.resources

import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.model.Diagnostic
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.Language
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
import ingsis.language.resources.dtos.ValidateRequest
import ingsis.language.services.LanguageRegistry
import io.quarkus.test.InjectMock
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.Mockito

@QuarkusTest
class LanguageResourceTest {
    @InjectMock
    lateinit var registry: LanguageRegistry

    @Test
    fun `get languages returns 200 with languages list`() {
        val dummyLanguages = listOf(Language(name = "dummy-lang", versions = listOf("1.0", "1.1")))
        Mockito.`when`(registry.getAllLanguages()).thenReturn(dummyLanguages)

        given()
            .`when`()
            .get("/languages")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("languages.size()", `is`(1))
            .body("languages[0].name", `is`("dummy-lang"))
            .body("languages[0].versions[0]", `is`("1.0"))
            .body("languages[0].versions[1]", `is`("1.1"))
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

        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a: number = 5;", version = "1.1"))
            .`when`()
            .post("/languages/printscript/validate")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("valid", `is`(true))
            .body("errors.size()", `is`(0))
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

        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a: number = 5", version = "1.1"))
            .`when`()
            .post("/languages/printscript/validate")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("valid", `is`(false))
            .body("errors.size()", `is`(1))
            .body("errors[0].rule", `is`("UNEXPECTED_TOKEN"))
            .body("errors[0].message", `is`("Expected ';'"))
            .body("errors[0].line", `is`(1))
            .body("errors[0].column", `is`(17))
    }

    @Test
    fun `validate returns 404 when language is not found`() {
        Mockito
            .`when`(registry.getValidator("unknown", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.LanguageNotFound("unknown")))

        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a = 1;", version = "1.1"))
            .`when`()
            .post("/languages/unknown/validate")
            .then()
            .statusCode(404)
            .contentType(ContentType.JSON)
            .body("error", `is`("LANGUAGE_NOT_FOUND"))
    }

    @Test
    fun `validate returns 400 when version is unsupported`() {
        Mockito
            .`when`(registry.getValidator("printscript", "9.9"))
            .thenReturn(LanguageResult.Failure(DomainError.UnsupportedVersion("printscript", "9.9")))

        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a = 1;", version = "9.9"))
            .`when`()
            .post("/languages/printscript/validate")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", `is`("UNSUPPORTED_VERSION"))
    }

    @Test
    fun `validate returns 501 when capability not supported`() {
        Mockito
            .`when`(registry.getValidator("printscript", "1.1"))
            .thenReturn(LanguageResult.Failure(DomainError.CapabilityNotSupported("printscript", "validate")))

        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a = 1;", version = "1.1"))
            .`when`()
            .post("/languages/printscript/validate")
            .then()
            .statusCode(501)
            .contentType(ContentType.JSON)
            .body("error", `is`("CAPABILITY_NOT_SUPPORTED"))
    }

    @Test
    fun `validate returns 400 when request has blank version`() {
        given()
            .contentType(ContentType.JSON)
            .body(ValidateRequest(content = "let a = 1;", version = ""))
            .`when`()
            .post("/languages/printscript/validate")
            .then()
            .statusCode(400)
            .contentType(ContentType.JSON)
            .body("error", `is`("BAD_REQUEST"))
    }
}
