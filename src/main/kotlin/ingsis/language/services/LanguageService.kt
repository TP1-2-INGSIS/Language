package ingsis.language.services

import ingsis.language.domain.model.Languages
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class LanguageService(
    private val m_registry: LanguageRegistry,
) {
    fun getAllLanguages(): Result<Languages> =
        runCatching {
            m_registry.getAllLanguages()
        }
}
