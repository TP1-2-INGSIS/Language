package ingsis.language.resources

import ingsis.language.domain.model.Language
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
    fun `get languages returns 500 when registry fails`() {
        Mockito.`when`(registry.getAllLanguages()).thenThrow(RuntimeException("Registry failure"))

        given()
            .`when`()
            .get("/languages")
            .then()
            .statusCode(500)
    }
}
