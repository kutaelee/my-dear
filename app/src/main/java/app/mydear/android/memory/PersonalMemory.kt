package app.mydear.android.memory

data class PersonalMemory(
    val id: String,
    val text: String,
    val createdAtEpochMs: Long,
)

sealed interface MemoryCommand {
    data class Remember(val fact: String) : MemoryCommand
    data class Forget(val query: String) : MemoryCommand
    data object ForgetAll : MemoryCommand
    data object Recall : MemoryCommand
}

object MemoryIntentParser {
    private val rememberTerms = listOf(
        "기억해 주세요", "기억해줘요", "기억해 줘", "기억해줘",
        "기억해 두세요", "기억해두세요", "기억해둬요", "기억해둬",
    )
    private val forgetAllCommands = setOf(
        "모두 잊어", "전부 잊어", "다 잊어", "기억 전부 지워", "기억 모두 지워",
        "모두 잊어줘", "전부 잊어줘", "다 잊어줘", "기억 전부 지워줘", "기억 모두 지워줘",
        "모두 잊어 주세요", "전부 잊어 주세요", "다 잊어 주세요", "기억 전부 지워 주세요", "기억 모두 지워 주세요",
        "기억한 정보 모두 잊어줘", "기억한 정보 전부 잊어줘",
    )
    private val recallTerms = listOf("뭘 기억", "무엇을 기억", "기억한 내용", "기억하고 있는", "나에 대해 아는")

    fun parse(input: String): MemoryCommand? {
        val normalized = normalize(input).trim(' ', '.', '!', '?')
        if (normalized in forgetAllCommands) return MemoryCommand.ForgetAll
        if (recallTerms.any(normalized::contains) && !normalized.contains("잊어")) return MemoryCommand.Recall
        rememberTerms.firstOrNull(normalized::endsWith)?.let { term ->
            val fact = normalized.removeSuffix(term).trim(' ', '.', '!', '?')
            return if (fact.length >= 2) MemoryCommand.Remember(fact) else null
        }
        if (
            normalized.endsWith("잊어 주세요") || normalized.endsWith("잊어줘요") ||
            normalized.endsWith("잊어 줘") || normalized.endsWith("잊어줘") || normalized.endsWith("잊어")
        ) {
            val query = normalized
                .removeSuffix("잊어 주세요")
                .removeSuffix("잊어줘요")
                .replace("잊어 줘", " ")
                .replace("잊어줘", " ")
                .removeSuffix("잊어")
                .trim(' ', '.', '!', '?')
            return if (query.length >= 2) MemoryCommand.Forget(query) else null
        }
        return null
    }

    internal fun normalize(value: String): String = value
        .replace(Regex("\\s+"), " ")
        .trim()
}

object LocalMemoryRetriever {
    private val stopWords = setOf(
        "나는", "내가", "나의", "저는", "제가", "저의", "그리고", "그런데", "알려줘", "말해줘",
        "기억해줘", "기억", "내용", "정보", "무엇", "뭘", "대해", "있는", "있어", "했지",
    )

    fun retrieve(memories: List<PersonalMemory>, query: String, limit: Int = 1): List<PersonalMemory> {
        if (memories.isEmpty()) return emptyList()
        val queryTokens = tokens(query)
        return memories
            .map { memory ->
                val memoryTokens = tokens(memory.text)
                val overlap = queryTokens.intersect(memoryTokens).size
                val contains = queryTokens.count { it.length >= 2 && memory.text.contains(it) }
                val phraseMatch = PERSONAL_PHRASES.count { phrase -> query.contains(phrase) && memory.text.contains(phrase) }
                val score = overlap * 3 + contains * 2 + phraseMatch * 10
                memory to score
            }
            .filter { (_, score) -> score > 0 }
            .sortedWith(compareByDescending<Pair<PersonalMemory, Int>> { it.second }.thenByDescending { it.first.createdAtEpochMs })
            .take(limit.coerceIn(1, 8))
            .map { it.first }
    }

    fun bestMatches(memories: List<PersonalMemory>, query: String, limit: Int = 5): List<PersonalMemory> =
        retrieve(memories, query, limit).ifEmpty {
            val normalized = MemoryIntentParser.normalize(query)
            memories.filter { it.text.contains(normalized, ignoreCase = true) }.take(limit)
        }

    private fun tokens(value: String): Set<String> = value
        .lowercase()
        .split(Regex("[^가-힣a-z0-9]+"))
        .map { token ->
            token.trim().let { raw ->
                KOREAN_PARTICLES.firstOrNull { suffix -> raw.length >= suffix.length + 2 && raw.endsWith(suffix) }
                    ?.let(raw::removeSuffix) ?: raw
            }
        }
        .filter { it.length >= 2 && it !in stopWords }
        .toSet()

    private val KOREAN_PARTICLES = listOf("에서", "으로", "에게", "한테", "은", "는", "이", "가", "을", "를", "의", "도", "에")
    private val PERSONAL_PHRASES = listOf("내 이름", "제 이름", "딸 이름", "아들 이름", "배우자 이름", "좋아하는", "싫어하는")
}
