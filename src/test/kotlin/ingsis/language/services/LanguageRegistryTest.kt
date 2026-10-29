package ingsis.language.services

import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.engine.LanguageEngine
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.LanguageResult
import ingsis.language.domain.model.ValidationReport
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
            FakeValidatingEngine("dummy-lang", listOf("1.0", "1.1")),
            FakeNonValidatingEngine("other-lang", listOf("2.0")),
        )

    private class FakeValidatingEngine(
        override val name: String,
        override val versions: List<String>,
    ) : LanguageEngine,
        CodeValidator {
        override fun validate(
            code: String,
            version: String,
        ): LanguageResult<ValidationReport> = LanguageResult.Success(ValidationReport(isValid = true))
    }

    private class FakeNonValidatingEngine(
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
        assertTrue(result.errorOrNull() is DomainError.UnsupportedVersion)
        assertEquals("Versión no soportada: 9.9 para dummy-lang", result.errorOrNull()?.message)
    }

    @Test
    fun `getEngine fails when language is not registered`() {
        val result = registry.getEngine("unknown-lang", "1.0")

        assertFalse(result.isSuccess)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
        assertEquals("Lenguaje no soportado: unknown-lang", result.errorOrNull()?.message)
    }

    @Test
    fun `getValidator returns validator when engine implements CodeValidator`() {
        val result = registry.getValidator("dummy-lang", "1.0")

        assertTrue(result.isSuccess)
        assertEquals("dummy-lang", (result.getOrNull() as LanguageEngine).name)
    }

    @Test
    fun `getValidator fails when engine does not implement CodeValidator`() {
        val result = registry.getValidator("other-lang", "2.0")

        assertFalse(result.isSuccess)
        assertTrue(result.errorOrNull() is DomainError.CapabilityNotSupported)
        assertEquals("El lenguaje other-lang no soporta validate", result.errorOrNull()?.message)
    }

    @Test
    fun `getValidator fails when language is unknown`() {
        val result = registry.getValidator("unknown-lang", "1.0")

        assertFalse(result.isSuccess)
        assertTrue(result.errorOrNull() is DomainError.LanguageNotFound)
    }
}
