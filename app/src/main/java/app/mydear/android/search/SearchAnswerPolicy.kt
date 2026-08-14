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
        val detailedFact = detailedFact(query, evidence)
        if (detailedFact != null) {
            val (document, kind, name) = detailedFact
            return when (kind) {
                InternetQueryPolicy.DetailedKnowledgeKind.Leader -> "${document.title}의 리더는 ${name}입니다."
                InternetQueryPolicy.DetailedKnowledgeKind.Winner -> "${document.title}의 우승자는 ${name}입니다."
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
        val detailedFact = detailedFact(query, evidence)
        if (detailedFact != null) return listOf(evidence.documents.indexOf(detailedFact.first))
        return emptyList()
    }

    private fun detailedFact(
        query: String,
        evidence: SearchEvidence,
    ): Triple<app.mydear.android.domain.SearchDocument, InternetQueryPolicy.DetailedKnowledgeKind, String>? {
        val kind = InternetQueryPolicy.detailedKnowledgeKind(query) ?: return null
        val label = when (kind) {
            InternetQueryPolicy.DetailedKnowledgeKind.Leader -> "리더"
            InternetQueryPolicy.DetailedKnowledgeKind.Winner -> "우승자"
        }
        val pattern = Regex("${label}는\\s*([가-힣A-Za-z0-9_-]{1,30})입니다")
        evidence.documents.forEach { document ->
            val name = pattern.find(document.snippet)?.groupValues?.getOrNull(1)
            if (name != null) return Triple(document, kind, name)
        }
        return null
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
