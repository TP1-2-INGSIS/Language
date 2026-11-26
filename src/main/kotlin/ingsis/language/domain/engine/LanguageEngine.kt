package ingsis.language.domain.engine

interface LanguageEngine {
    val name: String
    val versions: List<String>

    /** Version of the underlying engine artifact (e.g. the CNC release), used to detect parser upgrades. */
    val engineVersion: String
}
