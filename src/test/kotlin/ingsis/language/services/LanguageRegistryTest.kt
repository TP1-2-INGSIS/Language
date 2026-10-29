package ingsis.language.services

import ingsis.language.domain.engine.LanguageEngine
import jakarta.enterprise.inject.Instance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class LanguageRegistryTest {
    private val mockInstance = Mockito.mock(Instance::class.java)
    private lateinit var registry: LanguageRegistry

    private val fakeEngines =
        listOf(
            FakeEngine("dummy-lang", listOf("1.0", "1.1")),
            FakeEngine("other-lang", listOf("2.0")),
        )

    private class FakeEngine(
        override val name: String,
        override val versions: List<String>,
    ) : LanguageEngine

    @BeforeEach
    @Suppress("UNCHECKED_CAST")
    fun setUp() {
        Mockito.`when`(mockInstance.iterator()).thenAnswer { fakeEngines.iterator() }
        registry = LanguageRegistry(mockInstance as Instance<LanguageEngine>)
    }

    @Test
    fun `getAllLanguages maps all registered engines`() {
        val languages = registry.getAllLanguages()

        assertEquals(2, languages.size)
        assertEquals("dummy-lang", languages[0].name)
        assertEquals(listOf("1.0", "1.1"), languages[0].versions)
        assertEquals("other-lang", languages[1].name)
    }

    @Test
    fun `getEngine returns engine when name and version match`() {
        val result = registry.getEngine("dummy-lang", "1.0")

        assertTrue(result.isSuccess)
        assertEquals("dummy-lang", result.getOrNull()?.name)
    }

    @Test
    fun `getEngine is case-insensitive`() {
        val result = registry.getEngine("DuMMy-LaNg", "1.1")

        assertTrue(result.isSuccess)
        assertEquals("dummy-lang", result.getOrNull()?.name)
    }

    @Test
    fun `getEngine fails when version is not supported`() {
        val result = registry.getEngine("dummy-lang", "9.9")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Versión no soportada: 9.9 para dummy-lang", result.exceptionOrNull()?.message)
    }

    @Test
    fun `getEngine fails when language is not registered`() {
        val result = registry.getEngine("unknown-lang", "1.0")

        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("Lenguaje no soportado: unknown-lang", result.exceptionOrNull()?.message)
    }
}
