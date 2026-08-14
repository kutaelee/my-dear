package app.mydear.android.search

import okhttp3.HttpUrl

data class ContextualActionLink(val title: String, val url: String)

object ContextualActionLinkPolicy {
    private val genericProductTerms = listOf("제품", "상품", "물건")
    private val specificProductTerms = listOf(
        "북쉘프 스피커", "스피커", "이어폰", "헤드폰", "노트북", "컴퓨터", "모니터", "휴대폰", "스마트폰", "태블릿",
        "텔레비전", "tv", "냉장고", "세탁기", "청소기", "카메라", "의자", "책상", "신발", "가방", "방향제", "향수",
        "화장품", "선풍기", "에어컨", "공기청정기", "가습기", "침대", "소파", "영양제",
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
    private val genericProductPlaceholder = Regex("제품(?:으로)?|상품(?:으로)?|물건(?:으로)?", RegexOption.IGNORE_CASE)
    private val recommendationContext = Regex("추천|골라|선택|살까|사려고|사고\\s*싶", RegexOption.IGNORE_CASE)
    private val transientTrendWords = Regex("요즘|최근|잘나가는|인기\\s*있는|많이\\s*쓰는", RegexOption.IGNORE_CASE)
    private val safeProductQualifiers = listOf(
        "가성비", "좋은", "저렴한", "고급", "무선", "유선", "소형", "대형", "휴대용", "입문용", "조용한",
    )

    fun create(currentQuery: String, previousUserMessages: List<String>): ContextualActionLink? {
        val normalized = SearchBoundary.normalizedQuery(currentQuery)
        if (!normalized.contains("링크")) return null
        if (containsPrivateContext(normalized)) return null
        val currentTopic = cleanTopic(normalized)
        val previousTopic = previousUserMessages.asReversed().asSequence()
            .mapNotNull(::previousProductTopic)
            .firstOrNull()
        val topic = when {
            hasSpecificProduct(currentTopic) -> currentTopic
            containsGenericProduct(currentTopic) && previousTopic != null -> mergeFollowUpTopic(currentTopic, previousTopic)
            currentTopic.isBlank() -> previousTopic
            previousTopic != null && !hasAnyProductSignal(currentTopic) -> mergeFollowUpTopic(currentTopic, previousTopic)
            else -> currentTopic.takeIf { hasAnyProductSignal(it) }
        }
            ?.takeIf(::isUsefulPublicTopic)
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

    private fun previousProductTopic(value: String): String? {
        if (containsPrivateContext(value)) return null
        if (!recommendationContext.containsMatchIn(value) && !hasAnyProductSignal(value)) return null
        val cleaned = cleanTopic(value)
            .replace(transientTrendWords, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val category = specificProductTerms
            .sortedByDescending(String::length)
            .firstOrNull { cleaned.contains(it, ignoreCase = true) }
            ?: return null
        val qualifiers = safeProductQualifiers.filter { cleaned.contains(it, ignoreCase = true) }
        return (qualifiers + category)
            .distinct()
            .joinToString(" ")
            .take(80)
    }

    private fun mergeFollowUpTopic(currentTopic: String, previousTopic: String): String {
        val qualifier = currentTopic
            .replace(genericProductPlaceholder, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return listOf(qualifier, previousTopic)
            .filter(String::isNotBlank)
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(80)
    }

    private fun isUsefulPublicTopic(value: String): Boolean {
        val compact = value.replace(Regex("\\s+"), "")
        return value.length >= 2 && hasSpecificProduct(value) && !containsPrivateContext(compact)
    }

    private fun hasSpecificProduct(value: String): Boolean = specificProductTerms.any { value.contains(it, ignoreCase = true) }

    private fun containsGenericProduct(value: String): Boolean = genericProductTerms.any { value.contains(it, ignoreCase = true) }

    private fun hasAnyProductSignal(value: String): Boolean = hasSpecificProduct(value) || containsGenericProduct(value)

    private fun containsPrivateContext(value: String): Boolean {
        val compact = value.replace(Regex("\\s+"), "")
        return privatePatterns.any { it.containsMatchIn(compact) }
    }
}
