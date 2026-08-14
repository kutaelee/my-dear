package app.mydear.android.search

import app.mydear.android.domain.SearchEvidence

object SearchAnswerPolicy {
    fun publicAnswer(query: String, evidence: SearchEvidence): String? {
        if (InternetQueryPolicy.isWeather(query)) {
            return evidence.documents.firstOrNull()?.snippet?.takeIf { it.isNotBlank() }
        }
        if (query.contains("대통령")) {
            evidence.documents.forEach { document ->
                val name = Regex("현(?:재)? 대한민국 대통령은[^.\\n]*?([가-힣]{2,4})이다")
                    .find(document.snippet)?.groupValues?.getOrNull(1)
                if (name != null) return "현재 대한민국 대통령은 ${name}입니다."
            }
        }
        return null
    }

    fun publicSourceIndices(query: String, evidence: SearchEvidence): List<Int> {
        if (InternetQueryPolicy.isWeather(query)) {
            return if (evidence.documents.firstOrNull()?.snippet.isNullOrBlank()) emptyList() else listOf(0)
        }
        if (query.contains("대통령")) {
            val index = evidence.documents.indexOfFirst { document ->
                Regex("현(?:재)? 대한민국 대통령은[^.\\n]*?([가-힣]{2,4})이다").containsMatchIn(document.snippet)
            }
            if (index >= 0) return listOf(index)
        }
        return emptyList()
    }

    fun sourceIndices(answer: String, documentCount: Int): List<Int> {
        if (documentCount <= 0) return emptyList()
        val referenced = Regex("\\[자료\\s*(\\d+)]").findAll(answer)
            .mapNotNull { it.groupValues[1].toIntOrNull()?.minus(1) }
            .filter { it in 0 until documentCount }
            .distinct()
            .toList()
        return referenced.firstOrNull()?.let(::listOf) ?: listOf(0)
    }
}
