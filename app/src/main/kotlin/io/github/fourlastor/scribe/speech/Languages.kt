package io.github.fourlastor.scribe.speech

import java.util.Locale

object Languages {

    /** The name of the language with ISO 639 [code], in the language of [locale]. */
    fun displayName(code: String, locale: Locale = Locale.getDefault()): String {
        val tag = ALIASES[code] ?: code
        val name = Locale.forLanguageTag(tag).getDisplayLanguage(locale)
        return if (name.isEmpty() || name == tag) code.uppercase(Locale.ROOT) else name.replaceFirstChar { it.titlecase(locale) }
    }

    /** Joins consecutive pieces of a transcript the way [language] is written. */
    fun join(text: String, next: String, language: String?): String = when {
        text.isEmpty() -> next
        language in UNSPACED -> text + next
        else -> "$text $next"
    }

    // Whisper names Javanese "jw", the standard code is "jv".
    private val ALIASES = mapOf("jw" to "jv")

    /** Languages that do not put spaces between words. */
    private val UNSPACED = setOf("zh", "yue", "ja", "th", "lo", "my", "km", "bo")
}
