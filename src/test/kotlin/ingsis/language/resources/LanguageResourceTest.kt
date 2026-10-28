package ingsis.language.resources

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class LanguageResourceTest {
    @Test
    fun testLanguageEndpoint() {
        given()
            .`when`()
            .get("/languages")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("languages.size()", `is`(0))
    }
}
