package app.mydear.android.search

import okhttp3.HttpUrl

data class ContextualActionLink(val title: String, val url: String)

object ContextualActionLinkPolicy {
    private val productTerms = listOf(
        "제품", "스피커", "이어폰", "헤드폰", "노트북", "컴퓨터", "모니터", "휴대폰", "스마트폰", "태블릿",
        "텔레비전", "tv", "냉장고", "세탁기", "청소기", "카메라", "의자", "책상", "신발", "가방",
    )
    private val privatePatterns = listOf(
        Regex("(?:내|제|저희|우리)(?:집|방|회사|학교|주소|전화|가족|엄마|아빠|딸|아들)"),
        Regex("\\b01[016789][- ]?\\d{3,4}[- ]?\\d{4}\\b"),
        Regex("\\b\\d{6}[- ]?[1-4]\\d{6}\\b"),
    )
    private val requestWords = Regex(
        "제품명(?:이랑|과|하고)?|상품명(?:이랑|과|하고)?|링크|주소|추천(?:해줘|해주세요|해|)?|알려(?:줘|주세요|)|보여(?:줘|주세요|)|찾아(?:줘|주세요|)|줘|주세요",
        RegexOption.IGNORE_CASE,
    )

    fun create(currentQuery: String, previousUserMessages: List<String>): ContextualActionLink? {
        val normalized = SearchBoundary.normalizedQuery(currentQuery)
        if (!normalized.contains("링크") || !productTerms.any { normalized.contains(it, ignoreCase = true) }) return null
        if (containsPrivateContext(normalized)) return null
        val currentTopic = cleanTopic(normalized)
        val topic = currentTopic.takeIf(::isUsefulPublicTopic)
            ?: previousUserMessages.asReversed().asSequence().map(::cleanTopic).firstOrNull(::isUsefulPublicTopic)
            ?: return null
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("search.naver.com")
            .addPathSegment("search.naver")
            .addQueryParameter("query", topic)
            .build()
            .toString()
        return ContextualActionLink("${topic.take(40)} 찾아보기", url)
    }

    private fun cleanTopic(value: String): String = value
        .replace(requestWords, " ")
        .replace(Regex("[?!.。,，]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(80)

    private fun isUsefulPublicTopic(value: String): Boolean {
        val compact = value.replace(Regex("\\s+"), "")
        return value.length >= 3 &&
            productTerms.any { value.contains(it, ignoreCase = true) } &&
            !containsPrivateContext(compact)
    }

    private fun containsPrivateContext(value: String): Boolean {
        val compact = value.replace(Regex("\\s+"), "")
        return privatePatterns.any { it.containsMatchIn(compact) }
    }
}
