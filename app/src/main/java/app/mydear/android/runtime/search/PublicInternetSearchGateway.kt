package app.mydear.android.runtime.search

import app.mydear.android.domain.SearchDocument
import app.mydear.android.domain.SearchEvidence
import app.mydear.android.domain.SearchGateway
import app.mydear.android.domain.SearchRequest
import app.mydear.android.search.InternetQueryPolicy
import app.mydear.android.search.SearchBoundary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream

class PublicInternetSearchGateway(
    private val client: OkHttpClient = HttpsSearchProxyGateway.secureClient(),
) : SearchGateway {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    override suspend fun search(request: SearchRequest): Result<SearchEvidence> = runCatching {
        withContext(Dispatchers.IO) {
            val query = SearchBoundary.normalizedQuery(request.query)
            require(query.isNotBlank()) { "검색할 내용을 입력해 주세요." }
            if (!InternetQueryPolicy.isPublicFallbackSupported(query)) {
                throw InternetSearchException("이 최신 정보는 아직 무료 인터넷 도움에서 정확히 확인할 수 없어요.")
            }
            if (InternetQueryPolicy.isWeather(query)) searchWeather(query) else searchWikipedia(query)
        }
    }

    private fun searchWeather(query: String): SearchEvidence {
        val location = InternetQueryPolicy.weatherLocation(query)
            ?: throw InternetSearchException("날씨를 알려드리려면 동네 이름도 함께 말씀해 주세요. 예: ‘오늘 와부읍 날씨 알려줘’")
        val geocodeUrl = HttpUrl.Builder()
            .scheme("https")
            .host("geocoding-api.open-meteo.com")
            .addPathSegments("v1/search")
            .addQueryParameter("name", location)
            .addQueryParameter("count", "3")
            .addQueryParameter("language", "ko")
            .addQueryParameter("countryCode", "KR")
            .build()
        val geocode = getJson<GeocodeResponse>(geocodeUrl)
        val place = geocode.results.firstOrNull() ?: searchKoreanPlace(location)
            ?: throw InternetSearchException("‘$location’ 위치를 찾지 못했어요. 시·군 이름을 함께 말씀해 주세요.")
        val forecastUrl = HttpUrl.Builder()
            .scheme("https")
            .host("api.open-meteo.com")
            .addPathSegments("v1/forecast")
            .addQueryParameter("latitude", place.latitude.toString())
            .addQueryParameter("longitude", place.longitude.toString())
            .addQueryParameter("current", "temperature_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "2")
            .build()
        val forecast = getJson<ForecastResponse>(forecastUrl)
        val current = forecast.current
            ?: throw InternetSearchException("날씨 정보가 아직 준비되지 않았어요. 잠시 뒤 다시 시도해 주세요.")
        val placeName = listOfNotNull(place.admin1, place.admin2, place.admin3, place.name).distinct().joinToString(" ")
        val today = forecast.daily
        val snippet = buildString {
            append("${current.time} 기준 $placeName 날씨는 ${weatherDescription(current.weatherCode)}입니다. ")
            append("현재 ${current.temperature}°C, 체감 ${current.apparentTemperature}°C, ")
            append("강수 ${current.precipitation}mm, 바람 ${current.windSpeed}km/h입니다.")
            if (today != null && today.time.isNotEmpty()) {
                append(" 오늘 최고 ${today.maxTemperature.firstOrNull()}°C, 최저 ${today.minTemperature.firstOrNull()}°C, ")
                append("최대 강수 확률 ${today.precipitationProbability.firstOrNull()}%입니다.")
            }
        }
        return SearchEvidence(
            listOf(
                SearchDocument(
                    title = "$placeName 현재 날씨와 오늘 예보",
                    host = "open-meteo.com",
                    url = forecastUrl.toString(),
                    snippet = snippet.take(2_000),
                    publishedAt = current.time,
                ),
            ),
        )
    }

    private fun searchWikipedia(query: String): SearchEvidence {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("ko.wikipedia.org")
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", InternetQueryPolicy.knowledgeQuery(query))
            .addQueryParameter("gsrlimit", "3")
            .addQueryParameter("prop", "extracts")
            .addQueryParameter("exintro", "1")
            .addQueryParameter("explaintext", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("utf8", "1")
            .build()
        val response = getJson<WikiResponse>(url)
        val documents = response.query?.pages.orEmpty()
            .sortedBy { it.index }
            .filter { it.title.isNotBlank() && it.extract.isNotBlank() }
            .take(3)
            .map { page ->
                val pageUrl = HttpUrl.Builder()
                    .scheme("https")
                    .host("ko.wikipedia.org")
                    .addPathSegment("wiki")
                    .addPathSegment(page.title.replace(' ', '_'))
                    .build()
                SearchDocument(
                    title = page.title.take(240),
                    host = "ko.wikipedia.org",
                    url = pageUrl.toString(),
                    snippet = sanitize(page.extract),
                    publishedAt = null,
                )
            }
        if (documents.isEmpty()) throw InternetSearchException("인터넷에서 확인할 자료를 찾지 못했어요. 질문을 조금 다르게 써 주세요.")
        return SearchEvidence(documents)
    }

    private fun searchKoreanPlace(location: String): GeocodePlace? {
        val url = HttpUrl.Builder()
            .scheme("https")
            .host("ko.wikipedia.org")
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", location)
            .addQueryParameter("gsrlimit", "5")
            .addQueryParameter("prop", "coordinates")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("utf8", "1")
            .build()
        val page = getJson<WikiResponse>(url).query?.pages.orEmpty()
            .sortedBy { it.index }
            .firstOrNull { it.coordinates.isNotEmpty() }
            ?: return null
        val coordinate = page.coordinates.first()
        return GeocodePlace(
            name = location,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
        )
    }

    private inline fun <reified T> getJson(url: HttpUrl): T {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", "MyDear/0.1 (+https://github.com/kutaelee/my-dear)")
            .header("Cache-Control", "no-store")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful || response.header("Location") != null) {
                throw InternetSearchException("인터넷 정보를 가져오지 못했어요. 연결을 확인하고 다시 시도해 주세요.")
            }
            return json.decodeFromString(response.body.byteStream().readBounded(MAX_RESPONSE_BYTES).decodeToString())
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
            if (total > limit) throw InternetSearchException("인터넷 응답이 너무 커서 안전하게 중단했어요.")
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun sanitize(value: String): String = value
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("[\\u0000-\\u001F\\u007F-\\u009F\\u202A-\\u202E\\u2066-\\u2069]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(2_000)

    private fun weatherDescription(code: Int): String = when (code) {
        0 -> "맑음"
        1, 2 -> "대체로 맑음"
        3 -> "흐림"
        45, 48 -> "안개"
        51, 53, 55, 56, 57 -> "이슬비"
        61, 63, 65, 66, 67, 80, 81, 82 -> "비"
        71, 73, 75, 77, 85, 86 -> "눈"
        95, 96, 99 -> "뇌우"
        else -> "변화가 있는 날씨"
    }

    companion object { private const val MAX_RESPONSE_BYTES = 96 * 1024 }
}

class InternetSearchException(message: String) : IllegalStateException(message)

@Serializable private data class GeocodeResponse(val results: List<GeocodePlace> = emptyList())
@Serializable private data class GeocodePlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val admin1: String? = null,
    val admin2: String? = null,
    val admin3: String? = null,
)
@Serializable private data class ForecastResponse(val current: CurrentWeather? = null, val daily: DailyWeather? = null)
@Serializable private data class CurrentWeather(
    val time: String,
    @SerialName("temperature_2m") val temperature: Double,
    @SerialName("apparent_temperature") val apparentTemperature: Double,
    val precipitation: Double,
    @SerialName("weather_code") val weatherCode: Int,
    @SerialName("wind_speed_10m") val windSpeed: Double,
)
@Serializable private data class DailyWeather(
    val time: List<String> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max") val maxTemperature: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val minTemperature: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbability: List<Int?> = emptyList(),
)
@Serializable private data class WikiResponse(val query: WikiQuery? = null)
@Serializable private data class WikiQuery(val pages: List<WikiPage> = emptyList())
@Serializable private data class WikiPage(
    val title: String = "",
    val extract: String = "",
    val index: Int = Int.MAX_VALUE,
    val coordinates: List<WikiCoordinate> = emptyList(),
)
@Serializable private data class WikiCoordinate(
    @SerialName("lat") val latitude: Double,
    @SerialName("lon") val longitude: Double,
)
