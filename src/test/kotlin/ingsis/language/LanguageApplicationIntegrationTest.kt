package ingsis.language

import ingsis.language.services.LanguageRegistry
import ingsis.language.worker.RedisTaskWorker
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.ObjectMapper
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LanguageApplicationIntegrationTest {
    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var registry: LanguageRegistry

    @Autowired
    private lateinit var worker: RedisTaskWorker

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val client = HttpClient.newHttpClient()

    @Test
    fun `spring context wires real printscript engine and disables redis worker in test profile`() {
        val languages = registry.getAllLanguages()
        assertEquals(1, languages.size)
        assertEquals("printscript", languages[0].name)
        assertEquals(listOf("1.0", "1.1"), languages[0].versions)
        assertFalse(worker.isRunning)
    }

    @Test
    fun `actuator health and languages endpoints respond over real HTTP`() {
        val healthRes = get("/actuator/health")
        assertEquals(200, healthRes.statusCode())
        assertTrue(healthRes.body().contains("\"UP\""))

        val languagesRes = get("/languages")
        assertEquals(200, languagesRes.statusCode())
        val languagesNode = objectMapper.readTree(languagesRes.body())
        assertEquals("printscript", languagesNode[0]["id"].asString())
        assertEquals(
            Regex("""cncVersion=(.+)""").find(File("gradle.properties").readText())!!.groupValues[1],
            languagesNode[0]["engineVersion"].asString(),
        )
    }

    @Test
    fun `all printscript operations work end to end without mocks`() {
        val validateRes = post("/languages/printscript/validate", """{"content":"let a: number = 1;","version":"1.1"}""")
        assertEquals(200, validateRes.statusCode())
        assertTrue(objectMapper.readTree(validateRes.body())["valid"].asBoolean())

        val formatRes = post("/languages/printscript/format", """{"content":"let a:number=1;","version":"1.1"}""")
        assertEquals(200, formatRes.statusCode())
        assertEquals("let a:number=1;", objectMapper.readTree(formatRes.body())["formatted"].asString())

        val lintRes = post("/languages/printscript/lint", """{"content":"let a: number = 1;","version":"1.1"}""")
        assertEquals(200, lintRes.statusCode())
        assertEquals(0, objectMapper.readTree(lintRes.body())["violations"].size())

        val executeRes = post("/languages/printscript/execute", """{"content":"println(1);","version":"1.1"}""")
        assertEquals(200, executeRes.statusCode())
        assertEquals("1", objectMapper.readTree(executeRes.body())["outputs"][0].asString())

        val testRes =
            post(
                "/languages/printscript/test",
                """{"content":"println(1);","version":"1.1","inputs":[],"expectedOutputs":["1"]}""",
            )
        assertEquals(200, testRes.statusCode())
        assertTrue(objectMapper.readTree(testRes.body())["passed"].asBoolean())
    }

    private fun get(path: String): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .GET()
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun post(
        path: String,
        body: String,
    ): HttpResponse<String> {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("http://localhost:$port$path"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
