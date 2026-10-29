package ingsis.language.services

import ingsis.language.domain.engine.CodeFormatter
import ingsis.language.domain.engine.CodeLinter
import ingsis.language.domain.engine.CodeValidator
import ingsis.language.domain.engine.LanguageEngine
import ingsis.language.domain.model.DomainError
import ingsis.language.domain.model.Language
import ingsis.language.domain.model.LanguageResult
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
    ): LanguageResult<LanguageEngine> {
        val engine =
            m_engines.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: return LanguageResult.Failure(DomainError.LanguageNotFound(name))

        if (version !in engine.versions) {
            return LanguageResult.Failure(DomainError.UnsupportedVersion(name, version))
        }

        return LanguageResult.Success(engine)
    }

    fun getValidator(
        name: String,
        version: String,
    ): LanguageResult<CodeValidator> =
        getEngine(name, version).flatMap { engine ->
            val validator =
                engine as? CodeValidator
                    ?: return@flatMap LanguageResult.Failure(DomainError.CapabilityNotSupported(name, "validate"))
            LanguageResult.Success(validator)
        }

    fun getFormatter(
        name: String,
        version: String,
    ): LanguageResult<CodeFormatter> =
        getEngine(name, version).flatMap { engine ->
            val formatter =
                engine as? CodeFormatter
                    ?: return@flatMap LanguageResult.Failure(DomainError.CapabilityNotSupported(name, "format"))
            LanguageResult.Success(formatter)
        }

    fun getLinter(
        name: String,
        version: String,
    ): LanguageResult<CodeLinter> =
        getEngine(name, version).flatMap { engine ->
            val linter =
                engine as? CodeLinter
                    ?: return@flatMap LanguageResult.Failure(DomainError.CapabilityNotSupported(name, "lint"))
            LanguageResult.Success(linter)
        }
}
