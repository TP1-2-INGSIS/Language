package ingsis.language.services

import ingsis.language.domain.model.Languages
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class LanguageService {
    fun getAllLanguages(): Result<Languages> = Result.success(listOf())
}
