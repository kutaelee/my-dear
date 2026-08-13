package app.mydear.android.search

import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.SearchGateway
import app.mydear.android.domain.SearchRequest

class DisabledSearchGateway : SearchGateway {
    override suspend fun search(request: SearchRequest): Result<SearchEvidence> =
        Result.failure(IllegalStateException("검색 서버가 아직 연결되지 않았어요"))
}

object SearchBoundary {
    const val MAX_QUERY_CHARS = 512
    private val controlCharacters = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun normalizedQuery(value: String): String = value
        .replace(controlCharacters, " ")
        .trim()
        .take(MAX_QUERY_CHARS)

    fun isAllowedSourceUrl(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
}
