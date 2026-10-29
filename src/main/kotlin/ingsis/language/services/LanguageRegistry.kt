package ingsis.language.services

import ingsis.language.domain.engine.LanguageEngine
import ingsis.language.domain.model.Language
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.inject.Instance

@ApplicationScoped
class LanguageRegistry(
    private val m_engines: Instance<LanguageEngine>,
) {
    fun getAllLanguages(): List<Language> =
        m_engines.map { engine ->
            Language(name = engine.name, versions = engine.versions)
        }

    fun getEngine(
        name: String,
        version: String,
    ): Result<LanguageEngine> {
        val engine =
            m_engines.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return Result.failure(IllegalArgumentException("Lenguaje no soportado: $name"))

        if (version !in engine.versions) {
            return Result.failure(IllegalArgumentException("Versión no soportada: $version para $name"))
        }

        return Result.success(engine)
    }
}
