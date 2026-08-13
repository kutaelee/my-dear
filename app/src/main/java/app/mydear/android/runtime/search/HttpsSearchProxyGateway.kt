package app.mydear.android.runtime.search

import app.mydear.android.domain.SearchDocument
import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.SearchGateway
import app.mydear.android.domain.SearchRequest
import app.mydear.android.search.SearchBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

@Serializable private data class ProxySearchRequest(val query: String, val locale: String, val safeSearch: Boolean = true)
@Serializable private data class ProxySearchResponse(val results: List<ProxySearchDocument>)
@Serializable private data class ProxySearchDocument(
    val title: String,
    val host: String,
    val url: String,
    val snippet: String,
    val publishedAt: String? = null,
)

class HttpsSearchProxyGateway(
    private val endpoint: HttpUrl,
    private val client: OkHttpClient = secureClient(),
) : SearchGateway {
    private val json = Json { ignoreUnknownKeys = false; explicitNulls = false }

    init {
        require(endpoint.isHttps && endpoint.username.isEmpty() && endpoint.password.isEmpty())
        require(endpoint.encodedPath == "/v1/search")
    }

    override suspend fun search(request: SearchRequest): Result<SearchEvidence> = runCatching {
        withContext(Dispatchers.IO) {
            val query = SearchBoundary.normalizedQuery(request.query)
            require(query.isNotEmpty()) { "검색어를 입력해 주세요" }
            val payload = json.encodeToString(ProxySearchRequest(query, request.locale.take(24)))
            require(payload.encodeToByteArray().size <= MAX_REQUEST_BYTES)
            val httpRequest = Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .build()
            client.newCall(httpRequest).execute().use { response ->
                check(response.isSuccessful) { "검색 서비스가 응답하지 않았어요" }
                check(response.header("Location") == null) { "예상하지 못한 검색 이동 응답이에요" }
                val bytes = response.body.byteStream().readBounded(MAX_RESPONSE_BYTES)
                val decoded = json.decodeFromString<ProxySearchResponse>(bytes.decodeToString())
                check(decoded.results.size <= MAX_RESULTS)
                SearchEvidence(decoded.results.map { source ->
                    check(source.title.length <= 240 && source.host.length <= 253 && source.snippet.length <= 2_000)
                    check(SearchBoundary.isAllowedSourceUrl(source.url))
                    SearchDocument(
                        title = source.title,
                        host = source.host,
                        url = source.url,
                        snippet = sanitizeEvidence(source.snippet),
                        publishedAt = source.publishedAt?.take(40),
                    )
                })
            }
        }
    }

    private fun java.io.InputStream.readBounded(limit: Int): ByteArray {
        val output = ByteArrayOutputStream(limit.coerceAtMost(8_192))
        val buffer = ByteArray(4_096)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            check(total <= limit) { "검색 응답이 너무 커요" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sanitizeEvidence(value: String): String = value
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u202A-\\u202E\\u2066-\\u2069]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2_000)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MAX_REQUEST_BYTES = 8 * 1024
        private const val MAX_RESPONSE_BYTES = 64 * 1024
        private const val MAX_RESULTS = 5

        fun secureClient(): OkHttpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .callTimeout(8, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
}
