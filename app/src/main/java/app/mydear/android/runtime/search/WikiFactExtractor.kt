package app.mydear.android.runtime.search

internal object WikiFactExtractor {
    private val rowSeparator = Regex("(?m)^\\|-.*$")
    private val leaderPosition = Regex("(?m)^\\|?\\s*\\*\\s*포지션\\s*:\\s*리더(?:\\s|$)")
    private val boldName = Regex("'''([^']{1,60})'''")
    private val winnerField = Regex("(?m)^\\|\\s*우승자\\s*=\\s*([^\\n<]+)")

    fun leader(wikitext: String): String? = wikitext
        .split(rowSeparator)
        .firstOrNull(leaderPosition::containsMatchIn)
        ?.let { row -> boldName.find(row)?.groupValues?.getOrNull(1) }
        ?.let(::cleanWikiValue)

    fun winner(wikitext: String): String? = winnerField
        .find(wikitext)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::cleanWikiValue)

    private fun cleanWikiValue(value: String): String = value
        .replace(Regex("\\[\\[(?:[^]|]+\\|)?([^]]+)]]"), "$1")
        .replace(Regex("\\{\\{[^}]+\\}\\}"), " ")
        .replace(Regex("'{2,}"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
}
